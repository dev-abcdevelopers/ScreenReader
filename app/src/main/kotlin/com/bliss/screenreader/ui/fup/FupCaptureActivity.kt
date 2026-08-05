@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.fup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.parser.FupDataParser
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityFupCaptureBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.adapter.CustomerPolicyAdapter
import org.json.JSONObject

class FupCaptureActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityFupCaptureBinding
    private val AdapterObj = CustomerPolicyAdapter()
    private val CapturedFupList = mutableListOf<FupPolicy>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityFupCaptureBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        ViewBindingObj.rvFup.layoutManager = LinearLayoutManager(this)
        ViewBindingObj.rvFup.adapter = AdapterObj

        CapturedFupList.addAll(PolicyRepository.GetFupPolicies(ContextRef = this))
        UpdateDisplayList()

        ViewBindingObj.btnFupStart.setOnClickListener {
            val ServiceInstance = ScreenReaderService.Instance
            if (ServiceInstance == null) {
                Toast.makeText(this, "Accessibility Service is disabled", Toast.LENGTH_SHORT).show()
            } else {
                ServiceInstance.StartCaptureSession()
                ViewBindingObj.tvFupStatus.text = "Recording FUP Renewal History..."
                Toast.makeText(this, "FUP Recording Started", Toast.LENGTH_SHORT).show()
            }
        }

        ViewBindingObj.btnFupStop.setOnClickListener {
            val ServiceInstance = ScreenReaderService.Instance
            if (ServiceInstance != null && ScreenReaderService.IsCapturing) {
                val JsonStr = ServiceInstance.StopCaptureSession()
                ParseFupNodes(JsonStr = JsonStr)
            } else {
                Toast.makeText(this, "No active capture session running", Toast.LENGTH_SHORT).show()
            }
        }

        ViewBindingObj.btnExportFupExcel.setOnClickListener {
            if (CapturedFupList.isEmpty()) {
                Toast.makeText(this, "No FUP records available to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ExportedFile = ExcelExporter.ExportFupPolicies(ContextRef = this, Policies = CapturedFupList)
            val FileUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", ExportedFile)
            val ShareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, FileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(ShareIntent, "Export FUP Excel via"))
        }
    }

    private fun ParseFupNodes(JsonStr: String) {
        try {
            val JsonObj = JSONObject(JsonStr)
            val NodesArray = JsonObj.optJSONArray("nodes") ?: return
            val NodeList = mutableListOf<String>()
            for (Idx in 0 until NodesArray.length()) {
                NodeList.add(NodesArray.getString(Idx))
            }

            val ParsedList = FupDataParser.ParseRenewalHistory(Nodes = NodeList)
            if (ParsedList.isNotEmpty()) {
                CapturedFupList.addAll(0, ParsedList)
                PolicyRepository.SaveFupPolicies(ContextRef = this, Policies = CapturedFupList)
                UpdateDisplayList()
                ViewBindingObj.tvFupStatus.text = "Parsed ${ParsedList.size} renewal history cards."
            } else {
                Toast.makeText(this, "No FUP policy cards detected in capture", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun UpdateDisplayList() {
        val ConvertedList = CapturedFupList.map { FupItem ->
            CustomerPolicy(
                PolicyNumber = FupItem.PolicyNumber,
                HolderName = FupItem.HolderName,
                PlanName = FupItem.PlanName,
                PremiumAmount = FupItem.PremiumAmount,
                RenewalDueDate = FupItem.DueDate,
                Status = FupItem.Status
            )
        }
        AdapterObj.UpdateData(NewPolicies = ConvertedList)
    }
}
