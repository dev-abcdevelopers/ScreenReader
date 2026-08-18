@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import android.content.Context
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.model.SessionGap
import com.bliss.screenreader.data.repository.PolicyRepository
import java.util.regex.Pattern

object CaptureParsers {

    @Volatile
    var LastCommitResult: CommitResult = CommitResult(AddedCount = 0, UpdatedCount = 0)
        private set

    private val POLICY_NO_REGEX: Pattern = Pattern.compile("^\\d{9}$")
    private val HOLDER_NAME_REGEX = Regex("^[A-Z][A-Za-z.]+(?:\\s+[A-Z][A-Za-z.]+){1,3}$")

    private val NAME_STOP_WORDS = setOf(
        "SUM ASSURED", "DATE OF MATURITY", "DATE OF COMMENCEMENT", "POLICY DETAILS",
        "END OF PREMIUM PAYING TERM", "MODE OF PAYMENT", "POLICY STATUS", "PLAN NAME"
    )

    fun Preview(ModeVal: CaptureMode, Nodes: List<String>): List<ParsedRecord> {
        if (Nodes.isEmpty()) return emptyList()
        return when (ModeVal) {
            CaptureMode.POLICY -> PreviewPolicy(Nodes = Nodes)
            CaptureMode.PS -> PreviewPs(Nodes = Nodes)
            CaptureMode.FUP -> PreviewFup(Nodes = Nodes)
            CaptureMode.CUSTOMER -> emptyList()
        }
    }

    fun Commit(
        ContextRef: Context,
        SessionId: String,
        ModeVal: CaptureMode,
        Nodes: List<String>,
        PolicyRecords: List<CustomerPolicy> = emptyList(),
        FupRecords: List<FupPolicy> = emptyList(),
        CapturePolicyDetails: Boolean = false,
        GapRecords: List<SessionGap> = emptyList()
    ): Int {
        require(SessionId.isNotBlank()) { "A capture session id is required" }
        LastCommitResult = CommitResult(AddedCount = 0, UpdatedCount = 0)
        return when (ModeVal) {
            CaptureMode.POLICY -> CommitPolicy(
                ContextRef = ContextRef,
                SessionId = SessionId,
                Nodes = Nodes,
                PolicyRecords = PolicyRecords,
                CapturePolicyDetails = CapturePolicyDetails
            )
            CaptureMode.PS -> CommitPs(
                ContextRef = ContextRef,
                SessionId = SessionId,
                Nodes = Nodes
            )
            CaptureMode.CUSTOMER -> CommitCustomerProfiles(
                ContextRef = ContextRef,
                SessionId = SessionId,
                Patches = PolicyRecords,
                Gaps = GapRecords
            )

            CaptureMode.FUP -> CommitFup(
                ContextRef = ContextRef,
                SessionId = SessionId,
                Nodes = Nodes,
                FupRecords = FupRecords
            )
        }
    }


    fun BuildPolicy(Nodes: List<String>): CustomerPolicy {
        val DetailsMap = ScreenDataParser.ParseDetailedPolicyView(Nodes = Nodes)
        val ProfileMap = ScreenDataParser.ParseCustomerProfile(Nodes = Nodes)

        return CustomerPolicy(
            HolderName = FindHolderName(Nodes = Nodes),
            PolicyNumber = FindPolicyNumber(Nodes = Nodes),
            SumAssured = DetailsMap["sumAssured"].orEmpty(),
            TermPPT = DetailsMap["termPPT"].orEmpty(),
            DateOfCommencement = DetailsMap["dateOfCommencement"].orEmpty(),
            EndOfPremiumPayingTerm = DetailsMap["endOfPremiumPayingTerm"].orEmpty(),
            DateOfMaturity = DetailsMap["dateOfMaturity"].orEmpty(),
            MobileNumber = ProfileMap["mobileNumber"].orEmpty(),
            Dob = ProfileMap["dob"].orEmpty(),
            PremiumAmount = FindFirstCurrency(Nodes = Nodes),
            Status = FindStatus(Nodes = Nodes)
        )
    }

    private fun PreviewPolicy(Nodes: List<String>): List<ParsedRecord> {
        val DashboardPolicies = if (IsPolicyDashboardNodes(Nodes = Nodes)) {
            ScreenDataParser.ParsePolicyDashboard(Nodes = Nodes)
        } else {
            emptyList()
        }
        val PolicyList = DashboardPolicies.ifEmpty {
            listOf(BuildPolicy(Nodes = Nodes)).filter { PolicyItem ->
                CountPolicyFields(PolicyItem = PolicyItem) > 0
            }
        }

        return PreviewPolicies(Policies = PolicyList)
    }

