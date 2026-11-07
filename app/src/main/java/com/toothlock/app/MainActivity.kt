package com.toothlock.app

import android.Manifest
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: ToothPreferences
    private lateinit var detector: WatchAndSleepDetector

    // Navigation & Tab Containers
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tabHome: View
    private lateinit var tabLab: View
    private lateinit var tabSettings: View

    // Home Tab Views
    private lateinit var cardStatus: MaterialCardView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusSubtitle: TextView
    private lateinit var tvStreakHeader: TextView
    private lateinit var btnToggleStatus: MaterialButton
    private lateinit var layoutWeekHistory: LinearLayout
    private lateinit var tvWatchChip: TextView
    private lateinit var tvSleepChip: TextView

    // Smart Brush Timer Views
    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTimerZone: TextView
    private lateinit var btnTimerToggle: MaterialButton
    private lateinit var pbTimerProgress: ProgressBar
    private var brushTimer: CountDownTimer? = null
    private var isTimerRunning = false

    // Lab Tab Views
    private lateinit var tvLabWatchDetails: TextView
    private lateinit var btnLabCheckWatch: MaterialButton
    private lateinit var btnLabTestWatchPulse: MaterialButton
    private lateinit var btnLabTestPhoneAlarm: MaterialButton
    private lateinit var tvLabSimStatus: TextView
    private lateinit var btnLabStartSimulation: MaterialButton
    private lateinit var tvLabSleepDetails: TextView
    private var simulationJob: Job? = null

    // Settings Tab Views
    private lateinit var tvSettingsTargetTime: TextView
    private lateinit var btnSettingsChangeTime: MaterialButton
    private lateinit var btnSettingsPinWidget: MaterialButton
    private lateinit var btnSettingsCheckUpdate: MaterialButton
    private lateinit var tvSettingsVersionInfo: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (!notifGranted) {
            Toast.makeText(this, "יש לאשר הרשאת התראות כדי לקבל תזכורות לשעון!", Toast.LENGTH_LONG).show()
        }
        refreshLiveStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = ToothPreferences(this)
        detector = WatchAndSleepDetector(this)

        initViews()
        setupBottomNavigation()
        setupListeners()
        requestAppPermissions()

        updateUiState()
        refreshLiveStatus()

        // Ensure background bedtime check is scheduled
        ToothCheckWorker.scheduleDailyBedtimeCheck(this)

        // Silent background check for OTA updates
        checkForUpdates(manual = false)
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
        refreshLiveStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        brushTimer?.cancel()
        simulationJob?.cancel()
    }

    private fun initViews() {
        bottomNav = findViewById(R.id.bottom_navigation)
        tabHome = findViewById(R.id.tab_content_home)
        tabLab = findViewById(R.id.tab_content_lab)
        tabSettings = findViewById(R.id.tab_content_settings)

        // Home
        cardStatus = findViewById(R.id.card_status)
        ivStatusIcon = findViewById(R.id.iv_status_icon)
        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvStatusSubtitle = findViewById(R.id.tv_status_subtitle)
        tvStreakHeader = findViewById(R.id.tv_streak_header)
        btnToggleStatus = findViewById(R.id.btn_toggle_status)
        layoutWeekHistory = findViewById(R.id.layout_week_history)
        tvWatchChip = findViewById(R.id.tv_watch_chip)
        tvSleepChip = findViewById(R.id.tv_sleep_chip)

        // Timer
        tvTimerDisplay = findViewById(R.id.tv_timer_display)
        tvTimerZone = findViewById(R.id.tv_timer_zone)
        btnTimerToggle = findViewById(R.id.btn_timer_toggle)
        pbTimerProgress = findViewById(R.id.pb_timer_progress)

        // Lab
        tvLabWatchDetails = findViewById(R.id.tv_lab_watch_details)
        btnLabCheckWatch = findViewById(R.id.btn_lab_check_watch)
        btnLabTestWatchPulse = findViewById(R.id.btn_lab_test_watch_pulse)
        btnLabTestPhoneAlarm = findViewById(R.id.btn_lab_test_phone_alarm)
        tvLabSimStatus = findViewById(R.id.tv_lab_sim_status)
        btnLabStartSimulation = findViewById(R.id.btn_lab_start_simulation)
        tvLabSleepDetails = findViewById(R.id.tv_lab_sleep_details)

        // Settings
        tvSettingsTargetTime = findViewById(R.id.tv_settings_target_time)
        btnSettingsChangeTime = findViewById(R.id.btn_settings_change_time)
        btnSettingsPinWidget = findViewById(R.id.btn_settings_pin_widget)
        btnSettingsCheckUpdate = findViewById(R.id.btn_settings_check_update)
        tvSettingsVersionInfo = findViewById(R.id.tv_settings_version_info)
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    tabHome.visibility = View.VISIBLE
                    tabLab.visibility = View.GONE
                    tabSettings.visibility = View.GONE
                    updateUiState()
                    true
                }
                R.id.nav_lab -> {
                    tabHome.visibility = View.GONE
                    tabLab.visibility = View.VISIBLE
                    tabSettings.visibility = View.GONE
                    refreshLabDiagnostics()
                    true
                }
                R.id.nav_settings -> {
                    tabHome.visibility = View.GONE
                    tabLab.visibility = View.GONE
                    tabSettings.visibility = View.VISIBLE
                    updateSettingsUi()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupListeners() {
        // Toggle Brushed Status
        btnToggleStatus.setOnClickListener {
            val current = prefs.isBrushedToday()
            val newStatus = !current
            prefs.setBrushedToday(newStatus)

            if (newStatus) {
                NotificationHelper.cancelNotification(this)
                Toast.makeText(this, "מעולה! סומן שצחצחת שיניים 🪥✨", Toast.LENGTH_SHORT).show()
            }

            updateUiState()
            ToothWidgetProvider.updateAllWidgets(this)
        }

        // Smart 2-Minute Brush Timer
        btnTimerToggle.setOnClickListener {
            if (isTimerRunning) {
                stopBrushTimer()
            } else {
                startBrushTimer()
            }
        }

        // Lab: Check Watch Diagnostic
        btnLabCheckWatch.setOnClickListener {
            refreshLabDiagnostics()
        }

        // Lab: Test Watch-Only Multi-Pulse
        btnLabTestWatchPulse.setOnClickListener {
            lifecycleScope.launch {
                val isSleeping = detector.isSleepModeActive()
                NotificationHelper.sendWatchFocusedAlert(this@MainActivity, alertIndex = 1, isSleeping = isSleeping)
                Toast.makeText(this@MainActivity, "נשלח רטט 5 פעימות לשעון! הטלפון שקט כדי לא להרעיש ⌚✨", Toast.LENGTH_LONG).show()
            }
        }

        // Lab: Test Phone Emergency Alarm
        btnLabTestPhoneAlarm.setOnClickListener {
            NotificationHelper.sendEmergencyPhoneAlarm(this)
            Toast.makeText(this, "אזעקת חירום בטלפון הופעלה (שלב 4)! לחץ על ההתראה לכבות.", Toast.LENGTH_LONG).show()
        }

        // Lab: Full Escalation Simulation
        btnLabStartSimulation.setOnClickListener {
            runEscalationSimulation()
        }

        // Settings: Change Target Bedtime
        btnSettingsChangeTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    prefs.targetHour = hourOfDay
                    prefs.targetMinute = minute
                    updateSettingsUi()
                    ToothCheckWorker.scheduleDailyBedtimeCheck(this)
                    Toast.makeText(this, "שעת היעד עודכנה בהצלחה!", Toast.LENGTH_SHORT).show()
                },
                prefs.targetHour,
                prefs.targetMinute,
                true
            ).show()
        }

        // Settings: Pin Home Screen Widget
        btnSettingsPinWidget.setOnClickListener {
            pinHomeScreenWidget()
        }

        // Settings: Check Updates (OTA)
        btnSettingsCheckUpdate.setOnClickListener {
            checkForUpdates(manual = true)
        }
    }

    private fun updateUiState() {
        val isBrushed = prefs.isBrushedToday()
        val streak = prefs.getStreak()

        tvStreakHeader.text = "🔥 $streak ימים"

        if (isBrushed) {
            ivStatusIcon.setImageResource(R.drawable.ic_check)
            tvStatusTitle.text = "צחצחת היום! נקי ומצוחצח ✨"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            tvStatusSubtitle.text = "כל הכבוד על השמירה על השיניים!"
            btnToggleStatus.text = "בטל סימון (טרם צחצחתי)"
            btnToggleStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red))
        } else {
            ivStatusIcon.setImageResource(R.drawable.ic_toothbrush)
            tvStatusTitle.text = "טרם צחצחת שיניים היום!"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            tvStatusSubtitle.text = "השעון ירעיד את היד שלך בשעת היעד"
            btnToggleStatus.text = "סמן כ'צחצחתי' 🪥✨"
            btnToggleStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green))
        }

        populateWeekHistory()
        updateSettingsUi()
    }

    private fun populateWeekHistory() {
        layoutWeekHistory.removeAllViews()
        val history = prefs.getLast7DaysHistory()
        val inflater = LayoutInflater.from(this)

        for (item in history) {
            val view = inflater.inflate(R.layout.item_day_history, layoutWeekHistory, false)
            val tvLabel = view.findViewById<TextView>(R.id.tv_day_label)
            val flCircle = view.findViewById<FrameLayout>(R.id.fl_day_circle)
            val ivStatus = view.findViewById<ImageView>(R.id.iv_day_status)

            tvLabel.text = item.dayName

            if (item.isBrushed) {
                flCircle.setBackgroundResource(R.drawable.circle_day_green)
                ivStatus.setImageResource(R.drawable.ic_check)
                ivStatus.visibility = View.VISIBLE
            } else {
                flCircle.setBackgroundResource(R.drawable.circle_day_inactive)
                ivStatus.visibility = View.INVISIBLE
            }

            if (item.isToday) {
                tvLabel.setTextColor(ContextCompat.getColor(this, R.color.text_white))
                tvLabel.text = "${item.dayName} (היום)"
            }

            layoutWeekHistory.addView(view)
        }
    }

    private fun startBrushTimer() {
        isTimerRunning = true
        btnTimerToggle.text = "עצור טיימר ■"
        btnTimerToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red))

        brushTimer = object : CountDownTimer(120000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = (millisUntilFinished / 1000).toInt()
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                tvTimerDisplay.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                pbTimerProgress.progress = totalSeconds

                // Update Quadrant instruction every 30 seconds
                when (totalSeconds) {
                    in 91..120 -> tvTimerZone.text = "חלק 1/4: לסת עליונה ימין"
                    in 61..90 -> {
                        tvTimerZone.text = "חלק 2/4: לסת עליונה שמאל"
                        if (totalSeconds == 90) gentleVibrate()
                    }
                    in 31..60 -> {
                        tvTimerZone.text = "חלק 3/4: לסת תחתונה ימין"
                        if (totalSeconds == 60) gentleVibrate()
                    }
                    in 1..30 -> {
                        tvTimerZone.text = "חלק 4/4: לסת תחתונה שמאל"
                        if (totalSeconds == 30) gentleVibrate()
                    }
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                tvTimerDisplay.text = "00:00"
                tvTimerZone.text = "צחצוח הושלם בהצלחה! ✨🦷"
                btnTimerToggle.text = "התחל שוב ▶"
                btnTimerToggle.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
                pbTimerProgress.progress = 0

                // Mark brushed and update streak automatically!
                prefs.setBrushedToday(true)
                NotificationHelper.cancelNotification(this@MainActivity)
                updateUiState()
                ToothWidgetProvider.updateAllWidgets(this@MainActivity)

                longSuccessVibrate()
                Toast.makeText(this@MainActivity, "כל הכבוד! סיימת 2 דקות צחצוח מלאות 🪥🎉", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun stopBrushTimer() {
        brushTimer?.cancel()
        isTimerRunning = false
        tvTimerDisplay.text = "02:00"
        tvTimerZone.text = "30 שנ' לכל רבע פה"
        btnTimerToggle.text = "התחל צחצוח ▶"
        btnTimerToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        pbTimerProgress.progress = 120
    }

    private fun gentleVibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(150)
        }
    }

    private fun longSuccessVibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun refreshLiveStatus() {
        lifecycleScope.launch {
            val isConnected = detector.isSmartWatchConnected()
            val isSleeping = detector.isSleepModeActive()

            tvWatchChip.text = if (isConnected) "שעון: מחובר ✅" else "שעון: לא זוהה ⚠️"
            tvSleepChip.text = if (isSleeping) "שינה: פעיל 🌙" else "שינה: כבוי ☀️"
        }
    }

    private fun refreshLabDiagnostics() {
        lifecycleScope.launch {
            tvLabWatchDetails.text = "סורק התקנים..."
            val diag = detector.getWatchDiagnostic()
            tvLabWatchDetails.text = "${diag.details}\nפרוטוקול: ${diag.connectionType}"

            tvLabSleepDetails.text = detector.getSleepModeDiagnostic()
        }
    }

    private fun runEscalationSimulation() {
        simulationJob?.cancel()
        simulationJob = lifecycleScope.launch {
            btnLabStartSimulation.isEnabled = false
            try {
                tvLabSimStatus.text = "▶ [שלב 1/4]: שולח תזכורת 1 לשעון (רטט ממושך)..."
                NotificationHelper.sendWatchFocusedAlert(this@MainActivity, 1)
                delay(4000)

                tvLabSimStatus.text = "▶ [שלב 2/4]: שולח תזכורת 2 לשעון..."
                NotificationHelper.sendWatchFocusedAlert(this@MainActivity, 2)
                delay(4000)

                tvLabSimStatus.text = "▶ [שלב 3/4]: שולח תזכורת 3 אחרונה לשעון..."
                NotificationHelper.sendWatchFocusedAlert(this@MainActivity, 3)
                delay(4000)

                tvLabSimStatus.text = "💥 [שלב 4/4 - הסלמה!]: 3 התראות לא נענו! מפעיל אזעקת חירום בטלפון!"
                NotificationHelper.sendEmergencyPhoneAlarm(this@MainActivity)
                delay(4000)

                tvLabSimStatus.text = "✅ סימולציית ההסלמה הושלמה בהצלחה! כל השרשרת פועלת תקין."
            } finally {
                btnLabStartSimulation.isEnabled = true
            }
        }
    }

    private fun updateSettingsUi() {
        val h = String.format(Locale.US, "%02d", prefs.targetHour)
        val m = String.format(Locale.US, "%02d", prefs.targetMinute)
        tvSettingsTargetTime.text = "$h:$m"

        val pInfo = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }
        val ver = pInfo?.versionName ?: "1.0.12"
        tvSettingsVersionInfo.text = "ToothLock v$ver • מניעת שינה ללא צחצוח שיניים"
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

    private fun checkForUpdates(manual: Boolean) {
        val pInfo = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }
        val currentVer = pInfo?.versionName ?: "1.0.0"

        lifecycleScope.launch {
            if (manual) {
                Toast.makeText(this@MainActivity, "בודק עדכונים ב-GitHub...", Toast.LENGTH_SHORT).show()
            }
            val release = OtaUpdater.checkLatestRelease()
            if (release != null && OtaUpdater.isNewerVersion(currentVer, release.tagName)) {
                showUpdateDialog(release)
            } else if (manual) {
                Toast.makeText(this@MainActivity, "האפליקציה מעודכנת לגרסה האחרונה ($currentVer) ✨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(release: ReleaseInfo) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🚀 עדכון גרסה חדש זמין (${release.tagName})")
        val notes = if (release.body.isNotBlank()) release.body else "גרסה חדשה של ToothLock זמינה להתקנה."
        builder.setMessage("$notes\n\nהאם ברצונך להוריד ולהתקין את העדכון כעת?")
        builder.setPositiveButton("הורד ועדכן עכשיו") { _, _ ->
            startOtaDownload(release)
        }
        builder.setNegativeButton("הזכר לי מחר", null)
        builder.show()
    }

    @Suppress("DEPRECATION")
    private fun startOtaDownload(release: ReleaseInfo) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setTitle("מוריד עדכון גרסה...")
            setMessage("אנא המתן, מוריד את ToothLock ${release.tagName}...")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val apkFile = OtaUpdater.downloadApk(this@MainActivity, release.downloadUrl) { percent ->
                progressDialog.progress = percent
            }
            progressDialog.dismiss()

            if (apkFile != null && apkFile.exists()) {
                OtaUpdater.installApk(this@MainActivity, apkFile)
            } else {
                Toast.makeText(this@MainActivity, "שגיאה בהורדת העדכון. נסה שוב מאוחר יותר.", Toast.LENGTH_LONG).show()
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
