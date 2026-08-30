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
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.data.parser.PolicyStatusRules
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.ChangeSource
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.PolicyResumeMark
import com.bliss.screenreader.data.model.PolicyResumeTarget
import com.bliss.screenreader.data.model.PolicyResumeTrack
import com.bliss.screenreader.data.parser.FupDataParser
import com.bliss.screenreader.data.parser.RenewalDueProjection
import com.bliss.screenreader.data.model.DueDateReport
import com.bliss.screenreader.data.model.DueDateReportEntry
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.FragmentPoliciesBinding
import com.bliss.screenreader.databinding.ItemSessionPickBinding
import com.bliss.screenreader.databinding.SheetAgencyCodeBinding
import com.bliss.screenreader.databinding.SheetPolicyCaptureModeBinding
import com.bliss.screenreader.databinding.PartialChangeSectionBinding
import com.bliss.screenreader.databinding.PartialSettingsChoiceRowBinding
import com.bliss.screenreader.databinding.PartialDueDateGroupBinding
import com.bliss.screenreader.databinding.PartialDueDateRowBinding
import com.bliss.screenreader.databinding.PartialDueReasonGroupBinding
import com.bliss.screenreader.databinding.PartialDueStatBinding
import com.bliss.screenreader.databinding.SheetDuePreviewBinding
import com.bliss.screenreader.databinding.SheetSessionActionsBinding
import com.bliss.screenreader.databinding.SheetSessionPickerBinding
import com.bliss.screenreader.databinding.SheetSettingsDetailBinding
import com.bliss.screenreader.databinding.SheetUploadProgressBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.export.PdfExporter
import com.bliss.screenreader.service.CaptureDiagnostics
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.settings.SettingsStore
import com.bliss.screenreader.sync.SessionUploader
import com.bliss.screenreader.ui.adapter.CaptureSessionAdapter
import com.bliss.screenreader.ui.adapter.PolicyRowAdapter
import com.bliss.screenreader.ui.adapter.RenewalRowAdapter
import com.bliss.screenreader.ui.adapter.SessionStickyHeaderDecoration
import com.bliss.screenreader.ui.adapter.SessionSwipeCallback
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.ui.toast.AppToast
import com.bliss.screenreader.ui.changes.ChangesActivity
import com.bliss.screenreader.ui.detail.PolicyDetailActivity
import com.bliss.screenreader.ui.main.MainActivity
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
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
    private val SessionStickyObj = SessionStickyHeaderDecoration(SessionAdapterObj)
    private val RenewalAdapterObj = RenewalRowAdapter()
    private var RowDividerObj: DividerItemDecoration? = null

    private var AllPolicies: List<CustomerPolicy> = emptyList()
    private var AllRenewals: List<FupPolicy> = emptyList()
    private var SessionList: List<PolicyRepository.CaptureSessionReference> = emptyList()
    private var UploadSheetBinding: SheetUploadProgressBinding? = null
    private var UploadPhaseIndex = 0
    private var UploadDialogObj: BottomSheetDialog? = null
    private var UploadAgencyCode: String = ""
    private var UploadRunning: Boolean = false
    private var UploadCanRetry: Boolean = false

    private var SelectedSessionId: String = ""
    private var SelectedSessionMode: CaptureMode = CaptureMode.POLICY
    private var SearchQuery: String = ""
    private var StatusFilter: String = FILTER_ALL
    private var ShowPdfAction = true

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
        val DividerObj = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        RowDividerObj = DividerObj
        BindingObj.rvPolicies.addItemDecoration(DividerObj)
        SessionSwipeHelper.attachToRecyclerView(BindingObj.rvPolicies)
        BindingObj.btnSessionsBack.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ShowSessions()
        }

        CaptureSessionState.IsCapturingLive.observe(viewLifecycleOwner) { IsCapturingVal ->
            if (IsCapturingVal == false) ReloadFromStore()
        }
        CaptureSessionState.PendingSessionLive.observe(viewLifecycleOwner) { SessionObj ->
            if (SessionObj == null) ReloadFromStore()
        }

        BindingObj.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                SearchQuery = s?.toString().orEmpty()
                RenderList(ResetScroll = true)
            }
        })

        BindingObj.chipGroupStatus.setOnCheckedStateChangeListener { GroupRef, CheckedIds ->
            if (SuppressChipCallback) return@setOnCheckedStateChangeListener
            val CheckedId = CheckedIds.firstOrNull()
            StatusFilter = if (CheckedId == null) {
                FILTER_ALL
            } else {
                GroupRef.findViewById<Chip>(CheckedId)?.tag as? String ?: FILTER_ALL
            }
            RenderList(ResetScroll = true)
        }

        BindingObj.btnSessionActions.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ShowSessionActionsSheet()
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
        ReloadFromStore()
    }

    private fun ReloadFromStore() {
        if (ViewBindingObj == null) return
        LoadSessions()
        if (SelectedSessionId.isNotEmpty()) {
            if (SessionList.none { SessionRef -> SessionRef.SessionId == SelectedSessionId }) {
                SelectedSessionId = ""
            } else {
                LoadSessionRecords()
            }
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
            val MatchesStatus = StatusFilter == FILTER_ALL ||
                    StatusFilter == StatusFilterKey(StatusText = PolicyItem.NormalizedStatus)
            val MatchesQuery = QueryLower.isEmpty() ||
                    PolicyItem.PolicyNumber.lowercase(Locale.ROOT).contains(QueryLower) ||
                    PolicyItem.HolderName.lowercase(Locale.ROOT).contains(QueryLower)
            MatchesStatus && MatchesQuery
        }
    }

    private fun VisibleRenewals(): List<FupPolicy> {
        val QueryLower = SearchQuery.trim().lowercase(Locale.ROOT)
        val FilteredList = AllRenewals.filter { RenewalItem ->
            val IsConcerning = RenewalRowAdapter.IsConcerningStatus(
                StatusText = RenewalItem.Status
            )
            val MatchesStatus = when (StatusFilter) {
                FILTER_INFORCE -> !IsConcerning
                FILTER_LAPSED -> IsConcerning
                else -> true
            }
            val MatchesQuery = QueryLower.isEmpty() ||
                    RenewalItem.PolicyNumber.lowercase(Locale.ROOT).contains(QueryLower) ||
                    RenewalItem.HolderName.lowercase(Locale.ROOT).contains(QueryLower)
            MatchesStatus && MatchesQuery
        }
        return FilteredList.sortedWith(
            compareByDescending<FupPolicy> { RenewalItem -> SortableDate(RenewalItem = RenewalItem) }
                .thenBy { RenewalItem -> RenewalItem.HolderName }
        )
    }

    private fun SortableDate(RenewalItem: FupPolicy): Long {
        val PaymentObj = RenewalDueProjection.ParseDate(RawText = RenewalItem.PaymentDate)
        val DueObj = RenewalDueProjection.ParseDate(RawText = RenewalItem.DueDate)
        val ResolvedObj = PaymentObj ?: DueObj ?: return Long.MIN_VALUE
        return ResolvedObj.toEpochDay()
    }

    private fun RenewalSummaryText(VisibleList: List<FupPolicy>): String {
        val ConcerningCount = VisibleList.count { RenewalItem ->
            RenewalRowAdapter.IsConcerningStatus(StatusText = RenewalItem.Status)
        }
        val TotalRupees = VisibleList.sumOf { RenewalItem ->
            AmountValue(PremiumText = RenewalItem.PremiumAmount)
        }
        if (TotalRupees <= 0L) {
            return getString(R.string.renewals_summary_no_amounts, ConcerningCount)
        }
        val AmountText = NumberFormat.getInstance(Locale.forLanguageTag("en-IN")).format(TotalRupees)
        return getString(R.string.renewals_summary_format, "₹$AmountText", ConcerningCount)
    }

    private fun AmountValue(PremiumText: String): Long {
        val DigitsText = FupDataParser.AmountOf(PremiumText = PremiumText)
            .filter { CharValue -> CharValue.isDigit() }
        if (DigitsText.isEmpty()) return 0L
        return DigitsText.toLongOrNull() ?: 0L
    }

    private fun PickRenewalSessionForDueDates() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        if (SelectedSessionId.isEmpty() || SelectedSessionMode != CaptureMode.POLICY) return

        val RenewalSessions = PolicyRepository.GetSessionHistory(ContextRef = ActivityRef)
            .filter { SessionRef -> SessionRef.Mode == CaptureMode.FUP }
            .sortedByDescending { SessionRef -> SessionRef.SavedAt }

        if (RenewalSessions.isEmpty()) {
            ShowSnack(
                MessageVal = getString(R.string.due_no_sessions),
                KindVal = AppToast.Kind.Warning
            )
            return
        }

        val DateFormatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        val SheetBinding = SheetSessionPickerBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)

        SheetBinding.tvSessionPickHeading.setText(R.string.due_pick_session)
        SheetBinding.tvSessionPickBody.setText(R.string.due_pick_body)

        for ((SessionId, Mode, SavedAt, RecordCount) in RenewalSessions) {
            val RowBinding = ItemSessionPickBinding.inflate(
                layoutInflater,
                SheetBinding.sessionPickContainer,
                false
            )
            RowBinding.tvSessionPickTitle.text = Mode.DescribeCount(
                CountVal = RecordCount
            )
            RowBinding.tvSessionPickMeta.text = getString(
                R.string.capture_customer_session_format,
                DateFormatter.format(Date(SavedAt)),
                SessionId.take(8)
            )
            RowBinding.sessionPickCard.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                ApplyDueDatesFrom(RenewalSessionId = SessionId)
            }
            SheetBinding.sessionPickContainer.addView(RowBinding.root)
        }

        SheetBinding.btnSessionPickCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun ApplyDueDatesFrom(RenewalSessionId: String) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        val ContextRef = ActivityRef.applicationContext
        if (SelectedSessionId.isEmpty()) return

        val RenewalList = PolicyRepository.GetFupPolicies(
            ContextRef = ContextRef,
            SessionId = RenewalSessionId
        )
        if (RenewalList.isEmpty()) {
            ShowSnack(
                MessageVal = getString(R.string.due_no_sessions),
                KindVal = AppToast.Kind.Warning
            )
            return
        }

        val OutcomeObj = RenewalDueProjection.Apply(
            Policies = AllPolicies,
            Renewals = RenewalList
        )

        if (OutcomeObj.MatchedCount == 0) {
            ShowSnack(
                MessageVal = getString(R.string.due_no_matches),
                KindVal = AppToast.Kind.Warning
            )
            return
        }

        ShowDuePreviewSheet(
            ActivityRef = ActivityRef,
            OutcomeObj = OutcomeObj,
            RenewalSessionId = RenewalSessionId,
            RenewalCount = RenewalList.size
        )
    }

    private fun ShowDuePreviewSheet(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        OutcomeObj: RenewalDueProjection.Outcome,
        RenewalSessionId: String,
        RenewalCount: Int
    ) {
        val SheetBinding = SheetDuePreviewBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)
        SheetDialog.setCanceledOnTouchOutside(false)
        SheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        SheetDialog.behavior.skipCollapsed = true
        SheetDialog.behavior.isDraggable = false
        SheetBinding.dueSheetHandle.visibility = View.INVISIBLE

        val HasUpdates = OutcomeObj.Updates.isNotEmpty()
        val SkipGroups = GroupSkips(SkipList = OutcomeObj.Skips)

        if (HasUpdates) {
            BindUpdatePreview(
                SheetBinding = SheetBinding,
                OutcomeObj = OutcomeObj,
                SkipGroups = SkipGroups
            )
        } else {
            BindEmptyPreview(
                SheetBinding = SheetBinding,
                OutcomeObj = OutcomeObj,
                SkipGroups = SkipGroups
            )
        }

        SheetBinding.btnDuePreviewApply.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            SheetDialog.dismiss()
            CommitDueDates(
                OutcomeObj = OutcomeObj,
                RenewalSessionId = RenewalSessionId,
                RenewalCount = RenewalCount
            )
        }
        SheetBinding.btnDuePreviewCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun BindUpdatePreview(
        SheetBinding: SheetDuePreviewBinding,
        OutcomeObj: RenewalDueProjection.Outcome,
        SkipGroups: List<Pair<RenewalDueProjection.SkipReason, List<RenewalDueProjection.Skip>>>
    ) {
        SheetBinding.tvDuePreviewTitle.text = getString(
            R.string.due_preview_title,
            OutcomeObj.UpdatedCount
        )
        SheetBinding.tvDuePreviewHint.text = getString(
            R.string.due_preview_hint,
            OutcomeObj.UnchangedCount,
            OutcomeObj.SkippedCount
        )
        SheetBinding.btnDuePreviewApply.text = getString(
            R.string.due_preview_apply,
            OutcomeObj.UpdatedCount
        )

        AddChangeSection(
            ContainerRef = SheetBinding.duePreviewContainer,
            TitleRes = R.string.due_section_change
        )
        AddUpdateGroups(
            ContainerRef = SheetBinding.duePreviewContainer,
            UpdateList = OutcomeObj.Updates
        )

        if (SkipGroups.isEmpty()) return
        AddChangeSection(
            ContainerRef = SheetBinding.duePreviewContainer,
            TitleRes = R.string.due_section_keep
        )
        AddSkipGroups(
            ContainerRef = SheetBinding.duePreviewContainer,
            SkipGroups = SkipGroups
        )
    }

    private fun BindEmptyPreview(
        SheetBinding: SheetDuePreviewBinding,
        OutcomeObj: RenewalDueProjection.Outcome,
        SkipGroups: List<Pair<RenewalDueProjection.SkipReason, List<RenewalDueProjection.Skip>>>
    ) {
        val OnlyAlreadyUpdated = SkipGroups.size == 1 &&
                SkipGroups.first().first == RenewalDueProjection.SkipReason.ALREADY_CURRENT

        SheetBinding.tvDuePreviewTitle.visibility = View.GONE
        SheetBinding.tvDuePreviewHint.visibility = View.GONE
        SheetBinding.duePreviewScroll.visibility = View.GONE
        SheetBinding.btnDuePreviewApply.visibility = View.GONE
        SheetBinding.dueHeroGroup.visibility = View.VISIBLE
        SheetBinding.btnDuePreviewCancel.setText(R.string.due_close)

        SheetBinding.tvDueHeroTitle.setText(
            if (OnlyAlreadyUpdated) {
                R.string.due_hero_all_updated
            } else {
                R.string.due_preview_none_title
            }
        )
        SheetBinding.tvDueHeroBody.text = if (OnlyAlreadyUpdated) {
            getString(R.string.due_hero_all_updated_body, OutcomeObj.MatchedCount)
        } else {
            getString(R.string.due_hero_mixed_body, OutcomeObj.MatchedCount)
        }

        AddStatTiles(ContainerRef = SheetBinding.dueStatRow, SkipGroups = SkipGroups)
        AddSkipGroups(
            ContainerRef = SheetBinding.duePreviewContainer,
            SkipGroups = SkipGroups
        )

        val TotalSkips = OutcomeObj.Skips.size
        SheetBinding.tvShowPolicies.text = getString(R.string.due_show_policies, TotalSkips)
        SheetBinding.rowShowPolicies.visibility = if (TotalSkips == 0) View.GONE else View.VISIBLE
        SheetBinding.rowShowPolicies.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            val WillShow = SheetBinding.duePreviewScroll.visibility != View.VISIBLE
            SheetBinding.duePreviewScroll.visibility = if (WillShow) View.VISIBLE else View.GONE
            SheetBinding.ivShowPolicies.rotation = if (WillShow) 90f else 0f
            if (WillShow) {
                SheetBinding.tvShowPolicies.setText(R.string.due_hide_policies)
            } else {
                SheetBinding.tvShowPolicies.text = getString(
                    R.string.due_show_policies,
                    TotalSkips
                )
            }
        }
    }

    private fun GroupSkips(
        SkipList: List<RenewalDueProjection.Skip>
    ): List<Pair<RenewalDueProjection.SkipReason, List<RenewalDueProjection.Skip>>> {
        val OrderList = listOf(
            RenewalDueProjection.SkipReason.ALREADY_CURRENT,
            RenewalDueProjection.SkipReason.NO_FREQUENCY,
            RenewalDueProjection.SkipReason.NO_RENEWAL_ROW
        )
        return OrderList.mapNotNull { ReasonVal ->
            val GroupList = SkipList.filter { SkipItem -> SkipItem.Reason == ReasonVal }
            if (GroupList.isEmpty()) null else ReasonVal to GroupList
        }
    }

    private fun AddStatTiles(
        ContainerRef: ViewGroup,
        SkipGroups: List<Pair<RenewalDueProjection.SkipReason, List<RenewalDueProjection.Skip>>>
    ) {
        ContainerRef.removeAllViews()
        val ContextRef = ContainerRef.context
        for ((IndexVal, GroupPair) in SkipGroups.withIndex()) {
            val StatBinding = PartialDueStatBinding.inflate(layoutInflater, ContainerRef, false)
            val IsGood = GroupPair.first == RenewalDueProjection.SkipReason.ALREADY_CURRENT
            StatBinding.tvDueStatValue.text = GroupPair.second.size.toString()
            StatBinding.tvDueStatLabel.setText(StatLabelRes(ReasonVal = GroupPair.first))
            StatBinding.dueStatRoot.setBackgroundResource(
                if (IsGood) R.drawable.bg_badge_inforce else R.drawable.bg_badge_lapsed
            )
            val TintColor = ContextCompat.getColor(
                ContextRef,
                if (IsGood) R.color.status_green_text else R.color.status_amber_text
            )
            StatBinding.tvDueStatValue.setTextColor(TintColor)
            StatBinding.tvDueStatLabel.setTextColor(TintColor)
            if (IndexVal > 0) {
                val ParamsObj = StatBinding.dueStatRoot.layoutParams
                        as android.widget.LinearLayout.LayoutParams
                ParamsObj.marginStart = resources.getDimensionPixelSize(R.dimen.space_sm)
                StatBinding.dueStatRoot.layoutParams = ParamsObj
            }
            ContainerRef.addView(StatBinding.root)
        }
    }

    private fun AddSkipGroups(
        ContainerRef: ViewGroup,
        SkipGroups: List<Pair<RenewalDueProjection.SkipReason, List<RenewalDueProjection.Skip>>>
    ) {
        for ((ReasonVal, SkipList) in SkipGroups) {
            val GroupBinding = PartialDueReasonGroupBinding.inflate(
                layoutInflater,
                ContainerRef,
                false
            )
            val IsGood = ReasonVal == RenewalDueProjection.SkipReason.ALREADY_CURRENT
            GroupBinding.tvDueReasonTitle.setText(StatLabelRes(ReasonVal = ReasonVal))
            GroupBinding.tvDueReasonCount.text = SkipList.size.toString()
            GroupBinding.tvDueReasonWhy.setText(SkipReasonText(ReasonVal = ReasonVal))
            GroupBinding.dueReasonDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                        ContainerRef.context,
                        if (IsGood) R.color.status_green_text else R.color.status_amber_text
                    )
                )

            AddDueDateGroups(
                ContainerRef = GroupBinding.dueReasonDates,
                IsMuted = true,
                EntryList = SkipList.map { SkipItem ->
                    DueEntry(
                        PolicyNumber = SkipItem.PolicyNumber,
                        HolderName = SkipItem.HolderName,
                        DateText = SkipItem.CurrentDate,
                        NoteText = ""
                    )
                }
            )

            GroupBinding.dueReasonHeader.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                val WillShow = GroupBinding.dueReasonBody.visibility != View.VISIBLE
                GroupBinding.dueReasonBody.visibility = if (WillShow) View.VISIBLE else View.GONE
                GroupBinding.ivDueReasonChevron.rotation = if (WillShow) 90f else 0f
            }
            ContainerRef.addView(GroupBinding.root)
        }
    }

    private fun StatLabelRes(ReasonVal: RenewalDueProjection.SkipReason): Int = when (ReasonVal) {
        RenewalDueProjection.SkipReason.ALREADY_CURRENT -> R.string.due_stat_updated
        RenewalDueProjection.SkipReason.NO_FREQUENCY -> R.string.due_stat_frequency
        RenewalDueProjection.SkipReason.NO_RENEWAL_ROW -> R.string.due_stat_norow
    }

    private fun AddChangeSection(ContainerRef: ViewGroup, TitleRes: Int) {
        val SectionBinding = PartialChangeSectionBinding.inflate(
            layoutInflater,
            ContainerRef,
            false
        )
        SectionBinding.tvChangeSection.setText(TitleRes)
        ContainerRef.addView(SectionBinding.root)
    }

    private fun AddUpdateGroups(
        ContainerRef: ViewGroup,
        UpdateList: List<RenewalDueProjection.Update>
    ) {
        AddDueDateGroups(
            ContainerRef = ContainerRef,
            EntryList = UpdateList.map { UpdateItem ->
                DueEntry(
                    PolicyNumber = UpdateItem.PolicyNumber,
                    HolderName = UpdateItem.HolderName,
                    DateText = UpdateItem.NewDate,
                    NoteText = if (UpdateItem.OldDate.isEmpty()) {
                        getString(R.string.due_row_new)
                    } else {
                        getString(R.string.due_row_was, UpdateItem.OldDate)
                    }
                )
            }
        )
    }

    private fun AddDueDateGroups(
        ContainerRef: ViewGroup,
        EntryList: List<DueEntry>,
        IsMuted: Boolean = false
    ) {
        val GroupedMap = EntryList.groupBy { EntryItem -> EntryItem.DateText }
        val OrderedKeys = GroupedMap.keys.sortedBy { DateText ->
            RenewalDueProjection.ParseDate(RawText = DateText)?.toEpochDay() ?: Long.MAX_VALUE
        }

        for (DateText in OrderedKeys) {
            val GroupList = GroupedMap[DateText].orEmpty()
            val GroupBinding = PartialDueDateGroupBinding.inflate(
                layoutInflater,
                ContainerRef,
                false
            )
            GroupBinding.tvDueGroupDate.text = DateText.ifEmpty {
                getString(R.string.due_group_no_date)
            }
            GroupBinding.tvDueGroupPolicies.text = getString(
                R.string.due_group_policies,
                GroupList.size
            )

            if (IsMuted) {
                val MutedColor = ContextCompat.getColor(
                    ContainerRef.context,
                    R.color.text_secondary
                )
                GroupBinding.dueDateGroupHead.setBackgroundResource(
                    R.drawable.bg_due_group_head_muted
                )
                GroupBinding.tvDueGroupDate.setTextColor(MutedColor)
                GroupBinding.tvDueGroupPolicies.setTextColor(MutedColor)
                GroupBinding.ivDueGroupIcon.imageTintList =
                    android.content.res.ColorStateList.valueOf(MutedColor)
            }

            for (EntryItem in GroupList) {
                val RowBinding = PartialDueDateRowBinding.inflate(
                    layoutInflater,
                    GroupBinding.dueDateGroupBody,
                    false
                )
                RowBinding.tvDueRowPolicy.text = EntryItem.PolicyNumber
                RowBinding.tvDueRowName.text = EntryItem.HolderName.ifEmpty {
                    getString(R.string.status_unknown)
                }
                RowBinding.tvDueRowNote.text = EntryItem.NoteText
                RowBinding.tvDueRowNote.visibility = if (EntryItem.NoteText.isEmpty()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
                GroupBinding.dueDateGroupBody.addView(RowBinding.root)
            }

            ContainerRef.addView(GroupBinding.root)
        }
    }

    private fun SkipReasonText(ReasonVal: RenewalDueProjection.SkipReason): Int = when (ReasonVal) {
        RenewalDueProjection.SkipReason.ALREADY_CURRENT -> R.string.due_reason_current
        RenewalDueProjection.SkipReason.NO_FREQUENCY -> R.string.due_reason_frequency
        RenewalDueProjection.SkipReason.NO_RENEWAL_ROW -> R.string.due_reason_norow
    }

    private fun CommitDueDates(
        OutcomeObj: RenewalDueProjection.Outcome,
        RenewalSessionId: String,
        RenewalCount: Int
    ) {
        val ContextRef = context?.applicationContext ?: return
        if (SelectedSessionId.isEmpty() || OutcomeObj.Changes.isEmpty()) return

        PolicyRepository.SaveFieldChanges(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.POLICY,
            SessionId = SelectedSessionId,
            Changes = OutcomeObj.Changes,
            SourceName = ChangeSource.DUE_IMPORT
        )
        PolicyRepository.SaveCustomerPolicies(
            ContextRef = ContextRef,
            Policies = OutcomeObj.Policies,
            SessionId = SelectedSessionId
        )
        PolicyRepository.SaveDueDateReport(
            ContextRef = ContextRef,
            SessionId = SelectedSessionId,
            ReportObj = BuildDueDateReport(
                OutcomeObj = OutcomeObj,
                RenewalSessionId = RenewalSessionId
            )
        )
        LoadSessionRecords()
        RenderList()

        CaptureDiagnostics.LogForSession(
            ContextObj = ContextRef,
            SessionId = SelectedSessionId,
            EventName = "DUE_DATE_IMPORT",
            MessageText = "source=$RenewalSessionId renewals=$RenewalCount " +
                    "matched=${OutcomeObj.MatchedCount} anchored=${OutcomeObj.AnchoredCount} " +
                    "updated=${OutcomeObj.UpdatedCount} current=${OutcomeObj.UnchangedCount} " +
                    "skipped=${OutcomeObj.SkippedCount}"
        )

        ShowSnack(
            MessageVal = getString(
                R.string.due_result_format,
                OutcomeObj.UpdatedCount,
                OutcomeObj.UnchangedCount,
                OutcomeObj.SkippedCount
            ),
            KindVal = AppToast.Kind.Success
        )
    }

    private fun BuildDueDateReport(
        OutcomeObj: RenewalDueProjection.Outcome,
        RenewalSessionId: String
    ): DueDateReport {
        return DueDateReport(
            SavedAt = System.currentTimeMillis(),
            SourceSessionId = RenewalSessionId,
            UpdatedCount = OutcomeObj.UpdatedCount,
            UnchangedCount = OutcomeObj.UnchangedCount,
            SkippedCount = OutcomeObj.SkippedCount,
            Updates = OutcomeObj.Updates.map { UpdateItem ->
                DueDateReportEntry(
                    PolicyNumber = UpdateItem.PolicyNumber,
                    HolderName = UpdateItem.HolderName,
                    PlanCode = UpdateItem.PlanCode,
                    OldDate = UpdateItem.OldDate,
                    NewDate = UpdateItem.NewDate,
                    PaidForDate = UpdateItem.PaidForDate,
                    Frequency = UpdateItem.Frequency
                )
            },
            Skips = OutcomeObj.Skips.map { SkipItem ->
                DueDateReportEntry(
                    PolicyNumber = SkipItem.PolicyNumber,
                    HolderName = SkipItem.HolderName,
                    PlanCode = SkipItem.PlanCode,
                    OldDate = SkipItem.CurrentDate,
                    ReasonName = SkipItem.Reason.name
                )
            }
        )
    }

    private fun HasRecordedChanges(): Boolean {
        val ContextRef = context?.applicationContext ?: return false
        if (SelectedSessionId.isEmpty()) return false
        val HasChanges = PolicyRepository.GetFieldChanges(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.POLICY,
            SessionId = SelectedSessionId
        ).isNotEmpty()
        if (HasChanges) return true
        val ReportObj = PolicyRepository.GetDueDateReport(
            ContextRef = ContextRef,
            SessionId = SelectedSessionId
        )
        return ReportObj != null && ReportObj.Skips.isNotEmpty()
    }

    private fun OpenChanges() {
        if (SelectedSessionId.isEmpty()) return
        val IntentObj = Intent(requireContext(), ChangesActivity::class.java)
        IntentObj.putExtra(ChangesActivity.EXTRA_SESSION_ID, SelectedSessionId)
        startActivity(IntentObj)
    }

    private fun ShowSnack(
        MessageVal: String,
        KindVal: AppToast.Kind = AppToast.Kind.Info
    ) {
        AppToast.Show(ContextRef = context, MessageText = MessageVal, KindVal = KindVal)
    }

    private fun HasAnySessionAction(): Boolean = SelectedSessionId.isNotEmpty()

    private fun RenderList(ResetScroll: Boolean = false) {
        when {
            SelectedSessionId.isEmpty() -> RenderSessions()
            SelectedSessionMode == CaptureMode.FUP -> RenderRenewals()
            else -> RenderPolicies()
        }
        if (ResetScroll) ViewBindingObj?.rvPolicies?.scrollToPosition(0)
    }

    private fun BindListAdapter(TargetAdapter: RecyclerView.Adapter<*>) {
        val BindingObj = ViewBindingObj ?: return
        if (BindingObj.rvPolicies.adapter !== TargetAdapter) {
            BindingObj.rvPolicies.adapter = TargetAdapter
        }
        ApplyRowDivider(WantsDivider = TargetAdapter !== RenewalAdapterObj)
        ApplyStickyHeader(WantsSticky = TargetAdapter === SessionAdapterObj)
    }

    private fun ApplyStickyHeader(WantsSticky: Boolean) {
        val BindingObj = ViewBindingObj ?: return
        BindingObj.rvPolicies.removeItemDecoration(SessionStickyObj)
        BindingObj.rvPolicies.removeOnItemTouchListener(SessionStickyObj)
        if (!WantsSticky) {
            SessionStickyObj.Reset()
            return
        }
        BindingObj.rvPolicies.addItemDecoration(SessionStickyObj)
        BindingObj.rvPolicies.addOnItemTouchListener(SessionStickyObj)
    }

    private fun ApplyRowDivider(WantsDivider: Boolean) {
        val BindingObj = ViewBindingObj ?: return
        val DividerObj = RowDividerObj ?: return
        BindingObj.rvPolicies.removeItemDecoration(DividerObj)
        if (WantsDivider) BindingObj.rvPolicies.addItemDecoration(DividerObj)
    }


    private fun RenderRenewals() {
        val BindingObj = ViewBindingObj ?: return
        BuildRenewalStatusChips()
        val VisibleList = VisibleRenewals()
        BindListAdapter(TargetAdapter = RenewalAdapterObj)
        RenewalAdapterObj.UpdateData(NewRenewals = VisibleList)
        BindingObj.tvPoliciesHeading.setText(R.string.sessions_renewals_heading)
        BindingObj.btnSessionsBack.visibility = View.VISIBLE
        BindingObj.policyTools.visibility = View.VISIBLE
        BindingObj.scrollStatus.visibility = View.VISIBLE
        BindingObj.tilSearch.hint = getString(R.string.renewals_search_hint)
        SessionBackCallback?.isEnabled = true

        BindingObj.tvPolicyCount.text = getString(
            R.string.renewals_count_format, VisibleList.size, AllRenewals.size
        )
        BindingObj.tvListSummary.text = RenewalSummaryText(VisibleList = VisibleList)
        BindingObj.tvListSummary.visibility =
            if (AllRenewals.isEmpty()) View.GONE else View.VISIBLE

        val HasVisible = VisibleList.isNotEmpty()
        BindingObj.emptyState.emptyStateRoot.visibility =
            if (HasVisible) View.GONE else View.VISIBLE
        ShowPdfAction = false
        BindingObj.exportBar.visibility =
            if (HasAnySessionAction()) View.VISIBLE else View.GONE
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
        BindListAdapter(TargetAdapter = SessionAdapterObj)
        SessionAdapterObj.UpdateData(NewSessions = SessionList)
        BindingObj.tvPoliciesHeading.setText(R.string.sessions_heading)
        BindingObj.tvPolicyCount.text = getString(R.string.sessions_count_format, SessionList.size)
        BindingObj.btnSessionsBack.visibility = View.GONE
        BindingObj.policyTools.visibility = View.GONE
        BindingObj.tvListSummary.visibility = View.GONE
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
        BuildPolicyStatusChips()
        val VisibleList = VisiblePolicies()
        BindListAdapter(TargetAdapter = AdapterObj)
        AdapterObj.UpdateData(NewPolicies = VisibleList)
        BindingObj.tvPoliciesHeading.setText(R.string.sessions_policies_heading)
        BindingObj.btnSessionsBack.visibility = View.VISIBLE
        BindingObj.policyTools.visibility = View.VISIBLE
        BindingObj.scrollStatus.visibility = View.VISIBLE
        BindingObj.tvListSummary.visibility = View.GONE
        ShowPdfAction = true
        BindingObj.tilSearch.hint = getString(R.string.policies_search_hint)
        SessionBackCallback?.isEnabled = true

        BindingObj.tvPolicyCount.text = getString(
            R.string.policies_count_format, VisibleList.size, AllPolicies.size
        )

        val HasAny = AllPolicies.isNotEmpty()
        val HasVisible = VisibleList.isNotEmpty()

        BindingObj.emptyState.emptyStateRoot.visibility =
            if (HasVisible) View.GONE else View.VISIBLE
        BindingObj.exportBar.visibility =
            if (HasAnySessionAction()) View.VISIBLE else View.GONE

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
        val ShowRenewals = SettingsStore.IsRenewalHistoryVisible(ContextRef = requireContext())
        SessionList = PolicyRepository.GetSessionHistory(ContextRef = requireContext())
            .filter { SessionRef ->
                SessionRef.Mode == CaptureMode.POLICY ||
                        (SessionRef.Mode == CaptureMode.FUP && ShowRenewals)
            }
            .sortedByDescending { SessionRef -> SessionRef.SavedAt }
    }

    private fun StatusFilterKey(StatusText: String): String {
        return when (StatusText) {
            PolicyStatusRules.IN_FORCE -> FILTER_INFORCE
            PolicyStatusRules.GRACE -> FILTER_GRACE
            PolicyStatusRules.OUTSTANDING -> FILTER_OUTSTANDING
            PolicyStatusRules.LAPSED -> FILTER_LAPSED
            PolicyStatusRules.PAID_UP -> FILTER_PAID_UP
            PolicyStatusRules.REDUCED_PAID_UP -> FILTER_REDUCED_PAID_UP
            else -> FILTER_UNKNOWN
        }
    }

    private fun StatusFilterLabel(FilterKey: String): Int {
        return when (FilterKey) {
            FILTER_INFORCE -> R.string.policies_filter_inforce
            FILTER_GRACE -> R.string.policies_filter_grace
            FILTER_OUTSTANDING -> R.string.policies_filter_outstanding
            FILTER_LAPSED -> R.string.policies_filter_lapsed
            FILTER_PAID_UP -> R.string.policies_filter_paidup
            FILTER_REDUCED_PAID_UP -> R.string.policies_filter_rpu
            else -> R.string.policies_filter_unknown
        }
    }

    private fun BuildPolicyStatusChips() {
        val Counts = LinkedHashMap<String, Int>()
        for (FilterKey in POLICY_FILTER_ORDER) Counts[FilterKey] = 0
        for (PolicyItem in AllPolicies) {
            val FilterKey = StatusFilterKey(StatusText = PolicyItem.NormalizedStatus)
            Counts[FilterKey] = (Counts[FilterKey] ?: 0) + 1
        }

        val ChipSpecs = mutableListOf<Pair<String, String>>()
        ChipSpecs.add(
            FILTER_ALL to getString(
                R.string.policies_filter_count_format,
                getString(R.string.policies_filter_all),
                AllPolicies.size
            )
        )
        for (FilterKey in POLICY_FILTER_ORDER) {
            val CountValue = Counts[FilterKey] ?: 0
            if (CountValue == 0) continue
            ChipSpecs.add(
                FilterKey to getString(
                    R.string.policies_filter_count_format,
                    getString(StatusFilterLabel(FilterKey = FilterKey)),
                    CountValue
                )
            )
        }
        ApplyStatusChips(ChipSpecs = ChipSpecs)
    }

    private fun BuildRenewalStatusChips() {
        ApplyStatusChips(
            ChipSpecs = listOf(
                FILTER_ALL to getString(R.string.renewals_filter_all),
                FILTER_INFORCE to getString(R.string.renewals_filter_ontime),
                FILTER_LAPSED to getString(R.string.renewals_filter_grace)
            )
        )
    }

    private fun ApplyStatusChips(ChipSpecs: List<Pair<String, String>>) {
        val BindingObj = ViewBindingObj ?: return
        val GroupRef = BindingObj.chipGroupStatus

        val CurrentSignature = ChipSpecs.joinToString("|") { SpecItem ->
            "${SpecItem.first}=${SpecItem.second}"
        }
        val SelectedKey = if (ChipSpecs.any { SpecItem -> SpecItem.first == StatusFilter }) {
            StatusFilter
        } else {
            FILTER_ALL
        }
        if (SelectedKey != StatusFilter) StatusFilter = SelectedKey
        if (CurrentSignature == RenderedChipSignature) {
            SyncChipSelection(GroupRef = GroupRef, SelectedKey = SelectedKey)
            return
        }

        SuppressChipCallback = true
        GroupRef.removeAllViews()
        for (SpecItem in ChipSpecs) {
            val ChipRef = layoutInflater.inflate(
                R.layout.item_filter_chip, GroupRef, false
            ) as Chip
            ChipRef.id = View.generateViewId()
            ChipRef.tag = SpecItem.first
            ChipRef.text = SpecItem.second
            ChipRef.isCheckable = true
            ChipRef.isClickable = true
            ChipRef.isChecked = SpecItem.first == SelectedKey
            GroupRef.addView(ChipRef)
        }
        RenderedChipSignature = CurrentSignature
        SuppressChipCallback = false
    }

    private fun SyncChipSelection(GroupRef: ChipGroup, SelectedKey: String) {
        SuppressChipCallback = true
        for (ChildIdx in 0 until GroupRef.childCount) {
            val ChipRef = GroupRef.getChildAt(ChildIdx) as? Chip ?: continue
            ChipRef.isChecked = ChipRef.tag as? String == SelectedKey
        }
        SuppressChipCallback = false
    }

    private fun OpenSession(SessionRef: PolicyRepository.CaptureSessionReference) {
        SelectedSessionId = SessionRef.SessionId
        SelectedSessionMode = SessionRef.Mode
        SearchQuery = ""
        StatusFilter = FILTER_ALL
        ViewBindingObj?.etSearch?.setText("")
        LoadSessionRecords()
        RenderList(ResetScroll = true)
    }


    private fun ResumeSession(SessionRef: PolicyRepository.CaptureSessionReference) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return

        if (SessionRef.Mode == CaptureMode.POLICY) {
            val SheetBinding = SheetPolicyCaptureModeBinding.inflate(layoutInflater)
            val SheetDialog = BottomSheetDialog(ActivityRef)
            SheetDialog.setContentView(SheetBinding.root)
            val ResumePages = BindResumeFromPageRow(
                SheetBinding = SheetBinding,
                SessionRef = SessionRef
            )
            SheetBinding.cardFastCapture.setOnClickListener {
                SheetDialog.dismiss()
                LaunchResume(
                    SessionRef = SessionRef,
                    CapturePolicyDetails = false,
                    ResumeFromPage = SelectedResumePage(
                        SheetBinding = SheetBinding,
                        ResumePage = ResumePages.first
                    )
                )
            }
            SheetBinding.cardFullCapture.setOnClickListener {
                SheetDialog.dismiss()
                LaunchResume(
                    SessionRef = SessionRef,
                    CapturePolicyDetails = true,
                    ResumeFromPage = SelectedResumePage(
                        SheetBinding = SheetBinding,
                        ResumePage = ResumePages.second
                    )
                )
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

    private fun BindResumeFromPageRow(
        SheetBinding: SheetPolicyCaptureModeBinding,
        SessionRef: PolicyRepository.CaptureSessionReference
    ): Pair<Int, Int> {
        val ContextRef = context ?: return Pair(0, 0)
        val FastMark = PolicyRepository.GetPolicyResumeMark(
            ContextRef = ContextRef,
            SessionId = SessionRef.SessionId,
            TrackVal = PolicyResumeTrack.POLICY_FAST
        )
        val FullMark = PolicyRepository.GetPolicyResumeMark(
            ContextRef = ContextRef,
            SessionId = SessionRef.SessionId,
            TrackVal = PolicyResumeTrack.POLICY_FULL
        )
        val FastChoice = PolicyResumeTarget.ChooseMark(
            TrackVal = PolicyResumeTrack.POLICY_FAST,
            FastMark = FastMark,
            FullMark = FullMark,
            CustomerMark = null,
            StoredRecordCount = SessionRef.RecordCount
        )
        val FullChoice = PolicyResumeTarget.ChooseMark(
            TrackVal = PolicyResumeTrack.POLICY_FULL,
            FastMark = FastMark,
            FullMark = FullMark,
            CustomerMark = null,
            StoredRecordCount = SessionRef.RecordCount
        )

        RenderResumeCardNotes(
            SheetBinding = SheetBinding,
            FastChoice = FastChoice,
            FullChoice = FullChoice,
            FastMark = FastMark,
            FullMark = FullMark,
            ShowResumePage = true
        )

        val LatestMark = listOfNotNull(FastChoice, FullChoice)
            .maxByOrNull { MarkItem -> MarkItem.SavedAt }
        if (LatestMark == null) {
            SheetBinding.resumeFromPageGroup.visibility = View.GONE
            return Pair(0, 0)
        }

        val DateFormatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        SheetBinding.tvResumeFromPageMeta.text = getString(
            R.string.policy_resume_checkbox_meta,
            DateFormatter.format(Date(LatestMark.SavedAt)),
            CaptureMode.POLICY.DescribeCount(CountVal = LatestMark.CapturedCount)
        )
        SheetBinding.cbResumeFromPage.isChecked = true
        SheetBinding.cbResumeFromPage.setOnCheckedChangeListener { _, IsCheckedVal ->
            RenderResumeCardNotes(
                SheetBinding = SheetBinding,
                FastChoice = FastChoice,
                FullChoice = FullChoice,
                FastMark = FastMark,
                FullMark = FullMark,
                ShowResumePage = IsCheckedVal
            )
        }
        SheetBinding.resumeFromPageGroup.setOnClickListener { CardViewRef ->
            HapticFeedback.Tap(ViewRef = CardViewRef)
            SheetBinding.cbResumeFromPage.isChecked = !SheetBinding.cbResumeFromPage.isChecked
        }
        SheetBinding.resumeFromPageGroup.visibility = View.VISIBLE
        return Pair(
            FastChoice?.LastCompletedPage ?: 0,
            FullChoice?.LastCompletedPage ?: 0
        )
    }

    private fun RenderResumeCardNotes(
        SheetBinding: SheetPolicyCaptureModeBinding,
        FastChoice: PolicyResumeMark?,
        FullChoice: PolicyResumeMark?,
        FastMark: PolicyResumeMark?,
        FullMark: PolicyResumeMark?,
        ShowResumePage: Boolean
    ) {
        BindResumeCardNote(
            NoteView = SheetBinding.tvFastResumeNote,
            ChosenMark = if (ShowResumePage) FastChoice else null,
            TrackCompleteVal = PolicyResumeTarget.IsTrackComplete(
                TrackVal = PolicyResumeTrack.POLICY_FAST,
                FastMark = FastMark,
                FullMark = FullMark,
                CustomerMark = null
            )
        )
        BindResumeCardNote(
            NoteView = SheetBinding.tvFullResumeNote,
            ChosenMark = if (ShowResumePage) FullChoice else null,
            TrackCompleteVal = PolicyResumeTarget.IsTrackComplete(
                TrackVal = PolicyResumeTrack.POLICY_FULL,
                FastMark = FastMark,
                FullMark = FullMark,
                CustomerMark = null
            )
        )
    }

    private fun BindResumeCardNote(
        NoteView: android.widget.TextView,
        ChosenMark: PolicyResumeMark?,
        TrackCompleteVal: Boolean
    ) {
        when {
            ChosenMark != null -> {
                NoteView.text = getString(
                    R.string.policy_resume_card_note,
                    ChosenMark.LastCompletedPage,
                    ChosenMark.TotalPages
                )
                NoteView.visibility = View.VISIBLE
            }

            TrackCompleteVal -> {
                NoteView.setText(R.string.policy_resume_card_complete)
                NoteView.visibility = View.VISIBLE
            }

            else -> NoteView.visibility = View.GONE
        }
    }

    private fun SelectedResumePage(
        SheetBinding: SheetPolicyCaptureModeBinding,
        ResumePage: Int
    ): Int {
        if (ResumePage <= 0) return 0
        return if (SheetBinding.cbResumeFromPage.isChecked) ResumePage else 0
    }

    private fun LaunchResume(
        SessionRef: PolicyRepository.CaptureSessionReference,
        CapturePolicyDetails: Boolean,
        ResumeFromPage: Int = 0
    ) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.root)
        CaptureFlow.Start(
            ActivityRef = ActivityRef,
            ModeVal = SessionRef.Mode,
            LaunchTarget = true,
            CapturePolicyDetails = CapturePolicyDetails,
            ResumeSessionId = SessionRef.SessionId,
            ResumeFromPage = ResumeFromPage
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

        ShowSnack(
            MessageVal = getString(R.string.sessions_deleted),
            KindVal = AppToast.Kind.Success
        )
    }

    private fun ShowSessions() {
        SessionAdapterObj.CloseOpenRow()
        SelectedSessionId = ""
        SelectedSessionMode = CaptureMode.POLICY
        AllPolicies = emptyList()
        AllRenewals = emptyList()
        LoadSessions()
        RenderList(ResetScroll = true)
    }

    private fun ShowSessionActionsSheet() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        val SheetBinding = SheetSessionActionsBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)
        SheetDialog.setOnShowListener {
            SheetDialog.behavior.skipCollapsed = true
            SheetDialog.behavior.isDraggable = false
            SheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        val IsPolicySession = SelectedSessionId.isNotEmpty() &&
                SelectedSessionMode == CaptureMode.POLICY
        val ShowUpload = SessionUploader.IsEnabled() && IsUploadableSession()

        val SessionRef = SessionList.firstOrNull { ItemRef ->
            ItemRef.SessionId == SelectedSessionId
        }
        val HasLogs = SelectedSessionId.isNotEmpty() && CaptureDiagnostics.HasSessionLogs(
            ContextObj = requireContext().applicationContext,
            SessionId = SelectedSessionId
        )

        SheetBinding.rowActionPersonal.visibility =
            if (IsPolicySession) View.VISIBLE else View.GONE
        SheetBinding.rowActionDueDates.visibility =
            if (IsPolicySession) View.VISIBLE else View.GONE
        SheetBinding.rowActionUpload.visibility = if (ShowUpload) View.VISIBLE else View.GONE
        SheetBinding.rowActionChanges.visibility =
            if (IsPolicySession && HasRecordedChanges()) View.VISIBLE else View.GONE
        val ShowExports = SettingsStore.IsSessionExportVisible(ContextRef = requireContext())
        SheetBinding.rowActionExcel.visibility = if (ShowExports) View.VISIBLE else View.GONE
        SheetBinding.rowActionPdf.visibility =
            if (ShowExports && ShowPdfAction) View.VISIBLE else View.GONE
        SheetBinding.rowActionShareLog.visibility =
            if (HasLogs && SessionRef != null) View.VISIBLE else View.GONE
        SheetBinding.rowActionDelete.visibility =
            if (SessionRef != null) View.VISIBLE else View.GONE
        SheetBinding.tvActionPersonalDesc.text = getString(
            R.string.action_personal_desc,
            AllPolicies.size
        )
        SheetBinding.tvActionDeleteDesc.text = getString(
            R.string.action_delete_desc,
            SelectedSessionMode.DescribeCount(
                CountVal = SessionRef?.RecordCount ?: AllPolicies.size
            )
        )

        SheetBinding.labelActionsServer.visibility = SheetBinding.rowActionUpload.visibility
        SheetBinding.labelActionsAdd.visibility = SectionVisibility(
            RowViews = listOf(SheetBinding.rowActionPersonal, SheetBinding.rowActionDueDates)
        )
        SheetBinding.labelActionsReview.visibility = SectionVisibility(
            RowViews = listOf(
                SheetBinding.rowActionChanges,
                SheetBinding.rowActionExcel,
                SheetBinding.rowActionPdf
            )
        )
        SheetBinding.labelActionsSession.visibility = SectionVisibility(
            RowViews = listOf(SheetBinding.rowActionShareLog, SheetBinding.rowActionDelete)
        )

        SheetBinding.rowActionPersonal.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            CapturePersonalDetails()
        }
        SheetBinding.rowActionDueDates.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            PickRenewalSessionForDueDates()
        }
        SheetBinding.rowActionUpload.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            UploadSession()
        }
        SheetBinding.rowActionChanges.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            OpenChanges()
        }
        SheetBinding.rowActionExcel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            ExportExcel()
        }
        SheetBinding.rowActionPdf.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            ExportPdf()
        }
        SheetBinding.rowActionShareLog.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            if (SessionRef != null) ShareSessionLog(SessionRef = SessionRef)
        }
        SheetBinding.rowActionDelete.setOnClickListener { ViewRef ->
            HapticFeedback.Reject(ViewRef = ViewRef)
            SheetDialog.dismiss()
            if (SessionRef != null) ConfirmDeleteSession(SessionRef = SessionRef)
        }
        SheetBinding.btnActionsCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun SectionVisibility(RowViews: List<View>): Int {
        val AnyVisible = RowViews.any { RowItem -> RowItem.visibility == View.VISIBLE }
        return if (AnyVisible) View.VISIBLE else View.GONE
    }

    private fun IsUploadableSession(): Boolean =
        SelectedSessionId.isNotEmpty() && SelectedSessionMode == CaptureMode.POLICY

    private fun UploadSession() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        if (!IsUploadableSession()) return

        val AppContext = requireContext().applicationContext
        val SavedCode = PolicyRepository.GetAgencyCode(
            ContextRef = AppContext,
            SessionId = SelectedSessionId
        )
        PromptAgencyCode(
            ActivityRef = ActivityRef,
            SavedCode = SavedCode,
            BodyText = getString(R.string.export_agency_upload_body),
            ConfirmText = getString(R.string.export_agency_upload_confirm),
            ConfirmFormatRes = R.string.export_agency_upload_confirm_format
        ) { EnteredCode ->
            PolicyRepository.StampSessionAgencyCode(
                ContextRef = AppContext,
                SessionId = SelectedSessionId,
                AgencyCodeText = EnteredCode
            )
            RunUpload(ActivityRef = ActivityRef, AgencyCodeVal = EnteredCode)
        }
    }

    private fun RunUpload(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        AgencyCodeVal: String
    ) {
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnSessionActions)
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
                SetPhase(PhaseIndex = 4)
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
        SetPhase(PhaseIndex = UploadPhaseIndex, ActiveVal = false)
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

    private fun SetPhase(PhaseIndex: Int, ActiveVal: Boolean = true) {
        val SheetBinding = UploadSheetBinding ?: return
        UploadPhaseIndex = PhaseIndex
        PaintPhase(
            IconRef = SheetBinding.ivPhasePack,
            LabelRef = SheetBinding.tvPhasePack,
            StateVal = PhaseIndex - 0,
            ActiveVal = ActiveVal
        )
        PaintPhase(
            IconRef = SheetBinding.ivPhaseSend,
            LabelRef = SheetBinding.tvPhaseSend,
            StateVal = PhaseIndex - 1,
            ActiveVal = ActiveVal
        )
        PaintPhase(
            IconRef = SheetBinding.ivPhaseWait,
            LabelRef = SheetBinding.tvPhaseWait,
            StateVal = PhaseIndex - 2,
            ActiveVal = ActiveVal
        )
        PaintPhase(
            IconRef = SheetBinding.ivPhaseDone,
            LabelRef = SheetBinding.tvPhaseDone,
            StateVal = PhaseIndex - 3,
            ActiveVal = ActiveVal
        )
    }

    private fun PaintPhase(
        IconRef: android.widget.ImageView,
        LabelRef: android.widget.TextView,
        StateVal: Int,
        ActiveVal: Boolean
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

            StateVal == 0 && ActiveVal -> {
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
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnSessionActions)
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
        PromptAgencyCode(
            ActivityRef = ActivityRef,
            SavedCode = SavedCode,
            BodyText = getString(R.string.export_agency_body),
            ConfirmText = getString(R.string.export_agency_confirm),
            ConfirmFormatRes = R.string.export_agency_export_confirm_format
        ) { EnteredCode ->
            PolicyRepository.StampSessionAgencyCode(
                ContextRef = AppContext,
                SessionId = SelectedSessionId,
                AgencyCodeText = EnteredCode
            )
            RunExcelExport(AgencyCodeVal = EnteredCode)
        }
    }

    private fun PromptAgencyCode(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        SavedCode: String,
        BodyText: String,
        ConfirmText: String,
        ConfirmFormatRes: Int,
        OnConfirm: (String) -> Unit
    ) {
        val AppContext = requireContext().applicationContext
        val CodeList = PolicyRepository.ListAgencyCodes(ContextRef = AppContext)
        if (CodeList.isEmpty()) {
            ShowAgencyCodeSheet(
                ActivityRef = ActivityRef,
                SavedCode = SavedCode,
                BodyText = BodyText,
                ConfirmText = ConfirmText
            ) { EnteredCode ->
                PolicyRepository.AddAgencyCode(
                    ContextRef = AppContext,
                    AgencyCodeText = EnteredCode,
                    MakeDefault = true
                )
                OnConfirm(EnteredCode)
            }
            return
        }
        ShowAgencyPickerSheet(
            ActivityRef = ActivityRef,
            CodeList = CodeList,
            SavedCode = SavedCode,
            BodyText = BodyText,
            ConfirmText = ConfirmText,
            ConfirmFormatRes = ConfirmFormatRes,
            OnConfirm = OnConfirm
        )
    }

    private fun ShowAgencyPickerSheet(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        CodeList: List<PolicyRepository.AgencyCode>,
        SavedCode: String,
        BodyText: String,
        ConfirmText: String,
        ConfirmFormatRes: Int,
        OnConfirm: (String) -> Unit
    ) {
        val AppContext = requireContext().applicationContext
        val SheetBinding = SheetSettingsDetailBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)

        SheetBinding.tvDetailTitle.setText(R.string.export_agency_pick_title)
        SheetBinding.tvDetailBody.visibility = View.VISIBLE
        SheetBinding.tvDetailBody.text = BodyText

        val DefaultCode = PolicyRepository.GetDefaultAgencyCode(ContextRef = AppContext)
        var SelectedCode = CodeList
            .firstOrNull { Entry -> Entry.CodeText.equals(SavedCode, ignoreCase = true) }
            ?.CodeText
            ?: CodeList.first().CodeText

        val RowBindings = mutableListOf<Pair<String, PartialSettingsChoiceRowBinding>>()

        fun PaintSelection() {
            for (RowPair in RowBindings) {
                RowPair.second.rbChoice.isChecked =
                    RowPair.first.equals(SelectedCode, ignoreCase = true)
            }
            SheetBinding.btnDetailPrimary.text = getString(ConfirmFormatRes, SelectedCode)
        }

        for (Entry in CodeList) {
            val ChoiceBinding = PartialSettingsChoiceRowBinding.inflate(
                layoutInflater, SheetBinding.detailContainer, false
            )
            ChoiceBinding.tvChoiceTitle.text = Entry.CodeText

            val MetaParts = mutableListOf<String>()
            if (Entry.CodeText.equals(DefaultCode, ignoreCase = true)) {
                MetaParts.add(getString(R.string.settings_agency_default_badge))
            }
            if (Entry.LabelText.isNotEmpty()) MetaParts.add(Entry.LabelText)
            val MetaText = MetaParts.joinToString(separator = " · ")

            if (MetaText.isEmpty()) {
                ChoiceBinding.tvChoiceDesc.visibility = View.GONE
            } else {
                ChoiceBinding.tvChoiceDesc.visibility = View.VISIBLE
                ChoiceBinding.tvChoiceDesc.text = MetaText
            }

            ChoiceBinding.choiceRow.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SelectedCode = Entry.CodeText
                PaintSelection()
            }

            RowBindings.add(Entry.CodeText to ChoiceBinding)
            SheetBinding.detailContainer.addView(ChoiceBinding.root)
        }

        SheetBinding.btnDetailPrimary.visibility = View.VISIBLE
        SheetBinding.btnDetailPrimary.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            SheetDialog.dismiss()
            OnConfirm(SelectedCode)
        }

        SheetBinding.btnDetailSecondary.visibility = View.VISIBLE
        SheetBinding.btnDetailSecondary.setText(R.string.export_agency_pick_add)
        SheetBinding.btnDetailSecondary.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            ShowAgencyCodeSheet(
                ActivityRef = ActivityRef,
                SavedCode = "",
                BodyText = BodyText,
                ConfirmText = ConfirmText
            ) { EnteredCode ->
                PolicyRepository.AddAgencyCode(
                    ContextRef = AppContext,
                    AgencyCodeText = EnteredCode
                )
                OnConfirm(EnteredCode)
            }
        }

        SheetBinding.btnDetailClose.setText(R.string.action_cancel)
        SheetBinding.btnDetailClose.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }

        PaintSelection()
        SheetDialog.show()
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
            HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnSessionActions)
            ExcelExporter.ExportFupPolicies(
                ContextRef = requireContext(),
                Policies = VisibleList,
                AgencyCode = AgencyCodeVal
            )
        } else {
            val VisibleList = VisiblePolicies()
            if (VisibleList.isEmpty()) return
            HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnSessionActions)
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
            ShowSnack(
                MessageVal = getString(R.string.capture_customer_no_sessions),
                KindVal = AppToast.Kind.Warning
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
        val ContextRef = requireContext().applicationContext
        val ShareIntent = CaptureDiagnostics.BuildShareIntent(
            ContextObj = ContextRef,
            LogFiles = CaptureDiagnostics.GetSessionLogFiles(
                ContextObj = ContextRef,
                SessionId = SessionRef.SessionId
            )
        )
        if (ShareIntent == null) {
            ShowSnack(
                MessageVal = getString(R.string.sessions_share_log_missing),
                KindVal = AppToast.Kind.Warning
            )
            return
        }
        startActivity(
            Intent.createChooser(ShareIntent, getString(R.string.sessions_share_log_title))
        )
    }

    private fun ShareFile(FileRef: File, MimeType: String) {
        val FileUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", FileRef
        )
        val ShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MimeType
            putExtra(Intent.EXTRA_STREAM, FileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(ShareIntent, getString(R.string.exports_share)))
        ShowSnack(MessageVal = FileRef.name, KindVal = AppToast.Kind.Success)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        UploadDialogObj?.dismiss()
        UploadDialogObj = null
        UploadSheetBinding = null
        SessionStickyObj.Reset()
        ViewBindingObj = null
        SessionBackCallback = null
        RenderedChipSignature = ""
        SuppressChipCallback = false
    }

    private data class DueEntry(
        val PolicyNumber: String,
        val HolderName: String,
        val DateText: String,
        val NoteText: String
    )

    companion object {
        private const val FILTER_ALL = "all"
        private const val FILTER_INFORCE = "inforce"
        private const val FILTER_GRACE = "grace"
        private const val FILTER_OUTSTANDING = "outstanding"
        private const val FILTER_LAPSED = "lapsed"
        private const val FILTER_PAID_UP = "paidup"
        private const val FILTER_REDUCED_PAID_UP = "reducedpaidup"
        private const val FILTER_UNKNOWN = "unknown"

        private val POLICY_FILTER_ORDER = listOf(
            FILTER_INFORCE,
            FILTER_GRACE,
            FILTER_OUTSTANDING,
            FILTER_LAPSED,
            FILTER_PAID_UP,
            FILTER_REDUCED_PAID_UP,
            FILTER_UNKNOWN
        )
    }

    private var SessionBackCallback: OnBackPressedCallback? = null
    private var SuppressChipCallback = false
    private var RenderedChipSignature = ""
}
