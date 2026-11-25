package at.mtr.sewfar.domain

import java.time.LocalDate

/** Body measurement fields (SPEC 4.2), in display order. Keys are stored in `measurement_value.key`: never rename one. */
val MeasurementFields = listOf("height", "bust", "waist", "hips", "back_length", "arm_length", "inside_leg")

/** One measuring of a person: its date and the values in cm by `MeasurementFields` key. */
data class Measurements(val measuredOn: LocalDate, val valuesCm: Map<String, Double>)

/** cm from "74,5", "74.5" or "74"; null if blank, not a number, or not between 1 and 300 cm. */
fun parseCm(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..300.0 }
