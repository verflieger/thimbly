package at.mtr.sewfar.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.MeasurementRow
import at.mtr.sewfar.data.db.MeasurementSessionEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.data.db.ProjectEntity
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.ui.photos.ImageSource
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonDetailState(
    val loaded: Boolean = false,
    val person: PersonEntity? = null,
    val photoId: String? = null,
    val types: List<ListEntryRow> = emptyList(),
    val measurements: List<MeasurementRow> = emptyList(),
    val sessions: List<MeasurementSessionEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
)

/** Person detail with measurements (SPEC 5). Writes run in the app scope. */
class PersonDetailViewModel(private val container: AppContainer, val personId: String) : ViewModel() {
    private val persons = container.persons
    private val scope = container.appScope

    private val measured = combine(persons.observeMeasurements(personId), persons.observeSessions(personId)) { m, s -> m to s }

    val state: StateFlow<PersonDetailState> = combine(
        persons.observeOne(personId),
        container.photos.observe(PhotoOwner.PERSON, personId),
        container.lists.observe(ListKind.MEASUREMENT_TYPE),
        measured,
        persons.observeProjects(personId),
    ) { person, photos, types, (measurements, sessions), projects ->
        PersonDetailState(true, person, photos.firstOrNull()?.id, types, measurements, sessions, projects)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailState())

    /** Atomic per-field edit; see [at.mtr.sewfar.data.PersonRepository.edit]. */
    private fun edit(change: (PersonEntity) -> PersonEntity) {
        scope.launch { persons.edit(personId, change) }
    }

    fun rename(name: String) { if (name.isNotBlank()) edit { it.copy(name = name) } }
    fun setColor(color: Int) = edit { it.copy(avatarColor = color) }
    fun setNotes(text: String) = edit { it.copy(notes = text) }
    fun setArchived(archived: Boolean) { scope.launch { persons.setArchived(personId, archived) } }

    suspend fun setPhoto(source: ImageSource): Boolean =
        container.runInApp { runCatching { container.photos.setEntityPhoto(PhotoOwner.PERSON, personId, source) }.isSuccess }

    suspend fun removePhoto(photoId: String): DeletionToken? = container.runInApp { container.photos.delete(photoId) }

    /** Saves a measuring session: new if [sessionId] is null, else updates date/note and values (null = removed). */
    fun saveSession(sessionId: String?, date: LocalDate, note: String, valuesMm: Map<String, Int?>) {
        scope.launch {
            if (sessionId == null) {
                persons.createSession(personId, date, valuesMm.filterValues { it != null }.mapValues { it.value!! }, note)
            } else {
                persons.getSession(sessionId)?.let { persons.updateSession(it.copy(date = date, note = note)) }
                valuesMm.forEach { (typeId, mm) -> persons.setValue(sessionId, typeId, mm) }
            }
        }
    }

    suspend fun deleteSession(sessionId: String): DeletionToken? = container.runInApp { persons.deleteSession(sessionId) }

    suspend fun delete(): DeleteResult = container.runInApp { persons.delete(personId) }

    fun undo(token: DeletionToken) { scope.launch { container.persons.undo(token) } }
}
