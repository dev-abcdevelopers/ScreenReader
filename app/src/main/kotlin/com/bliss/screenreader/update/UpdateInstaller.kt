@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bliss.screenreader.BuildConfig
import java.io.File

object UpdateInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val PROVIDER_SUFFIX = ".fileprovider"

    fun CanInstallPackages(ContextRef: Context): Boolean {
        return try {
            ContextRef.packageManager.canRequestPackageInstalls()
        } catch (_: Exception) {
            false
        }
    }

    fun BuildUnknownSourcesIntent(ContextRef: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${ContextRef.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun BuildInstallIntent(ContextRef: Context, FileRef: File): Intent? {
        if (!FileRef.exists() || FileRef.length() <= 0L) return null

        val ContentUri = try {
            FileProvider.getUriForFile(
                ContextRef,
                BuildConfig.APPLICATION_ID + PROVIDER_SUFFIX,
                FileRef
            )
        } catch (_: Exception) {
            return null
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(ContentUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun Install(ContextRef: Context, FileRef: File): Boolean {
        val IntentRef = BuildInstallIntent(ContextRef = ContextRef, FileRef = FileRef) ?: return false
        return try {
            ContextRef.startActivity(IntentRef)
            true
        } catch (_: Exception) {
            false
        }
    }
}
