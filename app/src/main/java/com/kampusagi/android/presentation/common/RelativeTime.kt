package com.kampusagi.android.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.kampusagi.android.R
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** How long ago something happened, in the coarse units a feed shows. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data class Days(val value: Int) : RelativeTime
    data class Date(val text: String) : RelativeTime
    data object Unknown : RelativeTime

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("tr-TR"))

        /** [timestamp] is the ISO-8601 value PostgREST returns for a timestamptz. */
        fun of(timestamp: String, now: Instant, zone: ZoneId): RelativeTime {
            val instant = try {
                OffsetDateTime.parse(timestamp).toInstant()
            } catch (e: DateTimeParseException) {
                return Unknown
            }
            val elapsed = Duration.between(instant, now)
            return when {
                elapsed.isNegative || elapsed.toMinutes() < 1 -> JustNow
                elapsed.toHours() < 1 -> Minutes(elapsed.toMinutes().toInt())
                elapsed.toDays() < 1 -> Hours(elapsed.toHours().toInt())
                elapsed.toDays() < 7 -> Days(elapsed.toDays().toInt())
                else -> Date(DATE.format(instant.atZone(zone)))
            }
        }
    }
}

@Composable
fun RelativeTime.text(): String = when (this) {
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes, value, value)
    is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours, value, value)
    is RelativeTime.Days -> pluralStringResource(R.plurals.time_days, value, value)
    is RelativeTime.Date -> text
    RelativeTime.Unknown -> ""
}

@Composable
fun relativeTime(timestamp: String): String =
    RelativeTime.of(timestamp, Instant.now(), ZoneId.systemDefault()).text()
