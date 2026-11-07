package com.toothlock.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WearMainActivity : Activity(), SensorEventListener {

    private lateinit var ivIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvOnBody: TextView
    private lateinit var btnToggle: Button
    private lateinit var tvStreak: TextView

    private var sensorManager: SensorManager? = null
    private var offBodySensor: Sensor? = null
    private var isWorn: Boolean = true
    private var isBrushed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_main)

        ivIcon = findViewById(R.id.iv_wear_icon)
        tvStatus = findViewById(R.id.tv_wear_status)
        tvOnBody = findViewById(R.id.tv_wear_on_body)
        btnToggle = findViewById(R.id.btn_wear_toggle)
        tvStreak = findViewById(R.id.tv_wear_streak)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        offBodySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

        btnToggle.setOnClickListener {
            toggleBrushed()
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        offBodySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    private fun toggleBrushed() {
        isBrushed = !isBrushed
        vibrateTap(isBrushed)
        updateUi()

        // Notify phone over Google Play Services Wearable
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodeClient = Wearable.getNodeClient(this@WearMainActivity)
                val nodes = Tasks.await(nodeClient.connectedNodes)
                val messageClient = Wearable.getMessageClient(this@WearMainActivity)
                val payload = (if (isBrushed) "1" else "0").toByteArray(Charsets.UTF_8)
                for (node in nodes) {
                    Tasks.await(messageClient.sendMessage(node.id, "/watch_brushed_now", payload))
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateUi() {
        if (isBrushed) {
            ivIcon.setImageResource(R.drawable.ic_check)
            tvStatus.text = "צחצחת היום! ✨"
            tvStatus.setTextColor(Color.parseColor("#00E676"))
            btnToggle.text = "בטל סימון"
            btnToggle.setBackgroundColor(Color.parseColor("#FF5252"))
        } else {
            ivIcon.setImageResource(R.drawable.ic_toothbrush)
            tvStatus.text = "טרם צחצחת שיניים!"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
            btnToggle.text = "צחצחתי עכשיו! ✨"
            btnToggle.setBackgroundColor(Color.parseColor("#00E676"))
        }

        tvOnBody.text = if (isWorn) "ענוד על היד ✅" else "הוסר מפרק היד ⚠️"
        tvOnBody.setTextColor(if (isWorn) Color.parseColor("#80D8FF") else Color.parseColor("#FFB74D"))
    }

    private fun vibrateTap(success: Boolean) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (success) {
                    VibrationEffect.createWaveform(longArrayOf(0, 50, 40, 50), -1)
                } else {
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            val value = event.values[0]
            isWorn = (value == 0.0f)
            runOnUiThread { updateUi() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
