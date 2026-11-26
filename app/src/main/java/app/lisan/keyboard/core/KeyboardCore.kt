package app.lisan.keyboard.core

import java.util.Locale

data class Layout(val tag: String, val name: String, val rows: List<List<String>>) {
    val arabic get() = tag == "ar"
}

object KeyboardCore {
    private fun layout(tag: String, name: String, vararg rows: String) =
        Layout(tag, name, rows.map { it.split(" ") })
    val layouts = listOf(
        layout("ar", "العربية", "ض ص ث ق ف غ ع ه خ ح ج", "ش س ي ب ل ا ت ن م ك ط", "ئ ء ؤ ر ى ة و ز ظ د ذ"),
        layout("en", "English", "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"),
        layout("fr", "Français", "a z e r t y u i o p", "q s d f g h j k l m", "w x c v b n é"),
        layout("de", "Deutsch", "q w e r t z u i o p ü", "a s d f g h j k l ö ä", "y x c v b n m ß"),
        layout("nl", "Nederlands", "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"),
        layout("es", "Español", "q w e r t y u i o p", "a s d f g h j k l ñ", "z x c v b n m ¿ ¡"),
        layout("tr", "Türkçe", "q w e r t y u ı o p ğ ü", "a s d f g h j k l ş i", "z x c v b n m ö ç")
    )
    val marks = listOf("َ", "ُ", "ِ", "ْ", "ّ", "ً", "ٌ", "ٍ", "ٓ", "ـ")
    val symbols = listOf(
        listOf("!", "@", "#", "$", "%", "&", "*", "(", ")", "−"),
        listOf("€", "£", "+", "=", "/", "\\", ":", ";", "\"", "؟"),
        listOf("<", ">", "[", "]", "{", "}", "_", "|", "~", "^")
    )
    val alternatives = mapOf(
        "ا" to listOf("أ","إ","آ","ٱ","ٰ"), "ه" to listOf("ة"), "ي" to listOf("ى","ئ"),
        "و" to listOf("ؤ"), "ك" to listOf("گ"), "ب" to listOf("پ"), "ج" to listOf("چ"),
        "ف" to listOf("ڤ"), "a" to listOf("á","à","â","ä","å","æ"), "e" to listOf("é","è","ê","ë"),
        "i" to listOf("í","ì","î","ï","ı"), "o" to listOf("ó","ò","ô","ö","œ"),
        "u" to listOf("ú","ù","û","ü"), "n" to listOf("ñ"), "?" to listOf("¿"), "!" to listOf("¡"), "¿" to listOf("?"), "¡" to listOf("!"), "c" to listOf("ç"), "s" to listOf("ß","ş"),
        "g" to listOf("ğ"), "." to listOf("…",":",";","!","؟","?"), "," to listOf("،","؛"),
        "،" to listOf(",","؛","؟"), "-" to listOf("−","–","—"), "'" to listOf("’","\"")
    )
    private val wordTail = Regex("[\\p{L}\\p{M}’']+$")
    fun tail(text: String) = wordTail.find(text)?.value.orEmpty()
    fun upper(text: String, language: String) = text.uppercase(Locale.forLanguageTag(language))
    fun speechJoin(before: String, utterance: String): String {
        val clean = utterance.trim()
        if (clean.isEmpty()) return ""
        return if (before.isNotEmpty() && !before.last().isWhitespace() && clean.first() !in ",.!?؛،؟:)" &&
            before.last() !in "([{") " $clean" else clean
    }
}

