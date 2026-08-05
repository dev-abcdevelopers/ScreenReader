@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityMainBinding
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.dashboard.PolicyDashboardActivity
import com.bliss.screenreader.ui.pdf.PdfListActivity
import com.bliss.screenreader.ui.policy.PolicyCaptureActivity
import com.bliss.screenreader.utils.AppLauncherUtils

class MainActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        SetupListeners()
    }

    override fun onResume() {
        super.onResume()
        UpdateStatusIndicators()
    }

    private fun UpdateStatusIndicators() {
        val IsServiceActive = ScreenReaderService.IsServiceRunning()
        if (IsServiceActive) {
            ViewBindingObj.tvAccessibilityStatus.text = "Active & Ready for Automation"
            ViewBindingObj.tvAccessibilityStatus.setTextColor(getColor(R.color.status_green_text))
            ViewBindingObj.btnEnableAccessibility.text = "Settings"
        } else {
            ViewBindingObj.tvAccessibilityStatus.text = "Disabled - Action Required"
            ViewBindingObj.tvAccessibilityStatus.setTextColor(getColor(R.color.status_red_text))
            ViewBindingObj.btnEnableAccessibility.text = "Enable"
        }

        val IsBatteryOptimizedVal = AppLauncherUtils.IsBatteryOptimized(ContextRef = this)
        if (IsBatteryOptimizedVal) {
            ViewBindingObj.tvBatteryStatus.text = "Optimized (System may kill background service)"
            ViewBindingObj.tvBatteryStatus.setTextColor(getColor(R.color.status_amber_text))
            ViewBindingObj.btnBatteryExemption.text = "Exempt"
        } else {
            ViewBindingObj.tvBatteryStatus.text = "Unrestricted (Background persistence active)"
            ViewBindingObj.tvBatteryStatus.setTextColor(getColor(R.color.status_green_text))
            ViewBindingObj.btnBatteryExemption.text = "OK"
        }
    }

    private fun SetupListeners() {
        ViewBindingObj.btnEnableAccessibility.setOnClickListener {
            val SettingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(SettingsIntent)
        }

        ViewBindingObj.btnBatteryExemption.setOnClickListener {
            AppLauncherUtils.RequestBatteryOptimizationExemption(ContextRef = this)
        }

        ViewBindingObj.btnLaunchLicApp.setOnClickListener {
            AppLauncherUtils.LaunchTargetApp(ContextRef = this, PackageNameVal = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        }

        ViewBindingObj.cardSmartReader.setOnClickListener {
            startActivity(Intent(this, PolicyCaptureActivity::class.java))
        }

        ViewBindingObj.cardDashboard.setOnClickListener {
            startActivity(Intent(this, PolicyDashboardActivity::class.java))
        }

        ViewBindingObj.cardDocuments.setOnClickListener {
            startActivity(Intent(this, PdfListActivity::class.java))
        }
    }
}
