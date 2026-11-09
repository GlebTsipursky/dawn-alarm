package com.example.dawnalarm

import android.app.NotificationManager
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * The full-screen screen. In sunrise mode it slowly raises screen brightness and shows
 * a warm background. In ring mode it plays the alarm sound and vibrates until dismissed.
 */
class AlarmActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var rampTask: Runnable? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var torchCameraId: String? = null
    private var savedAlarmVolume: Int? = null
    private var alarmId = -1

    private val progress = mutableFloatStateOf(0f)
    private val ringing = mutableStateOf(false)
    private val timeLabel = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen and turn the screen on, so the alarm is visible without unlocking.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                AlarmScreen(
                    time = timeLabel.value,
                    ringing = ringing.value,
                    progress = progress.floatValue,
                    onDismiss = { dismiss() },
                    onSnooze = { snooze() }
                )
            }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val alarm = AlarmStore.load(this).find { it.id == alarmId }
        if (alarm == null) {
            finish()
            return
        }
        timeLabel.value = formatTime(alarm.hour, alarm.minute)
        if (intent.getStringExtra(AlarmScheduler.EXTRA_PHASE) == AlarmScheduler.PHASE_SUNRISE &&
            alarm.sunriseMinutes > 0
        ) {
            startSunrise(alarm)
        } else {
            startRinging(alarm)
        }
    }

    private fun startSunrise(alarm: Alarm) {
        stopRamp()
        ringing.value = false
        val totalMs = alarm.sunriseMinutes * 60_000L
        val startedAt = System.currentTimeMillis()
        val task = object : Runnable {
            override fun run() {
                val fraction = ((System.currentTimeMillis() - startedAt).toFloat() / totalMs)
                    .coerceIn(0f, 1f)
                progress.floatValue = fraction
                setScreenBrightness(0.01f + fraction * 0.99f)
                if (fraction < 1f) handler.postDelayed(this, 5_000L)
            }
        }
        rampTask = task
        task.run()
    }

    private fun startRinging(alarm: Alarm) {
        stopRamp()
        progress.floatValue = 1f
        setScreenBrightness(1f)
        ringing.value = true
        raiseAlarmVolume()
        playSound()
        if (alarm.vibrate) vibrate()
        startTorch()
    }

    /**
     * Sets the alarm volume to its maximum so the alarm is audible even if the phone's volume
     * was turned down. The previous level is restored when the alarm stops.
     */
    private fun raiseAlarmVolume() {
        val audio = getSystemService(AudioManager::class.java)
        if (savedAlarmVolume == null) {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        }
        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )
    }

    private fun restoreAlarmVolume() {
        val previous = savedAlarmVolume ?: return
        getSystemService(AudioManager::class.java)
            .setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        savedAlarmVolume = null
    }

    /** Turns on the phone's flashlight (the back camera's light) and keeps it on until the alarm stops. */
    private fun startTorch() {
        try {
            val cameraManager = getSystemService(CameraManager::class.java)
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager.setTorchMode(cameraId, true)
            torchCameraId = cameraId
        } catch (e: Exception) {
            // The phone has no flashlight, or another app is using the camera. The alarm still rings.
        }
    }

    private fun stopTorch() {
        val cameraId = torchCameraId ?: return
        try {
            getSystemService(CameraManager::class.java).setTorchMode(cameraId, false)
        } catch (e: Exception) {
            // Ignore: the flashlight may already be off.
        }
        torchCameraId = null
    }

    private fun playSound() {
        player?.release()
        player = null
        // Some phones have no alarm sound set, so fall back to other sounds. If none works,
        // the alarm still shows its screen and vibrates instead of closing.
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            // Keeps the processor awake while the sound plays, so it continues with the screen locked.
            mediaPlayer.setWakeMode(applicationContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer.setDataSource(this, uri)
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
        } catch (e: Exception) {
            mediaPlayer.release()
        }
    }

    private fun vibrate() {
        vibrator = getSystemService(Vibrator::class.java)
        // Pattern: wait 0 ms, vibrate 800 ms, pause 600 ms, repeat until stopped.
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun setScreenBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    private fun stopRamp() {
        rampTask?.let { handler.removeCallbacks(it) }
        rampTask = null
    }

    private fun stopSoundAndVibration() {
        player?.stop()
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        stopTorch()
        restoreAlarmVolume()
    }

    private fun dismiss() {
        stopRamp()
        stopSoundAndVibration()
        getSystemService(NotificationManager::class.java).cancel(alarmId)
        finish()
    }

    private fun snooze() {
        AlarmScheduler.snooze(this, alarmId, minutes = 5)
        dismiss()
    }

    override fun onDestroy() {
        stopRamp()
        stopSoundAndVibration()
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(
    time: String,
    ringing: Boolean,
    progress: Float,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val background = lerp(Color(0xFF2A0A00), Color(0xFFFFB347), progress)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (ringing) "Wake up" else "Sunrise in progress",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
        )
        Text(text = time, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Dismiss")
        }
        if (ringing) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                Text("Snooze 5 minutes", color = Color.White)
            }
        }
    }
}