object Prediction {
    private val en = ("the and that there their them these then thanks thank this time today tomorrow together " +
        "hello help hey here have happy how however home hope good great morning night evening afternoon " +
        "please people place plan play work world would could should with without will want what when where " +
        "which while writing because before after message meeting project translation keyboard language love like " +
        "you your yours are from friend first finally fine can can't don't did done reply yes yesterday " +
        "really ready right see send sent soon some something sorry sure welcome well week weekend").split(' ')
    private val ar = ("السلام عليكم وعليكم ورحمة الله وبركاته صباح مساء الخير النور مرحبا أهلاً أهلا شكراً شكرا " +
        "شكرًا كيف حالك بخير تمام نعم لا ربما من على إلى الى في عن مع بعد قبل بين يوم اليوم الأيام " +
        "غدا غداً أمس الآن لان لأن هنا هناك هذا هذه ذلك تلك هؤلاء الذي التي لدينا لدينا لدي عندك عندنا " +
        "أنا نحن أنت انتم أنتم هو هي هم كان كانت تكون يكون ممكن جميل ممتاز رائع رسالة مشروع كتابة لوحة " +
        "مفاتيح ترجمة العربية الإنجليزية لغة موعد دقيقة دقيقة أسبوع سنة وقت مساءً صباحاً أرجو أريد يمكن يجب " +
        "عمل العمل البيت أهلك صديقي صديقتي تشرفت سعيدة سعيد حاضر أهلاً وسهلاً لسان").split(' ')
    private val typos = mapOf("teh" to "the", "adn" to "and", "recieve" to "receive",
        "thier" to "their", "wierd" to "weird", "seperate" to "separate", "definately" to "definitely")
    private val arTypos = mapOf("هاذا" to "هذا", "هاذه" to "هذه", "اللغه" to "اللغة", "الترجمه" to "الترجمة",
        "الكتابه" to "الكتابة", "الانجليزيه" to "الإنجليزية", "الاسبوع" to "الأسبوع", "اللذي" to "الذي")
    private val next = mapOf("how" to listOf("are","to"), "thank" to listOf("you"),
        "good" to listOf("morning","night","evening"), "see" to listOf("you"),
        "السلام" to listOf("عليكم"), "صباح" to listOf("الخير","النور"),
        "مساء" to listOf("الخير","النور"), "كيف" to listOf("حالك"), "وعليكم" to listOf("السلام"))
    fun choices(before: String, lang: String, personal: List<String>, ignored: List<String>): List<String> {
        val tail = KeyboardCore.tail(before).lowercase(Locale.ROOT)
        val dict = personal + when (lang) { "en" -> en; "ar" -> ar; else -> emptyList() }
        val known = tail in dict || tail in ignored
        val choices = if (tail.isBlank()) next[KeyboardCore.tail(before.trimEnd()).lowercase(Locale.ROOT)].orEmpty()
        else {
            val direct = listOfNotNull(if (lang == "en") typos[tail] else if (lang == "ar") arTypos[tail] else null)
            val prefix = dict.filter { it.startsWith(tail, ignoreCase = true) && !it.equals(tail, true) }
            val near = if (!known && direct.isEmpty() && prefix.isEmpty() && tail.length in 4..18 &&
                tail.none { Character.getType(it) == Character.NON_SPACING_MARK.toInt() } && lang in listOf("ar","en"))
                dict.filter { it.firstOrNull() == tail.firstOrNull() && oneEdit(tail,it) } else emptyList()
            (if (known) emptyList() else direct) + prefix + near
        }
        return choices.distinct().filterNot { value -> ignored.any { it.equals(value,true) } }.take(3)
    }
    private fun oneEdit(a:String,b:String):Boolean {
        if(kotlin.math.abs(a.length-b.length)>1) return false
        if(a.length==b.length) {
            val mismatches=a.indices.filter{a[it]!=b[it]}
            return mismatches.size==1 || (mismatches.size==2 && mismatches[1]==mismatches[0]+1 &&
                a[mismatches[0]]==b[mismatches[1]] && a[mismatches[1]]==b[mismatches[0]])
        }
        val longer=if(a.length>b.length)a else b
        val shorter=if(a.length>b.length)b else a
        val first=shorter.indices.firstOrNull{shorter[it]!=longer[it]} ?: return true
        return longer.regionMatches(first+1,shorter,first,shorter.length-first)
    }
    fun correction(word: String, lang: String, protected: List<String>, arabic:Boolean=false): String? {
        if (word.length < 3 || protected.any { it.equals(word,true) }) return null
        return when(lang) {
            "en" -> if(word.all { it.isLowerCase() }) typos[word] else null
            "ar" -> if(arabic) arTypos[word] else null
            else -> null
        }
    }
}
