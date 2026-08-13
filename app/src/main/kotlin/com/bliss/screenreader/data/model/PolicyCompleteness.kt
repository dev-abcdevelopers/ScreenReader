@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

/**
 * Groups a [CustomerPolicy]'s fields by the screen they were captured from,
 * and reports how much of each group actually landed.
 *
 * Grouping by capture source rather than by topic is what makes "missing" both
 * computable and actionable: a group maps one-to-one onto a screen or an
 * accordion in the target app, so an incomplete group names the exact thing to
 * go back and capture.
 *
 * Two deliberate exclusions from the denominator:
 *
 * - **Status flags** (KYC, NEFT, nominee, mobile, address) are absence markers.
 *   The parser only writes them when the source app says "not updated", so an
 *   empty value means the customer is fine, not that capture failed. Counting
 *   them would make a healthy policy look incomplete.
 * - **Customer profile** fields have no capture path yet - nothing navigates to
 *   that screen - so they are shown when present but never counted as missing.
 *   A denominator that cannot be reached trains people to ignore the bar.
 */
object PolicyCompleteness {

    data class FieldEntry(
        val Label: String,
        val Value: String,
        val IsDate: Boolean = false,
        val IsAmount: Boolean = false
    )

    data class FieldGroup(
        val Key: String,
        val Title: String,
        val Fields: List<FieldEntry>,
        /** False for groups the app cannot capture yet. */
        val IsCountedTowardTotal: Boolean,
        /** False when the app has no way to go and fetch this group. */
        val IsCapturable: Boolean
    ) {
        val CapturedCount: Int get() = Fields.count { FieldRef -> FieldRef.Value.isNotEmpty() }
        val TotalCount: Int get() = Fields.size
        val IsComplete: Boolean get() = CapturedCount == TotalCount
        val IsEmpty: Boolean get() = CapturedCount == 0
    }

    data class Summary(
        val Groups: List<FieldGroup>,
        val CapturedCount: Int,
        val TotalCount: Int
    ) {
        val Percent: Int
            get() = if (TotalCount == 0) 0 else (CapturedCount * 100) / TotalCount

        val IsComplete: Boolean get() = TotalCount > 0 && CapturedCount == TotalCount

        val MissingGroups: List<FieldGroup>
            get() = Groups.filter { GroupRef ->
                GroupRef.IsCountedTowardTotal && !GroupRef.IsComplete
            }

        val MissingCount: Int get() = TotalCount - CapturedCount
    }

    const val GROUP_CARD = "card"
    const val GROUP_POLICY_DETAILS = "policy_details"
    const val GROUP_COMMISSIONS = "commissions"
    const val GROUP_KEY_DATES = "key_dates"
    const val GROUP_CUSTOMER = "customer"

