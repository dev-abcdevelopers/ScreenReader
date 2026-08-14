@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.RecordFieldChange

object RecordMerge {

    data class MergeOutcome<RecordType>(
        val Record: RecordType,
        val Changes: List<RecordFieldChange>
    )

    fun ResolveField(
        RecordKey: String,
        FieldName: String,
        ExistingValue: String,
        IncomingValue: String,
        ChangeSink: MutableList<RecordFieldChange>
    ): String {
        if (IncomingValue.isEmpty()) return ExistingValue
        if (ExistingValue.isEmpty()) return IncomingValue
        if (ExistingValue == IncomingValue) return ExistingValue

        ChangeSink.add(
            RecordFieldChange(
                RecordKey = RecordKey,
                FieldName = FieldName,
                OldValue = ExistingValue,
                NewValue = IncomingValue
            )
        )
        return IncomingValue
    }

    fun MergePolicy(
        ExistingItem: CustomerPolicy,
        IncomingItem: CustomerPolicy
    ): MergeOutcome<CustomerPolicy> {
        val RecordKey = ExistingItem.PolicyNumber.ifEmpty { IncomingItem.PolicyNumber }
        val ChangeSink = mutableListOf<RecordFieldChange>()

        fun Resolve(FieldName: String, ExistingValue: String, IncomingValue: String): String {
            return ResolveField(
                RecordKey = RecordKey,
                FieldName = FieldName,
                ExistingValue = ExistingValue,
                IncomingValue = IncomingValue,
                ChangeSink = ChangeSink
            )
        }

        val MergedItem = ExistingItem.copy(
            HolderName = Resolve("Holder name", ExistingItem.HolderName, IncomingItem.HolderName),
            PlanName = Resolve("Plan name", ExistingItem.PlanName, IncomingItem.PlanName),
            PlanCode = Resolve("Plan code", ExistingItem.PlanCode, IncomingItem.PlanCode),
            RenewalDueDate = Resolve(
                "Renewal due date", ExistingItem.RenewalDueDate, IncomingItem.RenewalDueDate
            ),
            SumAssured = Resolve("Sum assured", ExistingItem.SumAssured, IncomingItem.SumAssured),
            TermPPT = Resolve("Term / PPT", ExistingItem.TermPPT, IncomingItem.TermPPT),
            DateOfCommencement = Resolve(
                "Date of commencement",
                ExistingItem.DateOfCommencement,
                IncomingItem.DateOfCommencement
            ),
            EndOfPremiumPayingTerm = Resolve(
                "End of premium paying term",
                ExistingItem.EndOfPremiumPayingTerm,
                IncomingItem.EndOfPremiumPayingTerm
            ),
            DateOfMaturity = Resolve(
                "Date of maturity", ExistingItem.DateOfMaturity, IncomingItem.DateOfMaturity
            ),
            MobileNumber = Resolve(
                "Mobile number", ExistingItem.MobileNumber, IncomingItem.MobileNumber
            ),
            Dob = Resolve("Date of birth", ExistingItem.Dob, IncomingItem.Dob),
            Address = Resolve("Address", ExistingItem.Address, IncomingItem.Address),
            Email = Resolve("Email", ExistingItem.Email, IncomingItem.Email),
            Gender = Resolve("Gender", ExistingItem.Gender, IncomingItem.Gender),
            Education = Resolve("Education", ExistingItem.Education, IncomingItem.Education),
            Occupation = Resolve(
                "Occupation", ExistingItem.Occupation, IncomingItem.Occupation
            ),
            MaritalStatus = Resolve(
                "Marital status", ExistingItem.MaritalStatus, IncomingItem.MaritalStatus
            ),
            AnnualIncome = Resolve(
                "Annual income", ExistingItem.AnnualIncome, IncomingItem.AnnualIncome
            ),
            PremiumAmount = Resolve(
                "Premium amount", ExistingItem.PremiumAmount, IncomingItem.PremiumAmount
            ),
            PremiumFrequency = Resolve(
                "Premium frequency", ExistingItem.PremiumFrequency, IncomingItem.PremiumFrequency
            ),
            AutoPay = Resolve("Auto pay", ExistingItem.AutoPay, IncomingItem.AutoPay),
            Status = Resolve("Status", ExistingItem.Status, IncomingItem.Status),
            NomineeStatus = Resolve(
                "Nominee status", ExistingItem.NomineeStatus, IncomingItem.NomineeStatus
            ),
            MobileUpdateStatus = Resolve(
                "Mobile update status",
                ExistingItem.MobileUpdateStatus,
                IncomingItem.MobileUpdateStatus
            ),
            AddressUpdateStatus = Resolve(
                "Address update status",
                ExistingItem.AddressUpdateStatus,
                IncomingItem.AddressUpdateStatus
            ),
            KycStatus = Resolve("KYC status", ExistingItem.KycStatus, IncomingItem.KycStatus),
            NeftStatus = Resolve("NEFT status", ExistingItem.NeftStatus, IncomingItem.NeftStatus),
            RenewalType = Resolve(
                "Renewal type", ExistingItem.RenewalType, IncomingItem.RenewalType
            ),
            CommissionDateOfPremiumPayment = Resolve(
                "Commission date of premium payment",
                ExistingItem.CommissionDateOfPremiumPayment,
                IncomingItem.CommissionDateOfPremiumPayment
            ),
            CommissionDateOfPayment = Resolve(
                "Commission date of payment",
                ExistingItem.CommissionDateOfPayment,
                IncomingItem.CommissionDateOfPayment
            ),
            CommissionType = Resolve(
                "Commission type", ExistingItem.CommissionType, IncomingItem.CommissionType
            ),
            BonusCommission = Resolve(
                "Bonus commission", ExistingItem.BonusCommission, IncomingItem.BonusCommission
            ),
            CommissionPaidAmount = Resolve(
                "Commission paid amount",
                ExistingItem.CommissionPaidAmount,
                IncomingItem.CommissionPaidAmount
            )
        )

        return MergeOutcome(Record = MergedItem, Changes = ChangeSink)
    }

