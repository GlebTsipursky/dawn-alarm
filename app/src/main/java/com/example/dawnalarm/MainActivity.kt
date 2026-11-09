package com.example.dawnalarm

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // Tells the checklist to re-read the permission states, for example after a settings page.
        PermissionState.tick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmReceiver.createChannel(this)

        // Android 13 and later: ask permission to show alarm notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // The remaining permissions are listed in the checklist on the main screen, where each
        // one has an Open button. The app does not open settings pages on its own.

        AlarmScheduler.scheduleAll(this)

        setContent {
            MaterialTheme {
                AlarmApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmApp() {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.load(context)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    if (showEditor) {
        AlarmEditor(
            initial = editing,
            newId = (alarms.maxOfOrNull { it.id } ?: 0) + 1,
            onCancel = { showEditor = false },
            onSave = { alarm ->
                val updated = if (alarms.any { it.id == alarm.id }) {
                    alarms.map { if (it.id == alarm.id) alarm else it }
                } else {
                    alarms + alarm
                }
                AlarmStore.save(context, updated)
                AlarmScheduler.schedule(context, alarm)
                alarms = updated
                showEditor = false
            },
            onDelete = {
                val target = editing ?: return@AlarmEditor
                AlarmScheduler.cancel(context, target.id)
                val updated = alarms.filter { it.id != target.id }
                AlarmStore.save(context, updated)
                AlarmScheduler.scheduleAll(context)
                alarms = updated
                showEditor = false
            },
        )
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Dawn Alarm") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    editing = null
                    showEditor = true
                }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        ) { padding ->
            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No alarms yet. Tap + to add one.")
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    item { PermissionChecklist() }
                    item { CrashReportCard() }
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onClick = {
                                editing = alarm
                                showEditor = true
                            },
                            onToggle = { on ->
                                val updated = alarms.map {
                                    if (it.id == alarm.id) it.copy(enabled = on) else it
                                }
                                AlarmStore.save(context, updated)
                                AlarmScheduler.schedule(context, updated.first { it.id == alarm.id })
                                alarms = updated
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Changes each time the app comes to the front, so the checklist re-reads the permission states. */
object PermissionState {
    val tick = mutableIntStateOf(0)
}

private class PermissionCheck(
    val label: String,
    val explanation: String,
    val granted: Boolean,
    val open: () -> Unit,
)

/** Builds the list of settings that can stop an alarm from waking you, with each one's current state. */
private fun permissionChecks(context: Context): List<PermissionCheck> {
    val pkg = context.packageName
    val uri = android.net.Uri.parse("package:$pkg")
    fun open(intent: Intent): () -> Unit = {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val nm = context.getSystemService(NotificationManager::class.java)
    val checks = mutableListOf<PermissionCheck>()

    checks += PermissionCheck(
        label = "Notifications",
        explanation = "Lets the alarm show on the lock screen.",
        granted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        open = open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)),
    )
    val channel = nm.getNotificationChannel(AlarmReceiver.CHANNEL_ID)
    checks += PermissionCheck(
        label = "Alarm notification channel",
        explanation = "Must not be turned off, or the alarm cannot open from a notification.",
        granted = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE,
        open = open(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmReceiver.CHANNEL_ID)
        ),
    )
    checks += PermissionCheck(
        label = "Do Not Disturb access",
        explanation = "Lets the alarm break through Do Not Disturb mode.",
        granted = nm.isNotificationPolicyAccessGranted,
        open = open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)),
    )
    checks += PermissionCheck(
        label = "Display over other apps",
        explanation = "Lets the alarm screen cover whatever app is open. If Android says the app was denied access, " +
            "first open Settings, Apps, Dawn Alarm, tap the three dots at the top right, choose Allow restricted " +
            "settings, confirm with your phone's lock code, then come back and tap Open again.",
        granted = Settings.canDrawOverlays(context),
        open = open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)),
    )
    if (Build.VERSION.SDK_INT >= 34) {
        checks += PermissionCheck(
            label = "Full-screen alarms",
            explanation = "Lets the alarm screen open over the lock screen.",
            granted = nm.canUseFullScreenIntent(),
            open = open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, uri)),
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        checks += PermissionCheck(
            label = "Exact alarms",
            explanation = "Lets alarms fire on the minute instead of a few minutes late.",
            granted = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            open = open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri)),
        )
    }
    checks += PermissionCheck(
        label = "Battery: unrestricted",
        explanation = "Stops Android from putting the app to sleep and delaying or dropping alarms.",
        granted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg),
        open = open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri)),
    )
    return checks
}

