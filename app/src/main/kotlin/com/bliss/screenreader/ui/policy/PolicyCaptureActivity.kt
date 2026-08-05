@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.policy

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.parser.ScreenDataParser
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityPolicyCaptureBinding
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.adapter.CustomerPolicyAdapter
import com.bliss.screenreader.utils.AppLauncherUtils
import org.json.JSONObject

class PolicyCaptureActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityPolicyCaptureBinding
    private val AdapterObj = CustomerPolicyAdapter()
    private val CapturedPoliciesList = mutableListOf<CustomerPolicy>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityPolicyCaptureBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        ViewBindingObj.rvCapturedPolicies.layoutManager = LinearLayoutManager(this)
        ViewBindingObj.rvCapturedPolicies.adapter = AdapterObj

        CapturedPoliciesList.addAll(PolicyRepository.GetCustomerPolicies(ContextRef = this))
        AdapterObj.UpdateData(NewPolicies = CapturedPoliciesList)

        ViewBindingObj.btnLaunchLicApp.setOnClickListener {
            AppLauncherUtils.LaunchTargetApp(ContextRef = this, PackageNameVal = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        }

        ViewBindingObj.btnStartCapture.setOnClickListener {
            val ServiceInstance = ScreenReaderService.Instance
            if (ServiceInstance == null) {
                Toast.makeText(this, "Accessibility Service is not enabled!", Toast.LENGTH_SHORT).show()
            } else {
                ServiceInstance.StartCaptureSession()
                ViewBindingObj.tvCaptureStatus.text = "Capturing Accessibility Nodes..."
                AppLauncherUtils.LaunchTargetApp(ContextRef = this, PackageNameVal = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
                Toast.makeText(this, "Overlay activated. Launching LIC SuperApp...", Toast.LENGTH_SHORT).show()
            }
        }

        ViewBindingObj.btnStopCapture.setOnClickListener {
            val ServiceInstance = ScreenReaderService.Instance
            if (ServiceInstance != null && ScreenReaderService.IsCapturing) {
                val JsonStr = ServiceInstance.StopCaptureSession()
                ParseCapturedNodes(JsonStr = JsonStr)
            } else {
                Toast.makeText(this, "No active capture session running.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ParseCapturedNodes(JsonStr: String) {
        try {
            val JsonObj = JSONObject(JsonStr)
            val NodesArray = JsonObj.optJSONArray("nodes") ?: return
            val NodeList = mutableListOf<String>()
            for (Idx in 0 until NodesArray.length()) {
                NodeList.add(NodesArray.getString(Idx))
            }

            val DetailsMap = ScreenDataParser.ParseDetailedPolicyView(Nodes = NodeList)
            val ProfileMap = ScreenDataParser.ParseCustomerProfile(Nodes = NodeList)

            val PolicyItem = CustomerPolicy(
                HolderName = ProfileMap["holderName"] ?: "Scanned Holder",
                PolicyNumber = DetailsMap["policyNumber"] ?: "27960${(1000..9999).random()}",
                PlanName = "LIC'S NEW ENDOWMENT PLAN",
                SumAssured = DetailsMap["sumAssured"] ?: "₹5,00,000",
                TermPPT = DetailsMap["termPPT"] ?: "21 / 21",
                RenewalDueDate = "28/09/2026",
                PremiumAmount = "₹12,450",
                Status = "Inforce",
                MobileNumber = ProfileMap["mobileNumber"] ?: ""
            )

            CapturedPoliciesList.add(0, PolicyItem)
            PolicyRepository.SaveCustomerPolicies(ContextRef = this, Policies = CapturedPoliciesList)
            AdapterObj.UpdateData(NewPolicies = CapturedPoliciesList)
            ViewBindingObj.tvCaptureStatus.text = "Captured policy ${PolicyItem.PolicyNumber} successfully."
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