    fun PreviewPolicies(Policies: List<CustomerPolicy>): List<ParsedRecord> {
        return Policies.map { PolicyItem ->
            val FieldCount = CountPolicyFields(PolicyItem = PolicyItem)
            ParsedRecord(
                PolicyNumber = PolicyItem.PolicyNumber.ifEmpty { "Unknown policy" },
                PrimaryLine = PolicyItem.HolderName.ifEmpty { "No holder name" },
                SecondaryLine = BuildPolicySummary(PolicyItem = PolicyItem),
                FieldCount = FieldCount,
                Warning = when {
                    PolicyItem.PolicyNumber.isEmpty() -> "No policy number found"
                    PolicyItem.HolderName.isEmpty() -> "No holder name found"
                    FieldCount < 3 -> "Only $FieldCount fields matched"
                    else -> ""
                }
            )
        }
    }

    fun PreviewProfilePatches(
        Patches: List<CustomerPolicy>,
        NameMap: Map<String, String>
    ): List<ParsedRecord> {
        return Patches.map { PatchItem ->
            val FilledFields = ProfileFieldSummary(PatchItem = PatchItem)
            ParsedRecord(
                PolicyNumber = PatchItem.PolicyNumber.ifEmpty { "Unknown policy" },
                PrimaryLine = NameMap[PatchItem.PolicyNumber].orEmpty().ifEmpty { "Unknown customer" },
                SecondaryLine = if (FilledFields.isEmpty()) {
                    "No personal fields matched"
                } else {
                    FilledFields.joinToString(" · ")
                },
                FieldCount = FilledFields.size,
                Warning = if (FilledFields.isEmpty()) "Nothing to update" else ""
            )
        }
    }

    private fun ProfileFieldSummary(PatchItem: CustomerPolicy): List<String> {
        val PartList = mutableListOf<String>()
        if (PatchItem.MobileNumber.isNotEmpty()) PartList.add("Mobile")
        if (PatchItem.Email.isNotEmpty()) PartList.add("Email")
        if (PatchItem.Address.isNotEmpty()) PartList.add("Address")
        if (PatchItem.Dob.isNotEmpty()) PartList.add("DOB")
        if (PatchItem.Gender.isNotEmpty()) PartList.add("Gender")
        if (PatchItem.Education.isNotEmpty()) PartList.add("Education")
        if (PatchItem.Occupation.isNotEmpty()) PartList.add("Occupation")
        if (PatchItem.MaritalStatus.isNotEmpty()) PartList.add("Marital status")
        if (PatchItem.AnnualIncome.isNotEmpty()) PartList.add("Annual income")
        return PartList
    }

    private fun CommitCustomerProfiles(
        ContextRef: Context,
        SessionId: String,
        Patches: List<CustomerPolicy>,
        Gaps: List<SessionGap>
    ): Int {
        PolicyRepository.SaveSessionGaps(
            ContextRef = ContextRef,
            SessionId = SessionId,
            Gaps = Gaps
        )
        if (Patches.isEmpty()) return 0

        val ExistingList = PolicyRepository.GetCustomerPolicies(
            ContextRef = ContextRef,
            SessionId = SessionId
        ).toMutableList()
        if (ExistingList.isEmpty()) return 0

        val ChangeLog = mutableListOf<RecordFieldChange>()
        val SkippedGaps = mutableListOf<SessionGap>()
        var UpdatedCount = 0

        for (PatchItem in Patches) {
            if (PatchItem.PolicyNumber.isEmpty()) continue
            val MatchIndex = ExistingList.indexOfFirst { ExistingItem ->
                ExistingItem.PolicyNumber == PatchItem.PolicyNumber
            }
            if (MatchIndex < 0) {
                SkippedGaps.add(
                    SessionGap(
                        PolicyNumber = PatchItem.PolicyNumber,
                        CustomerName = PatchItem.HolderName,
                        SeenAt = System.currentTimeMillis()
                    )
                )
                continue
            }
            val MergeOutcomeVal = RecordMerge.MergePolicy(
                ExistingItem = ExistingList[MatchIndex],
                IncomingItem = PatchItem
            )
            if (MergeOutcomeVal.Record != ExistingList[MatchIndex]) UpdatedCount++
            ExistingList[MatchIndex] = MergeOutcomeVal.Record
            ChangeLog.addAll(MergeOutcomeVal.Changes)
        }

        if (SkippedGaps.isNotEmpty()) {
            PolicyRepository.SaveSessionGaps(
                ContextRef = ContextRef,
                SessionId = SessionId,
                Gaps = SkippedGaps
            )
        }

        PolicyRepository.SaveFieldChanges(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.POLICY,
            SessionId = SessionId,
            Changes = ChangeLog
        )
        PolicyRepository.SaveCustomerPolicies(
            ContextRef = ContextRef,
            Policies = ExistingList,
            SessionId = SessionId,
            CapturePolicyDetails = PolicyRepository.GetSessionReference(
                ContextRef = ContextRef,
                SessionId = SessionId
            )?.CapturePolicyDetails == true
        )
        LastCommitResult = CommitResult(AddedCount = 0, UpdatedCount = UpdatedCount)
        return UpdatedCount
    }

