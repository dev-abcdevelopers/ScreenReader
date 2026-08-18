@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.model

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

    var CommissionDateOfPremiumPayment: String = "",
    var CommissionDateOfPayment: String = "",
    var CommissionType: String = "",
    var BonusCommission: String = "",
    var CommissionPaidAmount: String = "",

    var MobileNumberOthers: List<String>? = null,
    var EmailOthers: List<String>? = null,
    var AddressOthers: List<String>? = null
) {
    val NormalizedStatus: String
        get() {
            val Trimmed = Status.trim()
            if (Trimmed.isEmpty()) return ""
            return if (Trimmed.lowercase(Locale.ROOT).contains("lapsed")) "Lapsed" else "Inforce"
        }
}
