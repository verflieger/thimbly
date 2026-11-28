package at.mtr.sewfar.domain

// Stored in the database and backups by name: never rename or remove an entry, only add (SPEC 7).

/** SPEC 3.2. Declaration order is the order shown in the status selector. */
enum class ProjectStatus { PLANNED, IN_PROGRESS, DONE }

/** SPEC 3.5 */
enum class PhotoOwner { PROJECT, PATTERN }

/**
 * SPEC 4.1: what a pattern sews. TOP is shown as "Blouse & shirt" since 2026-11-28 (T_SHIRT and HOODIE split off),
 * JACKET includes coats, BAG accessories. Declaration order = list order; stored by name, so inserting is safe.
 */
enum class GarmentType { DRESS, SKIRT, T_SHIRT, HOODIE, TOP, TROUSERS, JACKET, BAG, OTHER }

/** SPEC 4.3. Declaration order = chip order. */
enum class PurchaseCategory { FABRIC, NOTIONS, PATTERNS, TOOLS, MACHINES, OTHER }

/** SPEC 5.1 */
enum class MachineType { SEWING_MACHINE, OVERLOCKER, COVERSTITCH, EMBROIDERY, OTHER }
