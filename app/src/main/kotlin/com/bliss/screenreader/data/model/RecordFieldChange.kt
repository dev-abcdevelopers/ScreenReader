@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.model

/**
 * One field that a resumed capture overwrote.
 *
 * Only genuine replacements are recorded. Filling a blank field is not a
 * change - it is the whole point of resuming - and logging those would bury
 * the handful of entries that actually deserve a second look.
 */
data class RecordFieldChange(
    val RecordKey: String,
    val FieldName: String,
    val OldValue: String,
    val NewValue: String,
    val ChangedAt: Long = System.currentTimeMillis()
)
