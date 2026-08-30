package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusChipRulesTest {

    private val DetailViewNodes = listOf(
        "back", "Detailed Policy View", "125226685", "873 - LIC'S INDEX PLUS",
        "detailed policy view button icon", "First Year Renewal", "Contact Details Absent",
        "KYC not updated", "PAN not updated", "NEFT not updated", "Address Missing",
        "Premium Amount (excl. GST)", "₹", "5,000/Month", "Sum Assured", "₹", "6,00,000",
        "Policy Details", "Please contact the customer to update missing details", "Pay Premium"
    )

    @Test
    fun `every chip on the detail screen is captured`() {
        val Chips = StatusChipRules.Extract(Nodes = DetailViewNodes)
        assertEquals(
            listOf(
                "Contact Details Absent",
                "KYC not updated",
                "PAN not updated",
                "NEFT not updated",
                "Address Missing"
            ),
            Chips
        )
    }

    @Test
    fun `renewal type is not treated as a status chip`() {
        assertFalse(StatusChipRules.IsStatusChip("First Year Renewal"))
        assertFalse(StatusChipRules.Extract(DetailViewNodes).contains("First Year Renewal"))
    }

    @Test
    fun `overflow markers and prose are rejected`() {
        assertFalse(StatusChipRules.IsStatusChip("+3"))
        assertFalse(StatusChipRules.IsStatusChip("+1"))
        assertFalse(
            StatusChipRules.IsStatusChip("Please contact the customer to update missing details")
        )
    }

    @Test
    fun `polarity is derived from text without needing colour`() {
        assertEquals(
            StatusChipRules.Polarity.NEGATIVE,
            StatusChipRules.PolarityOf("PAN not updated")
        )
        assertEquals(
            StatusChipRules.Polarity.POSITIVE,
            StatusChipRules.PolarityOf("PAN updated")
        )
        assertEquals(
            StatusChipRules.Polarity.NEGATIVE,
            StatusChipRules.PolarityOf("Contact Details Absent")
        )
        assertEquals(
            StatusChipRules.Polarity.NEGATIVE,
            StatusChipRules.PolarityOf("Address Missing")
        )
    }

    @Test
    fun `positive chips survive capture`() {
        val Chips = StatusChipRules.Extract(listOf("PAN updated", "KYC not updated"))
        assertEquals(listOf("PAN updated", "KYC not updated"), Chips)
    }

    @Test
    fun `lapsed variants are captured as chips`() {
        assertTrue(StatusChipRules.IsStatusChip("Lapsed"))
        assertTrue(StatusChipRules.IsStatusChip("Lapsed, DGH Required"))
    }

    @Test
    fun `duplicates are collapsed and order is preserved`() {
        val Chips = StatusChipRules.Extract(
            listOf("KYC not updated", "kyc not updated", "NEFT not updated")
        )
        assertEquals(listOf("KYC not updated", "NEFT not updated"), Chips)
    }

    @Test
    fun `merge prefers a non empty incoming list and keeps existing otherwise`() {
        assertEquals(
            listOf("PAN updated"),
            StatusChipRules.Merge(listOf("KYC not updated"), listOf("PAN updated"))
        )
        assertEquals(
            listOf("KYC not updated"),
            StatusChipRules.Merge(listOf("KYC not updated"), null)
        )
        assertEquals(null, StatusChipRules.Merge(null, emptyList()))
    }

    @Test
    fun `unrecognised future chip is still captured as neutral`() {
        assertTrue(StatusChipRules.IsStatusChip("Bank Mandate Missing"))
        assertEquals(
            StatusChipRules.Polarity.NEGATIVE,
            StatusChipRules.PolarityOf("Bank Mandate Missing")
        )
    }
}
