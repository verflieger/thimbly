package at.mtr.sewfar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import at.mtr.sewfar.domain.MachineType
import at.mtr.sewfar.domain.PhotoOwner
import at.mtr.sewfar.domain.ProjectStatus
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
                container.runInApp { fill(container, ::t) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fill(c: AppContainer, t: (String, String) -> String) {
        if (c.projects.observeAll().first().isNotEmpty()) return
        val p = c.projects
        val anna = c.persons.create("Anna")
        val hoodie = c.patterns.create(t("Hoodie Kai", "Hoodie Kai"))
        c.patterns.edit(hoodie) { it.copy(designer = "Studio Faden") }
        val dress = c.patterns.create(t("Wrap dress Mila", "Wickelkleid Mila"))
        c.photos.add(PhotoOwner.PATTERN, hoodie) { image(0xFF2F6474.toInt(), 0xFFE9DCC4.toInt(), 0).inputStream() }

        val overlocker = c.machines.create(t("My overlocker", "Meine Overlock"), MachineType.OVERLOCKER)
        val jersey = c.machines.createSetup(overlocker, t("Jersey seam", "Jerseynaht"))
        mapOf("threads" to "4", "tension_left_needle" to "4", "tension_right_needle" to "4", "differential_feed" to "1.3", "stitch_length" to "2.5")
            .forEach { (k, v) -> c.machines.setValue(jersey, k, v) }
        val sewing = c.machines.create(t("Sewing machine", "Nähmaschine"), MachineType.SEWING_MACHINE)
        val topstitch = c.machines.createSetup(sewing, t("Topstitching denim", "Absteppen Jeans"))
        mapOf("stitch" to t("Triple straight", "Dreifach-Gerade"), "stitch_length" to "3.5", "needle_tension" to "5", "needle" to "Jeans 100")
            .forEach { (k, v) -> c.machines.setValue(topstitch, k, v) }

        val p1 = p.create(t("Hoodie for Anna", "Hoodie für Anna"))
        p.edit(p1) { it.copy(personId = anna, notes = t("Sweat fabric, cuffs from the stash.", "Sweat, Bündchen aus dem Vorrat.")) }
        p.addPattern(p1, hoodie)
        p.addSetup(p1, jersey)
        p.addTime(p1, LocalDate.now().minusDays(3), 95)
        p.addTime(p1, LocalDate.now().minusDays(1), 50)
        p.setStatus(p1, ProjectStatus.IN_PROGRESS)
        c.photos.add(PhotoOwner.PROJECT, p1) { image(0xFFD9A441.toInt(), 0xFF2F6474.toInt(), 2).inputStream() }

        val p2 = p.create(t("Summer dress", "Sommerkleid"))
        p.addPattern(p2, dress)
        p.addTime(p2, LocalDate.now().minusDays(20), 240)
        p.setStatus(p2, ProjectStatus.DONE)
        p.edit(p2) { it.copy(rating = 5, learned = t("Stay-stitch the neckline first.", "Halsausschnitt zuerst stabilisieren.")) }
        c.photos.add(PhotoOwner.PROJECT, p2) { image(0xFF8E4E33.toInt(), 0xFFFBF8F3.toInt(), 0).inputStream() }

        val p3 = p.create(t("Jeans hem", "Jeans kürzen"))
        p.addSetup(p3, topstitch)

        val p4 = p.create(t("Tote bag", "Einkaufstasche"))
        c.photos.add(PhotoOwner.PROJECT, p4) { image(0xFF5E6135.toInt(), 0xFFE3E6AE.toInt(), 1).inputStream() }
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
