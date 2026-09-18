@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui

import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bliss.screenreader.service.CaptureDiagnostics
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

@Suppress("DEPRECATION")
fun BottomSheetDialog.PadForKeyboard(RootView: View) {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    val BasePadding = RootView.paddingBottom
    var LastExtra = -1

    fun ApplyKeyboardPadding() {
        val InsetsObj = ViewCompat.getRootWindowInsets(RootView) ?: return
        val ImeBottom = InsetsObj.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val BarBottom = InsetsObj.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val SheetBottom = (RootView.parent as? View)?.paddingBottom ?: 0
        val ExtraBottom = (maxOf(ImeBottom, BarBottom) - SheetBottom).coerceAtLeast(0)
        if (ExtraBottom == LastExtra) return
        LastExtra = ExtraBottom
        CaptureDiagnostics.Log(
            ContextObj = RootView.context,
            EventName = "SHEET_IME_PAD",
            MessageText = "ime=$ImeBottom bars=$BarBottom sheetPad=$SheetBottom " +
                    "base=$BasePadding applied=${BasePadding + ExtraBottom}"
        )
        RootView.updatePadding(bottom = BasePadding + ExtraBottom)
    }

    val LayoutListener = ViewTreeObserver.OnGlobalLayoutListener { ApplyKeyboardPadding() }
    RootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(ViewRef: View) {
            ViewRef.viewTreeObserver.addOnGlobalLayoutListener(LayoutListener)
        }

        override fun onViewDetachedFromWindow(ViewRef: View) {
            ViewRef.viewTreeObserver.removeOnGlobalLayoutListener(LayoutListener)
        }
    })
    if (RootView.isAttachedToWindow) {
        RootView.viewTreeObserver.addOnGlobalLayoutListener(LayoutListener)
    }
    ViewCompat.setOnApplyWindowInsetsListener(RootView) { _, WindowInsetsObj ->
        ApplyKeyboardPadding()
        WindowInsetsObj
    }
    ViewCompat.requestApplyInsets(RootView)
}
