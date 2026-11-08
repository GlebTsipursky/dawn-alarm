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

    fun scheduleAll(context: Context) {
        AlarmStore.load(context).forEach { schedule(context, it) }
    }

    /** Replaces any existing schedule for this alarm with its next occurrence. */
    fun schedule(context: Context, alarm: Alarm) {
        cancel(context, alarm.id)
        val ringAt = alarm.nextTrigger() ?: return
        if (alarm.sunriseMinutes > 0) {
            val sunriseAt = ringAt.minusMinutes(alarm.sunriseMinutes.toLong())
            setExact(context, sunriseAt.toInstant().toEpochMilli(), alarm.id, PHASE_SUNRISE)
        }
        setExact(context, ringAt.toInstant().toEpochMilli(), alarm.id, PHASE_RING)
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
