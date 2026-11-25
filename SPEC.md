# Thimbly — Specification

Thimbly is a private, offline Android tracker for sewing projects: what you are sewing, how long it takes, which
pattern and machine settings you used, and how it turned out.

**Guiding rule: keep it simple.** One home screen, one page per project, everything a project needs can be added
from the project itself. New features must not crowd the project page;
the data model stays generic so they can be added later (see [ROADMAP.md](ROADMAP.md)).

First designed on 2026-11-23; radically simplified on 2026-11-24 after using the first prototype (it had too many
screens, fields and tabs).

## 1. Platform & constraints

- Kotlin, Jetpack Compose + Material 3, Room, DataStore, WorkManager (auto-backup), kotlinx.serialization.
- `applicationId = "at.mtr.sewfar"`, minSdk 34, targetSdk/compileSdk 37. Licence GPLv3.
- Languages: English (`values`) and standard German (`values-de`), following the system or Android's per-app
  language setting. Dates follow the locale.
- **Offline:** no `INTERNET` permission, no analytics, no crash reporting, `allowBackup=false`. Data leaves the
  device only through a backup the user exports (§8).
- **No runtime permissions:** camera via `TakePicture` (works without `CAMERA` only while the merged manifest does
  not declare it), gallery via the Photo Picker. A build check fails if the merged manifest contains `INTERNET` or
  `CAMERA`.
- **Sync-ready data:** UUID string keys; `createdAt`, `updatedAt`, `deletedAt` on every row. Deleted data is really
  removed after the Undo window; `deletion_log` remembers which rows were removed, so a future sync can pass
  deletions on (§6). Photo files are immutable.
- Theme: Material 3 with an own calm, textile-inspired colour scheme, light/dark following the system; a setting
  switches to dynamic colour.
- Screen changes use short (250 ms) slide transitions, also for the predictive back gesture; the default 700 ms fades
  feel sluggish.

## 2. Navigation

- **Home = project list** (§3.1). No bottom bar.
- Top bar: search, and a menu with **Settings**. At the bottom: **Library** and **+ New project**.
- **Library:** one screen with three tabs (Patterns, People, Machines; opens on Patterns), each a list with a "+" button. Patterns,
  machines, settings and people can also be created right from a project, so the Library is only needed to edit or
  delete them.
