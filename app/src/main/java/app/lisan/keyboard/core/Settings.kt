package app.lisan.keyboard.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.lisanData by preferencesDataStore("lisan_settings")
val Tools = listOf("translate", "mic", "tashkeel", "clipboard", "emoji", "cursor", "language", "settings")
val CustomColors = listOf("bg", "key", "text", "numbers", "symbols", "special", "mic", "translate")

data class Prefs(
    val ui: String = "device", val languages: List<String> = listOf("ar", "en"), val defaultLang: String = "ar",
    val theme: String = "dark", val font: String = "system", val fontSize: Int = 19, val symbolSize: Int = 15,
    val height: Int = 48, val width: Int = 100, val gap: Int = 2, val radius: Int = 12, val border: Int = 0,
    val alpha: Int = 100, val toolbarSize: Int = 38, val shadow: Boolean = false, val keyPreview: Boolean = true, val autoTashkeel: Boolean = true, val textStyle: String = "normal",
    val bgStamp: Long = 0, val bgDim: Int = 35,
    val savedThemes: Map<String,String> = emptyMap(),
    val rowHeights: List<Int> = listOf(0,0,0), val rowOrder: List<Int> = listOf(0,1,2),
    val colors: Map<String,String> = emptyMap(),
    val numbers: Boolean = true, val suggestions: Boolean = true, val toolbar: Boolean = true,
    val marks: Boolean = false, val eastern: Boolean = false, val haptics: Boolean = false,
    val autoCaps: Boolean = true, val correction: Boolean = false, val arabicCorrection:Boolean=false, val tools: List<String> = Tools,
    val voiceLang: String = "keyboard", val voiceMode: String = "local",
    val voiceAuto: Boolean = false, val voiceRepeat: Boolean = false,
    val source: String = "auto", val target: String = "device", val wifi: Boolean = false,
    val autoPaste: Boolean = false, val saveHistory: Boolean = false, val clipboard: Boolean = true,
    val emojiHistory: Boolean = false, val storeUrl: String = "", val cloudEndpoint: String = ""
) {
    fun json(): JSONObject = JSONObject().apply {
        put("ui",ui); put("languages",JSONArray(languages)); put("defaultLang",defaultLang); put("theme",theme)
        put("font",font); put("fontSize",fontSize); put("symbolSize",symbolSize); put("height",height)
        put("width",width); put("gap",gap); put("radius",radius); put("border",border); put("alpha",alpha)
        put("toolbarSize",toolbarSize); put("shadow",shadow); put("keyPreview",keyPreview); put("autoTashkeel",autoTashkeel); put("textStyle",textStyle); put("bgStamp",bgStamp); put("bgDim",bgDim); put("toolsV2",true); put("rowHeights",JSONArray(rowHeights))
        put("rowOrder",JSONArray(rowOrder)); put("colors",JSONObject(colors)); put("savedThemes",JSONObject(savedThemes))
        put("numbers",numbers); put("suggestions",suggestions); put("toolbar",toolbar); put("marks",marks)
        put("eastern",eastern); put("haptics",haptics); put("autoCaps",autoCaps); put("correction",correction);put("arabicCorrection",arabicCorrection)
        put("tools",JSONArray(tools)); put("voiceLang",voiceLang); put("voiceMode",voiceMode)
        put("voiceAuto",voiceAuto); put("voiceRepeat",voiceRepeat); put("source",source); put("target",target)
        put("wifi",wifi); put("autoPaste",autoPaste); put("saveHistory",saveHistory); put("clipboard",clipboard)
        put("emojiHistory",emojiHistory); put("storeUrl",storeUrl); put("cloudEndpoint",cloudEndpoint)
    }
    fun themeJson() = JSONObject().apply { val full = json(); THEME_KEYS.forEach { put(it, full.get(it)) } }
    fun importTheme(j: JSONObject) = from(json().apply { THEME_KEYS.forEach { if (j.has(it)) put(it,j.get(it)) } })
    companion object {
        val themeNames = listOf("dark","light","gradient","minimal","forest","glass","custom")
        val fontNames = listOf("system","cairo","tajawal","amiri","serif","mono")
        private val THEME_KEYS = listOf("theme","font","fontSize","symbolSize","height","width","gap","radius",
            "border","alpha","toolbarSize","shadow","rowHeights","colors")
        fun from(j: JSONObject): Prefs {
            fun s(k: String,d: String) = j.optString(k,d)
            fun b(k: String,d: Boolean = false) = j.optBoolean(k,d)
            fun i(k: String,d: Int,r: IntRange) = j.optInt(k,d).coerceIn(r)
            fun list(k: String,d: List<String>): List<String> =
                j.optJSONArray(k)?.let { a -> List(a.length().coerceAtMost(32)) { a.optString(it) } } ?: d
            val languages = list("languages", listOf("ar","en"))
                .filter { tag -> KeyboardCore.layouts.any { it.tag == tag } }.distinct().ifEmpty { listOf("ar") }
            val order = list("rowOrder", listOf("0","1","2")).mapNotNull(String::toIntOrNull)
                .takeIf { it.size == 3 && it.toSet() == setOf(0,1,2) } ?: listOf(0,1,2)
            val heights = list("rowHeights", listOf("0","0","0")).map { (it.toIntOrNull() ?: 0).coerceIn(-8,8) }
                .takeIf { it.size == 3 } ?: listOf(0,0,0)
            val colorJson = j.optJSONObject("colors")
            val colors = CustomColors.mapNotNull { key -> colorJson?.optString(key)?.takeIf {
                Regex("#[0-9a-fA-F]{6}").matches(it)
            }?.let { key to it } }.toMap()
            val library=j.optJSONObject("savedThemes")
            val savedThemes=library?.keys()?.asSequence()?.take(12)?.mapNotNull { name ->
                val theme=library.optString(name).takeIf{it.length in 2..2048}?.let { runCatching{JSONObject(it)}.getOrNull() }
                if(name.isBlank() || name.length>40 || theme==null)null else {
                    val limited=JSONObject().apply{THEME_KEYS.forEach{if(theme.has(it))put(it,theme.get(it))}}
                    name to limited.toString()
                }
            }?.toMap().orEmpty()
            return Prefs(
                ui=s("ui","device").takeIf { it in listOf("device","ar","en") } ?: "device",
                languages=languages, defaultLang=s("defaultLang","ar").takeIf { it in languages } ?: languages.first(),
                theme=s("theme","dark").takeIf { it in themeNames } ?: "dark",
                font=s("font","system").takeIf { app.lisan.keyboard.keyboard.FontCatalog.isValidId(it) } ?: "system",
                fontSize=i("fontSize",19,12..28), symbolSize=i("symbolSize",15,10..23), height=i("height",48,38..60),
                width=i("width",100,80..100), gap=i("gap",2,0..4), radius=i("radius",12,0..24),
                border=i("border",0,0..3), alpha=i("alpha",100,50..100), toolbarSize=i("toolbarSize",38,32..50),
                shadow=b("shadow"), keyPreview=b("keyPreview",true), autoTashkeel=b("autoTashkeel",true), textStyle=s("textStyle","normal").takeIf { it in TextStyles.ids } ?: "normal",
                bgStamp=j.optLong("bgStamp",0L).coerceAtLeast(0L), bgDim=i("bgDim",35,0..85), rowHeights=heights, rowOrder=order, colors=colors, savedThemes=savedThemes,
                numbers=b("numbers",true), suggestions=b("suggestions",true), toolbar=b("toolbar",true),
                marks=b("marks"), eastern=b("eastern"), haptics=b("haptics"), autoCaps=b("autoCaps",true),
                correction=b("correction"), arabicCorrection=b("arabicCorrection"), tools=list("tools",Tools).filter { it in Tools }.distinct().let { t ->
                    if (j.has("toolsV2") || "tashkeel" in t) t else t.toMutableList().apply { add((indexOf("mic")+1).coerceIn(0,size),"tashkeel") } },
                voiceLang=s("voiceLang","keyboard").takeIf { it == "keyboard" || Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}").matches(it) } ?: "keyboard",
                voiceMode=s("voiceMode","local").takeIf { it in listOf("local","bundled","system") } ?: "local",
                voiceAuto=b("voiceAuto"), voiceRepeat=b("voiceRepeat"),
                source=s("source","auto").take(12), target=s("target","device").take(12),
                wifi=b("wifi",false), autoPaste=b("autoPaste"), saveHistory=b("saveHistory"),
                clipboard=b("clipboard",true), emojiHistory=b("emojiHistory"),
                storeUrl=s("storeUrl","").takeIf { it.startsWith("https://") }?.take(600).orEmpty(),
                cloudEndpoint=app.lisan.keyboard.translation.CloudTranslator.endpoint(s("cloudEndpoint",""))?.toString().orEmpty()
            )
        }
    }
}

class Settings private constructor(context: Context) {
    private val store = context.applicationContext.lisanData
    private val key = stringPreferencesKey("config_v1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val flow = store.data.map { decode(it[key]) }.stateIn(scope, SharingStarted.Eagerly, Prefs())
    private fun decode(raw: String?) = runCatching { Prefs.from(JSONObject(raw ?: "{}")) }.getOrDefault(Prefs())
    suspend fun update(change: (Prefs) -> Prefs) {
        store.edit { p -> p[key] = Prefs.from(change(decode(p[key])).json()).json().toString() }
    }
    companion object {
        @Volatile private var current: Settings? = null
        fun get(context: Context) = current ?: synchronized(this) {
            current ?: Settings(context).also { current = it }
        }
    }
}

fun isArabic(p: Prefs, context: Context) = p.ui == "ar" ||
    (p.ui == "device" && context.resources.configuration.locales[0].language == "ar")
