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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.FragmentPoliciesBinding
import com.bliss.screenreader.databinding.SheetPolicyCaptureModeBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.export.PdfExporter
import com.bliss.screenreader.ui.adapter.CaptureSessionAdapter
import com.bliss.screenreader.ui.adapter.PolicyRowAdapter
import com.bliss.screenreader.ui.adapter.RenewalRowAdapter
import com.bliss.screenreader.ui.adapter.SessionSwipeCallback
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.ui.detail.PolicyDetailActivity
import com.bliss.screenreader.ui.main.MainActivity
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Locale

class PoliciesFragment : Fragment() {

    private var ViewBindingObj: FragmentPoliciesBinding? = null
    private val AdapterObj = PolicyRowAdapter { PolicyItem -> OpenDetail(PolicyItem = PolicyItem) }
    private val SessionAdapterObj = CaptureSessionAdapter(
        OnRowClick = { SessionRef -> OpenSession(SessionRef = SessionRef) },
        OnResumeClick = { SessionRef -> ResumeSession(SessionRef = SessionRef) },
        OnDeleteClick = { SessionRef -> ConfirmDeleteSession(SessionRef = SessionRef) }
    )
    private val SessionSwipeHelper = ItemTouchHelper(SessionSwipeCallback(SessionAdapterObj))
    private val RenewalAdapterObj = RenewalRowAdapter()

