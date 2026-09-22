@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.service

import android.content.Context
import com.bliss.screenreader.data.model.RunCounter
import com.bliss.screenreader.data.model.RunOutcome
import com.bliss.screenreader.data.model.RunSummary
import com.bliss.screenreader.data.repository.PolicyRepository

object RunSummaryTally {

    private val StateLock = Any()
    private val FieldRegex = Regex("([A-Za-z_]+)=([^\\s]+)")

    private var SessionIdVal: String = ""
    private var ModeVal: String = ""
    private var StartedAtVal: Long = 0L
    private var EndedAtVal: Long = 0L
    private var ResumedVal: Boolean = false
    private var DetailsVal: Boolean = false
    private var SeededVal: Int = 0
    private var FirstPageVal: Int = 0
    private var LastPageVal: Int = 0
    private var TotalPagesVal: Int = 0
    private var CollectedVal: Int = 0
    private var FilledVal: Int = 0
    private var SavedAddedVal: Int = 0
    private var SavedUpdatedVal: Int = 0
    private var StopReasonVal: String = ""
    private var PausedAtVal: Long = 0L
    private var PausedTotalVal: Long = 0L
    private var AutomationStartCount: Int = 0
    private var IsOpen: Boolean = false
    private var CompletedCleanly: Boolean = false

    private val CounterMap = linkedMapOf<String, Int>()

    fun Note(ContextObj: Context, EventName: String, MessageText: String) {
        synchronized(StateLock) {
            val FieldMap = ReadFields(MessageText = MessageText)
            when (EventName) {
                "SESSION_START" -> {
                    if (IsOpen) Persist(ContextObj = ContextObj, OutcomeVal = RunOutcome.UNFINISHED)
                    Begin(FieldMap = FieldMap)
                }

                "SESSION_RESUME" -> {
                    SeededVal = maxOf(
                        FieldMap["seeded"]?.toIntOrNull() ?: 0,
                        FieldMap["scopePolicies"]?.toIntOrNull() ?: 0
                    )
                }

                "SESSION_STATE" -> NotePause(MessageText = MessageText)

                "SESSION_FINISH" -> {
                    EndedAtVal = System.currentTimeMillis()
                    CollectedVal = maxOf(
                        CollectedVal,
                        FieldMap["policies"]?.toIntOrNull() ?: 0
                    )
                    Persist(ContextObj = ContextObj, OutcomeVal = DecideOutcome())
                }

                "SESSION_COMMIT" -> {
                    SavedAddedVal = FieldMap["added"]?.toIntOrNull() ?: 0
                    SavedUpdatedVal = FieldMap["updated"]?.toIntOrNull() ?: 0
                    Persist(ContextObj = ContextObj, OutcomeVal = DecideOutcome())
                    Close()
                }

                "SESSION_DISCARD" -> {
                    EndedAtVal = System.currentTimeMillis()
                    Persist(ContextObj = ContextObj, OutcomeVal = RunOutcome.DISCARDED)
                    Close()
                }

                "POLICY_AUTOMATION_START" -> {
                    AutomationStartCount++
                    val PageVal = FieldMap["page"]?.toIntOrNull() ?: 0
                    if (AutomationStartCount > 1 && PageVal <= 0) Bump(KeyVal = RunCounter.RESTARTED_AT_ONE)
                }

                "POLICY_PAGE_LOADED", "POLICY_PAGE_DETECTED" -> NotePage(FieldMap = FieldMap)
                "CUSTOMER_PAGE_LOADED" -> NoteCustomerPage(MessageText = MessageText)
                "POLICY_PAGE_ADOPTED" -> Bump(KeyVal = RunCounter.YOU_TURNED_PAGE)
                "POLICIES_CAPTURED" -> {
                    CollectedVal = maxOf(
                        CollectedVal,
                        FieldMap["uniqueTotal"]?.toIntOrNull() ?: 0
                    )
                }

                "POLICY_AUTOMATION_COMPLETE" -> CompletedCleanly = true

                "CUSTOMER_AUTOMATION_COMPLETE" -> {
                    CollectedVal = maxOf(CollectedVal, FieldMap["customers"]?.toIntOrNull() ?: 0)
                    FilledVal = maxOf(FilledVal, FieldMap["policiesFilled"]?.toIntOrNull() ?: 0)
                    val ReasonText = MessageText.substringAfter("reason=", "")
                        .substringBefore(" customers=")
                        .trim()
                    if (ReasonText.contains("limit reached", ignoreCase = true)) {
                        StopReasonVal = ReasonText
                    } else {
                        CompletedCleanly = true
                    }
                }

                "WARNING/CUSTOMER_PAGE_RETRY" -> Bump(KeyVal = RunCounter.WRONG_PAGE)
                "WARNING/POLICY_NAVIGATION_RETRY" -> Bump(KeyVal = RunCounter.PAGE_SELECT_FAILED)
                "WARNING/POLICY_MANUAL_PAGE_WAIT" -> Bump(KeyVal = RunCounter.ASKED_YOU_FOR_PAGE)
                "WARNING/ERROR_SHEET_GIVEUP" -> Bump(KeyVal = RunCounter.APP_ERROR)
                "WARNING/POLICY_DETAIL_SKIPPED" -> Bump(KeyVal = RunCounter.CARDS_SKIPPED)
                "WARNING/OFFLINE_WAIT" -> Bump(KeyVal = RunCounter.OFFLINE)

                "WARNING/POLICY_AUTOMATION_RECOVERY" -> {
                    Bump(KeyVal = RunCounter.RECOVERED)
                    StopReasonVal = MessageText.substringAfter("reason=[", "")
                        .substringBefore("]")
                        .trim()
                }

                "WARNING/CUSTOMER_AUTOMATION_RECOVERY" -> Bump(KeyVal = RunCounter.RECOVERED)
            }
        }
    }

