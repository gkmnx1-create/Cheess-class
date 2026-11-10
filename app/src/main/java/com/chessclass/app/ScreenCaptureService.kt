package com.chessclass.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var frameCount = 0
    private var lastScanElapsedMs = 0L
    private var lastBoardSummary = "Tahta henüz algılanmadı"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

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

        captureThread = HandlerThread("ChessFrameAnalyzer").apply { start() }
        captureHandler = Handler(captureThread!!.looper)
        // ONNX session creation can be expensive; initialize it on the same worker
        // that processes frames, never on the service/main thread.
        captureHandler?.post {
            BoardVision.initialize(applicationContext)
            BoardVision.reset()
        }
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
        // Lower capture resolution to reduce memory bandwidth and CPU load.
        val captureScale = minOf(1f, 720f / maxOf(metrics.widthPixels, metrics.heightPixels))
        val captureWidth = (metrics.widthPixels * captureScale).toInt().coerceAtLeast(1)
        val captureHeight = (metrics.heightPixels * captureScale).toInt().coerceAtLeast(1)
        imageReader = ImageReader.newInstance(
            captureWidth, captureHeight, PixelFormat.RGBA_8888, 2
        )
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                frameCount++
                val now = SystemClock.elapsedRealtime()
                if (now - lastScanElapsedMs >= SCAN_INTERVAL_MS && isChessClashForeground()) {
                    lastScanElapsedMs = now
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
                        try {
                            val scan = BoardVision.scan(bitmap)
                            lastBoardSummary = "${scan.summary} • Ekran: ${image.width}×${image.height} • Tahta ölçüsü otomatik"
                            sendBroadcast(Intent(ACTION_BOARD_UPDATED).setPackage(packageName)
                                .putExtra(EXTRA_FEN, scan.fen)
                                .putExtra(EXTRA_SUMMARY, lastBoardSummary))
                            Log.d("CHESS_CAPTURE", "scan=${scan.summary}, size=${image.width}x${image.height}, pieces=${scan.recognized}")
                        } finally {
                            bitmap.recycle()
                        }
                    } catch (error: Exception) {
                        lastBoardSummary = "Ekran görüntüsü işlenemedi: ${error.javaClass.simpleName}"
                        Log.e("CHESS_CAPTURE", lastBoardSummary, error)
                        sendBroadcast(Intent(ACTION_BOARD_UPDATED).setPackage(packageName)
                            .putExtra(EXTRA_FEN, null as String?)
                            .putExtra(EXTRA_SUMMARY, lastBoardSummary))
                    }
                    val managerNotification = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    managerNotification.notify(
                        NOTIFICATION_ID,
                        buildNotification("$lastBoardSummary • $frameCount kare")
                    )
                }
                image.close()
            }
        }, captureHandler)

        virtualDisplay = projection?.createVirtualDisplay(
            "ChessClassScreenCapture",
            captureWidth,
            captureHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )
        sendCaptureStatus(virtualDisplay != null)
        return START_NOT_STICKY
    }

    private fun isChessClashForeground(): Boolean {
        val usage = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usage.queryEvents(end - 15_000L, end)
        val event = UsageEvents.Event()
        var foregroundPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundPackage = event.packageName
            }
        }
        return foregroundPackage == CHESS_CLASH_PACKAGE
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

    private fun sendCaptureStatus(active: Boolean) {
        sendBroadcast(Intent(ACTION_CAPTURE_STATUS).setPackage(packageName)
            .putExtra(EXTRA_CAPTURE_ACTIVE, active))
    }

    private fun releaseCapture(stopProjection: Boolean = true) {
        sendCaptureStatus(false)
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        captureHandler?.removeCallbacksAndMessages(null)
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
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
        const val ACTION_CAPTURE_STATUS = "com.chessclass.app.CAPTURE_STATUS"
        const val EXTRA_CAPTURE_ACTIVE = "capture_active"
        const val EXTRA_FEN = "board_fen"
        const val EXTRA_SUMMARY = "board_summary"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_DATA = "capture_result_data"
        private const val CHANNEL_ID = "chess_class_capture"
        private const val NOTIFICATION_ID = 2401
        private const val CHESS_CLASH_PACKAGE = "com.miniclip.chess"
        private const val SCAN_INTERVAL_MS = 1000L
    }
}
