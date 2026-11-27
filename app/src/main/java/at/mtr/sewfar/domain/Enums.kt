package at.mtr.sewfar.domain

// Stored in the database and backups by name: never rename or remove an entry, only add (SPEC 7).

/** SPEC 3.2. Declaration order is the order shown in the status selector. */
enum class ProjectStatus { PLANNED, IN_PROGRESS, DONE }

/** SPEC 3.5 */
enum class PhotoOwner { PROJECT, PATTERN }

/** SPEC 4.1: what a pattern sews. TOP includes shirts, JACKET coats, BAG accessories. Declaration order = list order. */
enum class GarmentType { DRESS, SKIRT, TOP, TROUSERS, JACKET, BAG, OTHER }

/** SPEC 5.1 */
enum class MachineType { SEWING_MACHINE, OVERLOCKER, COVERSTITCH, EMBROIDERY, OTHER }
