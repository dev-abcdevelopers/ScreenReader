@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.parser.CustomerProfileParser.ContactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerProfileParserTest {

    private val ProfilePaneNodes = listOf(
        "Daljeet Singh",
        "47 Years",
        "Policies", "Profile",
        "Contact Details",
        "Mobile Number", "9810326023", "View all",
        "Email ID", "daljeetsingh051078@....", "+1", "View all",
        "Communication Address", "JG-11/653, VIKAS PU....", "View all",
        "Personal Details",
        "Date of Birth", "05 Oct 1978",
        "Gender", "Male",
        "Education", "SSC",
        "Occupation", "Self Emp (Not Paying ITAX)",
        "Marital Status", "-",
        "Annual Income", "-"
    )

    @Test
    fun ParseProfilePane_ReadsPersonalDetailsAndSkipsDashes() {
        val ProfileObj = CustomerProfileParser.ParseProfilePane(
            Nodes = ProfilePaneNodes,
            CustomerNameVal = "Daljeet Singh"
        )

        assertEquals("05 Oct 1978", ProfileObj.Dob)
        assertEquals("Male", ProfileObj.Gender)
        assertEquals("SSC", ProfileObj.Education)
        assertEquals("Self Emp (Not Paying ITAX)", ProfileObj.Occupation)
        assertEquals("", ProfileObj.MaritalStatus)
        assertEquals("", ProfileObj.AnnualIncome)
    }

    @Test
    fun ParseProfilePane_HandlesRowOrderedLabels() {
        val RowOrderedNodes = listOf(
            "Personal Details",
            "Date of Birth", "Gender", "05 Oct 1978", "Male",
            "Education", "Occupation", "SSC", "Self Emp (Not Paying ITAX)"
        )
        val ProfileObj = CustomerProfileParser.ParseProfilePane(Nodes = RowOrderedNodes)

        assertEquals("05 Oct 1978", ProfileObj.Dob)
        assertEquals("Male", ProfileObj.Gender)
        assertEquals("SSC", ProfileObj.Education)
        assertEquals("Self Emp (Not Paying ITAX)", ProfileObj.Occupation)
    }

    @Test
    fun ParseProfilePane_KeepsTruncatedInlineValuesButMarksThemPartial() {
        val ProfileObj = CustomerProfileParser.ParseProfilePane(Nodes = ProfilePaneNodes)

        assertEquals(1, ProfileObj.Mobiles.size)
        assertEquals("9810326023", ProfileObj.Mobiles.first().Value)
        assertFalse(ProfileObj.Mobiles.first().IsPartial)

        assertEquals(1, ProfileObj.Emails.size)
        assertTrue(ProfileObj.Emails.first().IsPartial)
        assertEquals(1, ProfileObj.Addresses.size)
        assertTrue(ProfileObj.Addresses.first().IsPartial)
    }

    @Test
    fun ParseProfilePane_PrefersAFullValueThatMatchesTheTruncatedPrefix() {
        val ProfileObj = CustomerProfileParser.ParseProfilePane(
            Nodes = ProfilePaneNodes + listOf("daljeetsingh051078@gmail.com")
        )

        assertEquals("daljeetsingh051078@gmail.com", ProfileObj.Emails.first().Value)
        assertFalse(ProfileObj.Emails.first().IsPartial)
    }

    @Test
    fun ParseProfilePane_DoesNotAdoptADifferentAddressAsTheFullValue() {
        val ProfileObj = CustomerProfileParser.ParseProfilePane(
            Nodes = ProfilePaneNodes + listOf("someoneelse@gmail.com")
        )

        assertEquals("daljeetsingh051078@....", ProfileObj.Emails.first().Value)
        assertTrue(ProfileObj.Emails.first().IsPartial)
    }

    @Test
    fun ParseProfilePane_NeverTakesASheetTitleAsAFieldValue() {
        val ProfileObj = CustomerProfileParser.ParseProfilePane(
            Nodes = listOf(
                "Personal Details",
                "Occupation", "Employed Professional",
                "Annual Income",
                "Email ID(s)", "Mark as default email for emails"
            )
        )

        assertEquals("", ProfileObj.AnnualIncome)
        assertEquals("Employed Professional", ProfileObj.Occupation)
    }

    @Test
    fun NeedsSheet_TrueForChipAndForEllipsis_FalseForPlainValue() {
        assertFalse(
            CustomerProfileParser.NeedsSheet(
                Nodes = ProfilePaneNodes,
                LabelText = CustomerProfileParser.LABEL_MOBILE
            )
        )
        assertTrue(
            CustomerProfileParser.NeedsSheet(
                Nodes = ProfilePaneNodes,
                LabelText = CustomerProfileParser.LABEL_EMAIL
            )
        )
        assertTrue(
            CustomerProfileParser.NeedsSheet(
                Nodes = ProfilePaneNodes,
                LabelText = CustomerProfileParser.LABEL_ADDRESS
            )
        )
    }

    @Test
    fun ParseContactSheet_ReadsEmailsWithRelatedPolicies() {
        val SheetNodes = listOf(
            "Email ID(s)",
            "Mark as default email for emails",
            "daljeetsingh051078@gmail.com",
            "Policy(ies) Related:  166251128, 166251129",
            "daljeet.singh@work.co.in",
            "Policy(ies) Related:  166251135"
        )
        val ValueList = CustomerProfileParser.ParseContactSheet(
            Nodes = SheetNodes,
            KindVal = ContactKind.EMAIL
        )

        assertEquals(2, ValueList.size)
        assertEquals("daljeetsingh051078@gmail.com", ValueList[0].Value)
        assertEquals(listOf("166251128", "166251129"), ValueList[0].RelatedPolicies)
        assertEquals(listOf("166251135"), ValueList[1].RelatedPolicies)
        assertTrue(ValueList[0].IsDefault)
        assertFalse(ValueList[1].IsDefault)
    }

    @Test
    fun ParseContactSheet_JoinsAddressPincodeSplitAcrossNodes() {
        val SheetNodes = listOf(
            "Address(es)",
            "Mark as default address for address",
            "JG-11/653, VIKAS PURI WEST DELHI DELHI,",
            "110018",
            "Policy(ies) Related:  166251128, 166251129, 166251135"
        )
        val ValueList = CustomerProfileParser.ParseContactSheet(
            Nodes = SheetNodes,
            KindVal = ContactKind.ADDRESS
        )

        assertEquals(1, ValueList.size)
        assertEquals("JG-11/653, VIKAS PURI WEST DELHI DELHI, 110018", ValueList[0].Value)
        assertEquals(3, ValueList[0].RelatedPolicies.size)
    }

    @Test
    fun ParseContactSheet_MarksSelectedIndexAsDefault() {
        val SheetNodes = listOf(
            "Email ID(s)",
            "first@example.com",
            "second@example.com"
        )
        val ValueList = CustomerProfileParser.ParseContactSheet(
            Nodes = SheetNodes,
            KindVal = ContactKind.EMAIL,
            SelectedIndexVal = 1
        )

        assertFalse(ValueList[0].IsDefault)
        assertTrue(ValueList[1].IsDefault)
    }

    @Test
    fun ParseContactSheet_NormalisesMobileWithCountryCode() {
        val SheetNodes = listOf("Mobile Number(s)", "+91 98103 26023")
        val ValueList = CustomerProfileParser.ParseContactSheet(
            Nodes = SheetNodes,
            KindVal = ContactKind.MOBILE
        )

        assertEquals(1, ValueList.size)
        assertEquals("9810326023", ValueList[0].Value)
    }

    @Test
    fun ValueFor_PrefersRelatedPolicyThenDefault() {
        val ProfileObj = CustomerProfileParser.ParseProfilePane(Nodes = ProfilePaneNodes).copy(
            Emails = CustomerProfileParser.ParseContactSheet(
                Nodes = listOf(
                    "Email ID(s)",
                    "first@example.com",
                    "Policy(ies) Related:  166251128",
                    "second@example.com",
                    "Policy(ies) Related:  166251135"
                ),
                KindVal = ContactKind.EMAIL
            )
        )

        assertEquals(
            "second@example.com",
            ProfileObj.ValueFor(PolicyNumber = "166251135", Values = ProfileObj.Emails)
        )
        assertEquals(
            "first@example.com",
            ProfileObj.ValueFor(PolicyNumber = "999999999", Values = ProfileObj.Emails)
        )
        assertEquals("", ProfileObj.ValueFor(PolicyNumber = "166251135", Values = emptyList()))
    }

    @Test
    fun ToPolicyPatch_CarriesOnlyPersonalFields() {
        val ProfileObj = CustomerProfileParser.ParseProfilePane(
            Nodes = ProfilePaneNodes,
            CustomerNameVal = "Daljeet Singh"
        )
        val PatchItem = ProfileObj.ToPolicyPatch(PolicyNumber = "166251135")

        assertEquals("166251135", PatchItem.PolicyNumber)
        assertEquals("9810326023", PatchItem.MobileNumber)
        assertEquals("05 Oct 1978", PatchItem.Dob)
        assertEquals("Male", PatchItem.Gender)
        assertEquals("", PatchItem.HolderName)
        assertEquals("", PatchItem.PlanName)
        assertEquals("", PatchItem.PremiumAmount)
    }

    @Test
    fun ParsePolicyNumbers_ReadsCustomerPoliciesTab() {
        val TabNodes = listOf(
            "03", "Policy(ies)", "Page", "01", "of 01",
            "Lapsed, DGH Required",
            "KYC not updated",
            "166251135 | 936 - LIC'S NEW JEEVAN LABH PLAN",
            "Daljeet Singh",
            "Auto Pay", "Disabled",
            "Premium Amount (excl. GST)", "₹2,967/Month",
            "Send Reminder",
            "KYC not updated", "NEFT not updated",
            "166251128 | 849 - LIC'S NIVESH PLUS",
            "Daljeet Singh",
            "Auto Pay", "Disabled",
            "Premium Amount (excl. GST)", "₹1,00,000/Single Premium",
            "Send Reminder"
        )
        val NumberList = CustomerProfileParser.ParsePolicyNumbers(Nodes = TabNodes)

        assertTrue(NumberList.contains("166251135"))
        assertTrue(NumberList.contains("166251128"))
    }

    @Test
    fun ReadContactSheet_RealTreeExposesGroupsButNoValues() {
        val RealTree = ProfilePaneNodes + listOf(
            "Email ID(s)",
            "Mark as default email for emails",
            "Policy(ies) Related:",
            "166251128, 166251129",
            "Policy(ies) Related:",
            "166251135"
        )
        val SheetRead = CustomerProfileParser.ReadContactSheet(
            Nodes = RealTree,
            KindVal = ContactKind.EMAIL
        )

        assertEquals(0, SheetRead.Values.size)
        assertEquals(2, SheetRead.RelatedGroupCount)
        assertEquals(2, SheetRead.OrphanGroupCount)
    }

    @Test
    fun ReadContactSheet_AttachesRelatedNumbersFromTheFollowingNode() {
        val SheetRead = CustomerProfileParser.ReadContactSheet(
            Nodes = listOf(
                "Email ID(s)",
                "first@example.com",
                "Policy(ies) Related:",
                "166251128, 166251129",
                "second@example.com",
                "Policy(ies) Related:",
                "166251135"
            ),
            KindVal = ContactKind.EMAIL
        )

        assertEquals(2, SheetRead.Values.size)
        assertEquals(listOf("166251128", "166251129"), SheetRead.Values[0].RelatedPolicies)
        assertEquals(listOf("166251135"), SheetRead.Values[1].RelatedPolicies)
        assertEquals(0, SheetRead.OrphanGroupCount)
    }

    @Test
    fun ReadContactSheet_IgnoresValuesAboveTheSheetTitle() {
        val SheetRead = CustomerProfileParser.ReadContactSheet(
            Nodes = ProfilePaneNodes + listOf("Address(es)", "SOME STREET, DELHI", "110018"),
            KindVal = ContactKind.ADDRESS
        )

        assertEquals(1, SheetRead.Values.size)
        assertEquals("SOME STREET, DELHI, 110018", SheetRead.Values.first().Value)
    }

    @Test
    fun IsProfilePaneComplete_NeedsBothSections() {
        assertTrue(CustomerProfileParser.IsProfilePaneComplete(Nodes = ProfilePaneNodes))
        assertFalse(
            CustomerProfileParser.IsProfilePaneComplete(
                Nodes = listOf("Contact Details", "Mobile Number", "9810326023")
            )
        )
    }
}
