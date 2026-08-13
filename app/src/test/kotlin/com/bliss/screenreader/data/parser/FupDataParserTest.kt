@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.FupPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FupDataParserTest {

    /** Row-major traversal: each label is immediately followed by its value. */
    private val RowMajorCard = listOf(
        "156264678 | 934 - LIC'S JEEVAN TARUN PLAN",
        "Somesh Khulbe",
        "Premium Amount (excl. GST)", "₹1,221/Month",
        "Due Date", "28 Jul 2026",
        "Payment Date", "08 Aug 2026",
        "Mode of Payment", "Cash",
        "Status at Time of Payment", "Paid in Grace Period",
        "Call Customer"
    )

    /** Column-major traversal: the labels of a row arrive before its values. */
    private val ColumnMajorCard = listOf(
        "128636412 | 821 - NEW MONEY BACK PLAN - 25 YEARS",
        "Lata",
        "Premium Amount (excl. GST)", "Due Date",
        "₹999/Month", "28 Aug 2026",
        "Payment Date", "Mode of Payment",
        "08 Aug 2026", "Cash",
        "Status at Time of Payment",
        "Paid on Time",
        "Call Customer"
    )

    @Test
    fun ParseRenewalHistory_ReadsEveryFieldFromARowMajorCard() {
        val ParsedList = FupDataParser.ParseRenewalHistory(Nodes = RowMajorCard)

        assertEquals(1, ParsedList.size)
        val RecordItem = ParsedList.first()
        assertEquals("156264678", RecordItem.PolicyNumber)
        assertEquals("934", RecordItem.PlanCode)
        assertEquals("LIC'S JEEVAN TARUN PLAN", RecordItem.PlanName)
        assertEquals("Somesh Khulbe", RecordItem.HolderName)
        assertEquals("₹1,221/Month", RecordItem.PremiumAmount)
        assertEquals("28 Jul 2026", RecordItem.DueDate)
        assertEquals("08 Aug 2026", RecordItem.PaymentDate)
        assertEquals("Cash", RecordItem.ModeOfPayment)
        assertEquals("Paid in Grace Period", RecordItem.Status)
    }

    @Test
    fun ParseRenewalHistory_FallsBackToValueShapesWhenLabelsAreGrouped() {
        val ParsedList = FupDataParser.ParseRenewalHistory(Nodes = ColumnMajorCard)

        assertEquals(1, ParsedList.size)
        val RecordItem = ParsedList.first()
        assertEquals("128636412", RecordItem.PolicyNumber)
        // Only the first hyphen separates code from name, so the trailing
        // "- 25 YEARS" stays part of the plan name.
        assertEquals("821", RecordItem.PlanCode)
        assertEquals("NEW MONEY BACK PLAN - 25 YEARS", RecordItem.PlanName)
        assertEquals("Lata", RecordItem.HolderName)
        assertEquals("₹999/Month", RecordItem.PremiumAmount)
        assertEquals("28 Aug 2026", RecordItem.DueDate)
        assertEquals("08 Aug 2026", RecordItem.PaymentDate)
        assertEquals("Cash", RecordItem.ModeOfPayment)
        assertEquals("Paid on Time", RecordItem.Status)
    }

    @Test
    fun ParseRenewalHistory_SplitsConsecutiveCardsAndKeepsRepeatedValues() {
        val ParsedList = FupDataParser.ParseRenewalHistory(
            Nodes = RowMajorCard + ColumnMajorCard
        )

        assertEquals(2, ParsedList.size)
        assertEquals("156264678", ParsedList[0].PolicyNumber)
        assertEquals("128636412", ParsedList[1].PolicyNumber)
        // "Cash" appears on both cards and must not be consumed by the first.
        assertEquals("Cash", ParsedList[0].ModeOfPayment)
        assertEquals("Cash", ParsedList[1].ModeOfPayment)
    }

    @Test
    fun ParseRenewalHistory_IgnoresPageChromeAroundTheCards() {
        val NodeList = listOf(
            "Renewal History",
            "Last 7 Days",
            "05", "Policies", "Based on Selected Filters",
            "Page", "01", "of 01"
        ) + RowMajorCard

        val ParsedList = FupDataParser.ParseRenewalHistory(Nodes = NodeList)

        assertEquals(1, ParsedList.size)
        assertEquals("Somesh Khulbe", ParsedList.first().HolderName)
        assertEquals("Paid in Grace Period", ParsedList.first().Status)
    }

    @Test
    fun ParseRenewalHistory_HandlesHalfYearlyPremiumAndOthersPaymentMode() {
        val NodeList = listOf(
            "146341526 | 945 - LIC'S JEEVAN UMANG PLAN",
            "Poonam P",
            "Premium Amount (excl. GST)", "₹5,535/Half Year",
            "Due Date", "25 Jul 2026",
            "Payment Date", "07 Aug 2026",
            "Mode of Payment", "Others",
            "Status at Time of Payment", "Paid in Grace Period",
            "Call Customer"
        )

        val RecordItem = FupDataParser.ParseRenewalHistory(Nodes = NodeList).first()

        assertEquals("Poonam P", RecordItem.HolderName)
        assertEquals("₹5,535/Half Year", RecordItem.PremiumAmount)
        assertEquals("Others", RecordItem.ModeOfPayment)
    }

    @Test
    fun MergeRenewalRecord_KeepsFieldsFromThePartialFirstRead() {
        val PartialRecord = FupPolicy(
            PolicyNumber = "156264678",
            PlanName = "934 - LIC'S JEEVAN TARUN PLAN",
            HolderName = "Somesh Khulbe",
            PremiumAmount = "₹1,221/Month"
        )
        val LaterRecord = FupPolicy(
            PolicyNumber = "156264678",
            PaymentDate = "08 Aug 2026",
            ModeOfPayment = "Cash",
            Status = "Paid in Grace Period"
        )

        val MergedRecord = FupDataParser.MergeRenewalRecord(
            ExistingRecord = PartialRecord,
            IncomingRecord = LaterRecord
        )

        assertEquals("Somesh Khulbe", MergedRecord.HolderName)
        assertEquals("₹1,221/Month", MergedRecord.PremiumAmount)
        assertEquals("08 Aug 2026", MergedRecord.PaymentDate)
        assertEquals("Cash", MergedRecord.ModeOfPayment)
    }

    @Test
    fun PreviewFup_SummarisesPaymentDetailsWithoutWarnings() {
        val ReviewList = CaptureParsers.Preview(
            ModeVal = CaptureMode.FUP,
            Nodes = RowMajorCard
        )

        assertEquals(1, ReviewList.size)
        val ReviewItem = ReviewList.first()
        assertEquals("156264678", ReviewItem.PolicyNumber)
        assertEquals("Somesh Khulbe", ReviewItem.PrimaryLine)
        assertTrue(ReviewItem.SecondaryLine.contains("Paid 08 Aug 2026"))
        assertTrue(ReviewItem.SecondaryLine.contains("Cash"))
        assertEquals("", ReviewItem.Warning)
    }
}
