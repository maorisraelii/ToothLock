package com.toothlock.app

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.provider.Settings
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable

data class WatchDiagnostic(
    val isConnected: Boolean,
    val deviceName: String,
    val connectionType: String,
    val details: String
)

class WatchAndSleepDetector(private val context: Context) {

    /**
     * Checks if a smartwatch (Galaxy Watch / Wear OS) is currently connected
     */
    suspend fun isSmartWatchConnected(): Boolean {
        return getWatchDiagnostic().isConnected
    }

    /**
     * Returns detailed diagnostic information for the Test Lab.
     */
    suspend fun getWatchDiagnostic(): WatchDiagnostic {
        // 1. Google Play Services Wearable API
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = Tasks.await(nodeClient.connectedNodes)
            if (nodes.isNotEmpty()) {
                val node = nodes.first()
                return WatchDiagnostic(
                    isConnected = true,
                    deviceName = node.displayName,
                    connectionType = "Google Wearable DataLayer",
                    details = "שעון מחובר פעיל: ${node.displayName} (Node ID: ${node.id.take(8)}...)"
                )
            }
        } catch (_: Exception) {}

        // 2. Bluetooth profile check
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

            if (adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices ?: emptySet()
                for (device in bonded) {
                    val name = device.name ?: "Unknown Device"
                    val lower = name.lowercase()
                    val isWatch = lower.contains("watch") || lower.contains("galaxy") ||
                            device.bluetoothClass?.majorDeviceClass == 0x0700

                    if (isWatch) {
                        val a2dpState = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                        val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                        if (a2dpState == BluetoothProfile.STATE_CONNECTED || headset == BluetoothProfile.STATE_CONNECTED) {
                            return WatchDiagnostic(
                                isConnected = true,
                                deviceName = name,
                                connectionType = "Bluetooth Companion",
                                details = "שעון מחובר ב-Bluetooth: $name (סטטוס: מחובר פעיל)"
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {}

        return WatchDiagnostic(
            isConnected = false,
            deviceName = "לא זוהה שעון",
            connectionType = "None",
            details = "לא זוהה שעון סמסונג מחובר כרגע. ההתראה תעבור לטלפון."
        )
    }

    /**
     * Detects if Samsung Sleep Mode (Bedtime Mode) or Do Not Disturb is active.
     */
    fun isSleepModeActive(): Boolean {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (notificationManager != null) {
                val filter = notificationManager.currentInterruptionFilter
                if (filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                    filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                    filter == NotificationManager.INTERRUPTION_FILTER_NONE
                ) {
                    return true
                }
            }

            val zenMode = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
            if (zenMode > 0) {
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    fun getSleepModeDiagnostic(): String {
        val isSleeping = isSleepModeActive()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val filterName = when (notificationManager?.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "רגיל (ער, הכל מורשה)"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "עדיפויות (ייתכן מצב שינה)"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "אזעקות בלבד (מצב שינה פעיל 🌙)"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "השתק מלא (DND)"
            else -> "לא ידוע"
        }
        val zenMode = try {
            Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
        } catch (_: Exception) { 0 }

        return "מצב: ${if (isSleeping) "שינה פעיל 🌙" else "ער ☀️"} | סינון DND: $filterName | Zen Mode: $zenMode"
    }
}