    private var AllPolicies: List<CustomerPolicy> = emptyList()
    private var AllRenewals: List<FupPolicy> = emptyList()
    private var SessionList: List<PolicyRepository.CaptureSessionReference> = emptyList()
    private var SelectedSessionId: String = ""
    private var SelectedSessionMode: CaptureMode = CaptureMode.POLICY
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
        SessionSwipeHelper.attachToRecyclerView(BindingObj.rvPolicies)
        BindingObj.btnSessionsBack.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ShowSessions()
        }

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

        BindingObj.emptyState.btnEmptyAction.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
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
            LoadSessionRecords()
        }
        RenderList()
    }

    private fun LoadSessionRecords() {
        if (SelectedSessionMode == CaptureMode.FUP) {
            AllRenewals = PolicyRepository.GetFupPolicies(
                ContextRef = requireContext(),
                SessionId = SelectedSessionId
            )
            AllPolicies = emptyList()
        } else {
            AllPolicies = PolicyRepository.GetCustomerPolicies(
                ContextRef = requireContext(),
                SessionId = SelectedSessionId
            )
            AllRenewals = emptyList()
        }
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

    private fun VisibleRenewals(): List<FupPolicy> {
        val QueryLower = SearchQuery.trim().lowercase(Locale.ROOT)
        if (QueryLower.isEmpty()) return AllRenewals
        return AllRenewals.filter { RenewalItem ->
            RenewalItem.PolicyNumber.lowercase(Locale.ROOT).contains(QueryLower) ||
                    RenewalItem.HolderName.lowercase(Locale.ROOT).contains(QueryLower)
        }
    }

    private fun RenderList() {
        when {
            SelectedSessionId.isEmpty() -> RenderSessions()
            SelectedSessionMode == CaptureMode.FUP -> RenderRenewals()
            else -> RenderPolicies()
        }
    }

    /**
     * Renewal history has no Inforce/Lapsed split and no detail screen, so the
     * status chips and the PDF export are hidden rather than left inert.
     */
    private fun RenderRenewals() {
        val BindingObj = ViewBindingObj ?: return
        val VisibleList = VisibleRenewals()
        BindingObj.rvPolicies.adapter = RenewalAdapterObj
        RenewalAdapterObj.UpdateData(NewRenewals = VisibleList)
        BindingObj.tvPoliciesHeading.setText(R.string.sessions_renewals_heading)
        BindingObj.btnSessionsBack.visibility = View.VISIBLE
        BindingObj.policyTools.visibility = View.VISIBLE
        BindingObj.chipGroupStatus.visibility = View.GONE
        BindingObj.tilSearch.hint = getString(R.string.renewals_search_hint)
        SessionBackCallback?.isEnabled = true

        BindingObj.tvPolicyCount.text = getString(
            R.string.renewals_count_format, VisibleList.size, AllRenewals.size
        )

        val HasVisible = VisibleList.isNotEmpty()
        BindingObj.emptyState.emptyStateRoot.visibility = if (HasVisible) View.GONE else View.VISIBLE
        BindingObj.exportBar.visibility = if (HasVisible) View.VISIBLE else View.GONE
        BindingObj.btnExportPdf.visibility = View.GONE
        if (HasVisible) return

        if (AllRenewals.isNotEmpty()) {
            BindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_search)
            BindingObj.emptyState.tvEmptyTitle.setText(R.string.policies_no_match_title)
            BindingObj.emptyState.tvEmptyBody.setText(R.string.policies_no_match_body)
            BindingObj.emptyState.btnEmptyAction.visibility = View.GONE
        } else {
            BindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_inbox_empty)
            BindingObj.emptyState.tvEmptyTitle.setText(R.string.renewals_empty_title)
            BindingObj.emptyState.tvEmptyBody.setText(R.string.renewals_empty_body)
            BindingObj.emptyState.btnEmptyAction.setText(R.string.policies_empty_action)
            BindingObj.emptyState.btnEmptyAction.visibility = View.VISIBLE
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
        // Undo whatever the renewal view hid, in case that was shown first.
        BindingObj.chipGroupStatus.visibility = View.VISIBLE
        BindingObj.btnExportPdf.visibility = View.VISIBLE
        BindingObj.tilSearch.hint = getString(R.string.policies_search_hint)
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

    /**
     * Both capture modes write session history, so both are listed. PS is
     * excluded while its picker entry is hidden.
     */
    private fun LoadSessions() {
        SessionList = PolicyRepository.GetSessionHistory(ContextRef = requireContext())
            .filter { SessionRef ->
                SessionRef.Mode == CaptureMode.POLICY || SessionRef.Mode == CaptureMode.FUP
            }
            .sortedByDescending { SessionRef -> SessionRef.SavedAt }
    }

    private fun OpenSession(SessionRef: PolicyRepository.CaptureSessionReference) {
        SelectedSessionId = SessionRef.SessionId
        SelectedSessionMode = SessionRef.Mode
        SearchQuery = ""
        StatusFilter = FILTER_ALL
        ViewBindingObj?.etSearch?.setText("")
        ViewBindingObj?.chipAll?.isChecked = true
        LoadSessionRecords()
        RenderList()
    }

    /**
     * Continues an existing session rather than starting a new one, so the
     * capture merges into what is already stored. A full-detail policy capture
     * skips policies whose sections were all read last time.
     */
    private fun ResumeSession(SessionRef: PolicyRepository.CaptureSessionReference) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return

        if (SessionRef.Mode == CaptureMode.POLICY) {
            val SheetBinding = SheetPolicyCaptureModeBinding.inflate(layoutInflater)
            val SheetDialog = BottomSheetDialog(ActivityRef)
            SheetDialog.setContentView(SheetBinding.root)
            SheetBinding.cardFastCapture.setOnClickListener {
                SheetDialog.dismiss()
                LaunchResume(SessionRef = SessionRef, CapturePolicyDetails = false)
            }
            SheetBinding.cardFullCapture.setOnClickListener {
                SheetDialog.dismiss()
                LaunchResume(SessionRef = SessionRef, CapturePolicyDetails = true)
            }
            SheetBinding.btnCancelCaptureMode.setOnClickListener { CancelViewRef ->
                HapticFeedback.Tap(ViewRef = CancelViewRef)
                SheetDialog.dismiss()
            }
            SheetDialog.show()
            return
        }

        LaunchResume(SessionRef = SessionRef, CapturePolicyDetails = false)
    }

    private fun LaunchResume(
        SessionRef: PolicyRepository.CaptureSessionReference,
        CapturePolicyDetails: Boolean
    ) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.root)
        CaptureFlow.Start(
            ActivityRef = ActivityRef,
            ModeVal = SessionRef.Mode,
            LaunchTarget = true,
            CapturePolicyDetails = CapturePolicyDetails,
            ResumeSessionId = SessionRef.SessionId
        )
    }

    /**
     * Deleting a session removes every record it holds and cannot be undone,
     * so the revealed icon asks before acting. The record count is named in
     * the prompt - "12 policies" is a very different decision to "1 policy".
     */
    private fun ConfirmDeleteSession(SessionRef: PolicyRepository.CaptureSessionReference) {
        val ContextRef = context ?: return
        AlertDialog.Builder(ContextRef)
            .setTitle(R.string.sessions_delete_title)
            .setMessage(
                getString(
                    R.string.sessions_delete_body,
                    SessionRef.Mode.DescribeCount(CountVal = SessionRef.RecordCount)
                )
            )
            .setPositiveButton(R.string.sessions_delete_confirm) { _, _ ->
                DeleteSession(SessionRef = SessionRef)
            }
            .setNegativeButton(R.string.sessions_delete_cancel) { _, _ ->
                SessionAdapterObj.CloseOpenRow()
            }
            .setOnCancelListener { SessionAdapterObj.CloseOpenRow() }
            .show()
    }

    private fun DeleteSession(SessionRef: PolicyRepository.CaptureSessionReference) {
        // Fires on the committed delete, not on the dialog appearing, so the
        // pattern only ever means "records are gone".
        HapticFeedback.Reject(ViewRef = ViewBindingObj?.root)
        PolicyRepository.DeleteSession(
            ContextRef = requireContext().applicationContext,
            SessionId = SessionRef.SessionId,
            ModeVal = SessionRef.Mode
        )
        // The open session could be the one just removed.
        if (SelectedSessionId == SessionRef.SessionId) {
            SelectedSessionId = ""
            AllPolicies = emptyList()
            AllRenewals = emptyList()
        }
        SessionAdapterObj.CloseOpenRow()
        LoadSessions()
        RenderList()

        (activity as? androidx.appcompat.app.AppCompatActivity)?.let { ActivityRef ->
            CaptureFlow.ShowMessage(
                ActivityRef = ActivityRef,
                MessageVal = getString(R.string.sessions_deleted)
            )
        }
    }

    private fun ShowSessions() {
        SessionAdapterObj.CloseOpenRow()
        SelectedSessionId = ""
        SelectedSessionMode = CaptureMode.POLICY
        AllPolicies = emptyList()
        AllRenewals = emptyList()
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
        // Fired after the empty guard, so a tap that produces no file stays
        // silent rather than signalling success.
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnExportPdf)
        val ExportedFile = PdfExporter.GeneratePolicyPdf(ContextRef = requireContext(), Policies = VisibleList)
        ShareFile(FileRef = ExportedFile, MimeType = "application/pdf")
    }

    private fun ExportExcel() {
        val ExportedFile = if (SelectedSessionMode == CaptureMode.FUP) {
            val VisibleList = VisibleRenewals()
            if (VisibleList.isEmpty()) return
            HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnExportExcel)
            ExcelExporter.ExportFupPolicies(ContextRef = requireContext(), Policies = VisibleList)
        } else {
            val VisibleList = VisiblePolicies()
            if (VisibleList.isEmpty()) return
            HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnExportExcel)
            ExcelExporter.ExportCustomerPolicies(ContextRef = requireContext(), Policies = VisibleList)
        }
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
