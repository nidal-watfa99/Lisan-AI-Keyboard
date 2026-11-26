package app.lisan.keyboard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lisan.keyboard.core.Prefs
import app.lisan.keyboard.privacy.Vault
import app.lisan.keyboard.translation.LocalTranslator
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun LibraryScreen(group:String,p:Prefs,w:W,edit:EditPrefs,open:(String)->Unit) {
    val ctx=LocalContext.current
    val vault=remember{Vault.get(ctx)}
    val scope=rememberCoroutineScope()
    var items by remember{mutableStateOf(emptyList<Pair<String,JSONObject>>())}
    var q by remember{mutableStateOf("")}
    var value by remember{mutableStateOf("")}
    var category by remember{mutableStateOf("")}
    var ignore by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<String?>(null)}
    var confirm by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    val actual=if(group=="emoji")listOf("emojiFav","emojiRecent") else listOf(group)
    suspend fun refresh(){items=withContext(Dispatchers.IO){actual.flatMap{a->vault.items(a).map{a to it}}}}
    fun modify(a:String,rule:(List<JSONObject>)->List<JSONObject>){scope.launch{
        val ok=withContext(Dispatchers.IO){runCatching{vault.update(a,rule)}.isSuccess}
        if(!ok)message=w("Could not update the local library.","تعذر تحديث المكتبة المحلية.")
        refresh()
    }}
    LaunchedEffect(group){refresh()}
    val title=when(group){
        "clipboard"->w("Clipboard library","مكتبة الحافظة");"words"->w("Personal dictionary","القاموس الشخصي")
        "history"->w("Translation history","سجل الترجمة");else->w("Emoji & symbols","الإيموجي والرموز")
    }
    Page(w("Encrypted library","مكتبة مشفّرة"),title,if(group=="clipboard")
        w("Only the phrases you explicitly save appear here. Android’s clipboard is not monitored.",
            "تظهر العبارات التي تحفظها صراحةً فقط. لا يراقب لسان حافظة أندرويد.") else "")
    Tile {
        when(group){
            "clipboard"->Toggle(w("Enable clipboard tool on keyboard","تفعيل أداة الحافظة في الكيبورد"),p.clipboard){n->edit{it.copy(clipboard=n)}}
            "history"->Toggle(w("Save future translations","حفظ الترجمات التالية"),p.saveHistory){n->edit{it.copy(saveHistory=n)}}
            "emoji"->Toggle(w("Remember recently used emoji","تذكّر الرموز المستخدمة مؤخراً"),p.emojiHistory){n->edit{it.copy(emojiHistory=n)}}
        }
        Field(q,{q=it.take(100)},w("Search saved items","البحث في المحفوظات"))
        TextButton(onClick={confirm=true},enabled=items.isNotEmpty()){Text(w("Clear this library","مسح هذه المكتبة"))}
        if(message.isNotBlank())Help(message)
    }
    if(group in listOf("clipboard","words"))Tile {
        Sub(if(editing==null)w("Add item","إضافة عنصر") else w("Edit item","تعديل العنصر"))
        Field(value,{value=it.take(if(group=="words")50 else 5000)},if(group=="words")w("Word","الكلمة")else w("Saved phrase","عبارة محفوظة"),if(group=="words")1 else 2)
        if(group=="clipboard")Field(category,{category=it.take(30)},w("Category (optional)","التصنيف (اختياري)"))
        if(group=="words")Toggle(w("Ignore this word","تجاهل هذه الكلمة"),ignore){ignore=it}
        Button(enabled=value.isNotBlank() && (group!="words" || value.trim().none{it.isWhitespace()}),onClick={
            val text=value.trim(); val old=editing
            val keepPin=items.firstOrNull{it.second.optString("text")==old}?.second?.optBoolean("pin") ?: false
            val obj=JSONObject().put("text",text).put("category",category.trim()).put("ignore",ignore).put("pin",keepPin)
            modify(group){existing->listOf(obj)+existing.filter{it.optString("text")!=text && it.optString("text")!=old}}
            value="";category="";ignore=false;editing=null
        }){Text(w("Save","حفظ"))}
        if(editing!=null)TextButton(onClick={value="";category="";ignore=false;editing=null}){Text(w("Cancel edit","إلغاء التعديل"))}
    }
    val filtered=items.sortedByDescending{it.second.optBoolean("pin")}.filter{(_,item)->
        listOf(item.optString("text"),item.optString("out"),item.optString("category")).any{it.contains(q,true)}
    }
    if(filtered.isEmpty())Help(w("No matching saved items.","لا توجد عناصر محفوظة مطابقة."))
    filtered.forEach {(a,item)->
        val txt=item.optString("text")
        val out=item.optString("out")
        Tile {
            val label=when{
                a=="emojiFav"->w("Favorite","مفضلة");a=="emojiRecent"->w("Recent","مستخدم مؤخراً")
                group=="history"->"${item.optString("from")} → ${item.optString("to")}"
                item.optBoolean("ignore")->w("Ignored word","كلمة متجاهلة")
                else->item.optString("category")
            }
            if(label.isNotBlank())Help(label)
            SelectionText(txt.take(300))
            if(out.isNotEmpty()){ SelectionText(out.take(500)); GoogleTranslationBadge(w) }
            Row(horizontalArrangement=Arrangement.spacedBy(1.dp)){
                if(group!="words")TextButton(onClick={ctx.copyText("Lisan",out.ifEmpty{txt})}){Text(w("Copy","نسخ"))}
                if(group in listOf("clipboard","words"))TextButton(onClick={editing=txt;value=txt;ignore=item.optBoolean("ignore");category=item.optString("category")}){Text(w("Edit","تعديل"))}
                if(group=="clipboard")TextButton(onClick={
                    modify(a){old->old.map{if(it.optString("text")==txt)JSONObject(it.toString()).put("pin",!it.optBoolean("pin")) else it}}
                }){Text(if(item.optBoolean("pin"))w("Unpin","إلغاء تثبيت")else w("Pin","تثبيت"))}
                TextButton(onClick={modify(a){old->old.filter{it.toString()!=item.toString()}}}){Text(w("Delete","حذف"))}
            }
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(w("Delete saved items?","حذف العناصر المحفوظة؟"))},
        text={Text(w("This cannot be undone.","لا يمكن التراجع عن الحذف."))},
        confirmButton={TextButton(onClick={confirm=false;actual.forEach{modify(it){emptyList()}}}){Text(w("Delete","حذف"))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(w("Cancel","إلغاء"))}})
}

