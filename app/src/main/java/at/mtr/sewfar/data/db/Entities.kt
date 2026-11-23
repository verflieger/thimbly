package at.mtr.sewfar.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.mtr.sewfar.domain.LogKind
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PatternFormat
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.domain.SetupResult
import java.time.LocalDate

// SPEC 8. Every table: UUID string id, createdAt/updatedAt (epoch millis UTC), deletedAt (soft delete, null = live).
// Foreign keys have no ON DELETE action (rows are never hard-deleted); every FK column is indexed.
// Unique indexes are on all rows, not only live ones: re-adding something revives its tombstone (SPEC 1), so a key
// never has two rows.

// ---- People (SPEC 5) ----

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarColor: Int,
    val notes: String = "",
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "measurement_type", indices = [Index("builtinKey", unique = true)])
data class MeasurementTypeEntity(
    @PrimaryKey val id: String,
    val builtinKey: String? = null,
    val customName: String? = null,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "measurement_session",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"])],
    indices = [Index("personId")],
)
data class MeasurementSessionEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val date: LocalDate,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "measurement_value",
    foreignKeys = [
        ForeignKey(entity = MeasurementSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"]),
        ForeignKey(entity = MeasurementTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"]),
    ],
    indices = [Index("sessionId", "typeId", unique = true), Index("typeId")],
)
data class MeasurementValueEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val typeId: String,
    val valueMm: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// ---- Patterns (SPEC 4) ----

@Entity(tableName = "pattern_category", indices = [Index("builtinKey", unique = true)])
data class PatternCategoryEntity(
    @PrimaryKey val id: String,
    val builtinKey: String? = null,
    val customName: String? = null,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "pattern",
    foreignKeys = [ForeignKey(entity = PatternCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])],
    indices = [Index("categoryId")],
)
data class PatternEntity(
    @PrimaryKey val id: String,
    val name: String,
    val designer: String = "",
    val categoryId: String? = null,
    val sizeRange: String = "",
    val format: PatternFormat? = null,
    val sourceUrl: String = "",
    val suitedFabrics: String = "",
    val notes: String = "",
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "pattern_yardage",
    foreignKeys = [ForeignKey(entity = PatternEntity::class, parentColumns = ["id"], childColumns = ["patternId"])],
    indices = [Index("patternId")],
)
data class PatternYardageEntity(
    @PrimaryKey val id: String,
    val patternId: String,
    val size: String,
    val amountCm: Int,
    val fabricWidthCm: Int? = null,
    val note: String = "",
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// ---- Projects (SPEC 3) ----

@Entity(
    tableName = "project",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"]),
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["sourceProjectId"]),
    ],
    // coverPhotoId has no FK: photo rows point back at their project (ownerId), which would make the FKs circular.
    indices = [Index("personId"), Index("sourceProjectId"), Index("coverPhotoId")],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: ProjectType = ProjectType.SEWING,
    val status: ProjectStatus = ProjectStatus.PLANNED,
    val personId: String? = null,
    /** Size sewn when the project has no pattern (SPEC 3.3). */
    val size: String = "",
    val notes: String = "",
    val startedOn: LocalDate? = null,
    val finishedOn: LocalDate? = null,
    val rating: Int? = null,
    val completionNotes: String = "",
    /** Null = automatic cover (SPEC 3.6). */
    val coverPhotoId: String? = null,
    val sourceProjectId: String? = null,
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
    val sizeSewn: String = "",
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "project_fabric",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"])],
    indices = [Index("projectId")],
)
data class ProjectFabricEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val description: String,
    val amountCm: Int? = null,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "lesson_tag", indices = [Index("builtinKey", unique = true)])
