package app.lisan.keyboard

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lisan.keyboard.core.Prefs
import app.lisan.keyboard.keyboard.Board
import app.lisan.keyboard.keyboard.LisanIme
import app.lisan.keyboard.keyboard.isSensitiveField
import app.lisan.keyboard.privacy.Vault
import app.lisan.keyboard.translation.LocalTranslator
import app.lisan.keyboard.translation.MissingModels
import app.lisan.keyboard.translation.UnsupportedLanguage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class OnDeviceTest {
    private val test=InstrumentationRegistry.getInstrumentation()
    private val context=test.targetContext
    @Test fun serviceDeclaredWithSystemBindPermission() {
        val service=context.packageManager.getServiceInfo(ComponentName(context,LisanIme::class.java),PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_INPUT_METHOD",service.permission)
        assertTrue(service.metaData.containsKey("android.view.im"))
    }
    @Test fun passwordAndNoLearningClassification() {
        assertTrue(isSensitiveField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,0))
        assertTrue(isSensitiveField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,0))
        assertTrue(isSensitiveField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,0))
        assertTrue(isSensitiveField(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,0))
        assertTrue(isSensitiveField(InputType.TYPE_CLASS_TEXT,EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(isSensitiveField(InputType.TYPE_CLASS_TEXT,0))
    }
    private fun descendants(v:View): List<View> = listOf(v) + if(v is ViewGroup)
        (0 until v.childCount).flatMap{descendants(v.getChildAt(it))} else emptyList()
    @Test fun liveBoardSendsRealKeyEventsForAllFourThemes() {
        test.runOnMainSync {
            val events=mutableListOf<Pair<String,String>>()
            val b=Board(context){action,value->events+=action to value}
            listOf("dark","light","gradient","minimal").forEach { theme ->
                b.configure(Prefs(theme=theme,marks=true),"ar")
                descendants(b).first{it.contentDescription=="ض"}.performClick()
                descendants(b).first{it.contentDescription=="◌َ"}.performClick()
            }
            assertEquals(List(4){listOf("text" to "ض","text" to "َ")}.flatten(),events)
        }
    }
    @Test fun sensitiveBoardHidesMicrophoneTranslationAndClipboard() {
        test.runOnMainSync {
            val b=Board(context){_,_->}
            b.configure(Prefs(ui="en"),"en")
            val tools=setOf("Microphone","Translate","Clipboard")
            assertTrue(descendants(b).map{it.contentDescription}.containsAll(tools))
            b.configure(Prefs(ui="en"),"en",sensitive=true)
            assertTrue(descendants(b).none{it.contentDescription in tools})
        }
    }
    @Test fun settingsImportRejectsInvalidDimensionsAndKeepsKeyboardUsable() {
        val p=Prefs.from(JSONObject("""{"languages":["fake"],"height":999,"width":0,"rowOrder":[1,1,1],"colors":{"bg":"bad"}}"""))
        assertEquals(listOf("ar"),p.languages); assertEquals("ar",p.defaultLang)
        assertEquals(60,p.height); assertEquals(80,p.width); assertEquals(listOf(0,1,2),p.rowOrder)
        assertTrue(p.colors.isEmpty())
    }
    @Test fun themeExportDoesNotOverrideVoiceOrLanguage() {
        val source=Prefs(theme="gradient",gap=4,languages=listOf("fr"),voiceMode="system")
        val target=Prefs().importTheme(source.themeJson())
        assertEquals("gradient",target.theme); assertEquals(4,target.gap)
        assertEquals(listOf("ar","en"),target.languages); assertEquals("local",target.voiceMode)
    }
    @Test fun themeLibraryRoundTripsWithoutChangingActiveDesign() {
        val original=Prefs(theme="gradient",gap=4)
        val target=Prefs(savedThemes=mapOf("Blue" to original.themeJson().toString()))
        val restored=Prefs.from(target.json())
        assertEquals("dark",restored.theme)
        assertEquals("gradient",restored.importTheme(JSONObject(restored.savedThemes.getValue("Blue"))).theme)
    }
    @Test fun packagedVoiceRuntimeLoads() {
        assertTrue(app.lisan.keyboard.voice.WhisperNative.available)
    }
    @Test fun offlineVoiceDecodesOptionalPcmFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("offlinePcmFixture")=="true")
        val voice=app.lisan.keyboard.voice.WhisperNative
        val model=app.lisan.keyboard.voice.SpeechModel.file(context)
        val pcm=File(context.noBackupFilesDir,"qa-offline-en.pcm").readBytes()
        assertTrue(model.exists() && pcm.size in 6400..960000 && pcm.size%2==0)
        val samples=ShortArray(pcm.size/2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        assertNull(voice.decode(model.path,samples,"en",AtomicBoolean(true),2))
        val text=checkNotNull(voice.decode(model.path,samples,"en",AtomicBoolean(false),2)).toString(Charsets.UTF_8)
        assertTrue(text,text.contains("ask not what your country",true))
    }
    @Test fun arabicModelActuallyTranslatesAfterExplicitTestOptIn() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("downloadTranslationModels")=="true")
        withTimeout(150_000) {
            val arabic=LocalTranslator.translate("Good morning","en","ar",true,false)
            assertTrue(arabic.text.any{it in '\u0600'..'\u06ff'})
            val english=LocalTranslator.translate(arabic.text,"ar","en",false,false)
            assertTrue(english.text.contains("morning",true))
        }
    }
    @Test fun encryptedLibraryRoundTripAndCiphertext() {
        val vault=Vault.get(context)
        val phrase="test-only-plain-text-that-must-not-be-on-disk"
        try {
            vault.update("qa"){ listOf(JSONObject().put("text",phrase)) }
            assertEquals(phrase,vault.items("qa").first().getString("text"))
            assertFalse(java.io.File(context.noBackupFilesDir,"lisan-vault-v1").readBytes().toString(Charsets.ISO_8859_1).contains(phrase))
        } finally { vault.update("qa"){emptyList()} }
    }
    @Test fun translationUsesBundledLanguageIdentificationAndRejectsUnknownTarget() = runBlocking {
        withTimeout(25_000) {
            val input="مرحبا بكم، هذه جملة عربية واضحة لاختبار تحديد اللغة محلياً."
            val result=LocalTranslator.translate(input,"auto","ar",false,true)
            assertEquals("ar",result.source); assertEquals(input,result.text)
            try {LocalTranslator.translate("test","en","zz",false,true);fail("Unsupported target accepted")}
            catch(_:UnsupportedLanguage){}
        }
    }
    @Test fun translationDoesNotStartAnUnapprovedModelDownload() = runBlocking {
        withTimeout(25_000) {
            val unloaded=LocalTranslator.languages.firstOrNull{it!="en" && it !in LocalTranslator.models()}
            if(unloaded!=null) {
                try { LocalTranslator.translate("A test","en",unloaded,false,true);fail("Missing model not reported") }
                catch(e:MissingModels) {assertTrue(unloaded in e.languages)}
            }
        }
    }
}
