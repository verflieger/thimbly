# SewFar — Specification v1

SewFar is a private, offline Android diary for sewing projects: what you sewed, for whom, with which pattern, what
you learned, and which machine settings worked. It focuses on project tracking with a simple, consistent UI.

Decided in a design interview on 2026-11-23, revised after a spec review the same day. Later ideas live in
[ROADMAP.md](ROADMAP.md).

## 1. Platform & constraints

- Kotlin, Jetpack Compose + Material 3, Room (SQLite), DataStore (preferences), WorkManager (auto-backup),
  kotlinx.serialization (backup JSON). Same toolchain and version catalog conventions as LogMyGut.
- `applicationId = "at.mtr.sewfar"`, minSdk 34, targetSdk/compileSdk 37. Licence GPLv3.
- Languages: English (`values`) and standard German (`values-de`). The app follows the system language; Android's
  per-app language setting works via `locales_config.xml`. No in-app language picker. Dates, decimal separators
  (`0.8 m` / `0,8 m`) and number input follow the active locale.
- **Offline:** no `INTERNET` permission, no analytics, no crash reporting. Android backup is disabled
  (`allowBackup=false`, data extraction rules exclude everything). This also blocks device-to-device transfer; the
  backup screen says that switching phones needs a manual export (§9). Data leaves the device only through a backup
  the user exports. Opening a pattern's shop link hands the URL to the browser via an intent (needs no permission).
- **No runtime permissions** in v1: camera via `ActivityResultContracts.TakePicture`, gallery via the system Photo
  Picker, no notifications. `TakePicture` works without the `CAMERA` permission only while the merged manifest does
  not declare it, so a build check fails if the merged manifest contains `CAMERA` or `INTERNET` (§12).
- **Sync-ready data model** (sync itself is not in v1):
  - Every record has a UUID primary key (string), `createdAt`, `updatedAt` (epoch millis UTC, bumped on every
    change) and `deletedAt` (soft delete, `null` = live). All queries filter out deleted rows. Rows are never
    hard-deleted.
  - Built-in seed rows get **deterministic IDs**: UUIDv5 of `"<table>:<builtinKey>"`, so two devices seed identical
    IDs.
  - Re-adding something that was removed (a lesson tag toggled on again, a measurement value re-entered) **revives
    the tombstone** (clears `deletedAt`) instead of inserting a new row.
  - Photo files are immutable; replacing a photo creates a new photo row and file.
- **Built-in list entries** (categories, purposes, lesson tags, measurement types, machine fields) carry a stable
  `builtinKey` that maps to a string resource, so they appear in the current language. User-created entries carry
  `customName` instead. Renaming a built-in entry sets `customName`, which then wins in every language. Hidden entries
  are not offered in pickers but are still shown wherever they are already used.
- Theme: Material 3 with an own calm, textile-inspired colour scheme, light/dark following the system. A setting
  switches to Material You dynamic colour.

## 2. Navigation

Bottom bar with four tabs; the app opens on **Projects**. Settings sit behind a gear icon in the top bar of every tab.

| Tab | Content | FAB "+" creates |
|---|---|---|
| Projects | Project list (§3.1) | a project |
| Patterns | Pattern library (§4) | a pattern |
| People | Persons and their measurements (§5) | a person |
| Machines | Machines and setups (§6) | a setup (machines are added from the machine row) |

**No dead ends:** every picker (pattern, person, machine, setup, category, purpose, lesson tag) has a search field
and a "Create new …" row that opens a short create form and returns with the new item selected, so an empty
library never blocks creating a project.

System back (gesture or button) is the only back navigation; there are no "← Back" text buttons.

## 3. Projects

### 3.1 Project list

- Default view: sections by status in this order: **In progress, Planned, Paused, Idea, Done**. This order is
  deliberate (active work first); pickers and menus use the order of §3.2. Sections are collapsible (state
  remembered); Done is collapsed by default. Within a section, sorted by `lastActivityAt` (newest first).
- `project.lastActivityAt` is updated on any change to the project or its child rows (patterns, fabrics, lessons,
  log entries, photos, setup uses).
