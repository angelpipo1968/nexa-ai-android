package com.nexa.ai

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Captures uncaught exceptions and shows a crash screen with the error details.
 * The user can copy the error and send it for debugging.
 */
class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val log = buildString {
                appendLine("═══════════════════════════════════════")
                appendLine("NEXA PRO CRASH LOG")
                appendLine("Time: $timestamp")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine("═══════════════════════════════════════")
                appendLine(stackTrace)
                appendLine()
            }

            // Launch crash screen
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra("crash_log", log)
                putExtra("exception_name", throwable.javaClass.simpleName)
                putExtra("error_message", throwable.message ?: "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)

            // Kill the process
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)

        } catch (e: Exception) {
            // If crash screen fails, fall back to default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun install(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, defaultHandler))
        }
    }
}
