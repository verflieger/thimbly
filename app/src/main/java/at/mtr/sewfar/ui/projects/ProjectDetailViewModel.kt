package at.mtr.sewfar.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.coverOf
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.LinkedPattern
import at.mtr.sewfar.data.db.LinkedSetup
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.PhotoEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.data.db.SetupRow
import at.mtr.sewfar.data.db.TimeEntryEntity
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.ui.common.EditableName
import at.mtr.sewfar.ui.common.SerialWriter
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectDetailState(
    val loaded: Boolean = false,
    val project: ProjectEntity? = null,
    val person: PersonEntity? = null,
    val patterns: List<LinkedPattern> = emptyList(),
    val setups: List<LinkedSetup> = emptyList(),
    val time: List<TimeEntryEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
) {
    val coverPhotoId: String? get() = coverOf(photos, project?.coverPhotoId)
    val running: TimeEntryEntity? get() = time.firstOrNull { it.endedAt == null }
}

/** Choices for the pickers on the project screen. */
data class Choices(
    val persons: List<PersonEntity> = emptyList(),
    val patterns: List<PatternEntity> = emptyList(),
    val setups: List<SetupRow> = emptyList(),
    val machines: List<MachineEntity> = emptyList(),
)

/** One project, edited in place (SPEC 3). Writes run in the app scope so they finish after leaving the screen. */
class ProjectDetailViewModel(private val container: AppContainer, val id: String, private val isNew: Boolean = false) : ViewModel() {
    private val projects = container.projects
    private val app = container.appScope
    private val writer = SerialWriter(app)

    val name = EditableName(app, writer) { text -> projects.edit(id) { it.copy(name = text.trim()) } }

    val state: StateFlow<ProjectDetailState> = combine(
        projects.observe(id),
        container.persons.observeAll(),
        combine(projects.observePatterns(id), projects.observeSetups(id)) { p, s -> p to s },
        projects.observeTime(id),
        container.photos.observe(PhotoOwner.PROJECT, id),
    ) { project, persons, (patterns, setups), time, photos ->
        ProjectDetailState(
            loaded = true, project = project, person = persons.firstOrNull { it.id == project?.personId },
            patterns = patterns, setups = setups, time = time, photos = photos,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailState())

    val choices: StateFlow<Choices> = combine(
        container.persons.observeAll(), container.patterns.observeAll(),
        container.machines.observeSetups(), container.machines.observeAll(),
    ) { persons, patterns, setups, machines -> Choices(persons, patterns, setups, machines) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Choices())

    init {
        viewModelScope.launch { projects.observe(id).filterNotNull().collect { name.load(it.name) } }
    }

    /** Saves the name; leaving a new project without entering anything discards it (SPEC 3.1). */
    override fun onCleared() {
        writer.launchLast {
            name.flushInLock()
            if (isNew) projects.discardIfEmpty(id)
        }
    }

    private fun write(block: suspend () -> Unit) = writer.launch(block)

    private suspend fun <T> run(block: suspend () -> T): T = writer.run(block)

    fun setStatus(status: ProjectStatus) = write { projects.setStatus(id, status) }
    fun setNotes(text: String) = write { projects.edit(id) { it.copy(notes = text) } }
    fun setLearned(text: String) = write { projects.edit(id) { it.copy(learned = text) } }
    fun setRating(stars: Int?) = write { projects.edit(id) { it.copy(rating = stars) } }
    fun setPerson(personId: String?) = write { projects.edit(id) { it.copy(personId = personId) } }
    fun createPerson(personName: String) = write {
        val personId = container.persons.create(personName)
        projects.edit(id) { it.copy(personId = personId) }
    }

    fun addPattern(patternId: String) = write { projects.addPattern(id, patternId) }
    fun createPattern(patternName: String) = write { projects.addPattern(id, container.patterns.create(patternName)) }
    suspend fun removePattern(linkId: String): DeletionToken? = run { projects.removePattern(linkId) }

    fun addSetup(setupId: String) = write { projects.addSetup(id, setupId) }

    /** Creates a setup on [machineId] (or on a new machine named [newMachineName]), links it and returns its ID. */
    suspend fun createSetup(title: String, machineId: String?, newMachineName: String?): String = run {
        val machine = machineId ?: container.machines.create(newMachineName.orEmpty())
        container.machines.createSetup(machine, title).also { projects.addSetup(id, it) }
    }

    suspend fun removeSetup(linkId: String): DeletionToken? = run { projects.removeSetup(linkId) }

    fun startTimer() = write { projects.startTimer(id) }
    fun stopTimer() = write { projects.stopTimer(id) }
    fun addTime(date: LocalDate, minutes: Int) = write { projects.addTime(id, date, minutes) }
    suspend fun deleteTime(entryId: String): DeletionToken? = run { projects.deleteTime(entryId) }

    /** Adds photos; returns how many failed. */
    suspend fun addPhotos(sources: List<() -> InputStream>): Int = run {
        sources.count { source -> runCatching { container.photos.add(PhotoOwner.PROJECT, id, source) }.isFailure }
    }

    suspend fun deletePhoto(photoId: String): DeletionToken? = run { container.photos.delete(photoId) }
    fun setCover(photoId: String?) = write { container.photos.setCover(id, photoId) }

    suspend fun delete(): DeletionToken? = run { projects.delete(id) }
    fun undo(token: DeletionToken) = write { projects.undo(token) }
}
