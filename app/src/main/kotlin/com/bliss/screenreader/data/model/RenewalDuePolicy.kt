@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

import java.util.Locale

enum class RenewalDueKind {
    RENEWAL_DUE,
    GRACE_EXPIRY,
    UNKNOWN
}

data class RenewalDuePolicy(
    val PolicyNumber: String,
    val PlanName: String = "",
    val PlanCode: String = "",
    val HolderName: String = "",
    val PremiumAmount: String = "",
    val PremiumFrequency: String? = null,
    val DateLabel: String = "",
    val DateValue: String = "",
    val UrgencyText: String = "",
    val AutoPay: String = "",
    val CapturedAt: Long = System.currentTimeMillis()
) {
    val Kind: RenewalDueKind
        get() {
            val LowerLabel = DateLabel.lowercase(Locale.ROOT)
            return when {
                LowerLabel.contains("grace") -> RenewalDueKind.GRACE_EXPIRY
                LowerLabel.contains("renewal due") -> RenewalDueKind.RENEWAL_DUE
                else -> RenewalDueKind.UNKNOWN
            }
        }

    val DueDateOrBlank: String
        get() = if (Kind == RenewalDueKind.RENEWAL_DUE) DateValue.trim() else ""
}
