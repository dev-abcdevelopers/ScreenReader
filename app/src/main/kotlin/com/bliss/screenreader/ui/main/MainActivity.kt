@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityMainBinding
import com.bliss.screenreader.ui.capture.CaptureFragment
import com.bliss.screenreader.ui.exports.ExportsFragment
import com.bliss.screenreader.ui.policies.PoliciesFragment

/**
 * The shell. Three tabs, one back stack, and a single place that owns window
 * insets. Replaces the old launcher grid, which sent every task through a
 * separate activity and gave the app no home to return to.
 */
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

        // BottomNavigationView already has item 0 checked once the menu inflates,
        // so assigning selectedItemId here dispatches nothing and the container
        // stays empty. The first tab has to be committed explicitly.
        if (savedInstanceState == null) {
            ShowTab(ItemId = R.id.tabCapture)
        }
    }

    /**
     * Safety net for restore. If a saved state comes back with nothing attached,
     * or with everything hidden, the container would render empty with no way
     * out except reelecting a tab.
     */
    override fun onStart() {
        super.onStart()
        val ManagerRef = supportFragmentManager
        val NothingShowing = ManagerRef.fragments.none { !it.isHidden && it.tag?.startsWith("tab_") == true }
        if (NothingShowing) {
            ShowTab(ItemId = ViewBindingObj.bottomNav.selectedItemId)
        }
    }

    /**
     * The status bar inset goes to the fragment container so each tab can put
     * its own header under it; the navigation bar inset goes to the bar itself.
     */
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
        }

        val TargetFragment = ManagerRef.findFragmentByTag(TagVal)
        if (TargetFragment == null) {
            TransactionRef.add(R.id.navHost, BuildFragment(TagVal = TagVal), TagVal)
        } else {
            TransactionRef.show(TargetFragment)
        }
        // Synchronous so a following call sees this fragment and does not add a
        // duplicate on top of it.
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
