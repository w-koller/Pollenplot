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
wide-window versions. Free disk space is short on this machine, so model exports and evaluations run in GitHub
Actions, and the large models are never downloaded here.

CI (`.github/workflows/android.yml`) runs the same tasks, checks that `strings.xml` matches `tools/strings_*.py`, and
uploads the debug APK, reports and screenshots. It builds a signed release APK when the `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets are set (the build reads them as `PP_*` environment
variables). A `v*` tag that matches `versionName` publishes the APK and its checksum as a GitHub Release, with the
notes from that version's section of `CHANGELOG.md`. Keystores live outside the repo and must never be committed.

## Layout

- `domain/`: every rule and calculation, with no Android code. Every rule change needs a test here.
  - `season/`: hemispheres, seasons and `MonthSet`.
- `app/data`: Room records and DataStore settings (`data/prefs/SettingsStore`).
- `app/ui`: Compose screens. `ui/components` holds the shared pieces, `ui/nav` the routes and the app frame.
- `AppContainer` builds the shared objects by hand. There is no dependency-injection library.

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