- Card: cover photo (or placeholder with the type icon), name, person (avatar + name), pattern name(s), size(s).
- Toggle in the top bar: **gallery view** = photo grid of finished projects (cover photos), newest finish first.
- Search (name, notes, pattern name, designer, person, fabric text) and filters: person, pattern, type, year
  (finish year for Done, else start year, else creation year). Active filters show as removable chips.

### 3.2 Status

Fixed set, same names in every screen: **Idea, Planned, In progress, Paused, Done**. A new project defaults to
Planned. Any status can change to any other.

Automatic effects (all dates stay editable):

| Change to | Effect |
|---|---|
| In progress | `startedOn` = today if empty |
| Done | `finishedOn` = today if empty; opens the completion sheet (§3.5) |
| away from Done | `finishedOn` is cleared; lessons, rating and completion notes are kept |

Every status change writes a log entry (§3.4).

### 3.3 Project detail / edit

Fields:

- **Name** (required)
- **Type:** Sewing, Upcycling, Repair, Alteration (required, default Sewing)
- **Status** (required, §3.2)
- **Person** (optional, one; picker with create-new)
- **Patterns:** zero or more, each pattern at most once per project. Each link has its own **size sewn** (free text,
  e.g. `110` or `M, +2 cm length`). If the project has no pattern, a single project-level **size** field is shown
  instead. When a pattern is added later, `project.size` is kept but hidden (shown again if all patterns are removed).
  Removing a pattern link soft-deletes the lessons attached to it.
- **Fabrics:** zero or more free-text lines, each `description` + optional amount in m (two decimals), e.g.
  "Jersey Ringel blau" / 0.8. (Becomes a link to the fabric stash in v2.)
- **Notes** (free text)
- **Dates:** started on, finished on (date pickers; auto-filled as in §3.2)
- **Photos** (§3.6)
- **Machine setups used** (§6.4, M2)
- **Lessons & rating:** shown when the project is Done or already has lessons, a rating or completion notes;
  editable whenever shown (§3.5)
- **Progress log** (§3.4)

Actions (overflow menu): Sew again (§3.7), Delete (§7).

Editing is in place on the detail screen (no long form embedded in the list). Creating a project asks only for name,
type, person and patterns; everything else is filled in on the detail screen.

### 3.4 Progress log (diary)

A timeline per project, newest first:

- **Note entries:** date/time (default now, editable), text (required), optional photos (tagged Progress; added to an
  entry once it exists), optional machine setups used (§6.4, M2).
- **Status entries:** written automatically on each status change ("Planned → In progress"). They cannot be edited
  but can be deleted.
- From a note entry (M2): "Save as setup" creates a new setup pre-filled from the entry's first linked setup
  snapshot, or empty if it has none (§6.3).

### 3.5 Completion sheet

Opens when a project changes to Done. Every part is optional. Lessons, rating and notes are staged and written on
"Save"; "Skip" (or system back) discards them. Photos taken in the sheet are stored right away and kept either way.

1. Finished photos: take or pick (tagged Finished).
2. **Lessons learned tags**, one tag row per linked pattern, plus a project-level row if there is no pattern or it
   already has lessons (set before a pattern was linked).
   Default tags: Fit was good, Size up, Size down, Lengthen, Shorten, Fabric suited well, Fabric unsuitable.
   Tags are an editable list (Settings → Lesson tags: add, rename, hide, reorder). A tag can be set at most once per
   row.
3. **Rating** 1–5 stars.
4. **Notes** ("what I'd do differently").

Lessons are stored on the project (per project-pattern link, or project-level), so they exist even without a
pattern. Each pattern's detail page lists the lessons from all projects that used it (§4.2).

### 3.6 Photos

- Any number per project. Each project photo has a tag: **Inspiration, Progress, Finished** (required for project
  photos). Photos added in a log entry are Progress and belong to that entry; photos added in the completion sheet
  are Finished.
- Cover: `project.coverPhotoId` = `null` means automatic: the first Finished photo, otherwise the first photo of any
  tag. The user can pin a different cover, which sets `coverPhotoId`. If the pinned photo is deleted, it falls back to
  automatic.
