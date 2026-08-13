@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {
    const val STEP_SECONDS = 30L
    const val DIGITS = 6

    private const val DRIFT_STEPS = 1

    private const val ALGORITHM = "HmacSHA1"

    const val NO_MATCH = -1L

    fun Generate(SeedBytes: ByteArray, CounterVal: Long): String {
        val CounterBytes = ByteArray(8)
        var Remaining = CounterVal
        for (Index in 7 downTo 0) {
            CounterBytes[Index] = (Remaining and 0xFF).toByte()
            Remaining = Remaining ushr 8
        }

        val MacObj = Mac.getInstance(ALGORITHM)
        MacObj.init(SecretKeySpec(SeedBytes, ALGORITHM))
        val DigestBytes = MacObj.doFinal(CounterBytes)

        val OffsetVal = (DigestBytes[DigestBytes.size - 1].toInt() and 0x0F)
        val TruncatedVal =
            ((DigestBytes[OffsetVal].toInt() and 0x7F) shl 24) or
                ((DigestBytes[OffsetVal + 1].toInt() and 0xFF) shl 16) or
                ((DigestBytes[OffsetVal + 2].toInt() and 0xFF) shl 8) or
                (DigestBytes[OffsetVal + 3].toInt() and 0xFF)

        var Modulus = 1
        repeat(DIGITS) { Modulus *= 10 }
        return (TruncatedVal % Modulus).toString().padStart(DIGITS, '0')
    }

    fun CounterFor(EpochSeconds: Long): Long = EpochSeconds / STEP_SECONDS

    fun FindMatchingCounter(
        SeedBytes: ByteArray,
        CodeText: String,
        EpochSeconds: Long,
        MinCounterExclusive: Long
    ): Long {
        if (CodeText.length != DIGITS || CodeText.any { !it.isDigit() }) return NO_MATCH

        val CentreCounter = CounterFor(EpochSeconds = EpochSeconds)
        var MatchedCounter = NO_MATCH

        for (Delta in -DRIFT_STEPS..DRIFT_STEPS) {
            val CandidateCounter = CentreCounter + Delta
            if (CandidateCounter < 0) continue
            val CandidateCode = Generate(SeedBytes = SeedBytes, CounterVal = CandidateCounter)
            if (ConstantTimeEquals(LeftText = CandidateCode, RightText = CodeText)) {
                MatchedCounter = CandidateCounter
            }
        }

        if (MatchedCounter == NO_MATCH) return NO_MATCH
        if (MatchedCounter <= MinCounterExclusive) return REPLAYED
        return MatchedCounter
    }

    const val REPLAYED = -2L

    private fun ConstantTimeEquals(LeftText: String, RightText: String): Boolean {
        if (LeftText.length != RightText.length) return false
        var Difference = 0
        for (Index in LeftText.indices) {
            Difference = Difference or (LeftText[Index].code xor RightText[Index].code)
        }
        return Difference == 0
    }
}