    data class CommitResult(val AddedCount: Int, val UpdatedCount: Int) {
        val TotalCount: Int get() = AddedCount + UpdatedCount
    }

    private fun CommitPolicy(
        ContextRef: Context,
        SessionId: String,
        Nodes: List<String>,
        PolicyRecords: List<CustomerPolicy>,
        CapturePolicyDetails: Boolean
    ): Int {
        val DashboardPolicies = if (PolicyRecords.isNotEmpty()) {
            PolicyRecords
        } else if (IsPolicyDashboardNodes(Nodes = Nodes)) {
            ScreenDataParser.ParsePolicyDashboard(Nodes = Nodes)
        } else {
            emptyList()
        }
        val ParsedPolicies = DashboardPolicies.ifEmpty {
            listOf(BuildPolicy(Nodes = Nodes)).filter { PolicyItem ->
                CountPolicyFields(PolicyItem = PolicyItem) > 0
            }
        }
        if (ParsedPolicies.isEmpty()) return 0

        val ExistingList = PolicyRepository.GetCustomerPolicies(
            ContextRef = ContextRef,
            SessionId = SessionId
        ).toMutableList()
        val ChangeLog = mutableListOf<RecordFieldChange>()
        var AddedCount = 0
        var UpdatedCount = 0

        for (PolicyItem in ParsedPolicies.reversed()) {
            val MatchIndex = ExistingList.indexOfFirst { ExistingItem ->
                PolicyItem.PolicyNumber.isNotEmpty() && ExistingItem.PolicyNumber == PolicyItem.PolicyNumber
            }
            if (MatchIndex >= 0) {
                val MergeOutcomeVal = RecordMerge.MergePolicy(
                    ExistingItem = ExistingList[MatchIndex],
                    IncomingItem = PolicyItem
                )
                if (MergeOutcomeVal.Record != ExistingList[MatchIndex]) UpdatedCount++
                ExistingList[MatchIndex] = MergeOutcomeVal.Record
                ChangeLog.addAll(MergeOutcomeVal.Changes)
            } else {
                ExistingList.add(0, PolicyItem)
                AddedCount++
            }
        }

        PolicyRepository.SaveFieldChanges(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.POLICY,
            SessionId = SessionId,
            Changes = ChangeLog
        )
        PolicyRepository.SaveCustomerPolicies(
            ContextRef = ContextRef,
            Policies = ExistingList,
            SessionId = SessionId,
            CapturePolicyDetails = CapturePolicyDetails
        )
        LastCommitResult = CommitResult(AddedCount = AddedCount, UpdatedCount = UpdatedCount)
        return AddedCount + UpdatedCount
    }

    private fun CountPolicyFields(PolicyItem: CustomerPolicy): Int {
        val Candidates = listOf(
            PolicyItem.PolicyNumber, PolicyItem.HolderName, PolicyItem.SumAssured,
            PolicyItem.TermPPT, PolicyItem.DateOfCommencement, PolicyItem.EndOfPremiumPayingTerm,
            PolicyItem.DateOfMaturity, PolicyItem.MobileNumber, PolicyItem.Dob,
            PolicyItem.PremiumAmount, PolicyItem.Status, PolicyItem.PlanName,
            PolicyItem.AutoPay, PolicyItem.RenewalDueDate, PolicyItem.KycStatus,
            PolicyItem.NeftStatus, PolicyItem.Address, PolicyItem.Email,
            PolicyItem.Gender, PolicyItem.Education, PolicyItem.Occupation,
            PolicyItem.MaritalStatus, PolicyItem.AnnualIncome,
            PolicyItem.CommissionDateOfPremiumPayment, PolicyItem.CommissionDateOfPayment,
            PolicyItem.CommissionType, PolicyItem.BonusCommission,
            PolicyItem.CommissionPaidAmount
        )
        return Candidates.count { it.isNotEmpty() }
    }

