package app.lisan.keyboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.lisan.keyboard.core.*
import app.lisan.keyboard.keyboard.Board
import app.lisan.keyboard.keyboard.BoardStyle
import app.lisan.keyboard.keyboard.toolName
import app.lisan.keyboard.privacy.Vault
import app.lisan.keyboard.translation.*
import app.lisan.keyboard.voice.AndroidVoice
import kotlinx.coroutines.*
import org.json.JSONObject

private val ink = Color(0xFF101826)
private val mint = Color(0xFF8AEDCF)
private val lavender = Color(0xFFC4B8FF)
private val night = darkColorScheme(primary=mint, onPrimary=Color(0xFF15332C), secondary=lavender,
    background=ink, surface=Color(0xFF182437), surfaceVariant=Color(0xFF25354D),
    onBackground=Color(0xFFF4F7FF), onSurface=Color(0xFFF4F7FF), onSurfaceVariant=Color(0xFFD2DDF2))
private val brandBrush = Brush.linearGradient(listOf(Color(0xFF243951),Color(0xFF28264B),Color(0xFF173D41)))
internal typealias W = (String,String) -> String
internal typealias EditPrefs = ((Prefs) -> Prefs) -> Unit

class MainActivity : ComponentActivity() {
    private val repo by lazy { app.lisan.keyboard.core.Settings.get(this) }
    private var route by mutableStateOf("")
    private var enabled by mutableStateOf(false)
    private var selected by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route = savedInstanceState?.getString("route") ?: intent.getStringExtra("screen").orEmpty()
        enableEdgeToEdge(
            statusBarStyle=androidx.activity.SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle=androidx.activity.SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent {
            val p by repo.flow.collectAsState()
            val ar = isArabic(p,this)
            val w: W = { en,a -> if(ar) a else en }
            val scope = rememberCoroutineScope()
            var notice by remember { mutableStateOf("") }
            val edit: EditPrefs = { change -> scope.launch {
                try { repo.update(change) } catch (_: Exception) { notice=w("Unable to save settings.","تعذر حفظ الإعدادات.") }
            } }
            val homes = listOf("","themes","translate","settings")
            BackHandler(route != "") { route = if(route in homes) "" else "settings" }
            CompositionLocalProvider(LocalLayoutDirection provides if(ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme=night) {
                    Scaffold(containerColor=ink, bottomBar={
                        NavigationBar(containerColor=Color(0xFF172236), windowInsets=NavigationBarDefaults.windowInsets) {
                            val items=listOf(Triple("","⌂",w("Home","الرئيسية")),Triple("themes","◈",w("Themes","السمات")),
                                Triple("translate","A↔ع",w("Translate","الترجمة")),Triple("settings","⚙",w("Settings","الإعدادات")))
                            items.forEach { (id,icon,title) -> NavigationBarItem(selected=route==id || (id=="settings" && route !in homes),
                                onClick={route=id},icon={ Text(icon,fontSize=20.sp,fontWeight=FontWeight.SemiBold) },
                                label={Text(title,maxLines=1,fontSize=11.sp)}) }
                        }
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
                            key(route) {
                                Column(Modifier.widthIn(max=740.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                                    .padding(horizontal=18.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                                    when(route) {
                                        "" -> Home(p,w,enabled,selected,::open,::enableIme,::pickIme,{ shareApp(p,w) })
                                        "themes" -> ThemeGallery(p,w,edit,::open)
                                        "translate" -> TranslationScreen(p,w,edit,::open)
                                        "settings" -> SettingsIndex(w,::open) { shareApp(p,w) }
                                        "languages" -> Languages(p,w,edit)
                                        "keyboard" -> KeyboardOptionsScreen(p,w,edit)
                                        "custom" -> Customize(p,w,edit)
                                        "fonts" -> Fonts(p,w,edit)
                                        "tools" -> ToolSettings(p,w,edit)
                                        "models" -> Models(p,w,edit)
                                        "voice" -> VoiceSettings(p,w,edit)
                                        "clipboard","words","history","emoji" -> LibraryScreen(route,p,w,edit,::open)
                                        "privacy" -> PrivacyScreen(p,w,edit,{notice=it},::open)
                                        "backup" -> BackupScreen(p,w,edit,{notice=it})
                                        "support" -> Support(w)
                                        else -> About(p,w,edit) { shareApp(p,w) }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                    if(notice.isNotBlank()) AlertDialog(onDismissRequest={notice=""},text={Text(notice)},
                        confirmButton={TextButton(onClick={notice=""}){Text(w("OK","حسناً"))}})
                }
            }
        }
    }
    private fun open(name:String) { route=name }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); route=intent.getStringExtra("screen").orEmpty() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("route",route); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); imeState() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) imeState() }
    private fun imeState() {
        val imm = getSystemService(InputMethodManager::class.java)
        val id=ComponentName(this,app.lisan.keyboard.keyboard.LisanIme::class.java)
        enabled=imm.enabledInputMethodList.any { ComponentName(it.packageName,it.serviceName)==id }
        val value=AndroidSettings.Secure.getString(contentResolver,AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
        selected=ComponentName.unflattenFromString(value.orEmpty())==id
    }
    private fun enableIme() { startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) }
    private fun pickIme() { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
    private fun shareApp(p:Prefs,w:W) {
        shareText(w("Lisan AI Keyboard · Type, speak, translate.","لسان · اكتب، تحدث، ترجم.") +
            (if(p.storeUrl.isNotBlank()) "\n${p.storeUrl}" else "\n" + w("An Android keyboard. No store link is published yet.","لوحة مفاتيح أندرويد. لم يُنشر رابط المتجر بعد.")),w("Share Lisan","مشاركة لسان"))
    }
}

internal fun android.content.Context.shareText(text: String, title: String) {
    val intent=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text)
    startActivity(Intent.createChooser(intent,title))
}
internal fun android.content.Context.copyText(label:String,text:String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label,text))
}

@Composable internal fun Page(kicker:String,title:String,body:String="") {
    Text(kicker.uppercase(),color=mint,letterSpacing=2.sp,fontSize=11.sp,fontWeight=FontWeight.Bold)
    Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
    if(body.isNotBlank()) Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun Tile(content:@Composable ColumnScope.()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Color(0xFF2B3C52))) {
        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
    }
}
@Composable internal fun Sub(text:String) { Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface) }
@Composable internal fun Help(text:String) { Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable internal fun Field(value:String,onChange:(String)->Unit,label:String,lines:Int=1,keyboard:KeyboardOptions=KeyboardOptions.Default) {
    OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},minLines=lines,maxLines=if(lines==1) 1 else lines+3,
        shape=RoundedCornerShape(15.dp),keyboardOptions=keyboard,singleLine=lines==1)
}
@Composable internal fun Toggle(title:String,on:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=45.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
        Switch(on,change)
    }
}
@Composable internal fun Action(title:String,caption:String="",go:()->Unit) {
    Surface(onClick=go,color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Color(0xFF2B3C52))) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Sub(title); if(caption.isNotBlank()) Help(caption) }
            Text(if(LocalLayoutDirection.current==LayoutDirection.Rtl) "‹" else "›",color=mint,fontSize=25.sp)
        }
    }
}
@Composable internal fun Choose(label:String,value:String,options:List<Pair<String,String>>,change:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={filter="";open=true},modifier=Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull{it.first==value}?.second ?: value}  ▾",maxLines=2)
        }
        DropdownMenu(open,{open=false},modifier=Modifier.widthIn(min=230.dp).heightIn(max=350.dp)){
            if(options.size>10) Box(Modifier.padding(horizontal=10.dp)) { Field(filter,{filter=it},if(LocalLayoutDirection.current==LayoutDirection.Rtl)"بحث" else "Search") }
            options.filter{it.first.contains(filter,true)||it.second.contains(filter,true)}.forEach { (key,name) ->
                DropdownMenuItem(text={Text(name)},onClick={open=false;change(key)})
            }
        }
    }
}
@Composable private fun Home(p:Prefs,w:W,enabled:Boolean,selected:Boolean,open:(String)->Unit,enable:()->Unit,pick:()->Unit,share:()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(29.dp)).background(brandBrush).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
            Icon(painterResource(R.drawable.lisan_mark),contentDescription=w("Lisan logo","شعار لسان"),
                tint=Color.Unspecified, modifier=Modifier.size(62.dp))
            Column { Text(w("LISAN STUDIO","استوديو لسان"),color=mint,letterSpacing=2.7.sp,fontSize=11.sp,fontWeight=FontWeight.Bold); Help("NATIVE ANDROID IME") }
        }
        Text(w("Lisan AI Keyboard","لسان، لوحة مفاتيح ذكية"),fontSize=31.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,color=Color.White)
        Text(w("Type, speak, translate. In your language, across languages.","اكتب، تحدّث، ترجم. بلغتك وعبر اللغات."),
            style=MaterialTheme.typography.bodyMedium,color=Color(0xFFDBE7FA))
        SuggestionChip(onClick={open("languages")},label={Text(w("${p.languages.size} keyboard layouts","${p.languages.size} تخطيطات مفعّلة"))})
    }
    Tile {
        Sub(w(if(selected) "Lisan is your active keyboard" else "Set up your keyboard",if(selected) "لسان هي لوحة مفاتيحك النشطة" else "فعّل لوحة مفاتيحك"))
        Help(w("Choose layouts, enable Lisan in Android, then select it. Microphone permission is only needed for voice.",
            "اختر التخطيطات، ثم فعّل لسان في أندرويد واخترها. إذن الميكروفون مطلوب فقط عند استخدام الصوت."))
        TextButton(onClick={open("languages")}){Text(w("1 · Choose languages","١ · اختر اللغات"))}
        Button(onClick=enable,modifier=Modifier.fillMaxWidth()){Text(if(enabled)w("2 · Enabled in Android · manage","٢ · مفعّلة في أندرويد · إدارة") else w("2 · Enable in Android","٢ · التفعيل في أندرويد"))}
        OutlinedButton(onClick=pick,modifier=Modifier.fillMaxWidth()){Text(w("3 · Choose keyboard","٣ · اختيار لوحة المفاتيح"))}
    }
    Sub(w("Live keyboard studio","استوديو اللوحة الحي"))
    PreviewBoard(p,w,open)
    Tile {
        Sub(w("Try it in a real Android text field","جرّبها في حقل نص نظامي حقيقي"))
        var text by remember { mutableStateOf("") }
        Field(text,{text=it.take(3000)},w("Type here after switching to Lisan","اكتب هنا بعد اختيار لسان"),2)
    }
    Action(w("Voice & microphone","الصوت والميكروفون"),w("On-device first","المعالجة المحلية أولاً")){open("voice")}
    Action(w("Translation models","نماذج الترجمة"),w("Install languages for offline translation","نزّل اللغات للترجمة دون اتصال")){open("models")}
    Action(w("Support & feedback","الدعم والملاحظات")){open("support")}
    Action(w("Share Lisan","مشاركة لسان"),w("Android Sharesheet","قائمة مشاركة أندرويد")){share()}
}

