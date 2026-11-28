package at.mtr.sewfar.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import at.mtr.sewfar.MainActivity
import at.mtr.sewfar.SewFarApp
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC 4.3: add purchases in the dialog, totals per year and overall, category filter, edit and delete on the page. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class PurchaseFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Before fun euro() = runBlocking { (compose.activity.application as SewFarApp).container.settings.setCurrency("EUR") }

    private fun add(name: String, price: String, category: String) {
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("purchaseName").performTextInput(name)
        compose.onNodeWithTag("purchasePrice").performTextInput(price)
        compose.onNodeWithTag("category-$category").performScrollTo().performClick()
        compose.onNodeWithTag("dialogSave").performClick()
        // Its page opens (SPEC 2); back to the list.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("edit-notes").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("total").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun addFilterEditDelete() {
        compose.onNodeWithTag("library").performClick()
        compose.onNodeWithTag("tab-PURCHASES").performClick()
        waitFor("No purchases yet.")

        // Save stays disabled while the price is not a number.
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("purchaseName").performTextInput("Thread")
        compose.onNodeWithTag("purchasePrice").performTextInput("1,234")
        compose.onNodeWithTag("dialogSave").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()

        add("Jersey 2 m", "18,50", "FABRIC")
        add("Scissors", "24.9", "TOOLS")
        compose.onNodeWithTag("total").assertTextEquals("€43.40")

        compose.onNodeWithTag("filter-TOOLS").performClick()
        compose.onNodeWithTag("total").assertTextEquals("€24.90")
        compose.onNodeWithText("Jersey 2 m").assertDoesNotExist()
        compose.onNodeWithTag("filter-TOOLS").performClick()
        compose.onNodeWithTag("total").assertTextEquals("€43.40")

        // Edited on its page, like every library record (SPEC 2).
        compose.onNodeWithText("Scissors").performClick()
        compose.onNodeWithTag("edit-price").performClick()
        compose.onNodeWithTag("purchasePrice").performTextReplacement("30")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("€30.00")
        compose.onNodeWithTag("edit-notes").performClick()
        compose.onNodeWithTag("dialogText").performTextInput("From the market")
        compose.onNodeWithTag("dialogSave").performClick()
        waitFor("From the market")
        compose.onNodeWithTag("edit-category").performClick()
        compose.onNodeWithTag("type-OTHER").performClick()
        waitFor("Other")
        compose.onNodeWithTag("edit").performClick() // rename
        compose.onNodeWithTag("dialogText").performTextReplacement("  Shears ")
        compose.onNodeWithTag("dialogSave").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Shears")).fetchSemanticsNodes().isNotEmpty() } // trimmed
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("total").assertTextEquals("€48.50") }.isSuccess }

        compose.onNodeWithTag("filter-OTHER").performClick()
        compose.onNodeWithTag("total").assertTextEquals("€30.00")
        compose.onNodeWithTag("filter-OTHER").performClick()

        compose.onNodeWithText("Shears").performClick()
        compose.onNodeWithTag("delete").performClick()
        waitFor("Purchase deleted")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("total").assertTextEquals("€18.50") }.isSuccess }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("total").assertTextEquals("€48.50") }.isSuccess }
    }
}
