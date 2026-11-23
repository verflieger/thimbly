package at.mtr.sewfar.ui.patterns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.mtr.sewfar.AppContainer
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.db.PatternEntity
import at.mtr.sewfar.data.db.PatternProjectRow
import at.mtr.sewfar.data.db.PatternYardageEntity
import at.mtr.sewfar.data.db.PersonEntity
import at.mtr.sewfar.domain.Lessons
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.ui.photos.ImageSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PatternDetailState(
    val loaded: Boolean = false,
    val pattern: PatternEntity? = null,
    val photoId: String? = null,
    val yardage: List<PatternYardageEntity> = emptyList(),
    val categories: List<ListEntryRow> = emptyList(),
    val projects: List<PatternProjectRow> = emptyList(),
    val persons: Map<String, PersonEntity> = emptyMap(),
    /** Tag ID → count, most frequent first (SPEC 4.2). */
    val lessonCounts: List<Pair<String, Int>> = emptyList(),
    val lessonTags: List<ListEntryRow> = emptyList(),
)

/** Pattern detail (SPEC 4.2). Writes run in the app scope. */
class PatternDetailViewModel(private val container: AppContainer, val patternId: String) : ViewModel() {
    private val patterns = container.patterns
    private val scope = container.appScope

    private val lists = combine(container.lists.observe(ListKind.PATTERN_CATEGORY), container.lists.observe(ListKind.LESSON_TAG)) { c, t -> c to t }
    private val usage = combine(
        patterns.observeProjects(patternId), patterns.observeLessonTagIds(patternId), container.persons.observe(includeArchived = true),
    ) { projects, tags, persons -> Triple(projects, tags, persons) }

    val state: StateFlow<PatternDetailState> = combine(
        patterns.observeOne(patternId),
        container.photos.observe(PhotoOwner.PATTERN, patternId),
        patterns.observeYardage(patternId),
        lists,
        usage,
    ) { pattern, photos, yardage, (categories, tags), (projects, tagIds, persons) ->
        PatternDetailState(
            loaded = true, pattern = pattern, photoId = photos.firstOrNull()?.id, yardage = yardage, categories = categories,
            projects = projects, persons = persons.associateBy { it.id }, lessonCounts = Lessons.counts(tagIds), lessonTags = tags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatternDetailState())

    /** Atomic per-field edit; see [at.mtr.sewfar.data.PatternRepository.edit]. */
    private fun edit(change: (PatternEntity) -> PatternEntity) {
        scope.launch { patterns.edit(patternId, change) }
    }

    fun rename(name: String) { if (name.isNotBlank()) edit { it.copy(name = name) } }
    fun setDesigner(text: String) = edit { it.copy(designer = text.trim()) }
    fun setCategory(id: String?) = edit { it.copy(categoryId = id) }
    fun setSizeRange(text: String) = edit { it.copy(sizeRange = text.trim()) }
    fun setFormat(format: at.mtr.sewfar.domain.PatternFormat?) = edit { it.copy(format = format) }
    fun setSourceUrl(text: String) = edit { it.copy(sourceUrl = text.trim()) }
    fun setSuitedFabrics(text: String) = edit { it.copy(suitedFabrics = text) }
    fun setNotes(text: String) = edit { it.copy(notes = text) }
    fun setArchived(archived: Boolean) { scope.launch { patterns.setArchived(patternId, archived) } }

    suspend fun createCategory(name: String): String = container.runInApp { container.lists.add(ListKind.PATTERN_CATEGORY, name) }

    fun addYardage() { scope.launch { patterns.addYardage(patternId, "", 0) } }
    fun editYardage(id: String, change: (PatternYardageEntity) -> PatternYardageEntity) { scope.launch { patterns.editYardage(id, change) } }
    suspend fun deleteYardage(id: String): DeletionToken? = patterns.deleteYardage(id)

    suspend fun setPhoto(source: ImageSource): Boolean =
        container.runInApp { runCatching { container.photos.setEntityPhoto(PhotoOwner.PATTERN, patternId, source) }.isSuccess }

    suspend fun removePhoto(photoId: String): DeletionToken? = container.runInApp { container.photos.delete(photoId) }

    /** Starts a Planned project named after the pattern, linked to it (SPEC 4.2). */
    suspend fun startProject(): String? {
        val pattern = patterns.get(patternId) ?: return null
        return container.runInApp { container.projects.create(pattern.name, patternIds = listOf(patternId)) }
    }

    suspend fun delete(): DeleteResult = container.runInApp { patterns.delete(patternId) }

    fun undo(token: DeletionToken) { scope.launch { container.patterns.undo(token) } }
}
