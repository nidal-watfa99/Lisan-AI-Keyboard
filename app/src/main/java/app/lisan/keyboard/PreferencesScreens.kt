package app.lisan.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.lisan.keyboard.core.*
import app.lisan.keyboard.keyboard.toolName
import app.lisan.keyboard.translation.displayLanguage
import app.lisan.keyboard.voice.*
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@Composable internal fun Languages(p:Prefs,w:W,edit:EditPrefs) {
    val ctx=LocalContext.current
    val ar=isArabic(p,ctx)
    var q by remember { mutableStateOf("") }
    Page(w("Language manager","مدير اللغات"),w("Languages & layouts","اللغات والتخطيطات"),
        w("Keyboard layouts are bundled. Voice, translation and interface languages are independent.","تخطيطات المفاتيح مضمّنة. لغات الصوت والترجمة والواجهة مستقلة عنها."))
    Tile {
        Choose(w("Interface","الواجهة"),p.ui,listOf("device" to displayLanguage("device",ar),"ar" to "العربية","en" to "English")) { code -> edit{it.copy(ui=code)} }
    }
    Tile {
        Choose(w("Default layout","التخطيط الافتراضي"),p.defaultLang,KeyboardCore.layouts.filter{it.tag in p.languages}.map { it.tag to it.name }) { tag -> edit{it.copy(defaultLang=tag)} }
        Field(q,{q=it.take(60)},w("Search layouts","بحث عن تخطيط"))
        KeyboardCore.layouts.filter{it.name.contains(q,true)||displayLanguage(it.tag,ar).contains(q,true)||it.tag.contains(q,true)}.forEach { l ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Sub(l.name); Help(displayLanguage(l.tag,ar)+" · "+w("Built in","مضمّن")) }
                Switch(l.tag in p.languages,{ enable -> edit { cur -> cur.copy(languages=if(enable)cur.languages+l.tag else cur.languages-l.tag) } },
                    enabled=p.languages.size>1 || l.tag !in p.languages)
            }
        }
    }
    PreviewBoard(p,w)
}

@Composable internal fun KeyboardOptionsScreen(p:Prefs,w:W,edit:EditPrefs) {
    Page(w("Typing engine","محرك الكتابة"),w("Keyboard & prediction","لوحة المفاتيح والتنبؤ"))
    Tile {
        Sub(w("Typing & feedback","الكتابة والتفاعل"))
        Toggle(w("Keyboard haptics","اهتزاز المفاتيح"),p.haptics){n->edit{it.copy(haptics=n)}}
        Toggle(w("Automatic capitalization","تكبير الأحرف تلقائياً"),p.autoCaps){n->edit{it.copy(autoCaps=n)}}
        Toggle(w("Arabic and English suggestions","اقتراحات العربية والإنجليزية"),p.suggestions){n->edit{it.copy(suggestions=n)}}
        Toggle(w("Conservative English autocorrection","تصحيح إنجليزي محدود"),p.correction){n->edit{it.copy(correction=n)}}
        Toggle(w("Conservative Arabic autocorrection","تصحيح عربي محدود"),p.arabicCorrection){n->edit{it.copy(arabicCorrection=n)}}
        Help(w("Only a short list of known typos is replaced. Protected and vocalized Arabic words are untouched. Press Delete immediately to undo.",
            "يقتصر التصحيح على قائمة قصيرة من الأخطاء المعروفة. لا تُغيّر الكلمات العربية المشكّلة أو المحمية. اضغط الحذف فوراً للتراجع."))
    }
    Tile {
        Sub(w("Rows & order","الصفوف والترتيب"))
        Toggle(w("Number row","صف الأرقام"),p.numbers){n->edit{it.copy(numbers=n)}}
        Toggle(w("Use ١٢٣ in Arabic","استخدم ١٢٣ في العربية"),p.eastern){n->edit{it.copy(eastern=n)}}
        Toggle(w("Arabic diacritics row","صف التشكيل العربي"),p.marks){n->edit{it.copy(marks=n)}}
        p.rowOrder.forEachIndexed { i,row ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(w("Letter row ${row+1}","صف الحروف ${row+1}"),Modifier.weight(1f))
                if(i>0) TextButton(onClick={edit { cur ->
                    val moved=cur.rowOrder.toMutableList(); val picked=moved.removeAt(i); moved.add(i-1,picked); cur.copy(rowOrder=moved)
                }}){Text(w("Move up","تحريك لأعلى"))}
            }
        }
        TextButton(onClick={edit{it.copy(rowOrder=listOf(0,1,2))}}){Text(w("Reset row order","استعادة ترتيب الصفوف"))}
    }
    PreviewBoard(p,w)
}

