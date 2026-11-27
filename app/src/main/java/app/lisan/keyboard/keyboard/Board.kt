package app.lisan.keyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import app.lisan.keyboard.core.KeyboardCore
import app.lisan.keyboard.core.Prefs
import app.lisan.keyboard.core.Tools
import app.lisan.keyboard.core.isArabic

data class Palette(val bg: Int, val end: Int, val key: Int, val fg: Int, val muted: Int,
                   val special: Int, val accent: Int, val onAccent: Int)
private fun c(hex: String) = Color.parseColor("#$hex")
object BoardStyle {
    fun palette(id: String) = when (id) {
        "light" -> Palette(c("EDF1F8"),c("E3EBF6"),c("FFFFFF"),c("15213C"),c("4C5A79"),c("D5E1F2"),c("2D568F"),-1)
        "gradient" -> Palette(c("342064"),c("124A62"),c("554675"),c("FFFFFF"),c("E2D6FF"),c("6C5490"),c("D9B1FF"),c("24123F"))
        "minimal" -> Palette(c("E7E6DF"),c("E7E6DF"),c("F8F7F2"),c("202924"),c("45564D"),c("D4D8CC"),c("2F5748"),-1)
        "forest" -> Palette(c("132C26"),c("203A30"),c("29483D"),c("F4FFEE"),c("B8D4A3"),c("345C49"),c("B5E8A1"),c("112A1E"))
        "glass" -> Palette(c("424C67"),c("2A3449"),c("515D79"),c("F3F6FF"),c("DEE8FF"),c("626F8B"),c("AED5FF"),c("101E30"))
        else -> Palette(c("111B2B"),c("1D2840"),c("273650"),c("F4F7FF"),c("ACBBDC"),c("334462"),c("79E9CB"),c("112D2D"))
    }
    fun type(ctx: Context, font: String): Typeface = FontCatalog.typeface(ctx,font)
    fun backgroundFile(ctx: Context) = java.io.File(ctx.filesDir,"keyboard_background.jpg")
    private var bgCache: Pair<Long,android.graphics.Bitmap>? = null
    fun backgroundBitmap(ctx: Context, stamp: Long): android.graphics.Bitmap? {
        if (stamp <= 0) return null
        bgCache?.let { if (it.first == stamp) return it.second }
        val f = backgroundFile(ctx)
        if (!f.exists()) return null
        val bmp = runCatching { android.graphics.BitmapFactory.decodeFile(f.path) }.getOrNull() ?: return null
        bgCache = stamp to bmp
        return bmp
    }
}

/** Draws a bitmap scaled to cover the bounds (center-crop) with a dark overlay for key legibility. */
class CoverDrawable(private val bmp: android.graphics.Bitmap, private val dim: Int) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val matrix = android.graphics.Matrix()
    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        if (b.width() == 0 || b.height() == 0) return
        val scale = maxOf(b.width().toFloat() / bmp.width, b.height().toFloat() / bmp.height)
        matrix.reset(); matrix.setScale(scale, scale)
        matrix.postTranslate(b.left + (b.width() - bmp.width * scale) / 2f, b.top + (b.height() - bmp.height * scale) / 2f)
        canvas.save(); canvas.clipRect(b)
        canvas.drawBitmap(bmp, matrix, paint)
        canvas.drawColor((dim * 255 / 100).coerceIn(0,255) shl 24)
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}


fun toolName(id: String, ar: Boolean): String {
    val pair = when(id) {
        "translate" -> "Translate" to "ترجمة"; "mic" -> "Microphone" to "الميكروفون"
        "clipboard" -> "Clipboard" to "الحافظة"; "emoji" -> "Emoji" to "الإيموجي"
        "cursor" -> "Editing tools" to "أدوات التحرير"; "tashkeel" -> "Auto diacritics" to "تشكيل تلقائي"
        "language" -> "Change language" to "تغيير اللغة"; "settings" -> "Settings" to "الإعدادات"
        else -> id to id
    }
    return if (ar) pair.second else pair.first
}