@Composable internal fun PrivacyScreen(p:Prefs,w:W,edit:EditPrefs,notice:(String)->Unit,open:(String)->Unit){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var erase by remember{mutableStateOf(false)}
    Page(w("Privacy by default","خصوصية افتراضية"),w("Privacy & security","الخصوصية والأمان"))
    Tile {
        Sub(w("Text and keyboard","النص ولوحة المفاتيح"))
        Help(w("Lisan does not store a keystroke log. Password fields and editors asking for no personalized learning disable reading and writing-assistance tools.",
            "لا يسجل لسان ضغطات المفاتيح. يعطّل أدوات القراءة والمساعدة في حقول كلمات المرور والمحررات التي تمنع التعلم الشخصي."))
        Sub(w("Voice and translation","الصوت والترجمة"))
        Help(w("Lisan does not save audio. Whisper runs locally. The optional Android provider may use its servers. The standalone translator sends text to your configured server only when you review and confirm that request.",
            "لا يحفظ لسان التسجيلات. يعمل Whisper محلياً. قد يستخدم محرك أندرويد الاختياري خوادمه. لا ترسل شاشة الترجمة المستقلة نصاً إلى خادمك إلا بعد معاينة الطلب وتأكيده."))
        Sub(w("Clipboard, history and backups","الحافظة والسجل والنسخ"))
        Help(w("The clipboard is read after a user action only. Saved phrases, dictionary, history and emoji lists use Android Keystore-backed encryption. JSON exports contain settings only.",
            "تُقرأ الحافظة عند إجراء صريح فقط. تُشفّر العبارات والقاموس والسجل وقوائم الإيموجي بمفتاح Android Keystore. لا يتضمن تصدير JSON سوى الإعدادات."))
        Help(w("There is no Lisan account, advertising SDK, telemetry endpoint or cloud writing assistant in this build.",
            "لا تتضمن هذه النسخة حساباً أو SDK للإعلانات أو نقطة قياس عن بُعد خاصة بلسان أو مساعد كتابة سحابياً."))
    }
    Action(w("Remove downloaded models","حذف النماذج المنزّلة")){open("models")}
    Action(w("Manage speech models","إدارة نماذج الصوت")){open("voice")}
    Action(w("Erase Lisan app data","مسح بيانات لسان المحلية"),w("Preferences and encrypted library; models are managed separately","التفضيلات والمكتبة المشفرة. تُدار النماذج بشكل مستقل.")){erase=true}
    if(erase)AlertDialog(onDismissRequest={erase=false},title={Text(w("Erase local data?","مسح البيانات المحلية؟"))},
        text={Text(w("Clears Lisan’s encrypted library and resets preferences. Android’s shared clipboard and installed models are not modified.",
            "يمسح مكتبة لسان المشفرة ويستعيد الإعدادات الافتراضية. لا يغيّر حافظة أندرويد المشتركة أو النماذج المثبّتة."))},
        confirmButton={TextButton(onClick={erase=false;scope.launch{
            val ok=withContext(Dispatchers.IO){runCatching{Vault.get(ctx).clear()}.isSuccess}
            if(ok){edit{Prefs()};notice(w("Local library erased; preferences reset.","مُسحت المكتبة المحلية واستُعيدت الإعدادات."))}
            else notice(w("Could not erase the local library.","تعذر مسح المكتبة المحلية."))
        }}){Text(w("Erase","مسح"))}},dismissButton={TextButton(onClick={erase=false}){Text(w("Cancel","إلغاء"))}})
}

