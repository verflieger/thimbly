# SewFar — Specification

SewFar is a private, offline Android tracker for sewing projects: what you are sewing, how long it takes, which
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
- **Sync-ready data:** UUID string keys; `createdAt`, `updatedAt`, `deletedAt` on every row; rows are never
  hard-deleted (soft delete, §6). Re-adding a removed link or value revives its old row. Photo files are immutable.
- Theme: Material 3 with an own calm, textile-inspired colour scheme, light/dark following the system; a setting
  switches to dynamic colour.
- Screen changes use short (250 ms) slide transitions, also for the predictive back gesture; the default 700 ms fades
  feel sluggish.

## 2. Navigation

- **Home = project list** (§3.1). No bottom bar.
- Top bar: search, and a menu with **Library** and **Settings**.
- **Library:** one screen with three tabs (Patterns, Machines, People), each a list with a "+" button. Patterns,
  machines, settings and people can also be created right from a project, so the Library is only needed to edit or
  delete them.
- **Creating:** "+" asks only for the name (dialog with Save), creates the record and opens its page.
- **Editing (2026-11-24):** real edits use an explicit form; state changes apply at once.
  - Texts (names, notes, a pattern's designer, a setting's values) are shown read-only. ✎ opens the edit form (or a
    text dialog for a project's name, notes and "What I learned"); **Save** stores, ✕ or back discards, asking
    "Discard changes?" first if something was changed.
  - Status, timer, photos, linking or unlinking patterns, person and settings, stars and a machine's type apply
    immediately.
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
4. Patterns (chips; tap opens the pattern, × removes, "+ Pattern" picks or creates).
5. Sewn for (one person, same chip pattern).
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
- Total = all entries, the running one counted up to now. "Show entries" lists them (day, duration) for deletion.

### 3.4 Completion

No separate completion screen: setting Done shows rating and "What I learned" on the project page.

### 3.5 Photos

- Projects and patterns have any number of photos. The photo row starts with one "Add photo" tile, which offers
  "Take photo" or "From gallery"; newest photo first. Tap opens the full-screen viewer (swipe, pinch zoom); long-press offers "Use as cover" (project)
  and delete (with Undo).
- Cover: the pinned photo, otherwise the newest one.
- Pipeline: decode, apply EXIF orientation, downscale to at most 2048 px on the long edge, re-encode as JPEG 85 (drops
  all EXIF, including location), store as `filesDir/photos/<photoId>.jpg`. The row is inserted after the file is
  written. Files of deleted photos and orphan files are removed after the 60 s Undo window (on start and after
  deletions).

## 4. Library: patterns and people

### 4.1 Patterns

Name, designer, notes, photos, and "Used in" (projects linking it). Deleting a pattern removes it from its projects
(with Undo).

### 4.2 People

Name, notes, "Used in". A deleted person no longer shows on projects.

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
- Cascade: project → pattern links, setting links, time entries, photos; pattern → its project links and photos;
  machine → settings; setting → values and project links.

## 7. Data model (Room)

Every table: `id` (UUID text), `createdAt`, `updatedAt`, `deletedAt`. Unique indexes span deleted rows (revival).

- `person` (name, notes)
- `pattern` (name, designer, notes)
- `project` (name, status, personId?, notes, startedOn?, finishedOn?, rating?, learned, coverPhotoId?, lastActivityAt)
- `project_pattern` (projectId, patternId, sortOrder; unique pair)
- `time_entry` (projectId, startedAt, endedAt? — null = running)
- `photo` (ownerType PROJECT|PATTERN, ownerId, sortOrder)
- `machine` (name, type, notes)
- `setup` (machineId, title, notes)
- `setup_value` (setupId, key, value; unique pair)
- `project_setup` (projectId, setupId, sortOrder; unique pair)

The database file is `sewfar2.db`; the incompatible `sewfar.db` of the 1.0 prototype is deleted on start.
Enums are stored by name; never rename or remove an entry. Schema version 1 is frozen once released
(`SchemaFreezeTest`); every later change needs a new version, a migration and a migration test.

## 8. Backup

### 8.1 Format

A zip with `backup.json` (`{ "format": 1, "appVersion", "exportedAt", "tables": { "<table>": [rows] } }`, including
soft-deleted rows, read in one transaction) and `photos/<photoId>.jpg` for every live photo. Preferences are not
included. Every app version reads every older format; each format has a fixture in
`src/test/resources/backup/format<n>.zip`. Backups of the discarded 1.0 prototype (also "format 1", but other tables)
are rejected with their own message.

### 8.2 Export, import, automatic backup

- **Export:** save via the system dialog (`sewfar-backup-YYYY-MM-DD-HHmmss.zip`) or share.
- **Import replaces all data:** reject zip slip and newer formats; validate one `backup.json`; confirm with counts;
  write a safety export (restorable via "Undo last restore"); extract photos to `photos.new`; replace all tables in
  one transaction; swap the photo folders, guarded by a marker file so an interrupted swap is completed or rolled back
  on the next start. Photo rows without a file are accepted.
- **Automatic backup:** optional, weekly, into a folder the user picks once (`sewfar-auto-*.zip`, newest 5 kept,
  nothing else touched). Errors (e.g. folder gone) are shown in Settings with "Choose folder again".

## 9. Settings

Appearance (dynamic colour), Backup (§8.2, plus the note that switching phones needs a manual backup), About
(version, licence).

## 10. Quality

- Unit tests (JVM + Robolectric): status dates, timer (one running, Done stops it), manual time, link revival,
  discarding empty new records, cascade and undo, photo pipeline and cleanup, backup round trip / fixture /
  rejections / interrupted swaps, auto-backup pruning, duration parsing, search.
- Compose UI tests: navigation, creating a project with a pattern and a machine setting (new machine) inline, timer,
  delete and undo, discarding an empty new project, German strings.
- Every string exists in both languages (`StringsCompletenessTest`).
