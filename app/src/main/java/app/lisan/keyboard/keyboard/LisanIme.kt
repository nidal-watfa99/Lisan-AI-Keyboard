package app.lisan.keyboard.keyboard

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lisan.keyboard.MainActivity
import app.lisan.keyboard.core.*
import app.lisan.keyboard.emoji.Emoji
import app.lisan.keyboard.emoji.Symbols
import app.lisan.keyboard.privacy.Vault
import app.lisan.keyboard.translation.*
import app.lisan.keyboard.voice.*
import kotlinx.coroutines.*
import org.json.JSONObject

class LisanIme : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { Settings.get(this) }
    private val vault by lazy { Vault.get(this) }
    private val voice by lazy { VoiceRouter(this) }
    private val clip get() = getSystemService(ClipboardManager::class.java)
    private var prefs = Prefs()
    private var language = "ar"
    private var hasManualLanguage = false
    private var active = false
    private var field = 0
    private var sensitive = false
    private var numeric = false
    private var entry = EditorInfo.IME_ACTION_NONE
    private var root: LinearLayout? = null
    private var board: Board? = null
    private var panel: LinearLayout? = null
    private var scroller: ScrollView? = null
    private var panelName = ""
    private var queryMode = false
    private var query = ""
    private var personal = emptyList<String>()
    private var ignored = emptyList<String>()
    private var undo: Pair<String,String>? = null
    private var selStart = -1
    private var selEnd = -1
    private var voiceLive = false
    private var repeatAllowed = false
    private var acceptingVoice = false
    private var voiceTranslation = false
    private var voiceRestart: Job? = null
    private var voiceTimer: Job? = null
    private var statusJob: Job? = null
    private var panelJob: Job? = null
    private var trans: String = ""
    private var transResult: String = ""
    private var targetOverride: String? = null
    private var sourceOverride: String? = null
    private var emojiGroup = ""
    private var variants = emptyList<String>()
    private val ar get() = isArabic(prefs,this)
    private fun t(en: String,a: String) = if (ar) a else en
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private val safe get() = active && !sensitive && !numeric

    override fun onCreate() {
        super.onCreate()
        scope.launch(Dispatchers.IO) { Emoji.load(this@LisanIme) }
        scope.launch {
            settings.flow.collect {
                prefs = it
                if (!hasManualLanguage || language !in it.languages) language = it.defaultLang
                if (!prefs.clipboard && panelName == "clipboard") closePanel()
                refreshBoard()
            }
        }
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onCreateInputView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            root = this
            val storage = LinearLayout(this@LisanIme).apply { orientation = LinearLayout.VERTICAL }
            panel = storage
            scroller = ScrollView(this@LisanIme).apply {
                isFillViewport = false; addView(storage); visibility = View.GONE
            }.also { addView(it,LinearLayout.LayoutParams(-1,-2)) }
            board = Board(this@LisanIme,::event).also { addView(it,LinearLayout.LayoutParams(-1,-2)) }
            refreshBoard()
        }
    }
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute,restarting)
        invalidateSession()
        prefs = settings.flow.value
        active = true
        val type = attribute?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        numeric = cls in listOf(InputType.TYPE_CLASS_NUMBER,InputType.TYPE_CLASS_PHONE,InputType.TYPE_CLASS_DATETIME)
        sensitive = isSensitiveField(type,attribute?.imeOptions ?: 0)
        selStart = attribute?.initialSelStart ?: -1
        selEnd = attribute?.initialSelEnd ?: -1
        entry = if ((attribute?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) EditorInfo.IME_ACTION_NONE
            else (attribute?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        board?.resetInput()
        refreshBoard()
    }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info,restarting)
        active = true
        loadPersonal()
        refreshBoard()
        updateSuggestions(true)
    }
    override fun onFinishInputView(finishingInput: Boolean) { invalidateSession(); super.onFinishInputView(finishingInput) }
    override fun onFinishInput() { invalidateSession(); super.onFinishInput() }
    override fun onWindowHidden() { invalidateSession(); super.onWindowHidden() }
    override fun onDestroy() { invalidateSession(); scope.cancel(); super.onDestroy() }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
        if(acceptingVoice && !voiceTranslation && selStart>=0 && (selStart!=newSelStart || selEnd!=newSelEnd)) stopVoice(true)
        selStart = newSelStart; selEnd = newSelEnd
        updateSuggestions(false)
    }
    private fun invalidateSession() {
        active = false; field++; undo = null; personal = emptyList(); ignored = emptyList()
        stopVoice(true); closePanel(); board?.suggestions(emptyList())
    }
    private fun refreshBoard() {
        val label = when(entry) {
            EditorInfo.IME_ACTION_GO -> t("Go","انتقال"); EditorInfo.IME_ACTION_NEXT -> t("Next","التالي")
            EditorInfo.IME_ACTION_DONE -> t("Done","تم"); EditorInfo.IME_ACTION_SEARCH -> t("Find","بحث")
            EditorInfo.IME_ACTION_SEND -> t("Send","إرسال"); EditorInfo.IME_ACTION_PREVIOUS -> t("Back","السابق")
            else -> null
        }
        board?.configure(prefs,language,sensitive,numeric,label)
    }
    private fun before(n: Int) = runCatching { currentInputConnection?.getTextBeforeCursor(n,0)?.toString().orEmpty() }.getOrDefault("")
    private fun selected() = runCatching { currentInputConnection?.getSelectedText(0)?.toString().orEmpty() }.getOrDefault("")
    private fun updateSuggestions(caps: Boolean = false) {
        if (!safe || queryMode) { board?.suggestions(emptyList()); return }
        val b = if (prefs.suggestions || caps) before(90) else ""
        if (caps) board?.changeCase(b.isEmpty() || b.lastOrNull() == '\n' || b.trimEnd().lastOrNull() in listOf('.','?','!'))
        board?.suggestions(if (prefs.suggestions && (selStart == selEnd || selStart < 0))
            Prediction.choices(b,language,personal,ignored) else emptyList())
    }
    private fun loadPersonal() {
        if (!safe) return
        val token = field
        scope.launch {
            val words = withContext(Dispatchers.IO) { vault.items("words") }
            if (token != field || !safe) return@launch
            personal = words.filterNot { it.optBoolean("ignore") }.map { it.optString("text") }
            ignored = words.filter { it.optBoolean("ignore") }.map { it.optString("text") }
            updateSuggestions()
        }
    }
    private fun insert(text: String) { if (active) currentInputConnection?.commitText(text,1) }
    private fun edit(text: String, n: Int, ic: InputConnection) {
        ic.beginBatchEdit()
        try { if (n > 0) ic.deleteSurroundingText(n,0); if (text.isNotEmpty()) ic.commitText(text,1) }
        finally { ic.endBatchEdit() }
    }
    private fun delete(word: Boolean, ic: InputConnection) {
        if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
            ic.commitText("",1); return
        }
        // Delegate sensitive deletion to the editor rather than reading its content.
        if (sensitive) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        val b = before(if (word) 180 else 96)
        val old = undo
        if (!word && old != null && b.endsWith(old.second + " ")) {
            undo = null; edit(old.first,old.second.length + 1,ic); return
        }
        undo = null
        if (b.isEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        val n = if (word) {
            val trimmed = b.trimEnd()
            val tail = KeyboardCore.tail(trimmed)
            if (tail.isNotEmpty()) b.length - trimmed.length + tail.length else if (trimmed.isEmpty()) b.length else 0
        } else 0
        val boundary = if (n > 0) n else BreakIterator.getCharacterInstance().run { setText(b); b.length - preceding(b.length).coerceAtLeast(0) }
        if (!ic.deleteSurroundingText(boundary,0)) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }
    private fun event(action: String, value: String) {
        if (!active) return
        if (panelName=="draft" && queryMode && action in listOf("text","space","delete","deleteWord","enter","suggestion")) {
            editDraft(action,value)
            return
        }
        if(panelName=="draft" && action !in listOf("language","symbols","shift","page","hide","imePicker")) return
        if (queryMode && action in listOf("text","space","delete","deleteWord","enter","suggestion")) {
            query = when(action) {
                "delete","deleteWord" -> if (query.isEmpty()) "" else query.substring(0,query.offsetByCodePoints(query.length,-1))
                "enter" -> query
                else -> (query + value).take(60)
            }
            if (action == "enter") { queryMode = false; board?.visibility = View.GONE }
            when(panelName) { "emoji" -> emojiPanel(); "target","source" -> languagePanel(panelName=="source"); "clipboard" -> clipPanel() }
            return
        }
        val ic = currentInputConnection
        if (action !in listOf("delete","space")) undo = null
        if (action in Tools && !safe && action != "language") return
        if (action != "mic" && voiceLive) stopVoice(true)
        when(action) {
            "text" -> {
                val styled = if (prefs.textStyle == "normal" || sensitive) value
                    else TextStyles.apply(value, prefs.textStyle, before(1).lastOrNull())
                insert(styled); updateSuggestions()
            }
            "delete","deleteWord" -> if (ic != null) { delete(action == "deleteWord",ic); updateSuggestions() }
            "space" -> if (ic != null) {
                val canCorrect=prefs.textStyle=="normal" && ((language=="en" && prefs.correction)||(language=="ar" && prefs.arabicCorrection))
                val word = if (safe && canCorrect && selStart == selEnd) KeyboardCore.tail(before(90)) else ""
                val fixed = Prediction.correction(word,language,personal + ignored,prefs.arabicCorrection)
                undo = if (fixed != null) word to fixed else null
                if (fixed == null) insert(" ") else edit("$fixed ",word.length,ic)
                updateSuggestions()
            }
            "suggestion" -> if (safe && ic != null) { edit("$value ",KeyboardCore.tail(before(90)).length,ic); updateSuggestions() }
            "enter" -> if (ic != null) {
                if (entry in EditorInfo.IME_ACTION_GO..EditorInfo.IME_ACTION_PREVIOUS && ic.performEditorAction(entry)) Unit
                else if (currentInputEditorInfo?.inputType == InputType.TYPE_NULL) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                else insert("\n")
                updateSuggestions(true)
            }
            "language" -> {
                val langs = prefs.languages
                language = if (value in langs) value else langs[(langs.indexOf(language)+1).mod(langs.size)]
                hasManualLanguage = true; refreshBoard(); updateSuggestions(true)
            }
            "imePicker" -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            "hide" -> requestHideSelf(0)
            "settings" -> openApp("")
            "cursor" -> cursorPanel()
            "emoji" -> { query=""; variants=emptyList(); emojiPanel() }
            "clipboard" -> if (prefs.clipboard) { query=""; clipPanel() }
            "translate" -> { closePanel();translatePanel() }
            "mic" -> toggleVoice(false)
        }
    }
    private fun openApp(route: String) {
        startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("screen",route))
    }
    private fun status(text: String, transient: Boolean = false) {
        statusJob?.cancel()
        board?.status(text)
        if (transient) statusJob = scope.launch { delay(4200); board?.status("") }
    }
    private fun stopVoice(cancel: Boolean) {
        repeatAllowed = false
        voiceRestart?.cancel(); voiceRestart=null
        if (cancel) {
            voiceTimer?.cancel(); voiceTimer=null
            voice.cancel(); voiceLive=false; acceptingVoice=false; voiceTranslation=false; status("")
        } else if (!acceptingVoice) {
            voiceLive=false; status("")
        } else { voice.stop(); status(voiceMessage("processing","",ar)) }
    }
    private fun toggleVoice(forTranslation: Boolean) {
        if (!safe) return
        if (voiceLive) { stopVoice(false); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this,MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        }
        voiceTranslation = forTranslation
        repeatAllowed = !forTranslation && prefs.voiceRepeat
        if (forTranslation) board?.visibility=View.VISIBLE
        startVoice()
    }
    private fun startVoice() {
        if (!safe) return
        val token = field
        val tag = if (prefs.voiceLang == "keyboard") if (language == "en") "en-US" else language else prefs.voiceLang
        voiceLive = true
        acceptingVoice = true
        voiceTimer?.cancel()
        voiceTimer = scope.launch { delay(if(prefs.voiceMode=="system") 35_000 else 120_000); if (token == field && voiceLive) {
            stopVoice(true)
            status(t("Speech session timed out. Tap to retry.","انتهت مهلة جلسة الصوت. اضغط للمحاولة."),true)
        } }
        voice.start(prefs.voiceMode,VoiceConfig(tag,prefs.voiceMode != "system",prefs.voiceAuto)) { state,value ->
            if (token != field || !safe || !acceptingVoice) return@start
            if (voiceTerminal(state)) {
                voiceLive = false; acceptingVoice = false; voiceTimer?.cancel(); voiceTimer=null
                voice.cancel()
            }
            if (state == "done") {
                status("")
                if (voiceTranslation) { trans=(trans+KeyboardCore.speechJoin(trans,value)).take(5000); transResult=""; translatePanel() }
                else if (value.isNotBlank()) { insert(KeyboardCore.speechJoin(before(3),value)); updateSuggestions(true) }
                if (repeatAllowed && !voiceTranslation && voiceRestart == null && value.isNotBlank()) {
                    voiceLive = true
                    voiceRestart = scope.launch { delay(450); voiceRestart=null; if (field == token && safe && voiceLive) startVoice() }
                }
            } else {
                val text = voiceMessage(state,value,ar)
                val terminal = voiceTerminal(state)
                status(text,terminal)
                if (terminal && voiceTranslation) translatePanel(text)
            }
        }
    }
    private fun closePanel() {
        panelJob?.cancel(); panelJob=null; panelName=""; queryMode=false; query=""
        trans=""; transResult=""; targetOverride=null; sourceOverride=null; variants=emptyList()
        scroller?.visibility=View.GONE; panel?.removeAllViews(); board?.visibility=View.VISIBLE
    }
    private fun frame(name: String, heading: String): LinearLayout? {
        if (!safe) return null
        panelName=name
        val p = panel ?: return null
        val orientation = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        scroller?.layoutParams = LinearLayout.LayoutParams(-1,dp(if (queryMode) if (orientation) 96 else 135 else if (orientation) 170 else 256))
        scroller?.visibility=View.VISIBLE
        board?.visibility=if (queryMode || (voiceLive && voiceTranslation)) View.VISIBLE else View.GONE
        p.removeAllViews(); p.setPadding(dp(10),dp(6),dp(10),dp(9)); p.setBackgroundColor(Color.rgb(20,29,47))
        heading(p,heading,true)
        line(p,listOf(t("Back to typing","العودة للكتابة") to { stopVoice(true); closePanel() }))
        return p
    }
    private fun heading(p: LinearLayout, text: String, bold: Boolean = false) {
        p.addView(TextView(this).apply {
            this.text=text; textSize=if (bold) 15f else 13f; setTextColor(if (bold) 0xff8cf0d5.toInt() else 0xffe5edff.toInt())
            layoutDirection = if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.START; setPadding(dp(7),dp(6),dp(7),dp(6))
            maxLines=5; textDirection=View.TEXT_DIRECTION_FIRST_STRONG; if (bold) setTypeface(null,Typeface.BOLD)
        })
    }
    private fun line(p: LinearLayout, actions: List<Pair<String,()->Unit>>) {
        p.addView(LinearLayout(this).apply {
            gravity=Gravity.CENTER
            actions.forEach { (title, action) ->
                addView(TextView(this@LisanIme).apply {
                    text=title; textSize=if (title.length > 19) 12f else 14f; gravity=Gravity.CENTER
                    maxLines=2; setTextColor(0xffe6f4ff.toInt())
                    background = android.graphics.drawable.GradientDrawable().also { d ->
                        d.setColor(0xff2b3c58.toInt()); d.cornerRadius=dp(9).toFloat()
                    }
                    setOnClickListener { if (safe) action() }
                },LinearLayout.LayoutParams(0,dp(42),1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
            }
        })
    }
    private fun cursorPanel() {
        val p = frame("cursor",t("Editing tools","أدوات التحرير")) ?: return
        fun key(code: Int) { val time=SystemClock.uptimeMillis()
            currentInputConnection?.sendKeyEvent(KeyEvent(time,time,KeyEvent.ACTION_DOWN,code,0))
            currentInputConnection?.sendKeyEvent(KeyEvent(time,time,KeyEvent.ACTION_UP,code,0)) }
        line(p,listOf("←" to { key(KeyEvent.KEYCODE_DPAD_LEFT) },"↑" to { key(KeyEvent.KEYCODE_DPAD_UP) },
            "↓" to { key(KeyEvent.KEYCODE_DPAD_DOWN) },"→" to { key(KeyEvent.KEYCODE_DPAD_RIGHT) }))
        line(p,listOf(t("Select all","تحديد الكل") to { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) },
            t("Copy","نسخ") to { currentInputConnection?.performContextMenuAction(android.R.id.copy) },
            t("Cut","قص") to { currentInputConnection?.performContextMenuAction(android.R.id.cut) },
            t("Paste","لصق") to { currentInputConnection?.performContextMenuAction(android.R.id.paste); closePanel() }))
        line(p,listOf(t("Hide keyboard","إخفاء اللوحة") to { closePanel(); requestHideSelf(0) },
            t("Keyboard settings","إعدادات اللوحة") to { openApp("") }))
    }
    private fun clipboardText(): String {
        if (!safe) return ""
        return runCatching {
            val c = clip.primaryClip ?: return ""
            if (c.description.extras?.let {
                    it.getBoolean("android.content.extra.IS_SENSITIVE") || it.getBoolean("android.content.extra.IS_SENSITIVE_DATA")
                } == true) return ""
            if (!c.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                !c.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return ""
            c.getItemAt(0).text?.toString().orEmpty().take(5000)
        }.getOrDefault("")
    }
    private fun save(group: String, obj: JSONObject, onDone: () -> Unit = {}) {
        if (!safe) return
        val token = field
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                vault.update(group) { old -> listOf(obj) + old.filterNot { it.optString("text") == obj.optString("text") } }
            }.isSuccess }
            if (token != field || !safe) return@launch
            if (ok) onDone() else status(t("Local storage failed.","تعذر الحفظ المحلي."),true)
        }
    }
    private fun searchLine(p: LinearLayout, redraw: () -> Unit) {
        line(p,listOf((if (queryMode) t("Search: $query","بحث: $query") else t("Search","بحث")) to {
            queryMode=!queryMode; redraw()
        },t("Clear search","مسح البحث") to { query=""; redraw() }))
    }
    private fun clipPanel() {
        val p = frame("clipboard",t("Clipboard","الحافظة")) ?: return
        line(p,listOf(t("Paste current","لصق الحالي") to {
            val text=clipboardText()
            if (text.isBlank()) heading(p,t("No accessible text.","لا يوجد نص متاح."))
            else { insert(text); closePanel() }
        },t("Save current","حفظ الحالي") to {
            val text=clipboardText()
            if (text.isBlank()) heading(p,t("No accessible text.","لا يوجد نص متاح."))
            else save("clipboard",JSONObject().put("text",text)) { clipPanel() }
        },t("Manage","إدارة") to { openApp("clipboard") }))
        searchLine(p,::clipPanel)
        panelJob?.cancel()
        val token=field
        panelJob=scope.launch {
            val items=withContext(Dispatchers.IO){ vault.items("clipboard") }
            if (token != field || panelName != "clipboard") return@launch
            items.sortedByDescending { it.optBoolean("pin") }.filter { it.optString("text").contains(query,true) }.forEach {
                val text=it.optString("text")
                heading(p,text.take(130),it.optBoolean("pin"))
                line(p,listOf(t("Insert","إدراج") to { insert(text); closePanel() },
                    t("Translation tools","أدوات الترجمة") to { queryMode=false; trans=text; transResult=""; translatePanel() },
                    t("Copy","نسخ") to { clip.setPrimaryClip(ClipData.newPlainText("Lisan",text)) }))
            }
        }
    }
    private fun emojiPanel() {
        Emoji.load(this)
        if (emojiGroup.isEmpty()) emojiGroup = Emoji.groups.keys.firstOrNull() ?: "★"
        val p=frame("emoji",t("Emoji","الإيموجي")) ?: return
        searchLine(p,::emojiPanel)
        if (variants.isNotEmpty()) {
            line(p,variants.map { e -> e to { insertEmoji(e) } }); variants=emptyList(); return
        }
        val symbolMode = Symbols.groups.containsKey(emojiGroup)
        if (query.isEmpty() && symbolMode) {
            val keys = Symbols.groups.keys.toList()
            chipStrip(p,listOf("☺" to { emojiGroup=Emoji.groups.keys.firstOrNull() ?: "★"; emojiPanel() }) +
                keys.map { g -> (if(ar) g.substringAfter(" / ") else g.substringBefore(" / ")) to { emojiGroup=g; emojiPanel() } },
                keys.indexOf(emojiGroup) + 1)
        } else if (query.isEmpty()) (listOf("★" , "◷", "ツ / وجوه نصية", "Ω / رموز كاملة") + Emoji.groups.keys).chunked(4).forEach { group ->
            line(p,group.map { g -> (if(ar) g.substringAfter(" / ") else g.substringBefore(" / ")) to {
                if (g == "Ω / رموز كاملة") {
                    Symbols.load(this)
                    val first = Symbols.groups.keys.firstOrNull()
                    if (first == null) status(t("Symbols file missing from this build.","ملف الرموز غير موجود في هذا الإصدار."), true)
                    else { emojiGroup=first; emojiPanel() }
                } else { emojiGroup=g; emojiPanel() }
            } })
        }
        if (symbolMode && query.isBlank()) {
            drawSymbols(p,Symbols.groups[emojiGroup].orEmpty())
        } else if (emojiGroup == "ツ / وجوه نصية" && query.isBlank()) {
            Kaomoji.list.chunked(3).forEach { row -> line(p,row.map { k -> k to { insert(k) } }) }
        } else if (emojiGroup in listOf("★","◷") && query.isBlank()) {
            panelJob?.cancel()
            panelJob=scope.launch {
                val found=withContext(Dispatchers.IO){ vault.items(if(emojiGroup=="★") "emojiFav" else "emojiRecent") }
                if (panelName=="emoji" && safe) drawEmoji(p,found.map { it.optString("text") })
            }
        } else drawEmoji(p,if(query.isBlank()) Emoji.groups[emojiGroup].orEmpty() else Emoji.search(query))
    }
    private fun drawSymbols(p: LinearLayout, items: List<String>) {
        items.filter { Emoji.drawable(it) }.chunked(8).forEach { row ->
            val grid=LinearLayout(this)
            row.forEach { sym ->
                grid.addView(TextView(this).apply {
                    text=sym; textSize=22f; gravity=Gravity.CENTER; contentDescription=sym
                    setOnClickListener { if (safe) insert(sym) }
                },LinearLayout.LayoutParams(0,dp(40),1f))
            }
            repeat(8 - row.size) { grid.addView(View(this),LinearLayout.LayoutParams(0,dp(40),1f)) }
            p.addView(grid)
        }
    }
    /** One horizontally scrollable row of buttons, so a long list of groups does not fill the panel. */
    private fun chipStrip(p: LinearLayout, actions: List<Pair<String,()->Unit>>, selected: Int) {
        val row = LinearLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        var selectedView: View? = null
        actions.forEachIndexed { i, (title, action) ->
            val chip = TextView(this).apply {
                text=title; textSize=14f; gravity=Gravity.CENTER; maxLines=1
                setTextColor(0xffe6f4ff.toInt()); setPadding(dp(14),0,dp(14),0)
                background = android.graphics.drawable.GradientDrawable().also { d ->
                    d.setColor(if (i == selected) 0xff3f6a8f.toInt() else 0xff2b3c58.toInt()); d.cornerRadius=dp(9).toFloat()
                }
                setOnClickListener { if (safe) action() }
            }
            if (i == selected) selectedView = chip
            row.addView(chip,LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,dp(42)).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
        }
        val strip = android.widget.HorizontalScrollView(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR; isHorizontalScrollBarEnabled=false; addView(row)
        }
        p.addView(strip)
        strip.post { strip.scrollTo(((selectedView?.left ?: 0) - dp(48)).coerceAtLeast(0),0) }
    }
    private fun drawEmoji(p: LinearLayout, items: List<String>, direct: Boolean = false) {
        items.filter { Emoji.drawable(it) }.chunked(8).forEach { row ->
            val grid=LinearLayout(this)
            row.forEach { e ->
                grid.addView(TextView(this).apply {
                    text=e; textSize=24f; gravity=Gravity.CENTER; contentDescription=e
                    setOnClickListener { if (!direct && Emoji.hasVariants(e)) emojiActions(e) else insertEmoji(e) }
                    setOnLongClickListener { if (!safe) return@setOnLongClickListener false
                        emojiActions(e); true }
                },LinearLayout.LayoutParams(0,dp(40),1f))
            }
            p.addView(grid)
        }
    }
    private fun emojiActions(e: String) {
        val p = frame("emoji",e) ?: return
        drawEmoji(p,Emoji.variants(e),true)
        line(p,listOf(t("Favorite","مفضلة") to { save("emojiFav",JSONObject().put("text",e)) { emojiPanel() } },
            t("Copy","نسخ") to { clip.setPrimaryClip(ClipData.newPlainText("Lisan emoji",e)); emojiPanel() },
            t("Back","رجوع") to { emojiPanel() }))
    }
    private fun insertEmoji(e: String) {
        if (!safe) return
        insert(e)
        if (prefs.emojiHistory) save("emojiRecent",JSONObject().put("text",e))
        // Keep the emoji panel open so several emoji can be entered in a row.
    }
    private fun translatePanel(message: String = "", missing: Boolean = false, busy: Boolean = false) {
        val p=frame("translate",t("Translate on device","ترجمة على الجهاز")) ?: return
        val target = targetOverride ?: prefs.target
        if(!busy)line(p,listOf(t("Target: ${displayLanguage(target,ar)}","الهدف: ${displayLanguage(target,ar)}") to { stopVoice(true); query=""; queryMode=false; languagePanel(false) },
            t("Source: ${displayLanguage(sourceOverride ?: prefs.source,ar)}","المصدر: ${displayLanguage(sourceOverride ?: prefs.source,ar)}") to { stopVoice(true); query=""; queryMode=false; languagePanel(true) }))
        if(!busy)line(p,listOf(t("Voice","بالصوت") to { toggleVoice(true) },
            t("Edit translation draft","تعديل مسودة الترجمة") to {
                stopVoice(true); queryMode=true; transResult=""; draftPanel()
            }))
        if (!busy) line(p,listOf(t("Field / selection","الحقل / التحديد") to {
            stopVoice(true)
            val selection=selected()
            trans=(selection.ifBlank { before(2500)+runCatching { currentInputConnection?.getTextAfterCursor(2500,0)?.toString().orEmpty() }.getOrDefault("") }).take(5000)
            transResult=""; translatePanel(if(trans.isBlank()) t("No field text.","لا يوجد نص في الحقل.") else "")
        },t("Use copied text","استخدام النص المنسوخ") to {
            stopVoice(true);trans=clipboardText(); transResult=""
            if (trans.isNotBlank()) translate(true)
            else translatePanel(if(trans.isBlank()) t("No accessible copied text.","لا يوجد نص منسوخ متاح.") else "")
        }))
        if (trans.isNotBlank()) heading(p,trans)
        if (message.isNotBlank()) heading(p,message,true)
        if (transResult.isNotBlank()) {
            heading(p,transResult,true)
            p.addView(android.widget.ImageView(this).apply {
                setImageResource(app.lisan.keyboard.R.drawable.google_translation_badge)
                contentDescription=t("Powered by Google Translate","ترجمة آلية من Google")
            },LinearLayout.LayoutParams(dp(176),dp(16)).apply { setMargins(dp(7),dp(2),0,dp(3)) })
            line(p,listOf(t("Insert result","إدراج الناتج") to { insert(transResult); closePanel() },
                t("Copy","نسخ") to { clip.setPrimaryClip(ClipData.newPlainText("Lisan translation",transResult)) }))
        }
        if (!busy) line(p,listOf(t("Translate","ترجمة") to {
                if (trans.isBlank()) {
                    val selection=selected()
                    trans=(selection.ifBlank { before(2500)+runCatching { currentInputConnection?.getTextAfterCursor(2500,0)?.toString().orEmpty() }.getOrDefault("") }).take(5000)
                }
                if(trans.isNotBlank()) translate(true) else translatePanel(t("Type or select text first, or use copied text.","اكتب أو حدّد نصاً أولاً، أو استخدم النص المنسوخ."))
            },
            t("Manual screen","واجهة الترجمة") to { openApp("translate") }))
        if(busy)line(p,listOf(t("Stop waiting","إيقاف الانتظار") to { panelJob?.cancel();panelJob=null;translatePanel() }))
        if (missing) line(p,listOf(t("Download and translate with Google","تنزيل وترجمة باستخدام Google") to { translate(true) }))
    }
    private fun languagePanel(source:Boolean) {
        val redraw={languagePanel(source)}
        val p=frame(if(source)"source" else "target",if(source)t("Temporary source language","لغة المصدر المؤقتة") else t("Temporary target language","لغة الهدف المؤقتة")) ?: return
        searchLine(p,redraw)
        (listOf(if(source)"auto" else "device") + LocalTranslator.languages.sortedBy { displayLanguage(it,ar) })
            .filter { query.isBlank() || displayLanguage(it,ar).contains(query,true) || it.equals(query,true) }
            .chunked(2).forEach { group -> line(p,group.map { code -> displayLanguage(code,ar) to {
                if(source)sourceOverride=code else targetOverride=code
                queryMode=false; transResult=""; translatePanel()
            } }) }
    }
    private fun draftPanel() {
        val p=frame("draft",t("Edit draft without changing the editor","عدّل المسودة دون تغيير نص التطبيق")) ?: return
        heading(p,trans.takeLast(300).let{if(trans.length>300)"…$it" else it})
        line(p,listOf(t("Done","تم") to { queryMode=false;translatePanel() },
            t("Clear draft","مسح المسودة") to { trans="";draftPanel() }))
    }
    private fun editDraft(action:String,value:String) {
        when(action) {
            "enter" -> {queryMode=false;translatePanel();return}
            "delete","deleteWord" -> if(trans.isNotEmpty())trans=trans.substring(0,trans.offsetByCodePoints(trans.length,-1))
            "space" -> if(trans.length<5000)trans+=" "
            else -> trans=(trans+value).take(5000)
        }
        board?.suggestions(emptyList())
        draftPanel()
    }
    private fun translate(download: Boolean) {
        if (!safe || trans.isBlank()) return
        stopVoice(true)
        panelJob?.cancel()
        val token=field
        val text=trans
        val target=(targetOverride ?: prefs.target).let { if(it=="device") resolvedTarget(prefs.copy(target="device"),resources.configuration.locales[0]) else it }
        translatePanel(if(download) t("Translating… the first time downloads the language model (~30 MB).","جارٍ الترجمة… في المرة الأولى يُنزَّل نموذج اللغة (حوالي 30 ميغابايت).") else t("Working…","جارٍ المعالجة…"),busy=true)
        panelJob=scope.launch {
            try {
                val result=LocalTranslator.translate(text,sourceOverride ?: prefs.source,target,download,false)
                if (token != field || !safe || panelName!="translate") return@launch
                transResult=result.text
                if (prefs.saveHistory) save("history",JSONObject().put("text",text).put("out",result.text).put("from",result.source).put("to",result.target))
                translatePanel("${displayLanguage(result.source,ar)} → ${displayLanguage(result.target,ar)}")
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(token==field && safe && panelName=="translate") translatePanel(describeTranslationError(e,ar),e is MissingModels)
            }
        }
    }
}

internal fun isSensitiveField(type: Int, options: Int): Boolean {
    val cls = type and InputType.TYPE_MASK_CLASS
    val variation = type and InputType.TYPE_MASK_VARIATION
    return cls == InputType.TYPE_NULL || options and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ||
        (cls == InputType.TYPE_CLASS_TEXT && variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
        (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
}
