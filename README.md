# Thimbly

**Thimbly remembers how you sewed something.** Other sewing apps list what you own; Thimbly records how each
project was made: the machine settings that worked (stitch, tensions, differential feed …), the pattern and size,
the fabric, how long it took, and the measurements of the person it was for at the time. Next time you sew that
pattern, it is all on one page.

It is a private, fully offline Android app in English and German, following the phone's language or Android's
per-app language setting. The full specification is in [SPEC.md](SPEC.md), later plans in [ROADMAP.md](ROADMAP.md).

## Features

- **One page per project:** status (planned, in progress, done), a timer and added or corrected time, photos,
  patterns with size and fabric, who it is for with their measurements at the time, machine settings, labels and notes.
- **Reusable machine settings:** a named setting per machine with the fields of its type (sewing machine,
  overlocker, coverstitch, embroidery), linked to every project that used it.
- **Quick start:** "New project" opens the project directly; patterns, people, machines and settings are created
  right from the project by typing a name.
- **Library** (button at the bottom of the project list): patterns grouped by type, with their photos and the
  photos of every project sewn from them; people with dated measurements; machines with their settings. Each shows
  the projects that used it.
- **Search** over names, people, patterns, labels, size, fabric and notes.
- **Backup:** zip export/import, optional weekly automatic backup to a folder of your choice. Every update keeps
  your data and reads all older backups.

## Privacy

The app has no network access: the `INTERNET` permission is removed from the manifest (a build check enforces it),
and there is no analytics, crash reporting, account or Android cloud backup. The camera and gallery work without
any permission. All data stays on the device until you export a backup yourself.

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
