@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

data class RunSummary(
    val SessionId: String = "",
    val Mode: String = "",
    val StartedAt: Long = 0L,
    val EndedAt: Long = 0L,
    val Outcome: String = RunOutcome.UNFINISHED,
    val Resumed: Boolean = false,
    val CaptureDetails: Boolean = false,
    val SeededCount: Int = 0,
    val FirstPage: Int = 0,
    val LastPage: Int = 0,
    val TotalPages: Int = 0,
    val CollectedCount: Int = 0,
    val FilledCount: Int = 0,
    val SavedAdded: Int = 0,
    val SavedUpdated: Int = 0,
    val StopReason: String = "",
    val Committed: Boolean = false,
    val PausedMs: Long = 0L,
    val Counters: Map<String, Int>? = null
)

object RunOutcome {
    const val FINISHED = "finished"
    const val STOPPED = "stopped"
    const val USER_STOPPED = "user_stopped"
    const val DISCARDED = "discarded"
    const val UNFINISHED = "unfinished"
}

object RunCounter {
    const val WRONG_PAGE = "wrong_page"
    const val PAGE_SELECT_FAILED = "page_select_failed"
    const val ASKED_YOU_FOR_PAGE = "asked_you_for_page"
    const val YOU_TURNED_PAGE = "you_turned_page"
    const val RESTARTED_AT_ONE = "restarted_at_one"
    const val RECOVERED = "recovered"
    const val APP_ERROR = "app_error"
    const val CARDS_SKIPPED = "cards_skipped"
    const val OFFLINE = "offline"
    const val PAUSED = "paused"

    val ORDER = listOf(
        WRONG_PAGE,
        PAGE_SELECT_FAILED,
        ASKED_YOU_FOR_PAGE,
        YOU_TURNED_PAGE,
        RESTARTED_AT_ONE,
        RECOVERED,
        APP_ERROR,
        CARDS_SKIPPED,
        OFFLINE,
        PAUSED
    )
}
