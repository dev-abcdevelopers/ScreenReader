@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyVault {
    private const val KEYSTORE_NAME = "AndroidKeyStore"
    private const val KEY_ALIAS = "screenreader_vault_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    const val CIPHER_PREFIX = "v1$"

    class VaultUnavailable(MessageText: String, CauseRef: Throwable?) :
        RuntimeException(MessageText, CauseRef)

    private fun LoadKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_NAME).apply { load(null) }

    private fun ObtainKey(): SecretKey {
        val StoreRef = LoadKeyStore()
        val ExistingEntry = StoreRef.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        ExistingEntry?.let { return it.secretKey }
        return GenerateKey()
    }

    private fun GenerateKey(): SecretKey {
        val GeneratorRef = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_NAME
        )

        fun BuildSpec(UseStrongBox: Boolean): KeyGenParameterSpec {
            val BuilderRef = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)

                .setRandomizedEncryptionRequired(true)
            if (UseStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                BuilderRef.setIsStrongBoxBacked(true)
            }
            return BuilderRef.build()
        }

        return try {
            GeneratorRef.init(BuildSpec(UseStrongBox = true))
            GeneratorRef.generateKey()
        } catch (_: Exception) {
            val FallbackGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_NAME
            )
            FallbackGenerator.init(BuildSpec(UseStrongBox = false))
            FallbackGenerator.generateKey()
        }
    }

    fun Encrypt(PlainText: String): String {
        try {
            val CipherRef = Cipher.getInstance(TRANSFORMATION)
            CipherRef.init(Cipher.ENCRYPT_MODE, ObtainKey())
            val CipherBytes = CipherRef.doFinal(PlainText.toByteArray(Charsets.UTF_8))
            val PackedBytes = ByteArray(CipherRef.iv.size + CipherBytes.size)
            System.arraycopy(CipherRef.iv, 0, PackedBytes, 0, CipherRef.iv.size)
            System.arraycopy(CipherBytes, 0, PackedBytes, CipherRef.iv.size, CipherBytes.size)
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(PackedBytes)
        } catch (ErrorRef: Exception) {
            throw VaultUnavailable(MessageText = "Could not encrypt", CauseRef = ErrorRef)
        }
    }

    fun Decrypt(StoredText: String): String? {
        if (!StoredText.startsWith(CIPHER_PREFIX)) return null
        return try {
            val PackedBytes = Base64.getDecoder()
                .decode(StoredText.substring(CIPHER_PREFIX.length))
            if (PackedBytes.size <= IV_LENGTH) return null
            val IvBytes = PackedBytes.copyOfRange(0, IV_LENGTH)
            val CipherBytes = PackedBytes.copyOfRange(IV_LENGTH, PackedBytes.size)
            val CipherRef = Cipher.getInstance(TRANSFORMATION)
            CipherRef.init(
                Cipher.DECRYPT_MODE,
                ObtainKey(),
                GCMParameterSpec(TAG_BITS, IvBytes)
            )
            String(CipherRef.doFinal(CipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun IsEncrypted(StoredText: String?): Boolean =
        StoredText != null && StoredText.startsWith(CIPHER_PREFIX)

}
