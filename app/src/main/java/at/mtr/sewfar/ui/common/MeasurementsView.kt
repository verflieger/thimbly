package at.mtr.sewfar.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.mtr.sewfar.R
import at.mtr.sewfar.domain.MeasurementFields
import at.mtr.sewfar.domain.Measurements
import java.text.DecimalFormat

/** Names of body measurements (SPEC 4.2); keys match `MeasurementFields`. */
@StringRes
fun measurementLabel(key: String): Int = when (key) {
    "height" -> R.string.m_height
    "bust" -> R.string.m_bust
    "waist" -> R.string.m_waist
    "hips" -> R.string.m_hips
    "back_length" -> R.string.m_back_length
    "arm_length" -> R.string.m_arm_length
    "inside_leg" -> R.string.m_inside_leg
    else -> error("Unknown measurement $key")
}

/** "74,5" / "74.5" by locale, no trailing ",0". */
fun formatCm(value: Double): String = DecimalFormat("0.#").format(value)

/** The measured values two per row ("Waist 58 cm") under [title]; only filled fields are shown. */
@Composable
fun MeasurementsView(title: String, measurements: Measurements, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MeasurementFields.filter { it in measurements.valuesCm }.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(measurementLabel(key)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.value_cm, formatCm(measurements.valuesCm.getValue(key))), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
