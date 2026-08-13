@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.RecordFieldChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordMergeTest {

    // ------------------------------------------------------- ResolveField

    @Test
    fun ResolveField_FillsABlankWithoutRecordingAChange() {
        val ChangeSink = mutableListOf<RecordFieldChange>()

        val ResultValue = RecordMerge.ResolveField(
            RecordKey = "146345511",
            FieldName = "Sum assured",
            ExistingValue = "",
            IncomingValue = "₹12,50,000",
            ChangeSink = ChangeSink
        )

        assertEquals("₹12,50,000", ResultValue)
        assertTrue(ChangeSink.isEmpty())
    }

    @Test
    fun ResolveField_KeepsExistingWhenIncomingIsBlank() {
        val ChangeSink = mutableListOf<RecordFieldChange>()

        val ResultValue = RecordMerge.ResolveField(
            RecordKey = "146345511",
            FieldName = "Sum assured",
            ExistingValue = "₹12,50,000",
            IncomingValue = "",
            ChangeSink = ChangeSink
        )

        assertEquals("₹12,50,000", ResultValue)
        assertTrue(ChangeSink.isEmpty())
    }

    @Test
    fun ResolveField_OverwritesAndLogsWhenBothDiffer() {
        val ChangeSink = mutableListOf<RecordFieldChange>()

        val ResultValue = RecordMerge.ResolveField(
            RecordKey = "146345511",
            FieldName = "Status",
            ExistingValue = "Inforce",
            IncomingValue = "Lapsed",
            ChangeSink = ChangeSink
        )

        assertEquals("Lapsed", ResultValue)
        assertEquals(1, ChangeSink.size)
        assertEquals("146345511", ChangeSink.first().RecordKey)
        assertEquals("Status", ChangeSink.first().FieldName)
        assertEquals("Inforce", ChangeSink.first().OldValue)
        assertEquals("Lapsed", ChangeSink.first().NewValue)
    }

    @Test
    fun ResolveField_LogsNothingWhenValuesAreIdentical() {
        val ChangeSink = mutableListOf<RecordFieldChange>()

        RecordMerge.ResolveField(
            RecordKey = "146345511",
            FieldName = "Status",
            ExistingValue = "Lapsed",
            IncomingValue = "Lapsed",
            ChangeSink = ChangeSink
        )

        assertTrue(ChangeSink.isEmpty())
    }

    // -------------------------------------------------------- MergePolicy

    @Test
    fun MergePolicy_FillsGapsFromAResumedCaptureWithoutLosingEarlierFields() {
        val StoredPolicy = CustomerPolicy(
            PolicyNumber = "146345511",
            HolderName = "Pushpender Khulbe",
            PremiumAmount = "₹5,641",
            SumAssured = "₹12,50,000"
        )
        val ResumedPolicy = CustomerPolicy(
            PolicyNumber = "146345511",
            TermPPT = "20/15",
            DateOfCommencement = "25 Jan 2022",
            DateOfMaturity = "25 Jan 2091"
        )

        val OutcomeVal = RecordMerge.MergePolicy(
            ExistingItem = StoredPolicy,
            IncomingItem = ResumedPolicy
        )

        assertEquals("Pushpender Khulbe", OutcomeVal.Record.HolderName)
        assertEquals("₹5,641", OutcomeVal.Record.PremiumAmount)
        assertEquals("20/15", OutcomeVal.Record.TermPPT)
        assertEquals("25 Jan 2091", OutcomeVal.Record.DateOfMaturity)
        assertTrue(OutcomeVal.Changes.isEmpty())
    }

    @Test
    fun MergePolicy_RecordsOnlyTheFieldsThatActuallyChanged() {
        val StoredPolicy = CustomerPolicy(
            PolicyNumber = "146345511",
            HolderName = "Pushpender Khulbe",
            Status = "Inforce",
            PremiumAmount = "₹5,641"
        )
        val ResumedPolicy = CustomerPolicy(
            PolicyNumber = "146345511",
            HolderName = "Pushpender Khulbe",
            Status = "Lapsed",
            PremiumAmount = "₹5,641"
        )

        val OutcomeVal = RecordMerge.MergePolicy(
            ExistingItem = StoredPolicy,
            IncomingItem = ResumedPolicy
        )

        assertEquals("Lapsed", OutcomeVal.Record.Status)
        assertEquals(1, OutcomeVal.Changes.size)
        assertEquals("Status", OutcomeVal.Changes.first().FieldName)
    }

    @Test
    fun MergePolicy_BlankIncomingRecordNeverErasesStoredData() {
        val StoredPolicy = CustomerPolicy(
            PolicyNumber = "146345511",
            HolderName = "Pushpender Khulbe",
            TermPPT = "20/15",
            CommissionType = "First year",
            DateOfMaturity = "25 Jan 2091"
        )

        val OutcomeVal = RecordMerge.MergePolicy(
            ExistingItem = StoredPolicy,
            IncomingItem = CustomerPolicy(PolicyNumber = "146345511")
        )

        assertEquals(StoredPolicy.HolderName, OutcomeVal.Record.HolderName)
        assertEquals(StoredPolicy.TermPPT, OutcomeVal.Record.TermPPT)
        assertEquals(StoredPolicy.CommissionType, OutcomeVal.Record.CommissionType)
        assertEquals(StoredPolicy.DateOfMaturity, OutcomeVal.Record.DateOfMaturity)
        assertTrue(OutcomeVal.Changes.isEmpty())
    }

    // ------------------------------------------------------- MergeRenewal

    @Test
    fun MergeRenewal_FillsFieldsMissedByAPartialFirstRead() {
        val StoredRenewal = FupPolicy(
            PolicyNumber = "156264678",
            HolderName = "Somesh Khulbe",
            PaymentDate = "08 Aug 2026"
        )
        val ResumedRenewal = FupPolicy(
            PolicyNumber = "156264678",
            PaymentDate = "08 Aug 2026",
            ModeOfPayment = "Cash",
            Status = "Paid in Grace Period"
        )

        val OutcomeVal = RecordMerge.MergeRenewal(
            ExistingItem = StoredRenewal,
            IncomingItem = ResumedRenewal
        )

        assertEquals("Somesh Khulbe", OutcomeVal.Record.HolderName)
        assertEquals("Cash", OutcomeVal.Record.ModeOfPayment)
        assertEquals("Paid in Grace Period", OutcomeVal.Record.Status)
        assertTrue(OutcomeVal.Changes.isEmpty())
    }

    @Test
    fun RenewalKey_SeparatesRepeatPaymentsOnTheSamePolicy() {
        val FirstPayment = FupPolicy(PolicyNumber = "156264678", PaymentDate = "08 Aug 2026")
        val SecondPayment = FupPolicy(PolicyNumber = "156264678", PaymentDate = "08 Sep 2026")

        assertTrue(
            RecordMerge.RenewalKey(RecordItem = FirstPayment) !=
                    RecordMerge.RenewalKey(RecordItem = SecondPayment)
        )
    }

    // ----------------------------------------- HasCompletePolicyDetails

    @Test
    fun HasCompletePolicyDetails_RequiresAllThreeSections() {
        val OnlyPolicyDetails = CustomerPolicy(PolicyNumber = "1", TermPPT = "20/15")
        val MissingKeyDates = CustomerPolicy(
            PolicyNumber = "1",
            TermPPT = "20/15",
            CommissionType = "First year"
        )
        val CompletePolicy = CustomerPolicy(
            PolicyNumber = "1",
            TermPPT = "20/15",
            CommissionType = "First year",
            DateOfCommencement = "25 Jan 2022"
        )

        assertEquals(false, RecordMerge.HasCompletePolicyDetails(PolicyItem = OnlyPolicyDetails))
        assertEquals(false, RecordMerge.HasCompletePolicyDetails(PolicyItem = MissingKeyDates))
        assertEquals(true, RecordMerge.HasCompletePolicyDetails(PolicyItem = CompletePolicy))
    }
}