@Composable private fun ThemeLibrary(p:Prefs,w:W,edit:EditPrefs) {
    var name by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<String?>(null) }
    var replace by remember { mutableStateOf<String?>(null) }
    fun save(key:String) { edit { it.copy(savedThemes=it.savedThemes+(key to it.themeJson().toString())) }; name="" }
    Tile {
        Sub(w("Saved themes","السمات المحفوظة"))
        Help(w("Save up to 12 named designs on this device. Applying a design does not change voice or language settings.",
            "احفظ حتى 12 تصميماً باسم على الجهاز. لا يغيّر تطبيق التصميم إعدادات الصوت أو اللغة."))
        Field(name,{name=it.take(40)},w("Name this design","اسم التصميم"))
        Button(enabled=name.isNotBlank() && (p.savedThemes.size<12 || name.trim() in p.savedThemes),onClick={
            val key=name.trim(); if(key in p.savedThemes)replace=key else save(key)
        }){Text(w("Save current design","حفظ التصميم الحالي"))}
        p.savedThemes.toSortedMap().forEach { (key,value) ->
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(key,Modifier.weight(1f))
                TextButton(onClick={edit{it.importTheme(org.json.JSONObject(value))}}){Text(w("Apply","تطبيق"))}
                TextButton(onClick={remove=key}){Text(w("Delete","حذف"))}
            }
        }
    }
    (remove ?: replace)?.let { key ->
        val deleting=remove!=null
        AlertDialog(onDismissRequest={remove=null;replace=null},
            title={Text(if(deleting)w("Delete saved design?","حذف التصميم المحفوظ؟") else w("Replace saved design?","استبدال التصميم المحفوظ؟"))},
            text={Text(key)},
            confirmButton={TextButton(onClick={if(deleting)edit{it.copy(savedThemes=it.savedThemes-key)} else save(key);remove=null;replace=null}){Text(w("Confirm","تأكيد"))}},
            dismissButton={TextButton(onClick={remove=null;replace=null}){Text(w("Cancel","إلغاء"))}})
    }
}

@Composable private fun Knob(title:String,value:Int,min:Int,max:Int,change:(Int)->Unit) {
    var slider by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Help("$title · ${slider.roundToInt()}")
    Slider(value=slider,onValueChange={slider=it; change(it.roundToInt())},valueRange=min.toFloat()..max.toFloat())
}

