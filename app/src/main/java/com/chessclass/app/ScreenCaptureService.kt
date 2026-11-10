package com.chessclass.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var frameCount = 0
    private var lastBoardSummary = "Tahta henüz algılanmadı"
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        BoardVision.reset()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ekran yakalama başlatılıyor..."))

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                releaseCapture(stopProjection = false)
                stopSelf()
            }
        }
        projection?.registerCallback(projectionCallback!!, mainHandler)

        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2
        )
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                frameCount++
                if (frameCount == 1 || frameCount % 15 == 0) {
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixels = IntArray(image.width * image.height)
                        for (y in 0 until image.height) {
                            for (x in 0 until image.width) {
                                val i = y * plane.rowStride + x * plane.pixelStride
                                val red = buffer.get(i).toInt() and 0xFF
                                val green = buffer.get(i + 1).toInt() and 0xFF
                                val blue = buffer.get(i + 2).toInt() and 0xFF
                                pixels[y * image.width + x] = android.graphics.Color.rgb(red, green, blue)
                            }
                        }
                        val bitmap = Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
                        val scan = BoardVision.scan(bitmap)
                        lastBoardSummary = scan.summary
                        sendBroadcast(Intent(ACTION_BOARD_UPDATED).setPackage(packageName)
                            .putExtra(EXTRA_FEN, scan.fen)
                            .putExtra(EXTRA_SUMMARY, scan.summary))
                        bitmap.recycle()
                    } catch (_: Exception) {
                        lastBoardSummary = "Tahta görüntüsü okunamadı"
                    }
                    val managerNotification = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    managerNotification.notify(
                        NOTIFICATION_ID,
                        buildNotification("$lastBoardSummary • $frameCount kare")
                    )
                }
                image.close()
            }
        }, mainHandler)

        virtualDisplay = projection?.createVirtualDisplay(
            "ChessClassScreenCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )
        return START_NOT_STICKY
    }

    private fun buildNotification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("CHESS CLASS ekran yakalama")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Ekran yakalama", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun releaseCapture(stopProjection: Boolean = true) {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projectionCallback?.let { projection?.unregisterCallback(it) }
        projectionCallback = null
        if (stopProjection) projection?.stop()
        projection = null
    }

    override fun onDestroy() {
        releaseCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_BOARD_UPDATED = "com.chessclass.app.BOARD_UPDATED"
        const val EXTRA_FEN = "board_fen"
        const val EXTRA_SUMMARY = "board_summary"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_DATA = "capture_result_data"
        private const val CHANNEL_ID = "chess_class_capture"
        private const val NOTIFICATION_ID = 2401
    }
}
