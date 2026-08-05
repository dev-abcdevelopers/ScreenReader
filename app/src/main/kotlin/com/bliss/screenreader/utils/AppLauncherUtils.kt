@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object AppLauncherUtils {

    const val LIC_SUPER_APP_PACKAGE = "com.lic.sales.superapp"
    const val PS_AGENT_APP_PACKAGE = "com.perfectandroidappforagents"

    fun LaunchTargetApp(ContextRef: Context, PackageNameVal: String = LIC_SUPER_APP_PACKAGE): Boolean {
        return try {
            val PackageManagerObj = ContextRef.packageManager
            var LaunchIntentObj = PackageManagerObj.getLaunchIntentForPackage(PackageNameVal)
            if (LaunchIntentObj != null) {
                LaunchIntentObj.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ContextRef.startActivity(LaunchIntentObj)
                true
            } else {
                val FallbackIntentObj = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(PackageNameVal)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ContextRef.startActivity(FallbackIntentObj)
                true
            }
        } catch (_: Exception) {
            Toast.makeText(
                ContextRef,
                "Target App ($PackageNameVal) is not installed on this device.",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    fun IsBatteryOptimized(ContextRef: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val PowerMgr = ContextRef.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !PowerMgr.isIgnoringBatteryOptimizations(ContextRef.packageName)
        }
        return false
    }

    fun RequestBatteryOptimizationExemption(ContextRef: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val PowerMgr = ContextRef.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!PowerMgr.isIgnoringBatteryOptimizations(ContextRef.packageName)) {
                try {
                    val RequestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${ContextRef.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ContextRef.startActivity(RequestIntent)
                } catch (e: Exception) {
                    Toast.makeText(ContextRef, "Unable to open battery optimization settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
