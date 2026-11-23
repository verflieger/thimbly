package at.mtr.sewfar.ui.patterns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.domain.PatternFormat
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.SearchText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class PatternRow(val pattern: PatternEntity, val photoId: String?)

data class PatternsState(
    val loaded: Boolean = false,
    val empty: Boolean = true,
    val rows: List<PatternRow> = emptyList(),
    val categories: List<ListEntryRow> = emptyList(),
    val query: String = "",
    val categoryId: String? = null,
    val format: PatternFormat? = null,
    val showArchived: Boolean = false,
)

/** Pattern library (SPEC 4.1): search, category/format filters, archived toggle. */
@OptIn(ExperimentalCoroutinesApi::class)
class PatternsViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow<String?>(null)
    private val format = MutableStateFlow<PatternFormat?>(null)
    private val showArchived = MutableStateFlow(false)

    private val filters = combine(query, categoryId, format, showArchived) { q, c, f, a -> Filters(q, c, f, a) }

    private data class Filters(val query: String, val categoryId: String?, val format: PatternFormat?, val archived: Boolean)

    val state: StateFlow<PatternsState> = combine(
        showArchived.flatMapLatest { container.patterns.observe(includeArchived = it) },
        container.photos.observeAllOfType(PhotoOwner.PATTERN),
        container.lists.observe(ListKind.PATTERN_CATEGORY),
        filters,
    ) { patterns, photos, categories, f ->
        val photoByOwner = photos.associateBy({ it.ownerId }, { it.id })
        val shown = patterns.filter { p ->
            (f.categoryId == null || p.categoryId == f.categoryId) &&
                (f.format == null || p.format == f.format) &&
                SearchText.matches(f.query, listOf(p.name, p.designer, p.notes, p.suitedFabrics))
        }
        PatternsState(
            loaded = true, empty = patterns.isEmpty(), rows = shown.map { PatternRow(it, photoByOwner[it.id]) },
            categories = categories, query = f.query, categoryId = f.categoryId, format = f.format, showArchived = f.archived,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatternsState())

    fun setQuery(text: String) { query.value = text }
    fun setCategory(id: String?) { categoryId.value = id }
    fun setFormat(value: PatternFormat?) { format.value = value }
    fun setShowArchived(value: Boolean) { showArchived.value = value }

    suspend fun create(name: String): String = container.runInApp { container.patterns.create(name) }
}
