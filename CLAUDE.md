# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

SewFar is a private, fully offline Android sewing project diary (Kotlin, Jetpack Compose, Room, DataStore).
**`SPEC.md` is the source of truth** for behaviour, data model, backup format and wording; code comments cite it as
"SPEC 3.2" etc. Update SPEC.md alongside behaviour changes. Milestones are in SPEC 13.

Code, identifiers and SPEC are English. The UI is **English** (default, `res/values/`) and **standard German**
(`res/values-de/`); every string needs both (`StringsCompletenessTest` enforces it).

Never mention other commercial sewing apps in committed files; this repo may be published.

## Commands

The system Java here is JRE-only; run Gradle with Android Studio's JBR:

```sh
export JAVA_HOME=~/.local/opt/android-studio/jbr
./gradlew assembleDebug                      # debug APK
./gradlew installDebug                       # install on attached phone / emulator
./gradlew testDebugUnitTest                  # all unit tests (JVM + Robolectric)
./gradlew testDebugUnitTest --tests 'at.mtr.sewfar.ui.NavigationTest'   # one class
```

There are no instrumented (`androidTest`) tests; UI tests use Compose test + Robolectric under `app/src/test`.
`check<Variant>Permissions` runs before every assemble and unit-test task and fails if the merged manifest declares
`INTERNET` or `CAMERA` (SPEC 1).

## Architecture

- **Manual DI**: `SewFarApp` creates one `AppContainer`; screens get dependencies from
  `(application as SewFarApp).container`. No Hilt.
- **Layers**: `data/` (Room entities/DAOs in `data/db`, repositories, built-in seed in `data/seed`, DataStore
  `SettingsRepository`),
  `domain/` (pure Kotlin, no Android deps), `ui/` (one package per tab; navigation and bottom bar in
  `ui/SewFarRoot.kt`, shared tab frame in `ui/common/TabScaffold.kt`, theme in `ui/theme/Theme.kt`).
- Tab screens use `TabScaffold`, which adds no window insets; the app shell's scaffold owns the bottom bar.
- **Data rules** (SPEC 1, 7, 8): UUID string keys; built-in list rows use `Ids.builtIn(table, key)` (UUIDv5 — never
  change the namespace or a key). Rows are never hard-deleted: delete through `SoftDeleter`, which cascades per the
  SPEC 7 table and returns a `DeletionToken` for undo; add any new child table to its `cascade` map and `AllTables`.
  Re-adding a removed link/value revives its tombstone (unique indexes span deleted rows). Child-row changes must call
  `ProjectDao.touch` (`lastActivityAt`). Status changes only via `ProjectRepository.setStatus`.
- Raw SQL writes (`openHelper.writableDatabase.execSQL`) only inside `db.withTransaction`, so they run off the main
  thread and Room observers are notified.
- Tests use `TestDb` (in-memory Room + `FakeTime`) under Robolectric.