    private fun BuildPolicySummary(PolicyItem: CustomerPolicy): String {
        val Parts = mutableListOf<String>()
        if (PolicyItem.PlanName.isNotEmpty()) Parts.add(PolicyItem.PlanName)
        if (PolicyItem.PremiumAmount.isNotEmpty()) {
            val FrequencyText = PolicyItem.PremiumFrequency
                .takeIf { ItValue -> ItValue.isNotEmpty() }
                ?.let { ItValue -> "/$ItValue" }
                .orEmpty()
            Parts.add("${PolicyItem.PremiumAmount}$FrequencyText")
        }
        if (PolicyItem.RenewalDueDate.isNotEmpty()) Parts.add("Due ${PolicyItem.RenewalDueDate}")
        if (PolicyItem.SumAssured.isNotEmpty()) Parts.add("SA ${PolicyItem.SumAssured}")
        if (PolicyItem.TermPPT.isNotEmpty()) Parts.add("Term ${PolicyItem.TermPPT}")
        if (PolicyItem.DateOfMaturity.isNotEmpty()) Parts.add("Matures ${PolicyItem.DateOfMaturity}")
        return if (Parts.isEmpty()) "No plan details matched" else Parts.joinToString(" · ")
    }

    private fun IsPolicyDashboardNodes(Nodes: List<String>): Boolean {
        return Nodes.any { NodeText ->
            NodeText.contains("Policy Dashboard", ignoreCase = true) ||
                    NodeText.contains("Based on selected filters", ignoreCase = true) ||
                    NodeText.startsWith("Send Reminder", ignoreCase = true)
        }
    }


    private fun PreviewPs(Nodes: List<String>): List<ParsedRecord> {
        return PsDataParser.ParsePsPolicies(Nodes = Nodes).map { PsItem ->
            val FieldCount = listOf(
                PsItem.PolicyNumber, PsItem.HolderName, PsItem.PremiumAmount,
                PsItem.Doc, PsItem.Fup, PsItem.Status
            ).count { it.isNotEmpty() }

            ParsedRecord(
                PolicyNumber = PsItem.PolicyNumber,
                PrimaryLine = PsItem.HolderName.ifEmpty { "No holder name" },
                SecondaryLine = listOf(PsItem.PremiumAmount, PsItem.Fup)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · ")
                    .ifEmpty { "No premium or FUP matched" },
                FieldCount = FieldCount,
                Warning = when {
                    PsItem.HolderName.isEmpty() -> "No holder name found"
                    FieldCount < 3 -> "Only $FieldCount fields matched"
                    else -> ""
                }
            )
        }
    }

    private fun CommitPs(ContextRef: Context, SessionId: String, Nodes: List<String>): Int {
        val ParsedList = PsDataParser.ParsePsPolicies(Nodes = Nodes)
        if (ParsedList.isEmpty()) return 0

        val ExistingList = PolicyRepository.GetPsPolicies(
            ContextRef = ContextRef,
            SessionId = SessionId
        ).toMutableList()
        val KnownNumbers = ExistingList.map { it.PolicyNumber }.toMutableSet()
        var AddedCount = 0
        for (PsItem in ParsedList) {
            if (PsItem.PolicyNumber.isNotEmpty() && !KnownNumbers.add(PsItem.PolicyNumber)) continue
            ExistingList.add(AddedCount, PsItem)
            AddedCount++
        }
        PolicyRepository.SavePsPolicies(
            ContextRef = ContextRef,
            Policies = ExistingList,
            SessionId = SessionId
        )
        LastCommitResult = CommitResult(AddedCount = AddedCount, UpdatedCount = 0)
        return AddedCount
    }


    private fun PreviewFup(Nodes: List<String>): List<ParsedRecord> {
        return PreviewFupRecords(Records = FupDataParser.ParseRenewalHistory(Nodes = Nodes))
    }

    fun PreviewFupRecords(Records: List<FupPolicy>): List<ParsedRecord> {
        return Records.map { FupItem ->
            val FieldCount = listOf(
                FupItem.PolicyNumber, FupItem.PlanName, FupItem.HolderName,
                FupItem.PremiumAmount, FupItem.DueDate, FupItem.PaymentDate,
                FupItem.ModeOfPayment, FupItem.Status
            ).count { it.isNotEmpty() }

            ParsedRecord(
                PolicyNumber = FupItem.PolicyNumber.ifEmpty { "Unknown policy" },
                PrimaryLine = FupItem.HolderName.ifEmpty { FupItem.PlanName.ifEmpty { "No holder name" } },
                SecondaryLine = BuildFupSummary(FupItem = FupItem),
                FieldCount = FieldCount,
                Warning = when {
                    FupItem.PolicyNumber.isEmpty() -> "No policy number found"
                    FupItem.PaymentDate.isEmpty() && FupItem.DueDate.isEmpty() ->
                        "No due or payment date matched"
                    FieldCount < 4 -> "Only $FieldCount fields matched"
                    else -> ""
                }
            )
        }
    }

