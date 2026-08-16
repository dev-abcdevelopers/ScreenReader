@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.ui.policies

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.bliss.screenreader.databinding.SheetAgencyCodeBinding
import com.bliss.screenreader.databinding.SheetPolicyCaptureModeBinding
import com.bliss.screenreader.databinding.SheetUploadProgressBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.export.PdfExporter
import com.bliss.screenreader.service.CaptureDiagnostics
import com.bliss.screenreader.sync.SessionUploader
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
        OnDeleteClick = { SessionRef -> ConfirmDeleteSession(SessionRef = SessionRef) },
        OnShareLogClick = { SessionRef -> ShareSessionLog(SessionRef = SessionRef) }
    )
    private val SessionSwipeHelper = ItemTouchHelper(SessionSwipeCallback(SessionAdapterObj))
    private val RenewalAdapterObj = RenewalRowAdapter()

    private var AllPolicies: List<CustomerPolicy> = emptyList()
    private var AllRenewals: List<FupPolicy> = emptyList()
    private var SessionList: List<PolicyRepository.CaptureSessionReference> = emptyList()
    private var UploadSheetBinding: SheetUploadProgressBinding? = null
    private var UploadDialogObj: BottomSheetDialog? = null
    private var UploadAgencyCode: String = ""
    private var UploadRunning: Boolean = false
    private var UploadCanRetry: Boolean = false

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
        BindingObj.btnUploadSync.setOnClickListener { UploadSession() }
        BindingObj.btnCapturePersonal.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            CapturePersonalDetails()
        }

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
        BindingObj.emptyState.emptyStateRoot.visibility =
            if (HasVisible) View.GONE else View.VISIBLE
        BindingObj.exportBar.visibility = if (HasVisible) View.VISIBLE else View.GONE
        ApplyExportBarMode(ShowPdf = false)
        BindingObj.btnCapturePersonal.visibility = View.GONE
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
        BindingObj.emptyState.emptyStateRoot.visibility =
            if (HasSessions) View.GONE else View.VISIBLE
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
        BindingObj.chipGroupStatus.visibility = View.VISIBLE
        ApplyExportBarMode(ShowPdf = true)
        BindingObj.btnCapturePersonal.visibility = if (SelectedSessionId.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        BindingObj.tilSearch.hint = getString(R.string.policies_search_hint)
        SessionBackCallback?.isEnabled = true

        BindingObj.tvPolicyCount.text = getString(
            R.string.policies_count_format, VisibleList.size, AllPolicies.size
        )

        val HasAny = AllPolicies.isNotEmpty()
        val HasVisible = VisibleList.isNotEmpty()

        BindingObj.emptyState.emptyStateRoot.visibility =
            if (HasVisible) View.GONE else View.VISIBLE
        BindingObj.exportBar.visibility = if (HasVisible) View.VISIBLE else View.GONE

        if (HasVisible) return


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
        HapticFeedback.Reject(ViewRef = ViewBindingObj?.root)
        PolicyRepository.DeleteSession(
            ContextRef = requireContext().applicationContext,
            SessionId = SessionRef.SessionId,
            ModeVal = SessionRef.Mode
        )
        CaptureDiagnostics.DeleteSessionLogs(
            ContextObj = requireContext().applicationContext,
            SessionId = SessionRef.SessionId
        )

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

    private fun ApplyExportBarMode(ShowPdf: Boolean) {
        val BindingObj = ViewBindingObj ?: return
        val UploadMode = SessionUploader.IsEnabled()
        BindingObj.exportButtonRow.visibility = if (UploadMode) View.GONE else View.VISIBLE
        BindingObj.btnExportPdf.visibility = if (ShowPdf) View.VISIBLE else View.GONE
        BindingObj.btnUploadSync.visibility = if (UploadMode) View.VISIBLE else View.GONE
    }

    private fun UploadSession() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        if (SelectedSessionId.isEmpty()) return

        val AppContext = requireContext().applicationContext
        val SavedCode = PolicyRepository.GetAgencyCode(
            ContextRef = AppContext,
            SessionId = SelectedSessionId
        )
        ShowAgencyCodeSheet(
            ActivityRef = ActivityRef,
            SavedCode = SavedCode,
            BodyText = getString(R.string.export_agency_upload_body),
            ConfirmText = getString(R.string.export_agency_upload_confirm)
        ) { EnteredCode ->
            PolicyRepository.SaveAgencyCode(
                ContextRef = AppContext,
                SessionId = SelectedSessionId,
                AgencyCode = EnteredCode
            )
            RunUpload(ActivityRef = ActivityRef, AgencyCodeVal = EnteredCode)
        }
    }

    private fun RunUpload(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        AgencyCodeVal: String
    ) {
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnUploadSync)
        UploadAgencyCode = AgencyCodeVal
        ShowUploadSheet(ActivityRef = ActivityRef)
        StartUpload()
    }

    private fun ShowUploadSheet(ActivityRef: androidx.appcompat.app.AppCompatActivity) {
        val SheetBinding = SheetUploadProgressBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)
        SheetDialog.setCancelable(false)
        SheetDialog.setOnDismissListener {
            UploadSheetBinding = null
            UploadDialogObj = null
        }
        UploadSheetBinding = SheetBinding
        UploadDialogObj = SheetDialog

        SheetBinding.btnUploadSecondary.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            if (UploadRunning) {
                SheetBinding.btnUploadSecondary.isEnabled = false
                SessionUploader.Cancel()
            } else {
                SheetDialog.dismiss()
            }
        }
        SheetBinding.btnUploadPrimary.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            if (UploadRunning) return@setOnClickListener
            if (UploadCanRetry) StartUpload() else SheetDialog.dismiss()
        }

        SheetDialog.show()
    }

    private fun StartUpload() {
        val SheetBinding = UploadSheetBinding ?: return
        UploadRunning = true
        UploadCanRetry = false

        SheetBinding.btnUploadPrimary.visibility = View.GONE
        SheetBinding.btnUploadSecondary.visibility = View.VISIBLE
        SheetBinding.btnUploadSecondary.isEnabled = true
        SheetBinding.btnUploadSecondary.setText(R.string.upload_action_cancel)
        SheetBinding.ivProgressResult.visibility = View.GONE
        SheetBinding.tvProgressPercent.visibility = View.GONE
        SheetBinding.progressDeterminate.visibility = View.GONE
        SheetBinding.progressIndeterminate.visibility = View.VISIBLE
        SheetBinding.tvUploadTitle.setText(R.string.upload_sheet_title)
        SheetBinding.tvUploadMeta.setText(R.string.upload_meta_preparing)
        SetPhase(PhaseIndex = 0)

        SessionUploader.UploadSession(
            ContextRef = requireContext(),
            SessionId = SelectedSessionId,
            AgencyCode = UploadAgencyCode,
            OnProgress = { ProgressVal -> RenderUploadProgress(ProgressVal = ProgressVal) }
        ) { OutcomeVal -> RenderUploadResult(OutcomeVal = OutcomeVal) }
    }

    private fun RenderUploadProgress(ProgressVal: SessionUploader.Progress) {
        if (!isAdded) return
        val SheetBinding = UploadSheetBinding ?: return

        when (ProgressVal) {
            SessionUploader.Progress.Packing -> SetPhase(PhaseIndex = 0)

            is SessionUploader.Progress.Ready -> {
                SheetBinding.tvUploadTitle.text = getString(
                    R.string.upload_sheet_title_key_format, ProgressVal.ObjectKey
                )
                SheetBinding.tvUploadMeta.text = getString(
                    R.string.upload_meta_format,
                    getString(
                        R.string.upload_counts_format,
                        ProgressVal.PolicyCount,
                        ProgressVal.RenewalCount,
                        ProgressVal.GapCount
                    ),
                    Formatter.formatShortFileSize(requireContext(), ProgressVal.TotalBytes)
                )
                SetPhase(PhaseIndex = 1)
            }

            is SessionUploader.Progress.Sending -> {
                val TotalBytes = ProgressVal.TotalBytes.coerceAtLeast(1L)
                val PercentVal = (ProgressVal.SentBytes * 100L / TotalBytes)
                    .toInt()
                    .coerceIn(0, 100)

                SheetBinding.progressIndeterminate.visibility = View.GONE
                SheetBinding.progressDeterminate.visibility = View.VISIBLE
                SheetBinding.progressDeterminate.setProgressCompat(PercentVal, true)
                SheetBinding.tvProgressPercent.visibility = View.VISIBLE
                SheetBinding.tvProgressPercent.text = getString(
                    R.string.upload_percent_format, PercentVal
                )
                SheetBinding.tvPhaseSend.text = getString(
                    R.string.upload_phase_send_format,
                    Formatter.formatShortFileSize(requireContext(), ProgressVal.SentBytes),
                    Formatter.formatShortFileSize(requireContext(), ProgressVal.TotalBytes)
                )
                SetPhase(PhaseIndex = 1)
            }

            SessionUploader.Progress.Waiting -> {
                SheetBinding.progressDeterminate.visibility = View.GONE
                SheetBinding.tvProgressPercent.visibility = View.GONE
                SheetBinding.progressIndeterminate.visibility = View.VISIBLE
                SetPhase(PhaseIndex = 2)
            }
        }
    }

    private fun RenderUploadResult(OutcomeVal: SessionUploader.Outcome) {
        if (!isAdded) return
        UploadRunning = false
        val SheetBinding = UploadSheetBinding ?: return

        SheetBinding.progressDeterminate.visibility = View.GONE
        SheetBinding.progressIndeterminate.visibility = View.GONE
        SheetBinding.tvProgressPercent.visibility = View.GONE
        SheetBinding.ivProgressResult.visibility = View.VISIBLE
        SheetBinding.btnUploadPrimary.visibility = View.VISIBLE

        when (OutcomeVal) {
            is SessionUploader.Outcome.Uploaded -> {
                HapticFeedback.Confirm(ViewRef = SheetBinding.root)
                SetResultIcon(
                    IconRes = R.drawable.ic_check_circle,
                    ColorRes = R.color.status_green_text
                )
                SetPhase(PhaseIndex = 3)
                SheetBinding.tvUploadTitle.setText(R.string.upload_sheet_title_done)
                SheetBinding.tvUploadMeta.text = getString(
                    R.string.upload_meta_done_format,
                    OutcomeVal.Key,
                    OutcomeVal.RecordCount,
                    getString(R.string.upload_elapsed_format, OutcomeVal.ElapsedMs / 1000f)
                )
                UploadCanRetry = false
                SheetBinding.btnUploadPrimary.setText(R.string.upload_action_done)
                SheetBinding.btnUploadSecondary.visibility = View.GONE
            }

            is SessionUploader.Outcome.Failed -> ShowUploadStop(
                TitleRes = R.string.upload_sheet_title_failed,
                ColorRes = R.color.status_red_text,
                MetaText = getString(R.string.upload_meta_failed_format, OutcomeVal.Message) +
                        "\n" + getString(R.string.upload_meta_kept),
                AllowRetry = true
            )

            SessionUploader.Outcome.Cancelled -> ShowUploadStop(
                TitleRes = R.string.upload_sheet_title_cancelled,
                ColorRes = R.color.status_amber_text,
                MetaText = getString(R.string.upload_meta_cancelled),
                AllowRetry = true
            )

            SessionUploader.Outcome.NotConfigured -> ShowUploadStop(
                TitleRes = R.string.upload_sheet_title_failed,
                ColorRes = R.color.status_red_text,
                MetaText = getString(R.string.upload_not_configured),
                AllowRetry = false
            )

            SessionUploader.Outcome.NothingToSend -> ShowUploadStop(
                TitleRes = R.string.upload_sheet_title_failed,
                ColorRes = R.color.status_amber_text,
                MetaText = getString(R.string.upload_nothing_to_send),
                AllowRetry = false
            )
        }
    }

    private fun ShowUploadStop(
        TitleRes: Int,
        ColorRes: Int,
        MetaText: String,
        AllowRetry: Boolean
    ) {
        val SheetBinding = UploadSheetBinding ?: return
        HapticFeedback.Reject(ViewRef = SheetBinding.root)
        SetResultIcon(IconRes = R.drawable.ic_alert, ColorRes = ColorRes)
        SheetBinding.tvUploadTitle.setText(TitleRes)
        SheetBinding.tvUploadMeta.text = MetaText

        UploadCanRetry = AllowRetry
        SheetBinding.btnUploadPrimary.setText(
            if (AllowRetry) R.string.upload_action_retry else R.string.upload_action_close
        )
        SheetBinding.btnUploadSecondary.visibility =
            if (AllowRetry) View.VISIBLE else View.GONE
        SheetBinding.btnUploadSecondary.isEnabled = true
        SheetBinding.btnUploadSecondary.setText(R.string.upload_action_close)
    }

    private fun SetResultIcon(IconRes: Int, ColorRes: Int) {
        val SheetBinding = UploadSheetBinding ?: return
        SheetBinding.ivProgressResult.setImageResource(IconRes)
        SheetBinding.ivProgressResult.setColorFilter(
            ContextCompat.getColor(requireContext(), ColorRes)
        )
    }

    private fun SetPhase(PhaseIndex: Int) {
        val SheetBinding = UploadSheetBinding ?: return
        PaintPhase(
            IconRef = SheetBinding.ivPhasePack,
            LabelRef = SheetBinding.tvPhasePack,
            StateVal = PhaseIndex - 0
        )
        PaintPhase(
            IconRef = SheetBinding.ivPhaseSend,
            LabelRef = SheetBinding.tvPhaseSend,
            StateVal = PhaseIndex - 1
        )
        PaintPhase(
            IconRef = SheetBinding.ivPhaseWait,
            LabelRef = SheetBinding.tvPhaseWait,
            StateVal = PhaseIndex - 2
        )
    }

    private fun PaintPhase(
        IconRef: android.widget.ImageView,
        LabelRef: android.widget.TextView,
        StateVal: Int
    ) {
        val ContextRef = context ?: return
        when {
            StateVal > 0 -> {
                IconRef.setImageResource(R.drawable.ic_check_circle)
                IconRef.setColorFilter(
                    ContextCompat.getColor(ContextRef, R.color.status_green_text)
                )
                LabelRef.setTextColor(ContextCompat.getColor(ContextRef, R.color.text_secondary))
            }

            StateVal == 0 -> {
                IconRef.setImageResource(R.drawable.ic_record)
                IconRef.setColorFilter(ContextCompat.getColor(ContextRef, R.color.primary))
                LabelRef.setTextColor(ContextCompat.getColor(ContextRef, R.color.text_primary))
            }

            else -> {
                IconRef.setImageResource(R.drawable.ic_record)
                IconRef.setColorFilter(ContextCompat.getColor(ContextRef, R.color.text_faint))
                LabelRef.setTextColor(ContextCompat.getColor(ContextRef, R.color.text_faint))
            }
        }
    }

    private fun OpenDetail(PolicyItem: CustomerPolicy) {
        startActivity(
            Intent(requireContext(), PolicyDetailActivity::class.java).apply {
                putExtra(PolicyDetailActivity.EXTRA_POLICY_NUMBER, PolicyItem.PolicyNumber)
                putExtra(PolicyDetailActivity.EXTRA_SESSION_ID, SelectedSessionId)
            }
        )
    }


    private fun ExportPdf() {
        val VisibleList = VisiblePolicies()
        if (VisibleList.isEmpty()) return
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnExportPdf)
        val ExportedFile =
            PdfExporter.GeneratePolicyPdf(ContextRef = requireContext(), Policies = VisibleList)
        ShareFile(FileRef = ExportedFile, MimeType = "application/pdf")
    }

    private fun ExportExcel() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        val HasRows = if (SelectedSessionMode == CaptureMode.FUP) {
            VisibleRenewals().isNotEmpty()
        } else {
            VisiblePolicies().isNotEmpty()
        }
        if (!HasRows) return

        val AppContext = requireContext().applicationContext
        val SavedCode = PolicyRepository.GetAgencyCode(
            ContextRef = AppContext,
            SessionId = SelectedSessionId
        )
        ShowAgencyCodeSheet(ActivityRef = ActivityRef, SavedCode = SavedCode) { EnteredCode ->
            PolicyRepository.SaveAgencyCode(
                ContextRef = AppContext,
                SessionId = SelectedSessionId,
                AgencyCode = EnteredCode
            )
            RunExcelExport(AgencyCodeVal = EnteredCode)
        }
    }

    private fun ShowAgencyCodeSheet(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        SavedCode: String,
        BodyText: String = getString(R.string.export_agency_body),
        ConfirmText: String = getString(R.string.export_agency_confirm),
        OnConfirm: (String) -> Unit
    ) {
        val SheetBinding = SheetAgencyCodeBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)

        SheetBinding.tvAgencyBody.text = BodyText
        SheetBinding.btnAgencyExport.text = ConfirmText
        SheetBinding.etAgencyCode.setText(SavedCode)
        SheetBinding.etAgencyCode.setSelection(SavedCode.length)
        SheetBinding.btnAgencyExport.isEnabled = SavedCode.isNotBlank()
        SheetBinding.etAgencyCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                SheetBinding.btnAgencyExport.isEnabled = !s?.toString().isNullOrBlank()
            }
        })

        SheetBinding.btnAgencyExport.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            val EnteredCode = SheetBinding.etAgencyCode.text?.toString()?.trim().orEmpty()
            if (EnteredCode.isEmpty()) return@setOnClickListener
            SheetDialog.dismiss()
            OnConfirm(EnteredCode)
        }
        SheetBinding.btnAgencyCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun RunExcelExport(AgencyCodeVal: String) {
        val ExportedFile = if (SelectedSessionMode == CaptureMode.FUP) {
            val VisibleList = VisibleRenewals()
            if (VisibleList.isEmpty()) return
            HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnExportExcel)
            ExcelExporter.ExportFupPolicies(
                ContextRef = requireContext(),
                Policies = VisibleList,
                AgencyCode = AgencyCodeVal
            )
        } else {
            val VisibleList = VisiblePolicies()
            if (VisibleList.isEmpty()) return
            HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnExportExcel)
            ExcelExporter.ExportCustomerPolicies(
                ContextRef = requireContext(),
                Policies = VisibleList,
                AgencyCode = AgencyCodeVal
            )
        }
        ShareFile(
            FileRef = ExportedFile,
            MimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    private fun CapturePersonalDetails() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        if (SelectedSessionId.isEmpty() || SelectedSessionMode != CaptureMode.POLICY) {
            CaptureFlow.ShowMessage(
                ActivityRef = ActivityRef,
                MessageVal = getString(R.string.capture_customer_no_sessions)
            )
            return
        }
        CaptureFlow.StartCustomerCapture(
            ActivityRef = ActivityRef,
            SessionIdVal = SelectedSessionId
        )
    }

    private fun ShareSessionLog(SessionRef: PolicyRepository.CaptureSessionReference) {
        SessionAdapterObj.CloseOpenRow()
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        val ContextRef = requireContext().applicationContext
        val ShareIntent = CaptureDiagnostics.BuildShareIntent(
            ContextObj = ContextRef,
            LogFiles = CaptureDiagnostics.GetSessionLogFiles(
                ContextObj = ContextRef,
                SessionId = SessionRef.SessionId
            )
        )
        if (ShareIntent == null) {
            CaptureFlow.ShowMessage(
                ActivityRef = ActivityRef,
                MessageVal = getString(R.string.sessions_share_log_missing)
            )
            return
        }
        startActivity(
            Intent.createChooser(ShareIntent, getString(R.string.sessions_share_log_title))
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
        UploadDialogObj?.dismiss()
        UploadDialogObj = null
        UploadSheetBinding = null
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
