# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

SewFar is a private, fully offline Android sewing project tracker (Kotlin, Jetpack Compose, Room, DataStore).
**`SPEC.md` is the source of truth** for behaviour, data model, backup format and wording; code comments cite it as
"SPEC 3.2" etc. Update SPEC.md alongside behaviour changes.

**Keep it simple** (the user's explicit wish after a cluttered first prototype): no feature or field that wasn't asked
for, no dialog before typing, everything a project needs is reachable from the project page. Prefer one screen over
two and keep the data model generic enough for later additions (ROADMAP.md).

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
- **Layers**: `data/` (Room entities/DAOs in `data/db`, repositories, DataStore `SettingsRepository`),
  `domain/` (pure Kotlin, no Android deps), `ui/` (`projects/` = home list + project page, `library/` = patterns,
  people, machines, settings ("setups"); navigation in `ui/SewFarRoot.kt`, theme in `ui/theme/Theme.kt`).
- Navigation: no bottom bar; the project list is home, Library and Settings come from its menu. Keep the short slide
  transitions in `SewFarRoot` (the 700 ms default fades made the app feel laggy; same fix as in LogMyGut).
- **Data rules** (SPEC 1, 6, 7): UUID string keys. Rows are never hard-deleted: delete through `SoftDeleter`, which
  cascades per the SPEC 6 list and returns a `DeletionToken` for undo; add any new table to `AllTables` and, if it
  has a parent, to the `cascade` map. Re-adding a removed link/value revives its tombstone (unique indexes span
  deleted rows). Child-row changes must call `ProjectDao.touch` (`lastActivityAt`). Status changes only via
  `ProjectRepository.setStatus`.
- **New records** (SPEC 2): "+" creates the row and opens its page with `isNew = true`; the name lives in the
  ViewModel (`EditableName`), and `onCleared` flushes it and, only for `isNew`, calls the repository's
  `discardIfEmpty`. All writes of a detail ViewModel go through its `SerialWriter` (ordered; `launchLast` runs the
  flush/discard after the fields' last saves) — never launch writes directly in `appScope` there. Library detail
  ViewModels extend `RecordViewModel`, screens use `RecordScreen`.
- **Frozen formats** (SPEC 7, 8): database schema 1 and backup format 1 are released. A schema change needs a new
  `SewFarDatabase.VERSION`, a migration in `SewFarDatabase.MIGRATIONS` and a migration test (`SchemaFreezeTest`
  checks the frozen v1 hash and that every version step has an exported schema and a migration); a
  backup format change needs a new `BackupRepository.FORMAT`, reading of all older formats, and a new fixture.
- Raw SQL writes (`openHelper.writableDatabase.execSQL`) only inside `db.withTransaction`, so they run off the main
  thread and Room observers are notified.
- **Photos** (SPEC 3.5): `PhotoRepository` stores the processed file first (`PhotoFiles`, `ImageProcessor`: EXIF
  orientation, ≤ 2048 px, JPEG 85, no EXIF), then inserts the row. Files are immutable; replacing = new row.
  Files of deleted photos and old orphan files are purged after `UndoWindowMillis` (on start and after deletions).
  UI: `rememberTakePhoto` (TakePicture → cache temp file via FileProvider, no CAMERA permission),
  `rememberPickPhotos` (Photo Picker), `PhotoImage` (via `LocalPhotoLoader`), `PhotoStrip`, `PhotoViewerRoute`.
  Photo tests need `@GraphicsMode(NATIVE)`.
- **UI conventions**: ViewModels are created with `viewModel { XViewModel(container) }`; their writes run in
  `container.appScope` (via `SerialWriter` on detail screens) so edits saved while leaving a screen survive. In-place editing uses `AutoSaveTextField`.
  Snackbars (incl. Undo) go through `LocalMessages`. Pickers are `EntityPicker` (search + "Create …" from the typed
  name, no extra form); show only one dialog at a time. After a suspend call, navigate inside `withContext(Dispatchers.Main)`.
- Tests use `TestDb` (in-memory Room + `FakeTime`) under Robolectric. UI flow tests keep Robolectric's default screen
  (a larger `w…dp-h…dp` qualifier made dialogs with text fields never go idle) and reach off-screen controls with
  `performScrollTo()`.
