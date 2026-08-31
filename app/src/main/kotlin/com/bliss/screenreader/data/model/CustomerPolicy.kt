@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.model

import com.bliss.screenreader.data.parser.PolicyStatusRules
import com.bliss.screenreader.data.parser.ScreenDataParser
import java.util.Locale

data class CustomerPolicy(
    val HolderName: String = "",
    val Age: String = "",
    val PolicyNumber: String = "",
    val PlanName: String = "",
    val PlanCode: String = "",
    val RenewalDueDate: String = "",
    var PremiumAmount: String = "",
    var PremiumFrequency: String = "",
    var AutoPay: String = "",
    val Status: String = "",
    var MobileNumber: String = "",
    var Dob: String = "",
    var Address: String = "",
    var Email: String = "",
    var Gender: String = "",
    var Education: String = "",
    var Occupation: String = "",
    var MaritalStatus: String = "",
    var AnnualIncome: String = "",
    var TermPPT: String = "",
    var SumAssured: String = "",
    var DateOfCommencement: String = "",
    var EndOfPremiumPayingTerm: String = "",
    var DateOfMaturity: String = "",
    val CapturedAt: Long = System.currentTimeMillis(),

    var NomineeStatus: String = "",
    var MobileUpdateStatus: String = "",
    var AddressUpdateStatus: String = "",
    var KycStatus: String = "",
    var NeftStatus: String = "",
    var RenewalType: String = "",
    var RenewalDateLabel: String = "",
    var RenewalDateValue: String = "",

    var CommissionDateOfPremiumPayment: String = "",
    var CommissionDateOfPayment: String = "",
    var CommissionType: String = "",
    var BonusCommission: String = "",
    var CommissionPaidAmount: String = "",

    var StatusChips: List<String>? = null,

    var MobileNumberOthers: List<String>? = null,
    var EmailOthers: List<String>? = null,
    var AddressOthers: List<String>? = null
) {
    val FupForStatus: String
        get() {
            if (RenewalDueDate.isNotEmpty()) return RenewalDueDate
            if (ScreenDataParser.DueDateSurvives(CardDateLabel = RenewalDateLabel)) {
                return RenewalDateValue
            }
            return ""
        }

    val DerivedStatus: String
        get() = PolicyStatusRules.Compute(
            FupText = FupForStatus,
            FrequencyText = PremiumFrequency,
            CommencementText = DateOfCommencement
        )

    val NormalizedStatus: String
        get() {
            val Derived = DerivedStatus
            if (Derived.isNotEmpty()) return Derived

            val Trimmed = Status.trim()
            if (Trimmed.isEmpty()) return ""
            val Lowered = Trimmed.lowercase(Locale.ROOT)
            return when {
                Lowered.contains("lapsed") -> PolicyStatusRules.LAPSED
                Lowered.contains("grace") -> PolicyStatusRules.GRACE
                else -> ""
            }
        }
}
