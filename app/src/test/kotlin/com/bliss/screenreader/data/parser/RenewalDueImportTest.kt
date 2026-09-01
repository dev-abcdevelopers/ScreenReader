@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.DueDateSkipReason
import com.bliss.screenreader.data.model.RenewalDuePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalDueImportTest {

    private fun PolicyOf(
        PolicyNumber: String = "166253803",
        RenewalDueDate: String = "",
        PremiumFrequency: String = "Month",
        HolderName: String = "Shanker Dutt Sharma"
    ) = CustomerPolicy(
        HolderName = HolderName,
        PolicyNumber = PolicyNumber,
        PlanCode = "745",
        RenewalDueDate = RenewalDueDate,
        PremiumFrequency = PremiumFrequency
    )

    private fun DueOf(
        PolicyNumber: String = "166253803",
        DateLabel: String = RenewalDueParser.LABEL_RENEWAL_DUE_DATE,
        DateValue: String = "04 Sep 2026",
        PremiumFrequency: String? = "Month"
    ) = RenewalDuePolicy(
        PolicyNumber = PolicyNumber,
        PlanCode = "745",
        HolderName = "Shanker Dutt Sharma",
        PremiumFrequency = PremiumFrequency,
        DateLabel = DateLabel,
        DateValue = DateValue
    )

    @Test
    fun `the due date is written exactly as read with no projection`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "04 Aug 2026")),
            DueRecords = listOf(DueOf(DateValue = "04 Sep 2026"))
        )
        assertEquals("04 Sep 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.UpdatedCount)
        assertEquals("04 Aug 2026", OutcomeObj.Changes.first().OldValue)
        assertEquals("04 Sep 2026", OutcomeObj.Changes.first().NewValue)
    }

    @Test
    fun `a blank due date is filled in`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "")),
            DueRecords = listOf(DueOf(DateValue = "04 Sep 2026"))
        )
        assertEquals("04 Sep 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.UpdatedCount)
        assertEquals("", OutcomeObj.Updates.first().OldDate)
    }

    @Test
    fun `an earlier date never moves the due date backwards`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "04 Dec 2026")),
            DueRecords = listOf(DueOf(DateValue = "04 Sep 2026"))
        )
        assertEquals("04 Dec 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(0, OutcomeObj.UpdatedCount)
        assertEquals(1, OutcomeObj.UnchangedCount)
        assertEquals(DueDateSkipReason.ALREADY_CURRENT, OutcomeObj.Skips.first().Reason)
    }

    @Test
    fun `the same date changes nothing`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "04 Sep 2026")),
            DueRecords = listOf(DueOf(DateValue = "04 Sep 2026"))
        )
        assertEquals(0, OutcomeObj.UpdatedCount)
        assertEquals(1, OutcomeObj.UnchangedCount)
        assertTrue(OutcomeObj.Changes.isEmpty())
    }

    @Test
    fun `a grace expiry never becomes a due date`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "01 Jan 2026")),
            DueRecords = listOf(
                DueOf(
                    DateLabel = RenewalDueParser.LABEL_GRACE_EXPIRY_DATE,
                    DateValue = "14 Sep 2026"
                )
            )
        )
        assertEquals("01 Jan 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(0, OutcomeObj.UpdatedCount)
        assertEquals(1, OutcomeObj.SkippedCount)
        assertEquals(DueDateSkipReason.GRACE_DATE, OutcomeObj.Skips.first().Reason)
    }

    @Test
    fun `a grace expiry does not fill a blank due date either`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "")),
            DueRecords = listOf(
                DueOf(
                    DateLabel = RenewalDueParser.LABEL_GRACE_EXPIRY_DATE,
                    DateValue = "14 Sep 2026"
                )
            )
        )
        assertEquals("", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(DueDateSkipReason.GRACE_DATE, OutcomeObj.Skips.first().Reason)
    }

    @Test
    fun `an unreadable date is skipped and counted`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "01 Jan 2026")),
            DueRecords = listOf(DueOf(DateValue = ""))
        )
        assertEquals("01 Jan 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(DueDateSkipReason.NO_DUE_DATE, OutcomeObj.Skips.first().Reason)
        assertEquals(1, OutcomeObj.SkippedCount)
    }

    @Test
    fun `a missing frequency never blocks the write`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "", PremiumFrequency = "")),
            DueRecords = listOf(DueOf(PremiumFrequency = null))
        )
        assertEquals("04 Sep 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.UpdatedCount)
    }

    @Test
    fun `a policy with no renewals due row is left alone and never counted`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(PolicyNumber = "111111111", RenewalDueDate = "01 Jan 2026")),
            DueRecords = listOf(DueOf(PolicyNumber = "222222222"))
        )
        assertEquals("01 Jan 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(0, OutcomeObj.MatchedCount)
        assertTrue(OutcomeObj.Skips.isEmpty())
    }

    @Test
    fun `the same policy seen twice keeps the later date`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "")),
            DueRecords = listOf(
                DueOf(DateValue = "04 Sep 2026"),
                DueOf(DateValue = "04 Oct 2026")
            )
        )
        assertEquals("04 Oct 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.MatchedCount)
    }

    @Test
    fun `a real due row beats a grace row for the same policy`() {
        val BestMap = RenewalDueImport.BestByPolicy(
            DueRecords = listOf(
                DueOf(
                    DateLabel = RenewalDueParser.LABEL_GRACE_EXPIRY_DATE,
                    DateValue = "31 Dec 2026"
                ),
                DueOf(DateValue = "04 Sep 2026")
            )
        )
        assertEquals("04 Sep 2026", BestMap.getValue("166253803").DateValue)
    }

    @Test
    fun `numeric and display dates compare correctly`() {
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "04/08/2026")),
            DueRecords = listOf(DueOf(DateValue = "04 Sep 2026"))
        )
        assertEquals("04 Sep 2026", OutcomeObj.Policies.first().RenewalDueDate)
        assertEquals(1, OutcomeObj.UpdatedCount)
    }

    @Test
    fun `the field name matches the renewal history importer`() {
        assertEquals(RenewalDueProjection.FIELD_NAME, RenewalDueImport.FIELD_NAME)
        val OutcomeObj = RenewalDueImport.Apply(
            Policies = listOf(PolicyOf(RenewalDueDate = "")),
            DueRecords = listOf(DueOf())
        )
        assertEquals("Renewal due date", OutcomeObj.Changes.first().FieldName)
    }

    @Test
    fun `empty input changes nothing`() {
        val PolicyList = listOf(PolicyOf(RenewalDueDate = "01 Jan 2026"))
        assertEquals(
            PolicyList,
            RenewalDueImport.Apply(Policies = PolicyList, DueRecords = emptyList()).Policies
        )
        assertEquals(
            emptyList<CustomerPolicy>(),
            RenewalDueImport.Apply(Policies = emptyList(), DueRecords = listOf(DueOf())).Policies
        )
    }
}
