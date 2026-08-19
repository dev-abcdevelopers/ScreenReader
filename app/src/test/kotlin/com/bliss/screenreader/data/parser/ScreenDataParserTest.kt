@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CaptureMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDataParserTest {

    @Test
    fun ParsePolicyDashboard_ExtractsMultipleCardsAndRepeatedLabels() {
        val NodeList = listOf(
            "Policy Dashboard",
            "87", "Policy(ies)", "Page", "01", "of 09",
            "Lapsed",
            "KYC not updated",
            "NEFT not updated",
            "146345511 | 945 - LIC'S JEEVAN UMANG PLAN",
            "Pushpender Khulbe",
            "Auto Pay", "Disabled",
            "Revival without DGH expiry date", "25 Sep 2026",
            "Premium Amount (excl. GST)", "₹5,641/Month",
            "Send Reminder",
            "Lapsed",
            "KYC not updated",
            "NEFT not updated",
            "146345561 | 945 - LIC'S JEEVAN UMANG PLAN",
            "Bineeta Bhatt",
            "Auto Pay", "Disabled",
            "Revival without DGH expiry date", "27 Sep 2026",
            "Premium Amount (excl. GST)", "₹2,732/Month",
            "Send Reminder"
        )

        val ParsedList = ScreenDataParser.ParsePolicyDashboard(Nodes = NodeList)

        assertEquals(2, ParsedList.size)
        assertEquals("146345511", ParsedList[0].PolicyNumber)
        assertEquals("Pushpender Khulbe", ParsedList[0].HolderName)
        assertEquals("945", ParsedList[0].PlanCode)
        // The code is stripped from the name so the two are never re-parsed.
        assertEquals("LIC'S JEEVAN UMANG PLAN", ParsedList[0].PlanName)
        assertEquals("₹5,641", ParsedList[0].PremiumAmount)
        assertEquals("Month", ParsedList[0].PremiumFrequency)
        assertEquals("", ParsedList[0].RenewalDueDate)
        assertEquals("Revival without DGH expiry date", ParsedList[0].RenewalDateLabel)
        assertEquals("25 Sep 2026", ParsedList[0].RenewalDateValue)
        assertEquals("Not Updated", ParsedList[0].KycStatus)
        assertEquals("Not Updated", ParsedList[0].NeftStatus)
        assertEquals("146345561", ParsedList[1].PolicyNumber)
        assertEquals("Bineeta Bhatt", ParsedList[1].HolderName)
    }

    @Test
    fun PreviewPolicy_ReturnsOneReviewRecordPerPolicyNumber() {
        val NodeList = listOf(
            "Lapsed",
            "146345511 | 945 - LIC'S JEEVAN UMANG PLAN",
            "Pushpender Khulbe",
            "Auto Pay", "Disabled",
            "Premium Amount (excl. GST)", "₹5,641/Month",
            "Send Reminder",
            "Inforce",
            "146345561 | 936 - LIC'S NEW JEEVAN LABH PLAN",
            "Bineeta Bhatt",
            "Auto Pay", "Enabled",
            "Premium Amount (excl. GST)", "₹2,732/Month",
            "Send Reminder"
        )

        val ReviewList = CaptureParsers.Preview(ModeVal = CaptureMode.POLICY, Nodes = NodeList)

        assertEquals(2, ReviewList.size)
        assertTrue(ReviewList.any { RecordItem -> RecordItem.PolicyNumber == "146345511" })
        assertTrue(ReviewList.any { RecordItem -> RecordItem.PolicyNumber == "146345561" })
    }

    @Test
    fun PreviewPolicy_KeepsDetailedPolicyParsingSeparateFromDashboardCards() {
        val NodeList = listOf(
            "Detailed Policy View",
            "146345511",
            "Pushpender Khulbe",
            "Policy Details",
            "Sum Assured", "₹5,00,000",
            "Term/PPT", "20/15"
        )

        val ReviewList = CaptureParsers.Preview(ModeVal = CaptureMode.POLICY, Nodes = NodeList)

        assertEquals(1, ReviewList.size)
        assertEquals("146345511", ReviewList.first().PolicyNumber)
        assertTrue(ReviewList.first().SecondaryLine.contains("SA ₹5,00,000"))
    }

    @Test
    fun ParseDetailedPolicyRecord_AssignsExpandedFieldsToTheCorrectPolicy() {
        val NodeList = listOf(
            "Detailed Policy View",
            "146345511",
            "Pushpender Khulbe",
            "945 - LIC'S JEEVAN UMANG PLAN",
            "Lapsed",
            "KYC not updated",
            "NEFT not updated",
            "Premium Amount (excl. GST)", "₹5,641/Month",
            "Sum Assured", "₹12,50,000",
            "Policy Details",
            "Term/ PPT", "69/20",
            "Auto Pay", "Disabled",
            "Commissions",
            "Date of Premium Payment", "18 Mar 2026",
            "Date Of Commission Payment", "-",
            "Commission Type", "Renewal",
            "Bonus Commission", "-",
            "₹282", "COMMISSION PAID"
        )

        val ParsedPolicy = ScreenDataParser.ParseDetailedPolicyRecord(Nodes = NodeList)

        requireNotNull(ParsedPolicy)
        assertEquals("146345511", ParsedPolicy.PolicyNumber)
        assertEquals("Pushpender Khulbe", ParsedPolicy.HolderName)
        assertEquals("945", ParsedPolicy.PlanCode)
        assertEquals("₹5,641", ParsedPolicy.PremiumAmount)
        assertEquals("Month", ParsedPolicy.PremiumFrequency)
        assertEquals("₹12,50,000", ParsedPolicy.SumAssured)
        assertEquals("69/20", ParsedPolicy.TermPPT)
        assertEquals("18 Mar 2026", ParsedPolicy.CommissionDateOfPremiumPayment)
        assertEquals("Renewal", ParsedPolicy.CommissionType)
        assertEquals("₹282", ParsedPolicy.CommissionPaidAmount)
    }

    @Test
    fun ParsePolicyDashboard_NeverTakesAnIconDescriptionAsTheHolderName() {
        val NodeList = listOf(
            "Policy Dashboard",
            "40", "Policy(ies)", "Page", "01", "of 04",
            "Inforce",
            "166251050 | 932 - LIC'S NEW CHILDREN'S MONEY BACK PLAN",
            "card right arrow icon",
            "Auto Pay", "Disabled",
            "Premium Amount (excl. GST)", "12,142/Month",
            "Send Reminder"
        )

        val PolicyList = ScreenDataParser.ParsePolicyDashboard(Nodes = NodeList)

        assertEquals(1, PolicyList.size)
        assertEquals("166251050", PolicyList.first().PolicyNumber)
        assertEquals("", PolicyList.first().HolderName)
    }

    @Test
    fun ParsePolicyDashboard_StillTakesARealHolderNameAfterTheNumber() {
        val NodeList = listOf(
            "Policy Dashboard",
            "40", "Policy(ies)", "Page", "01", "of 04",
            "Inforce",
            "166251050 | 932 - LIC'S NEW CHILDREN'S MONEY BACK PLAN",
            "Rahul Pal",
            "card right arrow icon",
            "Auto Pay", "Disabled",
            "Send Reminder"
        )

        val PolicyList = ScreenDataParser.ParsePolicyDashboard(Nodes = NodeList)

        assertEquals("Rahul Pal", PolicyList.first().HolderName)
    }
}
