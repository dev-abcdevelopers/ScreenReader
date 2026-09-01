@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalDueRangeTest {

    private val TimelineSheetTexts = listOf(
        "Timeline",
        "Today",
        "Next 7 Days",
        "Next 15 Days",
        "Next 30 Days",
        "All"
    )

    @Test
    fun `forward day ranges parse to their own number`() {
        assertEquals(7, RenewalDueRange.SpanDays(TextValue = "Next 7 Days"))
        assertEquals(15, RenewalDueRange.SpanDays(TextValue = "Next 15 Days"))
        assertEquals(30, RenewalDueRange.SpanDays(TextValue = "Next 30 Days"))
        assertEquals(60, RenewalDueRange.SpanDays(TextValue = "Next 60 Days"))
        assertEquals(1, RenewalDueRange.SpanDays(TextValue = "Next 1 Day"))
    }

    @Test
    fun `today is one day and all is unbounded`() {
        assertEquals(1, RenewalDueRange.SpanDays(TextValue = "Today"))
        assertEquals(RenewalDueRange.ALL_SPAN_DAYS, RenewalDueRange.SpanDays(TextValue = "All"))
    }

    @Test
    fun `history ranges are not forward ranges`() {
        assertNull(RenewalDueRange.SpanDays(TextValue = "Last 7 Days"))
        assertNull(RenewalDueRange.SpanDays(TextValue = "Last 60 Days"))
        assertFalse(RenewalDueRange.IsRangeLabel(TextValue = "Last 30 Days"))
    }

    @Test
    fun `all never matches a longer all phrase`() {
        assertNull(RenewalDueRange.SpanDays(TextValue = "All Policies"))
        assertNull(RenewalDueRange.SpanDays(TextValue = "All Products"))
        assertNull(RenewalDueRange.SpanDays(TextValue = "ALL RENEWAL POLICIES"))
        assertNull(RenewalDueRange.SpanDays(TextValue = "All Renewals Due"))
        assertNull(RenewalDueRange.SpanDays(TextValue = "All Time"))
        assertFalse(RenewalDueRange.IsRangeLabel(TextValue = "All Policies"))
    }

    @Test
    fun `today never matches a sentence that merely mentions today`() {
        assertNull(RenewalDueRange.SpanDays(TextValue = "Renewal due today"))
        assertNull(RenewalDueRange.SpanDays(TextValue = "Today's collection"))
        assertFalse(RenewalDueRange.IsRangeLabel(TextValue = "Renewal due today"))
    }

    @Test
    fun `chip labels carry icon names and still match`() {
        assertEquals(7, RenewalDueRange.SpanDays(TextValue = "Next 7 Days Arrow-down Icon Button-1"))
        assertEquals(
            RenewalDueRange.ALL_SPAN_DAYS,
            RenewalDueRange.SpanDays(TextValue = "All Arrow-down Icon Button-1")
        )
        assertEquals(1, RenewalDueRange.SpanDays(TextValue = "Today Arrow-down Icon Button-1"))
        assertTrue(RenewalDueRange.IsRangeLabel(TextValue = "All Arrow-down Icon Button-1"))
    }

    @Test
    fun `casing and stray whitespace do not matter`() {
        assertEquals(30, RenewalDueRange.SpanDays(TextValue = "NEXT 30 DAYS"))
        assertEquals(RenewalDueRange.ALL_SPAN_DAYS, RenewalDueRange.SpanDays(TextValue = "  all  "))
        assertEquals(7, RenewalDueRange.SpanDays(TextValue = "  Next   7   Days  "))
    }

    @Test
    fun `prose that mentions a period is not a control`() {
        val ProseText = "Your renewals for the next 30 days are listed below so you can " +
                "call each customer before the premium falls due"
        assertNull(RenewalDueRange.SpanDays(TextValue = ProseText))
    }

    @Test
    fun `the timeline sheet yields exactly five options`() {
        val Spans = TimelineSheetTexts.mapNotNull { TextValue ->
            RenewalDueRange.SpanDays(TextValue = TextValue)
        }
        assertEquals(listOf(1, 7, 15, 30, RenewalDueRange.ALL_SPAN_DAYS), Spans)
    }

    @Test
    fun `an exact match is always preferred`() {
        val Available = listOf(1, 7, 15, 30, RenewalDueRange.ALL_SPAN_DAYS)
        assertEquals(15, RenewalDueRange.ChooseSpanDays(AvailableSpans = Available, TargetDays = 15))
        assertEquals(
            RenewalDueRange.ALL_SPAN_DAYS,
            RenewalDueRange.ChooseSpanDays(
                AvailableSpans = Available,
                TargetDays = RenewalDueRange.ALL_SPAN_DAYS
            )
        )
    }

    @Test
    fun `a missing option widens to the narrowest option above it`() {
        val Available = listOf(1, 7, 15, RenewalDueRange.ALL_SPAN_DAYS)
        assertEquals(
            RenewalDueRange.ALL_SPAN_DAYS,
            RenewalDueRange.ChooseSpanDays(AvailableSpans = Available, TargetDays = 30)
        )
        assertEquals(7, RenewalDueRange.ChooseSpanDays(AvailableSpans = Available, TargetDays = 5))
    }

    @Test
    fun `with nothing wide enough the widest option wins`() {
        val Available = listOf(1, 7, 15)
        assertEquals(
            15,
            RenewalDueRange.ChooseSpanDays(
                AvailableSpans = Available,
                TargetDays = RenewalDueRange.ALL_SPAN_DAYS
            )
        )
    }

    @Test
    fun `an empty sheet chooses nothing`() {
        assertNull(RenewalDueRange.ChooseSpanDays(AvailableSpans = emptyList(), TargetDays = 30))
    }

    @Test
    fun `the default is all`() {
        assertEquals(RenewalDueRange.ALL_SPAN_DAYS, RenewalDueRange.DEFAULT_SPAN_DAYS)
        assertTrue(RenewalDueRange.SUPPORTED_SPAN_DAYS.contains(RenewalDueRange.ALL_SPAN_DAYS))
    }

    @Test
    fun `labels round trip for the settings sheet`() {
        assertEquals("Today", RenewalDueRange.LabelFor(SpanDays = 1))
        assertEquals("Next 15 Days", RenewalDueRange.LabelFor(SpanDays = 15))
        assertEquals("All", RenewalDueRange.LabelFor(SpanDays = RenewalDueRange.ALL_SPAN_DAYS))
        for (SpanVal in RenewalDueRange.SUPPORTED_SPAN_DAYS) {
            assertEquals(
                SpanVal,
                RenewalDueRange.SpanDays(TextValue = RenewalDueRange.LabelFor(SpanDays = SpanVal))
            )
        }
    }
}
