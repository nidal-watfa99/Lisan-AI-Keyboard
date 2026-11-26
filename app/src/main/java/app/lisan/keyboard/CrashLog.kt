package app.lisan.keyboard

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Local-only crash tracker. Stores the last unhandled exception on the device.
 * It never sends anything by itself: the user may attach it to an email report after reviewing it.
 * The exception message is deliberately omitted (it could contain typed text); only the class names
 * and stack frames are kept.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val MAX = 2000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { record(app, error) } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
    }

    fun format(error: Throwable): String {
        val out = StringBuilder()
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 4) {
            out.append(if (depth == 0) "" else "Caused by: ").append(cause.javaClass.name).append('\n')
            cause.stackTrace.take(12).forEach { out.append("  at ").append(it.toString()).append('\n') }
            cause = cause.cause; depth++
        }
        return out.toString().take(MAX)
    }

    fun record(ctx: Context, error: Throwable) {
        val head = "Time: ${java.util.Date()}\nVersion: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n"
        File(ctx.filesDir, FILE).writeText(head + format(error))
    }

    fun last(ctx: Context): String =
        try { File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText().orEmpty() } catch (_: Exception) { "" }

    fun clear(ctx: Context) { try { File(ctx.filesDir, FILE).delete() } catch (_: Exception) {} }
}

class LisanApp : Application() {
    override fun onCreate() { super.onCreate(); CrashLog.install(this) }
}
