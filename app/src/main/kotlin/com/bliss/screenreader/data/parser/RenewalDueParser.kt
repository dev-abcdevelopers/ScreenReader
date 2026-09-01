@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.RenewalDuePolicy

object RenewalDueParser {

    const val LABEL_RENEWAL_DUE_DATE = "Renewal Due Date"
    const val LABEL_GRACE_EXPIRY_DATE = "Grace Expiry Date"

    private const val LABEL_AUTO_PAY = "Auto Pay"
    private const val LABEL_PREMIUM = "Premium Amount"

    private val POLICY_LINE_REGEX = Regex("^(\\d{8,10})\\s*\\|\\s*(.+)$")
    private val POLICY_NUM_REGEX = Regex("^(\\d{8,10})$")
    private val PLAN_LABEL_REGEX = Regex("^\\d{3,4}\\s*-\\s*.+")

    private val DATE_REGEX = Regex(
        "^(\\d{1,2}[\\s-]+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s-]+\\d{4}|" +
                "\\d{2}/\\d{2}/\\d{4})$",
        RegexOption.IGNORE_CASE
    )
    private val CURRENCY_REGEX = Regex("₹\\s*[\\d,]+")

    private val URGENCY_REGEX = Regex(
        "(?i)^(Renewal due|Grace Expiring|Premium due)\\b.*$"
    )

    private val AUTO_PAY_VALUES = setOf("enabled", "disabled", "active", "inactive", "-", "na", "n/a")

    private val CUSTOMER_COUNT_REGEX = Regex("(?i)^(\\d{1,4})$")
    private const val LABEL_RENEWAL_POLICIES = "Renewal Policies"
    private const val MAX_GROUP_LOOKBACK = 8

    private val NON_VALUE_PREFIXES = listOf(
        LABEL_AUTO_PAY, LABEL_PREMIUM, LABEL_RENEWAL_DUE_DATE, LABEL_GRACE_EXPIRY_DATE,
        "All Renewals Due", "Renewals Due", "Renewal Policies", "All Renewal Policies",
        "Send Reminder", "Call Customer", "View All", "View all",
        "Based on Selected Filters", "Policies", "Policy(ies)",
        "Page", "of ", "Next ", "Last ", "All Policies", "All Products",
        "Filter", "Filter & Sort", "Special Revival", "Campaign Eligible",
        "Policy View", "Customer View", "Timeline", "Auto Pay"
    )

    data class CustomerGroup(
        val HolderName: String,
        val PolicyCount: Int,
        val HasViewAll: Boolean
    )

    fun Parse(Nodes: List<String>, HolderName: String = ""): List<RenewalDuePolicy> {
        val CleanNodes = CleanUp(Nodes = Nodes)
        if (CleanNodes.isEmpty()) return emptyList()

        val AnchorIndexes = CleanNodes.indices.filter { Index ->
            IsPolicyAnchor(TextValue = CleanNodes[Index])
        }
        if (AnchorIndexes.isEmpty()) return emptyList()

        val ResultList = mutableListOf<RenewalDuePolicy>()
        for ((AnchorPosition, StartIndex) in AnchorIndexes.withIndex()) {
            val EndIndex = AnchorIndexes.getOrNull(AnchorPosition + 1) ?: CleanNodes.size
            val WindowStart = if (AnchorPosition == 0) 0 else AnchorIndexes[AnchorPosition - 1]
            val UrgencyText = CleanNodes
                .subList(WindowStart, StartIndex)
                .lastOrNull { NodeText -> URGENCY_REGEX.matches(NodeText) }
                .orEmpty()
            ResultList.add(
                ParseCard(
                    CardNodes = CleanNodes.subList(StartIndex, EndIndex),
                    UrgencyText = UrgencyText,
                    HolderName = HolderName
                )
            )
        }
        return ResultList
    }

    fun ReadCustomerGroups(Nodes: List<String>): List<CustomerGroup> {
        val CleanNodes = CleanUp(Nodes = Nodes)
        val ResultList = mutableListOf<CustomerGroup>()
        for ((NodeIdx, NodeText) in CleanNodes.withIndex()) {
            if (!NodeText.startsWith(LABEL_RENEWAL_POLICIES, ignoreCase = true)) continue
            var CountValue = 0
            var NameIdx = -1
            var BackIdx = NodeIdx - 1
            while (BackIdx >= 0 && NodeIdx - BackIdx <= MAX_GROUP_LOOKBACK) {
                val Candidate = CleanNodes[BackIdx]
                if (Candidate.startsWith(LABEL_RENEWAL_POLICIES, ignoreCase = true)) break
                if (CountValue == 0 && CUSTOMER_COUNT_REGEX.matches(Candidate)) {
                    CountValue = Candidate.toIntOrNull() ?: 0
                } else if (IsPlausibleCustomerName(TextValue = Candidate)) {
                    NameIdx = BackIdx
                    break
                }
                BackIdx--
            }
            if (NameIdx < 0) continue
            val HasViewAll = CleanNodes
                .subList(NodeIdx, minOf(NodeIdx + 4, CleanNodes.size))
                .any { Candidate -> Candidate.trim().equals("View All", ignoreCase = true) }
            ResultList.add(
                CustomerGroup(
                    HolderName = CleanNodes[NameIdx].trim(),
                    PolicyCount = CountValue,
                    HasViewAll = HasViewAll
                )
            )
        }
        return ResultList
    }

