@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.FupPolicy
import java.util.regex.Pattern

object FupDataParser {

    private val POLICY_LINE_REGEX = Pattern.compile("^(\\d{8,10})\\s*\\|\\s*(.+)$")
    private val POLICY_NUM_REGEX = Pattern.compile("^(\\d{8,10})$")

    fun ParseRenewalHistory(Nodes: List<String>): List<FupPolicy> {
        val CleanNodes = Nodes
            .flatMap { it.split("\n") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val PolicyList = mutableListOf<FupPolicy>()
        var Index = 0
        while (Index < CleanNodes.size) {
            val TextValue = CleanNodes[Index]
            val MatcherLine = POLICY_LINE_REGEX.matcher(TextValue)
            val MatcherNum = POLICY_NUM_REGEX.matcher(TextValue)

            if (MatcherLine.matches()) {
                val PolicyNum = MatcherLine.group(1) ?: ""
                val PlanName = MatcherLine.group(2) ?: ""
                var HolderName = ""
                var Premium = ""
                var DueDate = ""
                var Status = ""

                var InnerIdx = Index + 1
                while (InnerIdx < CleanNodes.size && InnerIdx < Index + 10) {
                    val NodeText = CleanNodes[InnerIdx]
                    if (POLICY_NUM_REGEX.matcher(NodeText).matches() || POLICY_LINE_REGEX.matcher(NodeText).matches()) break
                    if (HolderName.isEmpty() && NodeText.matches(Regex("^[A-Za-z\\s.]{3,40}$"))) {
                        HolderName = NodeText
                    } else if (Premium.isEmpty() && NodeText.contains("₹")) {
                        Premium = NodeText
                    } else if (DueDate.isEmpty() && NodeText.matches(Regex("^\\d{2}/\\d{2}/\\d{4}$"))) {
                        DueDate = NodeText
                    } else if (Status.isEmpty() && (NodeText.equals("Inforce", ignoreCase = true) || NodeText.equals("Lapsed", ignoreCase = true))) {
                        Status = NodeText
                    }
                    InnerIdx++
                }
                PolicyList.add(
                    FupPolicy(
                        PolicyNumber = PolicyNum,
                        PlanName = PlanName,
                        HolderName = HolderName,
                        PremiumAmount = Premium,
                        DueDate = DueDate,
                        Status = Status
                    )
                )
                Index = InnerIdx
            } else if (MatcherNum.matches()) {
                val PolicyNum = MatcherNum.group(1) ?: ""
                PolicyList.add(FupPolicy(PolicyNumber = PolicyNum))
                Index++
            } else {
                Index++
            }
        }

        return PolicyList
    }
}
