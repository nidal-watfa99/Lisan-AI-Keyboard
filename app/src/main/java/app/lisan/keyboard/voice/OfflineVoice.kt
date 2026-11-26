package app.lisan.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs

object SpeechModel {
    const val bytes = 32_152_673L
    const val sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
    private const val url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
    private val lock = Mutex()
    fun file(c:Context) = File(c.noBackupFilesDir,"whisper-tiny-q5_1.bin")
    private fun part(c:Context) = File(c.noBackupFilesDir,"whisper-tiny-q5_1.part")
    fun installed(c:Context) = file(c).length()==bytes
    fun progress(c:Context) = if(installed(c)) bytes else part(c).length().coerceIn(0,bytes)
    suspend fun delete(c:Context) = withContext(Dispatchers.IO) { lock.withLock {
        listOf(file(c),part(c)).forEach { if (it.exists()) check(it.delete()) }
    } }
    private suspend fun verify(candidate:File,destination:File) {
        val digest=MessageDigest.getInstance("SHA-256")
        candidate.inputStream().buffered().use { stream ->
            val buffer=ByteArray(65536)
            while(true) {
                currentCoroutineContext().ensureActive()
                val n=stream.read(buffer); if(n<0) break
                digest.update(buffer,0,n)
            }
        }
        if(candidate.length()!=bytes || digest.digest().joinToString(""){"%02x".format(it)}!=sha256) {
            candidate.delete()
            error("Invalid speech model")
        }
        check(candidate.renameTo(destination))
    }
    private suspend fun copy(stream:InputStream,target:File,offset:Long,progress:(Long)->Unit) {
        java.io.FileOutputStream(target,offset>0).buffered().use { output ->
            val buffer=ByteArray(65536)
            var count=offset
            while(true) {
                currentCoroutineContext().ensureActive()
                val n=stream.read(buffer); if(n<0)break
                count+=n
                if(count>bytes) error("Oversized speech model")
                output.write(buffer,0,n)
                progress(count)
            }
        }
    }
    suspend fun download(c:Context,onProgress:(Long)->Unit) = withContext(Dispatchers.IO) { lock.withLock {
        if(installed(c)) return@withLock
        val partial=part(c)
        if(partial.length()>bytes) partial.delete()
        val offset=partial.length()
        if(offset==bytes) { verify(partial,file(c)); return@withLock }
        val connection=(URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout=12_000;readTimeout=8_000;setRequestProperty("Accept-Encoding","identity")
            if(offset>0) setRequestProperty("Range","bytes=$offset-")
        }
        try {
            val start=when(connection.responseCode) {
                200->0L
                206->{
                    val range=connection.getHeaderField("Content-Range").orEmpty()
                    check(offset>0 && range=="bytes $offset-${bytes-1}/$bytes"); offset
                }
                else->error("Model download unavailable")
            }
            connection.inputStream.use { copy(it,partial,start,onProgress) }
            if(partial.length()!=bytes) error("Incomplete download; can resume")
            verify(partial,file(c))
        } finally { connection.disconnect() }
    } }
    suspend fun import(c:Context,uri:Uri,onProgress:(Long)->Unit) = withContext(Dispatchers.IO) { lock.withLock {
        val tmp=File(c.noBackupFilesDir,"whisper.import.tmp")
        try {
            checkNotNull(c.contentResolver.openInputStream(uri)).use { copy(it,tmp,0,onProgress) }
            verify(tmp,file(c))
            part(c).delete()
        } finally { tmp.delete() }
    } }
}

object WhisperNative {
    val available = runCatching { System.loadLibrary("lisan_voice") }.isSuccess
    external fun decode(path:String,samples:ShortArray,language:String,canceled:AtomicBoolean,threads:Int):ByteArray?
}

internal class SpeechGate {
    private var speakingMs=0L
    private var lastActivity=0L
    fun observe(ms:Long,peak:Int,mean:Int,duration:Long):Boolean {
        if(peak>=650 && mean>=100) { speakingMs+=duration; lastActivity=ms }
        return (speakingMs>=140 && ms-lastActivity>=1_200) || (speakingMs<140 && ms>=5_500) || ms>=24_000
    }
    val heard get() = speakingMs>=140
}

