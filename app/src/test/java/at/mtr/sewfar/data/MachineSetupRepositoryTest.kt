package at.mtr.sewfar.data

import at.mtr.sewfar.data.db.DeleteResult
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.domain.MachineTemplates
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.Snapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 6.1–6.4 and the machine rules of SPEC 7. */
@RunWith(RobolectricTestRunner::class)
class MachineSetupRepositoryTest {
    private lateinit var t: TestDb
    private lateinit var machines: MachineRepository
    private lateinit var setups: SetupRepository
    private val label: FieldLabel = { f: MachineFieldEntity -> f.customName ?: f.builtinKey.orEmpty() }

    @Before fun setUp() = runTest {
        t = TestDb()
        t.seed()
        machines = MachineRepository(t.db, t.deleter, t.time)
        setups = SetupRepository(t.db, t.deleter, t.time)
    }

    @After fun tearDown() = t.close()

    private suspend fun field(machine: String, key: String) = machines.fields(machine).first { it.builtinKey == key }.id

    @Test
    fun newMachineGetsTemplateFieldsAndTypeChangeKeepsThem() = runTest {
        val id = machines.create("Juki", MachineType.OVERLOCKER)
        assertEquals(MachineTemplates.fields.getValue(MachineType.OVERLOCKER), machines.fields(id).map { it.builtinKey })
        machines.edit(id) { it.copy(type = MachineType.COVERSTITCH) }
        assertEquals(MachineTemplates.fields.getValue(MachineType.OVERLOCKER), machines.fields(id).map { it.builtinKey })
    }

    @Test
    fun snapshotIsKeptWhenSetupChangesAndTweakWriteBackWork() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        val diff = field(machine, "differential_feed")
        val setup = setups.create(machine, "Jersey hem")
        setups.setValue(setup, diff, "1.3")
        val project = t.projects.create("Leggings")
        val use = setups.link(setup, project, label = label)!!

        setups.setValue(setup, diff, "1.5")
        val row = setups.observeUses(project).first().single()
        assertEquals("1.3", Snapshots.decode(row.snapshot).first { it.fieldId == diff }.value)

        setups.tweak(use, diff, "1.4")
        assertTrue(setups.observeUses(project).first().single().tweaked)

