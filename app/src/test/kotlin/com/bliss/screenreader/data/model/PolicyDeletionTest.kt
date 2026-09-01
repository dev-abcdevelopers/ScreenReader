@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyDeletionTest {

    private fun PolicyOf(NumberText: String, NameText: String = "Vinod Joshi") =
        CustomerPolicy(HolderName = NameText, PolicyNumber = NumberText)

    private val PolicyList = listOf(
        PolicyOf("166253803", "Shanker Dutt Sharma"),
        PolicyOf("156264667", "Vinod Joshi"),
        PolicyOf("156258588", "Gaurav Barthwal")
    )

    @Test
    fun `the named policy is the only one removed`() {
        val Remaining = PolicyDeletion.RemainingPolicies(
            PolicyList = PolicyList,
            NumberList = listOf("156264667")
        )
        assertEquals(2, Remaining.size)
        assertTrue(Remaining.none { PolicyItem -> PolicyItem.PolicyNumber == "156264667" })
    }

    @Test
    fun `several policies go at once`() {
        val Remaining = PolicyDeletion.RemainingPolicies(
            PolicyList = PolicyList,
            NumberList = listOf("166253803", "156258588")
        )
        assertEquals(listOf("156264667"), Remaining.map { PolicyItem -> PolicyItem.PolicyNumber })
        assertEquals(2, PolicyDeletion.RemovedCount(PolicyList = PolicyList, NumberList = listOf("166253803", "156258588")))
    }

    @Test
    fun `an empty or blank selection removes nothing`() {
        assertEquals(PolicyList, PolicyDeletion.RemainingPolicies(PolicyList = PolicyList, NumberList = emptyList()))
        assertEquals(PolicyList, PolicyDeletion.RemainingPolicies(PolicyList = PolicyList, NumberList = listOf("", "   ")))
        assertEquals(0, PolicyDeletion.RemovedCount(PolicyList = PolicyList, NumberList = listOf(" ")))
    }

    @Test
    fun `a number that is not in the session changes nothing`() {
        assertEquals(
            PolicyList,
            PolicyDeletion.RemainingPolicies(PolicyList = PolicyList, NumberList = listOf("999999999"))
        )
    }

    @Test
    fun `surrounding whitespace still matches`() {
        val Remaining = PolicyDeletion.RemainingPolicies(
            PolicyList = PolicyList,
            NumberList = listOf("  156264667  ")
        )
        assertEquals(2, Remaining.size)
    }

    @Test
    fun `field changes for the deleted policy go with it`() {
        val ChangeList = listOf(
            RecordFieldChange("166253803", "Renewal due date", "", "04 Sep 2026"),
            RecordFieldChange("156264667", "Renewal due date", "", "10 Sep 2026"),
            RecordFieldChange("156264667", "Mobile", "", "99999")
        )
        val Remaining = PolicyDeletion.RemainingChanges(
            ChangeList = ChangeList,
            NumberList = listOf("156264667")
        )
        assertEquals(1, Remaining.size)
        assertEquals("166253803", Remaining.first().RecordKey)
    }

    @Test
    fun `gap rows for the deleted policy go with it`() {
        val GapList = listOf(
            SessionGap("166253803", "Shanker Dutt Sharma", 0L),
            SessionGap("156264667", "Vinod Joshi", 0L)
        )
        val Remaining = PolicyDeletion.RemainingGaps(
            GapList = GapList,
            NumberList = listOf("166253803")
        )
        assertEquals(listOf("156264667"), Remaining.map { GapItem -> GapItem.PolicyNumber })
    }

    @Test
    fun `a visited customer survives while any of their policies remain`() {
        val Remaining = PolicyDeletion.RemainingPolicies(
            PolicyList = PolicyList + PolicyOf("156264668", "Vinod Joshi"),
            NumberList = listOf("156264667")
        )
        val Visited = PolicyDeletion.RemainingVisitedCustomers(
            VisitedNames = listOf("VINOD JOSHI", "GAURAV BARTHWAL", "SHANKER DUTT SHARMA"),
            RemainingPolicyList = Remaining
        )
        assertTrue(Visited.contains("VINOD JOSHI"))
        assertEquals(3, Visited.size)
    }

    @Test
    fun `a visited customer drops once their last policy goes`() {
        val Remaining = PolicyDeletion.RemainingPolicies(
            PolicyList = PolicyList,
            NumberList = listOf("156264667")
        )
        val Visited = PolicyDeletion.RemainingVisitedCustomers(
            VisitedNames = listOf("VINOD JOSHI", "GAURAV BARTHWAL"),
            RemainingPolicyList = Remaining
        )
        assertEquals(listOf("GAURAV BARTHWAL"), Visited)
    }

    @Test
    fun `visited names match regardless of case or padding`() {
        val Visited = PolicyDeletion.RemainingVisitedCustomers(
            VisitedNames = listOf("  vinod joshi  "),
            RemainingPolicyList = listOf(PolicyOf("156264667", "Vinod Joshi"))
        )
        assertEquals(1, Visited.size)
    }

    @Test
    fun `duplicate numbers in the selection are harmless`() {
        assertEquals(
            2,
            PolicyDeletion.RemainingPolicies(
                PolicyList = PolicyList,
                NumberList = listOf("156264667", "156264667")
            ).size
        )
    }
}
