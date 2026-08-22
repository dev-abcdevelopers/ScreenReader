@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyResumeTargetTest {

    private fun MarkOf(
        TrackVal: String,
        LastCompletedPage: Int,
        TotalPages: Int = 158,
        IsComplete: Boolean = false,
        OutstandingBefore: Int? = null,
        SavedAt: Long = 1_000L
    ): PolicyResumeMark {
        return PolicyResumeMark(
            SessionId = "session",
            Track = TrackVal,
            LastCompletedPage = LastCompletedPage,
            TotalPages = TotalPages,
            CapturedCount = LastCompletedPage * 10,
            OutstandingBefore = OutstandingBefore,
            SavedAt = SavedAt,
            IsComplete = IsComplete
        )
    }

    private fun ResolveFast(
        FastMark: PolicyResumeMark?,
        FullMark: PolicyResumeMark?,
        StoredRecordCount: Int = 1577
    ): Int {
        return PolicyResumeTarget.ResolveForTrack(
            TrackVal = PolicyResumeTrack.POLICY_FAST,
            FastMark = FastMark,
            FullMark = FullMark,
            CustomerMark = null,
            StoredRecordCount = StoredRecordCount
        )
    }

    private fun ResolveFull(
        FastMark: PolicyResumeMark?,
        FullMark: PolicyResumeMark?,
        StoredRecordCount: Int = 1577
    ): Int {
        return PolicyResumeTarget.ResolveForTrack(
            TrackVal = PolicyResumeTrack.POLICY_FULL,
            FastMark = FastMark,
            FullMark = FullMark,
            CustomerMark = null,
            StoredRecordCount = StoredRecordCount
        )
    }

    private fun ResolveCustomer(
        CustomerMark: PolicyResumeMark?,
        StoredRecordCount: Int = 1577
    ): Int {
        return PolicyResumeTarget.ResolveForTrack(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            FastMark = null,
            FullMark = null,
            CustomerMark = CustomerMark,
            StoredRecordCount = StoredRecordCount
        )
    }

    @Test
    fun `fast and full keep their own pages`() {
        val FastMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FAST, LastCompletedPage = 9)
        val FullMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FULL, LastCompletedPage = 4)
        assertEquals(9, ResolveFast(FastMark = FastMark, FullMark = FullMark))
        assertEquals(4, ResolveFull(FastMark = FastMark, FullMark = FullMark))
    }

    @Test
    fun `fast borrows the full mark when full went further`() {
        val FastMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FAST, LastCompletedPage = 4)
        val FullMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FULL, LastCompletedPage = 9)
        assertEquals(9, ResolveFast(FastMark = FastMark, FullMark = FullMark))
        assertSame(
            FullMark,
            PolicyResumeTarget.ChooseMark(
                TrackVal = PolicyResumeTrack.POLICY_FAST,
                FastMark = FastMark,
                FullMark = FullMark,
                CustomerMark = null,
                StoredRecordCount = 1577
            )
        )
    }

    @Test
    fun `full never borrows the fast mark`() {
        val FastMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FAST, LastCompletedPage = 40)
        assertEquals(0, ResolveFull(FastMark = FastMark, FullMark = null))
    }

    @Test
    fun `a completed track offers nothing`() {
        val FullMark = MarkOf(
            TrackVal = PolicyResumeTrack.POLICY_FULL,
            LastCompletedPage = 158,
            IsComplete = true
        )
        assertEquals(0, ResolveFull(FastMark = null, FullMark = FullMark))
        assertFalse(
            PolicyResumeTarget.IsOffered(MarkObj = FullMark, StoredRecordCount = 1577)
        )
    }

    @Test
    fun `a run finished by hand on the last page offers nothing`() {
        val FullMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FULL, LastCompletedPage = 158)
        assertEquals(0, ResolveFull(FastMark = null, FullMark = FullMark))
    }

    @Test
    fun `a discarded session offers nothing`() {
        val FastMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FAST, LastCompletedPage = 26)
        assertEquals(0, ResolveFast(FastMark = FastMark, FullMark = null, StoredRecordCount = 0))
    }

    @Test
    fun `the first page offers nothing`() {
        val FastMark = MarkOf(TrackVal = PolicyResumeTrack.POLICY_FAST, LastCompletedPage = 1)
        assertEquals(0, ResolveFast(FastMark = FastMark, FullMark = null))
    }

    @Test
    fun `personal details jump only when nothing earlier is outstanding`() {
        val CleanMark = MarkOf(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            LastCompletedPage = 12,
            OutstandingBefore = 0
        )
        assertEquals(12, ResolveCustomer(CustomerMark = CleanMark))

        val MissedMark = MarkOf(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            LastCompletedPage = 12,
            OutstandingBefore = 2
        )
        assertEquals(0, ResolveCustomer(CustomerMark = MissedMark))

        val LegacyMark = MarkOf(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            LastCompletedPage = 12,
            OutstandingBefore = null
        )
        assertEquals(0, ResolveCustomer(CustomerMark = LegacyMark))
    }

    @Test
    fun `skip ahead is offered only when the guard blocked the jump`() {
        val MissedMark = MarkOf(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            LastCompletedPage = 12,
            OutstandingBefore = 2
        )
        assertEquals(
            12,
            PolicyResumeTarget.CustomerSkipAheadPage(
                MarkObj = MissedMark,
                StoredRecordCount = 1577
            )
        )

        val CleanMark = MarkOf(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            LastCompletedPage = 12,
            OutstandingBefore = 0
        )
        assertEquals(
            0,
            PolicyResumeTarget.CustomerSkipAheadPage(
                MarkObj = CleanMark,
                StoredRecordCount = 1577
            )
        )

        val CompleteMark = MarkOf(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            LastCompletedPage = 12,
            IsComplete = true,
            OutstandingBefore = 2
        )
        assertEquals(
            0,
            PolicyResumeTarget.CustomerSkipAheadPage(
                MarkObj = CompleteMark,
                StoredRecordCount = 1577
            )
        )
        assertEquals(
            0,
            PolicyResumeTarget.CustomerSkipAheadPage(
                MarkObj = null,
                StoredRecordCount = 1577
            )
        )
    }

    @Test
    fun `a missing mark offers nothing`() {
        assertEquals(0, ResolveFast(FastMark = null, FullMark = null))
        assertEquals(0, ResolveCustomer(CustomerMark = null))
        assertNull(
            PolicyResumeTarget.ChooseMark(
                TrackVal = PolicyResumeTrack.POLICY_FULL,
                FastMark = null,
                FullMark = null,
                CustomerMark = null,
                StoredRecordCount = 1577
            )
        )
    }

    @Test
    fun `a single page session offers nothing`() {
        val FastMark = MarkOf(
            TrackVal = PolicyResumeTrack.POLICY_FAST,
            LastCompletedPage = 1,
            TotalPages = 1
        )
        assertEquals(0, ResolveFast(FastMark = FastMark, FullMark = null))
    }

    @Test
    fun `the target is clamped when the dashboard shrank`() {
        assertEquals(40, PolicyResumeTarget.ClampToTotal(TargetPage = 90, TotalPages = 40))
        assertEquals(26, PolicyResumeTarget.ClampToTotal(TargetPage = 26, TotalPages = 158))
        assertEquals(0, PolicyResumeTarget.ClampToTotal(TargetPage = 1, TotalPages = 158))
        assertEquals(0, PolicyResumeTarget.ClampToTotal(TargetPage = 0, TotalPages = 158))
        assertEquals(26, PolicyResumeTarget.ClampToTotal(TargetPage = 26, TotalPages = 0))
    }

    @Test
    fun `tracks map from mode and depth`() {
        assertEquals(
            PolicyResumeTrack.POLICY_FAST,
            PolicyResumeTrack.OfMode(
                ModeVal = CaptureMode.POLICY,
                CapturePolicyDetails = false
            )
        )
        assertEquals(
            PolicyResumeTrack.POLICY_FULL,
            PolicyResumeTrack.OfMode(
                ModeVal = CaptureMode.POLICY,
                CapturePolicyDetails = true
            )
        )
        assertEquals(
            PolicyResumeTrack.CUSTOMER,
            PolicyResumeTrack.OfMode(
                ModeVal = CaptureMode.CUSTOMER,
                CapturePolicyDetails = false
            )
        )
        assertTrue(
            PolicyResumeTrack.OfMode(
                ModeVal = CaptureMode.FUP,
                CapturePolicyDetails = false
            ).isEmpty()
        )
    }
}
