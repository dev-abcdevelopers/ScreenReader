@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "AssignedValueIsNeverRead", "SpellCheckingInspection"
)

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.FupPolicy


object FupDataParser {

    private val POLICY_LINE_REGEX = Regex("^(\\d{8,10})\\s*\\|\\s*(.+)$")
    private val POLICY_NUM_REGEX = Regex("^(\\d{8,10})$")

    private val DATE_REGEX = Regex(
        "^(\\d{1,2}[\\s-]+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s-]+\\d{4}|" +
                "\\d{2}/\\d{2}/\\d{4})$",
        RegexOption.IGNORE_CASE
    )
    private val CURRENCY_REGEX = Regex("₹\\s*[\\d,]+")
    private const val CURRENCY_SYMBOL = "₹"
    private val AMOUNT_TAIL_REGEX = Regex("^[\\d,]+(?:\\.\\d+)?(?:\\s*/.*)?$")
    private val HOLDER_NAME_REGEX = Regex("^[A-Za-z][A-Za-z.]*(?:\\s+[A-Za-z][A-Za-z.]*){0,4}$")

    private const val LABEL_PREMIUM = "Premium Amount"
    private const val LABEL_DUE_DATE = "Due Date"
    private const val LABEL_PAYMENT_DATE = "Payment Date"
    private const val LABEL_MODE = "Mode of Payment"
    private const val LABEL_STATUS = "Status at Time of Payment"

    private val PAYMENT_MODES = setOf(
        "cash", "cheque", "online", "upi", "neft", "others", "other", "card",
        "debit card", "credit card", "nb", "net banking", "auto debit", "ecs",
        "nach", "si", "bank"
    )

    private val STATUS_VALUES = setOf(
        "paid on time", "paid in grace period", "paid late", "paid", "unpaid",
        "inforce", "lapsed", "not paid", "pending"
    )

    private val NON_VALUE_PREFIXES = listOf(
        LABEL_PREMIUM, LABEL_DUE_DATE, LABEL_PAYMENT_DATE, LABEL_MODE, LABEL_STATUS,
        "Renewal History", "Renewals Due", "Call Customer", "Send Reminder",
        "View all", "Based on Selected Filters", "Policies", "Policy(ies)",
        "Page", "of ", "Last ", "Next ", "All Policies", "All Products", "Filter",
        "Business Metrics", "Renewal Premium Collected", "First Year Lapsation",
        "Renewal due today", "Auto Pay", "Renewal Due Date", "Ratio", "Status at"
    )

    fun ParseRenewalHistory(Nodes: List<String>): List<FupPolicy> {
        val CleanNodes = Nodes
            .flatMap { NodeText -> NodeText.split("\n") }
            .map { NodeText -> NodeText.trim() }
            .filter { NodeText -> NodeText.isNotEmpty() }

        val JoinedNodes = JoinSplitCurrency(CleanNodes = CleanNodes)
        val CardStartIndexes = JoinedNodes.indices.filter { Index ->
            IsPolicyAnchor(TextValue = JoinedNodes[Index])
        }
        if (CardStartIndexes.isEmpty()) return emptyList()

        val ResultList = mutableListOf<FupPolicy>()
        for ((AnchorPosition, StartIndex) in CardStartIndexes.withIndex()) {
            val EndIndex = CardStartIndexes.getOrNull(AnchorPosition + 1) ?: JoinedNodes.size
            val CardNodes = JoinedNodes.subList(StartIndex, EndIndex)
            ResultList.add(ParseRenewalCard(CardNodes = CardNodes))
        }
        return ResultList
    }

    fun FrequencyOf(PremiumText: String): String {
        val SlashIndex = PremiumText.indexOf('/')
        if (SlashIndex < 0) return ""
        return PremiumText.substring(SlashIndex + 1).trim()
    }

    fun AmountOf(PremiumText: String): String {
        val SlashIndex = PremiumText.indexOf('/')
        if (SlashIndex < 0) return PremiumText.trim()
        return PremiumText.substring(0, SlashIndex).trim()
    }

