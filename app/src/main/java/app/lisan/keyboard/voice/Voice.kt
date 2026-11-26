package app.lisan.keyboard.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.ModelDownloadListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

data class VoiceConfig(val tag: String, val localOnly: Boolean, val detect: Boolean, val preferOffline: Boolean = false)
interface VoiceEngine {
    fun available(local: Boolean): Boolean
    fun start(config: VoiceConfig, listener: (String, String) -> Unit)
    fun stop()
    fun cancel()
}

class AndroidVoice(private val context: Context) : VoiceEngine {
    private var recognizer: SpeechRecognizer? = null
    private var session = 0
    private var modelRecognizer:SpeechRecognizer?=null
    private fun recognitionIntent(config:VoiceConfig)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.tag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (config.localOnly || config.preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (Build.VERSION.SDK_INT >= 33) putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        if (Build.VERSION.SDK_INT >= 34 && config.detect) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
        }
    }
    override fun available(local: Boolean): Boolean = if (local)
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    else SpeechRecognizer.isRecognitionAvailable(context)
    override fun start(config: VoiceConfig, listener: (String, String) -> Unit) {
        cancel()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener("permission",""); return
        }
        if (!available(config.localOnly)) { listener("unavailable",""); return }
        val token = session
        try {
            val r = if (config.localOnly && Build.VERSION.SDK_INT >= 31)
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                private fun emit(state: String, value: String = "") { if (token == session) listener(state,value) }
                override fun onReadyForSpeech(params: Bundle?) = emit("listening")
                override fun onBeginningOfSpeech() = emit("listening")
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() = emit("processing")
                override fun onError(error: Int) = emit("error",error.toString())
                override fun onResults(results: Bundle?) = emit("done",results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                override fun onPartialResults(partialResults: Bundle?) = emit("partial")
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onLanguageDetection(results: Bundle) {
                    if (Build.VERSION.SDK_INT >= 34) emit("detected",results.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty())
                }
            })
            listener("starting","")
            r.startListening(recognitionIntent(config))
        } catch (_: Exception) { cancel(); listener("unavailable","") }
    }
    override fun stop() { runCatching { recognizer?.stopListening() } }
    override fun cancel() {
        session++
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    fun closeModelOperation() { runCatching { modelRecognizer?.destroy() };modelRecognizer=null }
    fun modelOperation(config:VoiceConfig,download:Boolean,report:(String)->Unit) {
        closeModelOperation()
        if(Build.VERSION.SDK_INT<33 || !available(true)) { report("unknown");return }
        try {
            val r=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            modelRecognizer=r
            fun emit(status:String,terminal:Boolean=true) {
                if(modelRecognizer===r) { if(terminal)closeModelOperation();report(status) }
            }
            val request=recognitionIntent(config.copy(localOnly=true))
            if(!download) r.checkRecognitionSupport(request,context.mainExecutor,object:RecognitionSupportCallback {
                override fun onSupportResult(info:RecognitionSupport) {
                    fun List<String>.matches()=any { it.equals(config.tag,true) ||
                        (config.tag.length==2 && it.substringBefore('-').equals(config.tag,true)) }
                    emit(when {
                        info.installedOnDeviceLanguages.matches()->"installed"
                        info.pendingOnDeviceLanguages.matches()->"scheduled"
                        info.supportedOnDeviceLanguages.matches()->"downloadable"
                        else->"unknown"
                    })
                }
                override fun onError(error:Int)=emit("unknown")
            })
            else if(Build.VERSION.SDK_INT>=34) r.triggerModelDownload(request,context.mainExecutor,object:ModelDownloadListener {
                override fun onProgress(completedPercent:Int)=emit("progress:${completedPercent.coerceIn(0,100)}",false)
                override fun onSuccess()=emit("installed")
                override fun onScheduled()=emit("scheduled")
                override fun onError(error:Int)=emit("unknown")
            })
            else { r.triggerModelDownload(request);emit("requested") }
        } catch(_:Exception) { closeModelOperation();report("unknown") }
    }
}

class MicPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) finish()
        else if (savedInstanceState == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),901)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        finish()
    }
}

fun voiceMessage(state: String, value: String, ar: Boolean): String {
    val pair = when (state) {
        "starting" -> "Opening microphone…" to "جارٍ فتح الميكروفون…"
        "partial","listening" -> "● Listening · tap mic to stop" to "● أستمع الآن · اضغط الميكروفون للإيقاف"
        "processing" -> "Processing speech…" to "جارٍ معالجة الكلام…"
        "detected" -> "● Listening · $value" to "● أستمع الآن · $value"
        "permission" -> "Allow microphone access, then tap again" to "اسمح بالميكروفون ثم اضغط مرة أخرى"
        "unavailable" -> "No speech service found. Install or update the Google app, or download the Whisper model in Voice settings." to "لا توجد خدمة تعرّف صوتي. ثبّت تطبيق Google أو حدّثه، أو نزّل نموذج Whisper من إعدادات الصوت."
        "model_missing" -> "Install the offline model in Voice settings." to "ثبّت النموذج المحلي من إعدادات الصوت."
        "fallback" -> "Using Google speech recognition…" to "جارٍ استخدام التعرف الصوتي من Google…"
        "error" -> when (value.toIntOrNull()) {
            1,2 -> "Speech connection failed. Check the internet or download the offline Arabic pack in Google settings." to "فشل الاتصال بخدمة الصوت. تحقق من الإنترنت أو نزّل حزمة العربية دون اتصال من إعدادات Google."
            5 -> "Speech service busy. Tap to retry." to "خدمة الصوت مشغولة. اضغط للمحاولة."
            8 -> "Speech service busy. Tap again in a moment." to "خدمة الصوت مشغولة. اضغط بعد لحظة."
            3,9 -> "Microphone unavailable or permission denied." to "الميكروفون غير متاح أو الإذن مرفوض."
            6,7 -> "No speech recognized. Tap to retry." to "لم يُتعرف على كلام. اضغط للمحاولة."
            12,13 -> "Language unavailable in this provider." to "اللغة غير متوفرة لدى هذا المحرك."
            else -> "Speech recognition stopped. Tap to retry." to "توقف التعرف الصوتي. اضغط للمحاولة."
        }
        else -> "" to ""
    }
    return if (ar) pair.second else pair.first
}

fun voiceTerminal(state:String) = state in setOf("done","error","unavailable","permission","model_missing")
