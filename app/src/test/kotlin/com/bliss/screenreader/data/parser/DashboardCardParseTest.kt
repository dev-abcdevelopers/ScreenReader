@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCardParseTest {

    private val DashboardNodes = listOf(
        "156260854", "|", "945 - LIC'S JEEVAN UMANG PLAN", "Narender Rathor",
        "Auto Pay", "Enabled", "Renewal Due Date", "11 Aug 2026",
        "Premium Amount (excl. GST)", "₹", "5,635/Month",
        "card right arrow icon", "Send Reminder card icon",
        "KYC not updated", "PAN not updated",
        "166248113", "|", "871 - LIC'S JEEVAN UTSAV",
        "Aarvi Khulbe(A),Pushpender Khulbe(P)",
        "Auto Pay", "Disabled", "Renewal Due Date", "01 Feb 2026",
        "Premium Amount (excl. GST)", "₹", "5,179/Month",
        "card right arrow icon", "Send Reminder card icon",
        "First Year Renewal", "Contact Details Absent", "KYC not updated", "+3",
        "125226685", "|", "873 - LIC'S INDEX PLUS",
        "Auto Pay", "Enabled", "Renewal Due Date", "28 Aug 2026",
        "Premium Amount (excl. GST)", "₹", "5,000/Month",
        "card right arrow icon", "Send Reminder card icon",
        "KYC not updated", "PAN not updated", "+1",
        "166250947", "|", "917 - LIC'S SINGLE PREMIUM ENDOWMENT PLAN",
        "Hardik Verma(A),Sandeep (P)",
        "Auto Pay", "Disabled", "Renewal Due Date", "22 May 2024",
        "Premium Amount (excl. GST)", "₹", "50,467/Single Premium",
        "card right arrow icon", "Send Reminder card icon", "KYC not updated",
        "156264678", "|", "934 - LIC'S JEEVAN TARUN PLAN",
        "Somesh Khulbe(A) Jagdish Chandra Khulbe(P)",
        "Auto Pay", "Disabled", "Renewal Due Date", "28 Aug 2026",
        "Premium Amount (excl. GST)", "₹", "1,221/Month",
        "card right arrow icon", "Send Reminder card icon",
        "Renewal due in 10 days", "KYC not updated", "NEFT not updated", "+1",
        "128636411", "|", "845 - LIC'S JEEVAN UMANG",
        "Ashvi (a) Deepak kumar (p)",
        "Auto Pay", "Enabled", "Renewal Due Date", "28 Aug 2026",
        "Premium Amount (excl. GST)", "₹", "1,165/Month",
        "card right arrow icon", "Send Reminder card icon",
        "Renewal due in 10 days", "KYC not updated",
        "166251050", "|", "932 - LIC'S NEW CHILDREN'S MONEY BACK PLAN",
        "Ojaswi Jaiswal(La) Anand Jaiswal(P)",
        "Auto Pay", "Disabled", "Renewal Due Date", "27 Nov 2026",
        "Premium Amount (excl. GST)", "₹", "12,142/Half Year",
        "card right arrow icon", "Send Reminder card icon", "KYC not updated",
        "129831341", "|", "914 - LIC'S NEW JEEVAN LABH",
        "Auto Pay", "Disabled", "Grace Expiry Date", "31 Aug 2026",
        "Premium Amount (excl. GST)", "₹", "3,000/Month",
        "card right arrow icon", "Send Reminder card icon", "KYC not updated",
        "125228011", "|", "745 - LIC'S JEEVAN UMANG", "Kajal",
        "Auto Pay", "Enabled", "Renewal Due Date", "28 Aug 2026",
        "Premium Amount (excl. GST)", "₹", "2,109/Month",
        "card right arrow icon", "Send Reminder card icon"
    )

    private fun PolicyByNumber(NumberText: String) =
        ScreenDataParser.ParsePolicyDashboard(Nodes = DashboardNodes)
            .first { PolicyItem -> PolicyItem.PolicyNumber == NumberText }

    @Test
    fun `a plain holder name is read as written`() {
        assertEquals("Narender Rathor", PolicyByNumber("156260854").HolderName)
    }

    @Test
    fun `a role marked pair is stored exactly as the card writes it`() {
        assertEquals(
            "Aarvi Khulbe(A),Pushpender Khulbe(P)",
            PolicyByNumber("166248113").HolderName
        )
        assertEquals("Hardik Verma(A),Sandeep (P)", PolicyByNumber("166250947").HolderName)
    }

    @Test
    fun `a card with no name never borrows a badge from its neighbour`() {
        val HolderName = PolicyByNumber("125226685").HolderName
        assertTrue(
            "expected an empty holder, got '$HolderName'",
            HolderName.isEmpty() || !HolderName.contains("Contact Details", ignoreCase = true)
        )
    }

    @Test
    fun `a bare rupee sign is never the amount`() {
        val PolicyItem = PolicyByNumber("166250947")
        assertEquals("50,467", PolicyItem.PremiumAmount)
        assertEquals("Single Premium", PolicyItem.PremiumFrequency)
    }

    @Test
    fun `monthly amounts keep working`() {
        assertEquals("5,635", PolicyByNumber("156260854").PremiumAmount)
        assertEquals("Month", PolicyByNumber("156260854").PremiumFrequency)
        assertEquals("5,179", PolicyByNumber("166248113").PremiumAmount)
        assertEquals("5,000", PolicyByNumber("125226685").PremiumAmount)
    }

    @Test
    fun `role suffixes are stripped only when they are roles`() {
        assertEquals("Aarvi Khulbe", ScreenDataParser.NormaliseHolderName("Aarvi Khulbe(A)"))
        assertEquals("Sandeep", ScreenDataParser.NormaliseHolderName("Sandeep (P)"))
        assertEquals("Narender Rathor", ScreenDataParser.NormaliseHolderName("Narender Rathor"))
    }

    @Test
    fun `a space separated pair is kept whole and still recognised`() {
        assertEquals(
            "Somesh Khulbe(A) Jagdish Chandra Khulbe(P)",
            PolicyByNumber("156264678").HolderName
        )
        assertEquals(
            "Somesh Khulbe",
            ScreenDataParser.NormaliseHolderName("Somesh Khulbe(A) Jagdish Chandra Khulbe(P)")
        )
    }

    @Test
    fun `a single word life assured survives the name shape check`() {
        assertEquals("Ashvi (a) Deepak kumar (p)", PolicyByNumber("128636411").HolderName)
    }

    @Test
    fun `the life assured wins over a leading proposer`() {
        assertEquals(
            "Ojaswi Jaiswal",
            ScreenDataParser.NormaliseHolderName("Ojaswi Jaiswal(la) Anand jaiswal(p)")
        )
        assertEquals(
            "Vinod Joshi",
            ScreenDataParser.NormaliseHolderName("Avika Joshi(p),Vinod Joshi(a)")
        )
    }

    @Test
    fun `a one word holder is read as a name`() {
        assertEquals("Kajal", PolicyByNumber("125228011").HolderName)
        assertEquals("Enabled", PolicyByNumber("125228011").AutoPay)
        assertEquals("Prins", ScreenDataParser.NormaliseHolderName("Prins"))
        assertTrue(ScreenDataParser.IsPlausibleHolderName("Kajal"))
        assertTrue(ScreenDataParser.IsPlausibleHolderName("Prins"))
    }

    @Test
    fun `a card label never becomes the holder`() {
        assertEquals("", PolicyByNumber("129831341").HolderName)
        assertEquals("Disabled", PolicyByNumber("129831341").AutoPay)
        assertFalse(ScreenDataParser.IsPlausibleHolderName("Grace Expiry Date"))
        assertFalse(ScreenDataParser.IsPlausibleHolderName("Enabled"))
        assertFalse(ScreenDataParser.IsPlausibleHolderName("Disabled"))
        assertFalse(ScreenDataParser.IsPlausibleHolderName("Customer Details"))
        assertFalse(ScreenDataParser.IsPlausibleHolderName("Commissions"))
    }

    @Test
    fun `an LA marker is recognised whatever its case`() {
        assertEquals(
            "Ojaswi Jaiswal(La) Anand Jaiswal(P)",
            PolicyByNumber("166251050").HolderName
        )
        assertTrue(
            ScreenDataParser.IsPlausibleHolderName("Ojaswi Jaiswal(La) Anand Jaiswal(P)")
        )
        assertEquals(
            "Ojaswi Jaiswal",
            ScreenDataParser.NormaliseHolderName("Ojaswi Jaiswal(La) Anand Jaiswal(P)")
        )
    }

    @Test
    fun `a date is the fup date only under a Renewal Due Date label`() {
        val PolicyItem = PolicyByNumber("156260854")
        assertEquals("11 Aug 2026", PolicyItem.RenewalDueDate)
        assertEquals("Renewal Due Date", PolicyItem.RenewalDateLabel)
        assertEquals("11 Aug 2026", PolicyItem.RenewalDateValue)
    }

    @Test
    fun `a grace expiry date is never stored as the fup date`() {
        val PolicyItem = PolicyByNumber("129831341")
        assertEquals("", PolicyItem.RenewalDueDate)
        assertEquals("Grace Expiry Date", PolicyItem.RenewalDateLabel)
        assertEquals("31 Aug 2026", PolicyItem.RenewalDateValue)
    }

    @Test
    fun `the card date label never overwrites a real renewal badge`() {
        assertEquals("First Year Renewal", PolicyByNumber("125226685").RenewalType)
        assertEquals("28 Aug 2026", PolicyByNumber("125226685").RenewalDueDate)
    }

    @Test
    fun `a date label is told apart from a renewal badge`() {
        assertTrue(ScreenDataParser.IsCardDateLabel("Renewal Due Date"))
        assertTrue(ScreenDataParser.IsCardDateLabel("Grace Expiry Date"))
        assertTrue(ScreenDataParser.IsCardDateLabel("Revival without DGH expiry date"))
        assertFalse(ScreenDataParser.IsCardDateLabel("Renewal due in 10 days"))
        assertFalse(ScreenDataParser.IsCardDateLabel("Renewal due today"))
        assertFalse(ScreenDataParser.IsCardDateLabel("First Year Renewal"))
        assertFalse(ScreenDataParser.IsCardDateLabel("28 Aug 2026"))
        assertTrue(ScreenDataParser.IsRenewalDueLabel("Renewal Due Date"))
        assertFalse(ScreenDataParser.IsRenewalDueLabel("Grace Expiry Date"))
        assertFalse(ScreenDataParser.IsRenewalDueLabel(""))
    }

    @Test
    fun `every renewal due card in the sample keeps its date`() {
        val DatedPolicies = listOf(
            "156260854" to "11 Aug 2026", "166248113" to "01 Feb 2026",
            "125226685" to "28 Aug 2026", "166250947" to "22 May 2024",
            "156264678" to "28 Aug 2026", "128636411" to "28 Aug 2026",
            "166251050" to "27 Nov 2026", "125228011" to "28 Aug 2026"
        )
        for ((NumberText, DateText) in DatedPolicies) {
            assertEquals(DateText, PolicyByNumber(NumberText).RenewalDueDate)
        }
    }

    @Test
    fun `auto pay values are never mistaken for a holder`() {
        assertEquals("Disabled", PolicyByNumber("156264678").AutoPay)
        assertEquals("Enabled", PolicyByNumber("128636411").AutoPay)
    }
}
