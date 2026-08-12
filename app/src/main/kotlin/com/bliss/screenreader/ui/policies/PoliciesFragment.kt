@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.policies

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.FragmentPoliciesBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.export.PdfExporter
import com.bliss.screenreader.ui.adapter.CaptureSessionAdapter
import com.bliss.screenreader.ui.adapter.PolicyRowAdapter
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.ui.detail.PolicyDetailActivity
import com.bliss.screenreader.ui.main.MainActivity
import java.io.File
import java.util.Locale

class PoliciesFragment : Fragment() {

    private var ViewBindingObj: FragmentPoliciesBinding? = null
    private val AdapterObj = PolicyRowAdapter { PolicyItem -> OpenDetail(PolicyItem = PolicyItem) }
    private val SessionAdapterObj = CaptureSessionAdapter { SessionRef ->
        OpenSession(SessionRef = SessionRef)
    }

    private var AllPolicies: List<CustomerPolicy> = emptyList()
    private var SessionList: List<PolicyRepository.CaptureSessionReference> = emptyList()
    private var SelectedSessionId: String = ""
    private var SearchQuery: String = ""
    private var StatusFilter: String = FILTER_ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = FragmentPoliciesBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val BindingObj = ViewBindingObj ?: return

        BindingObj.rvPolicies.layoutManager = LinearLayoutManager(requireContext())
        BindingObj.rvPolicies.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        BindingObj.btnSessionsBack.setOnClickListener { ShowSessions() }

