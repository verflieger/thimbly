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
 * patterns, people with measurements and machines (in English or German) for store screenshots. Does nothing if
 * projects exist.
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
        val today = LocalDate.now()

        // People with measurements: Lena grows, so she was measured twice.
        val lena = c.persons.create("Lena")
        c.persons.edit(lena) { it.copy(notes = t("7 years old, loves purple and pockets.", "7 Jahre, liebt Lila und Taschen.")) }
        c.persons.saveMeasurements(lena, mapOf("height" to 116.0, "bust" to 59.0, "waist" to 54.0, "hips" to 62.0, "back_length" to 26.5, "arm_length" to 39.0), today.minusMonths(7))
        c.persons.saveMeasurements(lena, mapOf("height" to 122.0, "bust" to 61.0, "waist" to 55.5, "hips" to 65.0, "back_length" to 28.0, "arm_length" to 42.0, "inside_leg" to 53.0), today.minusDays(9))
        val anna = c.persons.create("Anna")
        c.persons.saveMeasurements(anna, mapOf("height" to 168.0, "bust" to 92.0, "waist" to 74.0, "hips" to 99.0, "back_length" to 41.5, "arm_length" to 60.0), today.minusMonths(2))
        val me = c.persons.create(t("Me", "Ich"))
        c.persons.saveMeasurements(me, mapOf("height" to 163.0, "bust" to 101.0, "waist" to 88.0, "hips" to 106.0), today.minusMonths(1))

        // Patterns
        val hoodie = c.patterns.create("Hoodie Kai")
        c.patterns.edit(hoodie) { it.copy(designer = "Studio Faden", notes = t("Size M fits Anna, add 2 cm to the sleeves.", "Größe M passt Anna, Ärmel 2 cm länger.")) }
        c.photos.add(PhotoOwner.PATTERN, hoodie) { image(0xFF2F6474.toInt(), 0xFFE9DCC4.toInt(), 0).inputStream() }
        val frieda = c.patterns.create(t("Twirl dress Frieda", "Drehkleid Frieda"))
        c.patterns.edit(frieda) { it.copy(designer = "Kleine Naht") }
        val mila = c.patterns.create(t("Wrap dress Mila", "Wickelkleid Mila"))
        val coat = c.patterns.create(t("Hooded coat Juna", "Kapuzenmantel Juna"))

        // Machines and their settings
        val overlocker = c.machines.create(t("My overlocker", "Meine Overlock"), MachineType.OVERLOCKER)
        val jersey = c.machines.createSetup(overlocker, t("Jersey seam", "Jerseynaht"))
        mapOf("threads" to "4", "tension_left_needle" to "4", "tension_right_needle" to "4", "tension_upper_looper" to "3.5",
            "tension_lower_looper" to "3.5", "differential_feed" to "1.3", "stitch_length" to "2.5", "cutting_width" to "6")
            .forEach { (k, v) -> c.machines.setValue(jersey, k, t(v, v.replace(".", ","))) }
        val sewing = c.machines.create(t("Sewing machine", "Nähmaschine"), MachineType.SEWING_MACHINE)
        val topstitch = c.machines.createSetup(sewing, t("Topstitching", "Absteppen"))
        mapOf("stitch" to t("Triple straight", "Dreifach-Gerade"), "stitch_length" to "3.5", "needle_tension" to "5", "foot" to t("Edge foot", "Kantenfuß"), "needle" to "Jeans 90")
            .forEach { (k, v) -> c.machines.setValue(topstitch, k, t(v, v.replace(".", ","))) }
        val hem = c.machines.createSetup(sewing, t("Twin needle hem", "Zwillingsnadel-Saum"))
        mapOf("stitch" to t("Straight", "Gerade"), "stitch_length" to "3", "needle_tension" to "3", "needle" to "Stretch 4,0/75")
            .forEach { (k, v) -> c.machines.setValue(hem, k, t(v, v.replace(".", ","))) }

        // Done months ago, between Lena's two measurings: it keeps showing the older measurements.
        val summer = p.create(t("Summer dress for Lena", "Sommerkleid für Lena"))
        p.edit(summer) { it.copy(personId = lena, notes = t("Cotton poplin with cherries, lined bodice.", "Popeline mit Kirschen, Oberteil gefüttert.")) }
        p.addPattern(summer, frieda)
        p.addTime(summer, today.minusMonths(5).minusDays(4), 150)
        p.addTime(summer, today.minusMonths(5), 110)
        p.setStatus(summer, ProjectStatus.DONE)
        p.edit(summer) { it.copy(startedOn = today.minusMonths(5).minusDays(4), finishedOn = today.minusMonths(5), rating = 5,
            learned = t("Stay-stitch the neckline before attaching the lining.", "Halsausschnitt vor dem Futter stabilisieren.")) }
        c.photos.add(PhotoOwner.PROJECT, summer) { image(0xFFB5443B.toInt(), 0xFFFBF8F3.toInt(), 1).inputStream() }

        val wrap = p.create(t("Wrap dress", "Wickelkleid"))
        p.edit(wrap) { it.copy(personId = me) }
        p.addPattern(wrap, mila)
        p.addTime(wrap, today.minusDays(40), 240)
        p.addSetup(wrap, hem)
        p.setStatus(wrap, ProjectStatus.DONE)
        p.edit(wrap) { it.copy(rating = 4, learned = t("Viscose shifts: cut single layer with weights.", "Viskose verrutscht: einlagig mit Gewichten zuschneiden.")) }
        c.photos.add(PhotoOwner.PROJECT, wrap) { image(0xFF8E4E33.toInt(), 0xFFFBF8F3.toInt(), 0).inputStream() }

        // In progress, with the timer running
        val twirl = p.create(t("Twirl dress for Lena", "Drehkleid für Lena"))
        p.edit(twirl) { it.copy(personId = lena, notes = t("Purple jersey, size 122, skirt 4 cm longer.", "Lila Jersey, Gr. 122, Rock 4 cm länger.")) }
        p.addPattern(twirl, frieda)
        p.addSetup(twirl, jersey)
        p.addSetup(twirl, hem)
        p.addTime(twirl, today.minusDays(2), 75)
        p.addTime(twirl, today.minusDays(1), 50)
        p.startTimer(twirl)
        c.photos.add(PhotoOwner.PROJECT, twirl) { image(0xFF6E4A8E.toInt(), 0xFFF1E6F7.toInt(), 1).inputStream() }

        val hoodieAnna = p.create(t("Hoodie for Anna", "Hoodie für Anna"))
        p.edit(hoodieAnna) { it.copy(personId = anna, notes = t("Sweat fabric, cuffs from the stash.", "Sweat, Bündchen aus dem Vorrat.")) }
        p.addPattern(hoodieAnna, hoodie)
        p.addSetup(hoodieAnna, jersey)
        p.addTime(hoodieAnna, today.minusDays(6), 95)
        p.setStatus(hoodieAnna, ProjectStatus.IN_PROGRESS)
        c.photos.add(PhotoOwner.PROJECT, hoodieAnna) { image(0xFFD9A441.toInt(), 0xFF2F6474.toInt(), 2).inputStream() }

        // Planned
        val winter = p.create(t("Winter coat for Lena", "Wintermantel für Lena"))
        p.edit(winter) { it.copy(personId = lena) }
        p.addPattern(winter, coat)
        val tote = p.create(t("Market tote", "Einkaufstasche"))
        p.addSetup(tote, topstitch)
        c.photos.add(PhotoOwner.PROJECT, tote) { image(0xFF5E6135.toInt(), 0xFFE3E6AE.toInt(), 0).inputStream() }
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
