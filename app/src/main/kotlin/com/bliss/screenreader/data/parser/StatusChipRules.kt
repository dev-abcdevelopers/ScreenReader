@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import java.util.Locale

object StatusChipRules {

    enum class Polarity { POSITIVE, NEGATIVE, NEUTRAL }

    private const val MAX_CHIP_LENGTH = 48

    private val NEGATIVE_MARKERS = listOf(
        "not updated", "absent", "missing", "required", "lapsed", "failed", "pending", "not available"
    )

    private val POSITIVE_MARKERS = listOf(
        "updated", "available", "completed", "verified"
    )

    private val CHIP_SUBJECTS = listOf(
        "kyc", "neft", "pan", "nominee", "mobile", "address", "contact details",
        "email", "bank", "dgh"
    )

    private val EXCLUDED_EXACT = setOf(
        "first year renewal", "renewal", "single premium", "auto pay", "enabled", "disabled"
    )

    private val OVERFLOW_REGEX = Regex("^\\+\\d+$")

    fun IsStatusChip(TextValue: String): Boolean {
        val TrimmedValue = TextValue.trim()
        if (TrimmedValue.isEmpty() || TrimmedValue.length > MAX_CHIP_LENGTH) return false
        if (OVERFLOW_REGEX.matches(TrimmedValue)) return false

        val LowerValue = TrimmedValue.lowercase(Locale.ROOT)
        if (EXCLUDED_EXACT.contains(LowerValue)) return false
        if (LowerValue.contains("renewal") && !LowerValue.contains("lapsed")) return false
        if (LowerValue.startsWith("please contact")) return false

        val HasSubject = CHIP_SUBJECTS.any { SubjectText -> LowerValue.contains(SubjectText) }
        val HasMarker = NEGATIVE_MARKERS.any { MarkerText -> LowerValue.contains(MarkerText) } ||
            POSITIVE_MARKERS.any { MarkerText -> LowerValue.contains(MarkerText) }

        if (LowerValue == "lapsed" || LowerValue.startsWith("lapsed,")) return true
        return HasSubject && HasMarker
    }

    fun PolarityOf(TextValue: String): Polarity {
        val LowerValue = TextValue.trim().lowercase(Locale.ROOT)
        if (LowerValue.isEmpty()) return Polarity.NEUTRAL
        if (NEGATIVE_MARKERS.any { MarkerText -> LowerValue.contains(MarkerText) }) {
            return Polarity.NEGATIVE
        }
        if (POSITIVE_MARKERS.any { MarkerText -> LowerValue.contains(MarkerText) }) {
            return Polarity.POSITIVE
        }
        return Polarity.NEUTRAL
    }

    fun Extract(Nodes: List<String>): List<String> {
        val SeenKeys = mutableSetOf<String>()
        val ResultList = mutableListOf<String>()
        for (NodeText in Nodes) {
            val TrimmedValue = NodeText.trim().trimEnd('}', '|').trim()
            if (!IsStatusChip(TextValue = TrimmedValue)) continue
            val NormalizedKey = TrimmedValue.lowercase(Locale.ROOT)
            if (SeenKeys.add(NormalizedKey)) ResultList.add(Canonical(TextValue = TrimmedValue))
        }
        return ResultList
    }

    fun Canonical(TextValue: String): String {
        val TrimmedValue = TextValue.trim()
        val LowerValue = TrimmedValue.lowercase(Locale.ROOT)
        return when {
            LowerValue == "kyc not updated" -> "KYC not updated"
            LowerValue == "neft not updated" -> "NEFT not updated"
            LowerValue == "pan not updated" -> "PAN not updated"
            LowerValue == "pan updated" -> "PAN updated"
            else -> TrimmedValue
        }
    }

    fun Merge(ExistingChips: List<String>?, IncomingChips: List<String>?): List<String>? {
        if (IncomingChips.isNullOrEmpty()) return ExistingChips
        return IncomingChips
    }

    fun HasChip(Chips: List<String>?, NeedleText: String): Boolean {
        if (Chips.isNullOrEmpty()) return false
        val LowerNeedle = NeedleText.lowercase(Locale.ROOT)
        return Chips.any { ChipText -> ChipText.lowercase(Locale.ROOT) == LowerNeedle }
    }
}
