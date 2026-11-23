package at.mtr.sewfar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import at.mtr.sewfar.domain.PhotoOwner
import kotlinx.coroutines.flow.Flow

// Reads return live rows only (deletedAt IS NULL) unless the name says otherwise ("Any").

@Dao
interface PersonDao {
    @Insert suspend fun insert(person: PersonEntity)
    @Update suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM person WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PersonEntity?

    @Query("SELECT * FROM person WHERE id = :id AND deletedAt IS NULL")
    fun observeOne(id: String): Flow<PersonEntity?>

    @Query(
        "SELECT * FROM person WHERE deletedAt IS NULL AND (:includeArchived OR archivedAt IS NULL) " +
            "ORDER BY name COLLATE NOCASE",
    )
    fun observe(includeArchived: Boolean): Flow<List<PersonEntity>>

    /** SPEC 7: a person referenced by a live project can only be archived. */
    @Query("SELECT EXISTS(SELECT 1 FROM project WHERE personId = :id AND deletedAt IS NULL)")
    suspend fun isInUse(id: String): Boolean

    @Insert suspend fun insertSession(session: MeasurementSessionEntity)
    @Update suspend fun updateSession(session: MeasurementSessionEntity)

    @Query("SELECT * FROM measurement_session WHERE id = :id AND deletedAt IS NULL")
    suspend fun getSession(id: String): MeasurementSessionEntity?

    @Query("SELECT * FROM measurement_session WHERE personId = :personId AND deletedAt IS NULL ORDER BY date DESC")
    fun observeSessions(personId: String): Flow<List<MeasurementSessionEntity>>

    @Query("SELECT * FROM project WHERE personId = :personId AND deletedAt IS NULL ORDER BY lastActivityAt DESC")
    fun observeProjects(personId: String): Flow<List<ProjectEntity>>

    /** All live values of the person's live sessions, with the session date (SPEC 5.2 history). */
    @Query(
        "SELECT v.typeId AS typeId, v.valueMm AS valueMm, s.date AS date, s.id AS sessionId " +
            "FROM measurement_value v JOIN measurement_session s ON s.id = v.sessionId " +
            "WHERE s.personId = :personId AND v.deletedAt IS NULL AND s.deletedAt IS NULL ORDER BY s.date DESC, s.createdAt DESC",
    )
    fun observeMeasurements(personId: String): Flow<List<MeasurementRow>>

    @Insert suspend fun insertValue(value: MeasurementValueEntity)
    @Update suspend fun updateValue(value: MeasurementValueEntity)

    /** Including a tombstone, for revival (SPEC 1). */
    @Query("SELECT * FROM measurement_value WHERE sessionId = :sessionId AND typeId = :typeId")
    suspend fun getValueAny(sessionId: String, typeId: String): MeasurementValueEntity?

    @Query("SELECT * FROM measurement_value WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun values(sessionId: String): List<MeasurementValueEntity>
}

@Dao
interface PatternDao {
    @Insert suspend fun insert(pattern: PatternEntity)
    @Update suspend fun update(pattern: PatternEntity)

