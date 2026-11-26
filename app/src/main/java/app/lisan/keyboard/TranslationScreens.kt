package app.lisan.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.lisan.keyboard.core.Prefs
import app.lisan.keyboard.core.isArabic
import app.lisan.keyboard.privacy.Vault
import app.lisan.keyboard.translation.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI

private data class CloudApproval(val url:URI,val text:String,val source:String,val target:String)

@Composable internal fun TranslationScreen(p:Prefs,w:W,edit:EditPrefs,open:(String)->Unit) {
    val ctx=LocalContext.current
    val locale=LocalConfiguration.current.locales[0]
    val ar=isArabic(p,ctx)
    val scope=rememberCoroutineScope()
    val languages=remember(ar){LocalTranslator.languages.map { it to displayLanguage(it,ar) }.sortedBy {it.second}}
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<TranslationResult?>(null) }
    var output by remember { mutableStateOf("") }
    var viaCloud by remember { mutableStateOf(false) }
    var endpoint by remember(p.cloudEndpoint) { mutableStateOf(p.cloudEndpoint) }
    var approval by remember { mutableStateOf<CloudApproval?>(null) }
    var source by remember(p.source) { mutableStateOf(p.source) }
    var target by remember(p.target) { mutableStateOf(p.target) }
    var message by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun reset() { job?.cancel(); job=null; busy=false; result=null; output=""; missing=false; message="" }
    fun run(download:Boolean,text:String=input,approved:CloudApproval?=null) {
        reset()
        if(text.isBlank())return
        val resolved=approved?.target ?: resolvedTarget(p.copy(target=target),locale)
        val from=approved?.source ?: source
        busy=true
        job=scope.launch(start=CoroutineStart.LAZY) {
            try {
                val r=if(approved!=null) CloudTranslator.translate(approved.url,text,from,resolved)
                    else LocalTranslator.translate(text,from,resolved,download,p.wifi)
                result=r;output=r.text;viaCloud=approved!=null
                if(p.saveHistory) withContext(Dispatchers.IO) {
                    runCatching {
                        Vault.get(ctx).update("history"){listOf(JSONObject().put("text",text).put("out",r.text).put("from",r.source).put("to",r.target))+it}
                    }
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception){ message=if(approved!=null)w("Server request failed. Check the address and provider.","فشل طلب الخادم. تحقق من العنوان والمزوّد.") else describeTranslationError(e,ar); missing=e is MissingModels }
            finally { if(job===currentCoroutineContext()[Job])busy=false }
        }
        job?.start()
    }
    Page(w("Language studio","استوديو اللغة"),w("Translate","الترجمة"),
        w("ML Kit translates offline after model installation. The optional server never receives text without a separate confirmation.",
            "يترجم ML Kit دون اتصال بعد تثبيت النماذج. لا يستقبل الخادم الاختياري أي نص دون تأكيد منفصل."))
    Tile {
        Choose(w("Source for this session","المصدر لهذه الجلسة"),source,listOf("auto" to displayLanguage("auto",ar))+languages){source=it;reset()}
        Choose(w("Target for this session","الهدف لهذه الجلسة"),target,listOf("device" to displayLanguage("device",ar))+languages){target=it;reset()}
        TextButton(enabled=source!=p.source || target!=p.target,onClick={edit{it.copy(source=source,target=target)}}){
            Text(w("Save languages as defaults","حفظ اللغتين كإعداد افتراضي"))
        }
        TextButton(enabled=source!="auto" || result?.source in LocalTranslator.languages,onClick={
            val formerTarget=resolvedTarget(p.copy(target=target),locale).substringBefore('-')
            val formerSource=if(source=="auto")result?.source.orEmpty() else source
            if(formerTarget in LocalTranslator.languages && formerSource in LocalTranslator.languages) {
                if(output.isNotBlank())input=output.take(5000)
                reset(); source=formerTarget;target=formerSource
            } else message=w("Choose a supported target before swapping.","اختر لغة هدف مدعومة قبل التبديل.")
        }){Text(w("⇄ Swap languages","⇄ تبديل اللغتين"))}
        Field(input,{input=it.take(5000);reset()},w("Text to translate","النص المراد ترجمته"),3)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            OutlinedButton(onClick={
                val manager=ctx.getSystemService(ClipboardManager::class.java)
                val c=runCatching{manager.primaryClip}.getOrNull()
                val clipText=if(c!=null && c.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                    c.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE")!=true &&
                    c.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE_DATA")!=true)
                    c.getItemAt(0).text?.toString().orEmpty().take(5000) else ""
                if(clipText.isNotBlank()) {input=clipText;reset();if(p.autoPaste)run(false,clipText)}
                else message=w("No accessible copied text.","لا يتوفر نص منسوخ قابل للقراءة.")
            }){Text(w("Paste","لصق"))}
            TextButton(onClick={input="";reset()}){Text(w("Clear","مسح"))}
        }
        Button(onClick={run(false)},enabled=!busy && input.isNotBlank(),modifier=Modifier.fillMaxWidth()){
            Text(if(busy)w("Working…","جارٍ المعالجة…") else if(result!=null)w("Translate again with Google","إعادة الترجمة باستخدام Google") else w("Translate with Google","الترجمة باستخدام Google"))
        }
        if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick={reset()}){Text(w("Stop waiting","إيقاف الانتظار"))} }
        if(message.isNotBlank()) Help(message)
        if(missing) OutlinedButton(onClick={run(true)},enabled=!busy){Text(w("Download models and translate with Google","تنزيل النماذج والترجمة باستخدام Google"))}
        CloudTranslator.endpoint(p.cloudEndpoint)?.let { url ->
            OutlinedButton(enabled=!busy && input.isNotBlank(),onClick={
                approval=CloudApproval(url,input,source,resolvedTarget(p.copy(target=target),locale).substringBefore('-'))
            }){Text(w("Review and send to server","معاينة وإرسال إلى الخادم"))}
        }
    }
    result?.let { r ->
        Tile {
            Sub("${displayLanguage(r.source,ar)} → ${displayLanguage(r.target,ar)}")
            Field(output,{output=it.take(20_000)},w("Editable translation","الترجمة القابلة للتعديل"),3)
            if(!viaCloud)GoogleTranslationBadge(w) else Help(w("Translated by your configured server.","الترجمة من الخادم الذي ضبطته."))
            Row {
                TextButton(enabled=output.isNotBlank(),onClick={ctx.copyText("Lisan translation",output)}){Text(w("Copy","نسخ"))}
                TextButton(enabled=output.isNotBlank(),onClick={ctx.shareText(output,w("Share translation","مشاركة الترجمة"))}){Text(w("Share","مشاركة"))}
            }
        }
    }
    Tile {
        Sub(w("Translation preferences","تفضيلات الترجمة"))
        Toggle(w("Translate after explicit Paste","ترجم بعد الضغط على لصق"),p.autoPaste){v->edit{it.copy(autoPaste=v)}}
        Toggle(w("Wi-Fi only for model downloads","تنزيل النماذج عبر Wi‑Fi فقط"),p.wifi){v->edit{it.copy(wifi=v)}}
        Toggle(w("Keep encrypted translation history","حفظ سجل ترجمة مشفّر"),p.saveHistory){v->edit{it.copy(saveHistory=v)}}
        Help(w("Automatic paste and keyboard translation always use ML Kit locally.","الترجمة عند اللصق ومن لوحة المفاتيح تستخدم ML Kit محلياً دائماً."))
        Help(TRANSLATION_DISCLAIMER)
    }
    Tile {
        Sub(w("Optional translation server","خادم ترجمة اختياري"))
        Help(w("Use a LibreTranslate-compatible HTTPS endpoint without credentials in the URL. No server is configured by default. Its operator determines retention.","استخدم عنوان HTTPS متوافقاً مع LibreTranslate دون بيانات اعتماد في العنوان. لا يوجد خادم افتراضي. يحدد مشغّل الخادم سياسة الاحتفاظ."))
        Field(endpoint,{endpoint=it.take(300)},w("HTTPS endpoint (ending in /translate)","عنوان HTTPS (ينتهي بـ /translate)"))
        Row {
            TextButton(enabled=CloudTranslator.endpoint(endpoint)!=null && endpoint!=p.cloudEndpoint,onClick={
                edit{it.copy(cloudEndpoint=endpoint.trim())}
            }) {Text(w("Save address","حفظ العنوان"))}
            TextButton(enabled=p.cloudEndpoint.isNotBlank(),onClick={endpoint="";edit{it.copy(cloudEndpoint="")}}){Text(w("Disable","تعطيل"))}
        }
    }
    approval?.let { a ->
        AlertDialog(onDismissRequest={approval=null},title={Text(w("Send this text for translation?","إرسال هذا النص للترجمة؟"))},
            text={Column(Modifier.heightIn(max=260.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Help(w("Recipient: ${a.url.host}. This server receives the text and language choices. Do not send passwords or sensitive text.","الوجهة: ${a.url.host}. سيستقبل الخادم النص واختيارات اللغات. لا ترسل كلمات مرور أو نصوصاً حساسة."))
                androidx.compose.foundation.layout.Box(Modifier.weight(1f,false)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { Text(a.text) }
                }
            }},
            confirmButton={TextButton(onClick={approval=null;run(false,a.text,a)}){Text(w("Send and translate","إرسال وترجمة"))}},
            dismissButton={TextButton(onClick={approval=null}){Text(w("Cancel","إلغاء"))}})
    }
    Action(w("Translation history","سجل الترجمات")){open("history")}
    Action(w("Manage language models","إدارة نماذج اللغات")){open("models")}
}

