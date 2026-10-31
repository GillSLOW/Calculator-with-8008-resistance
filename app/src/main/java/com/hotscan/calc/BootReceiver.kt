package com.hotscan.calc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts scanning after a reboot when "Auto-start after reboot" is enabled.
 *
 * Note: Android 12+ restricts starting a location foreground service from the background
 * (which is what a boot receiver is), so this may be blocked by the OS until the app is
 * opened once. It's best-effort.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).flag(Prefs.F_AUTOSTART)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java))
        } catch (_: Exception) {
            // Blocked by background-start restrictions; user can open the app to resume.
        }
    }
}
