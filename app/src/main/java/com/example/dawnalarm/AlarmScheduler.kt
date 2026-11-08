package com.example.dawnalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

/**
 * Asks Android to wake the app at the right times. Each alarm gets up to three
 * scheduled moments: the sunrise start, the ring, and a snooze (only when snoozed).
 */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_PHASE = "phase"
    const val PHASE_SUNRISE = "sunrise"
    const val PHASE_RING = "ring"
    const val PHASE_SNOOZE = "snooze"

    /**
     * Rebuilds the whole schedule from the saved alarms. Android keeps only one "alarm clock"
     * slot per app, so the soonest ring uses that slot (it wakes the phone even in deep sleep),
     * and the other rings use exact alarms.
     */
    fun scheduleAll(context: Context) {
        val alarms = AlarmStore.load(context)
        alarms.forEach { cancel(context, it.id) }
        val rings = alarms
            .mapNotNull { alarm -> alarm.nextTrigger()?.let { alarm to it } }
            .sortedBy { it.second }
        rings.forEachIndexed { index, (alarm, ringAt) ->
            if (alarm.sunriseMinutes > 0) {
                val sunriseAt = ringAt.minusMinutes(alarm.sunriseMinutes.toLong())
                setExact(context, sunriseAt.toInstant().toEpochMilli(), alarm.id, PHASE_SUNRISE)
            }
            val ringMillis = ringAt.toInstant().toEpochMilli()
            if (index == 0) {
                setAlarmClock(context, ringMillis, alarm.id)
            } else {
                setExact(context, ringMillis, alarm.id, PHASE_RING)
            }
        }
    }

    /** Kept for callers that change one alarm; the whole schedule is rebuilt either way. */
    fun schedule(context: Context, @Suppress("UNUSED_PARAMETER") alarm: Alarm) {
        scheduleAll(context)
    }

    private fun setAlarmClock(context: Context, atMillis: Long, id: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        // Tapping the alarm icon in the status bar opens the app.
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(atMillis, showIntent),
            pendingIntent(context, id, PHASE_RING)
        )
    }

    fun snooze(context: Context, id: Int, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setExact(context, at, id, PHASE_SNOOZE)
    }

    fun cancel(context: Context, id: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, id, PHASE_SUNRISE))
        am.cancel(pendingIntent(context, id, PHASE_RING))
        am.cancel(pendingIntent(context, id, PHASE_SNOOZE))
    }

    private fun setExact(context: Context, atMillis: Long, id: Int, phase: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, id, phase)
        // On Android 12 and later, the user must allow exact alarms. Without that permission,
        // the alarm still fires but may be delayed by a few minutes.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun pendingIntent(context: Context, id: Int, phase: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, id)
            .putExtra(EXTRA_PHASE, phase)
        val code = id * 10 + when (phase) {
            PHASE_SUNRISE -> 1
            PHASE_RING -> 2
            else -> 3
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
