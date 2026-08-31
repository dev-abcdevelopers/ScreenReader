@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.model

import com.bliss.screenreader.data.parser.PolicyStatusRules
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerPolicyStatusTest {

    @Test
    fun `a revival expiry date is never treated as the next premium date`() {
        val PolicyItem = CustomerPolicy(
            PolicyNumber = "146345738",
            HolderName = "Himanshu Khulbe",
            Status = "Lapsed, DGH Required",
            PremiumFrequency = "Half Year",
            RenewalDueDate = "",
            RenewalDateLabel = "Revival expiry date",
            RenewalDateValue = "01 Jan 2031"
        )

        assertEquals("", PolicyItem.FupForStatus)
        assertEquals(PolicyStatusRules.LAPSED, PolicyItem.NormalizedStatus)
    }

    @Test
    fun `a grace expiry card falls back to the status the card printed`() {
        val PolicyItem = CustomerPolicy(
            PolicyNumber = "129835618",
            Status = "Grace Expiring in 14 days",
            PremiumFrequency = "Month",
            RenewalDueDate = "",
            RenewalDateLabel = "Grace Expiry Date",
            RenewalDateValue = "11 Sep 2026"
        )

        assertEquals("", PolicyItem.FupForStatus)
        assertEquals(PolicyStatusRules.GRACE, PolicyItem.NormalizedStatus)
    }

    @Test
    fun `a real renewal due date still drives the status engine`() {
        val PolicyItem = CustomerPolicy(
            PolicyNumber = "856942119",
            PremiumFrequency = "Month",
            RenewalDueDate = "22 Jun 2027",
            RenewalDateLabel = "Renewal Due Date",
            RenewalDateValue = "22 Jun 2027"
        )

        assertEquals("22 Jun 2027", PolicyItem.FupForStatus)
        assertEquals(PolicyStatusRules.IN_FORCE, PolicyItem.NormalizedStatus)
    }

    @Test
    fun `a row captured before labels existed keeps the old fallback`() {
        val PolicyItem = CustomerPolicy(
            PolicyNumber = "156255273",
            PremiumFrequency = "Month",
            RenewalDueDate = "",
            RenewalDateLabel = "",
            RenewalDateValue = "22 Jun 2027"
        )

        assertEquals("22 Jun 2027", PolicyItem.FupForStatus)
        assertEquals(PolicyStatusRules.IN_FORCE, PolicyItem.NormalizedStatus)
    }
}
