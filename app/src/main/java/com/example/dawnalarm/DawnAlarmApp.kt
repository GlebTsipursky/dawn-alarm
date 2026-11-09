package com.example.dawnalarm

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the details of any crash to the phone before the app closes, so the next time the
 * app opens, the main screen can show the error text for copying.
 */
class DawnAlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                CrashLog.save(this, "Time: $stamp\nThread: ${thread.name}\n\n$trace")
            } catch (ignored: Exception) {
                // Saving the report must never hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

/** Stores the most recent crash report in the app's private preferences. */
object CrashLog {
    private const val PREFS = "dawn_alarm_crash"
    private const val KEY = "last_crash"

    fun save(context: Context, report: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, report).commit()
    }

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
