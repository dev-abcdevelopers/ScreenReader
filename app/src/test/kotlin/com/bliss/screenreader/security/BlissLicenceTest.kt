@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlissLicenceTest {

    private val BaseUrl = "https://blissmis.com/mregister1"
    private val ArgsText = "1!1!1!1!COMBO!1!1"
    private val Day = 86_400_000L

    @Test
    fun `url matches the documented example byte for byte`() {
        assertEquals(
            "https://blissmis.com/mregister1?message=format!52e02682bfad26bf!1!1!1!1!COMBO!1!1",
            BlissLicenceClient.BuildUrl(
                BaseUrl = BaseUrl,
                DeviceIdText = "52e02682bfad26bf",
                ArgsText = ArgsText
            )
        )
    }

    @Test
    fun `a plain body with no Error is valid`() {
        assertEquals(BlissLicenceClient.Verdict.Valid, BlissLicenceClient.JudgeBody(BodyText = "OK"))
        assertEquals(
            BlissLicenceClient.Verdict.Valid,
            BlissLicenceClient.JudgeBody(BodyText = "COMBO!1!1!VALID")
        )
    }

    @Test
    fun `an empty body is not licensed`() {
        assertEquals(
            BlissLicenceClient.Verdict.NotLicensed,
            BlissLicenceClient.JudgeBody(BodyText = "")
        )
    }

    @Test
    fun `any body containing Error is not licensed`() {
        assertEquals(
            BlissLicenceClient.Verdict.NotLicensed,
            BlissLicenceClient.JudgeBody(BodyText = "Error: device not registered")
        )
        // Substring match, mirroring IndexOf("Error") == -1 in the reference C#.
        // A success body that happens to carry the word is rejected too.
        assertEquals(
            BlissLicenceClient.Verdict.NotLicensed,
            BlissLicenceClient.JudgeBody(BodyText = "ErrorCode:0")
        )
        // Lower case is a different string and does not trip the check.
        assertEquals(
            BlissLicenceClient.Verdict.Valid,
            BlissLicenceClient.JudgeBody(BodyText = "no error")
        )
    }

    @Test
    fun `unusable device ids are rejected before any request`() {
        assertFalse(BlissLicenceClient.IsUsableDeviceId(DeviceIdText = ""))
        assertFalse(BlissLicenceClient.IsUsableDeviceId(DeviceIdText = "abc"))
        assertFalse(BlissLicenceClient.IsUsableDeviceId(DeviceIdText = "9774d56d682e549c"))
        assertTrue(BlissLicenceClient.IsUsableDeviceId(DeviceIdText = "52e02682bfad26bf"))
    }

    private fun StateAt(
        AgeDays: Long,
        StoredId: String = "52e02682bfad26bf",
        CurrentId: String = "52e02682bfad26bf",
        MaxSeenAt: Long = 0L
    ): BlissLicenceStore.CacheState {
        val NowMillis = 1_700_000_000_000L
        return BlissLicenceStore.Evaluate(
            LastOkAt = NowMillis - AgeDays * Day,
            MaxSeenAt = MaxSeenAt,
            NowMillis = NowMillis,
            StoredId = StoredId,
            CurrentId = CurrentId
        )
    }

    @Test
    fun `cache is fresh inside seven days and in grace for seven more`() {
        assertEquals(BlissLicenceStore.CacheState.Fresh, StateAt(AgeDays = 0))
        assertEquals(BlissLicenceStore.CacheState.Fresh, StateAt(AgeDays = 6))
        assertEquals(BlissLicenceStore.CacheState.InGrace, StateAt(AgeDays = 7))
        assertEquals(BlissLicenceStore.CacheState.InGrace, StateAt(AgeDays = 13))
        assertEquals(BlissLicenceStore.CacheState.Expired, StateAt(AgeDays = 14))
        assertEquals(BlissLicenceStore.CacheState.Expired, StateAt(AgeDays = 400))
    }

    @Test
    fun `a different device id discards the cache`() {
        assertEquals(
            BlissLicenceStore.CacheState.None,
            StateAt(AgeDays = 1, StoredId = "0000000000000000")
        )
        assertEquals(BlissLicenceStore.CacheState.None, StateAt(AgeDays = 1, StoredId = ""))
    }

    @Test
    fun `no stored stamp means no cache`() {
        assertEquals(
            BlissLicenceStore.CacheState.None,
            BlissLicenceStore.Evaluate(
                LastOkAt = 0L,
                MaxSeenAt = 0L,
                NowMillis = 1_700_000_000_000L,
                StoredId = "52e02682bfad26bf",
                CurrentId = "52e02682bfad26bf"
            )
        )
    }

    @Test
    fun `winding the clock back past the slack forces a real check`() {
        val NowMillis = 1_700_000_000_000L
        assertEquals(
            BlissLicenceStore.CacheState.RolledBack,
            BlissLicenceStore.Evaluate(
                LastOkAt = NowMillis - Day,
                MaxSeenAt = NowMillis + 3 * Day,
                NowMillis = NowMillis,
                StoredId = "52e02682bfad26bf",
                CurrentId = "52e02682bfad26bf"
            )
        )
        // A stamp in the future can only mean the clock moved backwards.
        assertEquals(
            BlissLicenceStore.CacheState.RolledBack,
            BlissLicenceStore.Evaluate(
                LastOkAt = NowMillis + Day,
                MaxSeenAt = 0L,
                NowMillis = NowMillis,
                StoredId = "52e02682bfad26bf",
                CurrentId = "52e02682bfad26bf"
            )
        )
    }

    @Test
    fun `less than a day of drift is tolerated`() {
        val NowMillis = 1_700_000_000_000L
        assertEquals(
            BlissLicenceStore.CacheState.Fresh,
            BlissLicenceStore.Evaluate(
                LastOkAt = NowMillis - Day,
                MaxSeenAt = NowMillis + Day / 2,
                NowMillis = NowMillis,
                StoredId = "52e02682bfad26bf",
                CurrentId = "52e02682bfad26bf"
            )
        )
    }

    @Test
    fun `grace countdown reaches zero exactly at the hard block`() {
        val NowMillis = 1_700_000_000_000L
        assertEquals(7, BlissLicenceStore.GraceDaysLeft(LastOkAt = NowMillis - 7 * Day, NowMillis = NowMillis))
        assertEquals(1, BlissLicenceStore.GraceDaysLeft(LastOkAt = NowMillis - 13 * Day, NowMillis = NowMillis))
        assertEquals(0, BlissLicenceStore.GraceDaysLeft(LastOkAt = NowMillis - 14 * Day, NowMillis = NowMillis))
        assertEquals(0, BlissLicenceStore.GraceDaysLeft(LastOkAt = NowMillis - 99 * Day, NowMillis = NowMillis))
    }

    @Test
    fun `days since last check rounds down and never goes negative`() {
        val NowMillis = 1_700_000_000_000L
        assertEquals(0, BlissLicenceStore.DaysSinceLastCheck(LastOkAt = NowMillis, NowMillis = NowMillis))
        assertEquals(2, BlissLicenceStore.DaysSinceLastCheck(LastOkAt = NowMillis - 2 * Day - 500L, NowMillis = NowMillis))
        assertEquals(0, BlissLicenceStore.DaysSinceLastCheck(LastOkAt = NowMillis + Day, NowMillis = NowMillis))
        assertEquals(0, BlissLicenceStore.DaysSinceLastCheck(LastOkAt = 0L, NowMillis = NowMillis))
    }

    @Test
    fun `display grouping keeps every character of the id`() {
        assertEquals("52E0-2682-BFAD-26BF", DeviceIdentity.GroupForDisplay(IdText = "52e02682bfad26bf"))
        assertEquals("", DeviceIdentity.GroupForDisplay(IdText = ""))
    }
}
