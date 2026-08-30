package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PolicyStatusRulesTest {

    private val Today = LocalDate.of(2026, 8, 30)

    private fun Status(
        Fup: String?,
        Frequency: String? = "Month",
        Doc: String? = null
    ): String = PolicyStatusRules.Compute(
        FupText = Fup,
        FrequencyText = Frequency,
        CommencementText = Doc,
        Today = Today
    )

    @Test
    fun `single premium is always in force even with a stale past due date`() {
        assertEquals(
            PolicyStatusRules.IN_FORCE,
            Status(Fup = "15 Apr 2025", Frequency = "Single Premium")
        )
    }

    @Test
    fun `a doc equal to the fup means no premium has fallen due yet`() {
        assertEquals(
            PolicyStatusRules.IN_FORCE,
            Status(Fup = "20 May 2024", Frequency = "Year", Doc = "20 May 2024")
        )
        assertTrue(
            PolicyStatusRules.HasNoRealDueDate(
                FrequencyText = "Year",
                FupText = "20 May 2024",
                CommencementText = "2024-05-20"
            )
        )
    }

    @Test
    fun `a genuine due date after the doc still runs the ladder`() {
        assertEquals(
            PolicyStatusRules.LAPSED,
            Status(Fup = "20 May 2024", Frequency = "Year", Doc = "20 May 2023")
        )
        assertFalse(
            PolicyStatusRules.HasNoRealDueDate(
                FrequencyText = "Year",
                FupText = "20 May 2024",
                CommencementText = "20 May 2023"
            )
        )
    }

    @Test
    fun `no real due date needs both dates before it can compare them`() {
        assertFalse(
            PolicyStatusRules.HasNoRealDueDate(
                FrequencyText = "Month",
                FupText = "",
                CommencementText = "20 May 2024"
            )
        )
        assertFalse(
            PolicyStatusRules.HasNoRealDueDate(
                FrequencyText = "Month",
                FupText = "20 May 2024",
                CommencementText = ""
            )
        )
    }

    @Test
    fun `a doc-equal date is an artefact on either side of a merge`() {
        assertEquals(
            "",
            PolicyStatusRules.RealDueDateOrBlank(
                FrequencyText = "Year",
                FupText = "20 May 2024",
                CommencementText = "20 May 2024"
            )
        )
        assertEquals(
            "",
            PolicyStatusRules.RealDueDateOrBlank(
                FrequencyText = "Single Premium",
                FupText = "15 Apr 2025",
                CommencementText = ""
            )
        )
        assertEquals(
            "20 May 2025",
            PolicyStatusRules.RealDueDateOrBlank(
                FrequencyText = "Year",
                FupText = "20 May 2025",
                CommencementText = "20 May 2024"
            )
        )
        assertEquals(
            "",
            PolicyStatusRules.RealDueDateOrBlank(
                FrequencyText = "Year",
                FupText = "",
                CommencementText = "20 May 2024"
            )
        )
    }

    @Test
    fun `missing fup yields unknown rather than a guess`() {
        assertEquals(PolicyStatusRules.UNKNOWN, Status(Fup = ""))
        assertEquals(PolicyStatusRules.UNKNOWN, Status(Fup = null))
    }

    @Test
    fun `future and same day due dates are in force`() {
        assertEquals(PolicyStatusRules.IN_FORCE, Status(Fup = "28 Sep 2026"))
        assertEquals(PolicyStatusRules.IN_FORCE, Status(Fup = "30 Aug 2026"))
    }

    @Test
    fun `monthly grace ends at fifteen days`() {
        assertEquals(PolicyStatusRules.GRACE, Status(Fup = "15 Aug 2026", Frequency = "Month"))
        assertEquals(
            PolicyStatusRules.OUTSTANDING,
            Status(Fup = "14 Aug 2026", Frequency = "Month")
        )
    }

    @Test
    fun `non monthly grace ends at thirty days`() {
        assertEquals(
            PolicyStatusRules.GRACE,
            Status(Fup = "31 Jul 2026", Frequency = "Half Year")
        )
        assertEquals(
            PolicyStatusRules.OUTSTANDING,
            Status(Fup = "30 Jul 2026", Frequency = "Half Year")
        )
    }

    @Test
    fun `absent frequency falls back to thirty day grace`() {
        assertEquals(PolicyStatusRules.GRACE, Status(Fup = "31 Jul 2026", Frequency = ""))
    }

    @Test
    fun `outstanding ends at one hundred eighty days`() {
        assertEquals(PolicyStatusRules.OUTSTANDING, Status(Fup = "02 Mar 2026"))
        assertEquals(PolicyStatusRules.LAPSED, Status(Fup = "01 Mar 2026"))
    }

    @Test
    fun `beyond outstanding without a doc stays lapsed`() {
        assertEquals(PolicyStatusRules.LAPSED, Status(Fup = "15 Apr 2025", Doc = null))
    }

    @Test
    fun `legacy policy needs three full years for paid up`() {
        assertEquals(
            PolicyStatusRules.PAID_UP,
            Status(Fup = "12 Jun 2017", Doc = "12 Jun 2014")
        )
        assertEquals(
            PolicyStatusRules.REDUCED_PAID_UP,
            Status(Fup = "11 Jun 2017", Doc = "12 Jun 2014")
        )
    }

    @Test
    fun `current policy needs two full years for paid up`() {
        assertEquals(
            PolicyStatusRules.PAID_UP,
            Status(Fup = "13 Jun 2018", Doc = "13 Jun 2016")
        )
        assertEquals(
            PolicyStatusRules.REDUCED_PAID_UP,
            Status(Fup = "12 Jun 2018", Doc = "13 Jun 2016")
        )
    }

    @Test
    fun `boundary date itself counts as legacy and needs three years`() {
        assertEquals(
            PolicyStatusRules.REDUCED_PAID_UP,
            Status(Fup = "12 Jun 2016", Doc = "12 Jun 2014")
        )
        assertEquals(
            PolicyStatusRules.PAID_UP,
            Status(Fup = "13 Jun 2016", Doc = "13 Jun 2014")
        )
    }

    @Test
    fun `partial years are truncated not rounded up`() {
        assertEquals(
            PolicyStatusRules.REDUCED_PAID_UP,
            Status(Fup = "11 Jun 2017", Doc = "13 Jun 2014")
        )
    }

    @Test
    fun `date parsing accepts the formats the super app emits`() {
        assertEquals(LocalDate.of(2026, 8, 28), PolicyStatusRules.ParseDate("28 Aug 2026"))
        assertEquals(LocalDate.of(2026, 8, 28), PolicyStatusRules.ParseDate("2026-08-28"))
        assertEquals(LocalDate.of(2026, 8, 28), PolicyStatusRules.ParseDate("28/08/2026"))
        assertEquals(LocalDate.of(2026, 2, 28), PolicyStatusRules.ParseDate("28 February 2026"))
        assertEquals(null, PolicyStatusRules.ParseDate("not a date"))
        assertEquals(null, PolicyStatusRules.ParseDate("32 Aug 2026"))
    }

    @Test
    fun `grace days respect the mode vocabulary the app emits`() {
        assertEquals(15L, PolicyStatusRules.GraceDaysFor("Month"))
        assertEquals(30L, PolicyStatusRules.GraceDaysFor("Quarter"))
        assertEquals(30L, PolicyStatusRules.GraceDaysFor("Half Year"))
        assertEquals(30L, PolicyStatusRules.GraceDaysFor("Year"))
    }

    @Test
    fun `adverse and attention buckets are disjoint`() {
        assertTrue(PolicyStatusRules.IsAdverse(PolicyStatusRules.LAPSED))
        assertTrue(PolicyStatusRules.IsAdverse(PolicyStatusRules.REDUCED_PAID_UP))
        assertTrue(PolicyStatusRules.IsAttention(PolicyStatusRules.GRACE))
        assertFalse(PolicyStatusRules.IsAdverse(PolicyStatusRules.IN_FORCE))
        assertFalse(PolicyStatusRules.IsAttention(PolicyStatusRules.IN_FORCE))
    }
}