@Composable internal fun PreviewBoard(p:Prefs,w:W,open:((String)->Unit)?=null) {
    var preview by remember { mutableStateOf("") }
    var lang by remember(p.languages,p.defaultLang) { mutableStateOf(p.defaultLang) }
    val prefsState by rememberUpdatedState(p)
    val langState by rememberUpdatedState(lang)
    val opener by rememberUpdatedState(open)
    val color by animateColorAsState(Color(BoardStyle.palette(p.theme).bg),label="keyboard-theme")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color)) {
        Text(preview.ifEmpty{w("Tap the keys to try this design","اضغط المفاتيح لتجربة هذا التصميم")},color=Color(BoardStyle.palette(p.theme).fg),
            modifier=Modifier.fillMaxWidth().padding(12.dp).heightIn(min=24.dp),maxLines=2)
        AndroidView(
            factory={ ctx -> Board(ctx){ action,value ->
                when(action) {
                    "text","space" -> preview=(preview+value).takeLast(220)
                    "enter" -> preview=(preview+"\n").takeLast(220)
                    "delete" -> if(preview.isNotEmpty()) preview=preview.substring(0,preview.offsetByCodePoints(preview.length,-1))
                    "deleteWord" -> preview=preview.dropLast(KeyboardCore.tail(preview.trimEnd()).length+(preview.length-preview.trimEnd().length))
                    "language" -> prefsState.languages.let { l -> lang=if(value in l)value else l[(l.indexOf(langState)+1).mod(l.size)] }
                    "translate" -> opener?.invoke("translate")
                    "mic" -> opener?.invoke("voice")
                    "clipboard","emoji","settings" -> opener?.invoke(action)
                    "cursor" -> preview=""
                }
            } },
            modifier=Modifier.fillMaxWidth(),
            update={ it.configure(p,lang) }
        )
    }
}

