@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

object PolicyResumeTrack {
    const val POLICY_FAST = "policy_fast"
    const val POLICY_FULL = "policy_full"
    const val CUSTOMER = "customer"

    val All = listOf(POLICY_FAST, POLICY_FULL, CUSTOMER)

    fun OfPolicyRun(CapturePolicyDetails: Boolean): String {
        return if (CapturePolicyDetails) POLICY_FULL else POLICY_FAST
    }

    fun OfMode(ModeVal: CaptureMode, CapturePolicyDetails: Boolean): String {
        return when (ModeVal) {
            CaptureMode.POLICY -> OfPolicyRun(CapturePolicyDetails = CapturePolicyDetails)
            CaptureMode.CUSTOMER -> CUSTOMER
            else -> ""
        }
    }
}

data class PolicyResumeMark(
    val SessionId: String = "",
    val Track: String? = null,
    val LastCompletedPage: Int = 0,
    val TotalPages: Int = 0,
    val CapturedCount: Int = 0,
    val OutstandingBefore: Int? = null,
    val SavedAt: Long = 0L,
    val IsComplete: Boolean = false
)

object PolicyResumeTarget {

    fun IsOffered(MarkObj: PolicyResumeMark?, StoredRecordCount: Int): Boolean {
        if (MarkObj == null) return false
        if (MarkObj.IsComplete) return false
        if (StoredRecordCount <= 0) return false
        if (MarkObj.TotalPages <= 1) return false
        return MarkObj.LastCompletedPage in 2..<MarkObj.TotalPages
    }

    fun Resolve(MarkObj: PolicyResumeMark?, StoredRecordCount: Int): Int {
        if (!IsOffered(MarkObj = MarkObj, StoredRecordCount = StoredRecordCount)) return 0
        return MarkObj?.LastCompletedPage ?: 0
    }

    fun IsCustomerJumpSafe(MarkObj: PolicyResumeMark?): Boolean {
        return MarkObj?.OutstandingBefore == 0
    }

    fun CustomerSkipAheadPage(MarkObj: PolicyResumeMark?, StoredRecordCount: Int): Int {
        if (!IsOffered(MarkObj = MarkObj, StoredRecordCount = StoredRecordCount)) return 0
        if (IsCustomerJumpSafe(MarkObj = MarkObj)) return 0
        return MarkObj?.LastCompletedPage ?: 0
    }

    fun ChooseMark(
        TrackVal: String,
        FastMark: PolicyResumeMark?,
        FullMark: PolicyResumeMark?,
        CustomerMark: PolicyResumeMark?,
        StoredRecordCount: Int
    ): PolicyResumeMark? {
        return when (TrackVal) {
            PolicyResumeTrack.POLICY_FULL -> {
                if (IsOffered(MarkObj = FullMark, StoredRecordCount = StoredRecordCount)) {
                    FullMark
                } else {
                    null
                }
            }

            PolicyResumeTrack.POLICY_FAST -> {
                val FastPage = Resolve(MarkObj = FastMark, StoredRecordCount = StoredRecordCount)
                val FullPage = Resolve(MarkObj = FullMark, StoredRecordCount = StoredRecordCount)
                when {
                    FullPage > FastPage -> FullMark
                    FastPage > 0 -> FastMark
                    else -> null
                }
            }

            PolicyResumeTrack.CUSTOMER -> {
                if (IsOffered(MarkObj = CustomerMark, StoredRecordCount = StoredRecordCount) &&
                    IsCustomerJumpSafe(MarkObj = CustomerMark)
                ) {
                    CustomerMark
                } else {
                    null
                }
            }

            else -> null
        }
    }

    fun ResolveForTrack(
        TrackVal: String,
        FastMark: PolicyResumeMark?,
        FullMark: PolicyResumeMark?,
        CustomerMark: PolicyResumeMark?,
        StoredRecordCount: Int
    ): Int {
        val ChosenMark = ChooseMark(
            TrackVal = TrackVal,
            FastMark = FastMark,
            FullMark = FullMark,
            CustomerMark = CustomerMark,
            StoredRecordCount = StoredRecordCount
        )
        return Resolve(MarkObj = ChosenMark, StoredRecordCount = StoredRecordCount)
    }

    fun ClampToTotal(TargetPage: Int, TotalPages: Int): Int {
        if (TargetPage <= 1) return 0
        if (TotalPages <= 0) return TargetPage
        if (TargetPage > TotalPages) return TotalPages
        return TargetPage
    }
}
