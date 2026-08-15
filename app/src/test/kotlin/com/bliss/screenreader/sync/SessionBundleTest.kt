@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBundleTest {

    private val MinimalBundle = """
        {
          "SchemaVersion": 1,
          "ExportedAt": 1786790400000,
          "Sessions": [
            {
              "SessionId": "policy_1786700000000",
              "Mode": "POLICY",
              "SavedAt": 1786788912345,
              "RecordCount": 1,
              "AgencyCode": "bma0147",
              "Policies": [ { "PolicyNumber": "123456789", "HolderName": "RAMESH" } ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `bundle files are told apart from agency payloads`() {
        assertTrue(SessionBundleStore.IsBundleFile(FileNameVal = "sessions_20260815-183000.json"))
        assertFalse(SessionBundleStore.IsBundleFile(FileNameVal = "bma0147.json"))
        assertFalse(SessionBundleStore.IsBundleFile(FileNameVal = "policies_20260815.xlsx"))
    }

    @Test
    fun `file name carries the stamp`() {
        assertEquals(
            "sessions_20260815-183000.json",
            SessionBundleStore.BuildFileName(StampText = "20260815-183000")
        )
    }

    @Test
    fun `rubbish input parses to null instead of throwing`() {
        assertNull(SessionBundleStore.ParseBundle(JsonText = "not json at all"))
        assertNull(SessionBundleStore.ParseBundle(JsonText = ""))
        assertNull(SessionBundleStore.ParseBundle(JsonText = "{\"SchemaVersion\":1}"))
    }

    @Test
    fun `missing collections survive as empty, not as crashes`() {
        val BundleObj = SessionBundleStore.ParseBundle(JsonText = MinimalBundle)
        assertNotNull(BundleObj)

        val EntryRef = BundleObj!!.Sessions.orEmpty().first()
        assertEquals("policy_1786700000000", EntryRef.SessionId)
        assertEquals(1, EntryRef.TotalRecordCount)
        assertTrue(EntryRef.Renewals.orEmpty().isEmpty())
        assertTrue(EntryRef.Gaps.orEmpty().isEmpty())
        assertTrue(EntryRef.Changes.orEmpty().isEmpty())
        assertEquals(0L, EntryRef.LastResumedAt)
        assertFalse(EntryRef.CapturePolicyDetails)
    }
}
