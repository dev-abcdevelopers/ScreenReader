@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object DeviceIdentity {
    private const val ID_BYTES = 8

    @Volatile
    private var CachedBytes: ByteArray? = null

    @Volatile
    private var CachedRegistrationId: String? = null

    @SuppressLint("HardwareIds")
    private fun AndroidId(ContextRef: Context): String = Settings.Secure.getString(
        ContextRef.contentResolver,
        Settings.Secure.ANDROID_ID
    ).orEmpty().trim()

    fun RawBytes(ContextRef: Context): ByteArray {
        CachedBytes?.let { return it.copyOf() }
        synchronized(this) {
            CachedBytes?.let { return it.copyOf() }

            val AndroidIdText = AndroidId(ContextRef = ContextRef)

            val SourceText = AndroidIdText + "|" + ContextRef.packageName
            val DigestBytes = MessageDigest.getInstance("SHA-256")
                .digest(SourceText.toByteArray(Charsets.UTF_8))
            val ResultBytes = DigestBytes.copyOf(ID_BYTES)
            CachedBytes = ResultBytes
            return ResultBytes.copyOf()
        }
    }

    fun DisplayId(ContextRef: Context): String {
        val Builder = StringBuilder()
        for ((Index, ByteVal) in RawBytes(ContextRef = ContextRef).withIndex()) {
            if (Index > 0 && Index % 2 == 0) Builder.append('-')
            Builder.append(String.format("%02X", ByteVal))
        }
        return Builder.toString()
    }

    fun Matches(ContextRef: Context, CandidateBytes: ByteArray): Boolean {
        val OwnBytes = RawBytes(ContextRef = ContextRef)
        if (CandidateBytes.size != OwnBytes.size) return false
        var Difference = 0
        for (Index in OwnBytes.indices) {
            Difference = Difference or (OwnBytes[Index].toInt() xor CandidateBytes[Index].toInt())
        }
        return Difference == 0
    }

    fun RegistrationId(ContextRef: Context): String {
        CachedRegistrationId?.let { return it }
        synchronized(this) {
            CachedRegistrationId?.let { return it }
            val ResultText = AndroidId(ContextRef = ContextRef).lowercase()
            CachedRegistrationId = ResultText
            return ResultText
        }
    }

    fun GroupForDisplay(IdText: String): String {
        if (IdText.isEmpty()) return ""
        val Builder = StringBuilder()
        for ((Index, CharVal) in IdText.uppercase().withIndex()) {
            if (Index > 0 && Index % 4 == 0) Builder.append('-')
            Builder.append(CharVal)
        }
        return Builder.toString()
    }
}
