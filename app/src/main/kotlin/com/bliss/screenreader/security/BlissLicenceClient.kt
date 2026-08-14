@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.content.Context
import com.bliss.screenreader.BuildConfig
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object BlissLicenceClient {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_BODY_BYTES = 64 * 1024

    private const val BROKEN_ANDROID_ID = "9774d56d682e549c"
    private const val MIN_ID_LENGTH = 8

    sealed class Verdict {
        object Valid : Verdict()
        object NotLicensed : Verdict()
        object Unreachable : Verdict()
        object NoDeviceId : Verdict()
    }

    fun BuildUrl(BaseUrl: String, DeviceIdText: String, ArgsText: String): String =
        "$BaseUrl?message=format!$DeviceIdText!$ArgsText"

    fun IsUsableDeviceId(DeviceIdText: String): Boolean =
        DeviceIdText.length >= MIN_ID_LENGTH && DeviceIdText != BROKEN_ANDROID_ID

    fun JudgeBody(BodyText: String): Verdict =
        if (BodyText.isNotEmpty() && !BodyText.contains("Error")) {
            Verdict.Valid
        } else {
            Verdict.NotLicensed
        }

    fun Check(ContextRef: Context): Verdict {
        val DeviceIdText = DeviceIdentity.RegistrationId(ContextRef = ContextRef)
        if (!IsUsableDeviceId(DeviceIdText = DeviceIdText)) return Verdict.NoDeviceId

        val UrlText = BuildUrl(
            BaseUrl = BuildConfig.LICENCE_URL,
            DeviceIdText = DeviceIdText,
            ArgsText = BuildConfig.LICENCE_ARGS
        )

        var ConnectionRef: HttpURLConnection? = null
        return try {
            ConnectionRef = (URL(UrlText).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val StatusCode = ConnectionRef.responseCode
            if (StatusCode !in 200..299) {
                Verdict.Unreachable
            } else {
                JudgeBody(BodyText = ReadBody(StreamRef = ConnectionRef.inputStream))
            }
        } catch (_: Exception) {
            Verdict.Unreachable
        } finally {
            try {
                ConnectionRef?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun ReadBody(StreamRef: InputStream): String {
        val BufferRef = ByteArray(4096)
        val CollectedBytes = java.io.ByteArrayOutputStream()
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
