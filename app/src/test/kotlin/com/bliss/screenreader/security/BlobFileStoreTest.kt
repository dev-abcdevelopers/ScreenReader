@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlobFileStoreTest {
    @get:Rule
    val TempDir = TemporaryFolder()

    private fun NewStore(): BlobFileStore = BlobFileStore(DirRef = TempDir.newFolder("blobs"))

    @Test
    fun WriteThenReadReturnsSameStoredText() {
        val StoreRef = NewStore()
        val StoredText = "v1$" + "A".repeat(200_000) + "é₹"
        assertTrue(StoreRef.WriteVerified(KeyText = "capture_session_policy_abc", StoredText = StoredText))
        assertTrue(StoreRef.Exists(KeyText = "capture_session_policy_abc"))
        assertEquals(StoredText, StoreRef.Read(KeyText = "capture_session_policy_abc"))
    }

    @Test
    fun OverwriteKeepsOnlyLatest() {
        val StoreRef = NewStore()
        StoreRef.Write(KeyText = "run_summary_x", StoredText = "old")
        StoreRef.Write(KeyText = "run_summary_x", StoredText = "new")
        assertEquals("new", StoreRef.Read(KeyText = "run_summary_x"))
        assertEquals(listOf("run_summary_x"), StoreRef.Keys())
    }

    @Test
    fun DeleteRemovesFile() {
        val StoreRef = NewStore()
        StoreRef.Write(KeyText = "capture_gaps_x", StoredText = "gaps")
        StoreRef.Delete(KeyText = "capture_gaps_x")
        assertFalse(StoreRef.Exists(KeyText = "capture_gaps_x"))
        assertNull(StoreRef.Read(KeyText = "capture_gaps_x"))
    }

    @Test
    fun KeysRoundTripThroughFileNames() {
        val StoreRef = NewStore()
        val KeyList = listOf("capture_changes_policy_a-b_c", "due_report_Z9", "key_fup_policies")
        for (KeyText in KeyList) StoreRef.Write(KeyText = KeyText, StoredText = KeyText)
        assertEquals(KeyList.sorted(), StoreRef.Keys().sorted())
        StoreRef.DeleteAll()
        assertTrue(StoreRef.Keys().isEmpty())
    }

    @Test
    fun NameEncodingIsReversible() {
        val KeyText = "capture_session_renewal_due_0f3e_ä"
        assertEquals(KeyText, BlobFileStore.DecodeName(BlobFileStore.EncodeName(KeyText = KeyText)))
        assertNull(BlobFileStore.DecodeName(NameText = "zz"))
    }
}
