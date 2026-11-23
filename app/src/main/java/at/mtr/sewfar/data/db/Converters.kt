package at.mtr.sewfar.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/** Dates without time are stored as ISO `yyyy-MM-dd` text (SPEC 8). Enums use Room's built-in name mapping. */
class Converters {
    @TypeConverter fun dateToText(date: LocalDate?): String? = date?.toString()
    @TypeConverter fun textToDate(text: String?): LocalDate? = text?.let(LocalDate::parse)
}
