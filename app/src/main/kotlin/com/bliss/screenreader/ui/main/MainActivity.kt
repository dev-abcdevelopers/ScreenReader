@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityMainBinding
import com.bliss.screenreader.ui.capture.CaptureFragment
import com.bliss.screenreader.ui.exports.ExportsFragment
import com.bliss.screenreader.ui.policies.PoliciesFragment
import com.bliss.screenreader.ui.update.UpdateSheet
import com.bliss.screenreader.update.UpdateChecker


class MainActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ViewBindingObj = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        ApplyInsets()

        ViewBindingObj.bottomNav.setOnItemSelectedListener { MenuItemRef ->
            ShowTab(ItemId = MenuItemRef.itemId)
            true
        }
        ViewBindingObj.bottomNav.setOnItemReselectedListener { }


        if (savedInstanceState == null) {
            ShowTab(ItemId = R.id.tabCapture)
            CheckForUpdate()
        }
    }


    private fun CheckForUpdate() {
        UpdateChecker.Check(ContextRef = this, ManualCheck = false) { OutcomeRef ->
            if (isFinishing || isDestroyed) return@Check
            if (OutcomeRef !is UpdateChecker.Outcome.Available) return@Check
            UpdateSheet.Show(
                ManagerRef = supportFragmentManager,
                ManifestObj = OutcomeRef.ManifestObj,
                SizeBytes = OutcomeRef.SizeBytes
            )
        }
    }


    override fun onStart() {
        super.onStart()
        val ManagerRef = supportFragmentManager
        val NothingShowing =
            ManagerRef.fragments.none { !it.isHidden && it.tag?.startsWith("tab_") == true }
        if (NothingShowing) {
            ShowTab(ItemId = ViewBindingObj.bottomNav.selectedItemId)
        }
    }

    private fun ApplyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(ViewBindingObj.root) { _, WindowInsetsObj ->
            val BarInsets = WindowInsetsObj.getInsets(WindowInsetsCompat.Type.systemBars())
            ViewBindingObj.navHost.updatePadding(top = BarInsets.top)
            ViewBindingObj.bottomNav.updatePadding(bottom = BarInsets.bottom)
            WindowInsetsObj
        }
    }

    private fun ShowTab(ItemId: Int) {
        val TagVal = when (ItemId) {
            R.id.tabPolicies -> TAG_POLICIES
            R.id.tabExports -> TAG_EXPORTS
            else -> TAG_CAPTURE
        }

        val ManagerRef = supportFragmentManager
        if (ManagerRef.isStateSaved) return

        val AlreadyShowing = ManagerRef.findFragmentByTag(TagVal)?.let { !it.isHidden } == true
        if (AlreadyShowing) return

        val TransactionRef = ManagerRef.beginTransaction()
        for (ExistingFragment in ManagerRef.fragments) {
            TransactionRef.hide(ExistingFragment)
            TransactionRef.setMaxLifecycle(ExistingFragment, Lifecycle.State.STARTED)
        }

        val TargetFragment = ManagerRef.findFragmentByTag(TagVal)
        if (TargetFragment == null) {
            val NewFragment = BuildFragment(TagVal = TagVal)
            TransactionRef.add(R.id.navHost, NewFragment, TagVal)
            TransactionRef.setMaxLifecycle(NewFragment, Lifecycle.State.RESUMED)
        } else {
            TransactionRef.show(TargetFragment)
            TransactionRef.setMaxLifecycle(TargetFragment, Lifecycle.State.RESUMED)
        }

        TransactionRef.commitNow()
    }

    private fun BuildFragment(TagVal: String): Fragment = when (TagVal) {
        TAG_POLICIES -> PoliciesFragment()
        TAG_EXPORTS -> ExportsFragment()
        else -> CaptureFragment()
    }

    fun GoToCaptureTab() {
        ViewBindingObj.bottomNav.selectedItemId = R.id.tabCapture
    }

    companion object {
        private const val TAG_CAPTURE = "tab_capture"
        private const val TAG_POLICIES = "tab_policies"
        private const val TAG_EXPORTS = "tab_exports"
    }
}