private val themeTitles=mapOf("dark" to ("Premium Dark" to "ليلي فاخر"),"light" to ("Modern Light" to "فاتح عصري"),
    "gradient" to ("Gradient AI" to "تدرّج ذكي"),"minimal" to ("Minimal Professional" to "بساطة احترافية"),
    "forest" to ("Forest Atelier" to "أخضر طبيعي"),"glass" to ("Frosted Glass" to "زجاج شفاف"),"custom" to ("Personal Studio" to "تصميم شخصي"))
@Composable private fun ThemeGallery(p:Prefs,w:W,edit:EditPrefs,open:(String)->Unit) {
    var active by remember { mutableStateOf(p.theme) }
    Page(w("Design studio","استوديو التصميم"),w("Keyboard themes","سمات لوحة المفاتيح"),
        w("Try each full keyboard before applying the theme to the system keyboard.","جرّب كل لوحة كاملة قبل تطبيق مظهرها على لوحة النظام."))
    Prefs.themeNames.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            pair.forEach { id ->
                val colors=BoardStyle.palette(id)
                val title=themeTitles.getValue(id)
                Surface(onClick={active=id},modifier=Modifier.weight(1f),color=Color(colors.bg),contentColor=Color(colors.fg),
                    shape=RoundedCornerShape(17.dp),border=BorderStroke(if(active==id)2.dp else 1.dp,Color(colors.accent))) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(w(title.first,title.second),fontWeight=FontWeight.Bold,maxLines=1)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            listOf(colors.key,colors.special,colors.accent).forEach { c -> Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(c))) }
                        }
                        Text(if(active==id)w("Previewing","قيد المعاينة") else if(p.theme==id)w("Applied","مطبّقة") else w("Preview","معاينة"),fontSize=11.sp)
                    }
                }
            }
            if(pair.size==1) Spacer(Modifier.weight(1f))
        }
    }
    PreviewBoard(p.copy(theme=active),w,open)
    Button(onClick={edit{it.copy(theme=active)}},enabled=p.theme!=active,modifier=Modifier.fillMaxWidth()){
        Text(if(p.theme==active)w("Applied to Lisan","مطبّقة على لسان") else w("Apply to keyboard","تطبيق على اللوحة"))
    }
    Action(w("Customize appearance","تخصيص المظهر"),w("Colors, dimensions and key geometry","الألوان والأبعاد وأشكال الأزرار")){open("custom")}
}

