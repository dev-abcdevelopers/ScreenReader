@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.raw

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.databinding.ActivityRawCaptureBinding
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.ui.SetupEdgeToEdge

class RawCaptureActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityRawCaptureBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityRawCaptureBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        val NodesList = ScreenReaderService.CapturedNodes
        if (NodesList.isEmpty()) {
            ViewBindingObj.tvRawOutput.text = "No captured accessibility nodes available.\nStart a capture session first."
        } else {
            val StrBuilder = StringBuilder()
            StrBuilder.append("Total Nodes Captured: ${NodesList.size}\n\n")
            NodesList.forEachIndexed { Idx, NodeText ->
                StrBuilder.append("[$Idx] $NodeText\n")
            }
            ViewBindingObj.tvRawOutput.text = StrBuilder.toString()
        }
    }
}
