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

`AccessibilityFlowTest` walks the main screens of the demo yard. It fails when a pressable control has no words
for a screen reader (read from the semantics tree, because Google's Accessibility Test Framework sees nothing of
Compose under Robolectric), and with text at twice its size it saves each screen as `large_*.png`. Screen tests save
pictures to `app/build/screenshots/`. Look at them after UI changes: there are light, dark and
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
  - `semantic/`: IRIs, the vocabularies, `GardenGraph` (records as Darwin Core and schema.org triples), `HoneyGraph`
    (honey bee records, with mite checks as SOSA observations), `NativeBeeGraph`, `HotelGraph`, `JsonLd`,
    `DarwinCore` (sightings as a Darwin Core CSV) and `SearchText` (what the search index holds for each record).
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
    registration page in `BeekeeperRegistration` and the states that ask for more mite checks than the Code in
    `StateMiteChecks`. `HiveReminders` decides which due checks a notification tells
    about: each once while it stays due.
  - `bees/stingless/`: stingless bee hives and their logs (`StinglessBees`, named so because `native` is a Java
    keyword) and `HeatAlerts` (which hot days to warn about, from Cumberland City Council's guide). `weather/` holds
    `OpenMeteoForecast`, and `wildlife/NativeBeesSeen` the native bees seen in a year.
  - `hotels/`: `BeeHotels` (hotels, their sections, looks and upkeep), `SealGuide` (who a nest's seal says is likely
    inside) and `HotelCare` (a hotel worth cleaning out in spring).
  - `irrigation/`: `WaterPlanner` (a line's daily soil-water account, after FAO 56: when watering is due and how much,
    and with `week` the waterings the forecast holds), `WateringWindow` (the hours to water in, which the user sets),
    `Runoff` (a spray or sprinkler line faster than its soil's basic infiltration rate, from FAO's irrigation manual,
    and the cycle-and-soak split that the US EPA's WaterSense programme describes), `PotsBeforeHeat` (pot zones to
    water deeply the morning before a day of 35 °C or more, after UC Agriculture and Natural Resources; the
    temperature is Pollenplot's choice),
    `SoilWater` (readily available water by soil, from the Tasmanian Institute of Agriculture, and root depths,
    Pollenplot's estimates), `Pairing` (plants of very different thirst on one line) and the line and weather types.
    `weather/OpenMeteoForecast` makes the forecast and weather requests and reads their answers.
  - `rhythms/`: `RhythmsEngine` (each bee group's forage points and level per month, its gaps, and plants that would
    fill them; the measure is described in its KDoc and on the screen) and `RhythmsFeed` (this month's cards).
  - `guide/`: `Guide` and `GuideParser`, which reads a guide page (see Guides below).
  - `report/`: `Report` (sections of paragraphs and tables of text, numbers and days) and `XlsxWriter`, an Excel
    writer on `java.util.zip` alone. `app/report` builds a report from the records (`ReportBuilder`) and draws it on
    A4 pages through a `PageSink` (`PdfReportWriter`), so `ReportTest` draws the pages as pictures.
  - `backup/`: `BackupArchive` (a backup file is a zip of its manifest, the garden's settings, every record as
    JSON-LD, the database and the photos and cut-outs, and only `files/photos/` and `files/thumbs/` entries with plain
    names are restored) and `BackupSeal`, which encrypts a backup with a password as a stream: PBKDF2-HMAC-SHA256,
    then AES-256-GCM in 64 KiB segments (the STREAM construction, as Tink uses), with a password check in the header
    so a wrong password is told from a damaged file.
- `ml/`: pure-JVM model code that is checked on the JVM against reference outputs: the BERT `WordPieceTokenizer` and
  `TextEmbedder` (bge-small-en-v1.5 through ONNX Runtime). It compiles against the desktop ONNX Runtime API, which the
  Android runtime matches; the app brings the Android one. `TextEmbedderTest` compares tokens and vectors with
  `ml/src/test/resources/bge_reference.json`, made with Hugging Face's tokenizers and ONNX Runtime in Python.
- `app/data`: `GardenRepository` (records, detections, the search index and their triples, which
  `AppContainer.exportJsonLd` joins with `BeeRepository`'s into one JSON-LD document), `GardenViews` (records
  joined with the knowledge base for the screens), `PhotoAnalysis` (a photo through the identifier into storage and
  review), `BeeRepository` (the honey bee records), `NativeBeeRepository` (stingless bee hives and logs),
  `HotelRepository` (bee hotels and their records), `BeeViews` (hives and hotels with their zones, and what each
  honey bee hive is due for today), `RhythmsViews` (the Rhythms tab's lanes and suggestions), `WaterRepository`
  (watering lines, waterings recorded and the weather kept), `WaterViews` (each line's and zone's plan) and
  `TodayViews` (the Plot's Today card, from every part of the garden), `GuideLibrary` in `data/guides` (the guide pages from the assets), Room in `data/db`, photos and cut-outs in `data/photos`, the knowledge base loader in `data/kb` and
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
- `app/backup`: `BackupService` writes a backup (the database copied by SQLite's `VACUUM INTO`, and the photos and
  cut-outs its records show) and restores one in two steps. `read` checks the whole file and keeps it aside; it opens
  the database through Room, which brings an older backup up to date with `PollenplotDatabase.MIGRATIONS` and refuses
  one whose tables differ from the app's. `restore` replaces every table but the search index in one transaction, then
  the photo folders, and the container rebuilds the index. `BackupSettings` holds the settings that go with the garden;
  photo scanning, the notifications already shown and the models stay with the phone.
- `app/scan`: the phone's gallery behind the `Gallery` interface (`MediaStoreGallery`), `PhotoScanner` (gallery and
  added photos through the analysis, each gallery photo recorded in `scanned_media` so it is looked at once), and the
  WorkManager jobs: `AddPhotosWorker`, `LibraryScanWorker` (stops after eight minutes and queues the rest),
  `LibraryWatchWorker` (a content-URI trigger that re-arms itself) and `ScanScheduler`, which matches them to the
  settings. `app/notify` holds the notification channels and `HiveReminderWorker`, which runs at about seven each
  morning while there is something to remind or warn about: `HiveReminderCheck` for honey bee checks due,
  `HeatAlertCheck` for hot days ahead of stingless bee hives and `WaterPlanCheck` for watering due.
- `app/ui`: Compose screens. `ui/components` holds the shared pieces, `ui/nav` the routes and the app frame,
  `ui/plot` the canvas and zones, `ui/plants` plant pages and labels, `ui/search` search, `ui/identify` the camera
  (`IdentifyModel` keeps its state across turns), `ui/weeds` the Weed card, the Weeds screen, Compare and the Weed
  finder, `ui/wildlife` the Wildlife tab, an animal's page, a sighting's page and the safety notice, and `ui/review`
  the review cards (`AnimalCards` for animals), `PhotoReviewScreen` (shared by the Identify results and the Review
  queue) and `ReviewModel`, `ui/bees` the Bees tab and its three sections (`HoneySection`, `NativeSection`,
  `HotelSection`), each with its pages, editors and forms, and `ui/guides` a guide page (`GuideScreen`).
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
- `WaterTestApp` gives the container a `RecordedWeb`, whose recorded Open-Meteo weather covers the week before and
  after the fixed day of 28 October 2026, and that fixed clock.
- Screens that open the system's file picker are tested with a stand-in `ActivityResultRegistry` given through
  `LocalActivityResultRegistryOwner`, and the chosen URI's streams registered with `shadowOf(contentResolver)`, as in
  `BackupFlowTest`.
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

## Guides

The bee guides are Markdown pages in `app/src/main/assets/guides/<section>/` (honey, native, hotels), one topic per
file. Each starts with front matter: `title`, `summary` (a sentence), `section`, `order`, `lastChecked` and
`sources`, one per line as `  - Title | https://…`, and `states` (such as `qld`) for a page about one state's rules,
which only gardens there are shown. `GuideParser` accepts `## ` headings, paragraphs, `- ` bullets,
`1. ` steps, `> ` notes for what must not be missed, `**bold**`, `*italic*` (species names) and `[links](https://…)`,
and refuses anything else with its line. Every fact on a page must come from one of its sources; leave out what a
source does not say, and name the state for a state's rule. Set `lastChecked` to the day the page was checked
against its sources. `GuideFilesTest` in `:domain` reads every page on each test run.

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
  migration in `PollenplotDatabase.MIGRATIONS`, which restored backups go through too, and a schema committed in
  `app/schemas/`, and there is no destructive migration fallback. The read-only
  knowledge database is rebuilt from `app/src/main/assets/kb/` whenever its version changes.
- Garden locations sent to online services (weather, ALA) are rounded to about 1 km. Photos never leave the phone,
  except in a backup file the user saves.
- Every write goes through `GardenRepository`, which also updates the FTS4 search index and links, or through
  `BeeRepository`, `NativeBeeRepository` and `HotelRepository` for bee records, which put hives and hotels in the
  same index. Room supports FTS4 but not FTS5, so the index
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

Status is always shown with an icon and a word, never colour alone, and no action is gesture-only. With large text
(`largeText()` in `ui/components`, a font scale of 1.3 or more), a label moves above or below what it names rather
than being squeezed beside it, and `ConnectedChoice` stacks its options when a label would be cut short.

## Icons and licences

Icons are Material Symbols Rounded (Apache License 2.0). Run `python3 tools/fetch_icons.py <name> [<name>:fill]` to
add one as `res/drawable/ic_<name>.xml`. About › Licences reads `assets/licences/`: the build copies the app's
`LICENSE` and the licence files inside library archives listed in `licenceArchives` in `app/build.gradle.kts`, and
`app/src/main/assets/licences/` holds texts no archive carries. Please add a library to the list in
`ui/settings/AboutScreens.kt` when it is added to the app.
