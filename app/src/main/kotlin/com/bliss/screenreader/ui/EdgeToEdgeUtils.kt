@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui

import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetDialog

fun ComponentActivity.SetupEdgeToEdge(RootView: View, AppBarView: View? = null, BottomView: View? = null) {
    enableEdgeToEdge()
    ViewCompat.setOnApplyWindowInsetsListener(RootView) { _, WindowInsetsObj ->
        val SystemBarsInsets = WindowInsetsObj.getInsets(WindowInsetsCompat.Type.systemBars())
        
        if (AppBarView != null) {
            AppBarView.updatePadding(top = SystemBarsInsets.top)
        } else {
            RootView.updatePadding(top = SystemBarsInsets.top)
        }

        if (BottomView != null) {
            BottomView.updatePadding(bottom = SystemBarsInsets.bottom)
        } else {
            RootView.updatePadding(bottom = SystemBarsInsets.bottom)
        }

        WindowInsetsObj
    }
}

fun BottomSheetDialog.PadForKeyboard(RootView: View) {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    val BasePadding = RootView.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(RootView) { ViewRef, WindowInsetsObj ->
        val ImeBottom = WindowInsetsObj.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val BarBottom = WindowInsetsObj.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val SheetBottom = (ViewRef.parent as? View)?.paddingBottom ?: 0
        val ExtraBottom = (maxOf(ImeBottom, BarBottom) - SheetBottom).coerceAtLeast(0)
        ViewRef.updatePadding(bottom = BasePadding + ExtraBottom)
        WindowInsetsObj
    }
    ViewCompat.requestApplyInsets(RootView)
}
