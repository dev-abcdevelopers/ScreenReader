@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TotpTest {
    private val SeedBytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun `matches RFC 6238 test vectors`() {
        val Vectors = listOf(
            59L to "287082",
            1111111109L to "081804",
            1111111111L to "050471",
            1234567890L to "005924",
            2000000000L to "279037",
            20000000000L to "353130"
        )
        for ((EpochSeconds, ExpectedCode) in Vectors) {
            val ActualCode = Totp.Generate(
                SeedBytes = SeedBytes,
                CounterVal = Totp.CounterFor(EpochSeconds = EpochSeconds)
            )
            assertEquals("at t=$EpochSeconds", ExpectedCode, ActualCode)
        }
    }

    @Test
    fun `accepts the current code`() {
        val EpochSeconds = 1_700_000_000L
        val CodeText = Totp.Generate(
            SeedBytes = SeedBytes,
            CounterVal = Totp.CounterFor(EpochSeconds = EpochSeconds)
        )
        val MatchedCounter = Totp.FindMatchingCounter(
            SeedBytes = SeedBytes,
            CodeText = CodeText,
            EpochSeconds = EpochSeconds,
            MinCounterExclusive = Totp.NO_MATCH
        )
        assertEquals(Totp.CounterFor(EpochSeconds = EpochSeconds), MatchedCounter)
    }

    @Test
    fun `accepts a code one step stale`() {
        val EpochSeconds = 1_700_000_000L
        val PreviousCode = Totp.Generate(
            SeedBytes = SeedBytes,
            CounterVal = Totp.CounterFor(EpochSeconds = EpochSeconds) - 1
        )
        val MatchedCounter = Totp.FindMatchingCounter(
            SeedBytes = SeedBytes,
            CodeText = PreviousCode,
            EpochSeconds = EpochSeconds,
            MinCounterExclusive = Totp.NO_MATCH
        )
        assertNotEquals(Totp.NO_MATCH, MatchedCounter)
    }

    @Test
    fun `rejects a code two steps stale`() {
        val EpochSeconds = 1_700_000_000L
        val OldCode = Totp.Generate(
            SeedBytes = SeedBytes,
            CounterVal = Totp.CounterFor(EpochSeconds = EpochSeconds) - 2
        )
        assertEquals(
            Totp.NO_MATCH,
            Totp.FindMatchingCounter(
                SeedBytes = SeedBytes,
                CodeText = OldCode,
                EpochSeconds = EpochSeconds,
                MinCounterExclusive = Totp.NO_MATCH
            )
        )
    }

    @Test
    fun `refuses to redeem the same code twice`() {
        val EpochSeconds = 1_700_000_000L
        val CounterVal = Totp.CounterFor(EpochSeconds = EpochSeconds)
        val CodeText = Totp.Generate(SeedBytes = SeedBytes, CounterVal = CounterVal)

        val FirstResult = Totp.FindMatchingCounter(
            SeedBytes = SeedBytes,
            CodeText = CodeText,
            EpochSeconds = EpochSeconds,
            MinCounterExclusive = Totp.NO_MATCH
        )
        assertEquals(CounterVal, FirstResult)

        val SecondResult = Totp.FindMatchingCounter(
            SeedBytes = SeedBytes,
            CodeText = CodeText,
            EpochSeconds = EpochSeconds,
            MinCounterExclusive = FirstResult
        )
        assertEquals(Totp.REPLAYED, SecondResult)
    }

    @Test
    fun `rejects malformed input without throwing`() {
        val BadInputs = listOf("", "12345", "1234567", "abcdef", "12 34 56")
        for (BadInput in BadInputs) {
            assertEquals(
                BadInput,
                Totp.NO_MATCH,
                Totp.FindMatchingCounter(
                    SeedBytes = SeedBytes,
                    CodeText = BadInput,
                    EpochSeconds = 1_700_000_000L,
                    MinCounterExclusive = Totp.NO_MATCH
                )
            )
        }
    }
}