- Patterns, persons and machines have at most one photo each. All photos live in the `photo` table
  (`ownerType` + `ownerId`); entities have no separate photo column.
- Pipeline: the camera writes into a temp file in `cacheDir` (via FileProvider); gallery picks are read from the
  Photo Picker URI. Every photo is decoded, EXIF orientation applied, downscaled so the long edge is at most 2048 px,
  and re-encoded as JPEG (quality 85). Re-encoding drops all EXIF data (including location). Stored as
  `filesDir/photos/<photoId>.jpg`. The temp file is deleted after processing or on cancel.
- Full-screen viewer with pinch zoom and swipe between photos of the same project.

### 3.7 Sew again

On any project: creates a new project with status Planned, same type, person, patterns and sizes (editable).

- Name: "<base name> (n)", where base name is the name without a trailing " (number)" and n is one higher than the
  highest existing number for that base name (first copy gets "(2)").
- Archived patterns or an archived person are copied as links and shown marked "archived".
- The new project stores `sourceProjectId` and shows a **"Last time"** card at the top while it is not Done: rating,
  lessons tags per pattern, completion notes, and a link to the old project. If the source project was deleted, the
  card is hidden.

## 4. Patterns

### 4.1 Pattern list

List with photo, name, designer, category; search (name, designer, notes, suited fabrics) and filters
(category, format). Archived patterns are hidden unless "Show archived" is on.

### 4.2 Pattern detail

Fields: **name** (required), **designer**, **photo**, **category** (optional; editable list, defaults: Shirt/Top,
Sweater/Hoodie, Jacket/Coat, Dress, Skirt, Pants/Shorts, Underwear/Nightwear, Baby, Accessories, Bag, Home, Other),
**size range** (free text, e.g. `74–164`), **format** (optional: Paper, PDF, Magazine, Self-drafted), **source link**
(URL, opens in the browser), **suited fabrics** (free text), **yardage table** (rows: size, amount in m, fabric width
in cm, note), **notes**.