    fun IsPlausibleCustomerName(TextValue: String): Boolean {
        val TrimmedText = TextValue.trim()
        if (TrimmedText.length !in 2..60) return false
        if (TrimmedText.any { CharValue -> CharValue.isDigit() }) return false
        if (IsNonValueNode(TextValue = TrimmedText)) return false
        if (RenewalDueRange.IsRangeLabel(TextValue = TrimmedText)) return false
        return ScreenDataParser.IsPlausibleHolderName(TextValue = TrimmedText)
    }

    private fun CleanUp(Nodes: List<String>): List<String> {
        val CleanNodes = Nodes
            .flatMap { NodeText -> NodeText.split("\n") }
            .map { NodeText -> NodeText.trim() }
            .filter { NodeText -> NodeText.isNotEmpty() }
        return CurrencyNodes.Join(CleanNodes = CleanNodes)
    }

    private fun ParseCard(
        CardNodes: List<String>,
        UrgencyText: String,
        HolderName: String
    ): RenewalDuePolicy {
        val AnchorMatch = POLICY_LINE_REGEX.find(CardNodes.first())
        val PolicyNumber = AnchorMatch?.groupValues?.get(1) ?: CardNodes.first().trim()
        var PlanLabel = AnchorMatch?.groupValues?.get(2)?.trim().orEmpty()
        if (PlanLabel.isEmpty()) {
            PlanLabel = CardNodes.drop(1).firstOrNull { NodeText ->
                PLAN_LABEL_REGEX.matches(NodeText)
            }.orEmpty()
        }
        val (PlanCode, PlanName) = PlanIdentity.Split(RawLabel = PlanLabel)

        val ClaimedIndexes = mutableSetOf<Int>()
        val AutoPay = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_AUTO_PAY,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText ->
                AUTO_PAY_VALUES.contains(ValueText.lowercase()) ||
                        (!DATE_REGEX.matches(ValueText) &&
                                !CURRENCY_REGEX.containsMatchIn(ValueText) &&
                                ValueText.length <= 20)
            }
        )
        val RenewalDueDate = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_RENEWAL_DUE_DATE,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> DATE_REGEX.matches(ValueText) }
        )
        val GraceExpiryDate = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_GRACE_EXPIRY_DATE,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> DATE_REGEX.matches(ValueText) }
        )
        val PremiumAmount = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_PREMIUM,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> CURRENCY_REGEX.containsMatchIn(ValueText) }
        )

        val LeftoverNodes = CardNodes
            .withIndex()
            .filter { Entry -> Entry.index > 0 && !ClaimedIndexes.contains(Entry.index) }
            .map { Entry -> Entry.value }
            .filter { NodeText -> !IsNonValueNode(TextValue = NodeText) }
            .filter { NodeText -> !URGENCY_REGEX.matches(NodeText) }

        val ResolvedPremium = PremiumAmount.ifEmpty {
            LeftoverNodes.firstOrNull { NodeText ->
                CURRENCY_REGEX.containsMatchIn(NodeText)
            }.orEmpty()
        }

        val DateLabel = when {
            RenewalDueDate.isNotEmpty() -> LABEL_RENEWAL_DUE_DATE
            GraceExpiryDate.isNotEmpty() -> LABEL_GRACE_EXPIRY_DATE
            else -> LabelFromUrgency(UrgencyText = UrgencyText)
        }
        val DateValue = when {
            RenewalDueDate.isNotEmpty() -> RenewalDueDate
            GraceExpiryDate.isNotEmpty() -> GraceExpiryDate
            else -> LeftoverNodes.firstOrNull { NodeText ->
                DATE_REGEX.matches(NodeText)
            }.orEmpty()
        }

        return RenewalDuePolicy(
            PolicyNumber = PolicyNumber,
            PlanName = PlanName,
            PlanCode = PlanCode,
            HolderName = HolderName.trim(),
            PremiumAmount = ResolvedPremium,
            PremiumFrequency = FupDataParser.FrequencyOf(PremiumText = ResolvedPremium),
            DateLabel = DateLabel,
            DateValue = DateValue,
            UrgencyText = UrgencyText.trim(),
            AutoPay = AutoPay
        )
    }

    private fun LabelFromUrgency(UrgencyText: String): String {
        val LowerText = UrgencyText.lowercase()
        if (LowerText.contains("grace")) return LABEL_GRACE_EXPIRY_DATE
        if (LowerText.contains("renewal due")) return LABEL_RENEWAL_DUE_DATE
        return ""
    }

    private fun TakeLabelledValue(
        CardNodes: List<String>,
        LabelText: String,
        ClaimedIndexes: MutableSet<Int>,
        IsExpectedValue: (String) -> Boolean
    ): String {
        val LabelIndex = CardNodes.indexOfFirst { NodeText ->
            NodeText.startsWith(LabelText, ignoreCase = true)
        }
        if (LabelIndex < 0) return ""
        ClaimedIndexes.add(LabelIndex)

        val ValueIndex = LabelIndex + 1
        if (ValueIndex >= CardNodes.size) return ""
        if (ClaimedIndexes.contains(ValueIndex)) return ""

        val ValueText = CardNodes[ValueIndex]
        if (IsPolicyAnchor(TextValue = ValueText)) return ""
        if (IsNonValueNode(TextValue = ValueText)) return ""
        if (!IsExpectedValue(ValueText)) return ""

        ClaimedIndexes.add(ValueIndex)
        return ValueText
    }

    private fun IsPolicyAnchor(TextValue: String): Boolean {
        return POLICY_LINE_REGEX.matches(TextValue) || POLICY_NUM_REGEX.matches(TextValue)
    }

    private fun IsNonValueNode(TextValue: String): Boolean {
        return NON_VALUE_PREFIXES.any { PrefixText ->
            TextValue.startsWith(PrefixText, ignoreCase = true)
        }
    }
}
