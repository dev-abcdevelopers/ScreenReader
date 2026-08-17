@file:Suppress("FunctionName", "LocalVariableName")

package com.bliss.screenreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetOcrParserTest {

    private val Address = CustomerProfileParser.ContactKind.ADDRESS
    private val Email = CustomerProfileParser.ContactKind.EMAIL
    private val Mobile = CustomerProfileParser.ContactKind.MOBILE

    @Test
    fun `a wrapped address is rejoined and keeps its policies`() {
        val Lines = listOf(
            "Address(es)", "Mark as default address for address",
            "H. NO. K - 3 / 51 WEST GHONDA STREET NO.",
            "- 12 A DELHI NORTH EAST DELHI , 110053",
            "Policy(ies) Related: 156264667",
            "K-3/51, GALI NO. 12A WEST GHONDA DELHI, 110053",
            "Policy(ies) Related: 156255273, 156255275, 156255276, 156255278"
        )

        val Values = SheetOcrParser.ParseSheetText(Lines = Lines, KindVal = Address)
        assertEquals(2, Values.size)
        assertEquals(
            "H. NO. K - 3 / 51 WEST GHONDA STREET NO. - 12 A DELHI NORTH EAST DELHI , 110053",
            Values[0].Value
        )
        assertEquals(listOf("156264667"), Values[0].RelatedPolicies)
        assertEquals(4, Values[1].RelatedPolicies.size)
        assertTrue(Values[0].IsDefault)
    }

    @Test
    fun `policy numbers wrapping onto their own line still attach`() {
        val Lines = listOf(
            "Email ID(s)", "Mark as default email for emails",
            "rahul.rhct@gmail.com",
            "Policy(ies) Related: 128365360, 129835618, 156255273, 156255275,",
            "156255276, 156255278, 156264667"
        )

        val Values = SheetOcrParser.ParseSheetText(Lines = Lines, KindVal = Email)
        assertEquals(1, Values.size)
        assertEquals("rahul.rhct@gmail.com", Values[0].Value)
        assertEquals(7, Values[0].RelatedPolicies.size)
    }

    @Test
    fun `two mobile numbers are two values`() {
        val Lines = listOf(
            "Mobile Number(s)", "Mark as default number for calls",
            "8368659292",
            "Policy(ies) Related: 156255273, 156255275, 156255276, 156255278",
            "8802772259",
            "Policy(ies) Related: 128365360, 129835618, 156264667"
        )

        val Values = SheetOcrParser.ParseSheetText(Lines = Lines, KindVal = Mobile)
        assertEquals(2, Values.size)
        assertEquals("8368659292", Values[0].Value)
        assertEquals("8802772259", Values[1].Value)
        assertEquals(3, Values[1].RelatedPolicies.size)
    }

    @Test
    fun `text with no sheet title yields nothing rather than guessing`() {
        val Values = SheetOcrParser.ParseSheetText(
            Lines = listOf("Detailed Customer View", "Vinod Joshi", "35 Years"),
            KindVal = Email
        )
        assertTrue(Values.isEmpty())
    }

    @Test
    fun `the close button is not read as a value`() {
        val Lines = listOf("Email ID(s)", "X", "Mark as default email for emails", "a@b.com")
        val Values = SheetOcrParser.ParseSheetText(Lines = Lines, KindVal = Email)
        assertEquals(1, Values.size)
        assertEquals("a@b.com", Values[0].Value)
    }
}
