@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import com.bliss.screenreader.data.repository.PolicyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlobRoutingTest {
    @Test
    fun RoutesTheBigPerSessionKeys() {
        val RoutedKeys = listOf(
            "capture_session_policy_abc",
            "capture_session_ps_abc",
            "capture_session_fup_abc",
            "capture_session_customer_abc",
            "capture_session_renewal_due_abc",
            "capture_changes_policy_abc",
            "capture_gaps_abc",
            "capture_visited_customers_abc",
            "run_summary_abc",
            "due_report_abc",
            "key_customer_policies",
            "key_fup_policies",
            "key_ps_policies",
            "key_renewal_due_policies"
        )
        for (KeyText in RoutedKeys) assertTrue(KeyText, BlobRouting.IsBlobKey(KeyText))
    }

    @Test
    fun KeepsSmallKeysInPrefs() {
        val PrefsKeys = listOf(
            "capture_session_history",
            "capture_session_name_abc",
            "latest_policy_session",
            "capture_agency_abc",
            "capture_resume_fast_abc",
            "capture_renewal_skips_abc",
            "last_agency_code",
            "agency_code_list",
            "secure_prefs_migrated_v1"
        )
        for (KeyText in PrefsKeys) assertFalse(KeyText, BlobRouting.IsBlobKey(KeyText))
    }

    @Test
    fun RepositoryUsesTheRoutedPrefsFile() {
        assertEquals(BlobRouting.DATA_PREFS_NAME, PolicyRepository.PREFS_NAME)
    }
}
