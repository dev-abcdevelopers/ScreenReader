@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

enum class DueDateSkipReason {
    NO_RENEWAL_ROW,
    NO_FREQUENCY,
    ALREADY_CURRENT,
    GRACE_DATE,
    NO_DUE_DATE
}

data class DueDateUpdate(
    val PolicyNumber: String,
    val HolderName: String,
    val PlanCode: String,
    val OldDate: String,
    val NewDate: String,
    val PaidForDate: String,
    val Frequency: String
)

data class DueDateSkip(
    val PolicyNumber: String,
    val HolderName: String,
    val PlanCode: String,
    val CurrentDate: String,
    val Reason: DueDateSkipReason
)

data class DueDateOutcome(
    val Policies: List<CustomerPolicy>,
    val Changes: List<RecordFieldChange>,
    val Updates: List<DueDateUpdate> = emptyList(),
    val Skips: List<DueDateSkip> = emptyList(),
    val MatchedCount: Int = 0,
    val AnchoredCount: Int = 0,
    val UpdatedCount: Int = 0,
    val UnchangedCount: Int = 0,
    val SkippedCount: Int = 0
)
