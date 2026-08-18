@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

object ContactValueSplit {

    private val RELATED_MARKER_REGEX = Regex(
        "polic\\w*\\s*\\(?\\w{0,4}\\)?\\s*related\\s*:?",
        RegexOption.IGNORE_CASE
    )

    private val LEADING_NUMBERS_REGEX = Regex("^[\\d\\s,]+")

    fun SplitMerged(RawValue: String): List<String> {
        val Trimmed = RawValue.trim()
        if (Trimmed.isEmpty()) return emptyList()
        val Pieces = RELATED_MARKER_REGEX.split(Trimmed)
        if (Pieces.size <= 1) return listOf(Trimmed)
        return Pieces
            .mapIndexed { PieceIndex, PieceText ->
                if (PieceIndex == 0) {
                    PieceText.trim()
                } else {
                    PieceText.replace(LEADING_NUMBERS_REGEX, "").trim()
                }
            }
            .filter { PieceText -> PieceText.isNotEmpty() }
    }

    fun Explode(PrimaryValue: String, OtherValues: List<String>?): List<String> {
        val Collected = mutableListOf<String>()
        Collected.addAll(SplitMerged(RawValue = PrimaryValue))
        for (OtherValue in OtherValues.orEmpty()) {
            Collected.addAll(SplitMerged(RawValue = OtherValue))
        }
        return Collected
            .map { ValueText -> ValueText.trim() }
            .filter { ValueText -> ValueText.isNotEmpty() }
            .distinct()
    }
}