@Composable internal fun Customize(p:Prefs,w:W,edit:EditPrefs) {
    Page(w("Design controls","أدوات التصميم"),w("Customize","تخصيص المظهر"),w("This preview uses the same layout engine as the system IME.","تستخدم هذه المعاينة محرّك التخطيط نفسه المستخدم في لوحة النظام."))
    PreviewBoard(p,w)
    Tile {
        Knob(w("Key height (dp)","ارتفاع المفتاح"),p.height,38,60){n->edit{it.copy(height=n)}}
        Knob(w("Width (%)","العرض (%)"),p.width,80,100){n->edit{it.copy(width=n)}}
        Knob(w("Key spacing","تباعد الأزرار"),p.gap,0,4){n->edit{it.copy(gap=n)}}
        Knob(w("Corner radius","استدارة الزوايا"),p.radius,0,24){n->edit{it.copy(radius=n)}}
        Knob(w("Border thickness","سماكة الحدود"),p.border,0,3){n->edit{it.copy(border=n)}}
        Knob(w("Background opacity (%)","عتامة الخلفية (%)"),p.alpha,50,100){n->edit{it.copy(alpha=n)}}
        Knob(w("Toolbar height","ارتفاع شريط الأدوات"),p.toolbarSize,32,50){n->edit{it.copy(toolbarSize=n)}}
        Knob(w("Symbol size","حجم الرموز"),p.symbolSize,10,23){n->edit{it.copy(symbolSize=n)}}
        Toggle(w("Key shadows","ظلال الأزرار"),p.shadow){n->edit{it.copy(shadow=n)}}
        p.rowHeights.forEachIndexed { idx,h -> Knob(w("Row ${idx+1} height adjustment","ضبط ارتفاع الصف ${idx+1}"),h,-8,8){n ->
            edit{cur -> cur.copy(rowHeights=cur.rowHeights.mapIndexed { i,v -> if(i==idx)n else v }) }
        } }
    }
    Tile {
        Sub(w("Personal colors","ألوان شخصية"))
        Help(w("Use #RRGGBB. Setting a color activates Personal Studio. Microphone and translation colors change their glyphs.",
            "استخدم #RRGGBB. يفعّل اختيار اللون السمة الشخصية. يغيّر لونا الصوت والترجمة لون رمزيهما."))
        val names=listOf(w("Background","الخلفية"),w("Letters","الحروف"),w("Numbers","الأرقام"),w("Symbols","الرموز"),
            w("Special keys","المفاتيح الخاصة"),w("Microphone","الميكروفون"),w("Translation","الترجمة"))
        CustomColors.forEachIndexed { i,key ->
            var color by remember(p.colors[key]){mutableStateOf(p.colors[key].orEmpty())}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)){Field(color,{color=it.take(7)},names[i])}
                TextButton(onClick={edit{it.copy(theme="custom",colors=it.colors+(key to color))}},enabled=Regex("#[0-9a-fA-F]{6}").matches(color)){Text(w("Set","تعيين"))}
            }
        }
        TextButton(onClick={edit{it.copy(colors=emptyMap())}}){Text(w("Reset colors","استعادة الألوان"))}
    }
    ThemeLibrary(p,w,edit)
    PreviewBoard(p,w)
}

@Composable internal fun Fonts(p:Prefs,w:W,edit:EditPrefs) {
    val ctx=LocalContext.current
    Page(w("Typography","الخطوط"),w("Fonts & sizes","الخطوط والأحجام"),
        w("Fonts change Lisan’s keys, not the font used by the app that receives your text.","تغيّر الخطوط مظهر مفاتيح لسان، وليس خط التطبيق الذي يستقبل النص."))
    PreviewBoard(p,w)
    Tile {
        Knob(w("Letter size","حجم الحروف"),p.fontSize,12,28){n->edit{it.copy(fontSize=n)}}
        Prefs.fontNames.forEach { name ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=p.font==name,onClick={edit{it.copy(font=name)}})
                Column(Modifier.weight(1f)) {
                    Sub(when(name) { "system"->w("System default","خط النظام"); "serif"->"Serif"; "mono"->"Monospace"; else->name.replaceFirstChar { it.uppercase() } })
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory={android.widget.TextView(it).apply {setTextColor(0xffdcdef7.toInt()); text="لِسَانٌ  ·  Typography"; textSize=22f }},
                        update={it.typeface=app.lisan.keyboard.keyboard.BoardStyle.type(ctx,name)}
                    )
                }
            }
        }
        Help(w("Cairo, Tajawal and Amiri are bundled. Their license files are included with the source.","خطوط القاهرة وتجوال وأميري مضمّنة. تراخيصها موجودة مع المصدر."))
    }
}

@Composable internal fun ToolSettings(p:Prefs,w:W,edit:EditPrefs) {
    val ar=isArabic(p,LocalContext.current)
    Page(w("Quick actions","إجراءات سريعة"),w("Toolbar","شريط الأدوات"))
    PreviewBoard(p,w)
    Tile {
        Toggle(w("Show toolbar","إظهار شريط الأدوات"),p.toolbar){n->edit{it.copy(toolbar=n)}}
        (p.tools + Tools.filterNot{it in p.tools}).forEach { key ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)){
                    Toggle(toolName(key,ar),key in p.tools){b->edit{it.copy(tools=if(b)it.tools+key else it.tools-key)}}
                }
                if(p.tools.indexOf(key)>0) TextButton(onClick={edit { cur ->
                    val order=cur.tools.toMutableList(); val index=order.indexOf(key)
                    if(index>0){order.removeAt(index);order.add(index-1,key)}; cur.copy(tools=order)
                }}){Text("↑")}
            }
        }
    }
}