    private fun Begin(FieldMap: Map<String, String>) {
        SessionIdVal = FieldMap["session"].orEmpty()
        ModeVal = FieldMap["mode"].orEmpty()
        StartedAtVal = System.currentTimeMillis()
        EndedAtVal = 0L
        ResumedVal = FieldMap["resumed"] == "true"
        DetailsVal = FieldMap["capturePolicyDetails"] == "true"
        SeededVal = 0
        FirstPageVal = 0
        LastPageVal = 0
        TotalPagesVal = 0
        CollectedVal = 0
        FilledVal = 0
        SavedAddedVal = 0
        SavedUpdatedVal = 0
        StopReasonVal = ""
        PausedAtVal = 0L
        PausedTotalVal = 0L
        AutomationStartCount = 0
        CompletedCleanly = false
        CounterMap.clear()
        IsOpen = true
    }

    private fun Close() {
        IsOpen = false
        SessionIdVal = ""
    }

    private fun NotePause(MessageText: String) {
        if (MessageText.contains("paused", ignoreCase = true)) {
            PausedAtVal = System.currentTimeMillis()
            Bump(KeyVal = RunCounter.PAUSED)
            return
        }
        if (PausedAtVal > 0L) {
            PausedTotalVal += System.currentTimeMillis() - PausedAtVal
            PausedAtVal = 0L
        }
    }

    private fun NotePage(FieldMap: Map<String, String>) {
        val PageVal = FieldMap["page"]?.toIntOrNull() ?: return
        if (PageVal <= 0) return
        if (FirstPageVal == 0 || PageVal < FirstPageVal) FirstPageVal = PageVal
        if (PageVal > LastPageVal) LastPageVal = PageVal
        val TotalVal = FieldMap["total"]?.toIntOrNull() ?: 0
        if (TotalVal > TotalPagesVal) TotalPagesVal = TotalVal
    }

    private fun NoteCustomerPage(MessageText: String) {
        val PageText = MessageText.substringAfter("page=", "").trim()
        val PageVal = PageText.substringBefore('/').toIntOrNull() ?: return
        val TotalVal = PageText.substringAfter('/', "").toIntOrNull() ?: 0
        NotePage(
            FieldMap = mapOf(
                "page" to PageVal.toString(),
                "total" to TotalVal.toString()
            )
        )
    }

    private fun DecideOutcome(): String = when {
        StopReasonVal.isNotEmpty() -> RunOutcome.STOPPED
        CompletedCleanly -> RunOutcome.FINISHED
        else -> RunOutcome.USER_STOPPED
    }

    private fun Bump(KeyVal: String) {
        if (!IsOpen) return
        CounterMap[KeyVal] = (CounterMap[KeyVal] ?: 0) + 1
    }

    private fun ReadFields(MessageText: String): Map<String, String> {
        val OutMap = linkedMapOf<String, String>()
        for (MatchObj in FieldRegex.findAll(MessageText)) {
            val KeyText = MatchObj.groupValues[1]
            if (!OutMap.containsKey(KeyText)) OutMap[KeyText] = MatchObj.groupValues[2]
        }
        return OutMap
    }

    private fun Persist(ContextObj: Context, OutcomeVal: String) {
        if (!IsOpen || SessionIdVal.isBlank()) return
        if (PausedAtVal > 0L) {
            PausedTotalVal += System.currentTimeMillis() - PausedAtVal
            PausedAtVal = 0L
        }
        PolicyRepository.SaveRunSummary(
            ContextRef = ContextObj.applicationContext,
            SummaryObj = RunSummary(
                SessionId = SessionIdVal,
                Mode = ModeVal,
                StartedAt = StartedAtVal,
                EndedAt = if (EndedAtVal > 0L) EndedAtVal else System.currentTimeMillis(),
                Outcome = OutcomeVal,
                Resumed = ResumedVal,
                CaptureDetails = DetailsVal,
                SeededCount = SeededVal,
                FirstPage = FirstPageVal,
                LastPage = LastPageVal,
                TotalPages = TotalPagesVal,
                CollectedCount = CollectedVal,
                FilledCount = FilledVal,
                SavedAdded = SavedAddedVal,
                SavedUpdated = SavedUpdatedVal,
                StopReason = StopReasonVal,
                PausedMs = PausedTotalVal,
                Counters = if (CounterMap.isEmpty()) null else LinkedHashMap(CounterMap)
            )
        )
    }
}