    fun MergeRenewal(
        ExistingItem: FupPolicy,
        IncomingItem: FupPolicy
    ): MergeOutcome<FupPolicy> {
        val RecordKey = RenewalKey(RecordItem = ExistingItem)
        val ChangeSink = mutableListOf<RecordFieldChange>()

        fun Resolve(FieldName: String, ExistingValue: String, IncomingValue: String): String {
            return ResolveField(
                RecordKey = RecordKey,
                FieldName = FieldName,
                ExistingValue = ExistingValue,
                IncomingValue = IncomingValue,
                ChangeSink = ChangeSink
            )
        }

        val MergedItem = ExistingItem.copy(
            PlanName = Resolve("Plan name", ExistingItem.PlanName, IncomingItem.PlanName),
            PlanCode = Resolve("Plan code", ExistingItem.PlanCode, IncomingItem.PlanCode),
            HolderName = Resolve("Holder name", ExistingItem.HolderName, IncomingItem.HolderName),
            PremiumAmount = Resolve(
                "Premium amount", ExistingItem.PremiumAmount, IncomingItem.PremiumAmount
            ),
            DueDate = Resolve("Due date", ExistingItem.DueDate, IncomingItem.DueDate),
            PaymentDate = Resolve(
                "Payment date", ExistingItem.PaymentDate, IncomingItem.PaymentDate
            ),
            ModeOfPayment = Resolve(
                "Mode of payment", ExistingItem.ModeOfPayment, IncomingItem.ModeOfPayment
            ),
            Status = Resolve("Status", ExistingItem.Status, IncomingItem.Status)
        )

        return MergeOutcome(Record = MergedItem, Changes = ChangeSink)
    }

    fun RenewalKey(RecordItem: FupPolicy): String {
        return "${RecordItem.PolicyNumber}|${RecordItem.PaymentDate}"
    }

    fun HasPersonalDetails(PolicyItem: CustomerPolicy): Boolean {
        val HasContact = PolicyItem.MobileNumber.isNotEmpty() ||
                PolicyItem.Email.isNotEmpty() ||
                PolicyItem.Address.isNotEmpty()
        val HasIdentity = PolicyItem.Dob.isNotEmpty() || PolicyItem.Gender.isNotEmpty()
        return HasContact && HasIdentity
    }

    fun HasCompletePolicyDetails(PolicyItem: CustomerPolicy): Boolean {
        val HasPolicyDetails = PolicyItem.TermPPT.isNotEmpty()
        val HasCommissions = PolicyItem.CommissionType.isNotEmpty() ||
                PolicyItem.CommissionDateOfPayment.isNotEmpty() ||
                PolicyItem.CommissionPaidAmount.isNotEmpty()
        val HasKeyDates = PolicyItem.DateOfCommencement.isNotEmpty() ||
                PolicyItem.DateOfMaturity.isNotEmpty() ||
                PolicyItem.EndOfPremiumPayingTerm.isNotEmpty()
        return HasPolicyDetails && HasCommissions && HasKeyDates
    }
}