    private fun BuildFupSummary(FupItem: FupPolicy): String {
        val Parts = mutableListOf<String>()
        if (FupItem.PremiumAmount.isNotEmpty()) Parts.add(FupItem.PremiumAmount)
        if (FupItem.DueDate.isNotEmpty()) Parts.add("Due ${FupItem.DueDate}")
        if (FupItem.PaymentDate.isNotEmpty()) Parts.add("Paid ${FupItem.PaymentDate}")
        if (FupItem.ModeOfPayment.isNotEmpty()) Parts.add(FupItem.ModeOfPayment)
        if (FupItem.Status.isNotEmpty()) Parts.add(FupItem.Status)
        return if (Parts.isEmpty()) "No renewal details matched" else Parts.joinToString(" · ")
    }

    private fun CommitFup(
        ContextRef: Context,
        SessionId: String,
        Nodes: List<String>,
        FupRecords: List<FupPolicy>
    ): Int {
        val ParsedList = FupRecords.ifEmpty {
            FupDataParser.ParseRenewalHistory(Nodes = Nodes)
        }
        if (ParsedList.isEmpty()) return 0

        val ExistingList = PolicyRepository.GetFupPolicies(
            ContextRef = ContextRef,
            SessionId = SessionId
        ).toMutableList()
        val ChangeLog = mutableListOf<RecordFieldChange>()
        var AddedCount = 0
        var UpdatedCount = 0

        for (FupItem in ParsedList) {
            val MatchIndex = ExistingList.indexOfFirst { ExistingItem ->
                FupItem.PolicyNumber.isNotEmpty() &&
                        RecordMerge.RenewalKey(RecordItem = ExistingItem) ==
                        RecordMerge.RenewalKey(RecordItem = FupItem)
            }
            if (MatchIndex >= 0) {
                val MergeOutcomeVal = RecordMerge.MergeRenewal(
                    ExistingItem = ExistingList[MatchIndex],
                    IncomingItem = FupItem
                )
                if (MergeOutcomeVal.Record != ExistingList[MatchIndex]) UpdatedCount++
                ExistingList[MatchIndex] = MergeOutcomeVal.Record
                ChangeLog.addAll(MergeOutcomeVal.Changes)
            } else {
                ExistingList.add(AddedCount, FupItem)
                AddedCount++
            }
        }

        PolicyRepository.SaveFieldChanges(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.FUP,
            SessionId = SessionId,
            Changes = ChangeLog
        )
        PolicyRepository.SaveFupPolicies(
            ContextRef = ContextRef,
            Policies = ExistingList,
            SessionId = SessionId
        )
        LastCommitResult = CommitResult(AddedCount = AddedCount, UpdatedCount = UpdatedCount)
        return AddedCount + UpdatedCount
    }


    private fun FindPolicyNumber(Nodes: List<String>): String {
        for (NodeText in Nodes) {
            val Trimmed = NodeText.trim()
            if (POLICY_NO_REGEX.matcher(Trimmed).matches()) return Trimmed
        }
        return ""
    }

    private fun FindHolderName(Nodes: List<String>): String {
        for (NodeText in Nodes) {
            val Trimmed = NodeText.trim()
            if (Trimmed.length !in 4..40) continue
            if (NAME_STOP_WORDS.contains(Trimmed.uppercase())) continue
            if (Trimmed.any { it.isDigit() }) continue
            if (HOLDER_NAME_REGEX.matches(Trimmed)) return Trimmed
        }
        return ""
    }

    private fun FindFirstCurrency(Nodes: List<String>): String {
        for (NodeText in Nodes) {
            val Trimmed = NodeText.trim()
            if (Trimmed.contains("₹") && Trimmed.any { it.isDigit() }) return Trimmed
        }
        return ""
    }

    private fun FindStatus(Nodes: List<String>): String {
        for (NodeText in Nodes) {
            val Trimmed = NodeText.trim()
            if (Trimmed.equals("Inforce", ignoreCase = true)) return "Inforce"
            if (Trimmed.equals("Lapsed", ignoreCase = true)) return "Lapsed"
        }
        return ""
    }
}
