@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.ContactValue
import java.util.Locale

object SheetOcrParser {

    private const val DEFAULT_MARKER = "mark as default"
    private val TITLE_PREFIXES = listOf("mobile number(", "email id(", "address(es")
    private val POLICY_NUMBER_REGEX = Regex("\\d{9}")
    private val CLOSE_LABELS = setOf("x", "×", "close")

    fun ParseSheetText(
        Lines: List<String>,
        KindVal: CustomerProfileParser.ContactKind
    ): List<ContactValue> {
        val Body = ScopeToSheet(Lines = Lines)
        if (Body.isEmpty()) return emptyList()

        val ValueBuilders = mutableListOf<MutableList<String>>()
        val RelatedGroups = mutableListOf<MutableList<String>>()
        var SeenRelatedForCurrent = false

        for (LineText in Body) {
            val JoinedLine = CustomerProfileParser.JoinSplitDigits(TextValue = LineText)

            if (CustomerProfileParser.IsRelatedPoliciesMarker(TextValue = LineText)) {
                if (ValueBuilders.isEmpty()) continue
                SeenRelatedForCurrent = true
                RelatedGroups.last().addAll(PolicyNumbersIn(TextValue = LineText))
                continue
            }

            if (SeenRelatedForCurrent && POLICY_NUMBER_REGEX.containsMatchIn(JoinedLine) &&
                LineText.none { CharacterVal -> CharacterVal.isLetter() }
            ) {
                RelatedGroups.last().addAll(PolicyNumbersIn(TextValue = LineText))
                continue
            }

            if (ValueBuilders.isEmpty() || SeenRelatedForCurrent) {
                ValueBuilders.add(mutableListOf(LineText.trim()))
                RelatedGroups.add(mutableListOf())
                SeenRelatedForCurrent = false
                continue
            }

            ValueBuilders.last().add(LineText.trim())
        }

        return ValueBuilders.mapIndexedNotNull { ValueIndex, PartList ->
            val JoinedValue = JoinWrappedValue(PartList = PartList, KindVal = KindVal)
            if (JoinedValue.isEmpty()) return@mapIndexedNotNull null
            ContactValue(
                Value = JoinedValue,
                RelatedPolicies = RelatedGroups[ValueIndex].distinct(),
                IsDefault = ValueIndex == 0
            )
        }
    }

    private fun ScopeToSheet(Lines: List<String>): List<String> {
        val Cleaned = Lines
            .map { LineText -> LineText.trim() }
            .filter { LineText -> LineText.isNotEmpty() }
            .filterNot { LineText -> CLOSE_LABELS.contains(LineText.lowercase(Locale.US)) }

        val TitleIndex = Cleaned.indexOfLast { LineText ->
            val Lower = LineText.lowercase(Locale.US)
            TITLE_PREFIXES.any { PrefixText -> Lower.startsWith(PrefixText) }
        }
        if (TitleIndex < 0) return emptyList()

        return Cleaned
            .drop(TitleIndex + 1)
            .filterNot { LineText -> LineText.lowercase(Locale.US).startsWith(DEFAULT_MARKER) }
    }

    private fun PolicyNumbersIn(TextValue: String): List<String> =
        POLICY_NUMBER_REGEX
            .findAll(CustomerProfileParser.JoinSplitDigits(TextValue = TextValue))
            .map { MatchVal -> MatchVal.value }
            .toList()


    private fun JoinWrappedValue(
        PartList: List<String>,
        KindVal: CustomerProfileParser.ContactKind
    ): String {
        if (KindVal == CustomerProfileParser.ContactKind.ADDRESS) {
            return PartList.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        return PartList.firstOrNull().orEmpty().replace(Regex("\\s+"), "").trim()
    }
}
