package at.mtr.sewfar.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.domain.PhotoOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class PeopleState(
    val loaded: Boolean = false,
    val persons: List<PersonEntity> = emptyList(),
    val photoByPerson: Map<String, String> = emptyMap(),
    val showArchived: Boolean = false,
)

/** People list (SPEC 5). */
@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModel(private val container: AppContainer) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<PeopleState> = combine(
        showArchived.flatMapLatest { container.persons.observe(includeArchived = it) },
        container.photos.observeAllOfType(PhotoOwner.PERSON),
        showArchived,
    ) { persons, photos, archived ->
        PeopleState(true, persons, photos.associateBy({ it.ownerId }, { it.id }), archived)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleState())

    fun setShowArchived(value: Boolean) { showArchived.value = value }

    suspend fun create(name: String): String = container.runInApp { container.persons.create(name) }
}