@Composable internal fun VoiceSettings(p:Prefs,w:W,edit:EditPrefs) {
    val ctx=LocalContext.current
    val ar=isArabic(p,ctx)
    val engine=remember { VoiceRouter(ctx) }
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ granted=it }
    var confirm by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var custom by remember(p.voiceLang){mutableStateOf(if(p.voiceLang=="keyboard")"" else p.voiceLang)}
    DisposableEffect(engine){
        val lifecycle=(ctx as? LifecycleOwner)?.lifecycle
        val observer=object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                granted=ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
            }
            override fun onPause(owner: LifecycleOwner) { engine.cancel(); listening=false; processing=false; status="" }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); engine.cancel() }
    }
    Page(w("Dictation","الإملاء"),w("Voice & microphone","الصوت والميكروفون"),
        w("Lisan doesn’t keep an audio recording. Use local mode for speech processed on this device.","لا يحتفظ لسان بتسجيل صوتي. اختر الوضع المحلي لمعالجة الصوت على هذا الجهاز."))
    Tile {
        Choose(w("Provider","المحرك"),p.voiceMode,listOf("local" to w("Automatic offline","اختيار محلي تلقائي"),
            "bundled" to w("Whisper offline model","نموذج Whisper المحلي"),
            "system" to w("Android provider (may use network)","محرك أندرويد (قد يستخدم الشبكة)"))) { mode ->
            if(mode=="system" && p.voiceMode!="system") confirm=true else edit{it.copy(voiceMode=mode)}
        }
        Help(if(engine.android.available(true))w("Automatic offline uses Whisper if installed, otherwise Android's on-device recognizer.","يستخدم الوضع المحلي التلقائي Whisper إذا ثُبّت، وإلا يستخدم محرك أندرويد المحلي.")
             else w("Android reports no local engine. Install the Whisper model to dictate without a network connection.","لم يُرصد محرك أندرويد محلي. ثبّت نموذج Whisper للإملاء دون اتصال."))
        val tags=listOf("ar","ar-SY","ar-SA","en-US","en-GB","fr-FR","de-DE","nl-NL","tr-TR").let { if(p.voiceLang !in it && p.voiceLang!="keyboard")it+p.voiceLang else it }
        Choose(w("Recognition language","لغة التعرف"),p.voiceLang,listOf("keyboard" to w("Current keyboard","الكيبورد الحالي"))+tags.map{it to displayLanguage(it,ar)}) { tag -> edit {it.copy(voiceLang=tag)} }
        Field(custom,{custom=it.take(35)},w("Custom language tag (e.g. ar-EG)","رمز لغة مخصص (مثل ar-EG)"))
        TextButton(onClick={edit{it.copy(voiceLang=custom)}},enabled=Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}").matches(custom)){Text(w("Use language tag","استخدام رمز اللغة"))}
        Toggle(w("Detect language (Whisper or compatible Android 14+ provider)","كشف اللغة (Whisper أو محرك متوافق مع أندرويد 14 فأحدث)"),p.voiceAuto){n->edit{it.copy(voiceAuto=n)}}
        Toggle(w("Continue after each recognized phrase","متابعة الاستماع بعد كل عبارة"),p.voiceRepeat){n->edit{it.copy(voiceRepeat=n)}}
        Help(w("Language, punctuation and switching depend on the recognizer and installed speech models.","تعتمد اللغة وعلامات الترقيم والتبديل على المحرك ونماذج الصوت المثبّتة."))
    }
    if(android.os.Build.VERSION.SDK_INT>=33 && engine.android.available(true)) {
        val tag=if(p.voiceLang=="keyboard")p.defaultLang else p.voiceLang
        AndroidModelSettings(engine.android,tag,w)
    }
    OfflineModelSettings(w)
    Tile {
        Sub(w("Microphone test","اختبار الميكروفون"))
        if(!granted) {
            Button(onClick={permission.launch(Manifest.permission.RECORD_AUDIO)}){Text(w("Allow microphone","السماح بالميكروفون"))}
            TextButton(onClick={ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${ctx.packageName}")))}){Text(w("Open Android permissions","فتح أذونات أندرويد"))}
        } else {
            Button(enabled=!processing,onClick={
                if(listening){ processing=true; engine.stop() } else {
                listening=true
                val tag=if(p.voiceLang=="keyboard") p.defaultLang else p.voiceLang
                engine.start(p.voiceMode,VoiceConfig(tag,p.voiceMode!="system",p.voiceAuto)){ state,value ->
                    status=voiceMessage(state,value,ar)
                    if(state=="processing")processing=true
                    if(voiceTerminal(state)) { listening=false; processing=false; engine.cancel() }
                    if(state=="done")transcript=(transcript+KeyboardCore.speechJoin(transcript,value)).take(3000)
                }
                }
            }) {Text(if(listening)w("Finish phrase","إنهاء العبارة") else w("Test speech recognition","اختبار التعرف الصوتي"))}
            if(listening) TextButton(onClick={engine.cancel(); listening=false; processing=false; status=""}){Text(w("Discard recording","إلغاء التسجيل"))}
        }
        if(status.isNotBlank()) Help(status)
        Field(transcript,{transcript=it.take(3000)},w("Editable transcript","النص الناتج القابل للتعديل"),2)
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(w("Use Android speech provider?","استخدام محرك صوت أندرويد؟"))},
        text={Text(w("When you tap the microphone, this provider may send audio to its servers. Lisan does not control the provider’s retention.",
            "عند الضغط على الميكروفون قد يرسل هذا المحرك الصوت إلى خوادمه. لا يتحكم لسان في سياسة الاحتفاظ الخاصة بالمزوّد."))},
        confirmButton={TextButton(onClick={confirm=false;edit{it.copy(voiceMode="system")}}){Text(w("Allow system provider","السماح بمحرك النظام"))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(w("Keep on-device mode","المتابعة محلياً"))}})
}

