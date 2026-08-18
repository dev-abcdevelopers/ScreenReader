@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateClient {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val HEAD_TIMEOUT_MS = 10_000
    private const val MAX_REDIRECTS = 5
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_APK_BYTES = 200L * 1024L * 1024L
    private const val COPY_BUFFER_BYTES = 32 * 1024
    private const val PROGRESS_INTERVAL_MS = 120L
    private const val USER_AGENT = "ScreenReaderUpdater/1"

    sealed class ManifestResult {
        data class Ok(val ManifestObj: UpdateManifest) : ManifestResult()
        data class Failure(val HttpCode: Int, val MessageText: String) : ManifestResult()
    }

    sealed class DownloadResult {
        data class Ok(val FileRef: File) : DownloadResult()
        data class Failure(val HttpCode: Int, val MessageText: String) : DownloadResult()
        object Cancelled : DownloadResult()
    }

    private class CancelledDownloadException : IOException("Cancelled")

    fun FetchManifest(ManifestUrl: String): ManifestResult {
        if (ManifestUrl.isBlank()) return ManifestResult.Failure(0, "No update URL configured")

        var ConnectionRef: HttpURLConnection? = null
        return try {
            ConnectionRef = OpenFollowing(
                UrlText = ManifestUrl,
                MethodText = "GET",
                ReadTimeoutMs = READ_TIMEOUT_MS
            )
            val CodeVal = ConnectionRef.responseCode
            if (CodeVal !in 200..299) {
                ManifestResult.Failure(CodeVal, "Server returned HTTP $CodeVal")
            } else {
                val BodyText = ReadBody(StreamRef = ConnectionRef.inputStream)
                val ManifestObj = UpdateManifest.Parse(JsonText = BodyText)
                if (ManifestObj == null) ManifestResult.Failure(CodeVal, "Update file is not valid")
                else ManifestResult.Ok(ManifestObj)
            }
        } catch (ErrorRef: Exception) {
            ManifestResult.Failure(0, ErrorRef.message.orEmpty().ifEmpty { "Network error" })
        } finally {
            ConnectionRef?.disconnect()
        }
    }

    fun ProbeSize(DownloadUrl: String): Long {
        if (DownloadUrl.isBlank()) return 0L
        var ConnectionRef: HttpURLConnection? = null
        return try {
            ConnectionRef = OpenFollowing(
                UrlText = DownloadUrl,
                MethodText = "HEAD",
                ReadTimeoutMs = HEAD_TIMEOUT_MS
            )
            val CodeVal = ConnectionRef.responseCode
            if (CodeVal !in 200..299) 0L else ContentLengthOf(ConnectionRef = ConnectionRef)
        } catch (_: Exception) {
            0L
        } finally {
            ConnectionRef?.disconnect()
        }
    }

    fun DownloadApk(
        DownloadUrl: String,
        TargetFile: File,
        OnBytes: (Long, Long) -> Unit = { _, _ -> },
        ShouldCancel: () -> Boolean = { false }
    ): DownloadResult {
        if (DownloadUrl.isBlank()) return DownloadResult.Failure(0, "No download URL")

        var ConnectionRef: HttpURLConnection? = null
        return try {
            ConnectionRef = OpenFollowing(
                UrlText = DownloadUrl,
                MethodText = "GET",
                ReadTimeoutMs = READ_TIMEOUT_MS
            )
            val CodeVal = ConnectionRef.responseCode
            if (CodeVal !in 200..299) {
                DownloadResult.Failure(CodeVal, "Server returned HTTP $CodeVal")
            } else {
                val TotalBytes = ContentLengthOf(ConnectionRef = ConnectionRef)
                if (TotalBytes > MAX_APK_BYTES) {
                    DownloadResult.Failure(CodeVal, "Download is unexpectedly large")
                } else {
                    TargetFile.parentFile?.mkdirs()
                    if (TargetFile.exists()) TargetFile.delete()
                    val WrittenBytes = CopyStream(
                        StreamRef = ConnectionRef.inputStream,
                        TargetFile = TargetFile,
                        TotalBytes = TotalBytes,
                        OnBytes = OnBytes,
                        ShouldCancel = ShouldCancel
                    )
                    if (WrittenBytes <= 0L) {
                        TargetFile.delete()
                        DownloadResult.Failure(CodeVal, "Downloaded file was empty")
                    } else {
                        OnBytes(WrittenBytes, if (TotalBytes > 0L) TotalBytes else WrittenBytes)
                        DownloadResult.Ok(TargetFile)
                    }
                }
            }
        } catch (_: CancelledDownloadException) {
            TargetFile.delete()
            DownloadResult.Cancelled
        } catch (ErrorRef: Exception) {
            TargetFile.delete()
            DownloadResult.Failure(0, ErrorRef.message.orEmpty().ifEmpty { "Download failed" })
        } finally {
            ConnectionRef?.disconnect()
        }
    }

    private fun CopyStream(
        StreamRef: InputStream,
        TargetFile: File,
        TotalBytes: Long,
        OnBytes: (Long, Long) -> Unit,
        ShouldCancel: () -> Boolean
    ): Long {
        var WrittenBytes = 0L
        var LastReportAt = 0L
        val BufferRef = ByteArray(COPY_BUFFER_BYTES)

        StreamRef.use { SourceStream ->
            FileOutputStream(TargetFile).use { TargetStream ->
                while (true) {
                    if (ShouldCancel()) throw CancelledDownloadException()
                    val ReadCount = SourceStream.read(BufferRef)
                    if (ReadCount <= 0) break
                    TargetStream.write(BufferRef, 0, ReadCount)
                    WrittenBytes += ReadCount

                    val NowMs = System.currentTimeMillis()
                    if (NowMs - LastReportAt >= PROGRESS_INTERVAL_MS) {
                        LastReportAt = NowMs
                        OnBytes(WrittenBytes, TotalBytes)
                    }
                }
                TargetStream.flush()
            }
        }
        return WrittenBytes
    }

    private fun ContentLengthOf(ConnectionRef: HttpURLConnection): Long {
        val HeaderText = ConnectionRef.getHeaderField("Content-Length").orEmpty()
        val ParsedVal = HeaderText.trim().toLongOrNull() ?: -1L
        return if (ParsedVal > 0L) ParsedVal else 0L
    }

    private fun ReadBody(StreamRef: InputStream): String {
        StreamRef.use { SourceStream ->
            val BufferRef = ByteArray(COPY_BUFFER_BYTES)
            val CollectedBytes = java.io.ByteArrayOutputStream()
            while (CollectedBytes.size() < MAX_MANIFEST_BYTES) {
                val ReadCount = SourceStream.read(BufferRef)
                if (ReadCount <= 0) break
                CollectedBytes.write(BufferRef, 0, ReadCount)
            }
            return CollectedBytes.toString(Charsets.UTF_8.name())
        }
    }

    private fun OpenFollowing(
        UrlText: String,
        MethodText: String,
        ReadTimeoutMs: Int
    ): HttpURLConnection {
        var CurrentUrl = UrlText
        var RedirectCount = 0

        while (true) {
            val ConnectionRef = (URL(CurrentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = MethodText
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = ReadTimeoutMs
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Cache-Control", "no-cache")
            }

            val CodeVal = ConnectionRef.responseCode
            val IsRedirect = CodeVal == HttpURLConnection.HTTP_MOVED_PERM ||
                CodeVal == HttpURLConnection.HTTP_MOVED_TEMP ||
                CodeVal == HttpURLConnection.HTTP_SEE_OTHER ||
                CodeVal == 307 ||
                CodeVal == 308

            if (!IsRedirect || RedirectCount >= MAX_REDIRECTS) return ConnectionRef

            val LocationText = ConnectionRef.getHeaderField("Location").orEmpty()
            ConnectionRef.disconnect()
            if (LocationText.isBlank()) throw IOException("Redirect without a target")

            CurrentUrl = URL(URL(CurrentUrl), LocationText).toString()
            RedirectCount += 1
        }
    }
}
