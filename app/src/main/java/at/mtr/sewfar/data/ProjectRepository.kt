package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.LogEntryEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.ProjectFabricEntity
import at.mtr.sewfar.data.db.ProjectLessonEntity
import at.mtr.sewfar.data.db.ProjectPatternEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.LogKind
import at.mtr.sewfar.domain.ProjectDates
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.domain.StatusRules
import kotlinx.coroutines.flow.Flow

/** Result of [ProjectRepository.setStatus]. */
data class StatusChange(val project: ProjectEntity, val opensCompletionSheet: Boolean)

/**
 * Projects and their child rows (SPEC 3). Every change to a child row also bumps the project's
 * `lastActivityAt` (SPEC 3.1).
 */
class ProjectRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.projectDao()

    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    fun observe(id: String): Flow<ProjectEntity?> = dao.observe(id)

    fun observeAllPatternLinks(): Flow<List<ProjectPatternEntity>> = dao.observeAllPatternLinks()

    fun observeAllFabrics(): Flow<List<ProjectFabricEntity>> = dao.observeAllFabrics()

    suspend fun get(id: String): ProjectEntity? = dao.get(id)

    /** Creates a Planned project (SPEC 3.2) with the given patterns, in that order. */
    suspend fun create(
        name: String,
        type: ProjectType = ProjectType.SEWING,
        personId: String? = null,
        patternIds: List<String> = emptyList(),
    ): String = db.withTransaction {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(
            ProjectEntity(
                id = id, name = name.trim(), type = type, personId = personId,
                lastActivityAt = now, createdAt = now, updatedAt = now,
            ),
        )
        patternIds.distinct().forEach { addPattern(id, it) }
        id
    }

    /**
     * Saves edited fields (name, type, person, size, notes, dates, rating, completion notes, cover).
     * The status changes only through [setStatus], which applies its side effects.
     */
    suspend fun update(project: ProjectEntity) = db.withTransaction {
        val current = requireNotNull(dao.get(project.id)) { "Project ${project.id} not found" }
        require(project.status == current.status) { "Use setStatus to change the status" }
        val now = time.nowMillis()
        dao.update(project.copy(name = project.name.trim(), updatedAt = now, lastActivityAt = now))
    }

    /**
     * Applies [change] to the current row in one transaction, so concurrent edits of different fields don't overwrite
     * each other. Nothing happens if the project is gone. The status changes only through [setStatus].
     */
    suspend fun edit(id: String, change: (ProjectEntity) -> ProjectEntity) = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction
        val next = change(current)
        require(next.status == current.status) { "Use setStatus to change the status" }
        val now = time.nowMillis()
        dao.update(next.copy(name = next.name.trim(), updatedAt = now, lastActivityAt = now))
    }

    /** Changes the status with its date effects and writes a status log entry (SPEC 3.2, 3.4). */
    suspend fun setStatus(id: String, status: ProjectStatus): StatusChange = db.withTransaction {
        val current = requireNotNull(dao.get(id)) { "Project $id not found" }
        if (current.status == status) return@withTransaction StatusChange(current, opensCompletionSheet = false)
        val now = time.nowMillis()
        val dates = StatusRules.apply(current.status, status, ProjectDates(current.startedOn, current.finishedOn), time.today())
        val updated = current.copy(
            status = status, startedOn = dates.startedOn, finishedOn = dates.finishedOn,
            updatedAt = now, lastActivityAt = now,
        )
        dao.update(updated)
        dao.insertLog(
            LogEntryEntity(
                id = Ids.random(), projectId = id, at = now, kind = LogKind.STATUS,
                fromStatus = current.status, toStatus = status, createdAt = now, updatedAt = now,
            ),
        )
        StatusChange(updated, StatusRules.opensCompletionSheet(current.status, status))
    }

    /** Soft-deletes the project with all its child rows (SPEC 7). */
    suspend fun delete(id: String): DeletionToken? = deleter.delete("project", id)

    suspend fun undo(token: DeletionToken) = deleter.undo(token)

    // ---- Patterns (SPEC 3.3) ----

    fun observePatterns(projectId: String): Flow<List<ProjectPatternEntity>> = dao.observePatterns(projectId)

    suspend fun patterns(projectId: String): List<ProjectPatternEntity> = dao.patterns(projectId)

    /** Links a pattern (at most once per project). Re-adding a removed pattern revives its link (SPEC 1). */
    suspend fun addPattern(projectId: String, patternId: String, sizeSewn: String = ""): String = db.withTransaction {
        val now = time.nowMillis()
        val existing = dao.getPatternAny(projectId, patternId)
        val id = when {
            existing == null -> Ids.random().also { id ->
                dao.insertPattern(
                    ProjectPatternEntity(
                        id, projectId, patternId, sizeSewn.trim(), dao.nextPatternOrder(projectId),
                        createdAt = now, updatedAt = now,
                    ),
                )
            }
            existing.deletedAt != null -> existing.id.also {
                dao.updatePattern(
                    existing.copy(
                        sizeSewn = sizeSewn.trim().ifEmpty { existing.sizeSewn },
                        sortOrder = dao.nextPatternOrder(projectId), deletedAt = null, updatedAt = now,
                    ),
                )
            }
            else -> existing.id
        }
        dao.touch(projectId, now)
        id
    }

    /** Removes a pattern link; its lessons go with it (SPEC 3.3). */
    suspend fun removePattern(projectPatternId: String): DeletionToken? = db.withTransaction {
        val link = dao.getPattern(projectPatternId) ?: return@withTransaction null
        deleter.delete("project_pattern", projectPatternId).also { dao.touch(link.projectId, time.nowMillis()) }
    }

    suspend fun setPatternSize(projectPatternId: String, sizeSewn: String) = db.withTransaction {
        val link = dao.getPattern(projectPatternId) ?: return@withTransaction
        val now = time.nowMillis()
        dao.updatePattern(link.copy(sizeSewn = sizeSewn.trim(), updatedAt = now))
        dao.touch(link.projectId, now)
    }

    // ---- Fabrics (SPEC 3.3) ----

    fun observeFabrics(projectId: String): Flow<List<ProjectFabricEntity>> = dao.observeFabrics(projectId)

    suspend fun addFabric(projectId: String, description: String, amountCm: Int? = null): String = db.withTransaction {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertFabric(
            ProjectFabricEntity(
                id, projectId, description.trim(), amountCm, dao.nextFabricOrder(projectId),
                createdAt = now, updatedAt = now,
            ),
        )
        dao.touch(projectId, now)
        id
    }

    /** Edits a live fabric line atomically; a removed line stays removed (late saves from the UI are dropped). */
    suspend fun editFabric(fabricId: String, change: (ProjectFabricEntity) -> ProjectFabricEntity) = db.withTransaction {
        val current = dao.getFabric(fabricId) ?: return@withTransaction
        val now = time.nowMillis()
        val next = change(current)
        dao.updateFabric(next.copy(description = next.description.trim(), updatedAt = now))
        dao.touch(current.projectId, now)
    }

    suspend fun removeFabric(fabricId: String): DeletionToken? = db.withTransaction {
        val fabric = dao.getFabric(fabricId) ?: return@withTransaction null
        deleter.delete("project_fabric", fabricId).also { dao.touch(fabric.projectId, time.nowMillis()) }
    }

    // ---- Lessons (SPEC 3.5) ----

    fun observeLessons(projectId: String): Flow<List<ProjectLessonEntity>> = dao.observeLessons(projectId)

    suspend fun lessons(projectId: String): List<ProjectLessonEntity> = dao.lessons(projectId)

    /**
     * Sets or clears one lesson tag for a pattern link, or project-level with [projectPatternId] = null.
     * A tag is set at most once per row; setting it again revives the old row (SPEC 1).
     */
    suspend fun setLesson(projectId: String, projectPatternId: String?, tagId: String, set: Boolean) = db.withTransaction {
        val now = time.nowMillis()
        val existing = dao.getLessonAny(projectId, projectPatternId, tagId)
        val changed = when {
            set && existing == null -> {
                dao.insertLesson(ProjectLessonEntity(Ids.random(), projectId, projectPatternId, tagId, createdAt = now, updatedAt = now))
                true
            }
            set && existing!!.deletedAt != null -> {
                dao.updateLesson(existing.copy(deletedAt = null, updatedAt = now))
                true
            }
            !set && existing != null && existing.deletedAt == null -> {
                dao.updateLesson(existing.copy(deletedAt = now, updatedAt = now))
                true
            }
            else -> false
        }
        if (changed) dao.touch(projectId, now)
    }

    // ---- Progress log (SPEC 3.4) ----

    fun observeLog(projectId: String): Flow<List<LogEntryEntity>> = dao.observeLog(projectId)

    suspend fun log(projectId: String): List<LogEntryEntity> = dao.log(projectId)

    suspend fun addNote(projectId: String, text: String, at: Long = time.nowMillis()): String = db.withTransaction {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insertLog(LogEntryEntity(id, projectId, at, LogKind.NOTE, text.trim(), createdAt = now, updatedAt = now))
        dao.touch(projectId, now)
        id
    }

    /** Edits a note's text or time. Status entries cannot be edited (SPEC 3.4). */
    suspend fun updateNote(entry: LogEntryEntity) = db.withTransaction {
        val current = requireNotNull(dao.getLog(entry.id)) { "Log entry ${entry.id} not found" }
        require(current.kind == LogKind.NOTE) { "Status entries cannot be edited" }
        val now = time.nowMillis()
        dao.updateLog(current.copy(text = entry.text.trim(), at = entry.at, updatedAt = now))
        dao.touch(current.projectId, now)
    }

    /** Deletes a note or status entry with its photos and setup uses (SPEC 7). */
    suspend fun deleteLogEntry(entryId: String): DeletionToken? = db.withTransaction {
        val entry = dao.getLog(entryId) ?: return@withTransaction null
        deleter.delete("log_entry", entryId).also { dao.touch(entry.projectId, time.nowMillis()) }
    }
}
