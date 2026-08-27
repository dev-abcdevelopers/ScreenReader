@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

object PolicySearchParser {

    private val POLICY_NUMBER_REGEX = Regex("(?<!\\d)(\\d{8,10})(?!\\d)")
    private val COUNT_IN_LINE_REGEX =
        Regex("(\\d{1,3})\\s*relevant\\s+results?", RegexOption.IGNORE_CASE)
    private val COUNT_ONLY_REGEX = Regex("^\\d{1,3}$")
    private val PLAN_LINE_REGEX = Regex("^\\d{1,4}\\s*[-–]\\s*\\S")
    private val HOLDER_NAME_REGEX = Regex("^[\\p{L}.'][\\p{L}.'\\s]{1,60}$")
    private val AGE_REGEX = Regex("^(\\d{1,3})\\s*year", RegexOption.IGNORE_CASE)
    private val MOBILE_REGEX = Regex("^[6-9]\\d{9}$")

    private const val SCREEN_TITLE = "Search for Policies"
    private const val RESULTS_LABEL = "Relevant results"

    private val SCREEN_MARKERS = listOf(
        "search for policies and customers",
        "search for policies",
        "relevant results",
        "recent searches"
    )

    private val EMPTY_MARKERS = listOf(
        "no results",
        "no relevant result",
        "no matching",
        "nothing found",
        "no record found"
    )

    private val NON_HOLDER_WORDS = setOf(
        "policies", "customers", "recent searches", "relevant results", "search",
        "cancel", "clear", "back", "close", "years", "year", "policy", "customer"
    )

    data class ResultRow(
        val PolicyNumber: String = "",
        val PlanText: String = "",
        val HolderName: String = "",
        val MobileNumber: String = "",
        val AgeText: String = ""
    )

    fun IsSearchScreen(Nodes: List<String>): Boolean {
        if (Nodes.isEmpty()) return false
        val HasTitle = Nodes.any { NodeText ->
            NodeText.contains(SCREEN_TITLE, ignoreCase = true)
        }
        if (HasTitle) return true
        val MarkerHits = SCREEN_MARKERS.count { MarkerText ->
            Nodes.any { NodeText -> NodeText.contains(MarkerText, ignoreCase = true) }
        }
        return MarkerHits >= 2
    }

    fun ResultCount(Nodes: List<String>): Int? {
        val CleanNodes = CleanOf(Nodes = Nodes)
        for ((NodeIdx, NodeText) in CleanNodes.withIndex()) {
            val InlineMatch = COUNT_IN_LINE_REGEX.find(NodeText)
            if (InlineMatch != null) return InlineMatch.groupValues[1].toIntOrNull()
            if (!NodeText.equals(RESULTS_LABEL, ignoreCase = true)) continue
            val PreviousText = CleanNodes.getOrNull(NodeIdx - 1).orEmpty()
            if (COUNT_ONLY_REGEX.matches(PreviousText)) return PreviousText.toIntOrNull()
        }
        return null
    }

    fun IsEmptyResult(Nodes: List<String>): Boolean {
        if (ResultCount(Nodes = Nodes) == 0) return true
        return Nodes.any { NodeText ->
            EMPTY_MARKERS.any { MarkerText -> NodeText.contains(MarkerText, ignoreCase = true) }
        }
    }

    fun HasResults(Nodes: List<String>): Boolean {
        val CountVal = ResultCount(Nodes = Nodes)
        if (CountVal != null && CountVal > 0) return true
        return ParseResults(Nodes = Nodes).isNotEmpty()
    }

    fun ContainsPolicyNumber(Nodes: List<String>, PolicyNumber: String): Boolean {
        if (PolicyNumber.isEmpty()) return false
        val BoundedRegex = Regex("(?<!\\d)" + Regex.escape(PolicyNumber) + "(?!\\d)")
        return Nodes.any { NodeText -> BoundedRegex.containsMatchIn(NodeText) }
    }

