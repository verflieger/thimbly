# SewFar roadmap

v1 is specified in [SPEC.md](SPEC.md). This file collects what comes after, roughly in priority order. Nothing here
is decided in detail yet.

## Principles

- Built for the developer first, easy to personalize; open source (GPLv3).
- Offline by default. Any online feature must be an explicit opt-in and must not be needed for the core diary.
- Data guarantee: every update migrates the database and reads all older backups.
- No ads, no shop content, no "premium".

## v1.x — small additions

- Deadlines on projects (event/birthday), shown on cards; optional reminder notification.
- Costs per project (fabric, notions, pattern).
- Statistics: projects finished per year/month, per person, most-sewn patterns, paused projects (UFOs).
- Measurement history chart per person; "size suggestion" by comparing measurements with a pattern's size chart.
- Pattern PDFs stored in the app (separate, optional backup part because of size).

## v2 — fabric stash

- Fabric entity: name, type, composition, weight (g/m²), width, colour, print, amount, where bought, price, storage
  location, photos.
- Project fabric lines link to stash fabrics; finishing a project subtracts the used amount.
- Setups link to fabrics instead of free text; "which setup worked for this fabric?".
- Notions (zippers, elastic, ribbing/Bündchen) and a ribbing calculator.
- Shopping list.

## v3 — sync & online (opt-in)

- Merge import by UUID + `updatedAt` (data model is prepared: UUIDs, deterministic seed IDs, timestamps,
  tombstones, immutable photo files).
- Last-writer-wins on device clocks is fragile (wrong clocks, offline edits on two phones). Consider a hybrid logical
  clock or per-field merge before building sync.
- Optional sync via WebDAV/Nextcloud (requires adding the INTERNET permission; off by default).
- Optional pattern info lookup from a shop URL.

## Distribution

- GitHub Releases first; F-Droid later (fastlane metadata en-US/de-DE prepared with v1.0).