data class LessonTagEntity(
    @PrimaryKey val id: String,
    val builtinKey: String? = null,
    val customName: String? = null,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "project_lesson",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"]),
        ForeignKey(entity = ProjectPatternEntity::class, parentColumns = ["id"], childColumns = ["projectPatternId"]),
        ForeignKey(entity = LessonTagEntity::class, parentColumns = ["id"], childColumns = ["tagId"]),
    ],
    // SQLite treats NULLs as distinct, so project-level rows (projectPatternId = null) are kept unique by the
    // repository, which always revives an existing row.
    indices = [Index("projectId", "projectPatternId", "tagId", unique = true), Index("projectPatternId"), Index("tagId")],
)
data class ProjectLessonEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** Null = project-level lesson (project without pattern). */
    val projectPatternId: String? = null,
    val tagId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "log_entry",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"])],
    indices = [Index("projectId")],
)
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val at: Long,
    val kind: LogKind,
    val text: String = "",
    val fromStatus: ProjectStatus? = null,
    val toStatus: ProjectStatus? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Photos of projects, patterns, persons and machines (SPEC 3.6). File: `photos/<id>.jpg`. */
@Entity(
    tableName = "photo",
    // ownerId is polymorphic (see ownerType) and has no FK.
    foreignKeys = [ForeignKey(entity = LogEntryEntity::class, parentColumns = ["id"], childColumns = ["logEntryId"])],
    indices = [Index("ownerType", "ownerId"), Index("logEntryId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val ownerType: PhotoOwner,
    val ownerId: String,
    val logEntryId: String? = null,
    /** Required for project photos, null otherwise. */
    val tag: PhotoTag? = null,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// ---- Machines & setups (SPEC 6, milestone M2) ----

@Entity(tableName = "machine")
data class MachineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String = "",
    val model: String = "",
    val type: MachineType,
    val notes: String = "",
    val lastServicedOn: LocalDate? = null,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "machine_field",
    foreignKeys = [ForeignKey(entity = MachineEntity::class, parentColumns = ["id"], childColumns = ["machineId"])],
    indices = [Index("machineId", "builtinKey", unique = true)],
)
data class MachineFieldEntity(
    @PrimaryKey val id: String,
    val machineId: String,
    val builtinKey: String? = null,
    val customName: String? = null,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "purpose", indices = [Index("builtinKey", unique = true)])
data class PurposeEntity(
    @PrimaryKey val id: String,
    val builtinKey: String? = null,
    val customName: String? = null,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "setup",
    foreignKeys = [
        ForeignKey(entity = MachineEntity::class, parentColumns = ["id"], childColumns = ["machineId"]),
        ForeignKey(entity = PurposeEntity::class, parentColumns = ["id"], childColumns = ["purposeId"]),
    ],
    indices = [Index("machineId"), Index("purposeId")],
)
data class SetupEntity(
    @PrimaryKey val id: String,
    val machineId: String,
    val purposeId: String? = null,
    val title: String,
    val fabric: String = "",
    val result: SetupResult? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "setup_value",
    foreignKeys = [
        ForeignKey(entity = SetupEntity::class, parentColumns = ["id"], childColumns = ["setupId"]),
        ForeignKey(entity = MachineFieldEntity::class, parentColumns = ["id"], childColumns = ["fieldId"]),
    ],
    indices = [Index("setupId", "fieldId", unique = true), Index("fieldId")],
)
data class SetupValueEntity(
    @PrimaryKey val id: String,
    val setupId: String,
    val fieldId: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "setup_use",
    foreignKeys = [
        ForeignKey(entity = SetupEntity::class, parentColumns = ["id"], childColumns = ["setupId"]),
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"]),
        ForeignKey(entity = LogEntryEntity::class, parentColumns = ["id"], childColumns = ["logEntryId"]),
    ],
    indices = [Index("setupId"), Index("projectId"), Index("logEntryId")],
)
data class SetupUseEntity(
    @PrimaryKey val id: String,
    val setupId: String,
    val projectId: String,
    val logEntryId: String? = null,
    val snapshotAt: Long,
    val tweaked: Boolean = false,
    /** JSON list of {fieldId, label, value} (SPEC 6.4). */
    val snapshot: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