@Composable private fun AndroidModelSettings(engine:AndroidVoice,tag:String,w:W) {
    var state by remember(tag){mutableStateOf("")}
    var ask by remember(tag){mutableStateOf(false)}
    var busy by remember(tag){mutableStateOf(false)}
    DisposableEffect(engine,tag) { onDispose { engine.closeModelOperation() } }
    LaunchedEffect(busy,tag) {
        if(busy) {
            delay(30_000)
            engine.closeModelOperation()
            state="requested";busy=false
        }
    }
    fun request(download:Boolean) {
        busy=true;state=""
        engine.modelOperation(VoiceConfig(tag,true,false),download){
            state=it; busy=it.startsWith("progress:")
        }
    }
    Tile {
        Sub(w("Android offline language · $tag","لغة أندرويد المحلية · $tag"))
        TextButton(enabled=!busy,onClick={request(false)}){Text(w("Check availability","تحقق من التوفر"))}
        if(state=="downloadable") TextButton(onClick={ask=true}){Text(w("Request model download","طلب تنزيل النموذج"))}
        if(busy) TextButton(onClick={engine.closeModelOperation();busy=false;state="requested"}){Text(w("Stop waiting","إيقاف الانتظار"))}
        if(state.isNotEmpty()) Help(when(state) {
            "installed"->w("Provider reports the model installed.","يفيد المحرك بأن النموذج مثبت.")
            "downloadable"->w("Provider reports a downloadable model.","يفيد المحرك بتوفر نموذج للتنزيل.")
            "scheduled","requested"->w("Check again later. The provider may continue independently.","تحقق مرة أخرى لاحقاً. قد يتابع المحرك العملية بصورة مستقلة.")
            "unknown"->w("The provider did not confirm availability for this tag.","لم يؤكد المحرك توفر نموذج لهذا الرمز.")
            else->w("Provider progress: ${state.substringAfter(':')}%","تقدم التنزيل لدى المحرك: ${state.substringAfter(':')}%")
        })
    }
    if(ask) AlertDialog(onDismissRequest={ask=false},title={Text(w("Ask Android to download this model?","مطالبة أندرويد بتنزيل هذا النموذج؟"))},
        text={Text(w("The speech provider manages the download and may use your current network.","يدير محرك الصوت التنزيل وقد يستخدم الشبكة الحالية."))},
        confirmButton={TextButton(onClick={ask=false;request(true)}){Text(w("Download","تنزيل"))}},
        dismissButton={TextButton(onClick={ask=false}){Text(w("Cancel","إلغاء"))}})
}

