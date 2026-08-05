@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

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
