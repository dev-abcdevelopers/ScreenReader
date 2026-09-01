@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.RenewalDueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalDueParserTest {

    private val CustomerViewNodes = listOf(
        "Renewals Due",
        "Policy View",
        "Customer View",
        "All",
        "Page",
        "01",
        "of",
        "01",
        "SHANKER DUTT SHARMA",
        "01",
        "Renewal Policies",
        "|",
        "View All",
        "Based on Selected Filters",
        "Renewal due in 04 days",
        "166253803",
        "|",
        "745 - LIC'S JEEVAN UMANG",
        "Auto Pay",
        "Disabled",
        "Renewal Due Date",
        "04 Sep 2026",
        "Premium Amount (excl. GST)",
        "₹",
        "2,396/Month",
        "VINOD JOSHI",
        "07",
        "Renewal Policies",
        "|",
        "View All",
        "Based on Selected Filters",
        "Renewal due in 10 days",
        "156264667",
        "|",
        "945 - LIC'S JEEVAN UMANG PLAN",
        "Auto Pay",
        "Enabled",
        "Renewal Due Date",
        "10 Sep 2026",
        "Premium Amount (excl. GST)",
        "₹",
        "2,277/Month"
    )

    private val AllRenewalsDueNodes = listOf(
        "All Renewals Due",
        "Vinod Joshi",
        "07",
        "ALL RENEWAL POLICIES",
        "All",
        "07",
        "Renewal Policies",
        "Based on Selected Filters",
        "Page",
        "01",
        "of",
        "01",
        "Renewal due in 10 days",
        "156264667",
        "|",
        "945 - LIC'S JEEVAN UMANG PLAN",
        "Auto Pay",
        "Enabled",
        "Renewal Due Date",
        "10 Sep 2026",
        "Premium Amount (excl. GST)",
        "₹",
        "2,277 / Month",
        "Send Reminder",
        "Grace Expiring in 14 days",
        "129835618",
        "|",
        "845 - LIC'S JEEVAN UMANG",
        "Auto Pay",
        "Enabled",
        "Grace Expiry Date",
        "14 Sep 2026",
        "Premium Amount (excl. GST)",
        "₹",
        "1,165 / Month",
        "Send Reminder",
        "Renewal due in 14 days"
    )

    @Test
    fun `a single customer card parses every field`() {
        val Records = RenewalDueParser.Parse(
            Nodes = CustomerViewNodes,
            HolderName = "SHANKER DUTT SHARMA"
        )
        val FirstRecord = Records.first()
        assertEquals("166253803", FirstRecord.PolicyNumber)
        assertEquals("745", FirstRecord.PlanCode)
        assertEquals("LIC'S JEEVAN UMANG", FirstRecord.PlanName)
        assertEquals("SHANKER DUTT SHARMA", FirstRecord.HolderName)
        assertEquals("Disabled", FirstRecord.AutoPay)
        assertEquals("Renewal Due Date", FirstRecord.DateLabel)
        assertEquals("04 Sep 2026", FirstRecord.DateValue)
        assertEquals("Renewal due in 04 days", FirstRecord.UrgencyText)
        assertEquals(RenewalDueKind.RENEWAL_DUE, FirstRecord.Kind)
    }

    @Test
    fun `the split rupee node is rejoined and the frequency survives`() {
        val Records = RenewalDueParser.Parse(Nodes = CustomerViewNodes, HolderName = "X Y")
        assertEquals("₹2,396/Month", Records.first().PremiumAmount)
        assertEquals("Month", Records.first().PremiumFrequency)
        assertEquals("₹2,396", FupDataParser.AmountOf(PremiumText = Records.first().PremiumAmount))
    }

    @Test
    fun `spaces around the frequency slash are tolerated`() {
        val Records = RenewalDueParser.Parse(Nodes = AllRenewalsDueNodes, HolderName = "Vinod Joshi")
        assertEquals("₹2,277 / Month", Records.first().PremiumAmount)
        assertEquals("Month", Records.first().PremiumFrequency)
    }

    @Test
    fun `a grace card keeps its own label and never reads as a due date`() {
        val Records = RenewalDueParser.Parse(Nodes = AllRenewalsDueNodes, HolderName = "Vinod Joshi")
        assertEquals(2, Records.size)
        val GraceRecord = Records[1]
        assertEquals("129835618", GraceRecord.PolicyNumber)
        assertEquals("Grace Expiry Date", GraceRecord.DateLabel)
        assertEquals("14 Sep 2026", GraceRecord.DateValue)
        assertEquals("Grace Expiring in 14 days", GraceRecord.UrgencyText)
        assertEquals(RenewalDueKind.GRACE_EXPIRY, GraceRecord.Kind)
        assertEquals("", GraceRecord.DueDateOrBlank)
    }

    @Test
    fun `a renewal card exposes its due date`() {
        val Records = RenewalDueParser.Parse(Nodes = AllRenewalsDueNodes, HolderName = "Vinod Joshi")
        assertEquals("10 Sep 2026", Records.first().DueDateOrBlank)
    }

    @Test
    fun `the urgency line belongs to the card that follows it`() {
        val Records = RenewalDueParser.Parse(Nodes = AllRenewalsDueNodes, HolderName = "Vinod Joshi")
        assertEquals("Renewal due in 10 days", Records[0].UrgencyText)
        assertEquals("Grace Expiring in 14 days", Records[1].UrgencyText)
    }

    @Test
    fun `a trailing urgency line does not invent a card`() {
        val Records = RenewalDueParser.Parse(Nodes = AllRenewalsDueNodes, HolderName = "Vinod Joshi")
        assertEquals(2, Records.size)
    }

    @Test
    fun `page numbers and counts are never mistaken for policies`() {
        val Records = RenewalDueParser.Parse(Nodes = CustomerViewNodes, HolderName = "X Y")
        assertEquals(2, Records.size)
        assertTrue(Records.all { RecordItem -> RecordItem.PolicyNumber.length >= 8 })
    }

    @Test
    fun `a combined policy line parses the same as a split one`() {
        val Combined = listOf(
            "Renewal due in 04 days",
            "166253803 | 745 - LIC'S JEEVAN UMANG",
            "Auto Pay",
            "Disabled",
            "Renewal Due Date",
            "04 Sep 2026",
            "Premium Amount (excl. GST)",
            "₹2,396/Month"
        )
        val RecordItem = RenewalDueParser.Parse(Nodes = Combined, HolderName = "A B").first()
        assertEquals("166253803", RecordItem.PolicyNumber)
        assertEquals("745", RecordItem.PlanCode)
        assertEquals("04 Sep 2026", RecordItem.DateValue)
        assertEquals("₹2,396/Month", RecordItem.PremiumAmount)
    }

    @Test
    fun `an empty screen yields nothing`() {
        assertEquals(emptyList<Any>(), RenewalDueParser.Parse(Nodes = emptyList()))
        assertEquals(
            emptyList<Any>(),
            RenewalDueParser.Parse(Nodes = listOf("Renewals Due", "No policies due as per selected filters."))
        )
    }

    @Test
    fun `customer groups are read with their counts`() {
        val Groups = RenewalDueParser.ReadCustomerGroups(Nodes = CustomerViewNodes)
        assertEquals(2, Groups.size)
        assertEquals("SHANKER DUTT SHARMA", Groups[0].HolderName)
        assertEquals(1, Groups[0].PolicyCount)
        assertTrue(Groups[0].HasViewAll)
        assertEquals("VINOD JOSHI", Groups[1].HolderName)
        assertEquals(7, Groups[1].PolicyCount)
    }

    @Test
    fun `the all renewals due header is not a customer group`() {
        val Groups = RenewalDueParser.ReadCustomerGroups(Nodes = AllRenewalsDueNodes)
        assertEquals(1, Groups.size)
        assertEquals("Vinod Joshi", Groups[0].HolderName)
        assertEquals(7, Groups[0].PolicyCount)
    }
}