    private fun JoinSplitCurrency(CleanNodes: List<String>): List<String> {
        if (CleanNodes.none { NodeText -> NodeText == CURRENCY_SYMBOL }) return CleanNodes

        val ResultList = mutableListOf<String>()
        var NodeIdx = 0
        while (NodeIdx < CleanNodes.size) {
            val NodeText = CleanNodes[NodeIdx]
            val NextText = CleanNodes.getOrNull(NodeIdx + 1)
            if (NodeText == CURRENCY_SYMBOL &&
                NextText != null &&
                AMOUNT_TAIL_REGEX.matches(NextText)
            ) {
                ResultList.add(CURRENCY_SYMBOL + NextText)
                NodeIdx += 2
                continue
            }
            ResultList.add(NodeText)
            NodeIdx++
        }
        return ResultList
    }

    fun MergeRenewalRecord(ExistingRecord: FupPolicy, IncomingRecord: FupPolicy): FupPolicy {
        return ExistingRecord.copy(
            PlanName = IncomingRecord.PlanName.ifEmpty { ExistingRecord.PlanName },
            PlanCode = IncomingRecord.PlanCode.ifEmpty { ExistingRecord.PlanCode },
            HolderName = IncomingRecord.HolderName.ifEmpty { ExistingRecord.HolderName },
            PremiumAmount = IncomingRecord.PremiumAmount.ifEmpty { ExistingRecord.PremiumAmount },
            PremiumFrequency = IncomingRecord.PremiumFrequency.ifEmpty {
                ExistingRecord.PremiumFrequency
            },
            DueDate = IncomingRecord.DueDate.ifEmpty { ExistingRecord.DueDate },
            PaymentDate = IncomingRecord.PaymentDate.ifEmpty { ExistingRecord.PaymentDate },
            ModeOfPayment = IncomingRecord.ModeOfPayment.ifEmpty { ExistingRecord.ModeOfPayment },
            Status = IncomingRecord.Status.ifEmpty { ExistingRecord.Status }
        )
    }


