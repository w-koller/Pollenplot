# Pollenplot

**Ecosystem Management for Your Backyard.**

Pollenplot is an Android app for recording everything about a backyard garden. It identifies plants and animals on
the phone, so photos never leave it, and it shows how the garden's plants, wildlife, bees and watering fit together.
It is written for Australian gardens first.

Version 1 includes:

- **Plant and wildlife identification** with on-device AI (AIY, BioCAP and Gemma 4), from the camera or from your
  photos, which are scanned in the background.
- **The Plot**, a canvas of your yard's zones with each plant's cut-out photo and badges for its role, such as
  pollinator magnet, high thirst or frog safe.
- **Plant requirements**: sun, water, fertiliser, compost, wildlife value, ground cover and companion plants.
- **A weed identifier** that checks plants against official weed sources for your area (Brisbane City Council,
  Weeds Australia and the Atlas of Living Australia).
- **Pollen Rhythms**, a nectar and pollen calendar with forage gaps for honey bees and native bees.
- **Bees**: separate guides and trackers for European honey bees and Australian native bees, and a logger for bee
  and insect hotels.
- **Water**: irrigation options, and watering times and amounts from your plants and the weather.
- **Search by meaning** across all your records, and garden reports as PDF or Excel.

## Building

```bash
export JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/android-sdk
./gradlew :domain:test :ml:test :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. It is a separate app from the release, with its own
records, so it is for testing only.

## Releases

Signed APKs are built by GitHub Actions and published as GitHub Releases, each with its SHA-256 checksum. To publish
one, set `versionCode` and `versionName` in `app/build.gradle.kts`, give the version a section headed `## <version>`
in `CHANGELOG.md`, merge it into `main` and push a tag such as `v1.0.0`. A version with a suffix, such as
`1.0.0-rc.1` and the tag `v1.0.0-rc.1`, is published as a pre-release for testing, which does not become the latest
release.

The release signing key is kept outside the repository and reaches CI as four repository secrets: `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Please keep a backup of the key, because without it a new version
cannot be installed over the old one.

## Licence

Pollenplot is released under the GNU General Public License, version 3. The models and data it uses keep their own
licences, which are listed in the app under About › Licences and About › Data sources.
