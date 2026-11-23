package at.mtr.sewfar.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEC 3.1 */
class ProjectListTest {
    private fun project(
        id: String,
        status: ProjectStatus = ProjectStatus.PLANNED,
        activity: Long = 0,
        person: Pair<String, String>? = null,
        patterns: List<PatternRef> = emptyList(),
        type: ProjectType = ProjectType.SEWING,
        started: LocalDate? = null,
        finished: LocalDate? = null,
        created: LocalDate = LocalDate.of(2026, 1, 10),
        fabrics: List<String> = emptyList(),
    ) = ProjectSummary(
        id, "Project $id", type, status, person?.first, person?.second, 0, patterns, "", "", fabrics,
        started, finished, created, activity, null,
    )

    @Test
    fun sectionsInFixedOrderSortedByActivity() {
        val list = listOf(
            project("idea", ProjectStatus.IDEA),
            project("done", ProjectStatus.DONE),
            project("plannedOld", activity = 1),
            project("plannedNew", activity = 5),
            project("active", ProjectStatus.IN_PROGRESS),
        )
        val sections = ProjectList.sections(list)
        assertEquals(
            listOf(ProjectStatus.IN_PROGRESS, ProjectStatus.PLANNED, ProjectStatus.IDEA, ProjectStatus.DONE),
            sections.map { it.status },
        )
        assertEquals(listOf("plannedNew", "plannedOld"), sections[1].projects.map { it.id })
    }

    @Test
    fun yearIsFinishThenStartThenCreation() {
        assertEquals(2025, project("a", ProjectStatus.DONE, started = LocalDate.of(2024, 5, 1), finished = LocalDate.of(2025, 2, 1)).year)
        assertEquals(2024, project("b", ProjectStatus.PAUSED, started = LocalDate.of(2024, 5, 1)).year)
        assertEquals(2026, project("c").year)
        // finishedOn only counts for Done.
        assertEquals(2026, project("d", ProjectStatus.PLANNED, finished = LocalDate.of(2023, 1, 1)).year)
    }

    @Test
    fun filtersCombine() {
        val hoodie = PatternRef("p1", "Hoodie", "Firma", "110")
        val list = listOf(
            project("1", person = "josi" to "Josi", patterns = listOf(hoodie)),
            project("2", person = "josi" to "Josi", type = ProjectType.REPAIR),
            project("3", person = "max" to "Max", patterns = listOf(hoodie)),
        )
        assertEquals(listOf("1", "2"), ProjectList.filter(list, "", ProjectFilter(personId = "josi")).map { it.id })
        assertEquals(listOf("1"), ProjectList.filter(list, "", ProjectFilter(personId = "josi", patternId = "p1")).map { it.id })
        assertEquals(listOf("2"), ProjectList.filter(list, "", ProjectFilter(type = ProjectType.REPAIR)).map { it.id })
        assertEquals(listOf("1", "2", "3"), ProjectList.filter(list, "", ProjectFilter(year = 2026)).map { it.id })
    }

    @Test
    fun searchCoversPatternDesignerPersonAndFabric() {
        val list = listOf(
            project("1", patterns = listOf(PatternRef("p", "Raglan", "Schnittwerk", ""))),
            project("2", person = "j" to "Jösi"),
            project("3", fabrics = listOf("Ringeljersey blau")),
        )
        assertEquals(listOf("1"), ProjectList.filter(list, "schnittwerk", ProjectFilter()).map { it.id })
        assertEquals(listOf("2"), ProjectList.filter(list, "josi", ProjectFilter()).map { it.id })
        assertEquals(listOf("3"), ProjectList.filter(list, "ringel", ProjectFilter()).map { it.id })
    }

    @Test
    fun galleryShowsDoneByFinishDate() {
        val list = listOf(
            project("old", ProjectStatus.DONE, finished = LocalDate.of(2025, 1, 1)),
            project("new", ProjectStatus.DONE, finished = LocalDate.of(2026, 3, 1)),
            project("active", ProjectStatus.IN_PROGRESS),
        )
        assertEquals(listOf("new", "old"), ProjectList.gallery(list).map { it.id })
        assertEquals(listOf(2026, 2025), ProjectList.years(list))
    }
}
