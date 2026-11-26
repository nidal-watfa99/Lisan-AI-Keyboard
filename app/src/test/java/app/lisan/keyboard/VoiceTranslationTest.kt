package app.lisan.keyboard

import app.lisan.keyboard.core.Prediction
import app.lisan.keyboard.translation.CloudTranslator
import app.lisan.keyboard.voice.SpeechGate
import org.junit.Assert.*
import org.junit.Test

class VoiceTranslationTest {
    @Test fun refusesUnsafeOrCredentialBearingEndpoints() {
        listOf("http://example.org/translate","https://name:pass@example.org/translate",
            "https://example.org/translate?api_key=test","https://example.org/translate#x",
            "https://example.org:80/translate","https://example.org","https:///translate").forEach {
            assertNull(it,CloudTranslator.endpoint(it))
        }
        assertEquals("example.org",CloudTranslator.endpoint(" https://example.org/translate ")?.host)
    }

    @Test fun silenceIsNotSentForDecoding() {
        val gate=SpeechGate()
        assertFalse(gate.observe(1000,1000,20,40))
        assertTrue(gate.observe(5600,300,55,40))
        assertFalse(gate.heard)
    }

    @Test fun finishesAfterSpeechAndSilence() {
        val gate=SpeechGate()
        repeat(4) { assertFalse(gate.observe(100+40L*it,1800,300,40)) }
        assertTrue(gate.heard)
        assertFalse(gate.observe(1300,0,0,40))
        assertTrue(gate.observe(1420,0,0,40))
    }

    @Test fun arabicCorrectionIsOptInAndKeepsProtectedOrVocalizedWords() {
        assertNull(Prediction.correction("هاذا","ar",emptyList()))
        assertEquals("هذا",Prediction.correction("هاذا","ar",emptyList(),true))
        assertNull(Prediction.correction("هاذا","ar",listOf("هاذا"),true))
        assertNull(Prediction.correction("هَاذا","ar",emptyList(),true))
    }
}
