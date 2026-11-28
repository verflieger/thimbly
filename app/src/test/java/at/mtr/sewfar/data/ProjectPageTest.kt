package at.mtr.sewfar.data

import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.ProjectStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** SPEC 3.2: what the project page shows, and its writes that span several repositories. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectPageTest {
    private lateinit var t: TestDb

    @Before fun setUp() { t = TestDb() }
    @After fun tearDown() = t.close()

    private suspend fun state(id: String) = t.projectPage.state(id).first()

    @Test
    fun measurementsAreTheNewestUntilDoneThenThoseOfTheFinishDay() = runTest {
        // One live flow, as on an open page: it must switch when the person or the finish date changes.
        lateinit var live: kotlinx.coroutines.flow.SharedFlow<ProjectDetailState>
        suspend fun heightBecomes(cm: Double?) = live.first { it.measurements?.valuesCm?.get("height") == cm }
        val project = t.projects.create("Dress")
        val lena = t.persons.create("Lena")
        val max = t.persons.create("Max")
        t.persons.saveMeasurements(lena, mapOf("height" to 116.0))
        t.persons.saveMeasurements(max, mapOf("height" to 140.0))
        live = t.projectPage.state(project).shareIn(backgroundScope, SharingStarted.Eagerly, replay = 1)
        heightBecomes(null)

        t.projects.edit(project) { it.copy(personId = lena) }
        heightBecomes(116.0)

        t.projects.setStatus(project, ProjectStatus.DONE)
        t.time.day = t.time.day.plusMonths(6)
        t.persons.saveMeasurements(lena, mapOf("height" to 121.0))
        assertEquals("Done: as of the finish day", 116.0, state(project).measurements!!.valuesCm["height"])

        t.projects.setStatus(project, ProjectStatus.IN_PROGRESS)
        heightBecomes(121.0) // not done: the newest

        t.projects.edit(project) { it.copy(personId = max) }
        assertEquals("Max", heightBecomes(140.0).person!!.name) // follows the person
    }

    @Test
    fun createSetupOnANewMachineCreatesMachineSettingAndLink() = runTest {
        val project = t.projects.create("Shirt")
        val setup = t.projectPage.createSetup(project, "Jersey seam", machineId = null, newMachineName = "Overlock", newMachineType = MachineType.OVERLOCKER)

        assertEquals(MachineType.OVERLOCKER, t.machines.observeAll().first().single().type)
        val linked = state(project).setups.single()
        assertEquals(setup, linked.setupId)
        assertEquals("Jersey seam", linked.title)
        assertEquals("Overlock", linked.machineName)

        val again = t.projectPage.createSetup(project, "Hem", machineId = t.machines.observeAll().first().single().id)
        assertEquals("Existing machine reused", 1, t.count("machine"))
        assertEquals(listOf(setup, again).toSet(), state(project).setups.map { it.setupId }.toSet())
    }

    @Test
    fun createPersonAndPatternLinkWhatTheyCreate() = runTest {
        val project = t.projects.create("Skirt")
        t.projectPage.createPerson(project, "Lena")
        t.projectPage.createPattern(project, "Circle skirt", null)

        val s = state(project)
        assertEquals("Lena", s.person!!.name)
        assertEquals("Circle skirt", s.patterns.single().name)
    }

    @Test
    fun coverIsPinnedOrNewestAndRunningIsTheOpenEntry() = runTest {
        t.time.now = System.currentTimeMillis()
        val project = t.projects.create("Bag")
        val image = TestDb.jpeg(40, 30)
        val first = t.photos.add(PhotoOwner.PROJECT, project) { image.inputStream() }
        val second = t.photos.add(PhotoOwner.PROJECT, project) { image.inputStream() }
        assertEquals("Newest by default", second, state(project).coverPhotoId)

        t.photos.setCover(PhotoOwner.PROJECT, project, first)
        assertEquals("Pinned", first, state(project).coverPhotoId)

        assertNull(state(project).running)
        t.projects.startTimer(project)
        assertEquals(state(project).time.single().id, state(project).running!!.id)
        t.projects.stopTimer(project)
        assertNull(state(project).running)
    }
}
