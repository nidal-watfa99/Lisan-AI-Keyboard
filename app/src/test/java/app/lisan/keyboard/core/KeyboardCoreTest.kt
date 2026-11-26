package app.lisan.keyboard.core

import org.junit.Assert.*
import org.junit.Test

class KeyboardCoreTest {
    @Test fun sixIndependentLetterLayouts() {
        assertEquals(listOf("ar","en","fr","de","nl","tr"),KeyboardCore.layouts.map { it.tag })
        KeyboardCore.layouts.forEach { assertEquals(3,it.rows.size); assertTrue(it.rows.all { r -> r in it.rows && r.isNotEmpty() }) }
    }
    @Test fun arabicLettersAndMarks() {
        val row=KeyboardCore.layouts.first().rows.flatten()
        assertTrue(row.containsAll(listOf("ى","ي","ة","ئ","ء","ؤ","ض","ظ")))
        assertTrue(KeyboardCore.alternatives.getValue("ا").containsAll(listOf("أ","إ","آ")))
        assertTrue(KeyboardCore.marks.containsAll(listOf("ّ","ْ","َ","ُ","ِ","ـ")))
    }
    @Test fun wordTailPreservesArabicDiacritics() {
        assertEquals("لِسَانٌ",KeyboardCore.tail("هذا لِسَانٌ"))
        assertEquals("",KeyboardCore.tail("لِسَانٌ "))
        assertEquals("don't",KeyboardCore.tail("please don't"))
    }
    @Test fun joinsSpeechWithoutDoubleSpacesOrBreakingPunctuation() {
        assertEquals(" world",KeyboardCore.speechJoin("Hello"," world "))
        assertEquals("بك",KeyboardCore.speechJoin("مرحباً ","بك"))
        assertEquals("؟",KeyboardCore.speechJoin("كيف الحال","؟"))
        assertEquals("",KeyboardCore.speechJoin("Hello"," "))
    }
    @Test fun turkishCaseUsesLocale() {
        assertEquals("İ",KeyboardCore.upper("i","tr"))
        assertEquals("I",KeyboardCore.upper("ı","tr"))
    }
    @Test fun predictionRespectsPersonalWordsAndIgnore() {
        assertEquals(listOf("linspace"),Prediction.choices("use lin","en",listOf("linspace"),emptyList()))
        assertEquals(emptyList<String>(),Prediction.choices("صباح ","ar",emptyList(),listOf("الخير","النور")))
        assertEquals("عليكم",Prediction.choices("السلام ","ar",emptyList(),emptyList()).first())
    }
    @Test fun correctionIsOptInAtCallSiteAndConservative() {
        assertEquals("the",Prediction.correction("teh","en",emptyList()))
        assertNull(Prediction.correction("Teh","en",emptyList()))
        assertNull(Prediction.correction("teh","en",listOf("teh")))
        assertNull(Prediction.correction("لان","ar",emptyList()))
    }
    @Test fun arabicSuggestionDoesNotNormalizeTheTypedWord() {
        assertFalse(Prediction.choices("شُ","ar",emptyList(),emptyList()).contains("شكراً"))
        assertEquals("شُ",KeyboardCore.tail("شُ"))
    }
    @Test fun spanishLayoutHasSpecialCharacters() {
        val es = KeyboardCore.layouts.first { it.tag == "es" }
        val keys = es.rows.flatten()
        listOf("ñ","¿","¡").forEach { assertTrue(it in keys) }
        assertEquals("Ñ", KeyboardCore.upper("ñ","es"))
        listOf("á","é","í","ó","ú","ü").forEach { v ->
            assertTrue(KeyboardCore.alternatives.values.any { v in it })
        }
        assertEquals("¿", KeyboardCore.alternatives["?"]?.first())
    }
}
