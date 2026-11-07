package com.toothlock.app

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class ToothLockTileService : TileService() {

    companion object {
        fun requestTileUpdate(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    requestListeningState(
                        context,
                        ComponentName(context, ToothLockTileService::class.java)
                    )
                }
            } catch (e: Exception) {
                // Ignore if tile not placed
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = ToothPreferences(this)
        val currentStatus = prefs.isBrushedToday()
        val newStatus = !currentStatus
        prefs.setBrushedToday(newStatus)

        if (newStatus) {
            NotificationHelper.cancelNotification(this)
            vibrateTap(true)
        } else {
            vibrateTap(false)
        }

        updateTileState()
        ToothWidgetProvider.updateAllWidgets(this)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = ToothPreferences(this)
        val isBrushed = prefs.isBrushedToday()
        val streak = prefs.getStreak()

        if (isBrushed) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "צוחצח! ✨"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "רצף: $streak ימים"
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_check)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "טרם צוחצח"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "לחץ לסימון"
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_toothbrush)
        }

        tile.updateTile()
    }

    private fun vibrateTap(success: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (success) {
                    VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40), -1)
                } else {
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            // Ignore vibration error
        }
    }
}
