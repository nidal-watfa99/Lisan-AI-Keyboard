package app.lisan.keyboard.emoji

import android.content.Context

/**
 * Unicode symbol blocks (arrows, mathematics, currency, shapes, Braille, ...), loaded from
 * assets/symbols.txt. Each line is: "<English name>\t<Arabic name>\t<symbols separated by spaces>".
 * The group id is "Ω <English> / <Arabic>" so the panel can show either language.
 * Symbols the device fonts cannot draw are hidden by the panel (see Emoji.drawable).
 */
object Symbols {
    @Volatile private var loaded = false
    private var groupMap: LinkedHashMap<String, List<String>> = linkedMapOf()

    val groups: Map<String, List<String>> get() = groupMap

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val result = linkedMapOf<String, List<String>>()
            runCatching {
                context.assets.open("symbols.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                    seq.forEach { line ->
                        val parts = line.split('\t')
                        if (parts.size < 3) return@forEach
                        val items = parts[2].split(' ').filter { it.isNotEmpty() }
                        if (items.isNotEmpty()) result["Ω ${parts[0]} / ${parts[1]}"] = items
                    }
                }
            }
            groupMap = LinkedHashMap(result)
            loaded = true
        }
    }
}
