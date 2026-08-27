@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.service

class PolicyTargetScope {

    var Numbers: List<String> = emptyList()
        private set

    var NameHints: Map<String, String> = emptyMap()
        private set

    val IsActive: Boolean
        get() = Numbers.isNotEmpty()

    fun Arm(NumbersVal: List<String>, NameHintsVal: Map<String, String>) {
        Numbers = NumbersVal
            .map { NumberText -> NumberText.trim() }
            .filter { NumberText -> NumberText.isNotEmpty() }
            .distinct()
        NameHints = NameHintsVal
            .mapKeys { HintEntry -> HintEntry.key.trim() }
            .filterKeys { KeyText -> KeyText.isNotEmpty() }
    }

    fun Reset() {
        Numbers = emptyList()
        NameHints = emptyMap()
    }

    fun Allows(PolicyNumber: String): Boolean {
        if (!IsActive) return true
        return Numbers.contains(PolicyNumber)
    }

    fun Filter(PolicyNumbers: List<String>): List<String> {
        if (!IsActive) return PolicyNumbers
        return PolicyNumbers.filter { PolicyNumber -> Numbers.contains(PolicyNumber) }
    }

    fun NameHintFor(PolicyNumber: String): String {
        return NameHints[PolicyNumber].orEmpty()
    }

    fun Describe(): String {
        if (!IsActive) return "none"
        return Numbers.joinToString(separator = ",")
    }
}