@Composable internal fun BackupScreen(p:Prefs,w:W,edit:EditPrefs,notice:(String)->Unit){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var kind by remember{mutableStateOf("settings")}
    var reset by remember{mutableStateOf(false)}
    var export by remember{mutableStateOf("")}
    var pending by remember{mutableStateOf<Pair<String,JSONObject>?>(null)}
    val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        val text=export
        if(uri!=null)scope.launch{
            val ok=withContext(Dispatchers.IO){runCatching{ctx.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(text.toByteArray())}}.isSuccess}
            notice(if(ok)w("Export saved.","حُفظ ملف التصدير.")else w("Export failed.","فشل التصدير."))
        }
    }
    val loader=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val chosen=kind
        if(uri!=null)scope.launch{
            val data=withContext(Dispatchers.IO){runCatching{
                ctx.contentResolver.openInputStream(uri)!!.buffered().use{stream->
                    val buffer=ByteArray(4096)
                    val output=java.io.ByteArrayOutputStream()
                    while (true) {
                        val n=stream.read(buffer)
                        if(n<0)break
                        require(output.size()+n<=65_536)
                        output.write(buffer,0,n)
                    }
                    val wrapper=JSONObject(output.toByteArray().toString(Charsets.UTF_8))
                    require(wrapper.getInt("schema")==1 && wrapper.getString("kind")==chosen)
                    wrapper.getJSONObject("data")
                }
            }.getOrNull()}
            if(data==null)notice(w("Invalid or unsupported settings file.","ملف غير صالح أو غير مدعوم."))
            else pending=chosen to data
        }
    }
    Page(w("Portability","نقل الإعدادات"),w("Export & restore","التصدير والاستعادة"),
        w("Exports contain preferences only. They never include clipboard text, translations, recordings or the personal dictionary.",
            "تضم ملفات التصدير التفضيلات فقط. لا تتضمن نصوص الحافظة أو الترجمات أو التسجيلات أو القاموس الشخصي."))
    Tile {
        Choose(w("File type","نوع الملف"),kind,listOf("settings" to w("All preferences","جميع التفضيلات"),"theme" to w("Theme only","السمة فقط"))){kind=it}
        Button(onClick={
            export=JSONObject().put("kind",kind).put("schema",1).put("data",if(kind=="theme")p.themeJson()else p.copy(cloudEndpoint="").json()).toString(2)
            saver.launch("lisan-$kind.json")
        }){Text(w("Export JSON","تصدير JSON"))}
        OutlinedButton(onClick={loader.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text(w("Import JSON","استيراد JSON"))}
        TextButton(onClick={reset=true}){Text(w("Restore default preferences","استعادة التفضيلات الافتراضية"))}
    }
    pending?.let{(which,data)->AlertDialog(onDismissRequest={pending=null},title={Text(w("Apply imported settings?","تطبيق الإعدادات المستوردة؟"))},
        text={Text(w("Existing settings in this category will be replaced. Restored voice mode defaults to local.","ستُستبدل الإعدادات الحالية في هذا القسم. يبقى الوضع الصوتي محلياً بعد استعادة التفضيلات."))},
        confirmButton={TextButton(onClick={pending=null;edit{if(which=="theme")it.importTheme(data) else Prefs.from(data).copy(voiceMode="local",cloudEndpoint="")}}){Text(w("Apply","تطبيق"))}},
        dismissButton={TextButton(onClick={pending=null}){Text(w("Cancel","إلغاء"))}})}
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text(w("Restore defaults?","استعادة الإعدادات الافتراضية؟"))},
        confirmButton={TextButton(onClick={reset=false;edit{Prefs()}}){Text(w("Restore","استعادة"))}},
        dismissButton={TextButton(onClick={reset=false}){Text(w("Cancel","إلغاء"))}})
}

