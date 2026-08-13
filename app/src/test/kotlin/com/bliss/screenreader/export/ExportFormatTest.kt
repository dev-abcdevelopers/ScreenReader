@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExportFormatTest {

    @Before
    fun ResetBetweenTests() {
        ExportFormat.ResetDiagnostics()
    }

    // ------------------------------------------------------------- IsoDate

    @Test
    fun IsoDate_ConvertsTheFormatsTheSourceAppRenders() {
        assertEquals("2022-01-25", ExportFormat.IsoDate("25 Jan 2022"))
        assertEquals("2026-08-08", ExportFormat.IsoDate("08 Aug 2026"))
        assertEquals("2026-09-25", ExportFormat.IsoDate("25 Sep 2026"))
        assertEquals("2091-01-25", ExportFormat.IsoDate("25 Jan 2091"))
        assertEquals("2026-07-28", ExportFormat.IsoDate("28 Jul 2026"))
    }

    @Test
    fun IsoDate_HandlesSingleDigitDaysAndFullMonthNames() {
        assertEquals("2026-03-05", ExportFormat.IsoDate("5 Mar 2026"))
        assertEquals("2026-03-05", ExportFormat.IsoDate("5 March 2026"))
        assertEquals("2026-09-25", ExportFormat.IsoDate("25-Sep-2026"))
    }

    @Test
    fun IsoDate_ReadsNumericDatesDayFirst() {
        assertEquals("2026-09-25", ExportFormat.IsoDate("25/09/2026"))
        assertEquals("2026-09-25", ExportFormat.IsoDate("25-09-2026"))
    }

    @Test
    fun IsoDate_PassesThroughValuesThatAreAlreadyNormalised() {
        assertEquals("2026-08-08", ExportFormat.IsoDate("2026-08-08"))
    }

    @Test
    fun IsoDate_ReturnsBlankForBlankInput() {
        assertEquals("", ExportFormat.IsoDate(""))
        assertEquals("", ExportFormat.IsoDate("   "))
        assertTrue(ExportFormat.UnparsedValues.isEmpty())
    }

    @Test
    fun IsoDate_RefusesToGuessAndRecordsWhatItRejected() {
        assertEquals("", ExportFormat.IsoDate("Not available"))
        assertEquals("", ExportFormat.IsoDate("25 Foo 2026"))
        assertTrue(ExportFormat.UnparsedValues.contains("Not available"))
        assertTrue(ExportFormat.UnparsedValues.contains("25 Foo 2026"))
    }

    // --------------------------------------------------------- PlainNumber

    @Test
    fun PlainNumber_StripsSymbolSeparatorsAndFrequency() {
        assertEquals(5641.0, ExportFormat.PlainNumber("₹5,641/Month"))
        assertEquals(1221.0, ExportFormat.PlainNumber("₹1,221/Month"))
        assertEquals(5535.0, ExportFormat.PlainNumber("₹5,535/Half Year"))
        assertEquals(1250000.0, ExportFormat.PlainNumber("₹12,50,000"))
        assertEquals(999.0, ExportFormat.PlainNumber("₹999/Month"))
    }

    @Test
    fun PlainNumber_HandlesPlainDigitsAndDecimals() {
        assertEquals(5641.0, ExportFormat.PlainNumber("5641"))
        assertEquals(5641.5, ExportFormat.PlainNumber("₹5,641.50"))
    }

    @Test
    fun PlainNumber_ReturnsNullRatherThanZeroWhenNothingWasCaptured() {
        assertNull(ExportFormat.PlainNumber(""))
        assertNull(ExportFormat.PlainNumber("   "))
        assertNull(ExportFormat.PlainNumber("₹"))
    }

    @Test
    fun PlainNumber_DistinguishesACapturedZeroFromAMissingValue() {
        assertEquals(0.0, ExportFormat.PlainNumber("₹0"))
        assertNull(ExportFormat.PlainNumber(""))
    }

    // ----------------------------------------------------- AmountFrequency

    @Test
    fun AmountFrequency_SplitsTheSuffixFromTheAmount() {
        assertEquals("Month", ExportFormat.AmountFrequency("₹5,641/Month"))
        assertEquals("Half Year", ExportFormat.AmountFrequency("₹5,535/Half Year"))
        assertEquals("", ExportFormat.AmountFrequency("₹12,50,000"))
        assertEquals("", ExportFormat.AmountFrequency(""))
    }

    // ------------------------------------------------------- Term and PPT

    @Test
    fun TermAndPpt_SplitTheSlashPair() {
        assertEquals(20.0, ExportFormat.TermYears("20/15"))
        assertEquals(15.0, ExportFormat.PptYears("20/15"))
        assertEquals(20.0, ExportFormat.TermYears(" 20 / 15 "))
    }

    @Test
    fun TermAndPpt_ReturnNullWhenThePairIsMissing() {
        assertNull(ExportFormat.TermYears(""))
        assertNull(ExportFormat.PptYears("20"))
        assertNull(ExportFormat.TermYears("Not available"))
    }

    // -------------------------------------------------------- Identifier

    @Test
    fun Identifier_StaysTextSoLeadingZerosSurvive() {
        assertEquals("0146345511", ExportFormat.Identifier(" 0146345511 "))
        assertEquals("9876543210", ExportFormat.Identifier("9876543210"))
    }
}
