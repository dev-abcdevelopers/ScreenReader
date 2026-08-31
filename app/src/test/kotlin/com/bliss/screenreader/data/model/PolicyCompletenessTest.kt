@file:Suppress("FunctionName", "LocalVariableName", "TestFunctionName", "PrivatePropertyName",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyCompletenessTest {

    private val Labels = PolicyCompleteness.LabelSet(
        CardTitle = "Card",
        PolicyDetailsTitle = "Policy details",
        CommissionsTitle = "Commissions",
        KeyDatesTitle = "Key dates",
        CustomerTitle = "Customer",
        PlanCode = "Plan code",
        PlanName = "Plan name",
        Status = "Status",
        Premium = "Premium",
        PremiumFrequency = "Frequency",
        AutoPay = "Auto pay",
        RenewalType = "Renewal type",
        RenewalDue = "Renewal due",
        SumAssured = "Sum assured",
        TermPpt = "Term / PPT",
        CommissionType = "Commission type",
        CommissionPaid = "Commission paid",
        BonusCommission = "Bonus commission",
        CommissionPaymentDate = "Commission paid on",
        CommissionPremiumDate = "Premium paid on",
        Commenced = "Commenced",
        PremiumsEnd = "Premiums end",
        Matures = "Matures",
        Mobile = "Mobile",
        Dob = "Date of birth",
        Address = "Address",
        FlagKyc = "KYC not updated",
        FlagNeft = "NEFT not updated",
        FlagNominee = "Nominee not updated",
        FlagMobile = "Mobile not updated",
        FlagAddress = "Address not updated",
        Email = "",
        Gender = "",
        Education = "",
        Occupation = "",
        MaritalStatus = "",
        AnnualIncome = ""
    )

    private val FastCapturedPolicy = CustomerPolicy(
        PolicyNumber = "146345511",
        HolderName = "Pushpender Khulbe",
        PlanCode = "945",
        PlanName = "LIC'S JEEVAN UMANG PLAN",
        Status = "Lapsed",
        PremiumAmount = "₹5,641",
        PremiumFrequency = "Month",
        AutoPay = "Disabled",
        RenewalType = "Revival without DGH expiry date",
        RenewalDueDate = "25 Sep 2026"
    )

    private val FullyCapturedPolicy = FastCapturedPolicy.copy(
        SumAssured = "₹12,50,000",
        TermPPT = "20/15",
        CommissionType = "First year",
        CommissionPaidAmount = "₹1,692",
        BonusCommission = "₹564",
        CommissionDateOfPayment = "14 Feb 2026",
        CommissionDateOfPremiumPayment = "10 Feb 2026",
        DateOfCommencement = "25 Jan 2022",
        EndOfPremiumPayingTerm = "25 Jan 2042",
        DateOfMaturity = "25 Jan 2091"
    )

    @Test
    fun Describe_CountsOnlyCapturableGroupsInTheDenominator() {
        val SummaryVal = PolicyCompleteness.Describe(
            PolicyItem = FullyCapturedPolicy,
            Labels = Labels
        )

        // Card 8 + policy details 2 + commissions 5 + key dates 3. The customer
        // group has no capture path yet, so it is excluded.
        assertEquals(18, SummaryVal.TotalCount)
        assertEquals(18, SummaryVal.CapturedCount)
        assertEquals(100, SummaryVal.Percent)
        assertTrue(SummaryVal.IsComplete)
    }

    @Test
    fun Describe_ReportsTheAccordionsAsMissingAfterAFastCapture() {
        val SummaryVal = PolicyCompleteness.Describe(
            PolicyItem = FastCapturedPolicy,
            Labels = Labels
        )

        assertEquals(8, SummaryVal.CapturedCount)
        assertEquals(18, SummaryVal.TotalCount)
        assertEquals(10, SummaryVal.MissingCount)
        assertFalse(SummaryVal.IsComplete)

        val MissingKeys = SummaryVal.MissingGroups.map { GroupRef -> GroupRef.Key }
        assertTrue(MissingKeys.contains(PolicyCompleteness.GROUP_POLICY_DETAILS))
        assertTrue(MissingKeys.contains(PolicyCompleteness.GROUP_COMMISSIONS))
        assertTrue(MissingKeys.contains(PolicyCompleteness.GROUP_KEY_DATES))
        assertFalse(MissingKeys.contains(PolicyCompleteness.GROUP_CARD))
    }

    @Test
    fun Describe_NeverReportsTheCustomerGroupAsMissing() {
        val SummaryVal = PolicyCompleteness.Describe(
            PolicyItem = FastCapturedPolicy,
            Labels = Labels
        )

        // Nothing navigates to the customer profile screen yet, so a bar that
        // counted it could never reach 100%.
        assertFalse(
            SummaryVal.MissingGroups.any { GroupRef ->
                GroupRef.Key == PolicyCompleteness.GROUP_CUSTOMER
            }
        )
        val CustomerGroup = SummaryVal.Groups.first { GroupRef ->
            GroupRef.Key == PolicyCompleteness.GROUP_CUSTOMER
        }
        assertFalse(CustomerGroup.IsCapturable)
        assertFalse(CustomerGroup.IsCountedTowardTotal)
        assertTrue(CustomerGroup.IsRefreshable)
        assertFalse(
            SummaryVal.Groups.any { GroupRef ->
                GroupRef.Key != PolicyCompleteness.GROUP_CUSTOMER && GroupRef.IsRefreshable
            }
        )
    }

    @Test
    fun Describe_DoesNotPenaliseAPolicyWithNoStatusFlags() {
        // Flags are absence markers: empty means the customer is fine. A
        // healthy policy must still be able to reach 100%.
        val HealthyPolicy = FullyCapturedPolicy.copy(
            KycStatus = "", NeftStatus = "", NomineeStatus = ""
        )
        val FlaggedPolicy = FullyCapturedPolicy.copy(
            KycStatus = "Not Updated", NeftStatus = "Not Updated"
        )

        val HealthySummary = PolicyCompleteness.Describe(PolicyItem = HealthyPolicy, Labels = Labels)
        val FlaggedSummary = PolicyCompleteness.Describe(PolicyItem = FlaggedPolicy, Labels = Labels)

        assertEquals(HealthySummary.TotalCount, FlaggedSummary.TotalCount)
        assertEquals(HealthySummary.CapturedCount, FlaggedSummary.CapturedCount)
        assertTrue(HealthySummary.IsComplete)
        assertTrue(FlaggedSummary.IsComplete)
    }

    @Test
    fun StatusFlags_ListOnlyTheMarkersThatArePresent() {
        val FlaggedPolicy = FullyCapturedPolicy.copy(
            KycStatus = "Not Updated",
            NeftStatus = "Not Updated"
        )

        val FlagList = PolicyCompleteness.StatusFlags(
            PolicyItem = FlaggedPolicy,
            Labels = Labels
        )

        assertEquals(listOf("KYC not updated", "NEFT not updated"), FlagList)
        assertTrue(
            PolicyCompleteness.StatusFlags(
                PolicyItem = FullyCapturedPolicy,
                Labels = Labels
            ).isEmpty()
        )
    }

    @Test
    fun Groups_ReportTheirOwnCompleteness() {
        val PartialPolicy = FastCapturedPolicy.copy(
            SumAssured = "₹12,50,000",
            DateOfCommencement = "25 Jan 2022"
        )
        val SummaryVal = PolicyCompleteness.Describe(PolicyItem = PartialPolicy, Labels = Labels)

        val PolicyDetails = SummaryVal.Groups.first { It -> It.Key == PolicyCompleteness.GROUP_POLICY_DETAILS }
        val KeyDates = SummaryVal.Groups.first { It -> It.Key == PolicyCompleteness.GROUP_KEY_DATES }
        val Commissions = SummaryVal.Groups.first { It -> It.Key == PolicyCompleteness.GROUP_COMMISSIONS }

        assertEquals(1, PolicyDetails.CapturedCount)
        assertEquals(2, PolicyDetails.TotalCount)
        assertFalse(PolicyDetails.IsComplete)

        assertEquals(1, KeyDates.CapturedCount)
        assertFalse(KeyDates.IsEmpty)

        assertTrue(Commissions.IsEmpty)
        assertEquals(0, Commissions.CapturedCount)
    }
}
