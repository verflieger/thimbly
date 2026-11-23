package at.mtr.sewfar.domain

// Stored in the database and backups by name: never rename or remove an entry, only add (SPEC 8).

/** SPEC 3.3 */
enum class ProjectType { SEWING, UPCYCLING, REPAIR, ALTERATION }

/** SPEC 3.2. Declaration order is the order used in pickers and menus. */
enum class ProjectStatus { IDEA, PLANNED, IN_PROGRESS, PAUSED, DONE }

/** SPEC 4.2 */
enum class PatternFormat { PAPER, PDF, MAGAZINE, SELF_DRAFTED }

/** SPEC 3.6 */
enum class PhotoOwner { PROJECT, PATTERN, PERSON, MACHINE }

/** SPEC 3.6 */
enum class PhotoTag { INSPIRATION, PROGRESS, FINISHED }

/** SPEC 3.4 */
enum class LogKind { NOTE, STATUS }

/** SPEC 6.1 */
enum class MachineType { SEWING_MACHINE, OVERLOCKER, COVERSTITCH, EMBROIDERY, OTHER }

/** SPEC 6.3 */
enum class SetupResult { GOOD, OK, BAD }
