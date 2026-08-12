@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName")

package com.bliss.screenreader.service

import android.content.Context
import android.os.Build
import com.bliss.screenreader.data.model.CaptureMode
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps one human-readable diagnostic file for the latest capture session.
 * The file stays on the device after a failed run and can be shared from the
 * capture bubble without requiring adb or Logcat.
 */
object CaptureDiagnostics {

    private const val LOG_FILE_NAME = "capture_diagnostics.txt"
    private const val PREVIOUS_LOG_FILE_NAME = "capture_diagnostics_previous.txt"
    private const val MAX_LOG_BYTES = 1_500_000L
    private const val MAX_VISIBLE_NODES = 100
    private const val MAX_NODE_LENGTH = 240

    private val FileLock = Any()
    private val TimestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun StartSession(
        ContextObj: Context,
        SessionId: String,
        ModeVal: CaptureMode,
        ExpectedPackage: String
    ) {
        synchronized(FileLock) {
            val LogFile = GetLogFile(ContextObj = ContextObj)
            LogFile.parentFile?.mkdirs()
            if (LogFile.exists()) {
                val PreviousFile = File(LogFile.parentFile, PREVIOUS_LOG_FILE_NAME)
                runCatching {
                    if (PreviousFile.exists()) PreviousFile.delete()
                    LogFile.renameTo(PreviousFile)
                }
            }
            val HeaderText = buildString {
                appendLine("Screen Reader capture diagnostics")
                appendLine("Started: ${FormatTimestamp(System.currentTimeMillis())}")
                appendLine("Session ID: $SessionId")
                appendLine("Mode: ${ModeVal.name}")
                appendLine("Expected package: $ExpectedPackage")
                appendLine("Reader package: ${ContextObj.packageName}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("------------------------------------------------------------")
            }
            runCatching { LogFile.writeText(HeaderText) }
        }
    }

    fun Log(ContextObj: Context, EventName: String, MessageText: String) {
        val CleanMessage = MessageText.replace('\u0000', ' ').trim()
        val LogLine = "${FormatTimestamp(System.currentTimeMillis())} | $EventName | $CleanMessage\n"
        synchronized(FileLock) {
            val LogFile = GetLogFile(ContextObj = ContextObj)
            LogFile.parentFile?.mkdirs()
            RotateIfRequired(LogFile = LogFile)
            runCatching {
                FileWriter(LogFile, true).use { WriterObj -> WriterObj.append(LogLine) }
            }
        }
    }

    fun LogVisibleNodes(
        ContextObj: Context,
        ScreenName: String,
        PackageNameVal: String,
        VisibleNodes: List<String>,
        StateText: String
    ) {
        val NodeDump = VisibleNodes
            .take(MAX_VISIBLE_NODES)
            .mapIndexed { NodeIndex, NodeText ->
                val CleanNode = NodeText
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .replace(Regex("\\s+"), " ")
                    .take(MAX_NODE_LENGTH)
                "[$NodeIndex] $CleanNode"
            }
            .joinToString(separator = " || ")
        val OmittedCount = (VisibleNodes.size - MAX_VISIBLE_NODES).coerceAtLeast(0)
        val OmittedText = if (OmittedCount > 0) " || ... $OmittedCount more nodes" else ""
        Log(
            ContextObj = ContextObj,
            EventName = "SCREEN",
            MessageText = "name=$ScreenName package=$PackageNameVal nodes=${VisibleNodes.size} " +
                    "state={$StateText} visible={$NodeDump$OmittedText}"
        )
    }

    fun GetLogFile(ContextObj: Context): File {
        val BaseDirectory = ContextObj.getExternalFilesDir("diagnostics")
            ?: File(ContextObj.filesDir, "diagnostics")
        return File(BaseDirectory, LOG_FILE_NAME)
    }

    private fun RotateIfRequired(LogFile: File) {
        if (!LogFile.exists() || LogFile.length() < MAX_LOG_BYTES) return
        val PreviousFile = File(LogFile.parentFile, PREVIOUS_LOG_FILE_NAME)
        runCatching {
            if (PreviousFile.exists()) PreviousFile.delete()
            LogFile.renameTo(PreviousFile)
            LogFile.writeText(
                "Screen Reader capture diagnostics continued after size limit\n" +
                        "Continued: ${FormatTimestamp(System.currentTimeMillis())}\n" +
                        "------------------------------------------------------------\n"
            )
        }
    }

    private fun FormatTimestamp(TimestampMs: Long): String {
        return synchronized(TimestampFormat) {
            TimestampFormat.format(Date(TimestampMs))
        }
    }
}
