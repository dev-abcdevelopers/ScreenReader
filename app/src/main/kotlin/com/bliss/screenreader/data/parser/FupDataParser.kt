@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.FupPolicy


/**
 * Parses the agent app's Renewal History cards.
 *
 * Each card is anchored on its policy-number line and carries a label/value
 * grid. Accessibility traversal does not guarantee that a label is immediately
 * followed by its value: a two-column grid can surface as
 * `label, value, label, value` or as `label, label, value, value` depending on
 * how the row is composed. So each card is resolved in two passes - first by
 * pairing labels with the node that follows them, then by matching the shape of
 * whatever values are still unclaimed.
 */
object FupDataParser {

    private val POLICY_LINE_REGEX = Regex("^(\\d{8,10})\\s*\\|\\s*(.+)$")
    private val POLICY_NUM_REGEX = Regex("^(\\d{8,10})$")

    // "08 Aug 2026" is what the renewal screens use; the slash form is kept so
    // older captures still parse.
    private val DATE_REGEX = Regex(
        "^(\\d{1,2}[\\s-]+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s-]+\\d{4}|" +
                "\\d{2}/\\d{2}/\\d{4})$",
        RegexOption.IGNORE_CASE
    )
    private val CURRENCY_REGEX = Regex("₹\\s*[\\d,]+")
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

    /**
     * Labels and chrome that must never be mistaken for a value. Matched as a
     * prefix so "Premium Amount (excl. GST)" and its variants are all covered.
     */
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

        val CardStartIndexes = CleanNodes.indices.filter { Index ->
            IsPolicyAnchor(TextValue = CleanNodes[Index])
        }
        if (CardStartIndexes.isEmpty()) return emptyList()

        val ResultList = mutableListOf<FupPolicy>()
        for ((AnchorPosition, StartIndex) in CardStartIndexes.withIndex()) {
            val EndIndex = CardStartIndexes.getOrNull(AnchorPosition + 1) ?: CleanNodes.size
            val CardNodes = CleanNodes.subList(StartIndex, EndIndex)
            ResultList.add(ParseRenewalCard(CardNodes = CardNodes))
        }
        return ResultList
    }

    /** Keeps whatever an earlier snapshot found rather than blanking it out. */
    fun MergeRenewalRecord(ExistingRecord: FupPolicy, IncomingRecord: FupPolicy): FupPolicy {
        return ExistingRecord.copy(
            PlanName = IncomingRecord.PlanName.ifEmpty { ExistingRecord.PlanName },
            PlanCode = IncomingRecord.PlanCode.ifEmpty { ExistingRecord.PlanCode },
            HolderName = IncomingRecord.HolderName.ifEmpty { ExistingRecord.HolderName },
            PremiumAmount = IncomingRecord.PremiumAmount.ifEmpty { ExistingRecord.PremiumAmount },
            DueDate = IncomingRecord.DueDate.ifEmpty { ExistingRecord.DueDate },
            PaymentDate = IncomingRecord.PaymentDate.ifEmpty { ExistingRecord.PaymentDate },
            ModeOfPayment = IncomingRecord.ModeOfPayment.ifEmpty { ExistingRecord.ModeOfPayment },
            Status = IncomingRecord.Status.ifEmpty { ExistingRecord.Status }
        )
    }

    // ------------------------------------------------------------- card parse

    private fun ParseRenewalCard(CardNodes: List<String>): FupPolicy {
        val AnchorMatch = POLICY_LINE_REGEX.find(CardNodes.first())
        val PolicyNumber = AnchorMatch?.groupValues?.get(1)
            ?: CardNodes.first().trim()
        var PlanLabel = AnchorMatch?.groupValues?.get(2)?.trim().orEmpty()

        // A card whose number and plan arrive as separate nodes.
        if (PlanLabel.isEmpty()) {
            PlanLabel = CardNodes.drop(1).firstOrNull { NodeText ->
                Regex("^\\d{3,4}\\s*-\\s*.+").matches(NodeText)
            }.orEmpty()
        }

        // "934 - LIC'S JEEVAN TARUN PLAN" splits on the first hyphen only, so
        // "821 - NEW MONEY BACK PLAN - 25 YEARS" keeps its trailing term.
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
            DueDate = ResolvedDueDate,
            PaymentDate = ResolvedPaymentDate,
            ModeOfPayment = ResolvedMode,
            Status = ResolvedStatus
        )
    }

    /**
     * Returns the node after [LabelText] and records the index so the later
     * shape-based pass cannot claim it twice.
     *
     * The adjacent node is only accepted when it looks like the kind of value
     * the label describes. Without that check a column-major grid quietly
     * mis-assigns - `Premium Amount, Due Date, ₹999/Month, 28 Aug 2026` would
     * hand the premium to Due Date, because it is simply the next node along.
     */
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

    /**
     * Accepts a known mode, or anything that is clearly not one of the other
     * field types, so an unfamiliar payment mode is still picked up.
     */
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
            if (NodeText.length !in 2..40) continue
            if (PAYMENT_MODES.contains(NodeText.lowercase())) continue
            if (STATUS_VALUES.contains(NodeText.lowercase())) continue
            if (HOLDER_NAME_REGEX.matches(NodeText)) return NodeText
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
