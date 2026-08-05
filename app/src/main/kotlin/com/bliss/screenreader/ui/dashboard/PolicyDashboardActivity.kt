@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityPolicyDashboardBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.export.PdfExporter
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.adapter.CustomerPolicyAdapter

class PolicyDashboardActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityPolicyDashboardBinding
    private val AdapterObj = CustomerPolicyAdapter()
    private val AllPoliciesList = mutableListOf<CustomerPolicy>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityPolicyDashboardBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        ViewBindingObj.rvPolicies.layoutManager = LinearLayoutManager(this)
        ViewBindingObj.rvPolicies.adapter = AdapterObj

        LoadPolicies()
        SetupSearchAndFilters()
        SetupExportButtons()
    }

    private fun LoadPolicies() {
        val SavedPolicies = PolicyRepository.GetCustomerPolicies(ContextRef = this)
        if (SavedPolicies.isNotEmpty()) {
            AllPoliciesList.addAll(SavedPolicies)
        } else {
            AllPoliciesList.addAll(
                listOf(
                    CustomerPolicy(
                        HolderName = "RAKESH KUMAR",
                        PolicyNumber = "279608790",
                        PlanName = "LIC'S NEW ENDOWMENT PLAN",
                        PlanCode = "714",
                        PremiumAmount = "₹12,450",
                        SumAssured = "₹5,00,000",
                        RenewalDueDate = "28/09/2026",
                        Status = "Inforce",
                        MobileNumber = "9876543210"
                    ),
                    CustomerPolicy(
                        HolderName = "ANITA SHARMA",
                        PolicyNumber = "182394012",
                        PlanName = "JEEVAN ANAND",
                        PlanCode = "149",
                        PremiumAmount = "₹8,900",
                        SumAssured = "₹3,50,000",
                        RenewalDueDate = "15/04/2025",
                        Status = "Lapsed",
                        MobileNumber = "9812345678"
                    ),
                    CustomerPolicy(
                        HolderName = "VIKRAM SINGH",
                        PolicyNumber = "345910283",
                        PlanName = "JEEVAN UMAG",
                        PlanCode = "945",
                        PremiumAmount = "₹24,000",
                        SumAssured = "₹10,00,000",
                        RenewalDueDate = "10/11/2026",
                        Status = "Inforce",
                        MobileNumber = "9765432109"
                    )
                )
            )
            PolicyRepository.SaveCustomerPolicies(ContextRef = this, Policies = AllPoliciesList)
        }
        AdapterObj.UpdateData(NewPolicies = AllPoliciesList)
    }

    private fun SetupSearchAndFilters() {
        ViewBindingObj.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ApplyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        ViewBindingObj.chipGroupStatus.setOnCheckedStateChangeListener { _, _ ->
            ApplyFilter()
        }
    }

    private fun ApplyFilter() {
        val QueryStr = ViewBindingObj.etSearch.text.toString().trim().lowercase()
        val SelectedChipId = ViewBindingObj.chipGroupStatus.checkedChipId

        val FilteredList = AllPoliciesList.filter { PolicyItem ->
            val MatchesQuery = PolicyItem.PolicyNumber.lowercase().contains(QueryStr) ||
                    PolicyItem.HolderName.lowercase().contains(QueryStr) ||
                    PolicyItem.PlanName.lowercase().contains(QueryStr)

            val MatchesStatus = when (SelectedChipId) {
                ViewBindingObj.chipInforce.id -> PolicyItem.NormalizedStatus.equals("Inforce", ignoreCase = true)
                ViewBindingObj.chipLapsed.id -> PolicyItem.NormalizedStatus.equals("Lapsed", ignoreCase = true)
                else -> true
            }

            MatchesQuery && MatchesStatus
        }

        AdapterObj.UpdateData(NewPolicies = FilteredList)
    }

    private fun SetupExportButtons() {
        ViewBindingObj.btnExportPdf.setOnClickListener {
            val PdfFile = PdfExporter.GeneratePolicyPdf(ContextRef = this, Policies = AllPoliciesList)
            Toast.makeText(this, "PDF Exported: ${PdfFile.name}", Toast.LENGTH_SHORT).show()
            ShareFile(FileObj = PdfFile, MimeTypeStr = "application/pdf")
        }

        ViewBindingObj.btnExportExcel.setOnClickListener {
            val ExcelFile = ExcelExporter.ExportCustomerPolicies(ContextRef = this, Policies = AllPoliciesList)
            Toast.makeText(this, "Excel Exported: ${ExcelFile.name}", Toast.LENGTH_SHORT).show()
            ShareFile(FileObj = ExcelFile, MimeTypeStr = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }
    }

    private fun ShareFile(FileObj: java.io.File, MimeTypeStr: String) {
        val FileUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", FileObj)
        val ShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypeStr
            putExtra(Intent.EXTRA_STREAM, FileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(ShareIntent, "Share Document via"))
    }
}
