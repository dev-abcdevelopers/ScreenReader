@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

data class RecordFieldChange(
    val RecordKey: String,
    val FieldName: String,
    val OldValue: String,
    val NewValue: String,
    val ChangedAt: Long = System.currentTimeMillis(),
    val SourceName: String? = null
)

object ChangeSource {
    const val DUE_IMPORT = "due_import"
    const val POLICY_CAPTURE = "policy_capture"
    const val PROFILE_CAPTURE = "profile_capture"
    const val RENEWAL_CAPTURE = "renewal_capture"
}
