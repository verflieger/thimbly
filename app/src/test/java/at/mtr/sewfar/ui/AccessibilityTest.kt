package at.mtr.sewfar.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SPEC 10: usable at 200 % font size (older users), touch targets of at least 48 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measurement
@Config(qualifiers = "de", fontScale = 2f)
class AccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun SemanticsNodeInteraction.overflows(): Boolean {
        val layout = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        val l = layout.single()
        // Not hasVisualOverflow: it also reports half a pixel of rounding.
        return l.lineCount > 1 || l.multiParagraph.intrinsics.maxIntrinsicWidth > l.layoutInput.constraints.maxWidth
    }

    @Test
    fun homeButtonsFitAtDoubleFontSize() {
        // German has the longer labels.
        assertFalse(compose.onNodeWithText("Neues Projekt", useUnmergedTree = true).overflows())
        assertFalse(compose.onNodeWithText("Bibliothek", useUnmergedTree = true).overflows())
    }

    @Test
    @Config(qualifiers = "de-w411dp", fontScale = 1.15f)
    fun homeButtonsStaySideBySideAtLargeFont() {
        assertFalse(compose.onNodeWithText("Neues Projekt", useUnmergedTree = true).overflows())
        assertEquals(compose.onNodeWithTag("newProject").getBoundsInRoot().top, compose.onNodeWithTag("library").getBoundsInRoot().top)
    }

    @Test
    fun addPhotoTileLabelFitsAtDoubleFontSize() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Rock")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Foto hinzufügen")).fetchSemanticsNodes().isNotEmpty() }
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Foto hinzufügen", useUnmergedTree = true).performScrollTo()
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        val l = layout.single()
        // Two lines are fine; a word broken across lines ("hinzufüge|n") is not.
        val lines = (0 until l.lineCount).map { l.layoutInput.text.substring(l.getLineStart(it), l.getLineEnd(it)).trim() }
        assertEquals(listOf("Foto", "hinzufügen"), lines)
    }

    @Test
    fun removeOnALinkChipIsBigEnoughToHit() {
        compose.onNodeWithTag("newProject").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("Rock")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("addPattern")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("addPattern").performScrollTo().performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("Raglan")
        compose.onNode(hasText("Raglan", substring = true) and hasText("anlegen", substring = true)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Raglan")).fetchSemanticsNodes().isNotEmpty() }
        val remove = compose.onNode(
            SemanticsMatcher("remove ×") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("remove-") == true },
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val size = minOf(remove.right - remove.left, remove.bottom - remove.top)
        assertTrue("× is only $size", size >= 48.dp)
    }
}
