# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Thimbly (German UI: Fingerhut) is a private, fully offline Android sewing project tracker (Kotlin, Jetpack Compose,
Room, DataStore). The package `at.mtr.sewfar` and class names (`SewFarApp` …) keep the
working title SewFar; user-visible texts say Thimbly / Fingerhut.
**`SPEC.md` is the source of truth** for behaviour, data model, backup format and wording; code comments cite it as
"SPEC 3.2" etc. Update SPEC.md alongside behaviour changes.

**Keep it simple** (the user's explicit wish after a cluttered first prototype): no feature or field that wasn't asked
for, everything a project needs is reachable from the project page. Prefer one screen over
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
- **Data rules** (SPEC 1, 6, 7): UUID string keys. Delete through `SoftDeleter`, which stamps `deletedAt` on the row
  and its cascade (SPEC 6) and returns a `DeletionToken`; the UI passes it to `messages.showUndo(text, token)`,
  which undoes through `AppContainer.undo` (repositories have no `undo`). After the 60 s Undo window
  `SoftDeleter.purge` removes the rows for good and logs each in `deletion_log` (for a future sync); the app runs it
  on start and after each deletion. A deletion must never contain a running time entry (SPEC 3.3). Add any new table
  to `AllTables` (parents first) and each of its foreign keys to `Cascade` or `Detach`; `TableRegistryTest` checks
  this against the schema. Re-adding a removed link/value within the Undo window revives its row (unique indexes
  span deleted rows). Child-row changes must call `ProjectDao.touch` (`lastActivityAt`). Status changes only via
  `ProjectRepository.setStatus`.
- **Creating and editing** (SPEC 2): "+" asks for the name (`TextDialog`), creates the row, opens the page. Texts are
  shown read-only and edited in a form with Save (`DetailScaffold` editing mode; library pages via `RecordScreen`
  with a draft map) or a `TextDialog`; state changes (status, timer, links, photos, stars) are written at once.
- **Formats** (SPEC 7, 8): until the first real release (decided 2026-11-25: nobody uses the app yet), schema and
  backup changes edit version 1 / format 1 in place: update the hash in `SchemaFreezeTest` and, if needed, the
  fixture; installed test copies clear their data. From that release on they freeze again: a schema change needs a
  new `SewFarDatabase.VERSION`, a migration in `SewFarDatabase.MIGRATIONS` and a migration test; a backup format
  change needs a new `BackupRepository.FORMAT`, reading of all older formats, and a new fixture.
- Raw SQL writes (`openHelper.writableDatabase.execSQL`) only inside `db.withTransaction`, so they run off the main
  thread and Room observers are notified.
- **Photos** (SPEC 3.5): `PhotoRepository` stores the processed file first (`PhotoFiles`, `ImageProcessor`: EXIF
  orientation, ≤ 2048 px, JPEG 85, no EXIF), then inserts the row. Files are immutable; replacing = new row.
  The purge removes a deleted photo's row; `purgeFiles` then removes files without a row older than the Undo window.
  UI: `rememberTakePhoto` (TakePicture → cache temp file via FileProvider, no CAMERA permission),
  `rememberPickPhotos` (Photo Picker), `PhotoImage` (via `LocalPhotoLoader`), `PhotoStrip`, `PhotoViewerRoute`.
  Photo tests need `@GraphicsMode(NATIVE)`.
- **UI conventions**: ViewModels are created with `viewModel { XViewModel(container) }`; their writes run in
  `container.appScope` so edits saved while leaving a screen survive.
  Text inputs use `LabeledTextField`/`FormField` (label fixed above the field; tests find fields by tag, not label).
  Snackbars (incl. Undo) go through `LocalMessages`. Pickers are `EntityPicker` (search + "Create …" from the typed
  name, no extra form); show only one dialog at a time. After a suspend call, navigate inside `withContext(Dispatchers.Main)`.
- Tests use `TestDb` (in-memory Room + `FakeTime`) under Robolectric. UI flow tests keep Robolectric's default screen
  (a larger `w…dp-h…dp` qualifier made dialogs with text fields never go idle) and reach off-screen controls with
  `performScrollTo()`.
