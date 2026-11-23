# SewFar

A private, offline Android diary for sewing projects: what you sewed, for whom, with which pattern, what you
learned, and which machine settings worked.

The app is in English and German, following the phone's language or Android's per-app language setting.
The full specification is in [SPEC.md](SPEC.md), later plans in [ROADMAP.md](ROADMAP.md).

**Status:** early development (milestone M1.1: app skeleton).

## Planned for v1

- **Projects:** status (idea → planned → in progress → paused → done), person, patterns with the size sewn,
  fabrics, notes, tagged photos, a dated progress log, and a completion step with lessons learned and a rating.
  "Sew again" starts a new project and shows what you learned last time.
- **Patterns:** designer, category, sizes, format, shop link, suited fabrics, yardage per size; every project and
  lesson for that pattern in one place.
- **People:** who you sew for, with dated measurement history.
- **Machines & setups:** your machines and reusable settings (stitch, tension, differential feed …) per purpose and
  fabric, linked to the projects that used them.
- **Backup:** zip export/import, optional weekly automatic backup to a folder of your choice.

## Privacy

The app has no network access: the `INTERNET` permission is removed from the manifest, and there is no analytics,
crash reporting or Android cloud backup. All data stays on the device until you export a backup yourself.

## Building

Requires the Android SDK (compileSdk 37) and a JDK 17+ (e.g. Android Studio's bundled JBR).

```sh
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit and UI tests (JVM + Robolectric)
```

## Licence

GNU General Public License v3.0, see [LICENSE](LICENSE).
