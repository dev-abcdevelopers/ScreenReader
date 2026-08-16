@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bliss.screenreader.BuildConfig
import com.bliss.screenreader.service.CaptureDiagnostics
import java.io.File
import java.util.concurrent.Executors

object SessionUploader {

    private val WorkerRef = Executors.newSingleThreadExecutor()
    private val MainHandler = Handler(Looper.getMainLooper())

    sealed class Outcome {
        data class Uploaded(
            val Key: String,
            val RecordCount: Int,
            val ElapsedMs: Long
        ) : Outcome()

        data class Failed(val HttpCode: Int, val Message: String) : Outcome()
        object Cancelled : Outcome()
        object NotConfigured : Outcome()
        object NothingToSend : Outcome()
    }

    sealed class Progress {
        object Packing : Progress()

        data class Ready(
            val ObjectKey: String,
            val PolicyCount: Int,
            val RenewalCount: Int,
            val ServicingCount: Int,
            val GapCount: Int,
            val TotalBytes: Long
        ) : Progress()

        data class Sending(val SentBytes: Long, val TotalBytes: Long) : Progress()
        object Waiting : Progress()
    }

    @Volatile
    private var CancelRequested = false

    fun Cancel() {
        CancelRequested = true
    }

    fun IsEnabled(): Boolean = BuildConfig.UPLOAD_ENABLED

    fun IsConfigured(): Boolean =
        BuildConfig.UPLOAD_URL.isNotBlank() &&
            BuildConfig.UPLOAD_APP_KEY.isNotBlank() &&
            BuildConfig.UPLOAD_APP_SECRET.isNotBlank()

    fun UploadSession(
        ContextRef: Context,
        SessionId: String,
        AgencyCode: String,
        OnProgress: (Progress) -> Unit = {},
        OnResult: (Outcome) -> Unit
    ) {
        val AppContext = ContextRef.applicationContext

        if (!IsEnabled() || !IsConfigured()) {
            LogUpload(
                ContextRef = AppContext,
                SessionId = SessionId,
                EventName = "UPLOAD_SKIPPED",
                MessageText = "session=$SessionId reason=not_configured " +
                    "url=${BuildConfig.UPLOAD_URL.isNotBlank()} " +
                    "key=${BuildConfig.UPLOAD_APP_KEY.isNotBlank()} " +
                    "secret=${BuildConfig.UPLOAD_APP_SECRET.isNotBlank()}"
            )
            OnResult(Outcome.NotConfigured)
            return
        }
        if (SessionId.isBlank()) {
            OnResult(Outcome.NothingToSend)
            return
        }

        CancelRequested = false
        WorkerRef.execute {
            val ResultOutcome = RunUpload(
                ContextRef = AppContext,
                SessionId = SessionId,
                AgencyCode = AgencyCode,
                OnProgress = { ProgressVal -> MainHandler.post { OnProgress(ProgressVal) } }
            )
            MainHandler.post { OnResult(ResultOutcome) }
        }
    }

