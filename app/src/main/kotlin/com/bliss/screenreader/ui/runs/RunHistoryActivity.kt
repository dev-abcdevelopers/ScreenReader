@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.runs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.RunCounter
import com.bliss.screenreader.data.model.RunOutcome
import com.bliss.screenreader.data.model.RunSummary
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityRunHistoryBinding
import com.bliss.screenreader.databinding.PartialRunEntryBinding
import com.bliss.screenreader.databinding.PartialRunFactRowBinding
import com.bliss.screenreader.databinding.PartialRunGroupBinding
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.utils.HapticFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunHistoryActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityRunHistoryBinding

    private var SessionIdVal: String = ""
    private var RunList: List<RunSummary> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityRunHistoryBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        SessionIdVal = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()

        ViewBindingObj.btnClearRuns.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ConfirmClearRuns()
        }

        LoadRuns()
        RenderRuns()
    }

    private fun ConfirmClearRuns() {
        if (RunList.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.runs_clear_title)
            .setMessage(R.string.runs_clear_body)
            .setPositiveButton(R.string.runs_clear_confirm) { _, _ -> ClearRuns() }
            .setNegativeButton(R.string.runs_clear_cancel, null)
            .show()
    }

    private fun ClearRuns() {
        HapticFeedback.Reject(ViewRef = ViewBindingObj.root)
        PolicyRepository.ClearRunSummaries(
            ContextRef = applicationContext,
            SessionId = SessionIdVal
        )
        LoadRuns()
        RenderRuns()
    }

    private fun LoadRuns() {
        RunList = PolicyRepository.GetRunSummaries(
            ContextRef = this,
            SessionId = SessionIdVal
        ).sortedByDescending { RunItem -> RunItem.StartedAt }

        ViewBindingObj.tvRunsSummary.text = if (RunList.size == 1) {
            getString(R.string.runs_summary_one)
        } else {
            getString(R.string.runs_summary_format, RunList.size)
        }
    }

    private fun RenderRuns() {
        val ContainerRef = ViewBindingObj.runsContainer
        ContainerRef.removeAllViews()

        for (RunItem in RunList) AddRun(ContainerRef = ContainerRef, RunItem = RunItem)

        val HasContent = RunList.isNotEmpty()
        ViewBindingObj.btnClearRuns.visibility = if (HasContent) View.VISIBLE else View.GONE
        ViewBindingObj.runsScroll.visibility = if (HasContent) View.VISIBLE else View.GONE
        ViewBindingObj.tvRunsSummary.visibility = if (HasContent) View.VISIBLE else View.GONE
        ViewBindingObj.emptyState.emptyStateRoot.visibility =
            if (HasContent) View.GONE else View.VISIBLE
        if (HasContent) return

        ViewBindingObj.emptyState.btnEmptyAction.visibility = View.GONE
        ViewBindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_inbox_empty)
        ViewBindingObj.emptyState.tvEmptyTitle.setText(R.string.runs_empty_title)
        ViewBindingObj.emptyState.tvEmptyBody.setText(R.string.runs_empty_body)
    }

    private fun AddRun(ContainerRef: ViewGroup, RunItem: RunSummary) {
        val RunBinding = PartialRunEntryBinding.inflate(layoutInflater, ContainerRef, false)
        RunBinding.tvRunEntryTitle.text = getString(
            R.string.runs_entry_title_format,
            OutcomeLabel(OutcomeVal = RunItem.Outcome),
            StampText(TimeVal = RunItem.StartedAt)
        )
        RunBinding.tvRunEntryMeta.text = listOf(
            ModeLabel(RunItem = RunItem),
            DurationText(RunItem = RunItem),
            CollectedShort(RunItem = RunItem)
        ).filter { PartText -> PartText.isNotEmpty() }.joinToString(separator = " · ")
        RunBinding.ivRunEntryIcon.setImageResource(OutcomeIconRes(OutcomeVal = RunItem.Outcome))

        AddReport(ContainerRef = RunBinding.runEntryBody, RunItem = RunItem)

        RunBinding.runEntryHeader.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            val WillShow = RunBinding.runEntryBody.visibility != View.VISIBLE
            RunBinding.runEntryBody.visibility = if (WillShow) View.VISIBLE else View.GONE
            RunBinding.ivRunEntryChevron.rotation = if (WillShow) 90f else 0f
        }
        ContainerRef.addView(RunBinding.root)
    }

    private fun AddReport(ContainerRef: ViewGroup, RunItem: RunSummary) {
        val FactGroup = PartialRunGroupBinding.inflate(layoutInflater, ContainerRef, false)
        FactGroup.tvRunGroupTitle.setText(R.string.runs_group_facts)
        AddFact(
            ContainerRef = FactGroup.runGroupBody,
            LabelRes = R.string.runs_fact_ran_for,
            ValueText = RanForText(RunItem = RunItem)
        )
        AddFact(
            ContainerRef = FactGroup.runGroupBody,
            LabelRes = R.string.runs_fact_picked_up,
            ValueText = PickedUpText(RunItem = RunItem)
        )
        if (RunItem.TotalPages > 0) {
            AddFact(
                ContainerRef = FactGroup.runGroupBody,
                LabelRes = R.string.runs_fact_got_through,
                ValueText = getString(
                    R.string.runs_value_pages,
                    RunItem.FirstPage,
                    RunItem.LastPage,
                    RunItem.TotalPages
                )
            )
        }
        AddFact(
            ContainerRef = FactGroup.runGroupBody,
            LabelRes = R.string.runs_fact_collected,
            ValueText = CollectedText(RunItem = RunItem)
        )
        AddFact(
            ContainerRef = FactGroup.runGroupBody,
            LabelRes = R.string.runs_fact_saved,
            ValueText = SavedText(RunItem = RunItem)
        )
        ContainerRef.addView(FactGroup.root)

        if (RunItem.StopReason.isNotEmpty()) {
            val StopGroup = PartialRunGroupBinding.inflate(layoutInflater, ContainerRef, false)
            StopGroup.tvRunGroupTitle.setText(R.string.runs_group_why)
            AddLine(ContainerRef = StopGroup.runGroupBody, ValueText = RunItem.StopReason)
            ContainerRef.addView(StopGroup.root)
        }

        val AlongGroup = PartialRunGroupBinding.inflate(layoutInflater, ContainerRef, false)
        AlongGroup.tvRunGroupTitle.setText(R.string.runs_group_along)
        var CounterCount = 0
        for (KeyText in RunCounter.ORDER) {
            val CountVal = RunItem.Counters?.get(KeyText) ?: 0
            if (CountVal <= 0) continue
            AddFact(
                ContainerRef = AlongGroup.runGroupBody,
                LabelRes = CounterLabelRes(KeyVal = KeyText),
                ValueText = CounterValueText(KeyVal = KeyText, CountVal = CountVal, RunItem = RunItem)
            )
            CounterCount++
        }
        if (CounterCount == 0) {
            AddLine(
                ContainerRef = AlongGroup.runGroupBody,
                ValueText = getString(R.string.runs_nothing_in_the_way)
            )
        }
        ContainerRef.addView(AlongGroup.root)
    }

    private fun AddFact(ContainerRef: ViewGroup, LabelRes: Int, ValueText: String) {
        val FactBinding = PartialRunFactRowBinding.inflate(layoutInflater, ContainerRef, false)
        FactBinding.tvRunFactLabel.setText(LabelRes)
        FactBinding.tvRunFactValue.text = ValueText
        ContainerRef.addView(FactBinding.root)
    }

    private fun AddLine(ContainerRef: ViewGroup, ValueText: String) {
        val FactBinding = PartialRunFactRowBinding.inflate(layoutInflater, ContainerRef, false)
        FactBinding.tvRunFactLabel.visibility = View.GONE
        FactBinding.tvRunFactValue.text = ValueText
        ContainerRef.addView(FactBinding.root)
    }

    private fun OutcomeLabel(OutcomeVal: String): String = getString(
        when (OutcomeVal) {
            RunOutcome.FINISHED -> R.string.runs_outcome_finished
            RunOutcome.STOPPED -> R.string.runs_outcome_stopped
            RunOutcome.USER_STOPPED -> R.string.runs_outcome_user_stopped
            RunOutcome.DISCARDED -> R.string.runs_outcome_discarded
            else -> R.string.runs_outcome_unfinished
        }
    )

    private fun OutcomeIconRes(OutcomeVal: String): Int = when (OutcomeVal) {
        RunOutcome.FINISHED -> R.drawable.ic_check_circle
        RunOutcome.DISCARDED -> R.drawable.ic_delete
        RunOutcome.STOPPED -> R.drawable.ic_alert
        else -> R.drawable.ic_history
    }

    private fun ModeLabel(RunItem: RunSummary): String {
        val BaseRes = when (RunItem.Mode.uppercase(Locale.US)) {
            "POLICY" -> if (RunItem.CaptureDetails) {
                R.string.runs_mode_policy_full
            } else {
                R.string.runs_mode_policy
            }

            "CUSTOMER" -> R.string.runs_mode_customer
            "FUP" -> R.string.runs_mode_renewal
            "RENEWAL_DUE" -> R.string.runs_mode_renewal_due
            else -> R.string.runs_mode_other
        }
        return getString(BaseRes)
    }

    private fun RanForText(RunItem: RunSummary): String = getString(
        R.string.runs_value_ran_for,
        DurationText(RunItem = RunItem),
        ClockText(TimeVal = RunItem.StartedAt),
        ClockText(TimeVal = RunItem.EndedAt)
    )

    private fun PickedUpText(RunItem: RunSummary): String = if (RunItem.Resumed) {
        getString(R.string.runs_value_resumed, RunItem.SeededCount)
    } else {
        getString(R.string.runs_value_fresh)
    }

    private fun CollectedText(RunItem: RunSummary): String {
        if (RunItem.Mode.equals("CUSTOMER", ignoreCase = true)) {
            return getString(
                R.string.runs_value_collected_customer,
                RunItem.CollectedCount,
                RunItem.FilledCount
            )
        }
        return getString(R.string.runs_value_collected_policy, RunItem.CollectedCount)
    }

    private fun CollectedShort(RunItem: RunSummary): String {
        if (RunItem.Mode.equals("CUSTOMER", ignoreCase = true)) {
            if (RunItem.CollectedCount <= 0) return ""
            return getString(R.string.runs_short_customers, RunItem.CollectedCount)
        }
        if (RunItem.CollectedCount <= 0) return ""
        return getString(R.string.runs_short_policies, RunItem.CollectedCount)
    }

    private fun SavedText(RunItem: RunSummary): String {
        if (RunItem.Outcome == RunOutcome.DISCARDED) return getString(R.string.runs_value_discarded)
        if (!RunItem.Committed) return getString(R.string.runs_value_not_saved)
        if (RunItem.SavedAdded <= 0 && RunItem.SavedUpdated <= 0) {
            return getString(R.string.runs_value_saved_none)
        }
        return getString(R.string.runs_value_saved, RunItem.SavedAdded, RunItem.SavedUpdated)
    }

    private fun CounterLabelRes(KeyVal: String): Int = when (KeyVal) {
        RunCounter.WRONG_PAGE -> R.string.runs_counter_wrong_page
        RunCounter.PAGE_SELECT_FAILED -> R.string.runs_counter_page_failed
        RunCounter.ASKED_YOU_FOR_PAGE -> R.string.runs_counter_asked_you
        RunCounter.YOU_TURNED_PAGE -> R.string.runs_counter_you_turned
        RunCounter.RESTARTED_AT_ONE -> R.string.runs_counter_restarted
        RunCounter.RECOVERED -> R.string.runs_counter_recovered
        RunCounter.APP_ERROR -> R.string.runs_counter_app_error
        RunCounter.CARDS_SKIPPED -> R.string.runs_counter_cards_skipped
        RunCounter.OFFLINE -> R.string.runs_counter_offline
        else -> R.string.runs_counter_paused
    }

    private fun CounterValueText(KeyVal: String, CountVal: Int, RunItem: RunSummary): String {
        if (KeyVal == RunCounter.PAUSED && RunItem.PausedMs > 0L) {
            return getString(
                R.string.runs_value_paused,
                CountVal,
                SpanText(MillisVal = RunItem.PausedMs)
            )
        }
        return CountVal.toString()
    }

    private fun DurationText(RunItem: RunSummary): String {
        val SpanMs = (RunItem.EndedAt - RunItem.StartedAt).coerceAtLeast(0L)
        return SpanText(MillisVal = SpanMs)
    }

    private fun SpanText(MillisVal: Long): String {
        val TotalSeconds = MillisVal / 1000L
        val MinutesVal = TotalSeconds / 60L
        val SecondsVal = TotalSeconds % 60L
        if (MinutesVal <= 0L) return getString(R.string.runs_span_seconds, SecondsVal)
        if (MinutesVal < 60L) return getString(R.string.runs_span_minutes, MinutesVal, SecondsVal)
        return getString(R.string.runs_span_hours, MinutesVal / 60L, MinutesVal % 60L)
    }

    private fun StampText(TimeVal: Long): String =
        StampFormat.format(Date(TimeVal))

    private fun ClockText(TimeVal: Long): String =
        ClockFormat.format(Date(TimeVal))

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"

        private val StampFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        private val ClockFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    }
}
