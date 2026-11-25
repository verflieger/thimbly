package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.LinkedPattern
import at.mtr.sewfar.data.db.LinkedSetup
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.ProjectPatternEntity
import at.mtr.sewfar.data.db.ProjectSetupEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.data.db.TimeEntryEntity
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.ProjectStatus
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Projects and their child rows (SPEC 3). Every change to a child row bumps the project's `lastActivityAt`. */
class ProjectRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.projectDao()

    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    fun observe(id: String): Flow<ProjectEntity?> = dao.observe(id)

    suspend fun get(id: String): ProjectEntity? = dao.get(id)

    /** Creates a Planned project (SPEC 3.1). */
    suspend fun create(name: String): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(ProjectEntity(id = id, name = name.trim(), lastActivityAt = now, createdAt = now, updatedAt = now))
        return id
    }

    /**
     * Applies [change] to the current row in one transaction, so concurrent edits of different fields don't overwrite
     * each other. Nothing happens if the project is gone. The status changes only through [setStatus].
     */
    suspend fun edit(id: String, change: (ProjectEntity) -> ProjectEntity) = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction
        val next = change(current)
        require(next.status == current.status) { "Use setStatus to change the status" }
        if (next == current) return@withTransaction
        val now = time.nowMillis()
        dao.update(next.copy(updatedAt = now, lastActivityAt = now))
    }

    /**
     * Changes the status (SPEC 3.2): In progress sets the start date if missing, Done sets the finish date and stops
     * a running timer, leaving Done clears the finish date.
     */
    suspend fun setStatus(id: String, status: ProjectStatus) = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction
        if (current.status == status) return@withTransaction
        val now = time.nowMillis()
        val today = time.today()
        dao.update(
            current.copy(
                status = status,
                startedOn = current.startedOn ?: today.takeIf { status != ProjectStatus.PLANNED },
                finishedOn = if (status == ProjectStatus.DONE) today else null,
                updatedAt = now, lastActivityAt = now,
            ),
        )
        if (status == ProjectStatus.DONE) stopTimer(id)
    }

    /**
     * Soft-deletes the project with all its child rows (SPEC 6). A running timer is stopped first, so Undo can't
     * bring back a second running timer.
     */
    suspend fun delete(id: String): DeletionToken? = db.withTransaction {
        stopTimer(id)
        deleter.delete("project", id)
    }


    // ---- Patterns (SPEC 3.1) ----

    fun observePatterns(projectId: String): Flow<List<LinkedPattern>> = dao.observePatterns(projectId)

    /** Pattern names per project ID, for the list and its search. */
    fun observeAllPatternNames(): Flow<Map<String, List<String>>> =
        dao.observeAllPatternNames().map { rows -> rows.groupBy({ it.projectId }, { it.name }) }

    /** Links a pattern (at most once per project). Re-adding a removed pattern revives its link (SPEC 1). */
    suspend fun addPattern(projectId: String, patternId: String) = db.withTransaction {
        addLink(
            projectId, dao.getPatternAny(projectId, patternId), { it.deletedAt != null },
            insert = { now -> dao.insertPattern(ProjectPatternEntity(Ids.random(), projectId, patternId, dao.nextPatternOrder(projectId), createdAt = now, updatedAt = now)) },
            revive = { old, now -> dao.updatePattern(old.copy(sortOrder = dao.nextPatternOrder(projectId), deletedAt = null, updatedAt = now)) },
        )
    }

    suspend fun removePattern(linkId: String): DeletionToken? = db.withTransaction {
        val link = dao.getPattern(linkId) ?: return@withTransaction null
        deleter.delete("project_pattern", linkId).also { dao.touch(link.projectId, time.nowMillis()) }
    }

    // ---- Machine settings (SPEC 3.1, 5.2) ----

    fun observeSetups(projectId: String): Flow<List<LinkedSetup>> = dao.observeSetups(projectId)

    suspend fun addSetup(projectId: String, setupId: String) = db.withTransaction {
        addLink(
            projectId, dao.getSetupAny(projectId, setupId), { it.deletedAt != null },
            insert = { now -> dao.insertSetup(ProjectSetupEntity(Ids.random(), projectId, setupId, dao.nextSetupOrder(projectId), createdAt = now, updatedAt = now)) },
            revive = { old, now -> dao.updateSetup(old.copy(sortOrder = dao.nextSetupOrder(projectId), deletedAt = null, updatedAt = now)) },
        )
    }

    /**
     * Links at most once per project: inserts a new link, or revives [existing] if it was removed (its row still
     * blocks the unique index, SPEC 1); a live link stays as it is. Any change counts as project activity.
     */
    private suspend inline fun <T : Any> addLink(
        projectId: String,
        existing: T?,
        removed: (T) -> Boolean,
        insert: (now: Long) -> Unit,
        revive: (old: T, now: Long) -> Unit,
    ) {
        val now = time.nowMillis()
        when {
            existing == null -> insert(now)
            removed(existing) -> revive(existing, now)
            else -> return
        }
        dao.touch(projectId, now)
    }

    suspend fun removeSetup(linkId: String): DeletionToken? = db.withTransaction {
        val link = dao.getSetup(linkId) ?: return@withTransaction null
        deleter.delete("project_setup", linkId).also { dao.touch(link.projectId, time.nowMillis()) }
    }

    // ---- Time (SPEC 3.3) ----

    fun observeTime(projectId: String): Flow<List<TimeEntryEntity>> = dao.observeTime(projectId)

    fun observeAllTime(): Flow<List<TimeEntryEntity>> = dao.observeAllTime()

    /**
     * Starts the timer. Only one timer runs at a time: a timer running on another project is stopped. A Planned
     * project becomes In progress.
     */
    suspend fun startTimer(projectId: String) = db.withTransaction {
        val now = time.nowMillis()
        val running = dao.running()
        if (running.any { it.projectId == projectId }) return@withTransaction
        running.forEach { stop(it, now) }
        dao.insertTime(TimeEntryEntity(Ids.random(), projectId, startedAt = now, createdAt = now, updatedAt = now))
        dao.touch(projectId, now)
        if (dao.get(projectId)?.status == ProjectStatus.PLANNED) setStatus(projectId, ProjectStatus.IN_PROGRESS)
    }

    suspend fun stopTimer(projectId: String) = db.withTransaction {
        val now = time.nowMillis()
        dao.running().filter { it.projectId == projectId }.forEach { stop(it, now) }
    }

    private suspend fun stop(entry: TimeEntryEntity, now: Long) {
        dao.updateTime(entry.copy(endedAt = maxOf(now, entry.startedAt), updatedAt = now))
        dao.touch(entry.projectId, now)
    }

    /** Adds [minutes] worked on [date] (stored as starting at noon of that day). */
    suspend fun addTime(projectId: String, date: LocalDate, minutes: Int, zone: ZoneId = ZoneId.systemDefault()) = db.withTransaction {
        require(minutes > 0)
        val now = time.nowMillis()
        val start = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        dao.insertTime(
            TimeEntryEntity(Ids.random(), projectId, startedAt = start, endedAt = start + minutes * 60_000L, createdAt = now, updatedAt = now),
        )
        dao.touch(projectId, now)
    }

    /**
     * Soft-deletes a time entry. A running entry is not deleted (null; stop it first), so Undo can't bring back a
     * second running timer (SPEC 3.3).
     */
    suspend fun deleteTime(entryId: String): DeletionToken? = db.withTransaction {
        val entry = dao.getTime(entryId) ?: return@withTransaction null
        if (entry.endedAt == null) return@withTransaction null
        deleter.delete("time_entry", entryId).also { dao.touch(entry.projectId, time.nowMillis()) }
    }
}