@Composable
private fun PermissionChecklist() {
    val context = LocalContext.current
    // Reading the tick makes this list update when the app returns to the front.
    @Suppress("UNUSED_VARIABLE")
    val refresh = PermissionState.tick.intValue
    val checks = permissionChecks(context)
    if (checks.all { it.granted }) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Alarm checklist", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each item below can stop an alarm from waking you. Turn on every item that says it needs action.",
            style = MaterialTheme.typography.bodySmall
        )
        checks.forEach { check ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = check.label + if (check.granted) " (on)" else " (needs action)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(check.explanation, style = MaterialTheme.typography.bodySmall)
                }
                if (!check.granted) {
                    OutlinedButton(onClick = check.open) { Text("Open") }
                }
            }
        }
        Text(
            "Phone makers add their own sleep settings. On Samsung, Xiaomi, Oppo, Vivo, Huawei, and similar phones, " +
                "also open the phone's battery or app settings for Dawn Alarm and set it to unrestricted or " +
                "no restrictions, and turn on auto-start if the phone offers it. On Samsung, also check " +
                "Settings, Battery, Background usage limits, and make sure Dawn Alarm is not listed under " +
                "Sleeping apps or Deep sleeping apps. Menu names vary by phone model. If any other permission " +
                "says the app was denied access, use the same Allow restricted settings step first.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Shows the last crash report, if the app closed unexpectedly, so the text can be copied and sent. */
@Composable
private fun CrashReportCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val current = report ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("The app closed unexpectedly last time", style = MaterialTheme.typography.titleMedium)
        Text(
            "Copy this report and send it to the person helping you build the app.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(current, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Dawn Alarm crash report", current))
            }) {
                Text("Copy report")
            }
            OutlinedButton(onClick = {
                CrashLog.clear(context)
                report = null
            }) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(alarm.hour, alarm.minute),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(text = alarm.describe(), style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    initial: Alarm?,
    newId: Int,
    onCancel: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var hour by remember { mutableIntStateOf(initial?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(initial?.minute ?: 0) }
    var days by remember { mutableStateOf(initial?.days ?: emptySet()) }
    var sunrise by remember { mutableFloatStateOf((initial?.sunriseMinutes ?: 30).toFloat()) }
    var vibrate by remember { mutableStateOf(initial?.vibrate ?: true) }
    var flashlight by remember { mutableStateOf(initial?.flashlight ?: true) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (initial == null) "New alarm" else "Edit alarm") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = {
                TimePickerDialog(context, { _, h, m ->
                    hour = h
                    minute = m
                }, hour, minute, false).show()
            }) {
                Text("Time: " + formatTime(hour, minute))
            }

            Text("Repeat on (leave all off to ring once)")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeek.values().forEach { day ->
                    FilterChip(
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                        label = { Text(day.name.take(3)) }
                    )
                }
            }

            Text("Sunrise light before alarm: ${sunrise.toInt()} minutes (0 turns it off)")
            Slider(
                value = sunrise,
                onValueChange = { sunrise = it },
                valueRange = 0f..30f,
                steps = 5
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate", modifier = Modifier.weight(1f))
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Flashlight (on from the sunrise start, or at alarm time if sunrise is off)",
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = flashlight, onCheckedChange = { flashlight = it })
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSave(
                            Alarm(
                                id = initial?.id ?: newId,
                                hour = hour,
                                minute = minute,
                                // Saving always turns the alarm on, so a changed time rings again.
                                enabled = true,
                                days = days,
                                sunriseMinutes = sunrise.toInt(),
                                vibrate = vibrate,
                                flashlight = flashlight,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
            if (initial != null) {
                TextButton(onClick = onDelete) {
                    Text("Delete alarm")
                }
            }
        }
    }
}
