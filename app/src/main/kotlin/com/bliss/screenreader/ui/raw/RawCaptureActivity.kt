@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.raw

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityRawCaptureBinding
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.ui.SetupEdgeToEdge

class RawCaptureActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityRawCaptureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityRawCaptureBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        val PendingSession = CaptureSessionState.PendingSession
        val NodesList: List<String> =
            PendingSession?.RawNodes ?: ScreenReaderService.CapturedNodes.toList()
        val SourceLabel = getString(
            if (PendingSession != null) R.string.raw_header_pending else R.string.raw_header_live
        )

        if (NodesList.isEmpty()) {
            ViewBindingObj.tvRawOutput.setText(R.string.raw_empty)
            return
        }

        val StrBuilder = StringBuilder()
        StrBuilder.append(getString(R.string.raw_header_format, NodesList.size, SourceLabel))
        StrBuilder.append("\n\n")
        NodesList.forEachIndexed { Idx, NodeText ->
            StrBuilder.append("[").append(Idx).append("] ").append(NodeText).append("\n")
        }
        ViewBindingObj.tvRawOutput.text = StrBuilder.toString()
    }
}
