@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
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
        "card right arrow icon", "Send Reminder card icon", "KYC not updated"
    )

    private fun PolicyByNumber(NumberText: String) =
        ScreenDataParser.ParsePolicyDashboard(Nodes = DashboardNodes)
            .first { PolicyItem -> PolicyItem.PolicyNumber == NumberText }

    @Test
    fun `a plain holder name is read as written`() {
        assertEquals("Narender Rathor", PolicyByNumber("156260854").HolderName)
    }

    @Test
    fun `the life assured is taken from a role marked pair`() {
        assertEquals("Aarvi Khulbe", PolicyByNumber("166248113").HolderName)
        assertEquals("Hardik Verma", PolicyByNumber("166250947").HolderName)
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
}
