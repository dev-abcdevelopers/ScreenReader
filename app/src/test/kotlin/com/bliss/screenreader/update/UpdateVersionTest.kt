@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    private val ServerJson = """
        {
          "AppName": "ScreenReaderApp",
          "Version": {
            "VersionCode": 1,
            "VersionName": "18.06.2026|1"
          },
          "DownloadUrl": "https://mybliss.in/update/beta/ScreenReader.apk",
          "ForceUserToUpdateAppWhenNewerVersionAvailable": true,
          "ChangeLog": {
            "Note": "1. Fixed The Bug Reported By Users.\\n2. Added Some New Nodes."
          }
        }
    """.trimIndent()

    @Test
    fun `a higher version code always wins`() {
        assertTrue(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "18.08.2026|1",
                RemoteCode = 2,
                RemoteName = "18.06.2026|1"
            )
        )
    }

    @Test
    fun `a lower version code never triggers an update`() {
        assertFalse(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 3,
                LocalName = "01.01.2026|1",
                RemoteCode = 2,
                RemoteName = "31.12.2026|9"
            )
        )
    }

    @Test
    fun `same code falls through to the date in the version name`() {
        assertTrue(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "18.06.2026|1",
                RemoteCode = 1,
                RemoteName = "18.08.2026|1"
            )
        )
    }

    @Test
    fun `the published sample is older than the installed build`() {
        assertFalse(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "18.08.2026|1",
                RemoteCode = 1,
                RemoteName = "18.06.2026|1"
            )
        )
    }

    @Test
    fun `same date with a higher build counts as newer`() {
        assertTrue(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "18.08.2026|1",
                RemoteCode = 1,
                RemoteName = "18.08.2026|2"
            )
        )
    }

    @Test
    fun `an identical version is not an update`() {
        assertFalse(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "18.08.2026|1",
                RemoteCode = 1,
                RemoteName = "18.08.2026|1"
            )
        )
    }

    @Test
    fun `a year change is respected over the day number`() {
        assertTrue(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "31.12.2026|9",
                RemoteCode = 1,
                RemoteName = "01.01.2027|1"
            )
        )
    }

    @Test
    fun `an unreadable version name never forces an update`() {
        assertFalse(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "18.08.2026|1",
                RemoteCode = 1,
                RemoteName = "beta-two"
            )
        )
        assertFalse(
            UpdateVersion.IsRemoteNewer(
                LocalCode = 1,
                LocalName = "",
                RemoteCode = 1,
                RemoteName = "19.08.2026|1"
            )
        )
    }

    @Test
    fun `a missing build number is treated as zero`() {
        val StampObj = UpdateVersion.ParseVersionName(VersionName = "18.08.2026")
        assertNotNull(StampObj)
        assertEquals(0, StampObj!!.Build)
        assertEquals(2026, StampObj.Year)
        assertEquals(8, StampObj.Month)
        assertEquals(18, StampObj.Day)
    }

    @Test
    fun `dashes and slashes parse like dots`() {
        assertEquals(
            UpdateVersion.ParseVersionName(VersionName = "18.08.2026|2"),
            UpdateVersion.ParseVersionName(VersionName = "18-08-2026|2")
        )
        assertEquals(
            UpdateVersion.ParseVersionName(VersionName = "18.08.2026|2"),
            UpdateVersion.ParseVersionName(VersionName = "18/08/2026|2")
        )
    }

    @Test
    fun `impossible dates are rejected`() {
        assertNull(UpdateVersion.ParseVersionName(VersionName = "18.13.2026|1"))
        assertNull(UpdateVersion.ParseVersionName(VersionName = "0.08.2026|1"))
        assertNull(UpdateVersion.ParseVersionName(VersionName = "18.08|1"))
        assertNull(UpdateVersion.ParseVersionName(VersionName = ""))
    }

    @Test
    fun `the server file parses into a manifest`() {
        val ManifestObj = UpdateManifest.Parse(JsonText = ServerJson)
        assertNotNull(ManifestObj)
        assertEquals("ScreenReaderApp", ManifestObj!!.AppName)
        assertEquals(1, ManifestObj.VersionCode)
        assertEquals("18.06.2026|1", ManifestObj.VersionName)
        assertEquals("https://mybliss.in/update/beta/ScreenReader.apk", ManifestObj.DownloadUrl)
        assertTrue(ManifestObj.ForceUpdate)
    }

    @Test
    fun `the change log note becomes numbered-free lines`() {
        val ManifestObj = UpdateManifest.Parse(JsonText = ServerJson)
        assertNotNull(ManifestObj)
        assertEquals(2, ManifestObj!!.ChangeLog.size)
        assertEquals("Fixed The Bug Reported By Users.", ManifestObj.ChangeLog[0])
        assertEquals("Added Some New Nodes.", ManifestObj.ChangeLog[1])
    }

    @Test
    fun `real newlines split the same way as escaped ones`() {
        assertEquals(
            listOf("One", "Two"),
            UpdateManifest.SplitNotes(RawText = "1. One\n2. Two")
        )
        assertEquals(
            listOf("One", "Two"),
            UpdateManifest.SplitNotes(RawText = "1) One\r\n2) Two")
        )
        assertEquals(emptyList<String>(), UpdateManifest.SplitNotes(RawText = "   "))
    }

    @Test
    fun `a change log given as a plain string still works`() {
        val ManifestObj = UpdateManifest.Parse(
            JsonText = """
                {
                  "Version": { "VersionCode": 4, "VersionName": "01.09.2026|1" },
                  "DownloadUrl": "https://example.test/a.apk",
                  "ChangeLog": "Only one line"
                }
            """.trimIndent()
        )
        assertNotNull(ManifestObj)
        assertEquals(listOf("Only one line"), ManifestObj!!.ChangeLog)
        assertFalse(ManifestObj.ForceUpdate)
    }

    @Test
    fun `a change log given as an array still works`() {
        val ManifestObj = UpdateManifest.Parse(
            JsonText = """
                {
                  "Version": { "VersionCode": 4, "VersionName": "01.09.2026|1" },
                  "DownloadUrl": "https://example.test/a.apk",
                  "ChangeLog": ["First", "Second"]
                }
            """.trimIndent()
        )
        assertNotNull(ManifestObj)
        assertEquals(listOf("First", "Second"), ManifestObj!!.ChangeLog)
    }

    @Test
    fun `rubbish and half-written files parse to null`() {
        assertNull(UpdateManifest.Parse(JsonText = "not json at all"))
        assertNull(UpdateManifest.Parse(JsonText = ""))
        assertNull(UpdateManifest.Parse(JsonText = "[1,2,3]"))
        assertNull(
            UpdateManifest.Parse(
                JsonText = """{"Version":{"VersionCode":2,"VersionName":"01.09.2026|1"}}"""
            )
        )
        assertNull(
            UpdateManifest.Parse(
                JsonText = """{"DownloadUrl":"https://example.test/a.apk"}"""
            )
        )
    }

    @Test
    fun `the force flag survives being sent as a string`() {
        val ManifestObj = UpdateManifest.Parse(
            JsonText = """
                {
                  "Version": { "VersionCode": 4, "VersionName": "01.09.2026|1" },
                  "DownloadUrl": "https://example.test/a.apk",
                  "ForceUserToUpdateAppWhenNewerVersionAvailable": "true"
                }
            """.trimIndent()
        )
        assertNotNull(ManifestObj)
        assertTrue(ManifestObj!!.ForceUpdate)
    }

    @Test
    fun `describe renders a padded date and build`() {
        assertEquals(
            "01.09.2026 · 3",
            UpdateVersion.Describe(VersionName = "1.9.2026|3", VersionCode = 7)
        )
        assertEquals(
            "7",
            UpdateVersion.Describe(VersionName = "", VersionCode = 7)
        )
    }
}
