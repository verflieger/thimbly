package at.mtr.sewfar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.db.MachineFieldEntity
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoTag
import at.mtr.sewfar.domain.ProjectStatus
import at.mtr.sewfar.domain.ProjectType
import at.mtr.sewfar.domain.Ids
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/**
 * Debug only: `adb shell am broadcast -n at.mtr.sewfar/.DemoDataReceiver --es lang de` adds sample projects,
 * patterns, people and a machine (in English or German) for store screenshots. Does nothing if projects exist.
 */
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SewFarApp).container
        val de = intent.getStringExtra("lang") == "de"
        fun t(en: String, deText: String) = if (de) deText else en
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.runInApp { fill(container, ::t, de) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fill(c: AppContainer, t: (String, String) -> String, de: Boolean) {
        if (c.database.projectDao().allNames().isNotEmpty()) return
        val josi = c.persons.create("Josi", t("Turns six in May", "Wird im Mai sechs"))
        val max = c.persons.create("Max")
        val height = c.lists.entries(ListKind.MEASUREMENT_TYPE).first { it.builtinKey == "height" }.id
        val chest = c.lists.entries(ListKind.MEASUREMENT_TYPE).first { it.builtinKey == "chest" }.id
        val waist = c.lists.entries(ListKind.MEASUREMENT_TYPE).first { it.builtinKey == "waist" }.id
        c.persons.createSession(josi, c.time.today().minusMonths(7), mapOf(height to 1040, chest to 560, waist to 520))
        c.persons.createSession(josi, c.time.today().minusDays(10), mapOf(height to 1090, chest to 580, waist to 530))

        val hoodie = c.patterns.create("Hoodie Kalle", "Studio Fadenlauf")
        val raglan = c.patterns.create(t("Raglan shirt", "Raglanshirt"), "Nadel & Faden")
        val tote = c.patterns.create(t("Market tote", "Markttasche"), t("Self-drafted", "Selbst gezeichnet"))
        c.patterns.edit(hoodie) { it.copy(sizeRange = "86–164", suitedFabrics = t("French terry, sweat, knit", "French Terry, Sweat, Strick")) }
        c.patterns.addYardage(hoodie, "110", 90, 150)
        c.patterns.addYardage(hoodie, "128", 120, 150)
        c.photos.setEntityPhoto(at.mtr.sewfar.domain.PhotoOwner.PATTERN, hoodie) { image(0xFF2F6474.toInt(), 0xFFE9DCC4.toInt(), 0).inputStream() }

        val p1 = c.projects.create(t("Hoodie for Josi", "Hoodie für Josi"), ProjectType.SEWING, josi, listOf(hoodie))
        c.projects.setPatternSize(c.projects.patterns(p1).single().id, "110")
        c.projects.addFabric(p1, t("French terry, mustard", "French Terry, senfgelb"), 90)
        c.projects.addFabric(p1, t("Ribbing, petrol", "Bündchen, petrol"), 20)
        c.projects.setStatus(p1, ProjectStatus.IN_PROGRESS)
        c.projects.addNote(p1, t("Cut out all pieces, hood lined with jersey.", "Alle Teile zugeschnitten, Kapuze mit Jersey gefüttert."))
        c.photos.addProjectPhoto(p1, PhotoTag.INSPIRATION) { image(0xFFD9A441.toInt(), 0xFFF5ECD9.toInt(), 1).inputStream() }
        c.photos.addProjectPhoto(p1, PhotoTag.PROGRESS) { image(0xFFD9A441.toInt(), 0xFF2F6474.toInt(), 2).inputStream() }

        val p2 = c.projects.create(t("Striped raglan shirt", "Geringeltes Raglanshirt"), ProjectType.SEWING, max, listOf(raglan))
        c.projects.setPatternSize(c.projects.patterns(p2).single().id, "M")
        c.projects.setStatus(p2, ProjectStatus.DONE)
        c.projects.setLesson(p2, c.projects.patterns(p2).single().id, Ids.builtIn("lesson_tag", "lengthen"), true)
        c.projects.setLesson(p2, c.projects.patterns(p2).single().id, Ids.builtIn("lesson_tag", "fabric_suited"), true)
        c.projects.edit(p2) { it.copy(rating = 5, completionNotes = t("Add 3 cm to the sleeves next time.", "Ärmel nächstes Mal 3 cm länger.")) }
        c.photos.addProjectPhoto(p2, PhotoTag.FINISHED) { image(0xFF8E4E33.toInt(), 0xFFFBF8F3.toInt(), 0).inputStream() }

        val p3 = c.projects.create(t("Tote bag for the market", "Tasche für den Markt"), ProjectType.SEWING, null, listOf(tote))
        c.photos.addProjectPhoto(p3, PhotoTag.INSPIRATION) { image(0xFF5E6135.toInt(), 0xFFE3E6AE.toInt(), 1).inputStream() }
        val p4 = c.projects.create(t("Patch jeans knee", "Jeansknie flicken"), ProjectType.REPAIR, max)
        c.projects.setStatus(p4, ProjectStatus.PAUSED)
        val p5 = c.projects.create(t("Linen summer dress", "Leinen-Sommerkleid"), ProjectType.SEWING, josi)
        c.projects.setStatus(p5, ProjectStatus.IDEA)

        val juki = c.machines.create("Juki MO-654DE", MachineType.OVERLOCKER)
        c.machines.edit(juki) { it.copy(brand = "Juki", model = "MO-654DE") }
        val fields = c.machines.fields(juki)
        fun f(key: String) = fields.first { it.builtinKey == key }.id
        val hem = c.lists.entries(ListKind.PURPOSE).first { it.builtinKey == "hem" }.id
        val seam = c.lists.entries(ListKind.PURPOSE).first { it.builtinKey == "seam" }.id
        val s1 = c.setups.create(juki, t("Hem – French terry", "Saum – French Terry"), hem, "French Terry")
        mapOf("threads" to "4", "tension_left_needle" to "4", "tension_right_needle" to "4", "tension_upper_looper" to "3.5",
            "tension_lower_looper" to "3.5", "differential_feed" to "1.3", "stitch_length" to "3").forEach { (k, v) -> c.setups.setValue(s1, f(k), v) }
        c.setups.edit(s1) { it.copy(result = at.mtr.sewfar.domain.SetupResult.GOOD) }
        val s2 = c.setups.create(juki, t("Seam – jersey", "Naht – Jersey"), seam, "Jersey")
        mapOf("threads" to "4", "differential_feed" to "1.5", "stitch_length" to "2.5").forEach { (k, v) -> c.setups.setValue(s2, f(k), v) }
        val label: (MachineFieldEntity) -> String = { it.customName ?: if (de) germanField(it.builtinKey) else englishField(it.builtinKey) }
        c.setups.link(s1, p1, label = label)
    }

    private fun englishField(key: String?) = key.orEmpty().replace('_', ' ').replaceFirstChar { it.uppercase() }
    private fun germanField(key: String?) = when (key) {
        "threads" -> "Fäden"; "tension_left_needle" -> "Spannung linke Nadel"; "tension_right_needle" -> "Spannung rechte Nadel"
        "tension_upper_looper" -> "Spannung oberer Greifer"; "tension_lower_looper" -> "Spannung unterer Greifer"
        "differential_feed" -> "Differentialtransport"; "stitch_length" -> "Stichlänge"; "cutting_width" -> "Schnittbreite"
        "needle" -> "Nadel"; "thread" -> "Garn"; else -> englishField(key)
    }

    /** A fabric-like picture: stripes (0), dots (1) or a check (2). */
    private fun image(main: Int, accent: Int, kind: Int): ByteArray {
        val size = 900
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(main)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        when (kind) {
            0 -> for (y in 0 until size step 90) canvas.drawRect(0f, y.toFloat(), size.toFloat(), y + 36f, paint)
            1 -> for (y in 45 until size step 90) for (x in 45 until size step 90) canvas.drawCircle(x.toFloat() + (y / 90 % 2) * 45, y.toFloat(), 14f, paint)
            else -> {
                paint.alpha = 140
                for (i in 0 until size step 120) {
                    canvas.drawRect(i.toFloat(), 0f, i + 50f, size.toFloat(), paint)
                    canvas.drawRect(0f, i.toFloat(), size.toFloat(), i + 50f, paint)
                }
            }
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
