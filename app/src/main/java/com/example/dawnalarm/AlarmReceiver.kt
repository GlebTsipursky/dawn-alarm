package com.example.dawnalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Runs when Android reaches a scheduled moment. It opens [AlarmActivity] directly when the
 * app has the "Display over other apps" permission, so the alarm screen covers whatever app
 * is open. It also posts a full-screen notification as a backup, which opens the alarm
 * screen when the phone is locked.
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

        // With the overlay permission, Android allows this app to start the alarm screen
        // directly, so it appears over other apps and not only as a notification.
        if (Settings.canDrawOverlays(context)) {
            context.startActivity(alarmScreenIntent(context, alarm, phase))
        }
    }

    private fun alarmScreenIntent(context: Context, alarm: Alarm, phase: String): Intent =
        Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            .putExtra(AlarmScheduler.EXTRA_PHASE, phase)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun showNotification(context: Context, alarm: Alarm, phase: String) {
        createChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)

        val pendingActivity = PendingIntent.getActivity(
            context,
            alarm.id * 10 + 7,
            alarmScreenIntent(context, alarm, phase),
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
        /**
         * The alarm notification channel. The "_v2" suffix gives a fresh channel with the
         * settings below, which Android does not change on channels that already exist.
         */
        const val CHANNEL_ID = "alarms_v2"

        /** Creates the channel if needed. It may break through Do Not Disturb, and it is silent because the alarm screen makes the sound. */
        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }
}
