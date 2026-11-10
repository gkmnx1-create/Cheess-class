package com.chessclass.app

import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
    private var suggestionView: TextView? = null
    private var suggestionParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var statusView: TextView? = null
    private var analysisIndicator: TextView? = null
    private var captureIndicator: TextView? = null
    private var captureActionTitle: TextView? = null
    private var captureActionDetail: TextView? = null
    private var closeIndicator: TextView? = null
    private var captureActive = false
    private var analysisSucceeded = false
    private var latestFen: String? = null
    private var latestSummary = "Tahta henüz algılanmadı"
    private var latestAnalysis = ""
    private var latestMoveSuggestion: String? = null
    private var lastAnalyzedPosition = ""
    @Volatile private var analysisInProgress = false
    @Volatile private var analysisGeneration = 0L
    private var receiverRegistered = false
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val mainHandler = Handler(Looper.getMainLooper())

    private val boardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenCaptureService.ACTION_CAPTURE_STATUS) {
                captureActive = intent.getBooleanExtra(ScreenCaptureService.EXTRA_CAPTURE_ACTIVE, false)
                refreshIndicators()
                return
            }
            if (intent?.action != ScreenCaptureService.ACTION_BOARD_UPDATED) return
            latestFen = intent.getStringExtra(ScreenCaptureService.EXTRA_FEN)
            latestSummary = intent.getStringExtra(ScreenCaptureService.EXTRA_SUMMARY) ?: "Tahta okundu"
            val incomingKey = latestFen?.split(" ")?.take(4)?.joinToString(" ")
            if (incomingKey != lastAnalyzedPosition) {
                analysisSucceeded = false
                // Never leave a recommendation for an older board position visible.
                hideSuggestion()
                latestAnalysis = if (latestFen.isNullOrBlank())
                    "Tahta doğrulanıyor • Güncel konum bekleniyor"
                else
                    "⏳ Yeni konum algılandı • Güncel hamle hesaplanacak…"
            }
            refreshIndicators()
            statusView?.text = displayStatus()
            val fen = latestFen
            if (fen.isNullOrBlank()) hideSuggestion()
            if (!fen.isNullOrBlank()) {
                val positionKey = fen.split(" ").take(4).joinToString(" ")
                if (positionKey != lastAnalyzedPosition && !analysisInProgress) {
                    startAnalysis(fen, positionKey)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_BOARD_UPDATED)
            addAction(ScreenCaptureService.ACTION_CAPTURE_STATUS)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(boardReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(boardReceiver, filter)
        receiverRegistered = true
        overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        bubbleParams = WindowManager.LayoutParams(
            dp(56), dp(56), overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            x = (screenWidth - dp(56 + 230 + 10 + 14)).coerceAtLeast(dp(8))
            y = ((screenHeight - dp(56)) / 2).coerceAtLeast(dp(8))
        }

        bubble = TextView(this).apply {
            text = "G"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(0xFFB8862B.toInt(), 0xFFFFD66B.toInt(), 28)
            elevation = dp(8).toFloat()
            contentDescription = "GX panelini aç"
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
                        val maxBubbleX = (resources.displayMetrics.widthPixels - bubbleParams.width - dp(230 + 10 + 8)).coerceAtLeast(0)
                        val maxBubbleY = (resources.displayMetrics.heightPixels - bubbleParams.height).coerceAtLeast(0)
                        bubbleParams.x = (startX + (event.rawX - downX)).toInt().coerceIn(0, maxBubbleX)
                        bubbleParams.y = (startY + (event.rawY - downY)).toInt().coerceIn(0, maxBubbleY)
                        // Move only the touchable G bubble during the drag.
                        // Repositioning the second overlay on every touch event causes jank.
                        windowManager.updateViewLayout(view, bubbleParams)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = abs(event.rawX - downX) > touchSlop ||
                            abs(event.rawY - downY) > touchSlop
                        if (moved) {
                            // Snap the information window to the bubble once the drag ends.
                            suggestionParams?.let { params ->
                                params.x = bubbleParams.x + bubbleParams.width + dp(10)
                                params.y = bubbleParams.y
                                suggestionView?.let { suggestion ->
                                    try { windowManager.updateViewLayout(suggestion, params) } catch (_: Exception) {}
                                }
                            }
                        } else {
                            togglePanel()
                        }
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

    private fun displayStatus(): String {
        val raw = if (latestAnalysis.isBlank()) latestSummary else latestSummary + "\n" + latestAnalysis
        val lower = raw.lowercase()
        val diagnosis = when {
            lower.contains("ekran boyutu uygun değil") ->
                "HATA • EKRAN\nEkran görüntüsü analiz için çok küçük. Uygun ekran boyutu bekleniyor."
            lower.contains("ekran görüntüsü işlenemedi") ->
                "HATA • EKRAN İŞLEME\n" + raw + "\nEkran yakalama veya görüntü işleme aşamasında hata oluştu; yakalamayı yeniden başlat."
            lower.contains("tahta örneklenemedi") ->
                "HATA • TAHTA OKUMA\nTahta bulundu ancak kareler okunamadı. Tahtanın tamamının görünür olduğundan emin ol."
            lower.contains("bazı karelerden emin değil") ->
                "HATA • TAŞ TANIMA\nBazı karelerdeki taşlar net seçilemedi. Görüntü netleşince yeniden denenecek."
            lower.contains("konumu doğrulayamadı") || lower.contains("konum doğrulanamadı") ||
                lower.contains("doğrulamadan geçmedi") ->
                "HATA • KONUM DOĞRULAMA\n" + raw
            lower.contains("fen'e çevrilemedi") ->
                "HATA • POZİSYON OLUŞTURMA\nOkunan taş dizilimi satranç konumuna dönüştürülemedi."
            lower.contains("hamle sırası") && lower.contains("bilinmiyor") ->
                "BİLGİ • HAMLE SIRASI\nKimin oynayacağı henüz belirlenemedi. Bir hamle daha algılanması bekleniyor."
            lower.contains("hata:") ->
                "HATA • HAMLE MOTORU\n" + raw.substringAfter("Hata:", raw).trim() +
                    "\nMotor yeniden denenebilir; sorun sürerse teknik kayıt incelenmeli."
            lower.contains("bestmove") && lower.contains("döndürmedi") ->
                "HATA • HAMLE MOTORU\nMotor geçerli hamle üretmedi. Analiz yeniden denenecek."
            else -> raw
        }
        return diagnosis
    }

    private fun runStockfish(status: TextView) {
        val fen = latestFen
        if (fen.isNullOrBlank()) {
            status.text = "Tahta henüz doğrulanmadı. Önce tahta algılamasını bekle."
            return
        }
        val key = fen.split(" ").take(4).joinToString(" ")
        startAnalysis(fen, key, force = true)
    }

    private fun startAnalysis(fen: String, positionKey: String, force: Boolean = false) {
        if (analysisInProgress) {
            latestAnalysis = "⏳ Analiz zaten devam ediyor…"
            statusView?.text = displayStatus()
            return
        }
        if (!force && positionKey == lastAnalyzedPosition) return

        analysisInProgress = true
        val requestGeneration = ++analysisGeneration
        lastAnalyzedPosition = positionKey
        latestAnalysis = "⏳ Yeni konum algılandı • Hamle hesaplanıyor…"
        analysisSucceeded = false
        refreshIndicators()
        statusView?.text = displayStatus()

        Thread {
            val raw = runCatching { StockfishEngine(this).analyzeFen(fen, 14) }
                .getOrElse { "Hata: " + (it.message ?: "Stockfish başlatılamadı") }
            val bestMove = Regex("bestmove\\s+(\\S+)").find(raw)?.groupValues?.get(1)
            val resultText = when {
                raw.startsWith("Hata:") -> raw
                bestMove == null -> "Hata: Motor geçerli bir hamle döndürmedi. $raw"
                bestMove == "0000" || bestMove == "(none)" -> "Bu konumda yasal hamle bulunamadı. Tahta algılamasını kontrol et."
                else -> "✓ $bestMove"
            }
            mainHandler.post {
                if (requestGeneration != analysisGeneration) return@post
                analysisInProgress = false
                val currentFen = latestFen
                val currentKey = currentFen?.split(" ")?.take(4)?.joinToString(" ")
                if (currentFen.isNullOrBlank()) {
                    latestAnalysis = "Tahta doğrulanamadı • Yeni konum bekleniyor"
                    lastAnalyzedPosition = ""
                    analysisSucceeded = false
                    hideSuggestion()
                } else if (currentKey != positionKey) {
                    latestAnalysis = "⏳ Tahta değişti • Güncel konum analiz ediliyor…"
                    lastAnalyzedPosition = positionKey
                    startAnalysis(currentFen, currentKey!!)
                } else {
                    latestAnalysis = resultText
                    analysisSucceeded = bestMove != null && bestMove != "0000" && bestMove != "(none)" && !raw.startsWith("Hata:")
                    if (analysisSucceeded) showSuggestion(formatTurkishMove(currentFen!!, bestMove!!)) else hideSuggestion()
                }
                refreshIndicators()
                statusView?.text = displayStatus()
            }
        }.start()
    }

    private fun stopScreenCapture(status: TextView) {
        analysisGeneration++
        analysisInProgress = false
        stopService(Intent(this, ScreenCaptureService::class.java))
        latestFen = null
        latestAnalysis = ""
        latestMoveSuggestion = null
        lastAnalyzedPosition = ""
        captureActive = false
        analysisSucceeded = false
        hideSuggestion()
        refreshIndicators()
        status.text = "✓ Ekran yakalama durduruldu"
    }

    private fun closeApplication(status: TextView) {
        status.text = "✓ GX kapatılıyor…"
        stopService(Intent(this, ScreenCaptureService::class.java))
        closePanel()
        stopSelf()
    }

    private fun addAction(
        box: LinearLayout,
        title: String,
        subtitle: String,
        action: (TextView) -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(7))
            background = rounded(0xFF1B2940.toInt(), 0xFF34445F.toInt(), 13)
            isClickable = true
            isFocusable = true
        }
        val heading = label(title, 13f, 0xFFFFD166.toInt(), true)
        val indicator = label("✕", 15f, 0xFFFF6B6B.toInt(), true)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(indicator)
        }
        when {
            title.contains("analiz", ignoreCase = true) -> analysisIndicator = indicator
            title.contains("yakalamayı", ignoreCase = true) -> {
                captureIndicator = indicator
                captureActionTitle = heading
                captureActionDetail = label(subtitle, 10f, 0xFFCFD8E6.toInt())
            }
            else -> closeIndicator = indicator
        }
        val detail = if (title.contains("yakalamayı", ignoreCase = true)) {
            captureActionDetail ?: label(subtitle, 10f, 0xFFCFD8E6.toInt())
        } else label(subtitle, 10f, 0xFFCFD8E6.toInt())
        detail.setPadding(0, dp(2), 0, 0)
        card.addView(titleRow)
        card.addView(detail)
        card.setOnClickListener { action(detail) }
        box.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
    }

    private fun formatTurkishMove(fen: String, uci: String): String {
        if (uci.length < 4) return uci
        val files = "abcdefgh"
        val ranks = "87654321"
        val fromFile = files.indexOf(uci[0])
        val fromRank = ranks.indexOf(uci[1])
        val toSquare = uci.substring(2, 4)
        if (fromFile !in 0..7 || fromRank !in 0..7) return uci
        val rows = fen.substringBefore(" ").split("/")
        if (rows.size != 8) return uci
        var piece: Char? = null
        var fileIndex = 0
        for (ch in rows[fromRank]) {
            if (ch.isDigit()) {
                fileIndex += ch.digitToInt()
            } else {
                if (fileIndex == fromFile) piece = ch
                fileIndex++
            }
        }
        val name = when (piece?.uppercaseChar()) {
            'N' -> "At"
            'B' -> "Fil"
            'R' -> "Kale"
            'Q' -> "Vezir"
            'K' -> "Şah"
            else -> ""
        }
        val promotion = if (uci.length >= 5) " = " + uci[4].uppercaseChar() else ""
        return (if (name.isBlank()) "" else name + " ") + toSquare + promotion
    }

    private fun showSuggestion(move: String) {
        latestMoveSuggestion = move
        refreshFloatingInfo()
    }

    // The floating label beside G is the live status channel: it shows board
    // detection, capture state, analysis progress/errors, and the final move.
    private fun refreshFloatingInfo() {
        val text = if (!latestMoveSuggestion.isNullOrBlank()) {
            "ÖNERİ: $latestMoveSuggestion\n$latestSummary"
        } else {
            displayStatus()
        }
        val view = suggestionView ?: TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxWidth = dp(230)
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = rounded(0xF51B2940.toInt(), 0xFFFFD166.toInt(), 14)
            elevation = dp(10).toFloat()
            isClickable = false
        }.also {
            suggestionView = it
            suggestionParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = bubbleParams.x + bubbleParams.width + dp(10)
                y = bubbleParams.y
            }
            windowManager.addView(it, suggestionParams)
        }
        view.text = text
        view.background = rounded(
            if (!latestMoveSuggestion.isNullOrBlank()) 0xF51B2940.toInt() else 0xF51B2940.toInt(),
            if (!latestMoveSuggestion.isNullOrBlank()) 0xFF63D995.toInt() else 0xFFFFD166.toInt(),
            14
        )
        view.visibility = View.VISIBLE
        suggestionParams?.let { params ->
            params.x = bubbleParams.x + bubbleParams.width + dp(10)
            params.y = bubbleParams.y
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun hideSuggestion() {
        latestMoveSuggestion = null
        refreshFloatingInfo()
    }

    private fun refreshIndicators() {
        refreshFloatingInfo()
        analysisIndicator?.text = if (analysisSucceeded) "✓" else "✕"
        captureIndicator?.text = if (captureActive) "✓" else "✕"
        captureActionTitle?.text = if (captureActive) "Ⅱ  EKRAN YAKALAMAYI DURDUR" else "Ⅱ  EKRAN YAKALAMAYI BAŞLAT"
        captureActionDetail?.text = if (captureActive) "Ekran paylaşımını ve tahta takibini kapat" else "Tahtayı algılamak için ekran paylaşımını başlat"
        closeIndicator?.text = "✓"
        captureIndicator?.setTextColor(if (captureActive) 0xFF63D995.toInt() else 0xFFFF6B6B.toInt())
        analysisIndicator?.setTextColor(if (analysisSucceeded) 0xFF63D995.toInt() else 0xFFFF6B6B.toInt())
        closeIndicator?.setTextColor(0xFF63D995.toInt())
    }

    private fun closePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
        statusView = null
        analysisIndicator = null
        captureIndicator = null
        captureActionTitle = null
        captureActionDetail = null
        closeIndicator = null
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(11), dp(11), dp(9))
            background = rounded(0xF5101929.toInt(), 0xFFB8862B.toInt(), 19)
            elevation = dp(12).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = label("G", 22f, 0xFFFFD166.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = rounded(0xFF34445F.toInt(), 0xFFFFD166.toInt(), 12)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(label("GX", 16f, Color.WHITE, true))
            addView(label("Akıllı satranç yardımcısı", 10f, 0xFFB8C4D6.toInt()))
        }
        header.addView(brand)
        header.addView(titles, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))

        val closeButton = label("✕", 19f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Paneli kapat"
            background = rounded(0xFF34445F.toInt(), 0xFFFFD166.toInt(), 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { closePanel() }
        }
        header.addView(closeButton, LinearLayout.LayoutParams(dp(34), dp(34)))
        box.addView(header)

        val status = label(displayStatus(), 12f, 0xFFFFD166.toInt())
        status.setPadding(dp(2), dp(7), dp(2), dp(1))
        statusView = status
        box.addView(status)

        addAction(box, "♟  KONUM ANALİZİ", "Yalnızca inceleme veya tekrar konumlarında") { detail ->
            runStockfish(detail)
        }
        val captureTitle = if (captureActive) "Ⅱ  EKRAN YAKALAMAYI DURDUR" else "Ⅱ  EKRAN YAKALAMAYI BAŞLAT"
        val captureSubtitle = if (captureActive) "Ekran görüntüsü alımını kapat" else "Tahtayı algılamak için ekran iznini başlat"
        addAction(box, captureTitle, captureSubtitle) { detail ->
            if (captureActive) {
                stopScreenCapture(detail)
            } else {
                detail.text = "İzin ekranı açılıyor…"
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_START_CAPTURE, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(launchIntent)
            }
        }
        addAction(box, "⏻  GX KAPAT", "G düğmesini kaldırır ve ekran yakalamayı durdurur") { detail ->
            closeApplication(detail)
        }

        val hint = label("İlk kullanım: tahtayı başlangıç diziliminde gösterip kalibrasyonun tamamlanmasını bekle.", 9f, 0xFFB8C4D6.toInt())
        hint.setPadding(0, dp(7), 0, 0)
        box.addView(hint)

        val footer = label("Kapatmak için G düğmesine dokun", 9f, 0xFF8998AE.toInt())
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(8), 0, 0)
        box.addView(footer)

        box.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                closePanel()
                true
            } else false
        }

        refreshIndicators()
        val params = WindowManager.LayoutParams(
            dp(245), WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(12) }
        panel = box
        windowManager.addView(box, params)
    }

    override fun onDestroy() {
        closePanel()
        suggestionView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        suggestionView = null
        suggestionParams = null
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        if (receiverRegistered) {
            try { unregisterReceiver(boardReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