    fun ParseResults(Nodes: List<String>): List<ResultRow> {
        val CleanNodes = CleanOf(Nodes = Nodes)
        val RowMap = linkedMapOf<String, ResultRow>()
        for ((NodeIdx, NodeText) in CleanNodes.withIndex()) {
            if (MOBILE_REGEX.matches(NodeText.trim())) continue
            val NumberMatch = POLICY_NUMBER_REGEX.find(NodeText) ?: continue
            if (NodeText.length > NumberMatch.value.length + 4) continue
            val PolicyNumber = NumberMatch.value
            if (RowMap.containsKey(PolicyNumber)) continue

            var PlanText = ""
            var HolderName = ""
            var MobileText = ""
            var AgeText = ""
            var LookIdx = NodeIdx + 1
            val LookLimit = minOf(CleanNodes.size, NodeIdx + 9)
            while (LookIdx < LookLimit) {
                val LookText = CleanNodes[LookIdx].trim()
                if (POLICY_NUMBER_REGEX.matches(LookText) && !MOBILE_REGEX.matches(LookText)) break
                when {
                    PlanText.isEmpty() && PLAN_LINE_REGEX.containsMatchIn(LookText) -> {
                        PlanText = LookText
                    }

                    MobileText.isEmpty() && MOBILE_REGEX.matches(LookText) -> {
                        MobileText = LookText
                    }

                    AgeText.isEmpty() && AGE_REGEX.containsMatchIn(LookText) -> {
                        AgeText = AGE_REGEX.find(LookText)?.groupValues?.get(1).orEmpty()
                    }

                    HolderName.isEmpty() && IsHolderName(TextValue = LookText) -> {
                        HolderName = LookText
                    }
                }
                LookIdx++
            }
            RowMap[PolicyNumber] = ResultRow(
                PolicyNumber = PolicyNumber,
                PlanText = PlanText,
                HolderName = HolderName,
                MobileNumber = MobileText,
                AgeText = AgeText
            )
        }
        return RowMap.values.toList()
    }

    fun RowFor(Nodes: List<String>, PolicyNumber: String): ResultRow? {
        if (PolicyNumber.isEmpty()) return null
        return ParseResults(Nodes = Nodes).firstOrNull { RowItem ->
            RowItem.PolicyNumber == PolicyNumber
        }
    }

    fun ResultsFloorMarkers(): List<String> {
        return listOf(RESULTS_LABEL, "Recent Searches")
    }

    fun DescribeResults(Nodes: List<String>): String {
        val RowList = ParseResults(Nodes = Nodes)
        if (RowList.isEmpty()) return "none"
        return RowList.joinToString(separator = ";") { RowItem ->
            "${RowItem.PolicyNumber}/${RowItem.HolderName.ifEmpty { "?" }}" +
                    if (RowItem.MobileNumber.isEmpty()) "" else "/mob"
        }
    }

    fun FirstNameOf(NameText: String): String {
        return NameText.trim()
            .split(Regex("\\s+"))
            .firstOrNull { NamePart -> NamePart.length >= 3 }
            .orEmpty()
            .filter { NameChar -> NameChar.isLetter() }
    }

    private fun IsHolderName(TextValue: String): Boolean {
        val Trimmed = TextValue.trim()
        if (Trimmed.length < 3) return false
        if (NON_HOLDER_WORDS.contains(Trimmed.lowercase())) return false
        if (MOBILE_REGEX.matches(Trimmed)) return false
        if (AGE_REGEX.containsMatchIn(Trimmed)) return false
        if (PLAN_LINE_REGEX.containsMatchIn(Trimmed)) return false
        return HOLDER_NAME_REGEX.matches(Trimmed)
    }

    private fun CleanOf(Nodes: List<String>): List<String> {
        return Nodes
            .flatMap { NodeText -> NodeText.split("\n") }
            .map { NodeText -> NodeText.trim() }
            .filter { NodeText -> NodeText.isNotEmpty() }
    }
}
