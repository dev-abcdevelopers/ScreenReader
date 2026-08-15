@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import com.bliss.screenreader.BuildConfig
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.security.DeviceIdentity
import com.bliss.screenreader.service.CaptureDiagnostics
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object SessionBundleStore {

    const val SCHEMA_VERSION = 1
    const val FILE_PREFIX = "sessions_"
    const val FILE_EXTENSION = ".json"

    private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024

    private val WorkerRef = Executors.newSingleThreadExecutor()
    private val MainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val GsonInstance: Gson = GsonBuilder().serializeNulls().create()
    private val StampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    sealed class ExportOutcome {
        data class Ready(
            val FileRef: File,
            val SessionCount: Int,
            val RecordCount: Int
        ) : ExportOutcome()

        data class Failed(val Message: String) : ExportOutcome()
        object NothingToExport : ExportOutcome()
    }

    data class BundlePreview(
        val FileName: String,
        val SessionCount: Int,
        val RecordCount: Int,
        val NewCount: Int,
        val ReplacedCount: Int,
        val SourceModel: String,
        val ExportedAt: Long
    )

    sealed class PreviewOutcome {
        data class Ready(val PreviewObj: BundlePreview) : PreviewOutcome()
        data class Failed(val Message: String) : PreviewOutcome()
    }

    sealed class ImportOutcome {
        data class Restored(
            val AddedCount: Int,
            val ReplacedCount: Int,
            val SkippedCount: Int
        ) : ImportOutcome()

        data class Failed(val Message: String) : ImportOutcome()
    }

    fun BuildFileName(StampText: String): String = "$FILE_PREFIX$StampText$FILE_EXTENSION"

    fun IsBundleFile(FileNameVal: String): Boolean =
        FileNameVal.startsWith(FILE_PREFIX) &&
            FileNameVal.endsWith(FILE_EXTENSION, ignoreCase = true)

    fun ParseBundle(JsonText: String): SessionBundle? {
        return try {
            val BundleObj = GsonInstance.fromJson(JsonText, SessionBundle::class.java)
            if (BundleObj == null || BundleObj.Sessions == null) null else BundleObj
        } catch (_: Exception) {
            null
        }
    }

    fun BuildBundle(ContextRef: Context): SessionBundle {
        val AppContext = ContextRef.applicationContext
        val EntryList = PolicyRepository.GetSessionHistory(ContextRef = AppContext)
            .map { SessionRef -> BuildEntry(ContextRef = AppContext, SessionRef = SessionRef) }

        return SessionBundle(
            SchemaVersion = SCHEMA_VERSION,
            ExportedAt = System.currentTimeMillis(),
            App = UploadAppInfo(
                PackageName = BuildConfig.APPLICATION_ID,
                VersionName = BuildConfig.VERSION_NAME,
                VersionCode = BuildConfig.VERSION_CODE,
                Flavour = BuildConfig.FLAVOR
            ),
            Device = UploadDeviceInfo(
                RegistrationId = DeviceIdentity.RegistrationId(ContextRef = AppContext),
                Manufacturer = Build.MANUFACTURER.orEmpty(),
                Model = Build.MODEL.orEmpty(),
                AndroidRelease = Build.VERSION.RELEASE.orEmpty(),
                SdkInt = Build.VERSION.SDK_INT
            ),
            Sessions = EntryList
        )
    }

    fun ExportAsync(ContextRef: Context, OnResult: (ExportOutcome) -> Unit) {
        val AppContext = ContextRef.applicationContext
        WorkerRef.execute {
            val ResultOutcome = RunExport(ContextRef = AppContext)
            MainHandler.post { OnResult(ResultOutcome) }
        }
    }

    fun PreviewAsync(ContextRef: Context, SourceUri: Uri, OnResult: (PreviewOutcome) -> Unit) {
        val AppContext = ContextRef.applicationContext
        WorkerRef.execute {
            val ResultOutcome = RunPreview(ContextRef = AppContext, SourceUri = SourceUri)
            MainHandler.post { OnResult(ResultOutcome) }
        }
    }

    fun ImportAsync(ContextRef: Context, SourceUri: Uri, OnResult: (ImportOutcome) -> Unit) {
        val AppContext = ContextRef.applicationContext
        WorkerRef.execute {
            val ResultOutcome = RunImport(ContextRef = AppContext, SourceUri = SourceUri)
            MainHandler.post { OnResult(ResultOutcome) }
        }
    }

    private fun BuildEntry(
        ContextRef: Context,
        SessionRef: PolicyRepository.CaptureSessionReference
    ): SessionBundleEntry {
        val SessionId = SessionRef.SessionId
        val ChangeMap = HashMap<String, List<RecordFieldChange>>()
        for (ModeVal in CaptureMode.entries) {
            val ChangeList = PolicyRepository.GetFieldChanges(
                ContextRef = ContextRef,
                ModeVal = ModeVal,
                SessionId = SessionId
            )
            if (ChangeList.isNotEmpty()) ChangeMap[ModeVal.name] = ChangeList
        }

        return SessionBundleEntry(
            SessionId = SessionId,
            Mode = SessionRef.Mode.name,
            SavedAt = SessionRef.SavedAt,
            LastResumedAt = SessionRef.LastResumedAt,
            RecordCount = SessionRef.RecordCount,
            CapturePolicyDetails = SessionRef.CapturePolicyDetails,
            ChangeCount = SessionRef.ChangeCount,
            AgencyCode = PolicyRepository.GetAgencyCode(
                ContextRef = ContextRef,
                SessionId = SessionId
            ),
            Policies = PolicyRepository.GetCustomerPolicies(
                ContextRef = ContextRef,
                SessionId = SessionId
            ),
            Renewals = PolicyRepository.GetFupPolicies(
                ContextRef = ContextRef,
                SessionId = SessionId
            ),
            Servicing = PolicyRepository.GetPsPolicies(
                ContextRef = ContextRef,
                SessionId = SessionId
            ),
            Gaps = PolicyRepository.GetStoredSessionGaps(
                ContextRef = ContextRef,
                SessionId = SessionId
            ),
            Changes = ChangeMap,
            VisitedCustomers = PolicyRepository.GetVisitedCustomers(
                ContextRef = ContextRef,
                SessionId = SessionId
            ).sorted()
        )
    }

    private fun RunExport(ContextRef: Context): ExportOutcome {
        return try {
            val BundleObj = BuildBundle(ContextRef = ContextRef)
            val SessionList = BundleObj.Sessions.orEmpty()
            if (SessionList.isEmpty()) return ExportOutcome.NothingToExport

            val TargetDir = ContextRef.getExternalFilesDir(null) ?: ContextRef.filesDir
            val TargetFile = File(
                TargetDir,
                BuildFileName(StampText = StampFormat.format(Date(BundleObj.ExportedAt)))
            )
            TargetFile.writeText(GsonInstance.toJson(BundleObj), Charsets.UTF_8)

            val RecordTotal = SessionList.sumOf { EntryRef -> EntryRef.TotalRecordCount }
            CaptureDiagnostics.Log(
                ContextObj = ContextRef,
                EventName = "SESSIONS_EXPORTED",
                MessageText = "file=${TargetFile.name} sessions=${SessionList.size} " +
                    "records=$RecordTotal bytes=${TargetFile.length()}"
            )

            ExportOutcome.Ready(
                FileRef = TargetFile,
                SessionCount = SessionList.size,
                RecordCount = RecordTotal
            )
        } catch (ErrorRef: Exception) {
            ExportOutcome.Failed(
                Message = ErrorRef.message.orEmpty().ifEmpty { "Export failed" }
            )
        }
    }

    private fun RunImport(ContextRef: Context, SourceUri: Uri): ImportOutcome {
        return try {
            val BundleObj = ReadBundle(ContextRef = ContextRef, SourceUri = SourceUri)
                ?: return ImportOutcome.Failed(Message = "Not a session export file")
            if (BundleObj.SchemaVersion > SCHEMA_VERSION) {
                return ImportOutcome.Failed(
                    Message = "File was written by a newer version of the app"
                )
            }
            val SessionList = BundleObj.Sessions.orEmpty()
            if (SessionList.isEmpty()) {
                return ImportOutcome.Failed(Message = "File contains no sessions")
            }

            var AddedCount = 0
            var ReplacedCount = 0
            var SkippedCount = 0

            for (EntryRef in SessionList) {
                if (EntryRef.SessionId.isBlank()) {
                    SkippedCount += 1
                    continue
                }
                val ExistingRef = PolicyRepository.GetSessionReference(
                    ContextRef = ContextRef,
                    SessionId = EntryRef.SessionId
                )
                if (ExistingRef != null) {
                    PolicyRepository.DeleteSession(
                        ContextRef = ContextRef,
                        SessionId = ExistingRef.SessionId,
                        ModeVal = ExistingRef.Mode
                    )
                }
                RestoreEntry(ContextRef = ContextRef, EntryRef = EntryRef)
                if (ExistingRef == null) AddedCount += 1 else ReplacedCount += 1
            }

            CaptureDiagnostics.Log(
                ContextObj = ContextRef,
                EventName = "SESSIONS_IMPORTED",
                MessageText = "sessions=${SessionList.size} added=$AddedCount " +
                    "replaced=$ReplacedCount skipped=$SkippedCount " +
                    "source=${BundleObj.Device?.RegistrationId.orEmpty()} " +
                    "exported=${BundleObj.ExportedAt}"
            )

            ImportOutcome.Restored(
                AddedCount = AddedCount,
                ReplacedCount = ReplacedCount,
                SkippedCount = SkippedCount
            )
        } catch (ErrorRef: Exception) {
            ImportOutcome.Failed(
                Message = ErrorRef.message.orEmpty().ifEmpty { "Import failed" }
            )
        }
    }

    private fun RunPreview(ContextRef: Context, SourceUri: Uri): PreviewOutcome {
        return try {
            val BundleObj = ReadBundle(ContextRef = ContextRef, SourceUri = SourceUri)
                ?: return PreviewOutcome.Failed(Message = "Not a session export file")
            if (BundleObj.SchemaVersion > SCHEMA_VERSION) {
                return PreviewOutcome.Failed(
                    Message = "written by a newer version of the app"
                )
            }

            val SessionList = BundleObj.Sessions.orEmpty()
                .filter { EntryRef -> EntryRef.SessionId.isNotBlank() }
            if (SessionList.isEmpty()) {
                return PreviewOutcome.Failed(Message = "it contains no sessions")
            }

            var ReplacedCount = 0
            for (EntryRef in SessionList) {
                val ExistingRef = PolicyRepository.GetSessionReference(
                    ContextRef = ContextRef,
                    SessionId = EntryRef.SessionId
                )
                if (ExistingRef != null) ReplacedCount += 1
            }

            PreviewOutcome.Ready(
                PreviewObj = BundlePreview(
                    FileName = DisplayNameOf(ContextRef = ContextRef, SourceUri = SourceUri),
                    SessionCount = SessionList.size,
                    RecordCount = SessionList.sumOf { EntryRef -> EntryRef.TotalRecordCount },
                    NewCount = SessionList.size - ReplacedCount,
                    ReplacedCount = ReplacedCount,
                    SourceModel = BundleObj.Device?.Model.orEmpty(),
                    ExportedAt = BundleObj.ExportedAt
                )
            )
        } catch (ErrorRef: Exception) {
            PreviewOutcome.Failed(
                Message = ErrorRef.message.orEmpty().ifEmpty { "it could not be read" }
            )
        }
    }

    private fun ReadBundle(ContextRef: Context, SourceUri: Uri): SessionBundle? {
        val JsonText = ContextRef.contentResolver.openInputStream(SourceUri)?.use { StreamRef ->
            ReadText(StreamRef = StreamRef)
        } ?: return null
        return ParseBundle(JsonText = JsonText)
    }

    private fun DisplayNameOf(ContextRef: Context, SourceUri: Uri): String {
        val CursorRef = try {
            ContextRef.contentResolver.query(SourceUri, null, null, null, null)
        } catch (_: Exception) {
            null
        }
        CursorRef?.use { RowRef ->
            val NameIndex = RowRef.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (NameIndex >= 0 && RowRef.moveToFirst()) {
                val NameText = RowRef.getString(NameIndex).orEmpty()
                if (NameText.isNotEmpty()) return NameText
            }
        }
        return SourceUri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private fun RestoreEntry(ContextRef: Context, EntryRef: SessionBundleEntry) {
        val ModeVal = CaptureMode.FromName(NameVal = EntryRef.Mode)
        val ChangeMap = HashMap<CaptureMode, List<RecordFieldChange>>()
        for ((ModeName, ChangeList) in EntryRef.Changes.orEmpty()) {
            ChangeMap[CaptureMode.FromName(NameVal = ModeName)] = ChangeList
        }

        PolicyRepository.RestoreSession(
            ContextRef = ContextRef,
            SessionRef = PolicyRepository.CaptureSessionReference(
                SessionId = EntryRef.SessionId,
                Mode = ModeVal,
                SavedAt = EntryRef.SavedAt,
                RecordCount = EntryRef.RecordCount,
                CapturePolicyDetails = EntryRef.CapturePolicyDetails,
                LastResumedAt = EntryRef.LastResumedAt,
                ChangeCount = EntryRef.ChangeCount
            ),
            Policies = EntryRef.Policies.orEmpty(),
            Renewals = EntryRef.Renewals.orEmpty(),
            Servicing = EntryRef.Servicing.orEmpty(),
            Gaps = EntryRef.Gaps.orEmpty(),
            Changes = ChangeMap,
            VisitedCustomers = EntryRef.VisitedCustomers.orEmpty(),
            AgencyCode = EntryRef.AgencyCode
        )

        CaptureDiagnostics.LogForSession(
            ContextObj = ContextRef,
            SessionId = EntryRef.SessionId,
            EventName = "SESSION_IMPORTED",
            MessageText = "session=${EntryRef.SessionId} mode=${ModeVal.name} " +
                "policies=${EntryRef.Policies.orEmpty().size} " +
                "renewals=${EntryRef.Renewals.orEmpty().size} " +
                "servicing=${EntryRef.Servicing.orEmpty().size} " +
                "gaps=${EntryRef.Gaps.orEmpty().size}"
        )
    }

    private fun ReadText(StreamRef: InputStream): String {
        val BufferRef = ByteArray(8192)
        val CollectedBytes = ByteArrayOutputStream()
        while (CollectedBytes.size() < MAX_IMPORT_BYTES) {
            val ReadCount = StreamRef.read(BufferRef)
            if (ReadCount <= 0) break
            CollectedBytes.write(BufferRef, 0, ReadCount)
        }
        return CollectedBytes.toString(Charsets.UTF_8.name())
    }
}
