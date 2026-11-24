# SewFar

A private, offline Android tracker for sewing projects: what you are sewing, how long it takes, which pattern and
machine settings you used, and how it turned out.

The app is in English and German, following the phone's language or Android's per-app language setting.
The full specification is in [SPEC.md](SPEC.md), later plans in [ROADMAP.md](ROADMAP.md).

## Features

- **Projects** on one page: status (planned, in progress, done), a timer and manually added time, photos, patterns,
  who it is for, machine settings, notes, and a rating with "what I learned" when done.
- **Quick start:** "New project" opens the project directly; patterns, people, machines and settings are created
  right from the project by typing a name.
- **Library** (from the menu): patterns with photos, people, and machines with their reusable settings (stitch,
  tensions, differential feed …), each with the projects that used them.
- **Backup:** zip export/import, optional weekly automatic backup to a folder of your choice.

## Privacy

The app has no network access: the `INTERNET` permission is removed from the manifest, and there is no analytics,
crash reporting or Android cloud backup. All data stays on the device until you export a backup yourself.

## Building

Requires the Android SDK (compileSdk 37) and a JDK 17+ (e.g. Android Studio's bundled JBR).

```sh
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit and UI tests (JVM + Robolectric)
./gradlew assembleRelease      # release APK, signed if ~/.config/sewfar/keystore.properties exists
```

Store texts and screenshots (English and German) are in `fastlane/metadata/android/`.

## Licence

GNU General Public License v3.0, see [LICENSE](LICENSE).
