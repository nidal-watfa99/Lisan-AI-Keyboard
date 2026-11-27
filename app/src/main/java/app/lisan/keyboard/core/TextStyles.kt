package app.lisan.keyboard.core

/**
 * "Fonts" that survive in any app: the typed letters are replaced by styled Unicode characters
 * (Mathematical Alphanumeric Symbols etc.) for Latin, and by decorative techniques
 * (kashida / alternative letter forms) for Arabic. The receiving app keeps its own font,
 * but these characters look different everywhere.
 */
data class TextStyle(val id: String, val en: String, val ar: String, val arabic: Boolean = false)

object TextStyles {
    val all = listOf(
        TextStyle("normal", "Normal", "عادي"),
        TextStyle("bold", "Bold", "عريض"),
        TextStyle("italic", "Italic", "مائل"),
        TextStyle("bolditalic", "Bold italic", "عريض مائل"),
        TextStyle("script", "Script", "خط يد"),
        TextStyle("boldscript", "Bold script", "خط يد عريض"),
        TextStyle("fraktur", "Gothic", "قوطي"),
        TextStyle("double", "Double-struck", "مزدوج"),
        TextStyle("sansbold", "Sans bold", "عصري عريض"),
        TextStyle("mono", "Typewriter", "آلة كاتبة"),
        TextStyle("circled", "Circled", "دوائر"),
        TextStyle("squared", "Squared", "مربعات"),
        TextStyle("smallcaps", "Small caps", "أحرف صغيرة"),
        TextStyle("wide", "Wide", "عريض المسافات"),
        TextStyle("strike", "Strikethrough", "مشطوب"),
        TextStyle("underline", "Underlined", "تحته خط"),
        TextStyle("ar_kashida", "Arabic: stretched", "عربي: ممدود (كشيدة)", true),
        TextStyle("ar_long", "Arabic: long stretch", "عربي: مدّ طويل", true),
        TextStyle("ar_decor", "Arabic: decorated letters", "عربي: حروف مزخرفة", true),
        TextStyle("ar_royal", "Arabic: decorated + stretched", "عربي: مزخرف وممدود", true)
    )
    val ids = all.map { it.id }.toSet()

    private class Block(val upper: Int, val lower: Int, val digit: Int = -1, val exceptions: Map<Char, Int> = emptyMap())
    private val blocks = mapOf(
        "bold" to Block(0x1D400, 0x1D41A, 0x1D7CE),
        "italic" to Block(0x1D434, 0x1D44E, exceptions = mapOf('h' to 0x210E)),
        "bolditalic" to Block(0x1D468, 0x1D482),
        "script" to Block(0x1D49C, 0x1D4B6, exceptions = mapOf('B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131,
            'H' to 0x210B, 'I' to 0x2110, 'L' to 0x2112, 'M' to 0x2133, 'R' to 0x211B,
            'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134)),
        "boldscript" to Block(0x1D4D0, 0x1D4EA),
        "fraktur" to Block(0x1D504, 0x1D51E, exceptions = mapOf('C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111,
            'R' to 0x211C, 'Z' to 0x2128)),
        "double" to Block(0x1D538, 0x1D552, 0x1D7D8, mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115,
            'P' to 0x2119, 'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124)),
        "sansbold" to Block(0x1D5D4, 0x1D5EE, 0x1D7EC),
        "mono" to Block(0x1D670, 0x1D68A, 0x1D7F6),
        "wide" to Block(0xFF21, 0xFF41, 0xFF10)
    )
    private const val SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ"
    private val decor = mapOf('ك' to "ڪ", 'ه' to "ھ", 'ة' to "ۃ", 'ا' to "ٱ", 'ى' to "ې")
    /** Arabic letters that do not join to the following letter. */
    private const val NON_JOINING = "اأإآٱدذرزوؤةىءژۃ"

    private fun isArabicLetter(c: Char) = c in 'ء'..'ي' || c in 'ٱ'..'ۓ'

    private fun latin(c: Char, style: String): String {
        if (c !in 'A'..'Z' && c !in 'a'..'z' && c !in '0'..'9') return c.toString()
        when (style) {
            "smallcaps" -> return if (c in 'a'..'z') SMALL[c - 'a'].toString() else c.toString()
            "strike" -> return "$c̶"
            "underline" -> return "$c̲"
            "circled" -> return String(Character.toChars(when (c) {
                in 'A'..'Z' -> 0x24B6 + (c - 'A'); in 'a'..'z' -> 0x24D0 + (c - 'a')
                '0' -> 0x24EA; else -> 0x2460 + (c - '1')
            }))
            "squared" -> return if (c in '0'..'9') c.toString()
                else String(Character.toChars(0x1F130 + (c.uppercaseChar() - 'A')))
        }
        val b = blocks[style] ?: return c.toString()
        b.exceptions[c]?.let { return String(Character.toChars(it)) }
        val cp = when (c) {
            in 'A'..'Z' -> b.upper + (c - 'A')
            in 'a'..'z' -> b.lower + (c - 'a')
            else -> if (b.digit < 0) return c.toString() else b.digit + (c - '0')
        }
        return String(Character.toChars(cp))
    }

    /**
     * Styles one typed piece of text. [previous] is the character just before the cursor
     * (used to place the Arabic kashida between joined letters).
     */
    fun apply(text: String, style: String, previous: Char? = null): String {
        if (style == "normal" || text.isEmpty()) return text
        val def = all.firstOrNull { it.id == style } ?: return text
        if (!def.arabic) return text.map { latin(it, style) }.joinToString("")
        val sb = StringBuilder()
        var prev = previous
        for (ch in text) {
            if (isArabicLetter(ch)) {
                val stretch = when (style) { "ar_kashida", "ar_royal" -> "ـ"; "ar_long" -> "ـــ"; else -> "" }
                if (stretch.isNotEmpty() && prev != null && isArabicLetter(prev) && prev !in NON_JOINING) sb.append(stretch)
                sb.append(if (style == "ar_decor" || style == "ar_royal") decor[ch] ?: ch.toString() else ch.toString())
            } else sb.append(ch)
            prev = ch
        }
        return sb.toString()
    }
}
