# SewFar roadmap

The current app is specified in [SPEC.md](SPEC.md). This file collects what may come after, roughly in priority
order. Nothing here is decided in detail. Every addition must keep the project page simple (SPEC, guiding rule).

## Principles

- Built for the developer first, easy to personalize; open source (GPLv3).
- Offline by default. Any online feature must be an explicit opt-in and must not be needed for the core diary.
- Data guarantee: every update migrates the database and reads all older backups.
- No ads, no shop content, no "premium".

## v1.x — small additions

- Fabrics (see v2) are the next big step: they become a second home tab (Projects | Fabrics).
- Deadlines on projects (event/birthday), shown on cards.
- Costs per project.
- Statistics: time and projects per month/year, most-sewn patterns.
- Measurements per person; size sewn per pattern.
- Pattern PDFs stored in the app (separate, optional backup part because of size).

## v2 — fabric stash

- Fabric entity: name, type, composition, weight (g/m²), width, colour, print, amount, where bought, price, storage
  location, photos.
- Projects link stash fabrics (like patterns today); finishing a project subtracts the used amount. The project's
  free-text Fabric field (v1) stays beside the linked fabrics for extras; nothing is converted.
- Machine settings link to fabrics: "which setting worked for this fabric?".
- Notions (zippers, elastic, ribbing/Bündchen) and a ribbing calculator.
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
