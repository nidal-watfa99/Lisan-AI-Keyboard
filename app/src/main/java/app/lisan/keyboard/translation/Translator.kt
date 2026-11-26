package app.lisan.keyboard.translation

import app.lisan.keyboard.core.Prefs
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class TranslationResult(val text: String, val source: String, val target: String)
class MissingModels(val languages: Set<String>) : IllegalStateException()
class UnrecognizedLanguage : IllegalArgumentException()
class UnsupportedLanguage(val tag: String) : IllegalArgumentException()
const val TRANSLATION_DISCLAIMER = "This service may contain translations powered by Google. Google disclaims all warranties related to the translations, express or implied, including any warranties of accuracy, reliability, and any implied warranties of merchantability, fitness for a particular purpose and noninfringement."

interface TranslationEngine {
    val languages: List<String>
    suspend fun models(): Set<String>
    suspend fun download(tag: String, wifi: Boolean)
    suspend fun remove(tag: String)
    suspend fun translate(text: String, source: String, target: String, download: Boolean, wifi: Boolean): TranslationResult
}

object LocalTranslator : TranslationEngine {
    override val languages get() = TranslateLanguage.getAllLanguages()
    private val manager get() = RemoteModelManager.getInstance()
    private fun model(tag: String) = TranslateRemoteModel.Builder(tag).build()
    private fun conditions(wifi: Boolean) = DownloadConditions.Builder().apply { if (wifi) requireWifi() }.build()
    override suspend fun models() = manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()
    override suspend fun download(tag: String, wifi: Boolean) {
        require(tag in languages && tag != "en")
        manager.download(model(tag), conditions(wifi)).await()
    }
    override suspend fun remove(tag: String) { require(tag in languages && tag != "en"); manager.deleteDownloadedModel(model(tag)).await() }
    private fun language(tag: String) = TranslateLanguage.fromLanguageTag(tag) ?: throw UnsupportedLanguage(tag)
    override suspend fun translate(text: String, source: String, target: String, download: Boolean, wifi: Boolean): TranslationResult {
        require(text.isNotBlank() && text.length <= 5000)
        val detected = if (source == "auto") {
            val id = LanguageIdentification.getClient()
            try { id.identifyLanguage(text).await().also { if (it == "und") throw UnrecognizedLanguage() } }
            finally { id.close() }
        } else source
        val from = language(detected); val to = language(target)
        if (from == to) return TranslationResult(text, from, to)
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        try {
            val absent = (setOf(from,to) - "en") - models()
            if (absent.isNotEmpty() && !download) throw MissingModels(absent)
            if (download) client.downloadModelIfNeeded(conditions(wifi)).await()
            return TranslationResult(client.translate(text).await(),from,to)
        } finally { client.close() }
    }
}

fun resolvedTarget(prefs: Prefs, locale: Locale): String =
    if (prefs.target != "device") prefs.target else locale.toLanguageTag()

fun displayLanguage(tag: String, ar: Boolean): String = when (tag) {
    "device" -> if (ar) "لغة الجهاز" else "Device language"
    "auto" -> if (ar) "اكتشاف تلقائي" else "Auto-detect"
    else -> Locale.forLanguageTag(tag).getDisplayName(if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH)
}

fun describeTranslationError(e: Throwable, ar: Boolean): String = when(e) {
    is MissingModels -> if (ar) "النماذج غير مثبتة: ${e.languages.joinToString()}" else "Models not installed: ${e.languages.joinToString()}"
    is UnrecognizedLanguage -> if (ar) "تعذر اكتشاف اللغة؛ اختر لغة المصدر أو اكتب نصاً أطول." else "Could not identify the language. Choose a source language or enter more text."
    is UnsupportedLanguage -> if (ar) "اللغة غير متاحة محلياً: ${e.tag}. اختر لغة مدعومة." else "Language not available locally: ${e.tag}. Select a supported language."
    else -> if (ar) "تعذرت العملية. تحقق من الاتصال ومساحة التخزين وإعداد Wi‑Fi." else "Operation failed. Check your connection, available storage, and Wi-Fi setting."
}
