@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.security

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object LicenseCodec {
    private const val PREFIX = "SRL1"
    private const val VERSION = 1
    private const val DEVICE_ID_OFFSET = 1
    private const val DEVICE_ID_LENGTH = 8
    private const val EXPIRY_OFFSET = 9
    private const val SEED_OFFSET = 13
    private const val SEED_LENGTH = 20
    private const val FLAGS_OFFSET = 33
    private const val LABEL_LENGTH_OFFSET = 34
    private const val MIN_BODY_LENGTH = 35
    const val SIGNING_PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETmecW9b6z0qyni+z3C4Ni5i8eJtPNwD85xBcDBeYlZgbx/tQ6i3x5QjKheGQRvh/TAhg/a81h1zqYcAIrL/XZA=="

    data class ActivationLicense(
        val DeviceIdBytes: ByteArray,
        val ExpiryDays: Int,
        val SeedBytes: ByteArray,
        val FlagsVal: Int,
        val LabelText: String
    ) {
        val NeverExpires: Boolean get() = ExpiryDays == 0

        fun IsExpired(NowMillis: Long): Boolean {
            if (NeverExpires) return false
            return TodayDays(NowMillis = NowMillis) > ExpiryDays
        }

        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    sealed class ParseResult {
        data class Valid(val LicenseObj: ActivationLicense) : ParseResult()

        object Malformed : ParseResult()

        object BadSignature : ParseResult()

        object NoSigningKey : ParseResult()
    }

    fun TodayDays(NowMillis: Long): Int = (NowMillis / 86_400_000L).toInt()

    fun Parse(
        BlobText: String,
        PublicKeyB64: String = SIGNING_PUBLIC_KEY_B64
    ): ParseResult {
        if (PublicKeyB64.isBlank()) return ParseResult.NoSigningKey

        val CleanText = BlobText.filterNot { it.isWhitespace() }
        val PartsList = CleanText.split('.')
        if (PartsList.size != 3 || PartsList[0] != PREFIX) return ParseResult.Malformed

        val BodyBytes = DecodeOrNull(EncodedText = PartsList[1]) ?: return ParseResult.Malformed
        val SignatureBytes = DecodeOrNull(EncodedText = PartsList[2]) ?: return ParseResult.Malformed
        if (BodyBytes.size < MIN_BODY_LENGTH) return ParseResult.Malformed
        if (BodyBytes[0].toInt() != VERSION) return ParseResult.Malformed

        if (!VerifySignature(
                BodyBytes = BodyBytes,
                SignatureBytes = SignatureBytes,
                PublicKeyB64 = PublicKeyB64
            )
        ) {
            return ParseResult.BadSignature
        }

        val LabelLength = BodyBytes[LABEL_LENGTH_OFFSET].toInt() and 0xFF
        if (BodyBytes.size < MIN_BODY_LENGTH + LabelLength) return ParseResult.Malformed

        val ExpiryDays =
            ((BodyBytes[EXPIRY_OFFSET].toInt() and 0xFF) shl 24) or
                ((BodyBytes[EXPIRY_OFFSET + 1].toInt() and 0xFF) shl 16) or
                ((BodyBytes[EXPIRY_OFFSET + 2].toInt() and 0xFF) shl 8) or
                (BodyBytes[EXPIRY_OFFSET + 3].toInt() and 0xFF)

        return ParseResult.Valid(
            LicenseObj = ActivationLicense(
                DeviceIdBytes = BodyBytes.copyOfRange(
                    DEVICE_ID_OFFSET,
                    DEVICE_ID_OFFSET + DEVICE_ID_LENGTH
                ),
                ExpiryDays = ExpiryDays,
                SeedBytes = BodyBytes.copyOfRange(SEED_OFFSET, SEED_OFFSET + SEED_LENGTH),
                FlagsVal = BodyBytes[FLAGS_OFFSET].toInt() and 0xFF,
                LabelText = String(
                    BodyBytes,
                    MIN_BODY_LENGTH,
                    LabelLength,
                    Charsets.UTF_8
                )
            )
        )
    }

    private fun DecodeOrNull(EncodedText: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(EncodedText)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun VerifySignature(
        BodyBytes: ByteArray,
        SignatureBytes: ByteArray,
        PublicKeyB64: String
    ): Boolean = try {
        val KeyBytes = Base64.getDecoder().decode(PublicKeyB64)
        val PublicKeyRef = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(KeyBytes))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(PublicKeyRef)
            update(BodyBytes)
            verify(SignatureBytes)
        }
    } catch (_: Exception) {
        false
    }
}
