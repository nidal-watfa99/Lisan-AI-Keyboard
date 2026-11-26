package app.lisan.keyboard.emoji

import android.content.Context
import android.graphics.Paint

/**
 * Full Unicode emoji catalogue (all fully-qualified sequences from emoji-test.txt),
 * loaded from assets/emoji.txt. Skin-tone variants are grouped under their base emoji
 * and offered on long press; the grid shows base emoji only.
 */
object Emoji {
    private val arabicGroup = mapOf(
        "Smileys & Emotion" to "وجوه ومشاعر", "People & Body" to "أشخاص وأجسام",
        "Animals & Nature" to "حيوانات وطبيعة", "Food & Drink" to "طعام وشراب",
        "Travel & Places" to "سفر وأماكن", "Activities" to "أنشطة", "Objects" to "أشياء",
        "Symbols" to "رموز", "Flags" to "أعلام"
    )
    private val tones = setOf(0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)

    @Volatile private var loaded = false
    private var groupMap: LinkedHashMap<String, List<String>> = linkedMapOf()
    private val names = HashMap<String, String>()
    private val toneVariants = HashMap<String, MutableList<String>>()
    private val glyphCache = HashMap<String, Boolean>()
    private val paint = Paint()

    val groups: Map<String, List<String>> get() = groupMap

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val result = linkedMapOf<String, MutableList<String>>()
            var current: MutableList<String>? = null
            runCatching {
                context.assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                    seq.forEach { line ->
                        if (line.isBlank()) return@forEach
                        if (line.startsWith("#G\t")) {
                            val en = line.substring(3)
                            current = result.getOrPut("$en / ${arabicGroup[en] ?: en}") { mutableListOf() }
                            return@forEach
                        }
                        val tab = line.indexOf('\t')
                        val e = if (tab < 0) line else line.substring(0, tab)
                        val name = if (tab < 0) "" else line.substring(tab + 1)
                        names[e] = name
                        val base = stripTones(e)
                        if (base != e) {
                            toneVariants.getOrPut(base) { mutableListOf() }.add(e)
                        } else current?.add(e)
                    }
                }
            }
            groupMap = LinkedHashMap(result)
            loaded = true
        }
    }

    private fun stripTones(e: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < e.length) {
            val cp = e.codePointAt(i)
            if (cp !in tones) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    /** True if this device's emoji font can draw the sequence (hides emoji newer than the OS). */
    fun drawable(e: String): Boolean = synchronized(glyphCache) {
        glyphCache.getOrPut(e) { runCatching { paint.hasGlyph(e) }.getOrDefault(false) }
    }

    private val aliases = mapOf(
        "قلب حب" to "heart", "ابتسامة سعيد ضحك" to "smil grin laugh joy", "حزين بكاء" to "sad cry tear",
        "كلب" to "dog", "قط قطة" to "cat", "علم" to "flag", "نار" to "fire", "ورد وردة زهرة" to "flower rose blossom",
        "شمس" to "sun", "قمر" to "moon", "نجمة" to "star", "طعام اكل" to "food", "سيارة" to "car",
        "يد" to "hand", "شكر" to "folded hands", "غضب" to "angry", "حب" to "love kiss", "طفل" to "baby child",
        "قهوة" to "coffee", "كتاب" to "book", "هاتف جوال" to "phone", "مال" to "money", "ساعة" to "clock"
    )

    fun search(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val terms = mutableListOf(q.lowercase())
        aliases.forEach { (k, v) -> if (k.split(' ').any { it.contains(q) || q.contains(it) }) terms += v.split(' ') }
        val fromGroups = groupMap.entries.filter { it.key.contains(q, true) }.flatMap { it.value }
        val fromNames = groupMap.values.flatten().filter { e ->
            val n = names[e].orEmpty().lowercase()
            e.contains(q) || terms.any { t -> t.length > 1 && n.contains(t) }
        }
        return (fromGroups + fromNames).distinct()
    }

    fun variants(emoji: String): List<String> = listOf(emoji) + toneVariants[emoji].orEmpty()
    fun hasVariants(emoji: String) = toneVariants[emoji]?.isNotEmpty() == true
}
