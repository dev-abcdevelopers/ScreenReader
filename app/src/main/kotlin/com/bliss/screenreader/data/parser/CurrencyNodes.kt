@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

object CurrencyNodes {

    const val SYMBOL = "₹"

    private val AMOUNT_TAIL_REGEX = Regex("^[\\d,]+(?:\\.\\d+)?(?:\\s*/.*)?$")

    fun Join(CleanNodes: List<String>): List<String> {
        if (CleanNodes.none { NodeText -> NodeText == SYMBOL }) return CleanNodes

        val ResultList = mutableListOf<String>()
        var NodeIdx = 0
        while (NodeIdx < CleanNodes.size) {
            val NodeText = CleanNodes[NodeIdx]
            val NextText = CleanNodes.getOrNull(NodeIdx + 1)
            if (NodeText == SYMBOL &&
                NextText != null &&
                AMOUNT_TAIL_REGEX.matches(NextText)
            ) {
                ResultList.add(SYMBOL + NextText)
                NodeIdx += 2
                continue
            }
            ResultList.add(NodeText)
            NodeIdx++
        }
        return ResultList
    }
}
