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
./gradlew :ml:test                      # the text model's tokenizer and embedder, run for real on the JVM
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
  - `wildlife/`: `LifeGroup`, the animal knowledge base (`FaunaBase`, with each state's rules and their official
    pages), `Sighting` and `Biodiversity` (a year of sightings for the Wildlife tab: each kind seen, the months, and
    notices for animals that are venomous or that the law in the garden's state asks about).
  - `id/`: identification. `RegionPlanner` (the tiles each photo is classified in and how regions are chosen),
    `CandidateFusion` (each model's suggestions combined, with a boost for plants in the garden and a smaller one for
    plants recorded near it), `ThumbnailMath`, `FlowerHint`, `Confidence` and `ZoneSuggestion` (the zone offered for
    a plant found in a photo).
  - `bees/honey/`: `HoneyBees` (apiaries, hives and their records: inspections, mite checks, treatments, feeding,
    harvests and colony events) and `HiveTasks` (when each hive is due for an inspection, a brood check and a mite
    check, following the Australian Honey Bee Industry Biosecurity Code of Practice, version 2.1), with each state's
    registration page in `BeekeeperRegistration`. `HiveReminders` decides which due checks a notification tells
    about: each once while it stays due.
- `ml/`: pure-JVM model code that is checked on the JVM against reference outputs: the BERT `WordPieceTokenizer` and
  `TextEmbedder` (bge-small-en-v1.5 through ONNX Runtime). It compiles against the desktop ONNX Runtime API, which the
  Android runtime matches; the app brings the Android one. `TextEmbedderTest` compares tokens and vectors with
  `ml/src/test/resources/bge_reference.json`, made with Hugging Face's tokenizers and ONNX Runtime in Python.
- `app/data`: `GardenRepository` (records, detections, the search index and JSON-LD export), `GardenViews` (records
  joined with the knowledge base for the screens), `PhotoAnalysis` (a photo through the identifier into storage and
  review), `BeeRepository` (the honey bee records) and `BeeViews` (hives with their zone, apiary and what each is due
  for today), Room in `data/db`, photos and cut-outs in `data/photos`, the knowledge base loader in `data/kb` and
  DataStore settings in `data/prefs`.
- `app/ml`: the `Identifier` interface and `FastPlantIdentifier` (AIY Plants V1 on tiles of the photo, and AIY Birds
  and Insects V1 on the middle of the frame, through the LiteRT `Interpreter`), `AccuratePlantIdentifier` (BioCAP on
  top, limited to the species of the group the user chose), and bitmap helpers. The quick models cannot tell a plant
  from an animal, so the camera's group chips say which to ask. For a photo nobody chose a group for, `groupOf` asks
  BioCAP, whose closest species must clearly agree on a group (`AccurateRegions.GROUP_SHARE_BP`); without the pack,
  it is a plant.
- `app/ai`: `GemmaModel` (Gemma 4 E2B through LiteRT-LM, with an image backend, downloaded when the user asks and
  released after two idle minutes) and `GardenAssistant`, behind the `Assistant` interface. `domain/assistant` holds
  `GardenAssistantGuard` (the questions Pollenplot answers itself, and answers that give a chemical amount),
  `GardenPrompts`, `AssistantFacts` (a plant's or animal's facts as plain lines for the model; nothing about eating)
  and `WildlifeRescue` (each state's rescue service, checked against its official page). The model cannot run on the
  JVM, so screen tests use `AskTestApp` and its `FakeAssistant`; the guard, the not-downloaded reply and the search for
  a question's records run for real. `SecondOpinionWorker` asks Gemma about unsure finds while the phone charges and is
  idle; its choice is one vote at Gemma's weight (`CandidateFusion.withSecondOpinion`). A plant waits for BioCAP's
  accurate pass first, because that pass weighs the suggestions again from the models alone.
- `app/search`: `MeaningSearch` (the optional text model, its download and the vectors it makes for each record, kept
  in the `embeddings` table and refreshed by `MeaningIndexWorker` after edits). Meaning matches come after keyword
  matches, at most three and only above `MeaningMatch.MIN_SCORE`, because this small model scores unrelated text
  about 0.5. `app/download` holds `VerifiedDownload`, the download manager with a SHA-256 check.
- `app/scan`: the phone's gallery behind the `Gallery` interface (`MediaStoreGallery`), `PhotoScanner` (gallery and
  added photos through the analysis, each gallery photo recorded in `scanned_media` so it is looked at once), and the
  WorkManager jobs: `AddPhotosWorker`, `LibraryScanWorker` (stops after eight minutes and queues the rest),
  `LibraryWatchWorker` (a content-URI trigger that re-arms itself) and `ScanScheduler`, which matches them to the
  settings. `app/notify` holds the notification channels and `HiveReminderWorker`, which looks at the hives at about
  seven each morning while there are hives and the reminders are on.
- `app/ui`: Compose screens. `ui/components` holds the shared pieces, `ui/nav` the routes and the app frame,
  `ui/plot` the canvas and zones, `ui/plants` plant pages and labels, `ui/search` search, `ui/identify` the camera
  (`IdentifyModel` keeps its state across turns), `ui/weeds` the Weed card, the Weeds screen, Compare and the Weed
  finder, `ui/wildlife` the Wildlife tab, an animal's page, a sighting's page and the safety notice, and `ui/review`
  the review cards (`AnimalCards` for animals), `PhotoReviewScreen` (shared by the Identify results and the Review
  queue) and `ReviewModel`, and `ui/bees` the Bees tab (`HoneySection`), a hive's page, its editor and the form for
  each kind of record (`HoneyForms`).
- `app/place`: `PlaceFinder` (suburb and postcode search through Open-Meteo, and the council area and state at a
  point through the Atlas of Living Australia) and `PhoneLocation` (approximate location from the phone's own location
  service). `app/web` holds `Web`, the small HTTP client the app's few web requests go through.
- `app/weeds`: `WeedLibrary` (the weed lists, nearby plant records and the animals most recorded nearby, kept in
  `filesDir/weeds` and read for the garden's place and state as `WeedData`), `WeedSync` (brings them up to date; a
  source that cannot be reached keeps its last good copy), `WeedSyncWorker` (monthly on Wi-Fi, and at once when the
  garden moves) and `WeedPhotos` (photos of a plant recorded in Australia, fetched when the user compares, kept only
  in memory). Which sources apply where is in `app/src/main/assets/weeds/sources.json`, checked by
  `WeedSourcesFileTest`. The rules that combine them are in `domain/weeds` (`WeedResolver`, `WeedNames`,
  `BrisbaneWeeds`, `AlaLists`, `NearbyRecords`, `AlaNames`) and the place parsing in `domain/place`.
  `GardenViews.locality` pairs the garden's state with its `WeedData`; badges, plant views and the search index take
  their weed risk from it.
- `AppContainer` builds the shared objects by hand. There is no dependency-injection library. Workers reach it through
  `PollenplotApp`. Tests that scan or review photos run with `@Config(application = TestApp::class)`, which gives the
  container a `FakeGallery` and a `ScriptedIdentifier`; `TestPhotos` paints garden photos, and workers run through
  `TestListenableWorkerBuilder` after `WorkManagerTestInitHelper.initializeTestWorkManager`.
- `WildlifeTestApp` fixes the container's clock at 8 am on 28 October 2026 in Brisbane, and `DemoWildlife` seeds a
  year of sightings in `DemoGarden`. Tests that read dates run with the `en-rAU` qualifier, because Robolectric
  otherwise formats them for the United States.
- `MeaningTestApp` gives the container a `ConceptEmbedder` in place of the text model, whose runtime cannot load on
  the JVM.
- `WeedTestApp` gives the container a `RecordedWeb`, which answers from responses recorded from Open-Meteo, the Atlas
  of Living Australia and Brisbane City Council in `app/src/test/resources/web`, and a `FixedLocation` in Taringa.
  Tests never reach the real services. WorkManager is not set up when a Robolectric app starts, so the container's
  schedules wait for a test to set it up, and an update the test needs is run directly with `weedSync.update()`.
- `SettingsStore` makes its DataStore itself, and DataStore allows one store per file in a process, so tests use the
  app's store (`app.container.settingsStore`) rather than making another.

## Models

BioCAP's input is prepared by `Bitmaps.squarePixels`: the square around the plant, halved while it is at least twice
224 across, then scaled to 224 (`smoothScale`). `tools/models/export_biocap.py` prepares its evaluation photos the
same way (`phone_square`), so a change to one needs the same change to the other. The pack's evaluation measures each
step from BioCAP as published to the model the phone runs, and ships the smallest form within half a point of the
full model. Weights-only 8-bit (`MatMulNBits`, a contrib operator in the full ONNX Runtime the app uses) keeps
BioCAP's accuracy; 8-bit activations lose about seven points of top-1 on transformers.

Model files are not committed. The build downloads each one once, checks it against its published SHA-256
(`VerifiedDownloadTask` in `buildSrc`) and extracts it into the generated assets (`ExtractModelTask`), which also
checks that the model's embedded labels match the committed labels file. Models the user downloads in the app are
pinned in `gradle.properties` (URL at a fixed revision, SHA-256 and size) and reach the app through `BuildConfig`. Assets ending in `.tflite` stay uncompressed so the
interpreter can map them. AIY Plants V1 takes 224 × 224 RGB bytes and gives 2,102 probabilities in 256ths, the last
being "no plant here". Run models with LiteRT; do not add MediaPipe Tasks or ML Kit, because MediaPipe Tasks sends
usage statistics to Google through `com.google.android.datatransport` and ML Kit is not open source. Text models run with ONNX Runtime (MIT, no telemetry on Android), which BioCAP will use too; its arm64 library adds
about 34 MB to the app. LiteRT itself
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
the real files on every test run. Animals and fungi are in `kb/fauna/*.json`, checked by `FaunaFilesTest`: each
names its group, family, the states it is native to (or `introduced`), and a hazard only with a note saying what to
do. A rule names its states and links the official page it comes from; check the number or address on that page
before adding it, because summaries elsewhere are often out of date.

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
- Every write goes through `GardenRepository`, which also updates the FTS4 search index and links, or through
  `BeeRepository` for bee records, which puts hives in the same index. Room supports FTS4 but not FTS5, so the index
  uses FTS4; queries are reduced to letters and digits with a prefix star on each word.

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