class Board @JvmOverloads constructor(context: Context, private val event: (String, String) -> Unit = { _,_ -> }) : LinearLayout(context) {
    private var p = Prefs()
    private var lang = "ar"
    private var privateField = false
    private var numericField = false
    private var page = false
    private var shifted = false
    private var locked = false
    private var lastShift = 0L
    private var marksOpen = false
    private var suggested: List<String> = emptyList()
    private var suggestionsRow: LinearLayout? = null
    private var statusView: TextView? = null
    private var status = ""
    private var enterLabel: String? = null
    private var popup: PopupWindow? = null
    private var preview: PopupWindow? = null
    private var previewText: TextView? = null
    private var previewOwner: View? = null
    private var palette = BoardStyle.palette("dark")
    private val ar get() = isArabic(p,context)
    private val compact get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val rowHeight get() = if (compact) (p.height - 12).coerceAtLeast(33) else p.height
    init { orientation = VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; gravity = Gravity.CENTER_HORIZONTAL }
    fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun local(en: String, a: String) = if (ar) a else en
    fun configure(prefs: Prefs, language: String, sensitive: Boolean = false, numeric: Boolean = false, action: String? = null) {
        val reset = lang != language || numericField != numeric || privateField != sensitive
        p = prefs; lang = language; privateField = sensitive; numericField = numeric; enterLabel = action
        if (reset) { page = false; shifted = false; locked = false }
        render()
    }
    fun resetInput() { page = false; marksOpen = false; shifted = false; locked = false; lastShift = 0; suggested = emptyList(); render() }
    fun changeCase(capitalize: Boolean) {
        if (p.autoCaps && lang != "ar" && !locked && shifted != capitalize && !page && !numericField) {
            shifted = capitalize; render()
        }
    }
    fun suggestions(items: List<String>) { if (items != suggested) { suggested = items; showSuggestions() } }
    fun status(text: String) { status = text; statusView?.apply { this.text = text; visibility = if (text.isBlank()) GONE else VISIBLE } }
    private fun color(key: String, fallback: Int) = if (p.theme == "custom")
        p.colors[key]?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback else fallback
    private fun row() = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = HORIZONTAL }
    private fun addRow(row: LinearLayout, height: Int) {
        row.tag = "key-row"
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(height)))
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        for (i in 0 until childCount) getChildAt(i).let {
            if (it.tag == "key-row") it.layoutParams.width = if (p.width == 100 || available == 0) LayoutParams.MATCH_PARENT else available * p.width / 100
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    private fun shape(fill: Int, corner: Int = p.radius): GradientDrawable = GradientDrawable().also {
        it.setColor(fill); it.cornerRadius = dp(corner).toFloat()
        if (p.border > 0) it.setStroke(dp(p.border), palette.muted and 0x66ffffff)
    }
    fun key(label: String, action: () -> Unit, labelForAccess: String = label,
            fill: Int = color("key",palette.key), foreground: Int = color("text",palette.fg),
            size: Int = p.fontSize): TextView = TextView(context).apply {
        text = label; textSize = size.toFloat(); gravity = Gravity.CENTER
        setTextColor(foreground); typeface = BoardStyle.type(context,p.font)
        includeFontPadding = false; maxLines = 1; textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        contentDescription = labelForAccess
        val keyFill = if (p.bgStamp > 0) (fill and 0xffffff) or (0xA0 shl 24) else fill
        background = RippleDrawable(ColorStateList.valueOf(0x407dbba9),shape(keyFill), null)
        if (p.shadow) elevation = dp(2).toFloat()
        setOnClickListener {
            if (p.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
        }
    }
    private fun LinearLayout.put(v: View, weight: Float = 1f) {
        addView(v, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(p.gap),dp(p.gap),dp(p.gap),dp(p.gap)) })
    }
    private fun popup(anchor: View, options: List<Pair<String,()->Unit>>) {
        popup?.dismiss()
        val line = row()
        val scroll = HorizontalScrollView(context).apply { isFillViewport = true; addView(line); isHorizontalScrollBarEnabled = false }
        options.forEach { (label, action) ->
            line.addView(key(label,{ popup?.dismiss(); action() }), LayoutParams(dp(if (label.length > 5) 95 else 40),dp(48)))
        }
        popup = PopupWindow(scroll,minOf(resources.displayMetrics.widthPixels - dp(12), options.sumOf { if (it.first.length > 5) 95 else 40 }.let(::dp)),dp(48),true).apply {
            setBackgroundDrawable(shape(palette.bg)); elevation = dp(8).toFloat()
            isOutsideTouchable = true
            // A negative offset anchors the panel above its key in the IME window.
            showAsDropDown(anchor,0,-anchor.height - dp(50))
        }
    }
    override fun onDetachedFromWindow() { popup?.dismiss(); hidePreview(); super.onDetachedFromWindow() }
    /** Enlarged bubble above the pressed key, like a phone keyboard's key preview. */
    private fun showPreview(anchor: View, label: String) {
        if (!p.keyPreview || anchor.width == 0 || !anchor.isAttachedToWindow) return
        previewOwner = anchor
        val w = (anchor.width * 1.5f).toInt().coerceAtLeast(dp(46))
        val h = (anchor.height * 1.7f).toInt().coerceAtLeast(dp(64))
        val tv = previewText ?: TextView(context).apply {
            gravity = Gravity.CENTER; includeFontPadding = false; maxLines = 1
        }.also { previewText = it }
        tv.text = label
        tv.textSize = (p.fontSize + 12).toFloat()
        tv.typeface = BoardStyle.type(context,p.font)
        tv.setTextColor(color("text",palette.fg))
        tv.background = shape(palette.special.let { if (p.theme == "custom") color("special",it) else it }, (p.radius + 4).coerceAtMost(24)).apply {
            setStroke(dp(1), palette.accent)
        }
        tv.elevation = dp(6).toFloat()
        val pw = preview ?: PopupWindow(tv, w, h, false).apply {
            isTouchable = false; isFocusable = false; isClippingEnabled = false
            setBackgroundDrawable(null); animationStyle = 0
        }.also { preview = it }
        val x = (anchor.width - w) / 2
        val y = -anchor.height - h + dp(4)
        if (pw.isShowing) pw.update(anchor, x, y, w, h)
        else runCatching { pw.width = w; pw.height = h; pw.showAsDropDown(anchor, x, y) }
    }
    private fun hidePreview(owner: View? = null) {
        if (owner != null && owner !== previewOwner) return
        previewOwner = null
        runCatching { preview?.dismiss() }
    }
    private fun pressEffect(v: View, label: String) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(60).start()
                    showPreview(view,label)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    hidePreview(view)
                }
            }
            false
        }
    }
    private fun printable(row: LinearLayout, label: String, mode: String = "letter") {
        val rendered = if (shifted && !page && mode == "letter") KeyboardCore.upper(label,lang) else label
        fun send(text: String) {
            event("text",text)
            if (shifted && !locked && mode == "letter") { shifted = false; render() }
        }
        val styleDef = app.lisan.keyboard.core.TextStyles.all.firstOrNull { it.id == p.textStyle }
        val display = if (mode == "letter" && styleDef != null && !styleDef.arabic && !privateField)
            app.lisan.keyboard.core.TextStyles.apply(rendered, p.textStyle) else rendered
        val key = key(display,{send(rendered)}, foreground = color(
            if (mode == "number") "numbers" else if (mode == "letter") "text" else "symbols",palette.fg),
            size = if (mode == "letter") p.fontSize else p.symbolSize)
        pressEffect(key,display)
        KeyboardCore.alternatives[label]?.let { subs ->
            key.setOnLongClickListener {
                hidePreview()
                popup(key,subs.map { raw ->
                    val s = if (shifted && mode == "letter") KeyboardCore.upper(raw,lang) else raw
                    s to { send(s) }
                }); true
            }
        }
        row.put(key)
    }
    private fun showSuggestions() {
        val r = suggestionsRow ?: return; r.removeAllViews()
        if (suggested.isEmpty()) {
            r.put(TextView(context).apply { gravity = Gravity.CENTER; text = "L I S A N   /   لسان"; textSize=10f; setTextColor(palette.muted) })
        } else suggested.forEach { suggestion -> r.put(key(suggestion,{event("suggestion",suggestion)},size=14)) }
    }
    private fun render() {
        removeAllViews(); suggestionsRow = null
        palette = BoardStyle.palette(p.theme)
        val first = color("bg",palette.bg)
        val opacity = ((if (p.theme == "glass" && p.alpha == 100) 85 else p.alpha) * 255 / 100) shl 24
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(
            (first and 0xffffff) or opacity, ((if (p.theme == "custom") first else palette.end) and 0xffffff) or opacity))
        BoardStyle.backgroundBitmap(context,p.bgStamp)?.let { background = CoverDrawable(it,p.bgDim) }
        setPadding(0,dp(3),0,dp(4))
        val layout = KeyboardCore.layouts.firstOrNull { it.tag == lang } ?: KeyboardCore.layouts.first()
        if (p.toolbar && !privateField && !numericField) {
            val labels = mapOf("translate" to "A↔ع", "mic" to "◉", "clipboard" to "▤", "emoji" to "☺",
                "cursor" to "↔", "language" to "◎", "settings" to "⚙", "tashkeel" to "شَكِّل")
            val tools = p.tools.filter { p.clipboard || it != "clipboard" }.filter { it in labels }
            if (tools.isNotEmpty()) addRow(row().apply {
                tools.forEach { id -> put(key(labels.getValue(id),{ event(id,"") },toolName(id,ar), fill=when(id) { "translate","mic" -> palette.accent; else -> color("special",palette.special) },
                    foreground=when(id) { "translate","mic" -> color(id,palette.onAccent); else -> color("text",palette.fg) }, size=p.symbolSize).apply {
                        if(id=="mic") {
                            val mic = context.getDrawable(app.lisan.keyboard.R.drawable.ic_microphone)?.mutate()
                            mic?.setTint(color(id,palette.onAccent)); mic?.setBounds(0,0,dp(p.symbolSize+2),dp(p.symbolSize+2))
                            if (mic != null) text=android.text.SpannableString("\uFFFC").apply {
                                setSpan(android.text.style.ImageSpan(mic,android.text.style.ImageSpan.ALIGN_BOTTOM),0,1,0)
                            }
                        }
                    }) }
            },if(compact) (p.toolbarSize - 6).coerceAtLeast(28) else p.toolbarSize)
        }
        statusView = TextView(context).apply {
            gravity=Gravity.CENTER; textSize=12f; setTextColor(palette.muted); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT,dp(23))) }
        status(status)
        if (p.suggestions && !privateField && !page && !numericField) {
            suggestionsRow = row().also { addRow(it,if (compact) 23 else 30) }; showSuggestions()
        }
        if ((p.numbers || page) && !numericField) {
            val digits = if (p.eastern && layout.arabic) "١٢٣٤٥٦٧٨٩٠" else "1234567890"
            addRow(row().apply { digits.forEach { printable(this,it.toString(),"number") } }, if (compact) 26 else 33)
        }
        if (numericField) {
            listOf("1 2 3","4 5 6","7 8 9","- 0 .").forEach { group ->
                addRow(row().apply { group.split(' ').forEach { printable(this,it,"number") } },rowHeight)
            }
        } else {
            val rows = if (page) KeyboardCore.symbols else p.rowOrder.map { layout.rows[it] }
            rows.forEachIndexed { i, cells ->
                addRow(row().apply {
                    if (!page && !layout.arabic && i == 2) put(key(if (locked) "⇪" else "⇧",{
                        val now = android.os.SystemClock.uptimeMillis()
                        if (locked) { shifted=false; locked=false } else if (now-lastShift < 360) { locked=true; shifted=true }
                        else shifted = !shifted
                        lastShift=now; render()
                    },local("Shift","الأحرف الكبيرة"), if (shifted) palette.accent else color("special",palette.special),
                        if (shifted) palette.onAccent else palette.fg),1.25f)
                    cells.forEach { printable(this,it,if(page) "symbol" else "letter") }
                },(rowHeight + p.rowHeights.getOrElse(i){0}).coerceIn(30,65))
            }
        }
        val special = color("special",palette.special)
        addRow(row().apply {
            if (!numericField) {
                put(key(if (page) "ABC" else "123", {page=!page; shifted=false; locked=false; render()},
                    local("Letters and symbols","الحروف والرموز"),special,size=13),1.3f)
                if (!privateField || p.languages.size > 1) {
                    val globe = key("◎",{event("language","")},toolName("language",ar),special)
                    globe.setOnLongClickListener {
                        popup(globe,KeyboardCore.layouts.filter { it.tag in p.languages }.map { l -> l.name to {event("language",l.tag)} } +
                            (local("System keyboards","لوحات النظام") to {event("imePicker","")})); true
                    }
                    put(globe,0.9f)
                }
                printable(this,if (layout.arabic && !page) "،" else ",","symbol")
                put(key(layout.name,{event("space"," ")},local("Space","مسافة"),size=14).apply {
                    setOnLongClickListener { event(if (privateField) "imePicker" else "cursor",""); true }
                },3.45f)
                printable(this,".","symbol")
            }
            val del = key("⌫",{event("delete","")},local("Delete","حذف"),special)
            del.setOnLongClickListener { event("deleteWord",""); true }
            put(del,1.25f)
            put(key(enterLabel ?: "↵",{event("enter","")},local("Enter or editor action","إدخال أو إجراء الحقل"),
                palette.accent,palette.onAccent,size=if(enterLabel==null) p.fontSize else 12).apply {
                    setOnLongClickListener { event("hide",""); true }
                },if(enterLabel==null) 1.2f else 1.6f)
        },rowHeight)
    }
}
