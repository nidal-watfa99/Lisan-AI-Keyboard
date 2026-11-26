package app.lisan.keyboard

import org.junit.Assert.*
import org.junit.Test

class CrashLogTest {
    @Test fun formatKeepsFramesButNotTheMessage() {
        val text = CrashLog.format(IllegalStateException("secret typed text"))
        assertTrue(text.startsWith("java.lang.IllegalStateException"))
        assertFalse(text.contains("secret typed text"))
        assertTrue(text.length <= 2000)
    }
}
