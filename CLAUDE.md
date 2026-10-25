# Pollenplot

Pollenplot: Ecosystem Management for Your Backyard. An Android app that identifies plants and animals on the phone,
keeps the garden's zones, plants, wildlife, bees, bee hotels and watering, and explains how they fit together. The
plan for version 1 is in `~/.claude/plans/i-want-to-build-generic-tulip.md`. It is written for Australia first
(Southern Hemisphere seasons, metric units, Australian natives and state rules).

## Build and test

The local toolchain lives outside the repo: JDK 17 at `~/.local/opt/jdk-17` and the Android SDK at
`~/Android/Sdk` (`local.properties` points there and is not committed). There is no emulator (no KVM), so the camera,
the models and background work can only be checked on a phone over `adb`.

```bash
export JAVA_HOME=~/.local/opt/jdk-17 ANDROID_HOME=~/Android/Sdk
./gradlew :domain:test                  # pure-Kotlin rules, fast
./gradlew :app:testDebugUnitTest        # Robolectric: screens, storage and workers
./gradlew :app:lintDebug :app:assembleDebug
```

Screen tests save pictures to `app/build/screenshots/`. Look at them after UI changes: there are light, dark and
wide-window versions, and `PlotScreenFlowTest` drives a demo yard (`DemoGarden`) through the Plot, zones, plants and
search. Screens fed by Room flows use the classic `createComposeRule`, because the v2 rule's standard test dispatcher
does not pick up database emissions while a test waits. Even so, a screen whose first data came only from a Room flow
now and then stayed on its loading state under Robolectric (about one run in ten, more while Gradle was busy), so the
Identify results are published by `IdentifyModel` as soon as the analysis returns, and the Room flow only brings later
changes. Prefer that shape when a screen opens on data the app has just written. Robolectric also cannot load the native model interpreter, decode with `ImageDecoder` or settle a text field inside a
dialog window, so screen tests use a fake `PlantIdentifier`, give Coil `imageDecoderEnabled(false)`, and searches open
as full screens rather than in dialogs.

Free disk space is short on this machine, so model exports and evaluations run in GitHub Actions, and the large models
are never downloaded here.

