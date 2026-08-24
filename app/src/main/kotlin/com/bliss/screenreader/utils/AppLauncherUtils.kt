@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.bliss.screenreader.ui.toast.AppToast

object AppLauncherUtils {

    const val LIC_SUPER_APP_PACKAGE = "com.lic.sales.superapp"

    val PS_AGENT_APP_PACKAGES = listOf(
        "com.bliss.blissbma.pro",
        "com.bliss.combo"
    )

    fun IsInstalled(ContextRef: Context, PackageNameVal: String): Boolean = try {
        ContextRef.packageManager.getLaunchIntentForPackage(PackageNameVal) != null
    } catch (_: Exception) {
        false
    }

    fun ResolveAgentPackage(ContextRef: Context): String =
        PS_AGENT_APP_PACKAGES.firstOrNull { PackageNameVal ->
            IsInstalled(ContextRef = ContextRef, PackageNameVal = PackageNameVal)
        } ?: PS_AGENT_APP_PACKAGES.first()

    fun LaunchTargetApp(
        ContextRef: Context,
        PackageNameVal: String = LIC_SUPER_APP_PACKAGE,
        FreshStartVal: Boolean = false
    ): Boolean {
        return try {
            val PackageManagerObj = ContextRef.packageManager
            val LaunchIntentObj = PackageManagerObj.getLaunchIntentForPackage(PackageNameVal)
            if (LaunchIntentObj != null) {
                LaunchIntentObj.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (FreshStartVal) {
                    LaunchIntentObj.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                ContextRef.startActivity(LaunchIntentObj)
                true
            } else {
                val FallbackIntentObj = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(PackageNameVal)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (FreshStartVal) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                ContextRef.startActivity(FallbackIntentObj)
                true
            }
        } catch (_: Exception) {
            AppToast.Error(
                ContextRef = ContextRef,
                MessageText = "Target App ($PackageNameVal) is not installed on this device."
            )
            false
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    fun IsBatteryOptimized(ContextRef: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val PowerMgr = ContextRef.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !PowerMgr.isIgnoringBatteryOptimizations(ContextRef.packageName)
        }
        return false
    }

    @SuppressLint("BatteryLife", "ObsoleteSdkInt")
    fun RequestBatteryOptimizationExemption(ContextRef: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val PowerMgr = ContextRef.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!PowerMgr.isIgnoringBatteryOptimizations(ContextRef.packageName)) {
                try {
                    val RequestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${ContextRef.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ContextRef.startActivity(RequestIntent)
                } catch (_: Exception) {
                    AppToast.Warning(
                        ContextRef = ContextRef,
                        MessageText = "Unable to open battery optimization settings"
                    )
                }
            }
        }
    }
}
