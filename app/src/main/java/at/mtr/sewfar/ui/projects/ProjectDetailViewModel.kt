package at.mtr.sewfar.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ProjectDetailState
import at.mtr.sewfar.data.patternCovers
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.MachineEntity
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.SetupRow
import at.mtr.sewfar.domain.GarmentType
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.ProjectStatus
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Choices for the pickers on the project screen. */
data class Choices(
    val persons: List<PersonEntity> = emptyList(),
    val patterns: List<PatternEntity> = emptyList(),
    val setups: List<SetupRow> = emptyList(),
    val machines: List<MachineEntity> = emptyList(),
    /** Pattern ID → cover photo ID (SPEC 3.5). */
    val patternCovers: Map<String, String> = emptyMap(),
    /** Tag names in use on any project (SPEC 3.2). */
    val tags: List<String> = emptyList(),
)

/** One project, edited in place (SPEC 3). Writes run in the app scope so they finish after leaving the screen. */
class ProjectDetailViewModel(private val container: AppContainer, val id: String) : ViewModel() {
    private val projects = container.projects
    private val app = container.appScope

    val state: StateFlow<ProjectDetailState> = container.projectPage.state(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailState())

    val choices: StateFlow<Choices> = combine(
        combine(container.persons.observeAll(), container.patterns.observeAll()) { p, pt -> p to pt },
        container.machines.observeSetups(), container.machines.observeAll(), container.photos.observeAllOfType(PhotoOwner.PATTERN),
        projects.observeAllTags(),
    ) { (persons, patterns), setups, machines, photos, tags ->
        Choices(persons, patterns, setups, machines, patternCovers(patterns, photos), tags.values.flatten().distinct())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Choices())

    private fun write(block: suspend () -> Unit) {
        app.launch { block() }
    }

    private suspend fun <T> run(block: suspend () -> T): T = container.runInApp(block)

    fun rename(name: String) = write { projects.edit(id) { it.copy(name = name.trim()) } }

    fun setStatus(status: ProjectStatus) = write { projects.setStatus(id, status) }
    fun setNotes(text: String) = write { projects.edit(id) { it.copy(notes = text) } }
    fun setSize(text: String) = write { projects.edit(id) { it.copy(size = text) } }
    fun setFabric(text: String) = write { projects.edit(id) { it.copy(fabric = text) } }
    fun setPerson(personId: String?) = write { projects.edit(id) { it.copy(personId = personId) } }
    fun createPerson(personName: String) = write { container.projectPage.createPerson(id, personName) }

    fun addPattern(patternId: String) = write { projects.addPattern(id, patternId) }
    fun createPattern(patternName: String, type: GarmentType?) = write { container.projectPage.createPattern(id, patternName, type) }
    suspend fun removePattern(linkId: String): DeletionToken? = run { projects.removePattern(linkId) }

    fun addTag(name: String) = write { projects.addTag(id, name) }
    fun renameTag(old: String, new: String) = write { projects.renameTag(old, new) }
    suspend fun removeTag(linkId: String): DeletionToken? = run { projects.removeTag(linkId) }

    fun addSetup(setupId: String) = write { projects.addSetup(id, setupId) }

    /** Creates a setup on [machineId] (or on a new machine named [newMachineName] of [newMachineType]), links it and returns its ID. */
    suspend fun createSetup(
        title: String, machineId: String?, newMachineName: String? = null, newMachineType: MachineType? = null,
    ): String = run { container.projectPage.createSetup(id, title, machineId, newMachineName, newMachineType) }

    suspend fun removeSetup(linkId: String): DeletionToken? = run { projects.removeSetup(linkId) }

    fun startTimer() = write { projects.startTimer(id) }
    fun stopTimer() = write { projects.stopTimer(id) }
    fun addTime(date: LocalDate, minutes: Int) = write { projects.addTime(id, date, minutes) }
    fun editTime(entryId: String, date: LocalDate, minutes: Int) = write { projects.editTime(entryId, date, minutes) }
    suspend fun deleteTime(entryId: String): DeletionToken? = run { projects.deleteTime(entryId) }

    /** Adds photos; returns how many failed. */
    suspend fun addPhotos(sources: List<() -> InputStream>): Int = run {
        sources.count { source -> runCatching { container.photos.add(PhotoOwner.PROJECT, id, source) }.isFailure }
    }

    suspend fun deletePhoto(photoId: String): DeletionToken? = run { container.photos.delete(photoId) }
    fun setCover(photoId: String?) = write { container.photos.setCover(PhotoOwner.PROJECT, id, photoId) }

    suspend fun delete(): DeletionToken? = run { projects.delete(id) }
}
