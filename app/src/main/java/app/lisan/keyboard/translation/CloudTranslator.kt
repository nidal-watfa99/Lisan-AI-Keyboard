package app.lisan.keyboard.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

object CloudTranslator {
    fun endpoint(text:String):URI? = runCatching { URI(text.trim()) }.getOrNull()?.takeIf {
        it.scheme=="https" && it.host!=null && it.userInfo==null && it.fragment==null && it.rawQuery==null &&
            (it.port==-1 || it.port in 1..65535 && it.port!=80) && it.rawPath.isNotBlank() && text.length<=300
    }
    suspend fun translate(endpoint:URI,text:String,source:String,target:String):TranslationResult = withContext(Dispatchers.IO) {
        require(endpoint(endpoint.toString())==endpoint)
        require(text.isNotBlank() && text.length<=5000 && source.length<=35 && target.length<=35)
        val body=JSONObject().put("q",text).put("source",source).put("target",target).put("format","text")
            .toString().toByteArray(Charsets.UTF_8)
        val connection=(endpoint.toURL().openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects=false
            useCaches=false
            connectTimeout=10_000
            readTimeout=15_000
            requestMethod="POST"
            doOutput=true
            setRequestProperty("Content-Type","application/json; charset=utf-8")
            setRequestProperty("Accept","application/json")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            connection.outputStream.use { it.write(body) }
            check(connection.responseCode==200 && connection.contentType.orEmpty().startsWith("application/json",true))
            val out=java.io.ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer=ByteArray(4096)
                while(true) {
                    val n=stream.read(buffer);if(n<0)break
                    check(out.size()+n<=65_536)
                    out.write(buffer,0,n)
                }
            }
            val data=JSONObject(out.toString("UTF-8"))
            val result=data.getString("translatedText")
            check(result.isNotBlank() && result.length<=20_000)
            val detected=data.optJSONObject("detectedLanguage")?.optString("language")
                ?.takeIf { Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8}){0,3}").matches(it) }
            TranslationResult(result,detected ?: source,target)
        } finally { connection.disconnect() }
    }
}