    private fun ParseRenewalCard(CardNodes: List<String>): FupPolicy {
        val AnchorMatch = POLICY_LINE_REGEX.find(CardNodes.first())
        val PolicyNumber = AnchorMatch?.groupValues?.get(1)
            ?: CardNodes.first().trim()
        var PlanLabel = AnchorMatch?.groupValues?.get(2)?.trim().orEmpty()

        if (PlanLabel.isEmpty()) {
            PlanLabel = CardNodes.drop(1).firstOrNull { NodeText ->
                Regex("^\\d{3,4}\\s*-\\s*.+").matches(NodeText)
            }.orEmpty()
        }

        val (PlanCode, PlanName) = PlanIdentity.Split(RawLabel = PlanLabel)

        val ClaimedIndexes = mutableSetOf<Int>()
        val PremiumAmount = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_PREMIUM,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> CURRENCY_REGEX.containsMatchIn(ValueText) }
        )
        val DueDate = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_DUE_DATE,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> DATE_REGEX.matches(ValueText) }
        )
        val PaymentDate = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_PAYMENT_DATE,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> DATE_REGEX.matches(ValueText) }
        )
        val ModeOfPayment = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_MODE,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> IsPlausiblePaymentMode(TextValue = ValueText) }
        )
        val StatusValue = TakeLabelledValue(
            CardNodes = CardNodes,
            LabelText = LABEL_STATUS,
            ClaimedIndexes = ClaimedIndexes,
            IsExpectedValue = { ValueText -> IsPlausibleStatus(TextValue = ValueText) }
        )

        val LeftoverNodes = CardNodes
            .withIndex()
            .filter { Entry -> Entry.index > 0 && !ClaimedIndexes.contains(Entry.index) }
            .map { Entry -> Entry.value }
            .filter { NodeText -> !IsNonValueNode(TextValue = NodeText) }

        val RemainingDates = LeftoverNodes.filter { NodeText ->
            DATE_REGEX.matches(NodeText)
        }
        var DateCursor = 0

        val ResolvedPremium = PremiumAmount.ifEmpty {
            LeftoverNodes.firstOrNull { NodeText ->
                CURRENCY_REGEX.containsMatchIn(NodeText)
            }.orEmpty()
        }
        val ResolvedDueDate = DueDate.ifEmpty {
            RemainingDates.getOrNull(DateCursor)?.also { DateCursor++ }.orEmpty()
        }
        val ResolvedPaymentDate = PaymentDate.ifEmpty {
            RemainingDates.getOrNull(DateCursor)?.also { DateCursor++ }.orEmpty()
        }
        val ResolvedMode = ModeOfPayment.ifEmpty {
            LeftoverNodes.firstOrNull { NodeText ->
                PAYMENT_MODES.contains(NodeText.lowercase())
            }.orEmpty()
        }
        val ResolvedStatus = StatusValue.ifEmpty {
            LeftoverNodes.firstOrNull { NodeText ->
                STATUS_VALUES.contains(NodeText.lowercase())
            }.orEmpty()
        }

        return FupPolicy(
            PolicyNumber = PolicyNumber,
            PlanName = PlanName,
            PlanCode = PlanCode,
            HolderName = FindHolderName(
                CardNodes = CardNodes,
                PlanNameText = PlanLabel,
                ExcludedValues = listOf(ResolvedMode, ResolvedStatus)
            ),
            PremiumAmount = ResolvedPremium,
            PremiumFrequency = FrequencyOf(PremiumText = ResolvedPremium),
            DueDate = ResolvedDueDate,
            PaymentDate = ResolvedPaymentDate,
            ModeOfPayment = ResolvedMode,
            Status = ResolvedStatus
        )
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
        if (IsNonValueNode(TextValue = ValueText)) return ""
        if (IsPolicyAnchor(TextValue = ValueText)) return ""
        if (!IsExpectedValue(ValueText)) return ""

        ClaimedIndexes.add(ValueIndex)
        return ValueText
    }

    private fun IsPlausiblePaymentMode(TextValue: String): Boolean {
        if (PAYMENT_MODES.contains(TextValue.lowercase())) return true
        return !DATE_REGEX.matches(TextValue) &&
                !CURRENCY_REGEX.containsMatchIn(TextValue) &&
                !STATUS_VALUES.contains(TextValue.lowercase()) &&
                TextValue.length <= 30
    }

    private fun IsPlausibleStatus(TextValue: String): Boolean {
        if (STATUS_VALUES.contains(TextValue.lowercase())) return true
        return !DATE_REGEX.matches(TextValue) &&
                !CURRENCY_REGEX.containsMatchIn(TextValue) &&
                !PAYMENT_MODES.contains(TextValue.lowercase()) &&
                TextValue.length <= 40
    }

    private fun FindHolderName(
        CardNodes: List<String>,
        PlanNameText: String,
        ExcludedValues: List<String>
    ): String {
        for (NodeText in CardNodes.drop(1)) {
            if (NodeText.equals(PlanNameText, ignoreCase = true)) continue
            if (ExcludedValues.any { Excluded ->
                    Excluded.isNotEmpty() && Excluded.equals(NodeText, ignoreCase = true)
                }
            ) {
                continue
            }
            if (IsNonValueNode(TextValue = NodeText)) continue
            if (NodeText.any { CharValue -> CharValue.isDigit() }) continue
            if (PAYMENT_MODES.contains(NodeText.lowercase())) continue
            if (STATUS_VALUES.contains(NodeText.lowercase())) continue
            if (NodeText.length !in 2..60) continue
            val CandidateName = ScreenDataParser.NormaliseHolderName(TextValue = NodeText)
            if (CandidateName.length !in 2..40) continue
            if (HOLDER_NAME_REGEX.matches(CandidateName)) return NodeText
        }
        return ""
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
