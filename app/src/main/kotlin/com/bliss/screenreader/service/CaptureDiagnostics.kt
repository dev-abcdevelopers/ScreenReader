@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "unused")

package com.bliss.screenreader.service

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.bliss.screenreader.data.model.CaptureMode
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CaptureDiagnostics {

    private const val DIRECTORY_NAME = "diagnostics"
    private const val SESSION_FILE_PREFIX = "session_"
    private const val AMBIENT_FILE_NAME = "app.log"
    private const val FILE_EXTENSION = ".log"
    private const val PART_MARKER = "_p"
    private const val MAX_PART_BYTES = 1_500_000L
    private const val MAX_SESSION_COUNT = 10
    private const val MAX_TOTAL_BYTES = 30_000_000L
    private const val MAX_SESSION_ID_LENGTH = 40
    private const val MAX_VISIBLE_NODES = 100
    private const val MAX_NODE_LENGTH = 240
    private const val SHARE_MIME_TYPE = "text/plain"
    private const val SHARE_SUBJECT = "Screen Reader capture diagnostics"
    private const val SHARE_CLIP_LABEL = "capture diagnostics"
    private const val SEPARATOR =
        "------------------------------------------------------------"

    private val LEGACY_FILE_NAMES = listOf(
        "capture_diagnostics.txt",
        "capture_diagnostics_previous.txt"
    )

    private val FileLock = Any()
    private val TimestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val FileStampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    @Volatile
    private var ActiveSessionId: String = ""

    fun StartSession(
        ContextObj: Context,
        SessionId: String,
        ModeVal: CaptureMode,
        ExpectedPackage: String,
        IsResumedVal: Boolean = false
    ) {
        val CleanId = SanitiseSessionId(SessionIdVal = SessionId)
        synchronized(FileLock) {
            val DirectoryObj = GetDirectory(ContextObj = ContextObj)
            DirectoryObj.mkdirs()
            RemoveLegacyFiles(DirectoryObj = DirectoryObj)

            val ExistingParts = PartsOf(DirectoryObj = DirectoryObj, CleanSessionId = CleanId)
            val IsContinuation = ExistingParts.isNotEmpty()
            val TargetFile = ExistingParts.lastOrNull() ?: File(
                DirectoryObj,
                SESSION_FILE_PREFIX +
                        FileStamp(TimestampMs = System.currentTimeMillis()) +
                        "_" + CleanId + FILE_EXTENSION
            )
            ActiveSessionId = CleanId

            val HeaderText = buildString {
                if (IsContinuation) appendLine()
                appendLine(SEPARATOR)
                appendLine("Screen Reader capture diagnostics")
                appendLine(
                    if (IsContinuation || IsResumedVal) {
                        "Resumed: ${FormatTimestamp(TimestampMs = System.currentTimeMillis())}"
                    } else {
                        "Started: ${FormatTimestamp(TimestampMs = System.currentTimeMillis())}"
                    }
                )
                appendLine("Session ID: $SessionId")
                appendLine("Mode: ${ModeVal.name}")
                appendLine("Expected package: $ExpectedPackage")
                appendLine("Reader package: ${ContextObj.packageName}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine(SEPARATOR)
            }
            runCatching {
                FileWriter(TargetFile, true).use { WriterObj -> WriterObj.append(HeaderText) }
            }
            PruneOldSessions(DirectoryObj = DirectoryObj)
        }
    }

    fun Log(ContextObj: Context, EventName: String, MessageText: String) {
        WriteLine(
            ContextObj = ContextObj,
            CleanSessionId = ActiveSessionId,
            LogLine = BuildLine(EventName = EventName, MessageText = MessageText)
        )
    }

    fun LogForSession(
        ContextObj: Context,
        SessionId: String,
        EventName: String,
        MessageText: String
    ) {
        WriteLine(
            ContextObj = ContextObj,
            CleanSessionId = SanitiseSessionId(SessionIdVal = SessionId),
            LogLine = BuildLine(EventName = EventName, MessageText = MessageText)
        )
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

    fun GetSessionLogFiles(ContextObj: Context, SessionId: String): List<File> {
        val CleanId = SanitiseSessionId(SessionIdVal = SessionId)
        if (CleanId.isEmpty()) return emptyList()
        synchronized(FileLock) {
            return PartsOf(
                DirectoryObj = GetDirectory(ContextObj = ContextObj),
                CleanSessionId = CleanId
            )
        }
    }

    fun GetActiveLogFiles(ContextObj: Context): List<File> {
        return GetSessionLogFiles(ContextObj = ContextObj, SessionId = ActiveSessionId)
    }

    fun HasSessionLogs(ContextObj: Context, SessionId: String): Boolean {
        return GetSessionLogFiles(ContextObj = ContextObj, SessionId = SessionId).isNotEmpty()
    }

    fun DeleteSessionLogs(ContextObj: Context, SessionId: String) {
        val CleanId = SanitiseSessionId(SessionIdVal = SessionId)
        if (CleanId.isEmpty()) return
        synchronized(FileLock) {
            val PartFiles = PartsOf(
                DirectoryObj = GetDirectory(ContextObj = ContextObj),
                CleanSessionId = CleanId
            )
            for (PartFile in PartFiles) runCatching { PartFile.delete() }
            if (ActiveSessionId == CleanId) ActiveSessionId = ""
        }
    }

    fun BuildShareIntent(ContextObj: Context, LogFiles: List<File>): Intent? {
        val ExistingFiles = LogFiles.filter { FileRef -> FileRef.exists() }
        if (ExistingFiles.isEmpty()) return null

        val AuthorityText = "${ContextObj.packageName}.fileprovider"
        val UriList = ArrayList<Uri>(ExistingFiles.size)
        for (FileRef in ExistingFiles) {
            val UriRef = runCatching {
                FileProvider.getUriForFile(ContextObj, AuthorityText, FileRef)
            }.getOrNull() ?: continue
            UriList.add(UriRef)
        }
        if (UriList.isEmpty()) return null

        val ClipDataObj = ClipData.newRawUri(SHARE_CLIP_LABEL, UriList.first())
        for (UriIndex in 1 until UriList.size) {
            ClipDataObj.addItem(ClipData.Item(UriList[UriIndex]))
        }

        val IntentObj = if (UriList.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, UriList.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, UriList)
            }
        }
        return IntentObj.apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_SUBJECT, SHARE_SUBJECT)
            clipData = ClipDataObj
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun BuildLine(EventName: String, MessageText: String): String {
        val NullChar: Char = 0.toChar()
        val CleanMessage = MessageText.replace(NullChar, ' ').trim()
        return FormatTimestamp(TimestampMs = System.currentTimeMillis()) +
                " | " + EventName + " | " + CleanMessage + "\n"
    }

    private fun WriteLine(ContextObj: Context, CleanSessionId: String, LogLine: String) {
        synchronized(FileLock) {
            val DirectoryObj = GetDirectory(ContextObj = ContextObj)
            DirectoryObj.mkdirs()
            val TargetFile = ResolveTarget(
                DirectoryObj = DirectoryObj,
                CleanSessionId = CleanSessionId
            )
            runCatching {
                FileWriter(TargetFile, true).use { WriterObj -> WriterObj.append(LogLine) }
            }
        }
    }

    private fun ResolveTarget(DirectoryObj: File, CleanSessionId: String): File {
        if (CleanSessionId.isEmpty()) return AmbientTarget(DirectoryObj = DirectoryObj)
        val PartFiles = PartsOf(DirectoryObj = DirectoryObj, CleanSessionId = CleanSessionId)
        val LatestPart = PartFiles.lastOrNull()
            ?: return AmbientTarget(DirectoryObj = DirectoryObj)
        if (LatestPart.length() < MAX_PART_BYTES) return LatestPart

        val NextIndex = PartIndexOf(FileNameVal = LatestPart.name) + 1
        val NextFile = File(
            DirectoryObj,
            BaseNameOf(FileNameVal = LatestPart.name) + PART_MARKER + NextIndex + FILE_EXTENSION
        )
        runCatching {
            NextFile.writeText(
                SEPARATOR + "\n" +
                        "Continued from part " + (NextIndex - 1) + " at " +
                        FormatTimestamp(TimestampMs = System.currentTimeMillis()) + "\n" +
                        SEPARATOR + "\n"
            )
        }
        return NextFile
    }

    private fun AmbientTarget(DirectoryObj: File): File {
        val AmbientFile = File(DirectoryObj, AMBIENT_FILE_NAME)
        if (AmbientFile.exists() && AmbientFile.length() >= MAX_PART_BYTES) {
            runCatching {
                AmbientFile.writeText(
                    SEPARATOR + "\n" +
                            "Restarted after size limit at " +
                            FormatTimestamp(TimestampMs = System.currentTimeMillis()) + "\n" +
                            SEPARATOR + "\n"
                )
            }
        }
        return AmbientFile
    }

    private fun PartsOf(DirectoryObj: File, CleanSessionId: String): List<File> {
        val AllFiles = DirectoryObj.listFiles() ?: return emptyList()
        return AllFiles
            .filter { FileRef ->
                FileRef.isFile &&
                        IsSessionFile(FileNameVal = FileRef.name) &&
                        SessionIdOf(FileNameVal = FileRef.name) == CleanSessionId
            }
            .sortedBy { FileRef -> PartIndexOf(FileNameVal = FileRef.name) }
    }

    private fun PruneOldSessions(DirectoryObj: File) {
        val AllFiles = (DirectoryObj.listFiles() ?: return)
            .filter { FileRef -> FileRef.isFile && IsSessionFile(FileNameVal = FileRef.name) }
        if (AllFiles.isEmpty()) return

        val Groups = AllFiles
            .groupBy { FileRef -> SessionIdOf(FileNameVal = FileRef.name) }
            .entries
            .sortedBy { GroupEntry -> GroupEntry.value.minOf { FileRef -> FileRef.name } }
            .toMutableList()

        var TotalBytes = AllFiles.sumOf { FileRef -> FileRef.length() }

        while (Groups.isNotEmpty()) {
            val IsOverCount = Groups.size > MAX_SESSION_COUNT
            val IsOverBytes = TotalBytes > MAX_TOTAL_BYTES
            if (!IsOverCount && !IsOverBytes) return

            val VictimIndex = Groups.indexOfFirst { GroupEntry ->
                GroupEntry.key != ActiveSessionId
            }
            if (VictimIndex < 0) return

            val VictimGroup = Groups.removeAt(VictimIndex)
            for (FileRef in VictimGroup.value) {
                TotalBytes -= FileRef.length()
                runCatching { FileRef.delete() }
            }
        }
    }

    private fun RemoveLegacyFiles(DirectoryObj: File) {
        for (LegacyName in LEGACY_FILE_NAMES) {
            val LegacyFile = File(DirectoryObj, LegacyName)
            if (LegacyFile.exists()) runCatching { LegacyFile.delete() }
        }
    }

    private fun IsSessionFile(FileNameVal: String): Boolean {
        return FileNameVal.startsWith(SESSION_FILE_PREFIX) &&
                FileNameVal.endsWith(FILE_EXTENSION) &&
                SessionIdOf(FileNameVal = FileNameVal).isNotEmpty()
    }

    private fun BodyOf(FileNameVal: String): String {
        return FileNameVal
            .removePrefix(SESSION_FILE_PREFIX)
            .removeSuffix(FILE_EXTENSION)
    }

    private fun BaseNameOf(FileNameVal: String): String {
        val BodyText = BodyOf(FileNameVal = FileNameVal)
        val TrimmedBody = if (HasPartSuffix(BodyText = BodyText)) {
            BodyText.substringBeforeLast(PART_MARKER)
        } else {
            BodyText
        }
        return SESSION_FILE_PREFIX + TrimmedBody
    }

    private fun SessionIdOf(FileNameVal: String): String {
        val BodyText = BodyOf(FileNameVal = FileNameVal)
        if (!BodyText.contains('_')) return ""
        val AfterStamp = BodyText.substringAfter('_')
        return if (HasPartSuffix(BodyText = AfterStamp)) {
            AfterStamp.substringBeforeLast(PART_MARKER)
        } else {
            AfterStamp
        }
    }

    private fun PartIndexOf(FileNameVal: String): Int {
        val BodyText = BodyOf(FileNameVal = FileNameVal)
        if (!HasPartSuffix(BodyText = BodyText)) return 1
        return BodyText.substringAfterLast(PART_MARKER).toIntOrNull() ?: 1
    }

    private fun HasPartSuffix(BodyText: String): Boolean {
        if (!BodyText.contains(PART_MARKER)) return false
        val SuffixText = BodyText.substringAfterLast(PART_MARKER)
        return SuffixText.isNotEmpty() && SuffixText.all { CharVal -> CharVal.isDigit() }
    }

    private fun SanitiseSessionId(SessionIdVal: String): String {
        return SessionIdVal
            .trim()
            .map { CharVal ->
                if (CharVal.isLetterOrDigit() || CharVal == '-') CharVal else '-'
            }
            .joinToString(separator = "")
            .take(MAX_SESSION_ID_LENGTH)
    }

    private fun GetDirectory(ContextObj: Context): File {
        return ContextObj.getExternalFilesDir(DIRECTORY_NAME)
            ?: File(ContextObj.filesDir, DIRECTORY_NAME)
    }

    private fun FileStamp(TimestampMs: Long): String {
        return synchronized(FileStampFormat) {
            FileStampFormat.format(Date(TimestampMs))
        }
    }

    private fun FormatTimestamp(TimestampMs: Long): String {
        return synchronized(TimestampFormat) {
            TimestampFormat.format(Date(TimestampMs))
        }
    }
}