@Composable internal fun Models(p:Prefs,w:W,edit:EditPrefs) {
    val ar=isArabic(p,LocalContext.current)
    val scope=rememberCoroutineScope()
    var models by remember {mutableStateOf(emptySet<String>())}
    var q by remember {mutableStateOf("")}
    var onlyInstalled by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf("")}
    var working by remember {mutableStateOf<String?>(null)}
    var askDelete by remember {mutableStateOf<String?>(null)}
    var job by remember {mutableStateOf<Job?>(null)}
    suspend fun refresh() { try{models=LocalTranslator.models()}catch(e:CancellationException){throw e}catch(e:Exception){status=describeTranslationError(e,ar)} }
    LaunchedEffect(Unit){refresh()}
    fun operation(tag:String,remove:Boolean) {
        if(working!=null) return
        working=tag;status=""
        job=scope.launch {
            try {
                if(remove) LocalTranslator.remove(tag) else LocalTranslator.download(tag,p.wifi)
                refresh()
                status=if(remove)w("Model removed.","حُذف النموذج.") else w("Model installed.","ثُبّت النموذج.")
            } catch(e:CancellationException){throw e}
            catch(e:Exception){status=describeTranslationError(e,ar)}
            finally{working=null}
        }
    }
    Page(w("On-device storage","التخزين المحلي"),w("Translation models","نماذج الترجمة"),
        w("The list comes from ML Kit. Google estimates roughly 30 MB per downloaded model; exact sizes and pausing are not exposed.",
            "القائمة مأخوذة من ML Kit. تقدّر Google نحو 30 ميغابايت للنموذج، ولا تتيح الواجهة الحجم الدقيق أو إيقاف التنزيل مؤقتاً."))
    Tile {
        Toggle(w("Require Wi-Fi for downloads","تنزيل عبر Wi‑Fi فقط"),p.wifi){n->edit{it.copy(wifi=n)}}
        Toggle(w("Installed only","المثبّتة فقط"),onlyInstalled){onlyInstalled=it}
        Field(q,{q=it.take(60)},w("Search models","البحث عن نموذج"))
        Help(w("${models.size} downloaded models","${models.size} نماذج منزّلة"))
        TextButton(onClick={scope.launch{refresh()}}){Text(w("Refresh installed status","تحديث حالة التثبيت"))}
        if(working!=null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Help(w("Working: ${displayLanguage(working.orEmpty(),ar)}","جارٍ التنفيذ: ${displayLanguage(working.orEmpty(),ar)}"))
            TextButton(onClick={job?.cancel();working=null;status=w("Stopped waiting. Android may finish the download.","توقف الانتظار. قد يُكمل أندرويد التنزيل.")}){Text(w("Stop waiting","إيقاف الانتظار"))}
        }
        if(status.isNotBlank())Help(status)
    }
    remember(ar){LocalTranslator.languages.sortedBy {displayLanguage(it,ar)}}
        .filter{(!onlyInstalled || it in models || it=="en") && (displayLanguage(it,ar).contains(q,true)||it.contains(q,true))}
        .forEach { tag ->
            Row(Modifier.fillMaxWidth().padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Sub("${displayLanguage(tag,ar)} · $tag")
                    Help(when { tag=="en"->w("Built in","مضمّن"); tag in models->w("Installed","مثبّت"); else->w("Not installed · availability checked by Google on request","غير مثبّت · تتحقق Google من التوفر عند الطلب") })
                }
                if(tag!="en") OutlinedButton(enabled=working==null,onClick={if(tag in models)askDelete=tag else operation(tag,false)}){
                    Text(if(tag in models)w("Remove","حذف")else w("Request model","طلب النموذج"))
                }
            }
        }
    askDelete?.let { tag ->
        AlertDialog(onDismissRequest={askDelete=null},title={Text(w("Remove model?","حذف النموذج؟"))},
            text={Text(displayLanguage(tag,ar))},confirmButton={TextButton(onClick={askDelete=null;operation(tag,true)}){Text(w("Remove","حذف"))}},
            dismissButton={TextButton(onClick={askDelete=null}){Text(w("Cancel","إلغاء"))}})
    }
}
@Composable internal fun SelectionText(text:String) {
    androidx.compose.foundation.text.selection.SelectionContainer { Text(text,color=MaterialTheme.colorScheme.onSurface) }
}

@Composable internal fun GoogleTranslationBadge(w:W) {
    Image(painterResource(R.drawable.google_translation_badge),w("Powered by Google Translate","ترجمة آلية من Google"),
        Modifier.width(176.dp).height(16.dp))
}
