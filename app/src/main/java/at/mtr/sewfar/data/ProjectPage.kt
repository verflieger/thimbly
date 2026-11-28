package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.LinkedPattern
import at.mtr.sewfar.data.db.LinkedSetup
import at.mtr.sewfar.data.db.LinkedTag
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.TimeEntryEntity
import at.mtr.sewfar.domain.GarmentType
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.Measurements
import at.mtr.sewfar.domain.PhotoOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class ProjectDetailState(
    val loaded: Boolean = false,
    val project: ProjectEntity? = null,
    val person: PersonEntity? = null,
    val patterns: List<LinkedPattern> = emptyList(),
    val setups: List<LinkedSetup> = emptyList(),
    val tags: List<LinkedTag> = emptyList(),
    val time: List<TimeEntryEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    /** The person's measurements: the newest, or once Done, those valid on the finish date (SPEC 3.2). */
    val measurements: Measurements? = null,
) {
    val coverPhotoId: String? get() = coverOf(photos, project?.coverPhotoId)
    val running: TimeEntryEntity? get() = time.firstOrNull { it.endedAt == null }
}

/** What the project page shows (SPEC 3.2) and its writes that span several repositories. */
class ProjectPage(
    private val db: SewFarDatabase,
    private val projects: ProjectRepository,
    private val persons: PersonRepository,
    private val patterns: PatternRepository,
    private val machines: MachineRepository,
    private val photos: PhotoRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun state(id: String): Flow<ProjectDetailState> {
        val measurements = projects.observe(id).map { it?.personId to it?.finishedOn }.distinctUntilChanged()
            .flatMapLatest { (personId, finishedOn) -> personId?.let { persons.observeMeasurements(it, finishedOn) } ?: flowOf(null) }
        return combine(
            projects.observe(id),
            persons.observeAll(),
            combine(projects.observePatterns(id), projects.observeSetups(id), projects.observeTags(id)) { p, s, t -> Triple(p, s, t) },
            combine(projects.observeTime(id), measurements) { t, m -> t to m },
            photos.observe(PhotoOwner.PROJECT, id),
        ) { project, allPersons, (linkedPatterns, setups, tags), (time, m), projectPhotos ->
            ProjectDetailState(
                loaded = true, project = project, person = allPersons.firstOrNull { it.id == project?.personId },
                patterns = linkedPatterns, setups = setups, tags = tags, time = time, photos = projectPhotos, measurements = m,
            )
        }
    }

    suspend fun createPerson(id: String, name: String) = db.withTransaction {
        val personId = persons.create(name)
        projects.edit(id) { it.copy(personId = personId) }
    }

    suspend fun createPattern(id: String, name: String, type: GarmentType?) = db.withTransaction {
        projects.addPattern(id, patterns.create(name, type))
    }

    /** Creates a setup on [machineId] (or on a new machine named [newMachineName] of [newMachineType]), links it and returns its ID. */
    suspend fun createSetup(
        id: String, title: String, machineId: String?, newMachineName: String? = null, newMachineType: MachineType? = null,
    ): String = db.withTransaction {
        // One transaction: a crash never leaves an empty machine or an unlinked setting.
        val machine = machineId ?: machines.create(newMachineName.orEmpty(), requireNotNull(newMachineType) { "A new machine needs its type (SPEC 5.1)" })
        machines.createSetup(machine, title).also { projects.addSetup(id, it) }
    }
}
