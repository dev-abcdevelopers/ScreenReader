@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CustomerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyPreviewGuardTest {

    private val LoginScreenNodes = listOf(
        "Enter mPIN to login",
        "Forgot mPIN?",
        "1",
        "2",
        "3",
        "4",
        "Login",
        "LIC SuperApp"
    )

    @Test
    fun `the mPIN login screen is not a policy`() {
        val RecordList = CaptureParsers.Preview(
            ModeVal = CaptureMode.POLICY,
            Nodes = LoginScreenNodes
        )
        assertTrue(RecordList.isEmpty())
    }

    @Test
    fun `a policy number on its own is not enough to make a record`() {
        val RecordList = CaptureParsers.Preview(
            ModeVal = CaptureMode.POLICY,
            Nodes = listOf("279608790")
        )
        assertTrue(RecordList.isEmpty())
    }

    @Test
    fun `a single policy screen still previews as one record`() {
        val RecordList = CaptureParsers.Preview(
            ModeVal = CaptureMode.POLICY,
            Nodes = listOf("279608790", "RAKESH KUMAR", "Sum Assured", "5,00,000")
        )
        assertEquals(1, RecordList.size)
        assertEquals("279608790", RecordList.first().PolicyNumber)
        assertEquals("RAKESH KUMAR", RecordList.first().PrimaryLine)
    }

    @Test
    fun `a numberless record is dropped from an explicit policy list`() {
        val RecordList = CaptureParsers.PreviewPolicies(
            Policies = listOf(
                CustomerPolicy(
                    HolderName = "Enter mPIN to login",
                    PolicyNumber = ""
                ),
                CustomerPolicy(
                    HolderName = "RAKESH KUMAR",
                    PolicyNumber = "279608790"
                )
            )
        )
        assertEquals(1, RecordList.size)
        assertEquals("279608790", RecordList.first().PolicyNumber)
    }
}
