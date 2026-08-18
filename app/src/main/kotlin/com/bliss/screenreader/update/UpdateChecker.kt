@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.pm.PackageInfoCompat
import com.bliss.screenreader.BuildConfig
import java.io.File
import java.util.concurrent.Executors

object UpdateChecker {

    private val WorkerRef = Executors.newSingleThreadExecutor()
    private val MainHandler = Handler(Looper.getMainLooper())

    private const val PREFS_NAME = "app_update"
    private const val KEY_LAST_CHECK = "last_check_at"
    private const val KEY_FORCE_PENDING = "force_pending"
    private const val KEY_FORCE_APP_NAME = "force_app_name"
    private const val KEY_FORCE_VERSION_CODE = "force_version_code"
    private const val KEY_FORCE_VERSION_NAME = "force_version_name"
    private const val KEY_FORCE_DOWNLOAD_URL = "force_download_url"
    private const val KEY_FORCE_NOTES = "force_notes"
    private const val NOTE_SEPARATOR = "\n"
    private const val THROTTLE_MS = 6L * 60L * 60L * 1000L
    private const val CACHE_FOLDER = "updates"
    private const val APK_SUFFIX = ".apk"

    sealed class Outcome {
        data class Available(
            val ManifestObj: UpdateManifest,
            val SizeBytes: Long
        ) : Outcome()

        object UpToDate : Outcome()
        object NotConfigured : Outcome()
        object Throttled : Outcome()
        data class Failed(val HttpCode: Int, val MessageText: String) : Outcome()
    }

    sealed class DownloadState {
        data class Running(val ReceivedBytes: Long, val TotalBytes: Long) : DownloadState()
        data class Done(val FileRef: File) : DownloadState()
        data class Failed(val HttpCode: Int, val MessageText: String) : DownloadState()
        object Cancelled : DownloadState()
    }

    @Volatile
    private var CancelRequested = false

    @Volatile
    private var DownloadRunning = false

    fun IsConfigured(): Boolean = BuildConfig.UPDATE_URL.isNotBlank()

    fun IsDownloading(): Boolean = DownloadRunning

    fun CancelDownload() {
        CancelRequested = true
    }