        BindingObj.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                SearchQuery = s?.toString().orEmpty()
                RenderList()
            }
        })

        BindingObj.chipGroupStatus.setOnCheckedStateChangeListener { _, CheckedIds ->
            StatusFilter = when (CheckedIds.firstOrNull()) {
                R.id.chipInforce -> FILTER_INFORCE
                R.id.chipLapsed -> FILTER_LAPSED
                else -> FILTER_ALL
            }
            RenderList()
        }

        BindingObj.btnExportPdf.setOnClickListener { ExportPdf() }
        BindingObj.btnExportExcel.setOnClickListener { ExportExcel() }

        BindingObj.emptyState.btnEmptyAction.setOnClickListener {
            (activity as? MainActivity)?.GoToCaptureTab()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    ShowSessions()
                }
            }.also { CallbackRef ->
                SessionBackCallback = CallbackRef
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (SelectedSessionId.isEmpty()) {
            LoadSessions()
        } else {
            AllPolicies = PolicyRepository.GetCustomerPolicies(
                ContextRef = requireContext(),
                SessionId = SelectedSessionId
            )
        }
        RenderList()
    }

    // -------------------------------------------------------------- listing

    private fun VisiblePolicies(): List<CustomerPolicy> {
        val QueryLower = SearchQuery.trim().lowercase(Locale.ROOT)
        return AllPolicies.filter { PolicyItem ->
            val MatchesStatus = when (StatusFilter) {
                FILTER_INFORCE -> PolicyItem.NormalizedStatus.equals("Inforce", ignoreCase = true)
                FILTER_LAPSED -> PolicyItem.NormalizedStatus.equals("Lapsed", ignoreCase = true)
                else -> true
            }
            val MatchesQuery = QueryLower.isEmpty() ||
                    PolicyItem.PolicyNumber.lowercase(Locale.ROOT).contains(QueryLower) ||
                    PolicyItem.HolderName.lowercase(Locale.ROOT).contains(QueryLower)
            MatchesStatus && MatchesQuery
        }
    }

    private fun RenderList() {
        if (SelectedSessionId.isEmpty()) {
            RenderSessions()
        } else {
            RenderPolicies()
        }
    }

    private fun RenderSessions() {
        val BindingObj = ViewBindingObj ?: return
        BindingObj.rvPolicies.adapter = SessionAdapterObj
        SessionAdapterObj.UpdateData(NewSessions = SessionList)
        BindingObj.tvPoliciesHeading.setText(R.string.sessions_heading)
        BindingObj.tvPolicyCount.text = getString(R.string.sessions_count_format, SessionList.size)
        BindingObj.btnSessionsBack.visibility = View.GONE
        BindingObj.policyTools.visibility = View.GONE
        BindingObj.exportBar.visibility = View.GONE
        SessionBackCallback?.isEnabled = false

        val HasSessions = SessionList.isNotEmpty()
        BindingObj.emptyState.emptyStateRoot.visibility = if (HasSessions) View.GONE else View.VISIBLE
        if (HasSessions) return

        BindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_folder_open)
        BindingObj.emptyState.tvEmptyTitle.setText(R.string.sessions_empty_title)
        BindingObj.emptyState.tvEmptyBody.setText(R.string.sessions_empty_body)
        BindingObj.emptyState.btnEmptyAction.setText(R.string.policies_empty_action)
        BindingObj.emptyState.btnEmptyAction.visibility = View.VISIBLE
    }

    private fun RenderPolicies() {
        val BindingObj = ViewBindingObj ?: return
        val VisibleList = VisiblePolicies()
        BindingObj.rvPolicies.adapter = AdapterObj
        AdapterObj.UpdateData(NewPolicies = VisibleList)
        BindingObj.tvPoliciesHeading.setText(R.string.sessions_policies_heading)
        BindingObj.btnSessionsBack.visibility = View.VISIBLE
        BindingObj.policyTools.visibility = View.VISIBLE
        SessionBackCallback?.isEnabled = true

        BindingObj.tvPolicyCount.text = getString(
            R.string.policies_count_format, VisibleList.size, AllPolicies.size
        )

        val HasAny = AllPolicies.isNotEmpty()
        val HasVisible = VisibleList.isNotEmpty()

        BindingObj.emptyState.emptyStateRoot.visibility = if (HasVisible) View.GONE else View.VISIBLE
        BindingObj.exportBar.visibility = if (HasVisible) View.VISIBLE else View.GONE

        if (HasVisible) return

        // Two different empty states: nothing captured, versus nothing matching.
        if (HasAny) {
            BindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_search)
            BindingObj.emptyState.tvEmptyTitle.setText(R.string.policies_no_match_title)
            BindingObj.emptyState.tvEmptyBody.setText(R.string.policies_no_match_body)
            BindingObj.emptyState.btnEmptyAction.visibility = View.GONE
        } else {
            BindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_inbox_empty)
            BindingObj.emptyState.tvEmptyTitle.setText(R.string.policies_empty_title)
            BindingObj.emptyState.tvEmptyBody.setText(R.string.policies_empty_body)
            BindingObj.emptyState.btnEmptyAction.setText(R.string.policies_empty_action)
            BindingObj.emptyState.btnEmptyAction.visibility = View.VISIBLE
        }
    }

    private fun LoadSessions() {
        SessionList = PolicyRepository.GetSessionHistory(
            ContextRef = requireContext(),
            ModeVal = com.bliss.screenreader.data.model.CaptureMode.POLICY
        )
    }

    private fun OpenSession(SessionRef: PolicyRepository.CaptureSessionReference) {
        SelectedSessionId = SessionRef.SessionId
        SearchQuery = ""
        StatusFilter = FILTER_ALL
        ViewBindingObj?.etSearch?.setText("")
        ViewBindingObj?.chipAll?.isChecked = true
        AllPolicies = PolicyRepository.GetCustomerPolicies(
            ContextRef = requireContext(),
            SessionId = SelectedSessionId
        )
        RenderList()
    }

    private fun ShowSessions() {
        SelectedSessionId = ""
        AllPolicies = emptyList()
        LoadSessions()
        RenderList()
    }

    private fun OpenDetail(PolicyItem: CustomerPolicy) {
        startActivity(
            Intent(requireContext(), PolicyDetailActivity::class.java).apply {
                putExtra(PolicyDetailActivity.EXTRA_POLICY_NUMBER, PolicyItem.PolicyNumber)
                putExtra(PolicyDetailActivity.EXTRA_SESSION_ID, SelectedSessionId)
            }
        )
    }

    // -------------------------------------------------------------- exports

    private fun ExportPdf() {
        val VisibleList = VisiblePolicies()
        if (VisibleList.isEmpty()) return
        val ExportedFile = PdfExporter.GeneratePolicyPdf(ContextRef = requireContext(), Policies = VisibleList)
        ShareFile(FileRef = ExportedFile, MimeType = "application/pdf")
    }

    private fun ExportExcel() {
        val VisibleList = VisiblePolicies()
        if (VisibleList.isEmpty()) return
        val ExportedFile = ExcelExporter.ExportCustomerPolicies(ContextRef = requireContext(), Policies = VisibleList)
        ShareFile(
            FileRef = ExportedFile,
            MimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    private fun ShareFile(FileRef: File, MimeType: String) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        val FileUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", FileRef
        )
        val ShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MimeType
            putExtra(Intent.EXTRA_STREAM, FileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(ShareIntent, getString(R.string.exports_share)))
        CaptureFlow.ShowMessage(ActivityRef = ActivityRef, MessageVal = FileRef.name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ViewBindingObj = null
        SessionBackCallback = null
    }

    companion object {
        private const val FILTER_ALL = "all"
        private const val FILTER_INFORCE = "inforce"
        private const val FILTER_LAPSED = "lapsed"
    }

    private var SessionBackCallback: OnBackPressedCallback? = null
}
