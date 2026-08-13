@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanIdentityTest {

    @Test
    fun Split_SeparatesCodeFromNameOnTheFirstHyphen() {
        val (CodeValue, NameValue) = PlanIdentity.Split("945 - LIC'S JEEVAN UMANG PLAN")

        assertEquals("945", CodeValue)
        assertEquals("LIC'S JEEVAN UMANG PLAN", NameValue)
    }

    @Test
    fun Split_KeepsHyphensThatBelongToThePlanName() {
        val (CodeValue, NameValue) = PlanIdentity.Split("821 - NEW MONEY BACK PLAN - 25 YEARS")

        assertEquals("821", CodeValue)
        assertEquals("NEW MONEY BACK PLAN - 25 YEARS", NameValue)
    }

    @Test
    fun Split_HandlesTheCodesSeenInCaptures() {
        assertEquals("934" to "LIC'S JEEVAN TARUN PLAN", PlanIdentity.Split("934 - LIC'S JEEVAN TARUN PLAN"))
        assertEquals("936" to "LIC'S NEW JEEVAN LABH PLAN", PlanIdentity.Split("936 - LIC'S NEW JEEVAN LABH PLAN"))
        assertEquals("945" to "LIC'S JEEVAN UMANG PLAN", PlanIdentity.Split("945 - LIC'S JEEVAN UMANG PLAN"))
    }

    @Test
    fun Split_ToleratesSpacingAndDashVariants() {
        assertEquals("945" to "JEEVAN UMANG", PlanIdentity.Split("945-JEEVAN UMANG"))
        assertEquals("945" to "JEEVAN UMANG", PlanIdentity.Split("  945   -   JEEVAN UMANG  "))
        assertEquals("945" to "JEEVAN UMANG", PlanIdentity.Split("945 – JEEVAN UMANG"))
    }

    @Test
    fun Split_TreatsALabelWithNoCodeAsNameOnly() {
        val (CodeValue, NameValue) = PlanIdentity.Split("LIC'S JEEVAN UMANG PLAN")

        assertEquals("", CodeValue)
        assertEquals("LIC'S JEEVAN UMANG PLAN", NameValue)
    }

    @Test
    fun Split_ReturnsBlanksForBlankInput() {
        assertEquals("" to "", PlanIdentity.Split(""))
        assertEquals("" to "", PlanIdentity.Split("   "))
    }

    @Test
    fun Split_KeepsACodeThatHasNoNameAfterIt() {
        assertEquals("945" to "", PlanIdentity.Split("945 - "))
    }

    @Test
    fun Combine_RebuildsTheLabelSoAReparseGivesTheSameSplit() {
        val OriginalLabel = "821 - NEW MONEY BACK PLAN - 25 YEARS"
        val (CodeValue, NameValue) = PlanIdentity.Split(OriginalLabel)

        val RebuiltLabel = PlanIdentity.Combine(CodeValue = CodeValue, NameValue = NameValue)

        assertEquals(OriginalLabel, RebuiltLabel)
        assertEquals(CodeValue to NameValue, PlanIdentity.Split(RebuiltLabel))
    }

    @Test
    fun Combine_OmitsTheSeparatorWhenEitherHalfIsMissing() {
        assertEquals("JEEVAN UMANG", PlanIdentity.Combine(CodeValue = "", NameValue = "JEEVAN UMANG"))
        assertEquals("945", PlanIdentity.Combine(CodeValue = "945", NameValue = ""))
        assertEquals("", PlanIdentity.Combine(CodeValue = "", NameValue = ""))
    }
}
