package com.chessclass.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var waitingForOverlayPermission = false
    private var startupStarted = false
    private var waitingForUsageAccess = false
    private var captureOnlyLaunch = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        continueStartup()
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                else startService(serviceIntent)
            } catch (error: Exception) {
                sendCaptureMessage("Ekran yakalama başlatılamadı: " + error.javaClass.simpleName)
            }
        } else {
            sendCaptureMessage("Ekran paylaşım izni verilmedi. Yakalama başlatılmadı.")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureOnlyLaunch = intent.getBooleanExtra(EXTRA_START_CAPTURE, false)
        if (captureOnlyLaunch) {
            // Keep the game visible behind Android's mandatory screen-share consent dialog.
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Keep the requesting activity visible while Android launches
            // the system MediaProjection consent dialog.
            requestScreenCapture()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueStartup()
        }
    }

    private fun continueStartup() {
        if (isFinishing || startupStarted || waitingForOverlayPermission) return
        if (!hasUsageAccess()) {
            waitingForUsageAccess = true
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
            return
        }
        startFloatingUiOnly()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForUsageAccess && hasUsageAccess()) {
            waitingForUsageAccess = false
            continueStartup()
        } else if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false
            startFloatingUiOnly()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun startFloatingUiOnly() {
        if (startupStarted) return
        startupStarted = true
        startService(Intent(this, OverlayService::class.java))
        finish()
    }

    private fun sendCaptureMessage(message: String) {
        sendBroadcast(Intent(ScreenCaptureService.ACTION_BOARD_UPDATED).setPackage(packageName)
            .putExtra(ScreenCaptureService.EXTRA_FEN, null as String?)
            .putExtra(ScreenCaptureService.EXTRA_SUMMARY, message))
    }

    private fun requestScreenCapture() {
        if (startupStarted) return
        startupStarted = true
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        const val EXTRA_START_CAPTURE = "com.chessclass.app.START_CAPTURE"
    }
}