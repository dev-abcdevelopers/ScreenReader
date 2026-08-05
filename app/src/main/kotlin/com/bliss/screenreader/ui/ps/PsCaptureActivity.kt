@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.ui.ps

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.PsPolicy
import com.bliss.screenreader.data.parser.PsDataParser
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityPsCaptureBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.adapter.CustomerPolicyAdapter
import org.json.JSONObject

class PsCaptureActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityPsCaptureBinding
    private val AdapterObj = CustomerPolicyAdapter()
    private val CapturedPsList = mutableListOf<PsPolicy>()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityPsCaptureBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        ViewBindingObj.rvPs.layoutManager = LinearLayoutManager(this)
        ViewBindingObj.rvPs.adapter = AdapterObj

        CapturedPsList.addAll(PolicyRepository.GetPsPolicies(ContextRef = this))
        UpdateDisplayList()

        ViewBindingObj.btnPsAutoScroll.setOnClickListener {
            val ServiceInstance = ScreenReaderService.Instance
            if (ServiceInstance == null) {
                Toast.makeText(this, "Accessibility Service is disabled", Toast.LENGTH_SHORT).show()
            } else {
                ServiceInstance.StartCaptureSession()
                ServiceInstance.PerformAutoScrollGesture()
                ViewBindingObj.tvPsStatus.text = "Executing PS Auto-scroll & Recording..."
                Toast.makeText(this, "PS Auto-scroll Gesture Injected", Toast.LENGTH_SHORT).show()
            }
        }

        ViewBindingObj.btnPsStop.setOnClickListener {
            val ServiceInstance = ScreenReaderService.Instance
            if (ServiceInstance != null && ScreenReaderService.IsCapturing) {
                val JsonStr = ServiceInstance.StopCaptureSession()
                ParsePsNodes(JsonStr = JsonStr)
            } else {
                Toast.makeText(this, "No active capture session running", Toast.LENGTH_SHORT).show()
            }
        }

        ViewBindingObj.btnExportPsExcel.setOnClickListener {
            if (CapturedPsList.isEmpty()) {
                Toast.makeText(this, "No PS records available to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ExportedFile = ExcelExporter.ExportPsPolicies(ContextRef = this, Policies = CapturedPsList)
            val FileUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", ExportedFile)
            val ShareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, FileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(ShareIntent, "Export PS Excel via"))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun ParsePsNodes(JsonStr: String) {
        try {
            val JsonObj = JSONObject(JsonStr)
            val NodesArray = JsonObj.optJSONArray("nodes") ?: return
            val NodeList = mutableListOf<String>()
            for (Idx in 0 until NodesArray.length()) {
                NodeList.add(NodesArray.getString(Idx))
            }

            val ParsedList = PsDataParser.ParsePsPolicies(Nodes = NodeList)
            if (ParsedList.isNotEmpty()) {
                CapturedPsList.addAll(0, ParsedList)
                PolicyRepository.SavePsPolicies(ContextRef = this, Policies = CapturedPsList)
                UpdateDisplayList()
                ViewBindingObj.tvPsStatus.text = "Parsed ${ParsedList.size} servicing records."
            } else {
                Toast.makeText(this, "No PS policy records detected in capture", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun UpdateDisplayList() {
        val ConvertedList = CapturedPsList.map { PsItem ->
            CustomerPolicy(
                PolicyNumber = PsItem.PolicyNumber,
                HolderName = PsItem.HolderName,
                PremiumAmount = PsItem.PremiumAmount,
                RenewalDueDate = PsItem.Fup,
                Status = PsItem.Status,
                DateOfCommencement = PsItem.Doc
            )
        }
        AdapterObj.UpdateData(NewPolicies = ConvertedList)
    }
}
