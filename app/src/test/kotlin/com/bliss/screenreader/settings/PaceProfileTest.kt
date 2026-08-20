@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PaceProfileTest {

    @Test
    fun `normal leaves every delay exactly as the service wrote it`() {
        assertEquals(1800L, PaceProfile.NORMAL.Scale(BaseMs = 1800L))
        assertEquals(0L, PaceProfile.NORMAL.Scale(BaseMs = 0L))
        assertEquals(6, PaceProfile.NORMAL.Scale(BaseCount = 6))
    }

    @Test
    fun `patient stretches a page load and fast shortens it`() {
        assertEquals(2880L, PaceProfile.PATIENT.Scale(BaseMs = 1800L))
        assertEquals(1350L, PaceProfile.FAST.Scale(BaseMs = 1800L))
    }

    @Test
    fun `a scaled delay never collapses to zero`() {
        assertEquals(1L, PaceProfile.FAST.Scale(BaseMs = 1L))
        assertEquals(1, PaceProfile.FAST.Scale(BaseCount = 1))
    }

    @Test
    fun `a zero or negative base is left alone`() {
        assertEquals(0L, PaceProfile.FAST.Scale(BaseMs = 0L))
        assertEquals(0L, PaceProfile.PATIENT.Scale(BaseMs = 0L))
        assertEquals(0, PaceProfile.PATIENT.Scale(BaseCount = 0))
    }

    @Test
    fun `an unknown or missing stored name falls back to normal`() {
        assertSame(PaceProfile.NORMAL, PaceProfile.FromName(NameVal = null))
        assertSame(PaceProfile.NORMAL, PaceProfile.FromName(NameVal = ""))
        assertSame(PaceProfile.NORMAL, PaceProfile.FromName(NameVal = "sluggish"))
    }

    @Test
    fun `stored names round trip whatever case they come back in`() {
        for (ProfileVal in PaceProfile.entries) {
            assertSame(ProfileVal, PaceProfile.FromName(NameVal = ProfileVal.StoredName))
            assertSame(ProfileVal, PaceProfile.FromName(NameVal = ProfileVal.StoredName.uppercase()))
            assertSame(ProfileVal, PaceProfile.FromName(NameVal = " ${ProfileVal.StoredName} "))
        }
    }
}
