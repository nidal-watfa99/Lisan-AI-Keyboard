package app.lisan.keyboard.tashkeel

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONObject
import java.nio.LongBuffer
import java.text.Normalizer

/**
 * On-device Arabic diacritization (tashkeel) using the rawi-ensemble ONNX model
 * (TigreGotico/rawi-ensemble, Apache-2.0). The model and vocab are downloaded into
 * assets/tashkeel at build time (see app/build.gradle).
 *
 * Pipeline: NFD → bare base letters → char ids → model → one diacritic class per letter
 * → attach the class's (NFD) marks to the letter → NFC. Marks the user typed are kept.
 */
object Tashkeel {
    private const val MODEL = "tashkeel/rawi_ensemble.int8.onnx"
    private const val VOCAB = "tashkeel/vocab.json"
    private const val CHUNK = 400

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName = "input"
    private val charToIdx = HashMap<Int, Long>()
    private val idxToDiac = HashMap<Long, String>()
    @Volatile var lastError: String = ""
        private set

    fun bundled(ctx: Context): Boolean = runCatching { ctx.assets.open(MODEL).close(); true }.getOrDefault(false)

    @Synchronized
    private fun ensure(ctx: Context): OrtSession {
        session?.let { return it }
        val vocab = JSONObject(ctx.assets.open(VOCAB).bufferedReader(Charsets.UTF_8).use { it.readText() })
        parseVocab(vocab)
        check(charToIdx.isNotEmpty() && idxToDiac.isNotEmpty()) { "Empty tashkeel vocabulary" }
        val bytes = ctx.assets.open(MODEL).use { it.readBytes() }
        val e = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        val s = e.createSession(bytes, opts)
        inputName = s.inputNames.firstOrNull { it == "input" } ?: s.inputNames.first()
        env = e; session = s
        return s
    }

    private fun findMap(root: JSONObject, vararg names: String): JSONObject? {
        names.forEach { n -> root.optJSONObject(n)?.let { return it } }
        return null
    }

    /** Accepts {char: id} or {id: char} for both tables. */
    private fun parseVocab(root: JSONObject) {
        charToIdx.clear(); idxToDiac.clear()
        val c2i = findMap(root, "char_to_idx", "char2idx", "chars", "char_to_id") ?: root
        c2i.keys().forEach { k ->
            val v = c2i.get(k)
            if (v is Number) {
                if (k.codePointCount(0, k.length) == 1) charToIdx[k.codePointAt(0)] = v.toLong()
            } else if (v is String && v.codePointCount(0, v.length) == 1) {
                k.toLongOrNull()?.let { charToIdx[v.codePointAt(0)] = it }
            }
        }
        val d2i = findMap(root, "diac_to_idx", "diac2idx", "diacritics", "diac_to_id") ?: return
        d2i.keys().forEach { k ->
            val v = d2i.get(k)
            if (v is Number) idxToDiac[v.toLong()] = k
            else if (v is String) k.toLongOrNull()?.let { idxToDiac[it] = v }
        }
    }

    private fun isMark(cp: Int) = Character.getType(cp) == Character.NON_SPACING_MARK.toInt()
    private fun isHamzaMark(cp: Int) = cp == 0x0653 || cp == 0x0654 || cp == 0x0655
    private fun isArabicLetter(cp: Int) = cp in 0x0621..0x064A || cp in 0x0671..0x06D3

    fun hasArabic(text: String) = text.codePoints().anyMatch { isArabicLetter(it) }
    /** True if the text already carries vowel marks (harakat), ignoring hamza/madda. */
    fun hasHarakat(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .codePoints().anyMatch { it in 0x064B..0x0652 || it == 0x0670 }

    fun strip(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder()
        nfd.codePoints().forEach { if (!(isMark(it) && !isHamzaMark(it))) sb.appendCodePoint(it) }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
    }

    fun diacritize(ctx: Context, text: String): String {
        if (text.isBlank() || !hasArabic(text)) return text
        return try {
            val s = ensure(ctx)
            val out = run(s, text)
            lastError = ""
            out
        } catch (t: Throwable) {
            lastError = (t.message ?: t.javaClass.simpleName).take(160)
            throw t
        }
    }

    private fun run(s: OrtSession, text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val bases = ArrayList<Int>()
        val marks = ArrayList<StringBuilder>()
        var i = 0
        while (i < nfd.length) {
            val cp = nfd.codePointAt(i)
            if (isMark(cp) && bases.isNotEmpty()) marks.last().appendCodePoint(cp)
            else { bases += cp; marks += StringBuilder() }
            i += Character.charCount(cp)
        }
        val predicted = arrayOfNulls<String>(bases.size)
        // Runs of characters the model knows; anything else (Latin, emoji, ...) splits the text.
        var start = 0
        while (start < bases.size) {
            if (charToIdx[bases[start]] == null) { start++; continue }
            var end = start
            while (end < bases.size && charToIdx[bases[end]] != null && end - start < CHUNK) end++
            // Prefer to cut long runs at a space.
            if (end - start >= CHUNK) {
                val space = (end - 1 downTo start + CHUNK / 2).firstOrNull { bases[it] == ' '.code }
                if (space != null) end = space + 1
            }
            if ((start until end).any { isArabicLetter(bases[it]) }) {
                val ids = LongArray(end - start) { charToIdx.getValue(bases[start + it]) }
                val classes = infer(s, ids)
                for (k in classes.indices) {
                    val c = classes[k]
                    if (c != 0L) predicted[start + k] = idxToDiac[c]
                }
            }
            start = end
        }
        val sb = StringBuilder()
        for (k in bases.indices) {
            sb.appendCodePoint(bases[k])
            val typed = marks[k].toString()
            val typedHarakat = typed.codePoints().anyMatch { !isHamzaMark(it) }
            val model = predicted[k]
            when {
                typedHarakat || model == null -> sb.append(typed)
                typed.isNotEmpty() -> { // user typed a hamza/madda: keep it, add the model's vowels
                    sb.append(typed)
                    model.codePoints().forEach { if (!isHamzaMark(it)) sb.appendCodePoint(it) }
                }
                else -> sb.append(model)
            }
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
    }

    @Suppress("UNCHECKED_CAST")
    private fun infer(s: OrtSession, ids: LongArray): LongArray {
        val e = env ?: OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(e, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { tensor ->
            s.run(mapOf(inputName to tensor)).use { result ->
                val value = result.get(0).value
                return when (value) {
                    is Array<*> -> when (val row = value[0]) {
                        is LongArray -> row
                        is IntArray -> LongArray(row.size) { row[it].toLong() }
                        is Array<*> -> { // logits [1, N, C] → argmax
                            val logits = row as Array<FloatArray>
                            LongArray(logits.size) { p ->
                                var best = 0; var bv = Float.NEGATIVE_INFINITY
                                logits[p].forEachIndexed { c, v -> if (v > bv) { bv = v; best = c } }
                                best.toLong()
                            }
                        }
                        else -> error("Unexpected model output")
                    }
                    is LongArray -> value
                    else -> error("Unexpected model output")
                }
            }
        }
    }
}
