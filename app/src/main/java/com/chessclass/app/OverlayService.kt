package com.chessclass.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: Button? = null
    private var panel: LinearLayout? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 20 }

        bubble = Button(this).apply {
            text = "♟ CHESS"
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

    private fun closePanel() {
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panel = null
    }

    private fun togglePanel() {
        if (panel != null) {
            closePanel()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 20)
            setBackgroundColor(0xEE101B2D.toInt())
            addView(TextView(this@OverlayService).apply {
                text = "CHESS CLASS"
                textSize = 20f
                setTextColor(0xFFFFD166.toInt())
            })
            addView(TextView(this@OverlayService).apply {
                text = "Antrenman ve oyun sonrasi analiz paneli.\nTahta tanima ve Stockfish sonraki asamada."
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
            })
            addView(Button(this@OverlayService).apply {
                text = "Paneli kapat"
                setOnClickListener { closePanel() }
            })
            addView(Button(this@OverlayService).apply {
                text = "Yüzen düğmeyi de kapat"
                setOnClickListener { stopSelf() }
            })
        }
        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            0,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 20 }
        panel = box
        windowManager.addView(box, panelParams)
    }

    override fun onDestroy() {
        closePanel()
        bubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}