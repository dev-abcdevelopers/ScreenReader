@file:Suppress("FunctionName", "LocalVariableName", "TestFunctionName", "PrivatePropertyName")

package com.bliss.screenreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNameTest {

    private val OriginalLabel = "87 policies · Full details"

    private fun Rename(
        HistoryList: List<SessionNameEntry>,
        NameText: String,
        StampVal: Long
    ): List<SessionNameEntry> {
        return SessionNameRules.Append(
            HistoryList = HistoryList,
            NameText = NameText,
            FallbackLabel = OriginalLabel,
            StampVal = StampVal
        ) ?: HistoryList
    }

    @Test
    fun FirstRenameRecordsTheDerivedLabelAsSource() {
        val HistoryList = Rename(
            HistoryList = emptyList(),
            NameText = "Vinod Joshi's Policies",
            StampVal = 1000L
        )

        assertEquals(1, HistoryList.size)
        assertEquals(OriginalLabel, HistoryList[0].FromText)
        assertEquals("Vinod Joshi's Policies", HistoryList[0].ToText)
        assertEquals(1000L, HistoryList[0].StampAt)
    }

    @Test
    fun EachRenameChainsFromThePreviousName() {
        var HistoryList = Rename(emptyList(), "Vinod Joshi (Full)", 1000L)
        HistoryList = Rename(HistoryList, "Joshi Agency", 2000L)
        HistoryList = Rename(HistoryList, "Vinod Joshi's Policies", 3000L)

        assertEquals(3, HistoryList.size)
        assertEquals("Vinod Joshi (Full)", HistoryList[1].FromText)
        assertEquals("Joshi Agency", HistoryList[1].ToText)
        assertEquals("Joshi Agency", HistoryList[2].FromText)
        assertEquals("Vinod Joshi's Policies", SessionNameRules.CurrentName(HistoryList))
    }

    @Test
    fun RestoreAppendsAnEntryInsteadOfClearingHistory() {
        var HistoryList = Rename(emptyList(), "Joshi Agency", 1000L)
        HistoryList = Rename(HistoryList, "", 2000L)

        assertEquals(2, HistoryList.size)
        assertTrue(HistoryList[1].IsRestore)
        assertEquals("Joshi Agency", HistoryList[1].FromText)
        assertEquals("", SessionNameRules.CurrentName(HistoryList))
    }

    @Test
    fun RenamingAfterARestoreFallsBackToTheDerivedLabel() {
        var HistoryList = Rename(emptyList(), "Joshi Agency", 1000L)
        HistoryList = Rename(HistoryList, "", 2000L)
        HistoryList = Rename(HistoryList, "Second Agency", 3000L)

        assertEquals(3, HistoryList.size)
        assertEquals(OriginalLabel, HistoryList[2].FromText)
        assertEquals("Second Agency", HistoryList[2].ToText)
    }

    @Test
    fun NoEntryIsAddedWhenTheNameIsUnchanged() {
        val HistoryList = Rename(emptyList(), "Joshi Agency", 1000L)

        assertNull(
            SessionNameRules.Append(
                HistoryList = HistoryList,
                NameText = "  Joshi Agency  ",
                FallbackLabel = OriginalLabel,
                StampVal = 2000L
            )
        )
    }

    @Test
    fun RestoringAnAlreadyOriginalListAddsNothing() {
        assertNull(
            SessionNameRules.Append(
                HistoryList = emptyList(),
                NameText = "   ",
                FallbackLabel = OriginalLabel,
                StampVal = 1000L
            )
        )
    }

    @Test
    fun LongNamesAreCappedAndTrimmed() {
        val HistoryList = Rename(
            HistoryList = emptyList(),
            NameText = "  " + "A".repeat(60) + "  ",
            StampVal = 1000L
        )

        assertEquals(SessionNameRules.MAX_NAME_LENGTH, HistoryList[0].ToText.length)
    }

    @Test
    fun AMissingStampReadsAsZeroRatherThanCrashing() {
        val EntryRef = SessionNameEntry()

        assertEquals(0L, EntryRef.StampAt)
        assertEquals("", EntryRef.FromText)
        assertTrue(EntryRef.IsRestore)
    }
}
