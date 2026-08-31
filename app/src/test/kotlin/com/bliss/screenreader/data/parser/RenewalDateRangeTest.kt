@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalDateRangeTest {

    private data class Option(val TextValue: String, val CentreY: Int, val SpanDays: Int)

    private val SheetTexts = listOf(
        "Timeline" to 929,
        "Last 7 Days" to 997,
        "Last 15 Days" to 1073,
        "Last 30 Days" to 1150,
        "Last 60 Days" to 1226,
        "Apply" to 1300
    )

    private fun PickWidest(
        EntryList: List<Pair<String, Int>>,
        ChipCentreY: Int?
    ): Option? {
        val RangeOptions = EntryList.mapNotNull { EntryRef ->
            val SpanDays = RenewalDateRange.SpanDays(TextValue = EntryRef.first)
            when {
                SpanDays == null -> null
                ChipCentreY != null && EntryRef.second <= ChipCentreY -> null
                else -> Option(EntryRef.first, EntryRef.second, SpanDays)
            }
        }
        return RangeOptions.maxWithOrNull(
            compareBy({ OptionRef -> OptionRef.SpanDays }, { OptionRef -> OptionRef.CentreY })
        )
    }

    @Test
    fun `day ranges parse to their own number`() {
        assertEquals(7, RenewalDateRange.SpanDays(TextValue = "Last 7 Days"))
        assertEquals(15, RenewalDateRange.SpanDays(TextValue = "Last 15 Days"))
        assertEquals(30, RenewalDateRange.SpanDays(TextValue = "Last 30 Days"))
        assertEquals(60, RenewalDateRange.SpanDays(TextValue = "Last 60 Days"))
        assertEquals(90, RenewalDateRange.SpanDays(TextValue = "Last 90 Days"))
        assertEquals(1, RenewalDateRange.SpanDays(TextValue = "Last 1 Day"))
    }

    @Test
    fun `weeks months and years convert to days`() {
        assertEquals(14, RenewalDateRange.SpanDays(TextValue = "Last 2 Weeks"))
        assertEquals(180, RenewalDateRange.SpanDays(TextValue = "Last 6 Months"))
        assertEquals(365, RenewalDateRange.SpanDays(TextValue = "Last 1 Year"))
    }

    @Test
    fun `casing and stray whitespace do not matter`() {
        assertEquals(60, RenewalDateRange.SpanDays(TextValue = "LAST 60 DAYS"))
        assertEquals(60, RenewalDateRange.SpanDays(TextValue = "  Last   60   Days  "))
        assertEquals(60, RenewalDateRange.SpanDays(TextValue = "last 60 days"))
    }

    @Test
    fun `named ranges are ordered sensibly`() {
        assertEquals(1, RenewalDateRange.SpanDays(TextValue = "Today"))
        assertEquals(30, RenewalDateRange.SpanDays(TextValue = "This Month"))
        assertEquals(365, RenewalDateRange.SpanDays(TextValue = "This Year"))
        assertEquals(Int.MAX_VALUE, RenewalDateRange.SpanDays(TextValue = "All Time"))
    }

    @Test
    fun `a silly number cannot overflow into a negative span`() {
        val SpanVal = RenewalDateRange.SpanDays(TextValue = "Last 999999999 Years")
        assertTrue(SpanVal != null && SpanVal > 0)
    }

    @Test
    fun `sheet furniture is not a date range`() {
        assertNull(RenewalDateRange.SpanDays(TextValue = "Timeline"))
        assertNull(RenewalDateRange.SpanDays(TextValue = "Apply"))
        assertNull(RenewalDateRange.SpanDays(TextValue = "Submit"))
        assertNull(RenewalDateRange.SpanDays(TextValue = "00 Policies"))
        assertNull(RenewalDateRange.SpanDays(TextValue = "Based on Selected Filters"))
        assertNull(RenewalDateRange.SpanDays(TextValue = "No recent policy renewals."))
        assertNull(RenewalDateRange.SpanDays(TextValue = ""))
    }

    @Test
    fun `custom needs a date picker so it has no span`() {
        assertTrue(RenewalDateRange.IsRangeLabel(TextValue = "Custom"))
        assertNull(RenewalDateRange.SpanDays(TextValue = "Custom"))
    }

    @Test
    fun `the chip matcher accepts ranges and rejects everything else`() {
        assertTrue(RenewalDateRange.IsRangeLabel(TextValue = "Last 7 Days"))
        assertTrue(RenewalDateRange.IsRangeLabel(TextValue = "All Time"))
        assertFalse(RenewalDateRange.IsRangeLabel(TextValue = "Renewal History"))
        assertFalse(RenewalDateRange.IsRangeLabel(TextValue = "Last few days"))
    }

    @Test
    fun `the real timeline sheet resolves to Last 60 Days`() {
        assertEquals("Last 60 Days", PickWidest(EntryList = SheetTexts, ChipCentreY = 178)?.TextValue)
    }

    @Test
    fun `the bottom-most rule would have tapped Apply instead`() {
        assertEquals("Apply", SheetTexts.maxByOrNull { EntryRef -> EntryRef.second }?.first)
    }

    @Test
    fun `the chip is never mistaken for an option`() {
        val WithChip = listOf("Last 60 Days" to 178) + SheetTexts
        val ChosenOption = PickWidest(EntryList = WithChip, ChipCentreY = 178)
        assertEquals("Last 60 Days", ChosenOption?.TextValue)
        assertEquals(1226, ChosenOption?.CentreY)
    }

    @Test
    fun `the chip carries icon and button names alongside the range`() {
        assertEquals(
            7,
            RenewalDateRange.SpanDays(TextValue = "Last 7 Days Arrow-down Icon Button-1")
        )
        assertTrue(
            RenewalDateRange.IsRangeLabel(TextValue = "Last 7 Days Arrow-down Icon Button-1")
        )
        assertEquals(
            "Last 7 Days",
            RenewalDateRange.FindRange(TextValue = "Last 7 Days Arrow-down Icon Button-1")
        )
    }

    @Test
    fun `other decorated chip shapes still resolve`() {
        assertEquals(60, RenewalDateRange.SpanDays(TextValue = "Filter: Last 60 Days"))
        assertEquals(30, RenewalDateRange.SpanDays(TextValue = "Last 30 Days Down arrow"))
        assertEquals(
            Int.MAX_VALUE,
            RenewalDateRange.SpanDays(TextValue = "All Time Arrow-down Icon Button-1")
        )
    }

    @Test
    fun `the real Renewal History screen finds its chip`() {
        val ScreenTexts = listOf(
            "back",
            "Renewal History",
            "Last 7 Days Arrow-down Icon Button-1",
            "00",
            "Policies",
            "Based on Selected Filters",
            "No data found 3D Icon",
            "No recent policy renewals."
        )
        val Matched = ScreenTexts.filter { TextRef ->
            RenewalDateRange.IsRangeLabel(TextValue = TextRef)
        }
        assertEquals(listOf("Last 7 Days Arrow-down Icon Button-1"), Matched)
    }

    @Test
    fun `a long sentence mentioning a range is not treated as a chip`() {
        assertNull(
            RenewalDateRange.SpanDays(
                TextValue = "Great job on maintaining a low lapsation ratio over the Last 30 Days"
            )
        )
    }

    @Test
    fun `a wider range wins even when it is not last in the list`() {
        val Reordered = listOf(
            "Last 60 Days" to 997,
            "Last 7 Days" to 1073,
            "Last 30 Days" to 1150
        )
        assertEquals("Last 60 Days", PickWidest(EntryList = Reordered, ChipCentreY = 178)?.TextValue)
    }

    @Test
    fun ChooseSpanDays_TakesTheExactRangeTheUserPicked() {
        val Available = listOf(7, 15, 30, 60)
        assertEquals(7, RenewalDateRange.ChooseSpanDays(Available, 7))
        assertEquals(15, RenewalDateRange.ChooseSpanDays(Available, 15))
        assertEquals(30, RenewalDateRange.ChooseSpanDays(Available, 30))
        assertEquals(60, RenewalDateRange.ChooseSpanDays(Available, 60))
    }

    @Test
    fun ChooseSpanDays_FallsToTheWidestRangeBelowTheTarget() {
        assertEquals(15, RenewalDateRange.ChooseSpanDays(listOf(7, 15, 60), 30))
        assertEquals(7, RenewalDateRange.ChooseSpanDays(listOf(7, 90), 15))
    }

    @Test
    fun ChooseSpanDays_TakesTheNarrowestWhenEveryRangeIsWiderThanTheTarget() {
        assertEquals(30, RenewalDateRange.ChooseSpanDays(listOf(30, 60, 90), 7))
    }

    @Test
    fun ChooseSpanDays_HasNothingToSayAboutAnEmptySheet() {
        assertNull(RenewalDateRange.ChooseSpanDays(emptyList(), 60))
    }

    @Test
    fun ChooseSpanDays_ReadsTheRealTimelineSheet() {
        val SheetLabels = listOf(
            "Timeline",
            "Last 7 Days",
            "Last 15 Days",
            "Last 30 Days",
            "Last 60 Days",
            "Custom"
        )
        val Spans = SheetLabels.mapNotNull { LabelText ->
            RenewalDateRange.SpanDays(TextValue = LabelText)
        }

        assertEquals(listOf(7, 15, 30, 60), Spans)
        assertEquals(15, RenewalDateRange.ChooseSpanDays(Spans, 15))
        assertEquals(60, RenewalDateRange.ChooseSpanDays(Spans, RenewalDateRange.DEFAULT_SPAN_DAYS))
    }
}
