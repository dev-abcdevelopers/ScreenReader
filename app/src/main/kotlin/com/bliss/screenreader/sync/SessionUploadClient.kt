@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.sync

import android.util.Base64
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SessionUploadClient {

    const val DEFAULT_SIGN_PATH = "/upload.php"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_RETRIES = 2
    private const val MAX_UPLOAD_BYTES = 25L * 1024L * 1024L
    private const val MAX_BODY_BYTES = 64 * 1024
    private const val BOUNDARY = "----BlissReaderUpload"
    private const val LINE_END = "\r\n"
    private const val JSON_MIME = "application/json"

    sealed class Result {
        data class Success(val Key: String, val ETag: String) : Result()
        data class Failure(val HttpCode: Int, val Message: String) : Result()
    }

    fun BuildStringToSign(
        MethodText: String,
        PathText: String,
        TimestampText: String,
        NonceText: String,
        FileKey: String
    ): String = "$MethodText\n$PathText\n$TimestampText\n$NonceText\n$FileKey"

    fun SignRequest(AppSecret: String, StringToSign: String): String {
        val MacRef = Mac.getInstance("HmacSHA256")
        MacRef.init(SecretKeySpec(AppSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val HashBytes = MacRef.doFinal(StringToSign.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(HashBytes, Base64.NO_WRAP)
    }

    fun ResolveSignPath(UploadUrl: String, SignPath: String): String {
        val TrimmedPath = SignPath.trim()
        if (TrimmedPath.isNotEmpty()) return TrimmedPath
        return try {
            URL(UploadUrl).path.orEmpty().ifEmpty { DEFAULT_SIGN_PATH }
        } catch (_: Exception) {
            DEFAULT_SIGN_PATH
        }
    }

    fun AlternateSignPath(UploadUrl: String, UsedPath: String): String {
        val UrlPath = try {
            URL(UploadUrl).path.orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (UrlPath.isNotEmpty() && UrlPath != UsedPath) return UrlPath
        if (UsedPath != DEFAULT_SIGN_PATH) return DEFAULT_SIGN_PATH
        return ""
    }

    fun IsAuthRejection(HttpCode: Int): Boolean = HttpCode == 401 || HttpCode == 403

    fun Fingerprint(ValueText: String): String {
        if (ValueText.isEmpty()) return "none"
        val DigestBytes = MessageDigest.getInstance("SHA-256")
            .digest(ValueText.toByteArray(Charsets.UTF_8))
        return DigestBytes.take(4).joinToString("") { ByteVal -> "%02x".format(ByteVal) }
    }

    fun Upload(
        UploadUrl: String,
        SignPath: String,
        AppKey: String,
        AppSecret: String,
        FileRef: File,
        FileKey: String,
        OnAttemptFailure: ((Int, Int, String) -> Unit)? = null
    ): Result {
        if (UploadUrl.isBlank() || AppKey.isBlank() || AppSecret.isBlank()) {
            return Result.Failure(0, "Upload credentials are missing")
        }
        if (FileKey.isBlank()) {
            return Result.Failure(0, "File key must not be blank")
        }
        if (!FileRef.exists() || !FileRef.canRead()) {
            return Result.Failure(0, "File is missing or unreadable")
        }
        if (FileRef.length() > MAX_UPLOAD_BYTES) {
            return Result.Failure(0, "File is larger than the 25 MB server limit")
        }

        var LastFailure = Result.Failure(0, "Upload failed")

        for (AttemptIndex in 0..MAX_RETRIES) {
            val AttemptResult = try {
                PerformUpload(
                    UploadUrl = UploadUrl.trim(),
                    SignPath = ResolveSignPath(UploadUrl = UploadUrl, SignPath = SignPath),
                    AppKey = AppKey,
                    AppSecret = AppSecret,
                    FileRef = FileRef,
                    FileKey = FileKey
                )
            } catch (ErrorRef: IOException) {
                Result.Failure(0, "Network error: ${ErrorRef.message.orEmpty()}")
            } catch (ErrorRef: Exception) {
                Result.Failure(0, ErrorRef.message.orEmpty().ifEmpty { "Upload failed" })
            }

            if (AttemptResult is Result.Success) return AttemptResult

            LastFailure = AttemptResult as Result.Failure
            if (!IsRetryable(HttpCode = LastFailure.HttpCode)) return LastFailure
            if (AttemptIndex < MAX_RETRIES) {
                OnAttemptFailure?.invoke(AttemptIndex, LastFailure.HttpCode, LastFailure.Message)
                try {
                    Thread.sleep(BackoffMillis(AttemptIndex = AttemptIndex))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return LastFailure
                }
            }
        }

        return LastFailure
    }

    fun IsRetryable(HttpCode: Int): Boolean = HttpCode == 0 || HttpCode in 500..599

    fun BackoffMillis(AttemptIndex: Int): Long = 1000L shl AttemptIndex

    private fun PerformUpload(
        UploadUrl: String,
        SignPath: String,
        AppKey: String,
        AppSecret: String,
        FileRef: File,
        FileKey: String
    ): Result {
        val TimestampText = (System.currentTimeMillis() / 1000L).toString()
        val NonceText = NewNonce()
        val SignatureText = SignRequest(
            AppSecret = AppSecret,
            StringToSign = BuildStringToSign(
                MethodText = "POST",
                PathText = SignPath,
                TimestampText = TimestampText,
                NonceText = NonceText,
                FileKey = FileKey
            )
        )

        val PreambleBytes = BuildPreamble(
            FileKey = FileKey,
            FileName = UploadFileName(FileKey = FileKey)
        )
        val EpilogueBytes = "$LINE_END--$BOUNDARY--$LINE_END".toByteArray(Charsets.UTF_8)
        val ContentLength = PreambleBytes.size + FileRef.length() + EpilogueBytes.size

        var ConnectionRef: HttpURLConnection? = null
        try {
            ConnectionRef = (URL(UploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
                setRequestProperty("X-App-Key", AppKey)
                setRequestProperty("X-Timestamp", TimestampText)
                setRequestProperty("X-Nonce", NonceText)
                setRequestProperty("X-Signature", SignatureText)
                setFixedLengthStreamingMode(ContentLength)
            }

            BufferedOutputStream(ConnectionRef.outputStream).use { OutputRef ->
                OutputRef.write(PreambleBytes)
                FileRef.inputStream().use { FileStream -> FileStream.copyTo(OutputRef) }
                OutputRef.write(EpilogueBytes)
                OutputRef.flush()
            }

            val StatusCode = ConnectionRef.responseCode
            val BodyText = if (StatusCode in 200..299) {
                ReadBody(StreamRef = ConnectionRef.inputStream)
            } else {
                ReadBody(StreamRef = ConnectionRef.errorStream ?: ConnectionRef.inputStream)
            }

            if (StatusCode !in 200..299) {
                return Result.Failure(StatusCode, ExtractError(BodyText = BodyText, StatusCode = StatusCode))
            }

            return ParseSuccess(BodyText = BodyText, FileKey = FileKey)
        } finally {
            try {
                ConnectionRef?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun UploadFileName(FileKey: String): String =
        FileKey.substringAfterLast('/').ifEmpty { FileKey }

    private fun BuildPreamble(FileKey: String, FileName: String): ByteArray {
        val BuilderRef = StringBuilder()
        BuilderRef.append("--").append(BOUNDARY).append(LINE_END)
        BuilderRef.append("Content-Disposition: form-data; name=\"file_key\"").append(LINE_END)
        BuilderRef.append(LINE_END)
        BuilderRef.append(FileKey).append(LINE_END)
        BuilderRef.append("--").append(BOUNDARY).append(LINE_END)
        BuilderRef.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
            .append(FileName).append("\"").append(LINE_END)
        BuilderRef.append("Content-Type: ").append(JSON_MIME).append(LINE_END)
        BuilderRef.append(LINE_END)
        return BuilderRef.toString().toByteArray(Charsets.UTF_8)
    }

    private fun ParseSuccess(BodyText: String, FileKey: String): Result {
        return try {
            val JsonObj = JSONObject(BodyText)
            Result.Success(
                Key = JsonObj.optString("key", FileKey).ifEmpty { FileKey },
                ETag = if (JsonObj.isNull("etag")) "" else JsonObj.optString("etag", "")
            )
        } catch (_: Exception) {
            Result.Success(Key = FileKey, ETag = "")
        }
    }

    private fun ExtractError(BodyText: String, StatusCode: Int): String {
        val ParsedError = try {
            JSONObject(BodyText).optString("error", "")
        } catch (_: Exception) {
            ""
        }
        if (ParsedError.isNotEmpty()) return ParsedError
        if (BodyText.isNotBlank()) return BodyText.take(200)
        return "Server returned HTTP $StatusCode"
    }

    private fun NewNonce(): String {
        val NonceBytes = ByteArray(16)
        SecureRandom().nextBytes(NonceBytes)
        return NonceBytes.joinToString("") { ByteVal -> "%02x".format(ByteVal) }
    }

    private fun ReadBody(StreamRef: InputStream): String {
        val BufferRef = ByteArray(4096)
        val CollectedBytes = ByteArrayOutputStream()
        StreamRef.use { OpenStream ->
            while (CollectedBytes.size() < MAX_BODY_BYTES) {
                val ReadCount = OpenStream.read(BufferRef)
                if (ReadCount <= 0) break
                CollectedBytes.write(BufferRef, 0, ReadCount)
            }
        }
        return CollectedBytes.toString(Charsets.UTF_8.name()).trim()
    }
}
