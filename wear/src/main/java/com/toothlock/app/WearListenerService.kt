package com.toothlock.app

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WearListenerService : WearableListenerService(), SensorEventListener {

    companion object {
        var isCurrentlyWornOnBody: Boolean = true
            private set
    }

    private var sensorManager: SensorManager? = null
    private var offBodySensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        offBodySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/vibrate_pulse" -> {
                triggerWearVibration()
            }
            "/check_on_body" -> {
                reportOnBodyStatus(messageEvent.sourceNodeId)
            }
            "/sync_status" -> {
                val data = String(messageEvent.data, Charsets.UTF_8)
                val prefs = getSharedPreferences("tooth_wear_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("last_sync_data", data).apply()
            }
        }
    }

    private fun triggerWearVibration() {
        // Wake the watch screen and processor
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ToothLock:WearVibrateWakeLock"
        )
        wakeLock?.acquire(8000)

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            // High intensity 5-pulse tactile vibration directly on the watch wrist!
            val pattern = longArrayOf(0, 400, 200, 400, 200, 500, 200, 500, 200, 800)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true) {
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}

        // Launch watch screen so user can see it
        try {
            val launchIntent = Intent(this, WearMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launchIntent)
        } catch (_: Exception) {}
    }

    private fun reportOnBodyStatus(sourceNodeId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messageClient = Wearable.getMessageClient(this@WearListenerService)
                val statusByte = if (isCurrentlyWornOnBody) byteArrayOf(1) else byteArrayOf(0)
                messageClient.sendMessage(sourceNodeId, "/report_on_body", statusByte)
            } catch (_: Exception) {}
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            val value = event.values[0]
            // Standard Wear OS convention: 0.0f = On Body (worn on wrist), 1.0f = Off Body
            isCurrentlyWornOnBody = (value == 0.0f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
