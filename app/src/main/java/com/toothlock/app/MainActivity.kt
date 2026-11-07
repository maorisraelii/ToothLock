package com.toothlock.app

import android.Manifest
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: ToothPreferences
    private lateinit var detector: WatchAndSleepDetector

    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStreakInfo: TextView
    private lateinit var btnToggleStatus: MaterialButton
    private lateinit var tvWatchStatus: TextView
    private lateinit var tvSleepStatus: TextView
    private lateinit var tvTargetTime: TextView
    private lateinit var btnChangeTime: MaterialButton
    private lateinit var btnPinWidget: MaterialButton
    private lateinit var btnTestNotification: MaterialButton
    private lateinit var cardStatus: MaterialCardView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (!notifGranted) {
            Toast.makeText(this, "יש לאשר הרשאת התראות כדי לקבל תזכורות לשעון!", Toast.LENGTH_LONG).show()
        }
        checkWatchAndSleepStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = ToothPreferences(this)
        detector = WatchAndSleepDetector(this)

        initViews()
        requestAppPermissions()
        setupListeners()
        updateUiState()
        checkWatchAndSleepStatus()

        // Ensure background bedtime check is scheduled
        ToothCheckWorker.scheduleDailyBedtimeCheck(this)
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
        checkWatchAndSleepStatus()
    }

    private fun initViews() {
        ivStatusIcon = findViewById(R.id.iv_status_icon)
        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvStreakInfo = findViewById(R.id.tv_streak_info)
        btnToggleStatus = findViewById(R.id.btn_toggle_status)
        tvWatchStatus = findViewById(R.id.tv_watch_status)
        tvSleepStatus = findViewById(R.id.tv_sleep_status)
        tvTargetTime = findViewById(R.id.tv_target_time)
        btnChangeTime = findViewById(R.id.btn_change_time)
        btnPinWidget = findViewById(R.id.btn_pin_widget)
        btnTestNotification = findViewById(R.id.btn_test_notification)
        cardStatus = findViewById(R.id.card_status)
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupListeners() {
        // Toggle brushed status
        btnToggleStatus.setOnClickListener {
            val current = prefs.isBrushedToday()
            val newStatus = !current
            prefs.setBrushedToday(newStatus)

            if (newStatus) {
                NotificationHelper.cancelNotification(this)
                Toast.makeText(this, "כל הכבוד! סומן שצחצחת שיניים 🪥✨", Toast.LENGTH_SHORT).show()
            }

            updateUiState()
            ToothWidgetProvider.updateAllWidgets(this)
        }

        // Change target bedtime
        btnChangeTime.setOnClickListener {
            val currentHour = prefs.targetHour
            val currentMinute = prefs.targetMinute

            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    prefs.targetHour = hourOfDay
                    prefs.targetMinute = minute
                    updateTimeDisplay()
                    ToothCheckWorker.scheduleDailyBedtimeCheck(this)
                    Toast.makeText(this, "שעת היעד עודכנה בהצלחה!", Toast.LENGTH_SHORT).show()
                },
                currentHour,
                currentMinute,
                true
            ).show()
        }

        // Pin home screen widget
        btnPinWidget.setOnClickListener {
            pinHomeScreenWidget()
        }

        // Test notification immediately
        btnTestNotification.setOnClickListener {
            lifecycleScope.launch {
                val isConnected = detector.isSmartWatchConnected()
                val isSleeping = detector.isSleepModeActive()
                NotificationHelper.sendToothAlert(
                    context = this@MainActivity,
                    isWatchConnected = isConnected,
                    isSleeping = isSleeping
                )
                Toast.makeText(this@MainActivity, "נשלחה התראת בדיקה לשעון ולטלפון! בדוק את היד שלך ⌚", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUiState() {
        val isBrushed = prefs.isBrushedToday()
        val streak = prefs.getStreak()

        if (isBrushed) {
            ivStatusIcon.setImageResource(R.drawable.ic_check)
            tvStatusTitle.text = "צחצחת היום! נקי ומצוחצח ✨"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            btnToggleStatus.text = "בטל סימון (טרם צחצחתי)"
            btnToggleStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red))
        } else {
            ivStatusIcon.setImageResource(R.drawable.ic_toothbrush)
            tvStatusTitle.text = "טרם צחצחת שיניים היום!"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            btnToggleStatus.text = "סמן כ'צחצחתי' 🪥✨"
            btnToggleStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green))
        }

        tvStreakInfo.text = "רצף צחצוח: $streak ימים ברציפות 🔥"
        updateTimeDisplay()
    }

    private fun updateTimeDisplay() {
        val h = String.format(Locale.US, "%02d", prefs.targetHour)
        val m = String.format(Locale.US, "%02d", prefs.targetMinute)
        tvTargetTime.text = "$h:$m"
    }

    private fun checkWatchAndSleepStatus() {
        lifecycleScope.launch {
            val isConnected = detector.isSmartWatchConnected()
            val isSleeping = detector.isSleepModeActive()

            tvWatchStatus.text = if (isConnected) {
                "שעון סמסונג / Wear OS: מחובר ✅ (מוכן לקבלת רטט)"
            } else {
                "שעון סמסונג: לא זוהה כרגע ⚠️ (ההתראה תצלצל בטלפון)"
            }

            tvSleepStatus.text = if (isSleeping) {
                "מצב שינה / Bedtime Mode: פעיל 🌙 (זוהתה שינה!)"
            } else {
                "מצב שינה: לא פעיל (ער)"
            }
        }
    }

    private fun pinHomeScreenWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, ToothWidgetProvider::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
                Toast.makeText(this, "הנח את הווידג'ט במסך הבית שלך", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "הוסף את הווידג'ט דרך לחיצה ארוכה במסך הבית", Toast.LENGTH_LONG).show()
    }
}
