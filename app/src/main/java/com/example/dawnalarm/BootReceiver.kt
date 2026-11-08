package com.example.dawnalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Android clears scheduled alarms on restart, so this puts them back after the phone boots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}