    fun Describe(PolicyItem: CustomerPolicy, Labels: LabelSet): Summary {
        val GroupList = listOf(
            FieldGroup(
                Key = GROUP_CARD,
                Title = Labels.CardTitle,
                IsCountedTowardTotal = true,
                IsCapturable = true,
                Fields = listOf(
                    FieldEntry(Labels.PlanCode, PolicyItem.PlanCode),
                    FieldEntry(Labels.PlanName, PolicyItem.PlanName),
                    FieldEntry(Labels.Status, PolicyItem.Status),
                    FieldEntry(Labels.Premium, PolicyItem.PremiumAmount, IsAmount = true),
                    FieldEntry(Labels.PremiumFrequency, PolicyItem.PremiumFrequency),
                    FieldEntry(Labels.AutoPay, PolicyItem.AutoPay),
                    FieldEntry(Labels.RenewalType, PolicyItem.RenewalType),
                    FieldEntry(Labels.RenewalDue, PolicyItem.RenewalDueDate, IsDate = true)
                )
            ),
            FieldGroup(
                Key = GROUP_POLICY_DETAILS,
                Title = Labels.PolicyDetailsTitle,
                IsCountedTowardTotal = true,
                IsCapturable = true,
                Fields = listOf(
                    FieldEntry(Labels.SumAssured, PolicyItem.SumAssured, IsAmount = true),
                    FieldEntry(Labels.TermPpt, PolicyItem.TermPPT)
                )
            ),
            FieldGroup(
                Key = GROUP_COMMISSIONS,
                Title = Labels.CommissionsTitle,
                IsCountedTowardTotal = true,
                IsCapturable = true,
                Fields = listOf(
                    FieldEntry(Labels.CommissionType, PolicyItem.CommissionType),
                    FieldEntry(Labels.CommissionPaid, PolicyItem.CommissionPaidAmount, IsAmount = true),
                    FieldEntry(Labels.BonusCommission, PolicyItem.BonusCommission, IsAmount = true),
                    FieldEntry(
                        Labels.CommissionPaymentDate,
                        PolicyItem.CommissionDateOfPayment,
                        IsDate = true
                    ),
                    FieldEntry(
                        Labels.CommissionPremiumDate,
                        PolicyItem.CommissionDateOfPremiumPayment,
                        IsDate = true
                    )
                )
            ),
            FieldGroup(
                Key = GROUP_KEY_DATES,
                Title = Labels.KeyDatesTitle,
                IsCountedTowardTotal = true,
                IsCapturable = true,
                Fields = listOf(
                    FieldEntry(Labels.Commenced, PolicyItem.DateOfCommencement, IsDate = true),
                    FieldEntry(Labels.PremiumsEnd, PolicyItem.EndOfPremiumPayingTerm, IsDate = true),
                    FieldEntry(Labels.Matures, PolicyItem.DateOfMaturity, IsDate = true)
                )
            ),
            FieldGroup(
                Key = GROUP_CUSTOMER,
                Title = Labels.CustomerTitle,
                // Read-only until a customer-profile capture flow exists.
                IsCountedTowardTotal = false,
                IsCapturable = false,
                Fields = listOf(
                    FieldEntry(Labels.Mobile, PolicyItem.MobileNumber),
                    FieldEntry(Labels.Dob, PolicyItem.Dob, IsDate = true),
                    FieldEntry(Labels.Address, PolicyItem.Address)
                )
            )
        )

        val CountedGroups = GroupList.filter { GroupRef -> GroupRef.IsCountedTowardTotal }
        return Summary(
            Groups = GroupList,
            CapturedCount = CountedGroups.sumOf { GroupRef -> GroupRef.CapturedCount },
            TotalCount = CountedGroups.sumOf { GroupRef -> GroupRef.TotalCount }
        )
    }

    /**
     * Status markers shown as chips rather than counted as fields. Present
     * means "the source app flagged a problem".
     */
    fun StatusFlags(PolicyItem: CustomerPolicy, Labels: LabelSet): List<String> {
        val FlagList = mutableListOf<String>()
        if (PolicyItem.KycStatus.isNotEmpty()) FlagList.add(Labels.FlagKyc)
        if (PolicyItem.NeftStatus.isNotEmpty()) FlagList.add(Labels.FlagNeft)
        if (PolicyItem.NomineeStatus.isNotEmpty()) FlagList.add(Labels.FlagNominee)
        if (PolicyItem.MobileUpdateStatus.isNotEmpty()) FlagList.add(Labels.FlagMobile)
        if (PolicyItem.AddressUpdateStatus.isNotEmpty()) FlagList.add(Labels.FlagAddress)
        return FlagList
    }

    /** Labels are passed in so this stays free of Android resource lookups. */
    data class LabelSet(
        val CardTitle: String,
        val PolicyDetailsTitle: String,
        val CommissionsTitle: String,
        val KeyDatesTitle: String,
        val CustomerTitle: String,
        val PlanCode: String,
        val PlanName: String,
        val Status: String,
        val Premium: String,
        val PremiumFrequency: String,
        val AutoPay: String,
        val RenewalType: String,
        val RenewalDue: String,
        val SumAssured: String,
        val TermPpt: String,
        val CommissionType: String,
        val CommissionPaid: String,
        val BonusCommission: String,
        val CommissionPaymentDate: String,
        val CommissionPremiumDate: String,
        val Commenced: String,
        val PremiumsEnd: String,
        val Matures: String,
        val Mobile: String,
        val Dob: String,
        val Address: String,
        val FlagKyc: String,
        val FlagNeft: String,
        val FlagNominee: String,
        val FlagMobile: String,
        val FlagAddress: String
    )
}
