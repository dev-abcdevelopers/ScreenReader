@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.parser

import java.util.regex.Pattern

object ScreenDataParser {

    private val DATE_REGEX = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$")

    fun IsValidDate(InputStr: String): Boolean {
        return DATE_REGEX.matcher(InputStr.trim()).matches()
    }

    fun ParseDetailedPolicyView(Nodes: List<String>): Map<String, String> {
        val ResultMap = mutableMapOf<String, String>()
        val CleanNodes = Nodes.map { it.trim() }.filter { it.isNotEmpty() }

        for (Index in CleanNodes.indices) {
            val TextValue = CleanNodes[Index]
            when {
                TextValue.equals("Sum Assured", ignoreCase = true) && Index + 1 < CleanNodes.size -> {
                    ResultMap["sumAssured"] = CleanNodes[Index + 1]
                }
                (TextValue.equals("Term/PPT", ignoreCase = true) || TextValue.equals("Term / PPT", ignoreCase = true)) && Index + 1 < CleanNodes.size -> {
                    ResultMap["termPPT"] = CleanNodes[Index + 1]
                }
                TextValue.equals("Date of Commencement", ignoreCase = true) && Index + 1 < CleanNodes.size -> {
                    ResultMap["dateOfCommencement"] = CleanNodes[Index + 1]
                }
                TextValue.equals("End of Premium Paying Term", ignoreCase = true) && Index + 1 < CleanNodes.size -> {
                    ResultMap["endOfPremiumPayingTerm"] = CleanNodes[Index + 1]
                }
                TextValue.equals("Date of Maturity", ignoreCase = true) && Index + 1 < CleanNodes.size -> {
                    ResultMap["dateOfMaturity"] = CleanNodes[Index + 1]
                }
            }
        }
        return ResultMap
    }

    fun ParseCustomerProfile(Nodes: List<String>): Map<String, String> {
        val ResultMap = mutableMapOf<String, String>()
        val CleanNodes = Nodes.map { it.trim() }.filter { it.isNotEmpty() }

        val PhonePattern = Pattern.compile("^[6-9]\\d{9}$")
        for (Index in CleanNodes.indices) {
            val TextValue = CleanNodes[Index]
            if (PhonePattern.matcher(TextValue).matches()) {
                ResultMap["mobileNumber"] = TextValue
            } else if (IsValidDate(InputStr = TextValue) && !ResultMap.containsKey("dob")) {
                ResultMap["dob"] = TextValue
            }
        }
        return ResultMap
    }
}
