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

class WatchAndSleepDetector(private val context: Context) {

    /**
     * Checks if a smartwatch (Galaxy Watch / Wear OS) is currently connected
     */
    suspend fun isSmartWatchConnected(): Boolean {
        // 1. Try official Wearable API (Works for Galaxy Watch 4, 5, 6, 7 and all Wear OS)
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = Tasks.await(nodeClient.connectedNodes)
            if (nodes.isNotEmpty()) {
                return true
            }
        } catch (_: Exception) {
            // Fallback to Bluetooth inspection
        }

        // 2. Fallback check via Bluetooth bonded devices
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

            if (adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices ?: emptySet()
                for (device in bonded) {
                    val name = device.name?.lowercase() ?: ""
                    val isWatch = name.contains("watch") || name.contains("galaxy") ||
                            device.bluetoothClass?.majorDeviceClass == 0x0700 // Major WEARABLE
                    if (isWatch) {
                        // Check if connected via profile
                        val a2dpState = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                        val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                        if (a2dpState == BluetoothProfile.STATE_CONNECTED || headset == BluetoothProfile.STATE_CONNECTED) {
                            return true
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Android 12+ requires BLUETOOTH_CONNECT permission
        }

        return false
    }

    /**
     * Detects if Samsung Sleep Mode (Bedtime Mode) or Do Not Disturb is active.
     * When Samsung detects sleep, it activates Bedtime Mode (screen turns black, DND activates).
     */
    fun isSleepModeActive(): Boolean {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (notificationManager != null) {
                val filter = notificationManager.currentInterruptionFilter
                // If filter is PRIORITY, ALARMS, or NONE, the device is in DND / Sleep Mode
                if (filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                    filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                    filter == NotificationManager.INTERRUPTION_FILTER_NONE
                ) {
                    return true
                }
            }

            // Also check Global Zen Mode setting
            val zenMode = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
            if (zenMode > 0) {
                return true
            }
        } catch (_: Exception) {
            // Ignore security or missing settings
        }

        return false
    }
}
