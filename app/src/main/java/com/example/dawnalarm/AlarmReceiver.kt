package com.example.dawnalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Runs when Android reaches a scheduled moment. It posts a full-screen notification,
 * which opens [AlarmActivity] even when the phone is locked.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val phase = intent.getStringExtra(AlarmScheduler.EXTRA_PHASE) ?: return
        val alarms = AlarmStore.load(context)
        val alarm = alarms.find { it.id == id } ?: return

        if (phase == AlarmScheduler.PHASE_RING) {
            // One-time alarms switch off after ringing. Repeating alarms schedule their next ring.
            val updated = alarms.map { if (it.id == id && it.days.isEmpty()) it.copy(enabled = false) else it }
            AlarmStore.save(context, updated)
            AlarmScheduler.scheduleAll(context)
        }

        showNotification(context, alarm, phase)
    }

    private fun showNotification(context: Context, alarm: Alarm, phase: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH)
        )

        val activityIntent = Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            .putExtra(AlarmScheduler.EXTRA_PHASE, phase)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingActivity = PendingIntent.getActivity(
            context,
            alarm.id * 10 + 7,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (phase == AlarmScheduler.PHASE_SUNRISE) "Sunrise starting" else "Alarm"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(formatTime(alarm.hour, alarm.minute))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pendingActivity, true)
            .setContentIntent(pendingActivity)
            .setOngoing(true)
            .build()

        nm.notify(alarm.id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "alarms"
    }
}
