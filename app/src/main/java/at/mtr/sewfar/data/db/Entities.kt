package at.mtr.sewfar.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.ProjectStatus
import java.time.LocalDate

// SPEC 7. Every table (except deletion_log): UUID string id, createdAt/updatedAt (epoch millis UTC), deletedAt (soft
// delete, null = live). Deleted rows are removed for good after the Undo window (SoftDeleter.purge, SPEC 6).
// Foreign keys have no ON DELETE action (the purge removes children first); every FK column is indexed.
// Unique indexes are on all rows, not only live ones: re-adding a link within the Undo window revives its row.

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One measuring of a person (SPEC 4.2): the date; the values are [MeasurementValueEntity] rows. */
@Entity(
    tableName = "measurement",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"])],
    indices = [Index("personId", "measuredOn")],
)
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val measuredOn: LocalDate,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One body measurement in cm; [key] is a `MeasurementFields` key. */
@Entity(
    tableName = "measurement_value",
    foreignKeys = [ForeignKey(entity = MeasurementEntity::class, parentColumns = ["id"], childColumns = ["measurementId"])],
    indices = [Index("measurementId", "key", unique = true)],
)
data class MeasurementValueEntity(
    @PrimaryKey val id: String,
    val measurementId: String,
    val key: String,
    val valueCm: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "pattern")
data class PatternEntity(
    @PrimaryKey val id: String,
    val name: String,
    val designer: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "project",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"])],
    // coverPhotoId has no FK: photo rows point back at their owner, which would make the FKs circular.
    indices = [Index("personId")],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: ProjectStatus = ProjectStatus.PLANNED,
    val personId: String? = null,
    val notes: String = "",
    val startedOn: LocalDate? = null,
    val finishedOn: LocalDate? = null,
    /** 1–5 stars, set when done (SPEC 3.4). */
    val rating: Int? = null,
    val learned: String = "",
    /** Null = newest photo (SPEC 3.5). */
    val coverPhotoId: String? = null,
    /** Last change to the project or any of its child rows; sorts the list (SPEC 3.1). */
    val lastActivityAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "project_pattern",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"]),
        ForeignKey(entity = PatternEntity::class, parentColumns = ["id"], childColumns = ["patternId"]),
    ],
    indices = [Index("projectId", "patternId", unique = true), Index("patternId")],
)
data class ProjectPatternEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val patternId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One timer session or manually added time (SPEC 3.3). [endedAt] null = timer running. */
@Entity(
    tableName = "time_entry",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"])],
    indices = [Index("projectId")],
)
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Photos of projects and patterns (SPEC 3.5). File: `photos/<id>.jpg`. */
@Entity(tableName = "photo", indices = [Index("ownerType", "ownerId")])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val ownerType: PhotoOwner,
    // Polymorphic (see ownerType), so no FK.
    val ownerId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "machine")
data class MachineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: MachineType = MachineType.SEWING_MACHINE,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A reusable machine setting (SPEC 5.2). */
@Entity(
    tableName = "setup",
    foreignKeys = [ForeignKey(entity = MachineEntity::class, parentColumns = ["id"], childColumns = ["machineId"])],
    indices = [Index("machineId")],
)
data class SetupEntity(
    @PrimaryKey val id: String,
    val machineId: String,
    val title: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One setting value; [key] is a `MachineTemplates` key. */
@Entity(
    tableName = "setup_value",
    foreignKeys = [ForeignKey(entity = SetupEntity::class, parentColumns = ["id"], childColumns = ["setupId"])],
    indices = [Index("setupId", "key", unique = true)],
)
data class SetupValueEntity(
    @PrimaryKey val id: String,
    val setupId: String,
    val key: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "project_setup",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"]),
        ForeignKey(entity = SetupEntity::class, parentColumns = ["id"], childColumns = ["setupId"]),
    ],
    indices = [Index("projectId", "setupId", unique = true), Index("setupId")],
)
data class ProjectSetupEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val setupId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * One row removed for good by the purge (SPEC 6): which table, which row, when it was deleted. Kept for a future sync,
 * so another device can learn about the deletion; never pruned.
 */
@Entity(tableName = "deletion_log")
data class DeletionLogEntity(
    @PrimaryKey val rowId: String,
    val tableName: String,
    val deletedAt: Long,
)
