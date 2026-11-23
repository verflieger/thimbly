package at.mtr.sewfar.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEC 6.4 diff rules. */
class SnapshotDiffTest {
    private val snapshot = listOf(SnapshotField("diff", "Differential feed", "1.3"), SnapshotField("len", "Stitch length", "3"))

    @Test fun unchangedHasNoDiff() =
        assertEquals(emptyList<FieldChange>(), SnapshotDiff.diff(snapshot, listOf(CurrentField("diff", "Differential feed", true), CurrentField("len", "Stitch length", true)), mapOf("diff" to "1.3", "len" to "3")))

    @Test fun labelRenameAloneIsNoDifference() =
        assertEquals(emptyList<FieldChange>(), SnapshotDiff.diff(snapshot, listOf(CurrentField("diff", "Diff.", true), CurrentField("len", "Länge", true)), mapOf("diff" to "1.3", "len" to "3")))

    @Test fun changedRemovedAndAdded() {
        val changes = SnapshotDiff.diff(
            snapshot,
            listOf(CurrentField("diff", "Differential feed", true), CurrentField("len", "Stitch length", false), CurrentField("foot", "Foot", true)),
            mapOf("diff" to "1.5", "foot" to "A"),
        )
        assertEquals(
            listOf(
                FieldChange("diff", "Differential feed", FieldChange.Kind.CHANGED, "1.3", "1.5"),
                FieldChange("len", "Stitch length", FieldChange.Kind.REMOVED, "3", null),
                FieldChange("foot", "Foot", FieldChange.Kind.ADDED, null, "A"),
            ),
            changes,
        )
    }

    @Test fun defaultTitle() {
        assertEquals("Hem – Jersey", SetupTitles.auto("Hem", " Jersey "))
        assertEquals("Hem", SetupTitles.auto("Hem", ""))
        assertEquals("", SetupTitles.auto(null, ""))
        assertEquals("Juki", SetupTitles.shown("", "Juki"))
    }

    @Test fun snapshotsRoundTripAndTolerateGarbage() {
        assertEquals(snapshot, Snapshots.decode(Snapshots.encode(snapshot)))
        assertEquals(emptyList<SnapshotField>(), Snapshots.decode("not json"))
    }
}