Read-only sections: **Projects** sewn with this pattern (with person and size), and **Lessons** aggregated from
them (tag counts, e.g. "Lengthen ×2", plus each project's completion notes). Only lessons attached to this pattern's
link count; project-level lessons (projects without a pattern, §3.5) are not attributed to any pattern.

Actions: Start project with this pattern, Archive / Delete (§7).

## 5. People

### 5.1 Person

Fields: **name** (required), **avatar colour** (required; chosen from a palette, default derived from the ID) and an
optional **photo** (shown instead of the colour), **notes**.

Detail page sections: latest measurements, measurement history, projects for this person.

### 5.2 Measurements

- Measurement types: built-in list (height, chest/bust, under-bust, waist, hip, inseam, outside leg, arm length,
  back length, shoulder width, neck, head, upper arm, thigh, calf, foot length) plus custom types. Settings →
  Measurement types: hide, reorder, rename, add. Values are entered in **cm** with one decimal and stored as integer
  millimetres.
- A **measurement session** has a date, values for any subset of types (at most one value per type) and an optional
  note.
- Person detail shows the latest value per type (with its date) and the history per type (list; a small sparkline is
  optional, no chart library).

## 6. Machines & setups (milestone M2)

### 6.1 Machines

Fields: **name** (required, e.g. "Pfaff Ambition"), **brand**, **model**, **type** (required: Sewing machine,
Overlocker, Coverstitch, Embroidery, Other), **photo**, **notes**, **last serviced on** (optional date).

### 6.2 Setting fields

When a machine is created, its field list is copied from the type template; the user can hide, reorder, rename and
add fields per machine. Changing a machine's type later does not change its existing fields. Values are stored as
**text** so dials like `4.5`, `A`, `auto` or `3–4` all work.

| Type | Template fields |
|---|---|
| Sewing machine | Stitch (no./name), Stitch length, Stitch width, Needle tension, Presser-foot pressure, Foot, Needle (type/size), Thread |
| Overlocker | Threads (2/3/4), Tension left needle, Tension right needle, Tension upper looper, Tension lower looper, Differential feed, Stitch length, Cutting width, Needle, Thread |
| Coverstitch | Needles used, Needle tension(s), Looper tension, Differential feed, Stitch length, Needle, Thread |
| Embroidery | Hoop, Needle, Thread, Stabilizer, Speed |
| Other | (empty) |

### 6.3 Setups

A setup is a reusable, named machine configuration:

- **machine** (required), **purpose** (optional; editable list, defaults: Seam, Hem, Topstitching, Buttonhole,
  Gathering, Edge finishing, Basting, Binding, Elastic, Decorative), **title** (required; default
  "<purpose> – <fabric>"), **fabric** (free text, e.g. "Jersey, 220 g/m², 2 layers"), **values** for the machine's
  visible fields (one per field), **result** (optional: Worked well, OK, Didn't work), **notes**.
- Machines tab: machine list at the top (horizontal cards with an "Add machine" card), below it all setups with
  search (title, fabric, notes) and filters (machine, purpose, result).
- Setup detail shows the projects that used it.

### 6.4 Using setups in projects (snapshot)

- A project and any progress log entry can link setups ("Used setups").
- Linking stores a **snapshot** of the setup's fields (`fieldId`, label, value) at that moment. Later edits to the
  setup do not change old projects. Deleting a setup does not delete its snapshots.
- **Diff:** fields are matched by `fieldId`. A label change alone is not a difference. Values that differ are shown
  as changed; fields hidden or deleted since the snapshot show as "removed", fields added since show as "added". If
  any difference exists, the project shows "Setup changed since" with the diff. If the setup was deleted, only the
  snapshot is shown (no diff, no write-back).
- A snapshot's values can be tweaked in the project (e.g. differential feed 1.3 this time). Tweaking edits the
  snapshot in place and sets `tweaked = true`. A tweaked snapshot offers "Update setup with these values" (writes the
  values back to the setup and refreshes `snapshotAt`) or "Save as new setup".

## 7. Archive, delete & undo

- **Archive instead of delete:** a pattern or person referenced by at least one live project, and a machine with at
  least one live setup that is used by a live `setup_use`, can only be **archived**: hidden from lists and pickers,
  still shown (marked "archived") wherever it is referenced. Unarchive from the item's detail page or via
  "Show archived".
- Everything else (unreferenced patterns, persons and machines; projects, log entries, photos, setups, measurement
  sessions, yardage rows, list entries) can be **deleted** (soft delete) with an Undo snackbar.
- **Cascade:** deleting a row soft-deletes its children with the same `deletedAt` value:

| Deleted | Also deleted |
|---|---|
| project | project_pattern, project_fabric, project_lesson, log_entry, its photos, setup_use |
| log_entry | its photos, its setup_use rows |
| project_pattern | its project_lesson rows |
| person | measurement_session → measurement_value, its photo |
| pattern | pattern_yardage, its photo |
| machine | machine_field, setup → setup_value, its photo |
| setup | setup_value (**not** setup_use: snapshots are kept) |

- **Undo** clears `deletedAt` on exactly the rows that carry the deletion's timestamp, so rows deleted earlier stay
  deleted.
- Photo files of deleted photos are removed from disk once the Undo window has passed (the row keeps its tombstone).
- A list entry (category, purpose, lesson tag, measurement type, machine field) that is in use can only be hidden,
  not deleted. Built-in list entries can never be deleted, only hidden (so seeding never has to resurrect them).

## 8. Data model (Room)

All tables have `id TEXT PRIMARY KEY` (UUID), `createdAt`, `updatedAt`, `deletedAt` unless noted. Enums are stored
as stable text codes. `?` marks nullable columns; everything else is required (text columns default to `""`).
Foreign keys are declared without ON DELETE actions (rows are never hard-deleted), and every FK column is indexed.
`photo.ownerId` is polymorphic and has no FK; `project.coverPhotoId` has none either (it would make project ↔ photo
circular). Dates without time (`startedOn`, `finishedOn`, session `date`,
`lastServicedOn`) are stored as ISO `yyyy-MM-dd` text. Amounts in m are stored as integer centimetres
(`amountCm`).

| Table | Columns (besides the common ones) | Unique (live rows) |
|---|---|---|
| `person` | name, avatarColor, notes, archivedAt? | |
| `measurement_type` | builtinKey?, customName?, sortOrder, hidden | builtinKey |
| `measurement_session` | personId, date, note | |
| `measurement_value` | sessionId, typeId, valueMm | sessionId + typeId |
| `pattern_category` | builtinKey?, customName?, sortOrder, hidden | builtinKey |
| `pattern` | name, designer, categoryId?, sizeRange, format?, sourceUrl, suitedFabrics, notes, archivedAt? | |
| `pattern_yardage` | patternId, size, amountCm, fabricWidthCm?, note, sortOrder | |
| `project` | name, type, status, personId?, size (used when no pattern), notes, startedOn?, finishedOn?, rating?, completionNotes, coverPhotoId? (null = automatic), sourceProjectId?, lastActivityAt | |
| `project_pattern` | projectId, patternId, sizeSewn, sortOrder | projectId + patternId |
| `project_fabric` | projectId, description, amountCm?, sortOrder | |
| `lesson_tag` | builtinKey?, customName?, sortOrder, hidden | builtinKey |
| `project_lesson` | projectId, projectPatternId? (null = project-level), tagId | projectId + projectPatternId + tagId |
| `log_entry` | projectId, at (timestamp), kind (NOTE/STATUS), text, fromStatus?, toStatus? | |
| `photo` | ownerType (PROJECT/PATTERN/PERSON/MACHINE), ownerId, logEntryId?, tag? (INSPIRATION/PROGRESS/FINISHED; required for PROJECT), sortOrder. File = `photos/<id>.jpg` | |
| `machine` | name, brand, model, type, notes, lastServicedOn?, archivedAt? | |
| `machine_field` | machineId, builtinKey?, customName?, sortOrder, hidden | machineId + builtinKey |
| `purpose` | builtinKey?, customName?, sortOrder, hidden | builtinKey |
| `setup` | machineId, purposeId?, title, fabric, result?, notes | |
| `setup_value` | setupId, fieldId, value | setupId + fieldId |
| `setup_use` | setupId, projectId, logEntryId?, snapshotAt, tweaked, snapshot (JSON: list of {fieldId, label, value}) | |

Room schema export is on. **Schema version 1 and backup format 1 are frozen** (v1.0 release; `SchemaFreezeTest` pins
schema 1's identity hash, `src/test/resources/backup/format1.zip` is the format-1 fixture). Every schema change raises
the version and ships a migration and a migration test; the database is never dropped.

## 9. Backup

### 9.1 Format

A zip file:

- `backup.json`: `{ "format": 1, "appVersion": "...", "exportedAt": "...", "tables": { "<table>": [ rows… ] } }`,
  including soft-deleted rows (needed for a future merge/sync). Written from one database read transaction.
- `photos/<photoId>.jpg` for every live photo row.
- DataStore preferences (theme, auto-backup folder, collapsed sections) are **not** included.

From v1.0 on, every app version reads every older backup format (migrate on import).

### 9.2 Export / import

- **Manual export:** Settings → Backup → Export, file `sewfar-backup-YYYY-MM-DD-HHmmss.zip`, saved via the system
  "create document" dialog or sent via the share sheet.
- **Automatic backup:** optional, weekly (WorkManager periodic work, no idle or charging constraint). The user picks a
  folder once (SAF tree URI, `takePersistableUriPermission`), e.g. a folder synced by Nextcloud or Syncthing.
  - Files are named `sewfar-auto-YYYY-MM-DD-HHmmss.zip`. After a successful run, the newest 5 files matching this
    pattern are kept and older ones matching it are deleted; other files in the folder are never touched.
  - If the permission was revoked or the folder no longer exists, the run fails and Settings shows the error with a
    "Choose folder again" action. Settings always show the last successful run.
- **Import = replace all** (merge comes with sync, see ROADMAP):
  1. Pick a zip. Reject entries with absolute paths or `..` (zip slip), and a `format` newer than the app supports.
  2. Validate: exactly one `backup.json`, it parses and has a known format. Photo rows whose file is missing are
     accepted (the photo shows as missing), so one lost file never makes later backups unrestorable.
  3. Confirm dialog with counts ("12 projects, 30 patterns … will replace your current data").
  4. Write a safety export of the current data to `filesDir/import-safety.zip` (kept until the next import;
     restorable from Settings).
  5. Extract photos to `filesDir/photos.new`.
  6. Replace all tables in one database transaction.
  7. Rename `photos` → `photos.old`, `photos.new` → `photos`, then delete `photos.old`. A marker file (written before
     step 6) flags a swap in progress; on the next app start an unfinished swap is completed if every live photo row
     of the committed database has its file in `photos.new`, otherwise rolled back (`photos.new` is dropped).
- Unknown entries in the zip are ignored; absolute paths, `..` segments and nested paths under `photos/` are rejected.

## 10. Settings

- Appearance: dynamic colour on/off.
- Lists: Pattern categories, Lesson tags, Measurement types, Setup purposes (each: add, rename, hide, reorder).
- Backup: save (file) or share, restore, "Undo last restore" (restores the safety export), automatic backup (on/off,
  folder, last run, last error, "Back up now", "Choose folder again"), note that switching phones needs a manual
  export.
- About: version, licence (GPLv3), source code link, open-source licences.

## 11. Out of scope for v1

Fabric stash, notions, shopping list, statistics, deadlines and reminders, costs, storing pattern PDFs, QR labels,
AI features, cloud sync and merge import, iOS, tablet-specific layouts (must work, not optimised), online lookups of
any kind.

## 12. Quality

- Unit tests:
  - repositories and list ordering (incl. `lastActivityAt`)
  - status side effects (§3.2)
  - cascade delete and undo, incl. setup_use surviving setup deletion (§7)
  - archive rules (§7)
  - deterministic seed IDs and tombstone revival (§1)
  - built-in vs `customName` display in both locales
  - lesson aggregation on the pattern page (§4.2)
  - sew again: naming, archived links, deleted source (§3.7)
  - snapshot diff: value change, label rename, hidden/added fields, deleted setup, write-back (§6.4)
  - locale-aware number parsing
- Room migration tests from every exported schema (from v1.0 on).
- Backup tests: export → import round trip gives identical tables and photos; every format fixture imports
  (`src/test/resources/backup/format<n>.zip`); corrupt zip, duplicate `backup.json`, zip-slip entry and newer format
  are rejected with no change to existing data; a failed commit keeps the old data; interrupted photo swaps are
  recovered on start; "Undo last restore" keeps the replaced data as the new safety export.
- Auto-backup worker tests (Robolectric/WorkManager test): pruning only touches `sewfar-auto-*`, revoked URI is
  reported.
- Photo file cleanup after the Undo window.
- Build check: merged release manifest contains neither `INTERNET` nor `CAMERA`.
- Compose UI tests for the main flows: create project with new pattern from picker, complete project with lessons,
  sew again, log entry with photo, create setup and link it.
- Both languages complete: a test fails if a string exists in `values` but not in `values-de`.
- Accessibility: content descriptions, 48 dp touch targets, works with font scale 200 %.

## 13. Milestones

**M1 – Diary core**
1. Project skeleton (Gradle catalog, theme, 4 tabs, EN/DE, locale config, manifest check without INTERNET/CAMERA).
2. Data model + repositories for persons, patterns, projects and list tables with deterministic built-in seed;
   soft delete, cascade, undo and archive rules (§7).
3. Photo pipeline (§3.6) and viewer.
4. Projects: list (sections, search, filters, gallery view), create, detail/edit, status logic, delete/undo UI.
5. Patterns and People incl. measurements and photos; pickers with create-new; archive UI.
6. Progress log, completion sheet, lessons on pattern page, sew again.
7. Backup: export/import + automatic backup.

**M2 – Machines & setups**
8. Machines with type templates and per-machine fields.
9. Setups library, setup detail.
10. Linking setups to projects/log entries with snapshots, diff and write-back; "Save as setup" from a log entry.

**Release v1.0** after M2 (done 2026-11-24): freeze schema v1 and backup format 1, fastlane metadata (en-US, de-DE), screenshots with
sample data, signed release APK.