    fun LocalVersionCode(ContextRef: Context): Int {
        return try {
            val InfoRef = ContextRef.packageManager.getPackageInfo(ContextRef.packageName, 0)
            PackageInfoCompat.getLongVersionCode(InfoRef).toInt()
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    fun LocalVersionName(ContextRef: Context): String {
        return try {
            ContextRef.packageManager.getPackageInfo(ContextRef.packageName, 0)
                .versionName.orEmpty().ifBlank { BuildConfig.VERSION_NAME }
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    fun Check(
        ContextRef: Context,
        ManualCheck: Boolean,
        OnResult: (Outcome) -> Unit
    ) {
        val AppContext = ContextRef.applicationContext

        if (!IsConfigured()) {
            OnResult(Outcome.NotConfigured)
            return
        }
        if (!ManualCheck && IsThrottled(ContextRef = AppContext)) {
            OnResult(Outcome.Throttled)
            return
        }

        WorkerRef.execute {
            val OutcomeVal = RunCheck(ContextRef = AppContext)
            MainHandler.post { OnResult(OutcomeVal) }
        }
    }

    fun Download(
        ContextRef: Context,
        ManifestObj: UpdateManifest,
        OnState: (DownloadState) -> Unit
    ) {
        val AppContext = ContextRef.applicationContext
        CancelRequested = false
        DownloadRunning = true

        WorkerRef.execute {
            val TargetFile = ApkFileFor(ContextRef = AppContext, ManifestObj = ManifestObj)
            ClearStaleApks(ContextRef = AppContext, KeepFile = TargetFile)

            val ResultVal = UpdateClient.DownloadApk(
                DownloadUrl = ManifestObj.DownloadUrl,
                TargetFile = TargetFile,
                OnBytes = { ReceivedBytes, TotalBytes ->
                    MainHandler.post {
                        OnState(
                            DownloadState.Running(
                                ReceivedBytes = ReceivedBytes,
                                TotalBytes = TotalBytes
                            )
                        )
                    }
                },
                ShouldCancel = { CancelRequested }
            )

            DownloadRunning = false
            val StateVal = when (ResultVal) {
                is UpdateClient.DownloadResult.Ok -> DownloadState.Done(FileRef = ResultVal.FileRef)
                is UpdateClient.DownloadResult.Failure -> DownloadState.Failed(
                    HttpCode = ResultVal.HttpCode,
                    MessageText = ResultVal.MessageText
                )

                UpdateClient.DownloadResult.Cancelled -> DownloadState.Cancelled
            }
            MainHandler.post { OnState(StateVal) }
        }
    }

    fun ApkFileFor(ContextRef: Context, ManifestObj: UpdateManifest): File {
        val FolderRef = File(ContextRef.cacheDir, CACHE_FOLDER)
        FolderRef.mkdirs()
        val StampText = ManifestObj.VersionName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { ManifestObj.VersionCode.toString() }
        return File(FolderRef, "ScreenReader-$StampText$APK_SUFFIX")
    }

    fun ClearStaleApks(ContextRef: Context, KeepFile: File?) {
        val FolderRef = File(ContextRef.cacheDir, CACHE_FOLDER)
        val FileList = FolderRef.listFiles() ?: return
        for (FileRef in FileList) {
            if (!FileRef.name.endsWith(APK_SUFFIX)) continue
            if (KeepFile != null && FileRef.absolutePath == KeepFile.absolutePath) continue
            FileRef.delete()
        }
    }

    private fun RunCheck(ContextRef: Context): Outcome {
        val ManifestResult = UpdateClient.FetchManifest(ManifestUrl = BuildConfig.UPDATE_URL)
        if (ManifestResult is UpdateClient.ManifestResult.Failure) {
            val StoredManifest = StoredForcedManifest(ContextRef = ContextRef)
            if (StoredManifest != null) {
                return Outcome.Available(ManifestObj = StoredManifest, SizeBytes = 0L)
            }
            return Outcome.Failed(
                HttpCode = ManifestResult.HttpCode,
                MessageText = ManifestResult.MessageText
            )
        }

        val ManifestObj = (ManifestResult as UpdateClient.ManifestResult.Ok).ManifestObj
        MarkChecked(ContextRef = ContextRef)

        val IsNewer = UpdateVersion.IsRemoteNewer(
            LocalCode = LocalVersionCode(ContextRef = ContextRef),
            LocalName = LocalVersionName(ContextRef = ContextRef),
            RemoteCode = ManifestObj.VersionCode,
            RemoteName = ManifestObj.VersionName
        )
        if (!IsNewer) {
            ClearForcedUpdate(ContextRef = ContextRef)
            return Outcome.UpToDate
        }

        if (ManifestObj.ForceUpdate) {
            RememberForcedUpdate(ContextRef = ContextRef, ManifestObj = ManifestObj)
        } else {
            ClearForcedUpdate(ContextRef = ContextRef)
        }

        val SizeBytes = UpdateClient.ProbeSize(DownloadUrl = ManifestObj.DownloadUrl)
        return Outcome.Available(ManifestObj = ManifestObj, SizeBytes = SizeBytes)
    }

    fun IsForcePending(ContextRef: Context): Boolean =
        ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FORCE_PENDING, false)

    private fun RememberForcedUpdate(ContextRef: Context, ManifestObj: UpdateManifest) {
        ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FORCE_PENDING, true)
            .putString(KEY_FORCE_APP_NAME, ManifestObj.AppName)
            .putInt(KEY_FORCE_VERSION_CODE, ManifestObj.VersionCode)
            .putString(KEY_FORCE_VERSION_NAME, ManifestObj.VersionName)
            .putString(KEY_FORCE_DOWNLOAD_URL, ManifestObj.DownloadUrl)
            .putString(KEY_FORCE_NOTES, ManifestObj.ChangeLog.joinToString(NOTE_SEPARATOR))
            .apply()
    }

    private fun ClearForcedUpdate(ContextRef: Context) {
        ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_FORCE_PENDING)
            .remove(KEY_FORCE_APP_NAME)
            .remove(KEY_FORCE_VERSION_CODE)
            .remove(KEY_FORCE_VERSION_NAME)
            .remove(KEY_FORCE_DOWNLOAD_URL)
            .remove(KEY_FORCE_NOTES)
            .apply()
    }

    private fun StoredForcedManifest(ContextRef: Context): UpdateManifest? {
        if (!IsForcePending(ContextRef = ContextRef)) return null

        val PrefsRef = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val DownloadUrl = PrefsRef.getString(KEY_FORCE_DOWNLOAD_URL, "").orEmpty()
        if (DownloadUrl.isBlank()) return null

        val StoredManifest = UpdateManifest(
            AppName = PrefsRef.getString(KEY_FORCE_APP_NAME, "").orEmpty(),
            VersionCode = PrefsRef.getInt(KEY_FORCE_VERSION_CODE, 0),
            VersionName = PrefsRef.getString(KEY_FORCE_VERSION_NAME, "").orEmpty(),
            DownloadUrl = DownloadUrl,
            ForceUpdate = true,
            ChangeLog = PrefsRef.getString(KEY_FORCE_NOTES, "").orEmpty()
                .split(NOTE_SEPARATOR)
                .filter { NoteText -> NoteText.isNotBlank() }
        )

        val IsStillNewer = UpdateVersion.IsRemoteNewer(
            LocalCode = LocalVersionCode(ContextRef = ContextRef),
            LocalName = LocalVersionName(ContextRef = ContextRef),
            RemoteCode = StoredManifest.VersionCode,
            RemoteName = StoredManifest.VersionName
        )
        if (!IsStillNewer) {
            ClearForcedUpdate(ContextRef = ContextRef)
            return null
        }
        return StoredManifest
    }

    private fun IsThrottled(ContextRef: Context): Boolean {
        if (IsForcePending(ContextRef = ContextRef)) return false

        val PrefsRef = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val LastCheckAt = PrefsRef.getLong(KEY_LAST_CHECK, 0L)
        if (LastCheckAt <= 0L) return false
        val ElapsedMs = System.currentTimeMillis() - LastCheckAt
        return ElapsedMs in 0 until THROTTLE_MS
    }

    private fun MarkChecked(ContextRef: Context) {
        ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .apply()
    }
}