class OfflineVoice(private val context:Context) : VoiceEngine {
    private class Turn {
        val cancel=AtomicBoolean()
        val stop=AtomicBoolean()
        var task:Job?=null
    }
    private var current:Turn?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    override fun available(local:Boolean)=WhisperNative.available && SpeechModel.installed(context)
    override fun start(config:VoiceConfig,listener:(String,String)->Unit) {
        cancel()
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            listener("permission",""); return
        }
        if(!WhisperNative.available) { listener("unavailable",""); return }
        if(!SpeechModel.installed(context)) { listener("model_missing",""); return }
        val turn=Turn()
        current=turn
        fun emit(state:String,value:String="") { if(current===turn && !turn.cancel.get()) listener(state,value) }
        turn.task=scope.launch(start=CoroutineStart.LAZY) {
            emit("starting")
            try {
                val pcm=withContext(Dispatchers.IO) {
                    audioLock.withLock { capture(turn) { withContext(Dispatchers.Main) { emit("listening") } } }
                }
                if(pcm.isEmpty()) { emit("error","7");return@launch }
                emit("processing")
                val tag=if(config.detect) "auto" else config.tag.substringBefore('-').lowercase(java.util.Locale.ROOT)
                val data=try { withContext(Dispatchers.Default) {
                    decodeLock.withLock { WhisperNative.decode(SpeechModel.file(context).path,pcm,tag,turn.cancel,
                        Runtime.getRuntime().availableProcessors().coerceIn(1,3)) }
                } } finally { pcm.fill(0) }
                val answer=data?.toString(Charsets.UTF_8)?.trim()
                if(answer.isNullOrBlank()) emit("error","7") else emit("done",answer)
            } catch(e:CancellationException) { throw e }
              catch(_:LinkageError) { emit("unavailable") }
              catch(_:Exception) { emit("error","3") }
            finally { if(current===turn) current=null }
        }
        turn.task?.start()
    }
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun capture(turn:Turn,ready:suspend ()->Unit):ShortArray {
        val rate=16000
        val min = AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        check(min>0)
        val r=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,maxOf(min,6400))
        val pcm=ShortArray(rate*24)
        val chunk=ShortArray(640)
        var size=0
        val gate=SpeechGate()
        try {
            check(r.state==AudioRecord.STATE_INITIALIZED)
            r.startRecording(); check(r.recordingState==AudioRecord.RECORDSTATE_RECORDING)
            ready()
            val started=SystemClock.elapsedRealtime()
            while(!turn.stop.get() && !turn.cancel.get() && size+chunk.size<=pcm.size) {
                val elapsed=SystemClock.elapsedRealtime()-started
                if(elapsed>=24_000)break
                val n=r.read(chunk,0,chunk.size,AudioRecord.READ_NON_BLOCKING)
                if(n==0) { delay(15); continue }
                check(n>0)
                var peak=0;var total=0
                for(i in 0 until n) { val v=abs(chunk[i].toInt());peak=maxOf(peak,v);total+=v }
                chunk.copyInto(pcm,size,0,n);size+=n
                if(gate.observe(elapsed,peak,total/n,n*1000L/rate))break
            }
            return if(gate.heard && size>=3200 && !turn.cancel.get()) pcm.copyOf(size) else ShortArray(0)
        } finally {
            runCatching { r.stop() }; r.release()
            pcm.fill(0)
            chunk.fill(0)
        }
    }
    override fun stop() { current?.stop?.set(true) }
    override fun cancel() { current?.apply { cancel.set(true);task?.cancel() };current=null }
    companion object { private val audioLock=Mutex(); private val decodeLock=Mutex() }
}

class VoiceRouter(context:Context) {
    val android=AndroidVoice(context)
    val offline=OfflineVoice(context)
    private var engine:VoiceEngine?=null
    fun start(mode:String,config:VoiceConfig,listener:(String,String)->Unit) {
        cancel()
        val offlineReady=offline.available(true)
        val next:VoiceEngine
        var cfg=config
        when {
            // Whisper model installed: use it (fully on-device).
            mode!="system" && offlineReady -> next=offline
            // Android 12+ on-device recognizer.
            mode!="system" && android.available(true) -> next=android
            // No offline engine (e.g. Android 11 without the Whisper model): fall back to the
            // standard Android/Google recognizer instead of failing. It prefers offline packs when installed.
            else -> {
                next=android
                cfg=config.copy(localOnly=false,preferOffline=mode!="system")
                if(mode!="system") listener("fallback","")
            }
        }
        engine=next
        next.start(cfg) { state,value ->
            if(engine===next) listener(state,value)
        }
    }
    fun stop() { engine?.stop() }
    fun cancel() { engine?.cancel();engine=null }
}
