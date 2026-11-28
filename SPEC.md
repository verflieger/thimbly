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
- Top bar: search and a **Settings** icon. At the bottom: **Library** and **+ New project**.
- **Library:** one screen with four tabs (Patterns, People, Machines, Purchases; opens on Patterns; the tab row scrolls
  if the labels don't fit), each a list with a "+" button. Patterns,
  machines, settings and people can also be created right from a project, so for them the Library is only needed to
  edit or delete them; purchases live only in the Library.
- **Creating (one rule since 2026-11-28):** "+" (and "Create “…”" in a picker) opens one "New …" dialog with the
  record's most important fields; only some of them are required, and Save waits for those. Today: projects, people
  and settings: the name (required); pattern: name (required) and type (optional chips); machine: name and type (both
  required, the type never changes); purchase: name, price, category and date (all required, category and date
  pre-filled). From "+", the new record's page opens, where the remaining fields (notes …) are added; from a project's
  picker the new record is linked and the project stays open (a new setting opens for its values, §5.2). Every
  record is edited on its page, the same way everywhere.
- **Deleting** a record: always the trash icon in its page's top bar, followed by the Undo snackbar (§6). Photos
  (viewer), links, tags and time entries are removed where they are shown, also with Undo.
- **Editing (2026-11-24, one way everywhere since 2026-11-25):** texts are edited in a dialog with Save; state
  changes apply at once.
  - Texts (names, notes, size, fabric, a pattern's designer) are shown read-only. Tapping one or its ✎ opens a text
    dialog; ✎ in the top bar renames. Only groups of values have a form: a person's measurements and a setting's
    values (✎ next to them; **Save** stores).
  - Nothing asks "Discard changes?": a tap outside a dialog does nothing, Cancel or back in a dialog discards; in a
    form ✕ discards and back saves (while the form can't be saved, e.g. a measurement is not a number, back does
    nothing).
  - Status, timer, photos, linking or unlinking patterns, person, settings and tags, and a machine's or pattern's
    type (on its page) apply immediately. A type is picked from its fixed list in a dialog; a tap picks.
  - Every text field has its label fixed above it (as on the read-only page), never floating inside, so fields
    keep their size when focused. Search fields have only a placeholder.
- Pickers (pattern, person, machine setting, machine, tag) have a search field and a "Create “…”" row that creates the
  item from the typed name, or opens its "New …" dialog (§2) with the name filled in when it has more fields (pattern,
  machine). A tap picks and closes; the tag picker picks several (checkboxes, Done).
- Back: the system back gesture or the arrow in the top bar.

## 3. Projects

### 3.1 List and creation

- Sections **In progress, Planned, Done** (collapsible; Done starts collapsed). Inside a section the project with a
  running timer comes first, then by last activity (any change to the project or its links, time or photos).
- A card shows the cover photo, name, first pattern and person, total time, and a start/stop timer button.
- Search matches name, person, pattern names, tags, size, fabric and notes. While the search field is empty it shows
  the tags and people of the projects as chips ("Labels", "People"); a tap searches for one, so filtering is the search
  (no separate filter row, 2026-11-27).
- "New project" asks for the name, creates a Planned project and opens it.

### 3.2 Project page

One scrolling page. The top bar shows the name with ✎ (rename dialog) and delete (with Undo).

1. Status: Planned · In progress · Done (segmented buttons).
2. Time (§3.3).
3. Photos (§3.5).
4. Patterns (chips "Pattern · Type", §4.1; tap opens the pattern, × removes, "+ Pattern" picks or creates). Below them **Size** (one line,
   e.g. "122", "M", "38/40") and **Fabric** (free text, e.g. "1,5 m jersey, 150 cm wide; ribbing 30 cm"), each shown
   like notes: "Add size" / "Add fabric" or ✎ opens a text dialog.
5. Sewn for (one person, same chip pattern). Below it, read-only, the person's measurements (§4.2) headed
   "Measurements from <date>": the newest ones, or once Done, the newest ones measured on or before the finish date
   (none if there are none). Tap opens the person.
6. Machine settings (chips "Setting · Machine"; tap opens it, × unlinks, "+ Machine setting" picks or creates one,
   §5.2).
7. Tags, shown as "Labels" (chips; × removes with Undo; "+ Label" opens the picker in its several-at-once mode: the
   labels in use and the starter suggestions Repair, Upcycling, Alteration, Gift, Costume, Toile, Quick sew,
   each with a checkbox. The ticked labels come first, then a line, then the others, both alphabetical (ticking moves
   a label above the line); every row has the same height. The search field
   says "Search or new label"; a label in use has ✎, which opens "Rename label" (in place of the picker, back to it
   after Save) and renames it on every project, merging with an existing label of that name; a tap ticks or unticks at once (no Undo: tick again), typing offers "Create “…”", Done closes; a tap
   on a chip does nothing). Tags have no list of
   their own: the names in use on any project are the tags, so an unused one disappears.
8. Notes (shown as text; "Add notes" or ✎ opens a text dialog).
9. Started / finished dates (read-only).

Sections 4–8 show only once filled (2026-11-25): empty ones fold into one "Add" row of chips below them (pattern,
size, fabric, person, machine setting, tag, notes), so a new project shows little more than status and photos. A filled
section keeps its own add chip where more can be linked (patterns, settings, tags).

Removing a link, the person, a tag or a time entry offers Undo.

**Status effects:** In progress or Done sets the start date if missing; Done sets the finish date and stops a running
timer; leaving Done clears the finish date.

### 3.3 Time tracking

- **Time tracking** can be switched off in Settings (§9; on by default). Off hides the Time section and the cards'
  timer button and total; a running timer still shows its Stop. Recorded entries stay (and stay in backups).
- Start/stop on the project page and on the list card; a Done project shows no Start (time can still be added by
  hand). Only one timer runs at a time: starting one stops the other.
  Starting the timer moves a Planned project to In progress. Deleting a project stops its timer first, so
  Undo never brings back a second running timer.
- "+" adds time by hand: duration (`45`, `1:30`, `1.5h`, `1,5 h`, `20 min`) and a day (stored as starting at noon).
- Total = all entries, the running one counted up to now. "Show entries" lists them (day, duration) for deletion;
  the running entry has no remove button (stop the timer first), for the same Undo reason.
- **Correcting (2026-11-26):** tapping a finished entry opens the same dialog as "+", pre-filled (`h:mm`, its day);
  Save replaces duration and day. The entry keeps its start time on the same day, otherwise it starts at noon of the
  new day.
- **Forgotten timer:** a timer that stopped after running 4 h or more, however it stopped (Stop, starting another
  timer, Done), opens that dialog on the current screen, titled "<project> – really <duration>?"
  and pre-filled with the measured time. Save corrects it, Cancel keeps it.

### 3.4 Completion

Setting a project to Done only changes the status: no dialog, no extra fields; what went well or wrong goes into the
notes (2026-11-25). The former rating and "What I learned" are no longer shown; text left in "What I learned" (also
from an imported older backup) is appended to the notes.

### 3.5 Photos

- Projects and patterns have any number of photos. The photo row and the viewer show the newest photo first (the order they were added) and the row ends with one "Add
  photo" tile, which offers "Take photo" or "From gallery". Tap opens the full-screen viewer (swipe, pinch zoom), whose top bar has "Use as cover"
  (projects and patterns; the star is filled while the shown photo is the pinned cover, tap again to choose automatically) and
  delete (with Undo); long-press in the row offers the same as a shortcut.
- Share (2026-11-26): the viewer's top bar also has share (every viewer, also "From projects", §4.1). A copy of the
  stored JPEG (`thimbly-<id prefix>.jpg`, at most 2048/4096 px, no EXIF) goes to the Android share sheet via the cache's
  share folder, like a shared backup (§8.2); no permission needed.
- Cover: the pinned photo, otherwise the newest one. A pattern's cover is the pinned photo, otherwise its oldest one,
  usually the envelope taken first (2026-11-26); it shows as a thumbnail in the Library pattern list and the pattern
  picker.
- Pipeline: decode, apply EXIF orientation, downscale to at most 2048 px on the long edge (4096 px
  with the "Fine photos" setting, §9; photos already stored keep their size), flatten transparency onto white, re-encode as JPEG 85 (drops
  all EXIF, including location), store as `filesDir/photos/<photoId>.jpg`. The row is inserted after the file is
  written and forced to storage (fsync of the file and its folder, 2026-11-26: without it a crash or power loss
  seconds later left the row with an empty or missing file). A deleted photo's file is removed with its row after the 60 s Undo window (§6); files without a row
  (e.g. after a crash) are removed once older than that window.

## 4. Library: patterns, people, purchases

### 4.1 Patterns

Name, type, designer, notes, photos, and "Used in" (projects linking it). Deleting a pattern removes it from its
projects (with Undo).

**Type (2026-11-27):** what the pattern sews, one of dress, skirt, T-shirt, hoodie & sweater, blouse & shirt ("Top" until
2026-11-28, so older patterns typed that way may be other tops), trousers, jacket (also coats), bag (also
accessories), other; who it is for comes from the person. Optional: the "New pattern" dialog (from "+" and
from a project's "Create “…”") shows the type chips under the name, none preselected, a second tap deselects
(2026-11-28); without one the page shows "Tap to add". Shown read-only, a tap or ✎ opens the choice dialog
"What is it?"; it can be changed any time but not cleared. The Library pattern list is grouped by
type like the home list by status ("Dress (3)", not collapsible) once any pattern has a type; patterns without one come
last under "No type". A project shows it in its pattern chip.

**From projects (2026-11-26):** below its own photos, the pattern page shows a read-only row of all photos of the
projects linking it, newest first ("how did it turn out last time, and on whom?"). A tap opens them in the viewer
without cover or delete; the shown photo's project name sits at the bottom and opens that project.

### 4.2 People

Name, measurements, notes, "Used in". A deleted person no longer shows on projects.

**Measurements (2026-11-25):** a fixed set: height, bust / chest, waist, hips, back length, arm length, inside leg;
in cm, numbers only (comma or dot), the field shows "cm". The page shows the filled ones two per row under
"Measured on <date>", or "Not measured yet". They are edited in a form (✎ next to them, Save is disabled while a value
is not a number between 1 and 300). Saving changed values stores today's measuring: a measuring from today is
corrected, otherwise a new one is added and older ones stay hidden, so finished projects keep showing what they were
sewn with. Unchanged values store nothing.

### 4.3 Purchases

**(2026-11-28)** A log of what sewing costs ("what does my hobby roughly cost?"): fabric, notions, patterns, tools,
machines. Not linked to projects.

- A purchase has a name ("Jersey 2 m"), a price, a category, a date and notes (optional, 2026-11-28, schema 5).
  Categories are a fixed list: Fabric, Notions, Patterns, Tools, Machines, Other.
- "+" opens one dialog with the required fields: name, price (numbers with comma or dot, at most two decimals; the field shows
  the currency), category (chips, Fabric preselected) and date (today, a tap opens the date picker). Save is disabled
  while the name is blank or the price is not a number. Save opens the new purchase's page; tapping a purchase in the
  list opens it too, like any library record: price, category, date and notes read-only ("Tap to add" while notes
  are empty), a tap edits one (price dialog, category choice, date picker, notes dialog), ✎ renames, the trash icon
  deletes (Undo, §6).
- The list shows the total on top, then the purchases by year, newest first, each year with its sum. Category chips
  above the list filter the list and all sums (a tap picks one category, a second tap shows all again).
- One currency for all purchases, chosen in Settings (§9); the default is the currency of the phone's region, stored
  with the first purchase so a later region change doesn't relabel the amounts. Changing it changes only how amounts
  are shown, not the amounts. Prices are stored in hundredths, shown in the locale's format with up to two decimals
  (also for currencies without cents).
- Later (ROADMAP, stash): stash items get an optional link to a purchase, so totals stay in this one table.

## 5. Machines and machine settings

### 5.1 Machines

Name, type (sewing machine, overlocker, coverstitch, embroidery, other; chosen with chips in the "New machine" dialog
next to the name, none preselected, Save is disabled until one is picked; shown read-only after: a machine never
changes kind), notes, and the
list of its settings with "+ New setting" (asks for the name, then opens the setting's values form). Deleting a machine deletes its settings and their project links (with Undo).

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
- The setting page shows its machine as "Name · Type" (tap opens the machine). The type belongs to the machine and is
  changed only on the machine page.
- Plus notes and "Used in".
- From a project: "+ Machine setting" → pick a setting, or type a name and "New setting “…”". With exactly one
  machine it is used directly; otherwise a machine picker follows (with "Create “…”" for a new machine, which opens the
  "New machine" dialog with the typed name filled in, §5.1). The new setting is linked and its page opens with
  the values form.

## 6. Delete and undo

- Delete = soft delete with cascade; the snackbar offers Undo, which restores exactly the rows of that deletion.
  The Undo snackbar shows for 6 s (longer with an accessibility timeout, at most 50 s, before the purge); a new one
  replaces the one on screen instead of queueing behind it.
  A deletion never contains a running timer (§3.3), so Undo needs no further checks.
- **Purge (2026-11-25):** 60 s after a deletion (and on every start) rows deleted longer ago are removed for good,
  children first, together with any row that still refers to them through the cascade. Each removed row is recorded
  in `deletion_log` (table, row ID, deletion time; never pruned). A deleted person's projects stay and lose the
  person. Re-adding a removed link or value within the window revives its row.
- Cascade: person → measurements → their values; project → pattern links, tags, setting links, time entries, photos;
  pattern → its project links and photos;
  machine → settings; setting → values and project links.

## 7. Data model (Room)

Every table: `id` (UUID text), `createdAt`, `updatedAt`, `deletedAt`. Unique indexes span deleted rows (revival).
Exception: `deletion_log` (rowId, tableName, deletedAt), see §6.

- `person` (name, notes)
- `measurement` (personId, measuredOn) and `measurement_value` (measurementId, key, valueCm; unique pair)
- `pattern` (name, designer, notes, coverPhotoId? — schema 2, 2026-11-26; garmentType? — schema 3)
- `project` (name, status, personId?, size, fabric, notes, startedOn?, finishedOn?, rating? and learned (both unused since
  2026-11-25, §3.4), coverPhotoId?, lastActivityAt)
- `project_pattern` (projectId, patternId, sortOrder; unique pair)
- `project_tag` (projectId, name, sortOrder; unique pair — schema 3)
- `time_entry` (projectId, startedAt, endedAt? — null = running)
- `photo` (ownerType PROJECT|PATTERN, ownerId, sortOrder)
- `machine` (name, type, notes)
- `setup` (machineId, title, notes)
- `setup_value` (setupId, key, value; unique pair)
- `project_setup` (projectId, setupId, sortOrder; unique pair)
- `purchase` (name, category, priceCents, boughtOn — schema 4; notes — schema 5)

The database file is `sewfar2.db`; the incompatible `sewfar.db` of the 1.0 prototype is deleted on start.
Enums are stored by name; never rename or remove an entry. Schema version 1 is frozen since v0.1
(`SchemaFreezeTest`); every change needs a new version, a migration and a migration test (`MigrationTest`).
Version 2 adds `pattern.coverPhotoId`, version 3 `pattern.garmentType` and `project_tag`, version 4 `purchase`, version 5 `purchase.notes`.

## 8. Backup

### 8.1 Format

A zip with `backup.json` (`{ "format": 5, "appVersion", "exportedAt", "tables": { "<table>": [rows] } }`, read in
one transaction) and `photos/<photoId>.jpg` for every live photo. Tables hold live rows only (rows inside the Undo
window are left out, references to them cleared) plus `deletion_log`; importing an older backup drops its deleted
rows the same way. Preferences are not
included. Format 1 is frozen since v0.1, format 2 adds `pattern.coverPhotoId`
(a format 1 backup restores without pinned pattern covers), format 3 `pattern.garmentType` and `project_tag` (older
backups restore without types and tags), format 4 `purchase` (older backups restore without purchases), format 5 `purchase.notes` (older
backups restore purchases without notes); every app version reads every older format; each format has a fixture in
`src/test/resources/backup/format<n>.zip`. Backups of the discarded 1.0 prototype (also "format 1", but other tables)
are rejected with their own message.

### 8.2 Export, import, automatic backup

- **Export:** save via the system dialog (`thimbly-backup-YYYY-MM-DD-HHmmss.zip`) or share.
- **Import replaces all data:** reject newer formats; only flat `photos/<id>.jpg` entries are
  extracted, other entries are ignored (no zip slip); validate one `backup.json`; confirm with counts;
  write a safety export (restorable via "Undo last restore"); extract photos to `photos.new`; replace all tables in
  one transaction once the safety export, the extracted photos and the marker are forced to storage; swap the photo folders, guarded by a marker file so an interrupted swap is completed or rolled back
  on the next start. Photo rows without a file are accepted.
- **Automatic backup:** optional, weekly, into a folder the user picks once (`thimbly-auto-*.zip`, newest 5 kept,
  nothing else touched). Errors (e.g. folder gone) are shown in Settings with "Choose folder again".

## 9. Settings

Appearance (dynamic colour), Purchases ("Currency", §4.3: a list of common currencies plus the phone's), Time ("Time tracking", §3.3), Photos ("Fine photos": new photos at up to 4096 px, §3.5), Backup (§8.2, plus the note that switching phones needs a manual backup), About
(version, licence).

## 10. Quality

- Unit tests (JVM + Robolectric): status dates, timer (one running, Done stops it), manual time, link and tag revival,
  cascade and undo, purge, photo pipeline and cleanup, backup round trip / every format's fixture / rejections /
  interrupted swaps, schema migrations, auto-backup pruning, duration and price parsing, search, purchases.
- Compose UI tests: navigation, creating a project with a pattern (New pattern dialog with type) and a machine
  setting (New machine dialog) inline, a tag from the suggestions filtering the search, a pattern type grouping the
  Library (picked, changed, deselected), adding a purchase, filtering the totals by category and editing and deleting
  it on its page, timer, delete and undo, German strings.
- Every string exists in both languages (`StringsCompletenessTest`).
- Accessibility (2026-11-25, `AccessibilityTest`): text follows the system font size; at 200 % the home buttons
  stack instead of cutting off their labels (side by side up to Android's "Large", 115 %); a link chip's × is a
  48 dp target; the "Add photo" tile's label grows up to 130 % so "Foto hinzufügen" never breaks mid-word; every icon button has a label for TalkBack.