CI (`.github/workflows/android.yml`) runs the same tasks, checks that `strings.xml` matches `tools/strings_*.py`, and
uploads the debug APK, reports and screenshots. It builds a signed release APK when the `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets are set (the build reads them as `PP_*` environment
variables). A `v*` tag that matches `versionName` publishes the APK and its checksum as a GitHub Release, with the
notes from that version's section of `CHANGELOG.md`. Keystores live outside the repo and must never be committed.

## Layout

- `domain/`: every rule and calculation, with no Android code. Every rule change needs a test here.
  - `season/`: hemispheres, seasons and `MonthSet`.
  - `plants/`: the plant model, `KnowledgeBase` (parsing and validating the JSON), `CareResolver` (species, then
    genus, then family), `EcoRoles` (the badges) and `Companions`.
  - `garden/`: zones, garden plants, `PlotLayout` (the 50 cm grid and tidy placement) and `ZoneBadges`.
  - `semantic/`: IRIs, the vocabularies, `GardenGraph` (records as Darwin Core and schema.org triples), `JsonLd`
    and `SearchText` (what the search index holds for each record).
  - `id/`: identification. `RegionPlanner` (the tiles each photo is classified in and how regions are chosen),
    `CandidateFusion` (each model's suggestions combined, with the garden boost), `ThumbnailMath`, `FlowerHint`,
    `Confidence` and `ZoneSuggestion` (the zone offered for a plant found in a photo).
- `app/data`: `GardenRepository` (records, detections, the search index and JSON-LD export), `GardenViews` (records
  joined with the knowledge base for the screens), `PhotoAnalysis` (a photo through the identifier into storage and
  review), Room in `data/db`, photos and cut-outs in `data/photos`, the knowledge base loader in `data/kb` and
  DataStore settings in `data/prefs`.
- `app/ml`: `PlantIdentifier` and `FastPlantIdentifier` (AIY Plants V1 through the LiteRT `Interpreter`), and bitmap
  helpers.
- `app/scan`: the phone's gallery behind the `Gallery` interface (`MediaStoreGallery`), `PhotoScanner` (gallery and
  added photos through the analysis, each gallery photo recorded in `scanned_media` so it is looked at once), and the
  WorkManager jobs: `AddPhotosWorker`, `LibraryScanWorker` (stops after eight minutes and queues the rest),
  `LibraryWatchWorker` (a content-URI trigger that re-arms itself) and `ScanScheduler`, which matches them to the
  settings. `app/notify` holds the notification channels.
- `app/ui`: Compose screens. `ui/components` holds the shared pieces, `ui/nav` the routes and the app frame,
  `ui/plot` the canvas and zones, `ui/plants` plant pages and labels, `ui/search` search, `ui/identify` the camera
  (`IdentifyModel` keeps its state across turns), and `ui/review` the review cards, `PhotoReviewScreen` (shared by
  the Identify results and the Review queue) and `ReviewModel`.
- `AppContainer` builds the shared objects by hand. There is no dependency-injection library. Workers reach it through
  `PollenplotApp`. Tests that scan or review photos run with `@Config(application = TestApp::class)`, which gives the
  container a `FakeGallery` and a `ScriptedIdentifier`; `TestPhotos` paints garden photos, and workers run through
  `TestListenableWorkerBuilder` after `WorkManagerTestInitHelper.initializeTestWorkManager`.
- `SettingsStore` makes its DataStore itself, and DataStore allows one store per file in a process, so tests use the
  app's store (`app.container.settingsStore`) rather than making another.

## Models

Model files are not committed. The build downloads each one once, checks it against its published SHA-256
(`VerifiedDownloadTask`) and extracts it into the generated assets (`ExtractModelTask`), which also checks that the
model's embedded labels match the committed labels file. Assets ending in `.tflite` stay uncompressed so the
interpreter can map them. AIY Plants V1 takes 224 × 224 RGB bytes and gives 2,102 probabilities in 256ths, the last
being "no plant here". Run models with LiteRT; do not add MediaPipe Tasks or ML Kit, because MediaPipe Tasks sends
usage statistics to Google through `com.google.android.datatransport` and ML Kit is not open source. LiteRT itself
depends on Google Play's model delivery (`com.google.android.play`, Play services), which the app does not use, so
`app/build.gradle.kts` excludes those groups. Before adding a library, run
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and check every new artefact for telemetry and
for a licence that is not open source.

## Knowledge base

The plant facts live in `app/src/main/assets/kb/` as JSON that people can read and edit: `species/*.json`,
`genera.json`, `families.json`, `companions.json` and `meta.json` (bump its version when the content changes).
Scores run from 0 to 3, sizes are in millimetres, months are written as "jan" to "dec" for the Southern Hemisphere,
and forage is `honey [nectar, pollen]`, `stingless [nectar, pollen, resin]`, `solitary [nectar, pollen]`. Leave a
fact out rather than guess it: the app says "Not recorded yet", and `CareResolver` fills gaps from the genus and family
only where a fact holds for the whole group. Weed status comes from official sources for the user's area, so species
records keep only the national Weeds of National Significance flag. `KnowledgeBaseFilesTest` in `:domain` checks
the real files on every test run.

## Material 3 Expressive

`material3` is pinned to the 1.5 line over the Compose BOM, because the Expressive components are stable only
there. `MaterialShapes` and the loading indicators still need `ExperimentalMaterial3ExpressiveApi`; reach them
through `ui/components/PollenShapes.kt` and other components, so an API change touches one place. Use the new
`ListItem` overloads (`onClick`, `checked`, trailing `content`), not the deprecated `headlineContent` one.

## Data rules

- Measurements are stored as integers with the unit in the name, never as floats: water depth in µm, volume in mL,
  flow in mL/h, area in cm², temperature in tenths of °C, weight in g, lengths in mm, confidence in basis points and
  coordinates in degrees × 10⁷.
- Every user record has a UUID string key, which is also its IRI (`urn:uuid:…`) in the semantic layer.
- The user database stays at schema version 1 until the first tagged release. After that, every change needs a
  migration and a schema committed in `app/schemas/`, and there is no destructive migration fallback. The read-only
  knowledge database is rebuilt from `app/src/main/assets/kb/` whenever its version changes.
- Garden locations sent to online services (weather, ALA) are rounded to about 1 km. Photos never leave the phone.
- Every write goes through `GardenRepository`, which also updates the FTS4 search index and links. Room supports
  FTS4 but not FTS5, so the index uses FTS4; queries are reduced to letters and digits with a prefix star on each word.

## Copy

All user-facing text is in `tools/strings_*.py`; run `python3 tools/gen_strings.py` to regenerate
`app/src/main/res/values/strings.xml`. Do not edit `strings.xml` by hand.

Copy follows the product-UI rules in `/home/william/informed-trader/VOICE.md`:

- Say plainly what the thing is, then what the reader can do, then what happens next, in complete sentences.
- Put the main clause first and the condition after it. Headings name the thing, without conditions.
- Write "Please" in instructions and errors. No clipped or inverted phrasing, and no colon-led openers.
- Spell a term out once with its abbreviation, as in "Reference evapotranspiration (ET₀)".
- Use Australian spelling everywhere (recognise, colour, organise, towards).
- Inside the app it is fine to name the models ("Gemma 4", "BioCAP") and their state ("Loading the model.").

Status is always shown with an icon and a word, never colour alone, and no action is gesture-only.

## Icons and licences

Icons are Material Symbols Rounded (Apache License 2.0). Run `python3 tools/fetch_icons.py <name> [<name>:fill]` to
add one as `res/drawable/ic_<name>.xml`. About › Licences reads `assets/licences/`: the build copies the app's
`LICENSE` and the licence files inside library archives listed in `licenceArchives` in `app/build.gradle.kts`, and
`app/src/main/assets/licences/` holds texts no archive carries. Please add a library to the list in
`ui/settings/AboutScreens.kt` when it is added to the app.
