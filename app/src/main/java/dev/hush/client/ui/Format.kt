package dev.hush.client.ui

import dev.hush.client.discord.snowflakeTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun timeFormatter(use24h: Boolean, seconds: Boolean = false): DateTimeFormatter = when {
        use24h && seconds -> DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
        use24h -> DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        seconds -> DateTimeFormatter.ofPattern("h:mm:ss a", Locale.getDefault())
        else -> DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    }

    fun day(id: String): LocalDate = Instant.ofEpochMilli(snowflakeTime(id)).atZone(zone).toLocalDate()

    fun dayLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))

    fun messageTime(id: String, use24h: Boolean, short: Boolean): String {
        val zdt = Instant.ofEpochMilli(snowflakeTime(id)).atZone(zone)
        val time = zdt.format(timeFormatter(use24h))
        if (short) return time
        val today = LocalDate.now(zone)
        val date = zdt.toLocalDate()
        return when (date) {
            today -> "Today at $time"
            today.minusDays(1) -> "Yesterday at $time"
            else -> zdt.format(DateTimeFormatter.ofPattern("M/d/yy", Locale.getDefault())) + " " + time
        }
    }

    /** Renders Discord's <t:unix:style> timestamps. */
    fun discordTimestamp(epochMs: Long, style: String?, use24h: Boolean): String {
        val zdt = Instant.ofEpochMilli(epochMs).atZone(zone)
        fun pattern(p: String) = zdt.format(DateTimeFormatter.ofPattern(p, Locale.getDefault()))
        val time = zdt.format(timeFormatter(use24h))
        return when (style) {
            "t" -> time
            "T" -> zdt.format(timeFormatter(use24h, seconds = true))
            "d" -> pattern("M/d/yyyy")
            "D" -> pattern("MMMM d, yyyy")
            "F" -> pattern("EEEE, MMMM d, yyyy") + " " + time
            "R" -> relative(epochMs)
            else -> pattern("MMMM d, yyyy") + " " + time
        }
    }

    fun relative(epochMs: Long): String {
        val diff = epochMs - System.currentTimeMillis()
        val seconds = abs(diff) / 1000
        val (value, unit) = when {
            seconds < 60 -> seconds to "second"
            seconds < 3_600 -> seconds / 60 to "minute"
            seconds < 86_400 -> seconds / 3_600 to "hour"
            seconds < 2_592_000 -> seconds / 86_400 to "day"
            seconds < 31_536_000 -> seconds / 2_592_000 to "month"
            else -> seconds / 31_536_000 to "year"
        }
        val label = "$value $unit${if (value == 1L) "" else "s"}"
        return if (diff >= 0) "in $label" else "$label ago"
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