@Composable private fun OfflineModelSettings(w:W) {
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var running by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableLongStateOf(SpeechModel.progress(ctx)) }
    var installed by remember { mutableStateOf(SpeechModel.installed(ctx)) }
    var message by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    fun operate(operation:suspend ((Long)->Unit)->Unit) {
        if(running!=null)return
        message=""
        running=scope.launch(start=CoroutineStart.LAZY) {
            try {
                operation { progress=it }
            } catch(e:CancellationException){ throw e }
            catch(_:Exception){message=w("Couldn’t complete the operation. Check the connection, file or available storage.","تعذرت العملية. تحقق من الاتصال أو الملف أو مساحة التخزين.")}
            finally { installed=SpeechModel.installed(ctx); progress=SpeechModel.progress(ctx);running=null }
        }
        running?.start()
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) operate{SpeechModel.import(ctx,uri,it)}
    }
    Tile {
        Sub(w("Offline speech model","نموذج الإملاء المحلي"))
        Help(w("Whisper Tiny multilingual · 32.2 MB · Arabic and English. Only the model is downloaded; audio stays on this device. A phrase is limited to 24 seconds.","Whisper Tiny متعدد اللغات · 32.2 ميغابايت · العربية والإنجليزية. يُنزّل النموذج فقط ويبقى الصوت على الجهاز. الحد لكل عبارة 24 ثانية."))
        if(!WhisperNative.available) Help(w("Native engine unavailable on this device.","المحرك المحلي غير متاح على هذا الجهاز."))
        if(running!=null || !installed && progress>0) LinearProgressIndicator(
            progress={progress.toFloat()/SpeechModel.bytes},modifier=Modifier.fillMaxWidth())
        if(running!=null) TextButton(onClick={running?.cancel()}){Text(w("Stop operation","إيقاف العملية"))}
        else if(installed) {
            Help(w("Verified model installed.","النموذج مثبت ومتحقق منه."))
            TextButton(onClick={confirm=true}){Text(w("Remove model","حذف النموذج"))}
        } else {
            Button(enabled=WhisperNative.available,onClick={confirm=true}){
                Text(if(progress>0)w("Resume download","استئناف التنزيل") else w("Download model","تنزيل النموذج"))
            }
            TextButton(enabled=WhisperNative.available,onClick={picker.launch(arrayOf("*/*"))}){Text(w("Import model file","استيراد ملف النموذج"))}
            if(progress>0)TextButton(onClick={operate{SpeechModel.delete(ctx)}}){Text(w("Discard partial download","حذف التنزيل الجزئي"))}
        }
        if(message.isNotBlank())Help(message)
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},
        title={Text(if(installed)w("Remove Whisper model?","حذف نموذج Whisper؟") else w("Download from Hugging Face?","تنزيل من Hugging Face؟"))},
        text={Text(if(installed)w("You can download it again when needed.","يمكن تنزيله مجدداً عند الحاجة.") else
            w("Downloads up to 32.2 MB over the current network. No dictation or editor text is sent.","يُنزّل حتى 32.2 ميغابايت عبر الشبكة الحالية. لا يُرسل أي صوت أو نص من المحرر."))},
        confirmButton={TextButton(onClick={confirm=false;if(installed)operate{SpeechModel.delete(ctx)} else operate{SpeechModel.download(ctx,it)}}){Text(w("Continue","متابعة"))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(w("Cancel","إلغاء"))}})
}
