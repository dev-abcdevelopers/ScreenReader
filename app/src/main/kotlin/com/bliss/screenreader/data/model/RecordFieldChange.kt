@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.model

data class RecordFieldChange(
    val RecordKey: String,
    val FieldName: String,
    val OldValue: String,
    val NewValue: String,
    val ChangedAt: Long = System.currentTimeMillis()
)