@Composable internal fun Support(w:W) {
    val ctx=LocalContext.current
    var kind by remember{mutableStateOf("Keyboard")}
    var details by remember{mutableStateOf("")}
    var device by remember{mutableStateOf(false)}
    var review by remember{mutableStateOf(false)}
    var noMail by remember{mutableStateOf(false)}
    var crashLog by remember{mutableStateOf(CrashLog.last(ctx))}
    var attachCrash by remember{mutableStateOf(false)}
    val kinds=listOf("Keyboard" to w("Keyboard","لوحة المفاتيح"),"Voice" to w("Voice","الصوت"),
        "Translation" to w("Translation","الترجمة"),"Design" to w("Design","التصميم"),"Other" to w("Other","أخرى"))
    val report="Lisan ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nCategory: $kind"+
        (if(device)"\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nModel: ${Build.MANUFACTURER} ${Build.MODEL}" else "")+"\n\n${details.trim()}"+
        (if(attachCrash&&crashLog.isNotBlank())"\n\n--- Last crash (stack only) ---\n$crashLog" else "")
    Page(w("Talk to us","تواصل معنا"),w("Report a problem","تواصل مع المطور"),"nidalwatfa99@gmail.com")
    Tile {
        Choose(w("Issue type","نوع المشكلة"),kind,kinds){kind=it}
        Field(details,{details=it.take(2500)},w("Describe the problem (avoid private text)","صف المشكلة دون نصوص خاصة"),3)
        Toggle(w("Include Android version and device model","إرفاق إصدار أندرويد وطراز الجهاز"),device){device=it}
        if(crashLog.isNotBlank()){
            Toggle(w("Attach last crash trace (class names and lines only)","إرفاق أثر آخر عطل (أسماء الأصناف والأسطر فقط)"),attachCrash){attachCrash=it}
            TextButton(onClick={CrashLog.clear(ctx);crashLog="";attachCrash=false}){Text(w("Delete stored crash trace","حذف أثر العطل المخزّن"))}
        }
        Help(w("Only the text you enter and the selected diagnostic details are included. No clipboard, editor text or raw logs are attached.",
            "يتضمن التقرير ما تكتبه وتفاصيل التشخيص المختارة فقط. لا تُرفق الحافظة أو نصوص المحرر أو السجلات الخام."))
        Button(onClick={review=true;noMail=false}){Text(w("Review report","مراجعة التقرير"))}
        if(noMail)Help(w("No email app found. You can copy the report.","لا يوجد تطبيق بريد. يمكنك نسخ التقرير."))
    }
    if(review) AlertDialog(onDismissRequest={review=false},title={Text(w("Review before sharing","راجع قبل المشاركة"))},
        text={Column(Modifier.heightIn(max=295.dp)){SelectionText(report.take(1000));if(report.length>1000)Help(w("Email or copy includes your complete description.","يتضمن البريد أو النسخ الوصف كاملاً."))}},
        confirmButton={TextButton(onClick={
            try {
                val uri=Uri.parse("mailto:nidalwatfa99@gmail.com?subject=${Uri.encode("Lisan report: $kind")}&body=${Uri.encode(report)}")
                ctx.startActivity(Intent(Intent.ACTION_SENDTO,uri));review=false
            } catch(_:ActivityNotFoundException){noMail=true;review=false;ctx.copyText("Lisan report",report)}
        }){Text(w("Open email","فتح البريد"))}},
        dismissButton={TextButton(onClick={ctx.copyText("Lisan report",report);review=false}){Text(w("Copy report","نسخ التقرير"))}})
}

@Composable internal fun About(p:Prefs,w:W,edit:EditPrefs,share:()->Unit){
    var url by remember(p.storeUrl){mutableStateOf(p.storeUrl)}
    Page(w("Lisan studio","استوديو لسان"),w("About Lisan","حول لسان"),"Android · ${BuildConfig.VERSION_NAME} · app.lisan.keyboard")
    Tile {
        Sub(w("Share your installation link","شارك رابط التطبيق"))
        Help(w("No store URL is assumed. Set your real HTTPS URL when it is published.",
            "لا يفترض لسان رابط متجر. عيّن رابط HTTPS الفعلي عند النشر."))
        Field(url,{url=it.take(600)},w("HTTPS sharing URL","رابط المشاركة HTTPS"))
        val valid=url.isBlank() || (url.startsWith("https://") && !Uri.parse(url).host.isNullOrBlank())
        Button(enabled=valid,onClick={edit{it.copy(storeUrl=url)}}){Text(w("Save link","حفظ الرابط"))}
        OutlinedButton(onClick=share){Text(w("Android Sharesheet","قائمة مشاركة أندرويد"))}
    }
}
