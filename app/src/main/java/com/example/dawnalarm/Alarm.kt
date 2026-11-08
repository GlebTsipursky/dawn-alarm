package com.example.dawnalarm

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/** Formats a 24-hour time as a 12-hour time with AM or PM, for example 7:05 AM or 12:30 PM. */
fun formatTime(hour: Int, minute: Int): String {
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(displayHour, minute, suffix)
}

/**
 * One alarm. An empty [days] set means the alarm rings once.
 * [sunriseMinutes] is how long before the alarm the screen starts brightening (0 turns this off).
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val days: Set<DayOfWeek> = emptySet(),
    val sunriseMinutes: Int = 30,
    val vibrate: Boolean = true,
) {
    /** The next time this alarm should ring after [now], or null if it is off. */
    fun nextTrigger(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (!enabled) return null
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (days.isNotEmpty() && date.dayOfWeek !in days) continue
            val candidate = ZonedDateTime.of(date, LocalTime.of(hour, minute), now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    fun describe(): String {
        val repeat = if (days.isEmpty()) {
            "Once"
        } else {
            DayOfWeek.values()
                .filter { it in days }
                .joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3) }
        }
        val sunrise = if (sunriseMinutes > 0) "Sunrise $sunriseMinutes min before" else "No sunrise"
        return "$repeat | $sunrise"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("enabled", enabled)
        .put("days", JSONArray(days.map { it.name }))
        .put("sunriseMinutes", sunriseMinutes)
        .put("vibrate", vibrate)

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daysArray = o.getJSONArray("days")
            val days = (0 until daysArray.length())
                .map { DayOfWeek.valueOf(daysArray.getString(it)) }
                .toSet()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                enabled = o.getBoolean("enabled"),
                days = days,
                sunriseMinutes = o.getInt("sunriseMinutes"),
                vibrate = o.getBoolean("vibrate"),
            )
        }
    }
}
