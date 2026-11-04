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
  Weeds Australia and the Atlas of Living Australia), with native alternatives from the Grow Me Instead booklets.
- **A wishlist** of plants to grow, including plants identified away from home, with whether each is native or a
  weed where you live.
- **Pollen Rhythms**, a nectar and pollen calendar with forage gaps for honey bees and native bees.
- **Bees**: separate guides and trackers for European honey bees and Australian native bees, and a logger for bee
  and insect hotels.
- **Water**: irrigation options, and watering times and amounts from your plants and the weather.
- **Search** across your zones, plants, sightings, hives, hotels and wishlist by their words, and by meaning once
  the optional text model is on the phone, and garden reports as PDF or Excel.

Pollenplot needs Android 12 or newer.

## Privacy

Photos are identified on the phone and never leave it, except in a backup file you save yourself. The app has no
account, no analytics and no advertising. It goes online only for:

- the weather forecast, from Open-Meteo;
- weed lists and plants and animals recorded near the garden, from Brisbane City Council and the Atlas of Living
  Australia;
- suburb search and the council area, from Open-Meteo and the Atlas of Living Australia;
- the optional models, when you choose to download them.

The garden's location is rounded to about 1 km before it is sent to any of these.

## Permissions

- **Camera**: to identify a plant or animal in front of you. Photos you add from the gallery come through the system
  photo picker and need no permission.
- **Photos and videos**: only when you turn on scanning the gallery in Settings, so new garden photos are looked at
  in the background. You can allow all photos or only the ones you choose.
- **Approximate location**: only when you ask the app to find the garden from where you are.
- **Notifications**: for hive checks, hot days ahead of stingless bee hives, watering due and finished scans.
- **Internet**: for the services listed under Privacy.

## Optional downloads

The quick identification models come with the app. These are downloaded only when you ask for them, and each file is
checked against its published SHA-256 checksum:

- **Accurate identification pack** (BioCAP), about 127 MB, for better plant identification.
- **Text model** (bge-small-en-v1.5), about 34 MB, for search by meaning.
- **Gemma 4 E2B**, about 2.6 GB, for second opinions on unsure finds and for answering questions about your garden.

## Known limits

- The knowledge base is written for Australia. Months follow the garden's hemisphere, but the plants, weed sources and
  state rules are Australian.
- Weed lists for a council come from Brisbane City Council's open data; elsewhere the app uses the national lists and
  links to the state's pages.
- Phone makers' battery savers can delay background scans and the morning reminders. Settings explains how to check
  this on your phone.
- Identification can be wrong. Please never eat a plant or handle an animal because the app named it.

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
