package com.example.dawnalarm

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13 and later: ask permission to show alarm notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // "Display over other apps": lets the alarm screen cover whatever app is open.
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        }
        // Android 14 and later: the full-screen alarm screen needs permission. Without it, the alarm
        // shows only as a small notification while the phone is locked, so ask for it.
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            }
        }
        // Android 12 and later: if exact alarms are not allowed, open the settings page for it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }

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
                                enabled = initial?.enabled ?: true,
                                days = days,
                                sunriseMinutes = sunrise.toInt(),
                                vibrate = vibrate,
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