@Composable private fun SettingsIndex(w:W,open:(String)->Unit,share:()->Unit) {
    Page(w("Control center","مركز التحكم"),w("Settings","الإعدادات"),w("Make Lisan yours. Changes are saved on this device.","خصّص لسان. تُحفظ التغييرات على هذا الجهاز."))
    val pages=listOf(
        Triple("keyboard",w("Keyboard & prediction","لوحة المفاتيح والتنبؤ"),w("Rows, numbering, haptics and correction","الصفوف والأرقام والاهتزاز والتصحيح")),
        Triple("languages",w("Languages & interface","اللغات والواجهة"),w("Layouts, defaults, Arabic RTL and English LTR","التخطيطات واللغة الافتراضية واتجاه الواجهة")),
        Triple("voice",w("Voice & microphone","الصوت والميكروفون"),w("Provider, language and permission","المحرك واللغة والإذن")),
        Triple("translate",w("Translation","الترجمة"),w("Manual translation, language and options","ترجمة يدوية وإعدادات اللغة")),
        Triple("models",w("Models & storage","النماذج والتخزين"),w("Download and remove language models","تنزيل نماذج الترجمة وحذفها")),
        Triple("fonts",w("Fonts & sizes","الخطوط والأحجام"),w("Live Arabic and Latin preview","معاينة عربية ولاتينية مباشرة")),
        Triple("custom",w("Colors & layout dimensions","الألوان والأبعاد"),w("Size, spacing, backgrounds and key shapes","الأحجام والمسافات والخلفيات والأشكال")),
        Triple("tools",w("Toolbar","شريط الأدوات"),w("Show and reorder controls","إظهار الأدوات وترتيبها")),
        Triple("words",w("Personal dictionary","القاموس الشخصي"),w("Add or ignore words","إضافة الكلمات أو تجاهلها")),
        Triple("emoji",w("Emoji & symbols","الإيموجي والرموز"),w("Favorites and recent emoji","المفضلة والمستخدمة مؤخراً")),
        Triple("clipboard",w("Clipboard library","مكتبة الحافظة"),w("Manage only the items you save","إدارة العناصر التي تحفظها بنفسك")),
        Triple("privacy",w("Privacy & local data","الخصوصية والبيانات المحلية"),w("What stays local and how to erase it","ما يبقى محلياً وكيفية حذفه")),
        Triple("backup",w("Export & restore","التصدير والاستعادة"),w("Settings and theme JSON files","ملفات JSON للإعدادات والسمات")),
        Triple("support",w("Report a problem","تواصل مع المطور"),w("Review a diagnostic report before sharing","راجع تقرير المشكلة قبل المشاركة")),
        Triple("about",w("About Lisan","حول لسان"),w("Version and sharing link","الإصدار ورابط المشاركة"))
    )
    pages.forEach { (key,title,caption) -> Action(title,caption){open(key)} }
    Action(w("Share app","مشاركة التطبيق")){share()}
}
