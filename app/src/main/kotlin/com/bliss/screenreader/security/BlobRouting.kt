@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

object BlobRouting {
    const val DATA_PREFS_NAME = "data_reader_prefs"
    const val DATA_BLOB_DIR = "session_blobs"

    private val SESSION_MODE_PREFIXES = listOf(
        "capture_session_policy_",
        "capture_session_ps_",
        "capture_session_fup_",
        "capture_session_customer_",
        "capture_session_renewal_due_"
    )

    private val DATA_PREFIXES = listOf(
        "capture_changes_",
        "capture_gaps_",
        "capture_visited_customers_",
        "run_summary_",
        "due_report_"
    )

    private val LEGACY_KEYS = setOf(
        "key_customer_policies",
        "key_fup_policies",
        "key_ps_policies",
        "key_renewal_due_policies"
    )

    fun IsBlobKey(KeyText: String): Boolean {
        if (KeyText in LEGACY_KEYS) return true
        if (SESSION_MODE_PREFIXES.any { PrefixText -> KeyText.startsWith(PrefixText) }) return true
        return DATA_PREFIXES.any { PrefixText -> KeyText.startsWith(PrefixText) }
    }
}
