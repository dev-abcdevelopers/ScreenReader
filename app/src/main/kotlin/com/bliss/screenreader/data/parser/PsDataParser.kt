@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.PsPolicy
import java.util.regex.Pattern

object PsDataParser {

    private val POLICY_NO_REGEX = Pattern.compile("^\\d{9}$")
    private val DATE_REGEX = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$")

    fun ParsePsPolicies(Nodes: List<String>): List<PsPolicy> {
        val CleanNodes = Nodes
            .flatMap { it.split("\n") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val PolicyList = mutableListOf<PsPolicy>()
        var Index = 0
        while (Index < CleanNodes.size) {
            val TextValue = CleanNodes[Index]
            if (POLICY_NO_REGEX.matcher(TextValue).matches()) {
                val PolicyNo = TextValue
                var Holder = ""
                var Status = ""
                var Premium = ""
                var Doc = ""
                var Fup = ""

                var InnerIdx = Index + 1
                while (InnerIdx < CleanNodes.size && InnerIdx < Index + 12) {
                    val NodeText = CleanNodes[InnerIdx]
                    if (POLICY_NO_REGEX.matcher(NodeText).matches()) break
                    if (Holder.isEmpty() && NodeText.matches(Regex("^[A-Za-z\\s.]{3,40}$"))) {
                        Holder = NodeText
                    } else if (Premium.isEmpty() && NodeText.contains("₹")) {
                        Premium = NodeText
                    } else if (DATE_REGEX.matcher(NodeText).matches()) {
                        if (Doc.isEmpty()) Doc = NodeText else if (Fup.isEmpty()) Fup = NodeText
                    } else if (Status.isEmpty() && (NodeText.equals("Inforce", ignoreCase = true) || NodeText.equals("Lapsed", ignoreCase = true))) {
                        Status = NodeText
                    }
                    InnerIdx++
                }

                PolicyList.add(
                    PsPolicy(
                        PolicyNumber = PolicyNo,
                        HolderName = Holder,
                        PremiumAmount = Premium,
                        Doc = Doc,
                        Fup = Fup,
                        Status = Status
                    )
                )
                Index = InnerIdx
            } else {
                Index++
            }
        }
        return PolicyList
    }
}
