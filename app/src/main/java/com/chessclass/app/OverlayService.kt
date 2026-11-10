package com.chessclass.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
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
            dp(56), dp(56), overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(14) }

        bubble = TextView(this).apply {
            text = "G"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(0xFFB8862B.toInt(), 0xFFFFD66B.toInt(), 28)
            elevation = dp(8).toFloat()
            contentDescription = "CHESS CLASS panelini aç"
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
                        if (!moved) togglePanel()
                        true
                    }
                    else -> true
                }
            }
        }
        windowManager.addView(bubble, bubbleParams)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, stroke: Int = color, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun runStockfish(status: TextView) {
        status.text = "Stockfish çalışıyor…"
        Thread {
            val result = runCatching { StockfishEngine(this).analyzeStartPosition() }
                .getOrElse { "Hata: " + (it.message ?: "Motor başlatılamadı") }
            mainHandler.post { status.text = result }
        }.start()
    }

    private fun stopScreenCapture(status: TextView) {
        stopService(Intent(this, ScreenCaptureService::class.java))
        status.text = "Ekran yakalama durduruldu"
    }

    private fun addAction(
        box: LinearLayout,
        title: String,
        subtitle: String,
        action: (TextView) -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(10))
            background = rounded(0xFF1B2940.toInt(), 0xFF34445F.toInt(), 13)
            isClickable = true
            isFocusable = true
        }
        val heading = label(title, 15f, 0xFFFFD166.toInt(), true)
        val detail = label(subtitle, 11f, 0xFFCFD8E6.toInt())
        detail.setPadding(0, dp(3), 0, 0)
        card.addView(heading)
        card.addView(detail)
        card.setOnClickListener { action(detail) }
        box.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
    }

    private fun closePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(13))
            background = rounded(0xF5101929.toInt(), 0xFFB8862B.toInt(), 19)
            elevation = dp(12).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = label("G", 25f, 0xFFFFD166.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = rounded(0xFF34445F.toInt(), 0xFFFFD166.toInt(), 12)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            addView(label("CHESS CLASS", 16f, Color.WHITE, true))
            addView(label("Hızlı yardımcı panel", 11f, 0xFFB8C4D6.toInt()))
        }
        header.addView(brand)
        header.addView(titles)
        box.addView(header)

        val status = label("Bir işlem seçin", 12f, 0xFFFFD166.toInt())
        status.setPadding(dp(2), dp(10), dp(2), dp(2))
        box.addView(status)

        addAction(box, "♟  Stockfish testi", "Motoru başlangıç konumunda dene") { detail ->
            runStockfish(detail)
        }
        addAction(box, "Ⅱ  Ekran yakalamayı durdur", "Ekran görüntüsü alımını kapat") { detail ->
            stopScreenCapture(detail)
        }

        val footer = label("Kapatmak için G düğmesine dokun", 10f, 0xFF8998AE.toInt())
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(12), 0, 0)
        box.addView(footer)

        val params = WindowManager.LayoutParams(
            dp(270), WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType, 0, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(12) }
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
