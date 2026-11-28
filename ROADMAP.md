# Thimbly roadmap

The current app is specified in [SPEC.md](SPEC.md). This file collects what may come after, roughly in priority
order. Nothing here is decided in detail. Every addition must keep the project page simple (SPEC, guiding rule).

## Principles

- Built for the developer first, easy to personalize; open source (GPLv3).
- Offline by default. Any online feature must be an explicit opt-in and must not be needed for the core diary.
- Data guarantee: every update migrates the database and reads all older backups.
- No ads, no shop content, no "premium".

## v1.x — small additions

- **Sew again** (next; builds on what only Thimbly records: settings, size and measurements per project). A Done
  project's page offers "Sew again": asks for the name (pre-filled), creates a Planned project with the same
  patterns, size, fabric text, person, machine settings and labels (not photos, time or notes) and opens it.
  Size hint: wherever a project links a pattern and a person, the Size section shows the last Done project of that
  pattern for that person ("Last time: 122, height 118 cm (March 2026); now 127 cm") with a link to it. It is
  computed from existing links and measurements, so it also works for projects created by hand and needs no schema
  or backup change. It only shows the numbers and does not suggest a size (that is "Size suggestion" below).
- Align creating and editing: purchases (SPEC 4.3) use one dialog with all fields, and Delete sits in the dialog;
  everything else asks for the name first, opens a page, and deletes from the page's top bar. The one-step dialog
  feels slightly better for short records. Decide on one pattern (maybe: short records in a dialog, records with
  photos or links on a page) and make delete look and work the same everywhere.
- Fabrics (see v2) are the next big step: they become a Library tab (Patterns, Fabrics, People, Machines, Purchases);
  home stays the project list.
- Costs per project.
- Statistics: time and projects per month/year, most-sewn patterns.
- Size per pattern for outfits with several patterns (today: one size per project).
- "Sew again" mark on patterns (replaces the project rating dropped on 2026-11-25); the pattern list can filter by it.
- Share a project as a PDF ("project sheet", from the project page): name, status and dates, photos (cover large),
  patterns, size, fabric, machine settings and notes on one or two A4 pages, sent via the Android share sheet or
  printed. Built with Android's `PdfDocument`, no new dependency or permission; nothing is stored, so no schema or
  backup change. Leaves out the person's measurements (private; maybe a checkbox later). Complements, not replaces,
  sharing a single photo: in a family chat a PDF is a file to open, a photo is a picture.
- Pattern PDFs stored in the app (separate, optional backup part because of size).
- Ribbing (Bündchen) calculator: opening length × stretch factor = ribbing length; remembers the last factor.
  A tool reachable from the project page, no new data.
- Measurement hints: each measurement (bust, waist, hip, back length, …) shows a short hint and a simple drawing of
  where and how to measure, opened from the measurement form (e.g. an ⓘ next to the field). Static texts and
  vector drawings in the app (EN/DE), no new data.
- Size suggestion from measurements: patterns get a size chart (size → body measurements), and the project page
  suggests a size from the person's newest measurements. Heavier: needs a new table, chart entry per pattern, and
  rules for between-size cases. Only a suggestion; the Size field stays free text.

## v2 — fabric stash

- Fabric entity: name, type, composition, weight (g/m²), width, colour, print, amount, where bought, price, storage
  location, photos. Price via an optional link to a purchase (SPEC 4.3): "Add to stash" on a purchase pre-fills a
  stash item, a stash item with a price creates its purchase, so totals stay in one table and nothing migrates.
- Projects link stash fabrics (like patterns today); finishing a project subtracts the used amount. The project's
  free-text Fabric field (v1) stays beside the linked fabrics for extras; nothing is converted.
- Machine settings link to fabrics: "which setting worked for this fabric?".
- Notions (zippers, elastic, ribbing/Bündchen).
- Shopping list.

## v3 — sync & online (opt-in)

- Merge import by UUID + `updatedAt` (data model is prepared: UUIDs, timestamps, tombstones, immutable photo
  files).
- Last-writer-wins on device clocks is fragile (wrong clocks, offline edits on two phones). Consider a hybrid logical
  clock or per-field merge before building sync.
- Optional sync via WebDAV/Nextcloud (requires adding the INTERNET permission; off by default).
- Optional pattern info lookup from a shop URL.

## Distribution

- GitHub Releases first; F-Droid later (fastlane metadata en-US/de-DE prepared).