    @Query("SELECT * FROM pattern WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PatternEntity?

    @Query("SELECT * FROM pattern WHERE id = :id AND deletedAt IS NULL")
    fun observeOne(id: String): Flow<PatternEntity?>

    @Query(
        "SELECT * FROM pattern WHERE deletedAt IS NULL AND (:includeArchived OR archivedAt IS NULL) " +
            "ORDER BY name COLLATE NOCASE",
    )
    fun observe(includeArchived: Boolean): Flow<List<PatternEntity>>

    /** SPEC 7: a pattern linked to a live project can only be archived. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM project_pattern pp JOIN project p ON p.id = pp.projectId " +
            "WHERE pp.patternId = :id AND pp.deletedAt IS NULL AND p.deletedAt IS NULL)",
    )
    suspend fun isInUse(id: String): Boolean

    /** Live projects linked to the pattern, with the link's size (SPEC 4.2, "Projects"). */
    @Query(
        "SELECT p.id AS projectId, p.name AS projectName, p.status AS status, p.personId AS personId, " +
            "pp.sizeSewn AS sizeSewn, p.completionNotes AS completionNotes, p.lastActivityAt AS lastActivityAt " +
            "FROM project_pattern pp JOIN project p ON p.id = pp.projectId " +
            "WHERE pp.patternId = :patternId AND pp.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY p.lastActivityAt DESC",
    )
    fun observeProjects(patternId: String): Flow<List<PatternProjectRow>>

    /** Lesson tags set on this pattern's links in live projects (SPEC 4.2, "Lessons"). */
    @Query(
        "SELECT l.tagId FROM project_lesson l JOIN project_pattern pp ON pp.id = l.projectPatternId " +
            "JOIN project p ON p.id = l.projectId " +
            "WHERE pp.patternId = :patternId AND l.deletedAt IS NULL AND pp.deletedAt IS NULL AND p.deletedAt IS NULL",
    )
    fun observeLessonTagIds(patternId: String): Flow<List<String>>

    @Insert suspend fun insertYardage(row: PatternYardageEntity)
    @Update suspend fun updateYardage(row: PatternYardageEntity)

    @Query("SELECT * FROM pattern_yardage WHERE id = :id AND deletedAt IS NULL")
    suspend fun getYardage(id: String): PatternYardageEntity?

    @Query("SELECT * FROM pattern_yardage WHERE patternId = :patternId AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeYardage(patternId: String): Flow<List<PatternYardageEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM pattern_yardage WHERE patternId = :patternId")
    suspend fun nextYardageOrder(patternId: String): Int
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

    // project_pattern

    @Insert suspend fun insertPattern(link: ProjectPatternEntity)
    @Update suspend fun updatePattern(link: ProjectPatternEntity)

    @Query("SELECT * FROM project_pattern WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPattern(id: String): ProjectPatternEntity?

    /** Including a tombstone, for revival (SPEC 1). */
    @Query("SELECT * FROM project_pattern WHERE projectId = :projectId AND patternId = :patternId")
    suspend fun getPatternAny(projectId: String, patternId: String): ProjectPatternEntity?

    @Query("SELECT * FROM project_pattern WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY sortOrder")
    suspend fun patterns(projectId: String): List<ProjectPatternEntity>

    @Query("SELECT * FROM project_pattern WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY sortOrder")
    fun observePatterns(projectId: String): Flow<List<ProjectPatternEntity>>

    /** Live links of all live projects, for the project list. */
    @Query(
        "SELECT pp.* FROM project_pattern pp JOIN project p ON p.id = pp.projectId " +
            "WHERE pp.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY pp.sortOrder",
    )
    fun observeAllPatternLinks(): Flow<List<ProjectPatternEntity>>

    /** Live fabric lines of all live projects, for list search. */
    @Query(
        "SELECT f.* FROM project_fabric f JOIN project p ON p.id = f.projectId " +
            "WHERE f.deletedAt IS NULL AND p.deletedAt IS NULL ORDER BY f.sortOrder",
    )
    fun observeAllFabrics(): Flow<List<ProjectFabricEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM project_pattern WHERE projectId = :projectId")
    suspend fun nextPatternOrder(projectId: String): Int

    // project_fabric

    @Insert suspend fun insertFabric(fabric: ProjectFabricEntity)
    @Update suspend fun updateFabric(fabric: ProjectFabricEntity)

    @Query("SELECT * FROM project_fabric WHERE id = :id AND deletedAt IS NULL")
    suspend fun getFabric(id: String): ProjectFabricEntity?

    @Query("SELECT * FROM project_fabric WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeFabrics(projectId: String): Flow<List<ProjectFabricEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM project_fabric WHERE projectId = :projectId")
    suspend fun nextFabricOrder(projectId: String): Int

    // project_lesson

    @Insert suspend fun insertLesson(lesson: ProjectLessonEntity)
    @Update suspend fun updateLesson(lesson: ProjectLessonEntity)

    /** Including a tombstone, for revival (SPEC 1). `IS` matches NULL for project-level lessons. */
    @Query("SELECT * FROM project_lesson WHERE projectId = :projectId AND projectPatternId IS :projectPatternId AND tagId = :tagId")
    suspend fun getLessonAny(projectId: String, projectPatternId: String?, tagId: String): ProjectLessonEntity?

    @Query("SELECT * FROM project_lesson WHERE projectId = :projectId AND deletedAt IS NULL")
    fun observeLessons(projectId: String): Flow<List<ProjectLessonEntity>>

    @Query("SELECT * FROM project_lesson WHERE projectId = :projectId AND deletedAt IS NULL")
    suspend fun lessons(projectId: String): List<ProjectLessonEntity>

    // log_entry

    @Insert suspend fun insertLog(entry: LogEntryEntity)
    @Update suspend fun updateLog(entry: LogEntryEntity)

    @Query("SELECT * FROM log_entry WHERE id = :id AND deletedAt IS NULL")
    suspend fun getLog(id: String): LogEntryEntity?

    @Query("SELECT * FROM log_entry WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY at DESC, createdAt DESC")
    fun observeLog(projectId: String): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entry WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY at DESC, createdAt DESC")
    suspend fun log(projectId: String): List<LogEntryEntity>
}

data class PatternProjectRow(
    val projectId: String,
    val projectName: String,
    val status: at.mtr.sewfar.domain.ProjectStatus,
    val personId: String?,
    val sizeSewn: String,
    val completionNotes: String,
    val lastActivityAt: Long,
)

data class MeasurementRow(val typeId: String, val valueMm: Int, val date: java.time.LocalDate, val sessionId: String)

/** Common shape of the editable lists (SPEC 1, "Built-in list entries"). */
data class ListEntryRow(
    val id: String,
    val builtinKey: String?,
    val customName: String?,
    val sortOrder: Int,
    val hidden: Boolean,
)

/** Raw access to the four list tables, which share one shape; see [at.mtr.sewfar.data.ListKind]. */
@Dao
interface ListDao {
    @RawQuery(
        observedEntities = [
            MeasurementTypeEntity::class, PatternCategoryEntity::class, LessonTagEntity::class, PurposeEntity::class,
        ],
    )
    fun observe(query: SupportSQLiteQuery): Flow<List<ListEntryRow>>

    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<ListEntryRow>

    @RawQuery
    suspend fun queryBoolean(query: SupportSQLiteQuery): Boolean

    @RawQuery
    suspend fun queryInt(query: SupportSQLiteQuery): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedMeasurementTypes(rows: List<MeasurementTypeEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedPatternCategories(rows: List<PatternCategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedLessonTags(rows: List<LessonTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedPurposes(rows: List<PurposeEntity>)
}

@Dao
interface PhotoDao {
    @Insert suspend fun insert(photo: PhotoEntity)
    @Update suspend fun update(photo: PhotoEntity)

    @Query("SELECT * FROM photo WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): PhotoEntity?

    @Query("SELECT * FROM photo WHERE ownerType = :ownerType AND ownerId = :ownerId AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeForOwner(ownerType: PhotoOwner, ownerId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photo WHERE ownerType = :ownerType AND ownerId = :ownerId AND deletedAt IS NULL ORDER BY sortOrder")
    suspend fun forOwner(ownerType: PhotoOwner, ownerId: String): List<PhotoEntity>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM photo WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun nextOrder(ownerType: PhotoOwner, ownerId: String): Int

    @Query("SELECT * FROM photo WHERE ownerType = :ownerType AND deletedAt IS NULL ORDER BY sortOrder")
    fun observeAllOfType(ownerType: PhotoOwner): Flow<List<PhotoEntity>>

    /** Photos deleted before [cutoff]: their Undo window has passed, so the files can go (SPEC 7). */
    @Query("SELECT id FROM photo WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun deletedBefore(cutoff: Long): List<String>

    /** IDs of all photo rows, live or deleted. */
    @Query("SELECT id FROM photo")
    suspend fun allIds(): List<String>
}