    private fun RunUpload(
        ContextRef: Context,
        SessionId: String,
        AgencyCode: String,
        OnProgress: (Progress) -> Unit
    ): Outcome {
        return try {
            OnProgress(Progress.Packing)
            val PayloadObj = SessionPayloadBuilder.Build(
                ContextRef = ContextRef,
                SessionId = SessionId,
                AgencyCode = AgencyCode
            )
            if (PayloadObj.TotalRecordCount == 0) {
                LogUpload(
                    ContextRef = ContextRef,
                    SessionId = SessionId,
                    EventName = "UPLOAD_SKIPPED",
                    MessageText = "session=$SessionId agency=${PayloadObj.AgencyCode} reason=no_records"
                )
                return Outcome.NothingToSend
            }

            val PayloadFile = SessionPayloadBuilder.WriteJsonFile(
                ContextRef = ContextRef,
                PayloadObj = PayloadObj
            )
            val ObjectKey = SessionPayloadBuilder.ObjectKeyFor(AgencyCode = PayloadObj.AgencyCode)
            val PayloadBytes = PayloadFile.length()

            OnProgress(
                Progress.Ready(
                    ObjectKey = ObjectKey,
                    PolicyCount = PayloadObj.Policies.size,
                    RenewalCount = PayloadObj.Renewals.size,
                    ServicingCount = PayloadObj.Servicing.size,
                    GapCount = PayloadObj.Gaps.size,
                    TotalBytes = PayloadBytes
                )
            )

            val PrimaryPath = SessionUploadClient.ResolveSignPath(
                UploadUrl = BuildConfig.UPLOAD_URL,
                SignPath = BuildConfig.UPLOAD_SIGN_PATH
            )

            LogUpload(
                ContextRef = ContextRef,
                SessionId = SessionId,
                EventName = "UPLOAD_START",
                MessageText = "session=$SessionId agency=${PayloadObj.AgencyCode} key=$ObjectKey " +
                    "policies=${PayloadObj.Policies.size} renewals=${PayloadObj.Renewals.size} " +
                    "servicing=${PayloadObj.Servicing.size} gaps=${PayloadObj.Gaps.size} " +
                    "bytes=$PayloadBytes url=${BuildConfig.UPLOAD_URL} " +
                    "filename=${SessionUploadClient.UploadFileName(FileKey = ObjectKey)} " +
                    "signpath=$PrimaryPath build=${BuildConfig.BUILD_TYPE} " +
                    "keylen=${BuildConfig.UPLOAD_APP_KEY.length} " +
                    "keyfp=${SessionUploadClient.Fingerprint(ValueText = BuildConfig.UPLOAD_APP_KEY)} " +
                    "secretlen=${BuildConfig.UPLOAD_APP_SECRET.length} " +
                    "secretfp=${SessionUploadClient.Fingerprint(ValueText = BuildConfig.UPLOAD_APP_SECRET)} " +
                    "saved=${PayloadFile.absolutePath}"
            )

            val StartedAt = System.currentTimeMillis()
            var SignPathUsed = PrimaryPath
            var UploadOutcome = AttemptUpload(
                ContextRef = ContextRef,
                SessionId = SessionId,
                ObjectKey = ObjectKey,
                SignPathVal = PrimaryPath,
                PayloadFile = PayloadFile,
                OnProgress = OnProgress
            )

            val AuthFailure = UploadOutcome as? SessionUploadClient.Result.Failure
            if (AuthFailure != null &&
                SessionUploadClient.IsAuthRejection(HttpCode = AuthFailure.HttpCode)
            ) {
                val AlternatePath = SessionUploadClient.AlternateSignPath(
                    UploadUrl = BuildConfig.UPLOAD_URL,
                    UsedPath = PrimaryPath
                )
                if (AlternatePath.isNotEmpty()) {
                    LogUpload(
                        ContextRef = ContextRef,
                        SessionId = SessionId,
                        EventName = "UPLOAD_SIGNPATH_RETRY",
                        MessageText = "session=$SessionId key=$ObjectKey " +
                            "rejected=$PrimaryPath retrying=$AlternatePath " +
                            "http=${AuthFailure.HttpCode} error=${AuthFailure.Message}"
                    )
                    SignPathUsed = AlternatePath
                    UploadOutcome = AttemptUpload(
                        ContextRef = ContextRef,
                        SessionId = SessionId,
                        ObjectKey = ObjectKey,
                        SignPathVal = AlternatePath,
                        PayloadFile = PayloadFile,
                        OnProgress = OnProgress
                    )
                }
            }
            val ElapsedMs = System.currentTimeMillis() - StartedAt

            when (UploadOutcome) {
                is SessionUploadClient.Result.Success -> {
                    LogUpload(
                        ContextRef = ContextRef,
                        SessionId = SessionId,
                        EventName = "UPLOAD_OK",
                        MessageText = "session=$SessionId key=${UploadOutcome.Key} " +
                            "etag=${UploadOutcome.ETag} records=${PayloadObj.TotalRecordCount} " +
                            "bytes=$PayloadBytes signpath=$SignPathUsed " +
                            "ms=$ElapsedMs"
                    )
                    Outcome.Uploaded(
                        Key = UploadOutcome.Key,
                        RecordCount = PayloadObj.TotalRecordCount,
                        ElapsedMs = ElapsedMs
                    )
                }

                is SessionUploadClient.Result.Failure -> {
                    LogUpload(
                        ContextRef = ContextRef,
                        SessionId = SessionId,
                        EventName = "UPLOAD_FAILED",
                        MessageText = "session=$SessionId key=$ObjectKey " +
                            "http=${UploadOutcome.HttpCode} bytes=$PayloadBytes " +
                            "signpath=$SignPathUsed ms=$ElapsedMs " +
                            "error=${UploadOutcome.Message}"
                    )
                    Outcome.Failed(
                        HttpCode = UploadOutcome.HttpCode,
                        Message = UploadOutcome.Message
                    )
                }

                SessionUploadClient.Result.Cancelled -> {
                    LogUpload(
                        ContextRef = ContextRef,
                        SessionId = SessionId,
                        EventName = "UPLOAD_CANCELLED",
                        MessageText = "session=$SessionId key=$ObjectKey bytes=$PayloadBytes " +
                            "signpath=$SignPathUsed ms=$ElapsedMs"
                    )
                    Outcome.Cancelled
                }
            }
        } catch (ErrorRef: Exception) {
            LogUpload(
                ContextRef = ContextRef,
                SessionId = SessionId,
                EventName = "UPLOAD_FAILED",
                MessageText = "session=$SessionId " +
                    "${ErrorRef.javaClass.simpleName}: ${ErrorRef.message.orEmpty()}"
            )
            Outcome.Failed(
                HttpCode = 0,
                Message = ErrorRef.message.orEmpty().ifEmpty { "Upload failed" }
            )
        }
    }