        setups.writeBack(use)
        assertEquals("1.4", setups.values(setup)[diff])
        val after = setups.observeUses(project).first().single()
        assertFalse(after.tweaked)
        assertEquals("1.4", Snapshots.decode(after.snapshot).first { it.fieldId == diff }.value)
    }

    @Test
    fun hiddenFieldsAreNotSnapshotted() = runTest {
        val machine = machines.create("Pfaff", MachineType.SEWING_MACHINE)
        machines.setFieldHidden(field(machine, "presser_foot_pressure"), true)
        val setup = setups.create(machine, "Topstitch")
        val use = setups.link(setup, t.projects.create("Jeans"), label = label)!!
        val keys = Snapshots.decode(t.db.machineDao().getUse(use)!!.snapshot).map { it.label }
        assertFalse("presser_foot_pressure" in keys)
        assertTrue("stitch_length" in keys)
    }

    @Test
    fun createFromSnapshotCopiesValuesWithoutTouchingTheUse() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        val diff = field(machine, "differential_feed")
        val purpose = t.lists.entries(ListKind.PURPOSE).first { it.builtinKey == "hem" }.id
        val setup = setups.create(machine, "Jersey hem", purposeId = purpose, fabric = "Jersey")
        setups.setValue(setup, diff, "1.3")
        val project = t.projects.create("Leggings")
        val use = setups.link(setup, project, label = label)!!
        setups.tweak(use, diff, "1.6")
        val before = t.db.machineDao().getUse(use)!!

        val copy = setups.createFromSnapshot(use, "Ribbed jersey hem")!!

        assertEquals("1.6", setups.values(copy)[diff])
        assertEquals("1.3", setups.values(setup)[diff])
        assertEquals(purpose, setups.get(copy)!!.purposeId)
        assertEquals("Jersey", setups.get(copy)!!.fabric)
        assertEquals(before, t.db.machineDao().getUse(use)) // the project's link and snapshot are unchanged
    }

    @Test
    fun createFromSnapshotOfDeletedSetupKeepsPurposeAndFabric() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        val purpose = t.lists.entries(ListKind.PURPOSE).first { it.builtinKey == "seam" }.id
        val setup = setups.create(machine, "Seam", purposeId = purpose, fabric = "Sweat")
        val use = setups.link(setup, t.projects.create("Hoodie"), label = label)!!
        setups.delete(setup)

        val copy = setups.createFromSnapshot(use, "Seam again")!!

        assertEquals(purpose, setups.get(copy)!!.purposeId)
        assertEquals("Sweat", setups.get(copy)!!.fabric)
    }

    @Test
    fun writeBackKeepsRemovedFieldsInSnapshotAndTouchesProject() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        val knife = machines.addField(machine, "Knife position")
        val diff = field(machine, "differential_feed")
        val setup = setups.create(machine, "Rolled hem")
        setups.setValue(setup, knife, "up")
        val project = t.projects.create("Scarf")
        val use = setups.link(setup, project, label = label)!!
        machines.setFieldHidden(knife, true)
        setups.tweak(use, diff, "0.7")
        t.time.advance()
        val activityBefore = t.projects.get(project)!!.lastActivityAt
        t.time.advance()

        setups.writeBack(use)

        val snapshot = Snapshots.decode(t.db.machineDao().getUse(use)!!.snapshot)
        assertEquals("up", snapshot.first { it.fieldId == knife }.value)
        assertEquals("0.7", setups.values(setup)[diff])
        assertTrue(t.projects.get(project)!!.lastActivityAt > activityBefore)
    }

    @Test
    fun archivedMachineIsFlaggedOnSetupsAndUses() = runTest {
        val machine = machines.create("Old Singer", MachineType.SEWING_MACHINE)
        val setup = setups.create(machine, "Straight seam")
        val project = t.projects.create("Curtains")
        setups.link(setup, project, label = label)
        machines.setArchived(machine, true)
        assertTrue(setups.observeAll().first().single().machineArchived)
        assertTrue(setups.observeUses(project).first().single().machineArchived)
    }

    @Test
    fun deletingMachineAfterItsUsedSetupIsGoneKeepsSnapshot() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        val setup = setups.create(machine, "Jersey hem")
        val project = t.projects.create("Leggings")
        val use = setups.link(setup, project, label = label)!!
        setups.delete(setup)

        val result = machines.delete(machine)

        assertTrue(result is DeleteResult.Deleted)
        assertEquals(null, t.deletedAt("setup_use", use))
        assertEquals(0, t.count("machine_field", "machineId = '$machine'"))
    }

    @Test
    fun deletedSetupLeavesSnapshotAndMachineRuleFollowsUses() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        val setup = setups.create(machine, "Jersey hem")
        val project = t.projects.create("Leggings")
        val use = setups.link(setup, project, label = label)!!

        assertEquals(DeleteResult.InUse, machines.delete(machine))
        val token = setups.unlink(use)!!
        assertTrue(machines.delete(machine) is DeleteResult.Deleted)
        assertNotNull(token)
    }

    @Test
    fun deletedSetupShowsAsDeletedOnProject() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        val setup = setups.create(machine, "Jersey hem")
        val project = t.projects.create("Leggings")
        setups.link(setup, project, label = label)
        setups.delete(setup)
        val row = setups.observeUses(project).first().single()
        assertTrue(row.setupDeleted)
        assertEquals(null, row.setupTitle)
    }

    @Test
    fun fieldDeletionRules() = runTest {
        val machine = machines.create("Juki", MachineType.OVERLOCKER)
        assertEquals(DeleteResult.BuiltIn, machines.deleteField(field(machine, "needle")))
        val custom = machines.addField(machine, "Knife position")
        val setup = setups.create(machine, "Rolled hem")
        setups.setValue(setup, custom, "up")
        assertEquals(DeleteResult.InUse, machines.deleteField(custom))
        setups.setValue(setup, custom, "")
        assertTrue(machines.deleteField(custom) is DeleteResult.Deleted)
    }
}
