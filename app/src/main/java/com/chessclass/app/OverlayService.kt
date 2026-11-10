package com.chessclass.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        bubbleParams = WindowManager.LayoutParams(
            dp(58), dp(58), overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(16) }

        bubble = TextView(this).apply {
            text = "★\nG"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.rgb(183, 132, 35))
            setOnClickListener { togglePanel() }
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            val touchSlop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = bubbleParams.x
                        startY = bubbleParams.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = (startX - (event.rawX - downX)).toInt().coerceAtLeast(0)
                        bubbleParams.y = startY + (event.rawY - downY).toInt()
                        windowManager.updateViewLayout(view, bubbleParams)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = abs(event.rawX - downX) > touchSlop ||
                            abs(event.rawY - downY) > touchSlop
                        if (!moved) performClick()
                        true
                    }
                    else -> true
                }
            }
        }
        windowManager.addView(bubble, bubbleParams)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun addMenuOption(box: LinearLayout, label: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = TextView(this).apply {
            text = " "
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                action()
                status.text = "✓"
                status.setTextColor(0xFF70D69A.toInt())
            }
        }
        row.addView(button)
        row.addView(status)
        box.addView(row)
    }

    private fun stopScreenCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        closePanel()
    }

    private fun runStockfish(status: TextView) {
        status.text = "Analiz ediliyor..."
        Thread {
            val result = runCatching { StockfishEngine(this).analyzeStartPosition() }
                .getOrElse { "Hata: " + it.message }
            mainHandler.post { status.text = result }
        }.start()
    }

    private fun addDashboardAction(box: LinearLayout, label: String, action: (TextView) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val status = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(0xFFFFD166.toInt())
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action(status) }
        }
        row.addView(button)
        row.addView(status)
        box.addView(row)
    }

    private fun closePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xEE101B2D.toInt())
            addView(TextView(this@OverlayService).apply {
                text = "★  G"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFD166.toInt())
                gravity = Gravity.CENTER
            })
            addDashboardAction(this, "Stockfish'i Test Et") { status -> runStockfish(status) }
            addDashboardAction(this, "Ekran Yakalamayı Durdur") { status ->
                stopScreenCapture()
                status.text = "Ekran yakalama durduruldu"
            }
            addDashboardAction(this, "Paneli Kapat") { closePanel() }
            addDashboardAction(this, "Yüzen G Düğmesini Kapat") { stopSelf() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType, 0, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(16) }
        panel = box
        windowManager.addView(box, params)
    }

    override fun onDestroy() {
        closePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
