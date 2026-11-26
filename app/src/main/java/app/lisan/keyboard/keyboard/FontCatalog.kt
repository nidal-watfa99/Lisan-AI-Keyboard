package app.lisan.keyboard.keyboard

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * Font sources for the keyboard keys:
 *  - built-in Android families ("system", "serif", "mono", "light", ...)
 *  - every .ttf/.otf placed in app/src/main/assets/fonts ("asset:<name>"; legacy ids "cairo", "tajawal", "amiri")
 *  - fonts installed on the phone itself, Android 10+ ("sys:<path>#<ttcIndex>")
 *  - font files the user imports from the phone ("user:<file name>")
 */
data class FontEntry(val id: String, val label: String, val arabic: Boolean, val source: String)

object FontCatalog {
    private val builtIn = listOf(
        "system" to "System default · خط النظام",
        "serif" to "Serif",
        "mono" to "Monospace",
        "light" to "Sans Light · نحيف",
        "thin" to "Sans Thin · رفيع جداً",
        "black" to "Sans Black · عريض جداً",
        "condensed" to "Condensed · مضغوط",
        "casual" to "Casual",
        "cursive" to "Cursive",
        "smallcaps" to "Small caps"
    )
    private val legacyAssets = setOf("cairo", "tajawal", "amiri")
    private val cache = mutableMapOf<String, Typeface>()

    fun isValidId(id: String): Boolean = id in builtIn.map { it.first } || id in legacyAssets ||
        (id.length < 400 && (id.startsWith("asset:") || id.startsWith("sys:/") || id.startsWith("user:")) &&
            !id.contains(".."))

    fun userDir(ctx: Context) = File(ctx.filesDir, "fonts").apply { mkdirs() }

    fun typeface(ctx: Context, id: String): Typeface = synchronized(cache) {
        cache.getOrPut(id) { runCatching { resolve(ctx, id) }.getOrNull() ?: Typeface.DEFAULT }
    }

    private fun resolve(ctx: Context, id: String): Typeface? = when {
        id == "system" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        id == "serif" -> Typeface.SERIF
        id == "mono" -> Typeface.MONOSPACE
        id == "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        id == "thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        id == "black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        id == "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        id == "casual" -> Typeface.create("casual", Typeface.NORMAL)
        id == "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
        id == "smallcaps" -> Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)
        id in legacyAssets -> Typeface.createFromAsset(ctx.assets, "fonts/$id.ttf")
        id.startsWith("asset:") -> Typeface.createFromAsset(ctx.assets, "fonts/" + id.removePrefix("asset:"))
        id.startsWith("user:") -> Typeface.createFromFile(File(userDir(ctx), id.removePrefix("user:")))
        id.startsWith("sys:") -> {
            val raw = id.removePrefix("sys:")
            val path = raw.substringBeforeLast('#')
            val index = raw.substringAfterLast('#', "0").toIntOrNull() ?: 0
            Typeface.Builder(File(path)).setTtcIndex(index).build()
        }
        else -> null
    }

    private fun supportsArabic(tf: Typeface): Boolean = runCatching {
        Paint().apply { typeface = tf }.hasGlyph("ب")
    }.getOrDefault(false)

    private fun pretty(file: String) = file.substringBeforeLast('.')
        .replace(Regex("[-_]"), " ")
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .replace(Regex("\\s+"), " ").trim()

    /** All fonts available to the keyboard. Heavy on first call (scans the device), so call off the main thread. */
    fun all(ctx: Context): List<FontEntry> {
        val out = mutableListOf<FontEntry>()
        builtIn.forEach { (id, label) -> out += FontEntry(id, label, true, "builtin") }
        val assets = runCatching { ctx.assets.list("fonts")?.toList() }.getOrNull().orEmpty()
            .filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }.sorted()
        assets.forEach { file ->
            val base = file.substringBeforeLast('.')
            val id = if (base in legacyAssets && file == "$base.ttf") base else "asset:$file"
            val tf = typeface(ctx, id)
            out += FontEntry(id, pretty(file).replaceFirstChar { it.uppercase() }, supportsArabic(tf), "bundled")
        }
        userDir(ctx).listFiles()?.sortedBy { it.name }?.forEach { f ->
            val id = "user:${f.name}"
            out += FontEntry(id, pretty(f.name), supportsArabic(typeface(ctx, id)), "user")
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val seen = HashSet<String>()
            runCatching {
                android.graphics.fonts.SystemFonts.getAvailableFonts()
                    .filter { f -> f.style.weight in 300..500 && f.style.slant == android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT }
                    .mapNotNull { f -> f.file?.let { it to f.ttcIndex } }
                    .sortedBy { it.first.name }
                    .forEach { (file, index) ->
                        val name = file.name
                        if (name.contains("Emoji", true) || name.contains("Symbol", true)) return@forEach
                        val key = pretty(name).replace(Regex("\\b(Regular|Medium|Light|Book|VF|Variable)\\b", RegexOption.IGNORE_CASE), "").trim() + "#$index"
                        if (!seen.add(key)) return@forEach
                        val id = "sys:${file.absolutePath}#$index"
                        val tf = typeface(ctx, id)
                        val arabic = supportsArabic(tf)
                        val latin = runCatching { Paint().apply { typeface = tf }.hasGlyph("A") }.getOrDefault(false)
                        if (arabic || latin) out += FontEntry(id, pretty(name), arabic, "device")
                        else synchronized(cache) { cache.remove(id) }
                    }
            }
        }
        return out
    }

    /** Copies a font file chosen by the user into private storage. Returns the new font id. */
    fun import(ctx: Context, uri: Uri): String {
        val display = runCatching {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "font.ttf"
        var name = display.replace(Regex("[^A-Za-z0-9._\\-\\u0600-\\u06FF ]"), "_").take(80)
        if (!name.endsWith(".ttf", true) && !name.endsWith(".otf", true)) name += ".ttf"
        val target = File(userDir(ctx), name)
        val tmp = File(userDir(ctx), "$name.tmp")
        try {
            checkNotNull(ctx.contentResolver.openInputStream(uri)).use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(65536); var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n; check(total <= 40L * 1024 * 1024) { "Font too large" }
                        out.write(buf, 0, n)
                    }
                }
            }
            // Validate the file really is a font before keeping it.
            checkNotNull(Typeface.Builder(tmp).build()) { "Not a font file" }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target))
        } finally { tmp.delete() }
        synchronized(cache) { cache.remove("user:$name") }
        return "user:$name"
    }

    fun remove(ctx: Context, id: String) {
        if (!id.startsWith("user:")) return
        File(userDir(ctx), id.removePrefix("user:")).delete()
        synchronized(cache) { cache.remove(id) }
    }
}
