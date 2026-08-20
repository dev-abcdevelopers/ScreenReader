@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.changes

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.ChangeSource
import com.bliss.screenreader.data.model.DueDateReport
import com.bliss.screenreader.data.model.DueDateReportEntry
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.parser.RenewalDueProjection
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityChangesBinding
import com.bliss.screenreader.databinding.PartialChangeFieldGroupBinding
import com.bliss.screenreader.databinding.PartialChangeRunBinding
import com.bliss.screenreader.databinding.PartialChangeValueRowBinding
import com.bliss.screenreader.databinding.PartialDueChipBinding
import com.bliss.screenreader.databinding.PartialDueDateGroupBinding
import com.bliss.screenreader.databinding.PartialDueDateRowBinding
import com.bliss.screenreader.databinding.PartialDueGroupBinding
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.utils.HapticFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChangesActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityChangesBinding

    private var SessionIdVal: String = ""
    private var RunList: List<ChangeRun> = emptyList()
    private var ReportObj: DueDateReport? = null
    private var NameMap: Map<String, String> = emptyMap()
    private var FilterVal: String = FILTER_ALL

    private data class ChangeRun(
        val SourceName: String,
        val ChangedAt: Long,
        val Changes: List<RecordFieldChange>,
        val Skips: List<DueDateReportEntry>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityChangesBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        SessionIdVal = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()

        ViewBindingObj.chipGroupChanges.setOnCheckedStateChangeListener { _, CheckedIds ->
            FilterVal = when (CheckedIds.firstOrNull()) {
                R.id.chipChangeDue -> FILTER_DUE
                R.id.chipChangeOther -> FILTER_OTHER
                R.id.chipChangeSkipped -> FILTER_SKIPPED
                else -> FILTER_ALL
            }
            RenderRuns()
        }

        ViewBindingObj.btnShareChanges.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ShareRuns()
        }

        LoadRuns()
        RenderRuns()
    }

    private fun LoadRuns() {
        NameMap = PolicyRepository.GetCustomerPolicies(
            ContextRef = this,
            SessionId = SessionIdVal
        ).associate { PolicyItem -> PolicyItem.PolicyNumber to PolicyItem.HolderName }

        val ChangeList = PolicyRepository.GetFieldChanges(
            ContextRef = this,
            ModeVal = CaptureMode.POLICY,
            SessionId = SessionIdVal
        )
        ReportObj = PolicyRepository.GetDueDateReport(
            ContextRef = this,
            SessionId = SessionIdVal
        )

        val GroupedMap = ChangeList.groupBy { ChangeItem ->
            val SourceText = ChangeItem.SourceName.orEmpty()
            if (SourceText.isEmpty()) {
                "" to 0L
            } else {
                SourceText to ChangeItem.ChangedAt
            }
        }
        val NewestImportAt = GroupedMap.keys
            .filter { KeyPair -> KeyPair.first == ChangeSource.DUE_IMPORT }
            .maxOfOrNull { KeyPair -> KeyPair.second }

        RunList = GroupedMap
            .map { EntryRef ->
                val CarriesSkips = EntryRef.key.first == ChangeSource.DUE_IMPORT &&
                        EntryRef.key.second == NewestImportAt
                ChangeRun(
                    SourceName = EntryRef.key.first,
                    ChangedAt = EntryRef.key.second,
                    Changes = EntryRef.value,
                    Skips = if (CarriesSkips) ReportObj?.Skips.orEmpty() else emptyList()
                )
            }
            .sortedByDescending { RunItem -> RunItem.ChangedAt }

        ViewBindingObj.tvChangesSummary.text = getString(
            R.string.changes_summary_format,
            ChangeList.size,
            ReportObj?.Skips?.size ?: 0
        )
    }

    private fun VisibleChanges(RunItem: ChangeRun): List<RecordFieldChange> = when (FilterVal) {
        FILTER_DUE -> RunItem.Changes.filter { ChangeItem -> IsDueDate(ChangeItem = ChangeItem) }
        FILTER_OTHER -> RunItem.Changes.filter { ChangeItem -> !IsDueDate(ChangeItem = ChangeItem) }
        FILTER_SKIPPED -> emptyList()
        else -> RunItem.Changes
    }

    private fun VisibleSkips(RunItem: ChangeRun): List<DueDateReportEntry> = when (FilterVal) {
        FILTER_ALL, FILTER_SKIPPED -> RunItem.Skips
        else -> emptyList()
    }

    private fun IsDueDate(ChangeItem: RecordFieldChange): Boolean = ChangeItem.FieldName.equals(
        RenewalDueProjection.FIELD_NAME,
        ignoreCase = true
    )

    private fun RenderRuns() {
        val ContainerRef = ViewBindingObj.changesContainer
        ContainerRef.removeAllViews()

        var RenderedCount = 0
        for (RunItem in RunList) {
            val ChangeList = VisibleChanges(RunItem = RunItem)
            val SkipList = VisibleSkips(RunItem = RunItem)
            if (ChangeList.isEmpty() && SkipList.isEmpty()) continue
            AddRun(
                ContainerRef = ContainerRef,
                RunItem = RunItem,
                ChangeList = ChangeList,
                SkipList = SkipList
            )
            RenderedCount++
        }

        val HasContent = RenderedCount > 0
        ViewBindingObj.changesScroll.visibility = if (HasContent) View.VISIBLE else View.GONE
        ViewBindingObj.emptyState.emptyStateRoot.visibility =
            if (HasContent) View.GONE else View.VISIBLE
        if (HasContent) return

        ViewBindingObj.emptyState.btnEmptyAction.visibility = View.GONE
        if (RunList.isEmpty()) {
            ViewBindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_inbox_empty)
            ViewBindingObj.emptyState.tvEmptyTitle.setText(R.string.changes_empty_title)
            ViewBindingObj.emptyState.tvEmptyBody.setText(R.string.changes_empty_body)
        } else {
            ViewBindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_search)
            ViewBindingObj.emptyState.tvEmptyTitle.setText(R.string.changes_no_match_title)
            ViewBindingObj.emptyState.tvEmptyBody.setText(R.string.changes_no_match_body)
        }
    }

    private fun AddRun(
        ContainerRef: ViewGroup,
        RunItem: ChangeRun,
        ChangeList: List<RecordFieldChange>,
        SkipList: List<DueDateReportEntry>
    ) {
        val RunBinding = PartialChangeRunBinding.inflate(layoutInflater, ContainerRef, false)
        RunBinding.tvChangeRunTitle.setText(RunTitleRes(SourceName = RunItem.SourceName))
        RunBinding.ivChangeRunIcon.setImageResource(RunIconRes(SourceName = RunItem.SourceName))
        RunBinding.tvChangeRunMeta.text = RunMetaText(
            RunItem = RunItem,
            ChangeCount = ChangeList.size,
            SkipCount = SkipList.size
        )

        val DueList = ChangeList.filter { ChangeItem -> IsDueDate(ChangeItem = ChangeItem) }
        val OtherList = ChangeList.filterNot { ChangeItem -> IsDueDate(ChangeItem = ChangeItem) }

        AddDueDateGroups(ContainerRef = RunBinding.changeRunBody, ChangeList = DueList)
        AddFieldGroups(ContainerRef = RunBinding.changeRunBody, ChangeList = OtherList)
        AddSkipGroups(ContainerRef = RunBinding.changeRunBody, SkipList = SkipList)

        RunBinding.changeRunHeader.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            val WillShow = RunBinding.changeRunBody.visibility != View.VISIBLE
            RunBinding.changeRunBody.visibility = if (WillShow) View.VISIBLE else View.GONE
            RunBinding.ivChangeRunChevron.rotation = if (WillShow) 90f else 0f
        }

        ContainerRef.addView(RunBinding.root)
    }

    private fun AddDueDateGroups(ContainerRef: ViewGroup, ChangeList: List<RecordFieldChange>) {
        if (ChangeList.isEmpty()) return
        val GroupedMap = ChangeList.groupBy { ChangeItem -> ChangeItem.NewValue }
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
            GroupBinding.tvDueGroupDate.text = DateText
            GroupBinding.tvDueGroupPolicies.text = getString(
                R.string.due_group_policies,
                GroupList.size
            )
            for ((RecordKey, _, OldValue) in GroupList) {
                val RowBinding = PartialDueDateRowBinding.inflate(
                    layoutInflater,
                    GroupBinding.dueDateGroupBody,
                    false
                )
                RowBinding.tvDueRowPolicy.text = RecordKey
                RowBinding.tvDueRowName.text = HolderNameOf(PolicyNumber = RecordKey)
                RowBinding.tvDueRowNote.text = if (OldValue.isEmpty()) {
                    getString(R.string.due_row_new)
                } else {
                    getString(R.string.changes_was_format, OldValue)
                }
                GroupBinding.dueDateGroupBody.addView(RowBinding.root)
            }
            ContainerRef.addView(GroupBinding.root)
        }
    }

    private fun AddFieldGroups(ContainerRef: ViewGroup, ChangeList: List<RecordFieldChange>) {
        if (ChangeList.isEmpty()) return
        val GroupedMap = ChangeList.groupBy { ChangeItem -> ChangeItem.FieldName }

        for ((FieldName, GroupList) in GroupedMap) {
            val GroupBinding = PartialChangeFieldGroupBinding.inflate(
                layoutInflater,
                ContainerRef,
                false
            )
            GroupBinding.tvChangeFieldName.text = FieldName
            GroupBinding.tvChangeFieldCount.text = GroupList.size.toString()
            for ((RecordKey, _, OldValue, NewValue) in GroupList) {
                val RowBinding = PartialChangeValueRowBinding.inflate(
                    layoutInflater,
                    GroupBinding.changeFieldBody,
                    false
                )
                RowBinding.tvChangeValuePolicy.text = RecordKey
                RowBinding.tvChangeValueName.text =
                    HolderNameOf(PolicyNumber = RecordKey)
                RowBinding.tvChangeValueNew.text = NewValue
                RowBinding.tvChangeValueOld.text = if (OldValue.isEmpty()) {
                    getString(R.string.due_row_new)
                } else {
                    getString(R.string.changes_was_format, OldValue)
                }
                GroupBinding.changeFieldBody.addView(RowBinding.root)
            }
            ContainerRef.addView(GroupBinding.root)
        }
    }

    private fun AddSkipGroups(ContainerRef: ViewGroup, SkipList: List<DueDateReportEntry>) {
        if (SkipList.isEmpty()) return
        val GroupedMap = SkipList.groupBy { SkipItem -> SkipItem.ReasonName }

        for ((ReasonName, GroupList) in GroupedMap) {
            val GroupBinding = PartialDueGroupBinding.inflate(layoutInflater, ContainerRef, false)
            GroupBinding.tvDueGroupTitle.setText(ReasonTitleRes(ReasonName = ReasonName))
            GroupBinding.tvDueGroupCount.text = GroupList.size.toString()
            GroupBinding.tvDueGroupWhy.setText(ReasonBodyRes(ReasonName = ReasonName))
            GroupBinding.dueGroupDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(
                        ContainerRef.context,
                        R.color.status_amber_text
                    )
                )

            AddSkipChips(GroupBinding = GroupBinding, SkipList = GroupList)
            GroupBinding.dueGroupHeader.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                val WillShow = GroupBinding.dueGroupBody.visibility != View.VISIBLE
                GroupBinding.dueGroupBody.visibility = if (WillShow) View.VISIBLE else View.GONE
                GroupBinding.ivDueGroupChevron.rotation = if (WillShow) 90f else 0f
            }
            ContainerRef.addView(GroupBinding.root)
        }
    }

    private fun AddSkipChips(
        GroupBinding: PartialDueGroupBinding,
        SkipList: List<DueDateReportEntry>
    ) {
        GroupBinding.dueGroupChips.removeAllViews()
        val VisibleList = SkipList.take(CHIP_PREVIEW_LIMIT)
        for ((PolicyNumber) in VisibleList) {
            AddChip(GroupBinding = GroupBinding, LabelText = PolicyNumber, IsMore = false)
        }
        val HiddenCount = SkipList.size - VisibleList.size
        if (HiddenCount <= 0) return

        AddChip(
            GroupBinding = GroupBinding,
            LabelText = getString(R.string.due_group_more, HiddenCount),
            IsMore = true
        ) {
            GroupBinding.dueGroupChips.removeAllViews()
            for ((PolicyNumber) in SkipList) {
                AddChip(
                    GroupBinding = GroupBinding,
                    LabelText = PolicyNumber,
                    IsMore = false
                )
            }
        }
    }

    private fun AddChip(
        GroupBinding: PartialDueGroupBinding,
        LabelText: String,
        IsMore: Boolean,
        OnClick: (() -> Unit)? = null
    ) {
        val ChipBinding = PartialDueChipBinding.inflate(
            layoutInflater,
            GroupBinding.dueGroupChips,
            false
        )
        ChipBinding.tvDueChip.text = LabelText
        if (IsMore) {
            ChipBinding.tvDueChip.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    GroupBinding.root.context,
                    R.color.text_accent
                )
            )
            ChipBinding.tvDueChip.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                OnClick?.invoke()
            }
        }
        GroupBinding.dueGroupChips.addView(ChipBinding.root)
    }

    private fun HolderNameOf(PolicyNumber: String): String =
        NameMap[PolicyNumber].orEmpty().ifEmpty { getString(R.string.status_unknown) }

    private fun RunTitleRes(SourceName: String): Int = when (SourceName) {
        ChangeSource.DUE_IMPORT -> R.string.changes_run_due_import
        ChangeSource.POLICY_CAPTURE -> R.string.changes_run_policy_capture
        ChangeSource.PROFILE_CAPTURE -> R.string.changes_run_profile_capture
        ChangeSource.RENEWAL_CAPTURE -> R.string.changes_run_renewal_capture
        else -> R.string.changes_run_earlier
    }

    private fun RunIconRes(SourceName: String): Int = when (SourceName) {
        ChangeSource.DUE_IMPORT -> R.drawable.ic_calendar_repeat
        ChangeSource.PROFILE_CAPTURE -> R.drawable.ic_person
        ChangeSource.RENEWAL_CAPTURE -> R.drawable.ic_calendar_repeat
        ChangeSource.POLICY_CAPTURE -> R.drawable.ic_policy
        else -> R.drawable.ic_history
    }

    private fun RunMetaText(RunItem: ChangeRun, ChangeCount: Int, SkipCount: Int): String {
        if (RunItem.ChangedAt <= 0L) {
            return getString(R.string.changes_run_meta_undated, ChangeCount)
        }
        val StampText = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
            .format(Date(RunItem.ChangedAt))
        if (SkipCount <= 0) {
            return getString(R.string.changes_run_meta, StampText, ChangeCount)
        }
        return getString(R.string.changes_run_meta_skips, StampText, ChangeCount, SkipCount)
    }

    private fun ReasonTitleRes(ReasonName: String): Int = when (ReasonName) {
        RenewalDueProjection.SkipReason.ALREADY_CURRENT.name -> R.string.due_stat_updated
        RenewalDueProjection.SkipReason.NO_RENEWAL_ROW.name -> R.string.due_stat_norow
        else -> R.string.due_stat_frequency
    }

    private fun ReasonBodyRes(ReasonName: String): Int = when (ReasonName) {
        RenewalDueProjection.SkipReason.ALREADY_CURRENT.name -> R.string.due_reason_current
        RenewalDueProjection.SkipReason.NO_RENEWAL_ROW.name -> R.string.due_reason_norow
        else -> R.string.due_reason_frequency
    }

    private fun ShareRuns() {
        if (RunList.isEmpty()) return

        val BodyText = buildString {
            for (RunItem in RunList) {
                val ChangeList = VisibleChanges(RunItem = RunItem)
                val SkipList = VisibleSkips(RunItem = RunItem)
                if (ChangeList.isEmpty() && SkipList.isEmpty()) continue

                appendLine(getString(RunTitleRes(SourceName = RunItem.SourceName)))
                appendLine(
                    RunMetaText(
                        RunItem = RunItem,
                        ChangeCount = ChangeList.size,
                        SkipCount = SkipList.size
                    )
                )
                for ((RecordKey, FieldName, OldValue, NewValue) in ChangeList) {
                    val OldText = OldValue.ifEmpty { getString(R.string.due_not_set) }
                    append(RecordKey)
                    append(" · ")
                    append(HolderNameOf(PolicyNumber = RecordKey))
                    append(" · ")
                    append(FieldName)
                    appendLine(": $OldText → $NewValue")
                }
                for ((PolicyNumber, _, _, _, _, _, _, ReasonName) in SkipList) {
                    append(PolicyNumber)
                    append(" · ")
                    appendLine(getString(ReasonTitleRes(ReasonName = ReasonName)))
                }
                appendLine()
            }
        }
        if (BodyText.isBlank()) return

        val ShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, BodyText)
        }
        startActivity(Intent.createChooser(ShareIntent, getString(R.string.changes_share)))
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"

        private const val CHIP_PREVIEW_LIMIT = 8
        private const val FILTER_ALL = "all"
        private const val FILTER_DUE = "due"
        private const val FILTER_OTHER = "other"
        private const val FILTER_SKIPPED = "skipped"
    }
}