- **Creating:** "+" asks only for the name (dialog with Save), creates the record and opens its page.
- **Editing (2026-11-24):** real edits use an explicit form; state changes apply at once.
  - Texts (names, notes, a pattern's designer, a setting's values) are shown read-only. ✎ opens the edit form (or a
    text dialog for a project's name, size, fabric, notes and "What I learned"); **Save** stores, ✕ or back discards, asking
    "Discard changes?" first if something was changed.
  - Status, timer, photos, linking or unlinking patterns, person and settings, stars and a machine's type apply
    immediately.
  - Every text field has its label fixed above it (as on the read-only page), never floating inside, so fields
    keep their size when focused. Search fields have only a placeholder.
- Pickers (pattern, person, machine setting, machine) have a search field and a "Create “…”" row that creates the
  item from the typed name.
- Back: the system back gesture or the arrow in the top bar.

## 3. Projects

### 3.1 List and creation

- Sections **In progress, Planned, Done** (collapsible; Done starts collapsed). Inside a section the project with a
  running timer comes first, then by last activity (any change to the project or its links, time or photos).
- A card shows the cover photo, name, first pattern and person, total time, and a start/stop timer button.
- Search matches name, person and pattern names.
- "New project" asks for the name, creates a Planned project and opens it.

### 3.2 Project page

One scrolling page. The top bar shows the name with ✎ (rename dialog) and delete (with Undo).

1. Status: Planned · In progress · Done (segmented buttons).
2. Time (§3.3).
3. Photos (§3.5).
4. Patterns (chips; tap opens the pattern, × removes, "+ Pattern" picks or creates). Below them **Size** (one line,
   e.g. "122", "M", "38/40") and **Fabric** (free text, e.g. "1,5 m jersey, 150 cm wide; ribbing 30 cm"), each shown
   like notes: "Add size" / "Add fabric" or ✎ opens a text dialog.
5. Sewn for (one person, same chip pattern). Below it, read-only, the person's measurements (§4.2) headed
   "Measurements from <date>": the newest ones, or once Done, the newest ones measured on or before the finish date
   (none if there are none). Tap opens the person.
6. Machine settings (chips "Setting · Machine"; tap opens it, × unlinks, "+ Machine setting" picks or creates one,
   §5.2).
7. Notes (shown as text; "Add notes" or ✎ opens a text dialog).
8. When Done: rating (1–5 stars, tap the same star again to clear) and "What I learned" (like notes).
9. Started / finished dates (read-only).

Removing a link, the person or a time entry offers Undo.

**Status effects:** In progress or Done sets the start date if missing; Done sets the finish date and stops a running
timer; leaving Done clears the finish date.

### 3.3 Time tracking

- Start/stop on the project page and on the list card; a Done project shows no Start (time can still be added by
  hand). Only one timer runs at a time: starting one stops the other.
  Starting the timer moves a Planned project to In progress. Deleting a project stops its timer first, so
  Undo never brings back a second running timer.
- "+" adds time by hand: duration (`45`, `1:30`, `1.5h`, `1,5 h`, `20 min`) and a day (stored as starting at noon).
- Total = all entries, the running one counted up to now. "Show entries" lists them (day, duration) for deletion;
  the running entry has no remove button (stop the timer first), for the same Undo reason.

### 3.4 Completion

Setting a project to Done opens a dialog asking for the rating and "What I learned" (**Save** or **Later**; typed
text is not dropped without asking). Both stay editable on the project page while it is Done.

### 3.5 Photos

- Projects and patterns have any number of photos. The photo row shows the newest photo first and ends with one "Add
  photo" tile, which offers "Take photo" or "From gallery". Tap opens the full-screen viewer (swipe, pinch zoom); long-press offers "Use as cover" (project)
  and delete (with Undo).
- Cover: the pinned photo, otherwise the newest one.
- Pipeline: decode, apply EXIF orientation, downscale to at most 2048 px on the long edge (4096 px
  with the "Fine photos" setting, §9; photos already stored keep their size), re-encode as JPEG 85 (drops
  all EXIF, including location), store as `filesDir/photos/<photoId>.jpg`. The row is inserted after the file is
  written. A deleted photo's file is removed with its row after the 60 s Undo window (§6); files without a row
  (e.g. after a crash) are removed once older than that window.

## 4. Library: patterns and people

### 4.1 Patterns

Name, designer, notes, photos, and "Used in" (projects linking it). Deleting a pattern removes it from its projects
(with Undo).

### 4.2 People

Name, measurements, notes, "Used in". A deleted person no longer shows on projects.

**Measurements (2026-11-25):** a fixed set: height, bust / chest, waist, hips, back length, arm length, inside leg;
in cm, numbers only (comma or dot), the field shows "cm". The page shows the filled ones two per row under
"Measured on <date>", or "Not measured yet". They are edited in the person's form (✎, Save is disabled while a value
is not a number between 1 and 300). Saving changed values stores today's measuring: a measuring from today is
corrected, otherwise a new one is added and older ones stay hidden, so finished projects keep showing what they were
sewn with. Unchanged values store nothing.

## 5. Machines and machine settings

### 5.1 Machines

