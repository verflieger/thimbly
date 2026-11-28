package at.mtr.sewfar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.ProjectStatus
import kotlinx.coroutines.flow.Flow

// Reads return live rows only (deletedAt IS NULL) unless the name says otherwise ("Any").

/** A project linked to a pattern, person or setup, for "Used in" lists. */
data class ProjectRef(val id: String, val name: String, val status: ProjectStatus)

/** A photo of a project that uses a pattern (SPEC 4.1, gallery). */
data class ProjectPhoto(val photoId: String, val projectId: String, val projectName: String)

/** A pattern linked to a project. */
data class LinkedPattern(val linkId: String, val patternId: String, val name: String, val designer: String, val garmentType: at.mtr.sewfar.domain.GarmentType?)

data class ProjectPatternName(val projectId: String, val name: String)

/** A tag on a project. */
data class LinkedTag(val linkId: String, val name: String)

data class ProjectTagName(val projectId: String, val name: String)

/** A setup linked to a project. */
data class LinkedSetup(val linkId: String, val setupId: String, val title: String, val machineName: String)

/** A setup with its machine, for pickers and lists. */
data class SetupRow(val id: String, val machineId: String, val machineName: String, val machineType: MachineType, val title: String)

/** One value of a person's measurements, with the date of that measuring. */
data class MeasurementRow(val measurementId: String, val measuredOn: java.time.LocalDate, val key: String?, val valueCm: Double?)

@Dao
interface PersonDao {
    @Insert suspend fun insert(person: PersonEntity)
    @Update suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM person WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PersonEntity?

    @Query("SELECT * FROM person WHERE id = :id AND deletedAt IS NULL")
    fun observeOne(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM person WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT id, name, status FROM project WHERE personId = :id AND deletedAt IS NULL ORDER BY lastActivityAt DESC")
    fun observeProjects(id: String): Flow<List<ProjectRef>>

    // ---- Measurements (SPEC 4.2) ----

    @Insert suspend fun insertMeasurement(measurement: MeasurementEntity)
    @Update suspend fun updateMeasurement(measurement: MeasurementEntity)
    @Insert suspend fun insertMeasurementValue(value: MeasurementValueEntity)
    @Update suspend fun updateMeasurementValue(value: MeasurementValueEntity)

    @Query("SELECT * FROM measurement WHERE personId = :personId AND deletedAt IS NULL ORDER BY measuredOn DESC, createdAt DESC LIMIT 1")
    suspend fun newestMeasurement(personId: String): MeasurementEntity?

    /** Including removed values, for revival (unique per measurement and key). */
    @Query("SELECT * FROM measurement_value WHERE measurementId = :measurementId")
    suspend fun measurementValuesAny(measurementId: String): List<MeasurementValueEntity>

    /** The newest measuring of [personId] on or before [onOrBefore] (null = newest at all), one row per value. */
    @Query(
        "SELECT m.id AS measurementId, m.measuredOn, v.key, v.valueCm FROM measurement m " +
            "LEFT JOIN measurement_value v ON v.measurementId = m.id AND v.deletedAt IS NULL " +
            "WHERE m.id = (SELECT id FROM measurement WHERE personId = :personId AND deletedAt IS NULL " +
            "AND (:onOrBefore IS NULL OR measuredOn <= :onOrBefore) ORDER BY measuredOn DESC, createdAt DESC LIMIT 1)",
    )
    fun observeMeasurement(personId: String, onOrBefore: java.time.LocalDate?): Flow<List<MeasurementRow>>
}

@Dao
interface PatternDao {
    @Insert suspend fun insert(pattern: PatternEntity)
    @Update suspend fun update(pattern: PatternEntity)

