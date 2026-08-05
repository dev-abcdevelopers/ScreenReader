@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import android.content.Context
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.data.repository.PolicyRepository
import java.util.regex.Pattern

/**
 * Single dispatch point between a [CaptureMode] and the parser that handles it.
 *
 * [Preview] is cheap and side effect free, so it can run repeatedly while a
 * capture is still in progress to drive the live count on the overlay.
 * [Commit] is the only path that writes to storage, and it only runs once the
 * user has accepted the review sheet.
 */
object CaptureParsers {

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
        }
    }

    /** Writes the parsed records to storage. Returns how many were saved. */
    fun Commit(ContextRef: Context, ModeVal: CaptureMode, Nodes: List<String>): Int {
        return when (ModeVal) {
            CaptureMode.POLICY -> CommitPolicy(ContextRef = ContextRef, Nodes = Nodes)
            CaptureMode.PS -> CommitPs(ContextRef = ContextRef, Nodes = Nodes)
            CaptureMode.FUP -> CommitFup(ContextRef = ContextRef, Nodes = Nodes)
        }
    }

    // ---------------------------------------------------------------- policy

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
        val PolicyItem = BuildPolicy(Nodes = Nodes)
        val FieldCount = CountPolicyFields(PolicyItem = PolicyItem)
        if (FieldCount == 0) return emptyList()

        val WarningText = when {
            PolicyItem.PolicyNumber.isEmpty() -> "No policy number found"
            PolicyItem.HolderName.isEmpty() -> "No holder name found"
            FieldCount < 3 -> "Only $FieldCount fields matched"
            else -> ""
        }

        return listOf(
            ParsedRecord(
                PolicyNumber = PolicyItem.PolicyNumber.ifEmpty { "Unknown policy" },
                PrimaryLine = PolicyItem.HolderName.ifEmpty { "No holder name" },
                SecondaryLine = BuildPolicySummary(PolicyItem = PolicyItem),
                FieldCount = FieldCount,
                Warning = WarningText
            )
        )
    }

    private fun CommitPolicy(ContextRef: Context, Nodes: List<String>): Int {
        val PolicyItem = BuildPolicy(Nodes = Nodes)
        if (CountPolicyFields(PolicyItem = PolicyItem) == 0) return 0

        val ExistingList = PolicyRepository.GetCustomerPolicies(ContextRef = ContextRef).toMutableList()
        val MatchIndex = ExistingList.indexOfFirst {
            PolicyItem.PolicyNumber.isNotEmpty() && it.PolicyNumber == PolicyItem.PolicyNumber
        }
        if (MatchIndex >= 0) {
            ExistingList[MatchIndex] = MergePolicies(ExistingItem = ExistingList[MatchIndex], IncomingItem = PolicyItem)
        } else {
            ExistingList.add(0, PolicyItem)
        }
        PolicyRepository.SaveCustomerPolicies(ContextRef = ContextRef, Policies = ExistingList)
        return 1
    }

    /** Keeps whatever the earlier capture found rather than overwriting it with blanks. */
    private fun MergePolicies(ExistingItem: CustomerPolicy, IncomingItem: CustomerPolicy): CustomerPolicy {
        return ExistingItem.copy(
            HolderName = IncomingItem.HolderName.ifEmpty { ExistingItem.HolderName },
            SumAssured = IncomingItem.SumAssured.ifEmpty { ExistingItem.SumAssured },
            TermPPT = IncomingItem.TermPPT.ifEmpty { ExistingItem.TermPPT },
            DateOfCommencement = IncomingItem.DateOfCommencement.ifEmpty { ExistingItem.DateOfCommencement },
            EndOfPremiumPayingTerm = IncomingItem.EndOfPremiumPayingTerm.ifEmpty { ExistingItem.EndOfPremiumPayingTerm },
            DateOfMaturity = IncomingItem.DateOfMaturity.ifEmpty { ExistingItem.DateOfMaturity },
            MobileNumber = IncomingItem.MobileNumber.ifEmpty { ExistingItem.MobileNumber },
            Dob = IncomingItem.Dob.ifEmpty { ExistingItem.Dob },
            PremiumAmount = IncomingItem.PremiumAmount.ifEmpty { ExistingItem.PremiumAmount }
        )
    }

    private fun CountPolicyFields(PolicyItem: CustomerPolicy): Int {
        val Candidates = listOf(
            PolicyItem.PolicyNumber, PolicyItem.HolderName, PolicyItem.SumAssured,
            PolicyItem.TermPPT, PolicyItem.DateOfCommencement, PolicyItem.EndOfPremiumPayingTerm,
            PolicyItem.DateOfMaturity, PolicyItem.MobileNumber, PolicyItem.Dob,
            PolicyItem.PremiumAmount, PolicyItem.Status
        )
        return Candidates.count { it.isNotEmpty() }
    }

    private fun BuildPolicySummary(PolicyItem: CustomerPolicy): String {
        val Parts = mutableListOf<String>()
        if (PolicyItem.SumAssured.isNotEmpty()) Parts.add("SA ${PolicyItem.SumAssured}")
        if (PolicyItem.TermPPT.isNotEmpty()) Parts.add("Term ${PolicyItem.TermPPT}")
        if (PolicyItem.DateOfMaturity.isNotEmpty()) Parts.add("Matures ${PolicyItem.DateOfMaturity}")
        return if (Parts.isEmpty()) "No plan details matched" else Parts.joinToString(" · ")
    }

    // -------------------------------------------------------------------- ps

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

    private fun CommitPs(ContextRef: Context, Nodes: List<String>): Int {
        val ParsedList = PsDataParser.ParsePsPolicies(Nodes = Nodes)
        if (ParsedList.isEmpty()) return 0

        val ExistingList = PolicyRepository.GetPsPolicies(ContextRef = ContextRef).toMutableList()
        val KnownNumbers = ExistingList.map { it.PolicyNumber }.toMutableSet()
        var AddedCount = 0
        for (PsItem in ParsedList) {
            if (PsItem.PolicyNumber.isNotEmpty() && !KnownNumbers.add(PsItem.PolicyNumber)) continue
            ExistingList.add(AddedCount, PsItem)
            AddedCount++
        }
        PolicyRepository.SavePsPolicies(ContextRef = ContextRef, Policies = ExistingList)
        return AddedCount
    }

    // ------------------------------------------------------------------- fup

    private fun PreviewFup(Nodes: List<String>): List<ParsedRecord> {
        return FupDataParser.ParseRenewalHistory(Nodes = Nodes).map { FupItem ->
            val FieldCount = listOf(
                FupItem.PolicyNumber, FupItem.PlanName, FupItem.HolderName,
                FupItem.PremiumAmount, FupItem.DueDate, FupItem.Status
            ).count { it.isNotEmpty() }

            ParsedRecord(
                PolicyNumber = FupItem.PolicyNumber,
                PrimaryLine = FupItem.HolderName.ifEmpty { FupItem.PlanName.ifEmpty { "No holder name" } },
                SecondaryLine = listOf(FupItem.PremiumAmount, FupItem.DueDate)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · ")
                    .ifEmpty { "No premium or due date matched" },
                FieldCount = FieldCount,
                Warning = if (FieldCount < 3) "Only $FieldCount fields matched" else ""
            )
        }
    }

    private fun CommitFup(ContextRef: Context, Nodes: List<String>): Int {
        val ParsedList = FupDataParser.ParseRenewalHistory(Nodes = Nodes)
        if (ParsedList.isEmpty()) return 0

        val ExistingList = PolicyRepository.GetFupPolicies(ContextRef = ContextRef).toMutableList()
        val KnownNumbers = ExistingList.map { it.PolicyNumber }.toMutableSet()
        var AddedCount = 0
        for (FupItem in ParsedList) {
            if (FupItem.PolicyNumber.isNotEmpty() && !KnownNumbers.add(FupItem.PolicyNumber)) continue
            ExistingList.add(AddedCount, FupItem)
            AddedCount++
        }
        PolicyRepository.SaveFupPolicies(ContextRef = ContextRef, Policies = ExistingList)
        return AddedCount
    }

    // --------------------------------------------------------------- helpers

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
