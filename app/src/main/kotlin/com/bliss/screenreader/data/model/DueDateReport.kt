@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

data class DueDateReportEntry(
    val PolicyNumber: String = "",
    val HolderName: String = "",
    val PlanCode: String = "",
    val OldDate: String = "",
    val NewDate: String = "",
    val PaidForDate: String = "",
    val Frequency: String = "",
    val ReasonName: String = ""
)

data class DueDateReport(
    val SavedAt: Long = 0L,
    val SourceSessionId: String = "",
    val UpdatedCount: Int = 0,
    val UnchangedCount: Int = 0,
    val SkippedCount: Int = 0,
    val Updates: List<DueDateReportEntry> = emptyList(),
    val Skips: List<DueDateReportEntry> = emptyList()
)