    private fun AttemptUpload(
        ContextRef: Context,
        SessionId: String,
        ObjectKey: String,
        SignPathVal: String,
        PayloadFile: File,
        OnProgress: (Progress) -> Unit
    ): SessionUploadClient.Result = SessionUploadClient.Upload(
        UploadUrl = BuildConfig.UPLOAD_URL,
        SignPath = SignPathVal,
        AppKey = BuildConfig.UPLOAD_APP_KEY,
        AppSecret = BuildConfig.UPLOAD_APP_SECRET,
        FileRef = PayloadFile,
        FileKey = ObjectKey,
        OnAttemptFailure = { AttemptIndex, HttpCode, MessageVal ->
            LogUpload(
                ContextRef = ContextRef,
                SessionId = SessionId,
                EventName = "UPLOAD_RETRY",
                MessageText = "session=$SessionId key=$ObjectKey signpath=$SignPathVal " +
                    "attempt=${AttemptIndex + 1} http=$HttpCode error=$MessageVal"
            )
        },
        OnBytesSent = { SentBytes, TotalBytes ->
            OnProgress(Progress.Sending(SentBytes = SentBytes, TotalBytes = TotalBytes))
        },
        OnBodySent = { OnProgress(Progress.Waiting) },
        ShouldCancel = { CancelRequested }
    )

    private fun LogUpload(
        ContextRef: Context,
        SessionId: String,
        EventName: String,
        MessageText: String
    ) {
        if (SessionId.isBlank()) return
        try {
            CaptureDiagnostics.LogForSession(
                ContextObj = ContextRef,
                SessionId = SessionId,
                EventName = EventName,
                MessageText = MessageText
            )
        } catch (_: Exception) {
        }
    }
}
