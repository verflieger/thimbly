package at.mtr.sewfar.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.ListEntryRow
import at.mtr.sewfar.data.seed.BuiltIns
import at.mtr.sewfar.ui.common.BuiltInLabels
import at.mtr.sewfar.ui.common.resolveName
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SPEC 1 / 12: built-in entries show in the current language; a custom name wins in every language. */
@RunWith(RobolectricTestRunner::class)
class BuiltInLabelsTest {
    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }

    @Test
    fun everySeedKeyHasALabel() {
        for ((kind, keys) in BuiltIns.keys) assertEquals("labels for $kind", keys.toSet(), BuiltInLabels.keys(kind))
    }

    @Test
    fun builtInFollowsLanguageCustomNameWins() {
        val en = context(Locale.ENGLISH)
        val de = context(Locale.GERMAN)
        val builtIn = ListEntryRow("id", "lengthen", null, 0, false)
        assertEquals("Lengthen", builtIn.resolveName(ListKind.LESSON_TAG, en::getString))
        assertEquals("Länger nähen", builtIn.resolveName(ListKind.LESSON_TAG, de::getString))

        val renamed = builtIn.copy(customName = "Ärmel länger")
        assertEquals("Ärmel länger", renamed.resolveName(ListKind.LESSON_TAG, en::getString))
        assertEquals("Ärmel länger", renamed.resolveName(ListKind.LESSON_TAG, de::getString))

        val custom = ListEntryRow("c", null, "Pre-wash", 1, false)
        assertEquals("Pre-wash", custom.resolveName(ListKind.LESSON_TAG, de::getString))
    }
}