Name, type (sewing machine, overlocker, coverstitch, embroidery, other), notes, and the list of its settings with
"+ New setting" (asks for the name, then opens the setting's form). Deleting a machine deletes its settings and their project links (with Undo).

### 5.2 Machine settings ("setups")

- A reusable, named setting of one machine, linked to any number of projects. Editing it changes it everywhere (no
  per-project copies).
- Fields come from the machine type (keys in `MachineTemplates`, stored in `setup_value.key`):
  - sewing machine: stitch, stitch length, stitch width, needle tension, foot, needle
  - overlocker: threads, tension left/right needle, tension upper/lower looper, differential feed, stitch length,
    cutting width
  - coverstitch: needles used, needle tension, looper tension, differential feed, stitch length
  - embroidery: hoop, needle, stabilizer
  - other: none (notes only)
  Values are free text; a blank value is removed. Values of fields the current type doesn't list are kept, not shown.
- The setting page shows the machine (tap opens it) and its type, which can be changed right there, e.g. after
  creating the machine from a project.
- Plus notes and "Used in".
- From a project: "+ Machine setting" → pick a setting, or type a name and "New setting “…”". With exactly one
  machine it is used directly; otherwise a machine picker follows (with "Create “…”" for a new machine). The new
  setting is linked and its page opens with the form, ready for the values.

## 6. Delete and undo

- Delete = soft delete with cascade; the snackbar offers Undo, which restores exactly the rows of that deletion.
  The Undo snackbar closes after at most 50 s (also with long accessibility timeouts), before the purge.
  A deletion never contains a running timer (§3.3), so Undo needs no further checks.
- **Purge (2026-11-25):** 60 s after a deletion (and on every start) rows deleted longer ago are removed for good,
  children first, together with any row that still refers to them through the cascade. Each removed row is recorded
  in `deletion_log` (table, row ID, deletion time; never pruned). A deleted person's projects stay and lose the
  person. Re-adding a removed link or value within the window revives its row.
- Cascade: person → measurements → their values; project → pattern links, setting links, time entries, photos;
  pattern → its project links and photos;
  machine → settings; setting → values and project links.

## 7. Data model (Room)

Every table: `id` (UUID text), `createdAt`, `updatedAt`, `deletedAt`. Unique indexes span deleted rows (revival).
Exception: `deletion_log` (rowId, tableName, deletedAt), see §6.

- `person` (name, notes)
- `measurement` (personId, measuredOn) and `measurement_value` (measurementId, key, valueCm; unique pair)
- `pattern` (name, designer, notes)
- `project` (name, status, personId?, size, fabric, notes, startedOn?, finishedOn?, rating?, learned, coverPhotoId?, lastActivityAt)
- `project_pattern` (projectId, patternId, sortOrder; unique pair)
- `time_entry` (projectId, startedAt, endedAt? — null = running)
- `photo` (ownerType PROJECT|PATTERN, ownerId, sortOrder)
- `machine` (name, type, notes)
- `setup` (machineId, title, notes)
- `setup_value` (setupId, key, value; unique pair)
- `project_setup` (projectId, setupId, sortOrder; unique pair)

The database file is `sewfar2.db`; the incompatible `sewfar.db` of the 1.0 prototype is deleted on start.
Enums are stored by name; never rename or remove an entry. Schema version 1 is frozen since v0.1
(`SchemaFreezeTest`); every change needs a new version, a migration and a migration test.

## 8. Backup

### 8.1 Format

A zip with `backup.json` (`{ "format": 1, "appVersion", "exportedAt", "tables": { "<table>": [rows] } }`, read in
one transaction) and `photos/<photoId>.jpg` for every live photo. Tables hold live rows only (rows inside the Undo
window are left out, references to them cleared) plus `deletion_log`; importing an older backup drops its deleted
rows the same way. Preferences are not
included. Format 1 is frozen since v0.1; every app version reads every older format; each format has a fixture in
`src/test/resources/backup/format<n>.zip`. Backups of the discarded 1.0 prototype (also "format 1", but other tables)
are rejected with their own message.

### 8.2 Export, import, automatic backup

- **Export:** save via the system dialog (`thimbly-backup-YYYY-MM-DD-HHmmss.zip`) or share.
- **Import replaces all data:** reject newer formats; only flat `photos/<id>.jpg` entries are
  extracted, other entries are ignored (no zip slip); validate one `backup.json`; confirm with counts;
  write a safety export (restorable via "Undo last restore"); extract photos to `photos.new`; replace all tables in
  one transaction; swap the photo folders, guarded by a marker file so an interrupted swap is completed or rolled back
  on the next start. Photo rows without a file are accepted.
- **Automatic backup:** optional, weekly, into a folder the user picks once (`thimbly-auto-*.zip`, newest 5 kept,
  nothing else touched). Errors (e.g. folder gone) are shown in Settings with "Choose folder again".

## 9. Settings

Appearance (dynamic colour), Photos ("Fine photos": new photos at up to 4096 px, §3.5), Backup (§8.2, plus the note that switching phones needs a manual backup), About
(version, licence).

## 10. Quality

- Unit tests (JVM + Robolectric): status dates, timer (one running, Done stops it), manual time, link revival,
  discarding empty new records, cascade and undo, photo pipeline and cleanup, backup round trip / fixture /
  rejections / interrupted swaps, auto-backup pruning, duration parsing, search.
- Compose UI tests: navigation, creating a project with a pattern and a machine setting (new machine) inline, timer,
  delete and undo, discarding an empty new project, German strings.
- Every string exists in both languages (`StringsCompletenessTest`).
