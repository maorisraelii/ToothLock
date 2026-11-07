package com.toothlock.app

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    lateinit var prefs: ToothPreferences
    lateinit var detector: WatchAndSleepDetector
    private lateinit var webView: WebView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (!notifGranted) {
            Toast.makeText(this, "יש לאשר הרשאת התראות כדי לקבל תזכורות לשעון!", Toast.LENGTH_LONG).show()
        }
        notifyWebStateChanged()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = ToothPreferences(this)
        detector = WatchAndSleepDetector(this)

        setupWebView()
        requestAppPermissions()

        // Ensure background bedtime check is scheduled
        ToothCheckWorker.scheduleDailyBedtimeCheck(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.web_view)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                notifyWebStateChanged()
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Expose Native Android Bridge to Web View
        webView.addJavascriptInterface(ToothLockBridge(this), "ToothLockBridge")

        // Load the local HTML bundle (which includes the PriceRadar-style live OTA engine)
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    override fun onResume() {
        super.onResume()
        notifyWebStateChanged()
    }

    fun notifyWebStateChanged() {
        runOnUiThread {
            webView.evaluateJavascript("if (window.onNativeStateChanged) window.onNativeStateChanged();", null)
        }
    }

    fun syncWidgetAndTile() {
        runOnUiThread {
            ToothWidgetProvider.updateAllWidgets(this)
            ToothLockTileService.requestTileUpdate(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
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

    fun pinHomeScreenWidget() {
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

    fun checkForUpdates(manual: Boolean) {
        val pInfo = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }
        val currentVer = pInfo?.versionName ?: "1.0.0"

        lifecycleScope.launch {
            if (manual) {
                Toast.makeText(this@MainActivity, "בודק עדכוני APK ב-GitHub Releases...", Toast.LENGTH_SHORT).show()
            }
            val release = OtaUpdater.checkLatestRelease()
            if (release != null && OtaUpdater.isNewerVersion(currentVer, release.tagName)) {
                showUpdateDialog(release)
            } else if (manual) {
                Toast.makeText(this@MainActivity, "גרסת ה-APK מעודכנת לגרסה האחרונה ($currentVer) ✨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(release: ReleaseInfo) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🚀 עדכון קובץ APK זמין (${release.tagName})")
        val notes = if (release.body.isNotBlank()) release.body else "גרסה חדשה של ToothLock זמינה להתקנה."
        builder.setMessage("$notes\n\nהאם ברצונך להוריד ולהתקין את קובץ ה-APK כעת?")
        builder.setPositiveButton("הורד ועדכן כעת") { _, _ ->
            startOtaDownload(release)
        }
        builder.setNegativeButton("סגור", null)
        builder.show()
    }

    @Suppress("DEPRECATION")
    private fun startOtaDownload(release: ReleaseInfo) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setTitle("מוריד קובץ APK...")
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

    // =========================================================================
    // JAVASCRIPT INTERFACE BRIDGE
    // Connects index.html Web UI & PriceRadar-style Live OTA to Kotlin logic
    // =========================================================================
    class ToothLockBridge(private val activity: MainActivity) {

        @JavascriptInterface
        fun isBrushedToday(): Boolean {
            return activity.prefs.isBrushedToday()
        }

        @JavascriptInterface
        fun setBrushedToday(brushed: Boolean) {
            activity.runOnUiThread {
                activity.prefs.setBrushedToday(brushed)
                if (brushed) {
                    NotificationHelper.cancelNotification(activity)
                }
                activity.syncWidgetAndTile()
            }
        }

        @JavascriptInterface
        fun getStreak(): Int {
            return activity.prefs.getStreak()
        }

        @JavascriptInterface
        fun getTargetHour(): Int {
            return activity.prefs.targetHour
        }

        @JavascriptInterface
        fun getTargetMinute(): Int {
            return activity.prefs.targetMinute
        }

        @JavascriptInterface
        fun setTargetTime(hour: Int, minute: Int) {
            activity.runOnUiThread {
                activity.prefs.targetHour = hour
                activity.prefs.targetMinute = minute
                ToothCheckWorker.scheduleDailyBedtimeCheck(activity)
                val mFormatted = String.format("%02d", minute)
                Toast.makeText(activity, "שעת היעד עודכנה ל-$hour:$mFormatted!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun isGalaxyWatchConnected(): Boolean {
            return runBlocking(Dispatchers.IO) {
                activity.detector.isSmartWatchConnected()
            }
        }

        @JavascriptInterface
        fun getWatchName(): String {
            return runBlocking(Dispatchers.IO) {
                activity.detector.getWatchDiagnostic().deviceName.ifBlank { "Galaxy Watch" }
            }
        }

        @JavascriptInterface
        fun isSleepModeActive(): Boolean {
            return activity.detector.isSleepModeActive()
        }

        @JavascriptInterface
        fun sendWatchAlert(alertIndex: Int) {
            activity.lifecycleScope.launch {
                val isSleeping = activity.detector.isSleepModeActive()
                NotificationHelper.sendWatchFocusedAlert(activity, alertIndex, isSleeping)
            }
        }

        @JavascriptInterface
        fun sendDelayedWatchAlert(delaySeconds: Int) {
            activity.lifecycleScope.launch {
                delay((delaySeconds * 1000).toLong())
                val isSleeping = activity.detector.isSleepModeActive()
                NotificationHelper.sendWatchFocusedAlert(activity, 1, isSleeping)
            }
        }

        @JavascriptInterface
        fun sendEmergencyPhoneAlarm() {
            activity.runOnUiThread {
                NotificationHelper.sendEmergencyPhoneAlarm(activity)
            }
        }

        @JavascriptInterface
        fun openGalaxyWearable() {
            activity.runOnUiThread {
                try {
                    val intent = activity.packageManager.getLaunchIntentForPackage("com.samsung.android.app.watchmanager")
                    if (intent != null) {
                        activity.startActivity(intent)
                    } else {
                        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        }
                        activity.startActivity(fallback)
                    }
                } catch (_: Exception) {
                    Toast.makeText(activity, "לא ניתן לפתוח את אפליקציית Galaxy Wearable", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun pinHomeScreenWidget() {
            activity.runOnUiThread {
                activity.pinHomeScreenWidget()
            }
        }

        @JavascriptInterface
        fun triggerHaptic(type: String) {
            activity.runOnUiThread {
                val view = activity.window.decorView
                if (type == "heavy") {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }

        @JavascriptInterface
        fun getNativeBuildCode(): Int {
            val pInfo = try { activity.packageManager.getPackageInfo(activity.packageName, 0) } catch (_: Exception) { null }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo?.longVersionCode?.toInt() ?: 130
            } else {
                @Suppress("DEPRECATION")
                pInfo?.versionCode ?: 130
            }
        }

        @JavascriptInterface
        fun getNativeVersionName(): String {
            val pInfo = try { activity.packageManager.getPackageInfo(activity.packageName, 0) } catch (_: Exception) { null }
            return pInfo?.versionName ?: "1.0.30"
        }

        @JavascriptInterface
        fun downloadAndInstallApk() {
            activity.runOnUiThread {
                activity.checkForUpdates(manual = true)
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
