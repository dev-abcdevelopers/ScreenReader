@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUploadTest {

    @Test
    fun `string to sign matches the server contract`() {
        assertEquals(
            "POST\n/upload.php\n1786782176\nabc123\nbliss001.json",
            SessionUploadClient.BuildStringToSign(
                MethodText = "POST",
                PathText = SessionUploadClient.DEFAULT_SIGN_PATH,
                TimestampText = "1786782176",
                NonceText = "abc123",
                FileKey = "bliss001.json"
            )
        )
    }

    @Test
    fun `sign path is taken as given, or derived from the url when blank`() {
        assertEquals(
            "/upload.php",
            SessionUploadClient.ResolveSignPath(
                UploadUrl = "https://bmaservices.in/agdata/upload.php",
                SignPath = "/upload.php"
            )
        )
        assertEquals(
            "/agdata/upload.php",
            SessionUploadClient.ResolveSignPath(
                UploadUrl = "https://bmaservices.in/agdata/upload.php",
                SignPath = ""
            )
        )
    }

    @Test
    fun `agency code is lowercased and stripped of separators`() {
        assertEquals("bliss001", SessionPayloadBuilder.NormalizeAgencyCode(AgencyCode = " BLISS 001 "))
        assertEquals("ag-12_b", SessionPayloadBuilder.NormalizeAgencyCode(AgencyCode = "AG-12_B"))
        assertEquals("unknown", SessionPayloadBuilder.NormalizeAgencyCode(AgencyCode = "   "))
    }

    @Test
    fun `object key is the agency code with a json suffix`() {
        assertEquals("bliss001.json", SessionPayloadBuilder.ObjectKeyFor(AgencyCode = "BLISS001"))
    }

    @Test
    fun `only server errors and transport failures are retried`() {
        assertTrue(SessionUploadClient.IsRetryable(HttpCode = 0))
        assertTrue(SessionUploadClient.IsRetryable(HttpCode = 503))
        assertFalse(SessionUploadClient.IsRetryable(HttpCode = 401))
        assertFalse(SessionUploadClient.IsRetryable(HttpCode = 413))
    }

    @Test
    fun `backoff doubles on every attempt`() {
        assertEquals(1000L, SessionUploadClient.BackoffMillis(AttemptIndex = 0))
        assertEquals(2000L, SessionUploadClient.BackoffMillis(AttemptIndex = 1))
        assertEquals(4000L, SessionUploadClient.BackoffMillis(AttemptIndex = 2))
    }
}
