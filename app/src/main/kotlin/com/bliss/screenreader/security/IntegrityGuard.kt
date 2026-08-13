@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "DEPRECATION")

package com.bliss.screenreader.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

object IntegrityGuard {
    private const val EXPECTED_CERT_SHA256 = ""

    fun IsTampered(ContextRef: Context): Boolean {
        if (EXPECTED_CERT_SHA256.isBlank()) return false
        val ActualList = CertificateHashes(ContextRef = ContextRef)
        if (ActualList.isEmpty()) return false
        return ActualList.none { it.equals(EXPECTED_CERT_SHA256, ignoreCase = true) }
    }

    fun CertificateHashes(ContextRef: Context): List<String> = try {
        val ManagerRef = ContextRef.packageManager
        val SignatureBytesList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val InfoRef = ManagerRef.getPackageInfo(
                ContextRef.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val SigningInfo = InfoRef.signingInfo
            when {
                SigningInfo == null -> emptyList()
                SigningInfo.hasMultipleSigners() -> SigningInfo.apkContentsSigners.map { it.toByteArray() }
                else -> SigningInfo.signingCertificateHistory.map { it.toByteArray() }
            }
        } else {
            ManagerRef.getPackageInfo(ContextRef.packageName, PackageManager.GET_SIGNATURES)
                .signatures.orEmpty().map { it.toByteArray() }
        }

        SignatureBytesList.map { CertBytes ->
            MessageDigest.getInstance("SHA-256").digest(CertBytes)
                .joinToString("") { String.format(Locale.US, "%02X", it) }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
