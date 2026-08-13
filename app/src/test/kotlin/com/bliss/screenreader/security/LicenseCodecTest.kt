@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class LicenseCodecTest {
    private fun NewKeyPair(): KeyPair {
        val GeneratorRef = KeyPairGenerator.getInstance("EC")
        GeneratorRef.initialize(ECGenParameterSpec("secp256r1"))
        return GeneratorRef.generateKeyPair()
    }

    private fun PublicKeyB64(PairRef: KeyPair): String =
        Base64.getEncoder().encodeToString(PairRef.public.encoded)

    private fun BuildBody(
        DeviceIdBytes: ByteArray,
        ExpiryDays: Int,
        SeedBytes: ByteArray,
        LabelText: String
    ): ByteArray {
        val LabelBytes = LabelText.toByteArray(Charsets.UTF_8)
        val BodyBytes = ByteArray(35 + LabelBytes.size)
        BodyBytes[0] = 1
        System.arraycopy(DeviceIdBytes, 0, BodyBytes, 1, 8)
        BodyBytes[9] = (ExpiryDays ushr 24).toByte()
        BodyBytes[10] = (ExpiryDays ushr 16).toByte()
        BodyBytes[11] = (ExpiryDays ushr 8).toByte()
        BodyBytes[12] = ExpiryDays.toByte()
        System.arraycopy(SeedBytes, 0, BodyBytes, 13, 20)
        BodyBytes[33] = 0
        BodyBytes[34] = LabelBytes.size.toByte()
        System.arraycopy(LabelBytes, 0, BodyBytes, 35, LabelBytes.size)
        return BodyBytes
    }

    private fun Mint(PairRef: KeyPair, BodyBytes: ByteArray): String {
        val SignatureBytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(PairRef.private)
            update(BodyBytes)
            sign()
        }
        val EncoderRef = Base64.getUrlEncoder().withoutPadding()
        return "SRL1." + EncoderRef.encodeToString(BodyBytes) + "." + EncoderRef.encodeToString(SignatureBytes)
    }

    private val DeviceIdBytes = byteArrayOf(0xA3.toByte(), 0xF1.toByte(), 0x9C.toByte(), 0x22, 0x7B, 0x04, 0xD5.toByte(), 0xE8.toByte())
    private val SeedBytes = ByteArray(20) { (it + 1).toByte() }

    @Test
    fun `round trips a minted licence`() {
        val PairRef = NewKeyPair()
        val BodyBytes = BuildBody(
            DeviceIdBytes = DeviceIdBytes,
            ExpiryDays = 20_500,
            SeedBytes = SeedBytes,
            LabelText = "Ravi"
        )
        val ResultRef = LicenseCodec.Parse(
            BlobText = Mint(PairRef = PairRef, BodyBytes = BodyBytes),
            PublicKeyB64 = PublicKeyB64(PairRef = PairRef)
        )

        assertTrue(ResultRef is LicenseCodec.ParseResult.Valid)
        val LicenseObj = (ResultRef as LicenseCodec.ParseResult.Valid).LicenseObj
        assertArrayEquals(DeviceIdBytes, LicenseObj.DeviceIdBytes)
        assertArrayEquals(SeedBytes, LicenseObj.SeedBytes)
        assertEquals(20_500, LicenseObj.ExpiryDays)
        assertEquals("Ravi", LicenseObj.LabelText)
    }

    @Test
    fun `survives the whitespace a chat app adds`() {
        val PairRef = NewKeyPair()
        val BlobText = Mint(
            PairRef = PairRef,
            BodyBytes = BuildBody(DeviceIdBytes, 20_500, SeedBytes, "Ravi")
        )
        val MangledText = BlobText.chunked(20).joinToString("\n  ") + "\n"

        assertTrue(
            LicenseCodec.Parse(
                BlobText = MangledText,
                PublicKeyB64 = PublicKeyB64(PairRef = PairRef)
            ) is LicenseCodec.ParseResult.Valid
        )
    }

    @Test
    fun `rejects a licence signed by a different key`() {
        val IssuerPair = NewKeyPair()
        val ImpostorPair = NewKeyPair()
        val BlobText = Mint(
            PairRef = ImpostorPair,
            BodyBytes = BuildBody(DeviceIdBytes, 20_500, SeedBytes, "Ravi")
        )

        assertTrue(
            LicenseCodec.Parse(
                BlobText = BlobText,
                PublicKeyB64 = PublicKeyB64(PairRef = IssuerPair)
            ) is LicenseCodec.ParseResult.BadSignature
        )
    }

    @Test
    fun `rejects a licence whose expiry was edited after signing`() {
        val PairRef = NewKeyPair()
        val BodyBytes = BuildBody(DeviceIdBytes, 20_500, SeedBytes, "Ravi")
        val BlobText = Mint(PairRef = PairRef, BodyBytes = BodyBytes)

        BodyBytes[10] = (BodyBytes[10] + 1).toByte()
        val ForgedBlob = "SRL1." +
            Base64.getUrlEncoder().withoutPadding().encodeToString(BodyBytes) +
            "." + BlobText.split(".")[2]

        assertTrue(
            LicenseCodec.Parse(
                BlobText = ForgedBlob,
                PublicKeyB64 = PublicKeyB64(PairRef = PairRef)
            ) is LicenseCodec.ParseResult.BadSignature
        )
    }

    @Test
    fun `rejects junk without throwing`() {
        val PairRef = NewKeyPair()
        val KeyText = PublicKeyB64(PairRef = PairRef)
        val BadInputs = listOf("", "hello", "SRL1.abc", "SRL2.aaa.bbb", "SRL1...")
        for (BadInput in BadInputs) {
            val ResultRef = LicenseCodec.Parse(BlobText = BadInput, PublicKeyB64 = KeyText)
            assertTrue(
                "expected rejection for $BadInput",
                ResultRef !is LicenseCodec.ParseResult.Valid
            )
        }
    }

    @Test
    fun `refuses to activate when no signing key is compiled in`() {
        val PairRef = NewKeyPair()
        val BlobText = Mint(
            PairRef = PairRef,
            BodyBytes = BuildBody(DeviceIdBytes, 20_500, SeedBytes, "Ravi")
        )
        assertTrue(
            LicenseCodec.Parse(BlobText = BlobText, PublicKeyB64 = "")
                is LicenseCodec.ParseResult.NoSigningKey
        )
    }

    @Test
    fun `expiry is inclusive of the final day`() {
        val LicenseObj = LicenseCodec.ActivationLicense(
            DeviceIdBytes = DeviceIdBytes,
            ExpiryDays = 20_500,
            SeedBytes = SeedBytes,
            FlagsVal = 0,
            LabelText = ""
        )
        val LastDayMillis = 20_500L * 86_400_000L + 1_000L
        val NextDayMillis = 20_501L * 86_400_000L + 1_000L
        assertTrue(!LicenseObj.IsExpired(NowMillis = LastDayMillis))
        assertTrue(LicenseObj.IsExpired(NowMillis = NextDayMillis))
    }
}
