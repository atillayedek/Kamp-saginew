package com.kampusagi.android.presentation.requirement

import com.kampusagi.android.R
import com.kampusagi.android.domain.model.RequirementCategory
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

fun RequirementCategory.labelRes(): Int = when (this) {
    RequirementCategory.SPORTS -> R.string.category_sports
    RequirementCategory.STUDY -> R.string.category_study
    RequirementCategory.PROJECT -> R.string.category_project
    RequirementCategory.TRANSPORT -> R.string.category_transport
    RequirementCategory.ITEM -> R.string.category_item
    RequirementCategory.EVENT -> R.string.category_event
    RequirementCategory.HOUSING -> R.string.category_housing
    RequirementCategory.OTHER -> R.string.category_other
}

private val DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("tr-TR"))

/** "27 Eylül 2026, 18:00" in the device's time zone; null for a missing or unreadable value. */
fun formatStartsAt(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (iso == null) return null
    return try {
        DATE_TIME.format(OffsetDateTime.parse(iso).atZoneSameInstant(zone))
    } catch (e: DateTimeParseException) {
        null
    }
}