    @Query("SELECT * FROM pattern WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PatternEntity?

    @Query("SELECT * FROM pattern WHERE id = :id AND deletedAt IS NULL")
    fun observeOne(id: String): Flow<PatternEntity?>

    @Query("SELECT * FROM pattern WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PatternEntity>>

    @Query(
        "SELECT p.id, p.name, p.status FROM project_pattern pp JOIN project p ON p.id = pp.projectId " +
            "WHERE pp.patternId = :id AND pp.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY p.lastActivityAt DESC",
    )
    fun observeProjects(id: String): Flow<List<ProjectRef>>

    /** Photos of the projects linking pattern [id], newest first. */
    @Query(
        "SELECT ph.id AS photoId, p.id AS projectId, p.name AS projectName FROM project_pattern pp " +
            "JOIN project p ON p.id = pp.projectId JOIN photo ph ON ph.ownerType = 'PROJECT' AND ph.ownerId = p.id " +
            "WHERE pp.patternId = :id AND pp.deletedAt IS NULL AND p.deletedAt IS NULL AND ph.deletedAt IS NULL " +
            "ORDER BY ph.createdAt DESC, ph.sortOrder DESC",
    )
    fun observeProjectPhotos(id: String): Flow<List<ProjectPhoto>>
}

@Dao
interface ProjectDao {
    @Insert suspend fun insert(project: ProjectEntity)
    @Update suspend fun update(project: ProjectEntity)

    @Query("SELECT * FROM project WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): ProjectEntity?

    @Query("SELECT * FROM project WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM project WHERE deletedAt IS NULL ORDER BY lastActivityAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    /** SPEC 3.1: any change to a child row counts as activity on the project. */
    @Query("UPDATE project SET lastActivityAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    /** "What I learned" was dropped (SPEC 3.4): its text moves to the end of the notes; old backups still carry it. */
    @Query(
        "UPDATE project SET notes = CASE WHEN notes = '' THEN learned ELSE notes || char(10) || char(10) || learned END, " +
            "learned = '', updatedAt = :now WHERE learned != ''",
    )
    suspend fun foldLearnedIntoNotes(now: Long)

    // ---- Patterns ----

    @Insert suspend fun insertPattern(link: ProjectPatternEntity)
    @Update suspend fun updatePattern(link: ProjectPatternEntity)

    @Query("SELECT * FROM project_pattern WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPattern(id: String): ProjectPatternEntity?

    /** Including a tombstone, for revival (SPEC 1). */
    @Query("SELECT * FROM project_pattern WHERE projectId = :projectId AND patternId = :patternId")
    suspend fun getPatternAny(projectId: String, patternId: String): ProjectPatternEntity?

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM project_pattern WHERE projectId = :projectId")
    suspend fun nextPatternOrder(projectId: String): Int

    @Query(
        "SELECT pp.id AS linkId, p.id AS patternId, p.name, p.designer, p.garmentType FROM project_pattern pp " +
            "JOIN pattern p ON p.id = pp.patternId " +
            "WHERE pp.projectId = :projectId AND pp.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY pp.sortOrder",
    )
    fun observePatterns(projectId: String): Flow<List<LinkedPattern>>

    @Query(
        "SELECT pp.projectId, p.name FROM project_pattern pp JOIN pattern p ON p.id = pp.patternId " +
            "WHERE pp.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY pp.sortOrder",
    )
    fun observeAllPatternNames(): Flow<List<ProjectPatternName>>

    // ---- Tags (SPEC 3.2) ----

    @Insert suspend fun insertTag(link: ProjectTagEntity)
    @Update suspend fun updateTag(link: ProjectTagEntity)

    @Query("SELECT * FROM project_tag WHERE id = :id AND deletedAt IS NULL")
    suspend fun getTag(id: String): ProjectTagEntity?

    /** Live tag rows of all projects, for renaming a label. */
    @Query("SELECT * FROM project_tag WHERE deletedAt IS NULL")
    suspend fun allTags(): List<ProjectTagEntity>

    /** All tags of a project, including tombstones, for revival (SPEC 1). */
    @Query("SELECT * FROM project_tag WHERE projectId = :projectId")
    suspend fun tagsAny(projectId: String): List<ProjectTagEntity>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM project_tag WHERE projectId = :projectId")
    suspend fun nextTagOrder(projectId: String): Int

    @Query("SELECT id AS linkId, name FROM project_tag WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeTags(projectId: String): Flow<List<LinkedTag>>

    /** Tags of live projects, for the list, its search and the pickers. */
    @Query(
        "SELECT t.projectId, t.name FROM project_tag t JOIN project p ON p.id = t.projectId " +
            "WHERE t.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY t.sortOrder",
    )
    fun observeAllTags(): Flow<List<ProjectTagName>>

    // ---- Setups ----

    @Insert suspend fun insertSetup(link: ProjectSetupEntity)
    @Update suspend fun updateSetup(link: ProjectSetupEntity)

    @Query("SELECT * FROM project_setup WHERE id = :id AND deletedAt IS NULL")
    suspend fun getSetup(id: String): ProjectSetupEntity?

    @Query("SELECT * FROM project_setup WHERE projectId = :projectId AND setupId = :setupId")
    suspend fun getSetupAny(projectId: String, setupId: String): ProjectSetupEntity?

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM project_setup WHERE projectId = :projectId")
    suspend fun nextSetupOrder(projectId: String): Int

    @Query(
        "SELECT ps.id AS linkId, s.id AS setupId, s.title, m.name AS machineName FROM project_setup ps " +
            "JOIN setup s ON s.id = ps.setupId JOIN machine m ON m.id = s.machineId " +
            "WHERE ps.projectId = :projectId AND ps.deletedAt IS NULL AND s.deletedAt IS NULL ORDER BY ps.sortOrder",
    )
    fun observeSetups(projectId: String): Flow<List<LinkedSetup>>

    // ---- Time ----

    @Insert suspend fun insertTime(entry: TimeEntryEntity)
    @Update suspend fun updateTime(entry: TimeEntryEntity)

    @Query("SELECT * FROM time_entry WHERE id = :id AND deletedAt IS NULL")
    suspend fun getTime(id: String): TimeEntryEntity?

    @Query("SELECT * FROM time_entry WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeTime(projectId: String): Flow<List<TimeEntryEntity>>

    /** Entries of live projects. */
    @Query(
        "SELECT t.* FROM time_entry t JOIN project p ON p.id = t.projectId " +
            "WHERE t.deletedAt IS NULL AND p.deletedAt IS NULL",
    )
    fun observeAllTime(): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entry WHERE endedAt IS NULL AND deletedAt IS NULL")
    suspend fun running(): List<TimeEntryEntity>
}

@Dao
interface PhotoDao {
    @Insert suspend fun insert(photo: PhotoEntity)

    @Query("SELECT * FROM photo WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PhotoEntity?

    /** Newest first, for the photo row and the viewer alike (SPEC 3.5). */
    @Query("SELECT * FROM photo WHERE ownerType = :ownerType AND ownerId = :ownerId AND deletedAt IS NULL ORDER BY sortOrder DESC")
    fun observeForOwner(ownerType: PhotoOwner, ownerId: String): Flow<List<PhotoEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM photo WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun nextOrder(ownerType: PhotoOwner, ownerId: String): Int

    @Query("SELECT * FROM photo WHERE ownerType = :ownerType AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeAllOfType(ownerType: PhotoOwner): Flow<List<PhotoEntity>>

    /** IDs of all photo rows, live or deleted. */
    @Query("SELECT id FROM photo")
    suspend fun allIds(): List<String>
}

@Dao
interface MachineDao {
    @Insert suspend fun insert(machine: MachineEntity)
    @Update suspend fun update(machine: MachineEntity)

    @Query("SELECT * FROM machine WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): MachineEntity?

    @Query("SELECT * FROM machine WHERE id = :id AND deletedAt IS NULL")
    fun observeOne(id: String): Flow<MachineEntity?>

    @Query("SELECT * FROM machine WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MachineEntity>>

    @Insert suspend fun insertSetup(setup: SetupEntity)
    @Update suspend fun updateSetup(setup: SetupEntity)

    @Query("SELECT * FROM setup WHERE id = :id AND deletedAt IS NULL")
    suspend fun getSetup(id: String): SetupEntity?

    @Query("SELECT * FROM setup WHERE id = :id AND deletedAt IS NULL")
    fun observeSetup(id: String): Flow<SetupEntity?>

    @Query(
        "SELECT s.id, s.machineId, m.name AS machineName, m.type AS machineType, s.title FROM setup s " +
            "JOIN machine m ON m.id = s.machineId WHERE s.deletedAt IS NULL AND m.deletedAt IS NULL " +
            "ORDER BY m.name COLLATE NOCASE, s.title COLLATE NOCASE",
    )
    fun observeSetups(): Flow<List<SetupRow>>

    @Insert suspend fun insertValue(value: SetupValueEntity)
    @Update suspend fun updateValue(value: SetupValueEntity)

    /** Including a tombstone, for revival (SPEC 1). */
    @Query("SELECT * FROM setup_value WHERE setupId = :setupId AND `key` = :key")
    suspend fun getValueAny(setupId: String, key: String): SetupValueEntity?

    @Query("SELECT * FROM setup_value WHERE setupId = :setupId AND deletedAt IS NULL")
    fun observeValues(setupId: String): Flow<List<SetupValueEntity>>

    @Query(
        "SELECT p.id, p.name, p.status FROM project_setup ps JOIN project p ON p.id = ps.projectId " +
            "WHERE ps.setupId = :id AND ps.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY p.lastActivityAt DESC",
    )
    fun observeProjects(id: String): Flow<List<ProjectRef>>
}

@Dao
interface PurchaseDao {
    @Insert suspend fun insert(purchase: PurchaseEntity)
    @Update suspend fun update(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchase WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PurchaseEntity?

    @Query("SELECT * FROM purchase WHERE id = :id AND deletedAt IS NULL")
    fun observeOne(id: String): Flow<PurchaseEntity?>

    /** Newest first (SPEC 4.3). */
    @Query("SELECT * FROM purchase WHERE deletedAt IS NULL ORDER BY boughtOn DESC, createdAt DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>
}
