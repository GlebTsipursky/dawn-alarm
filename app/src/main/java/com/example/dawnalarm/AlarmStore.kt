package com.example.dawnalarm

import android.content.Context
import org.json.JSONArray

/**
 * Saves the alarm list on the phone. Alarms are stored as text in the app's private
 * preferences file, which no other app can read.
 */
object AlarmStore {
    private const val PREFS_NAME = "dawn_alarm_prefs"
    private const val KEY_ALARMS = "alarms"

    fun load(context: Context): List<Alarm> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { Alarm.fromJson(array.getJSONObject(it)) }
    }

    fun save(context: Context, alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS, array.toString())
            .apply()
    }
}
