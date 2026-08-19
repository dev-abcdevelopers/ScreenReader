@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalDueProjectionTest {

    @Test
    fun MonthsForFrequency_MapsTheFrequenciesTheAppPrints() {
        assertEquals(1, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Month"))
        assertEquals(1, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Monthly"))
        assertEquals(1, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Mly"))
        assertEquals(3, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Quarter"))
        assertEquals(3, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Quarterly"))
        assertEquals(6, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Half Year"))
        assertEquals(6, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Half-Yearly"))
        assertEquals(12, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Year"))
        assertEquals(12, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Yearly"))
    }

    @Test
    fun MonthsForFrequency_RefusesFrequenciesThatCannotAdvanceADueDate() {
        assertEquals(0, RenewalDueProjection.MonthsForFrequency(FrequencyText = ""))
        assertEquals(0, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Single"))
        assertEquals(0, RenewalDueProjection.MonthsForFrequency(FrequencyText = "Nach"))
    }

    @Test
    fun NextDueDate_AdvancesByTheFrequency() {
        assertEquals(
            "18 Jul 2026",
            RenewalDueProjection.NextDueDate(PaidForDate = "18 Jun 2026", FrequencyText = "Month")
        )
        assertEquals(
            "28 Oct 2026",
            RenewalDueProjection.NextDueDate(PaidForDate = "28 Jul 2026", FrequencyText = "Quarter")
        )
        assertEquals(
            "25 Jan 2027",
            RenewalDueProjection.NextDueDate(
                PaidForDate = "25 Jul 2026",
                FrequencyText = "Half Year"
            )
        )
        assertEquals(
            "15 Aug 2027",
            RenewalDueProjection.NextDueDate(PaidForDate = "15 Aug 2026", FrequencyText = "Yearly")
        )
    }

    @Test
    fun NextDueDate_ClampsAMonthEndRatherThanOverflowing() {
        assertEquals(
            "28 Feb 2027",
            RenewalDueProjection.NextDueDate(PaidForDate = "31 Jan 2027", FrequencyText = "Month")
        )
        assertEquals(
            "29 Feb 2028",
            RenewalDueProjection.NextDueDate(PaidForDate = "31 Jan 2028", FrequencyText = "Month")
        )
    }

    @Test
    fun NextDueDate_ReturnsNothingWhenTheFrequencyOrDateIsUnusable() {
        assertEquals(
            "",
            RenewalDueProjection.NextDueDate(PaidForDate = "18 Jun 2026", FrequencyText = "")
        )
        assertEquals(
            "",
            RenewalDueProjection.NextDueDate(PaidForDate = "", FrequencyText = "Month")
        )
        assertEquals(
            "",
            RenewalDueProjection.NextDueDate(PaidForDate = "Cash", FrequencyText = "Month")
        )
    }

    @Test
    fun LatestByPolicy_KeepsTheFurthestPaidForDate() {
        val RenewalList = listOf(
            FupPolicy(PolicyNumber = "125225185", DueDate = "18 Apr 2026", PremiumFrequency = "Month"),
            FupPolicy(PolicyNumber = "125225185", DueDate = "18 Jun 2026", PremiumFrequency = "Month"),
            FupPolicy(PolicyNumber = "125225185", DueDate = "18 May 2026", PremiumFrequency = "Month")
        )

        val LatestMap = RenewalDueProjection.LatestByPolicy(Renewals = RenewalList)

        assertEquals("18 Jun 2026", LatestMap.getValue("125225185").DueDate)
    }

    @Test
    fun Apply_MovesTheDueDateForwardAndRecordsTheChange() {
        val PolicyList = listOf(
            CustomerPolicy(PolicyNumber = "125225185", RenewalDueDate = "18 Jun 2026")
        )
        val RenewalList = listOf(
            FupPolicy(
                PolicyNumber = "125225185",
                DueDate = "18 Jun 2026",
                PremiumAmount = "₹2,231/Month",
                PremiumFrequency = "Month"
            )
        )

        val OutcomeObj = RenewalDueProjection.Apply(
            Policies = PolicyList,
            Renewals = RenewalList
        )

        assertEquals("18 Jul 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.UpdatedCount)
        assertEquals(1, OutcomeObj.Changes.size)
        assertEquals("18 Jun 2026", OutcomeObj.Changes.first().OldValue)
        assertEquals("18 Jul 2026", OutcomeObj.Changes.first().NewValue)
    }

    @Test
    fun Apply_NeverMovesADueDateBackwards() {
        val PolicyList = listOf(
            CustomerPolicy(PolicyNumber = "156257874", RenewalDueDate = "20 Aug 2026")
        )
        val RenewalList = listOf(
            FupPolicy(
                PolicyNumber = "156257874",
                DueDate = "20 Feb 2026",
                PremiumFrequency = "Month"
            )
        )

        val OutcomeObj = RenewalDueProjection.Apply(
            Policies = PolicyList,
            Renewals = RenewalList
        )

        assertEquals("20 Aug 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.UnchangedCount)
        assertEquals(0, OutcomeObj.UpdatedCount)
        assertTrue(OutcomeObj.Changes.isEmpty())
    }

    @Test
    fun Apply_FillsAPolicyThatHasNoDueDateAtAll() {
        val PolicyList = listOf(CustomerPolicy(PolicyNumber = "146341526"))
        val RenewalList = listOf(
            FupPolicy(
                PolicyNumber = "146341526",
                DueDate = "25 Jul 2026",
                PremiumFrequency = "Half Year"
            )
        )

        val OutcomeObj = RenewalDueProjection.Apply(
            Policies = PolicyList,
            Renewals = RenewalList
        )

        assertEquals("25 Jan 2027", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.UpdatedCount)
    }

    @Test
    fun Apply_FallsBackToThePolicysOwnFrequencyWhenTheRenewalHasNone() {
        val PolicyList = listOf(
            CustomerPolicy(PolicyNumber = "129837565", PremiumFrequency = "Quarter")
        )
        val RenewalList = listOf(
            FupPolicy(PolicyNumber = "129837565", DueDate = "28 Jul 2026")
        )

        val OutcomeObj = RenewalDueProjection.Apply(
            Policies = PolicyList,
            Renewals = RenewalList
        )

        assertEquals("28 Oct 2026", OutcomeObj.Policies.first().RenewalDueDate)
    }

    @Test
    fun Apply_SkipsAMatchWithNoUsableFrequencyAndLeavesOthersAlone() {
        val PolicyList = listOf(
            CustomerPolicy(PolicyNumber = "111111111", RenewalDueDate = "10 Sep 2026"),
            CustomerPolicy(PolicyNumber = "222222222", RenewalDueDate = "10 Sep 2026")
        )
        val RenewalList = listOf(
            FupPolicy(PolicyNumber = "111111111", DueDate = "10 Sep 2026")
        )

        val OutcomeObj = RenewalDueProjection.Apply(
            Policies = PolicyList,
            Renewals = RenewalList
        )

        assertEquals(1, OutcomeObj.MatchedCount)
        assertEquals(1, OutcomeObj.SkippedCount)
        assertEquals(0, OutcomeObj.UpdatedCount)
        assertEquals("10 Sep 2026", OutcomeObj.Policies[1].RenewalDueDate)
    }
}
