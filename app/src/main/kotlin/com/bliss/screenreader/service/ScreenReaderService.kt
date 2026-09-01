@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SameParameterValue", "unused", "SpellCheckingInspection",
)

package com.bliss.screenreader.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.model.CustomerProfile
import com.bliss.screenreader.data.model.SessionGap
import com.bliss.screenreader.data.parser.CustomerProfileParser
import com.bliss.screenreader.data.parser.RenewalDateRange
import com.bliss.screenreader.data.parser.RenewalDueParser
import com.bliss.screenreader.data.parser.RenewalDueRange
import com.bliss.screenreader.data.parser.SheetOcrParser
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.data.model.PolicyResumeMark
import com.bliss.screenreader.data.model.RenewalDuePolicy
import com.bliss.screenreader.data.model.PolicyResumeTarget
import com.bliss.screenreader.data.model.PolicyResumeTrack
import com.bliss.screenreader.data.parser.CaptureParsers
import com.bliss.screenreader.data.parser.FupDataParser
import com.bliss.screenreader.data.parser.PlanIdentity
import com.bliss.screenreader.data.parser.PolicySearchParser
import com.bliss.screenreader.data.parser.RecordMerge
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.security.CredentialStore
import com.bliss.screenreader.settings.PaceProfile
import com.bliss.screenreader.settings.SettingsStore
import com.bliss.screenreader.ui.toast.AppToast
import com.bliss.screenreader.data.parser.ScreenDataParser
import com.bliss.screenreader.utils.AppLauncherUtils
import com.bliss.screenreader.utils.HapticFeedback
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import java.util.Locale
import kotlin.math.abs
import java.util.concurrent.Executors

@SuppressLint("AccessibilityPolicy")
class ScreenReaderService : AccessibilityService(), PolicySearchHost, CustomerSearchHost {

    companion object {
        private const val LOG_TAG = "ScreenReaderService"
        private const val TICK_INTERVAL_MS = 1000L
        private const val AUTO_SCROLL_START_DELAY_MS = 1500L
        private const val AUTO_SCROLL_SETTLE_MS = 1800L
        private const val AUTO_SCROLL_STALL_LIMIT = 2
        private const val POLICY_NAVIGATION_DELAY_MS = 350L
        private const val POLICY_SCROLL_SETTLE_MS = 700L
        private const val POLICY_PAGE_SELECTOR_DELAY_MS = 500L
        private const val POLICY_PAGE_LOAD_DELAY_MS = 1800L
        private const val POLICY_SMOOTH_SCROLL_DURATION_MS = 450L
        private const val POLICY_SMOOTH_SCROLL_START_RATIO = 0.78f
        private const val POLICY_SMOOTH_SCROLL_END_RATIO = 0.28f
        private const val POLICY_REVEAL_NUDGE_DURATION_MS = 280L
        private const val POLICY_DETAIL_OPEN_DELAY_MS = 900L
        private const val POLICY_DETAIL_EXPAND_DELAY_MS = 2900L
        private const val POLICY_DETAIL_SECTION_STEP_MS = 900L
        private const val POLICY_DETAIL_EXPAND_RETRY_SETTLE_MS = 800L
        private const val POLICY_SECTION_ATTEMPT_LIMIT = 6
        private const val POLICY_SECTION_RETRY_ROUND_LIMIT = 3
        private const val POLICY_SECTION_SCROLL_SETTLE_MS = 550L
        private const val POLICY_DETAIL_SWEEP_LIMIT = 10
        private const val POLICY_DETAIL_SWEEP_SETTLE_MS = 700L

        private const val POLICY_SECTION_VIEWPORT_TOP_RATIO = 0.16f
        private const val POLICY_SECTION_VIEWPORT_BOTTOM_RATIO = 0.72f
        private const val POLICY_SECTION_CHEVRON_X_RATIO = 0.86f
        private const val POLICY_DETAIL_RETURN_DELAY_MS = 900L
        private const val POLICY_DETAIL_SCROLL_LIMIT = 10
        private const val POLICY_DETAIL_RETURN_LIMIT = 3
        private const val POLICY_SCROLL_STALL_LIMIT = 2
        private const val POLICY_RETURN_TO_TOP_LIMIT = 20
        private const val POLICY_PAGE_RETRY_LIMIT = 3
        private const val POLICY_AUTOMATION_RECOVERY_LIMIT = 3
        private const val POLICY_SELECTOR_SCROLL_LIMIT = 12
        private const val POLICY_SELECTOR_SCROLL_SETTLE_MS = 650L
        private const val POLICY_SELECTOR_SCROLL_DURATION_MS = 420L
        private const val POLICY_SELECTOR_OPTION_MIN_COUNT = 4
        private const val POLICY_SELECTOR_COLUMN_MAX_WIDTH_RATIO = 0.4f
        private const val POLICY_SELECTOR_SCROLLABLE_MAX_WIDTH_RATIO = 0.6f
        private const val POLICY_RESUME_JUMP_LIMIT = 3
        private const val POLICY_RESUME_WAIT_LIMIT = 6
        private const val POLICY_JUMP_NONE = ""
        private const val POLICY_JUMP_DETAIL_RESTORE = "detail-restore"
        private const val POLICY_JUMP_RESUME = "resume"
        private const val PORTFOLIO_CLICK_RETRY_MS = 3000L
        private const val PORTFOLIO_TRANSITION_TIMEOUT_MS = 4000L
        private const val HOME_NAV_CLICK_RETRY_MS = 3000L
        private const val HOME_NAV_TRANSITION_TIMEOUT_MS = 4000L
        private const val HOME_NAV_TAB_Y_RATIO = 0.952f
        private const val HOME_BOTTOM_NAV_TOP_RATIO = 0.85f
        private const val HOME_BOTTOM_NAV_BAND_DP = 180f
        private const val HOME_NAV_REVEAL_RETRY_MS = 1200L
        private const val HOME_NAV_REVEAL_LIMIT = 4
        private const val HOME_NAV_BLIND_TAP_AFTER_ATTEMPTS = 4
        private const val HOME_CUSTOMERS_TAB_X_RATIO = 0.30f
        private const val HOME_RENEWALS_TAB_X_RATIO = 0.707f
        private const val HOME_TAB_CUSTOMERS = "Customers"
        private const val HOME_TAB_RENEWALS = "Renewals"

        private const val RENEWAL_NAVIGATION_DELAY_MS = 400L
        private const val RENEWAL_SCROLL_SETTLE_MS = 900L
        private const val RENEWAL_PAGE_LOAD_DELAY_MS = 2000L
        private const val RENEWAL_DROPDOWN_OPEN_DELAY_MS = 900L
        private const val RENEWAL_DASHBOARD_SCROLL_LIMIT = 14
        private const val RENEWAL_SCROLL_STALL_LIMIT = 2
        private const val RENEWAL_RETURN_TO_TOP_LIMIT = 20
        private const val RENEWAL_PAGE_RETRY_LIMIT = 3
        private const val RENEWAL_RELIST_LIMIT = 4
        private const val RENEWAL_RELIST_STALL_LIMIT = 2
        private const val RENEWAL_SKIP_WAIT_LIMIT = 6
        private const val RENEWAL_DROPDOWN_RETRY_LIMIT = 3
        private const val RENEWAL_RANGE_OCR_TOP_FRACTION = 0.55f
        private const val RENEWAL_RANGE_OCR_LIMIT = 2
        private const val RENEWAL_MANUAL_RANGE_WAIT_LIMIT = 45
        private const val RENEWAL_AUTOMATION_RECOVERY_LIMIT = 3
        private const val RENEWAL_FAILURE_RETRY_MS = 5000L
        private const val RENEWAL_SECTION_ROW_TOLERANCE_RATIO = 0.06f
        private const val RENEWAL_DUE_SECTION_HEADER = "Renewals Due"
        private const val RENEWAL_DUE_CUSTOMER_TOGGLE = "Customer View"
        private const val RENEWAL_DUE_POLICY_TOGGLE = "Policy View"
        private const val RENEWAL_DUE_VIEW_ALL_LIMIT = 400
        private const val RENEWAL_DUE_TOGGLE_RETRY_LIMIT = 4
        private const val RENEWAL_DUE_RETURN_STALL_LIMIT = 3
        private const val RENEWAL_DUE_TAP_RETRY_LIMIT = 4
        private const val RENEWAL_DUE_SEEK_SCROLL_LIMIT = 40
        private const val RENEWAL_DUE_SCROLL_DURATION_MS = 620L
        private const val RENEWAL_DUE_SCROLL_START_RATIO = 0.74f
        private const val RENEWAL_DUE_SCROLL_END_RATIO = 0.34f
        private const val RENEWAL_DUE_SEEK_START_RATIO = 0.70f
        private const val RENEWAL_DUE_SEEK_END_RATIO = 0.44f
        private const val RENEWAL_DUE_SCROLL_SETTLE_MS = 1100L
        private const val CUSTOMER_NAVIGATION_DELAY_MS = 400L
        private const val MAX_BUBBLE_NAME_LENGTH = 16

        private const val ERROR_SHEET_TITLE = "something went wrong"
        private const val ERROR_SHEET_RETRY_LABEL = "try again"
        private const val ERROR_SHEET_MAX_RETRIES = 3
        private const val ERROR_SHEET_BOUNDS_MISS_LIMIT = 4
        private const val ERROR_SHEET_HEALTHY_WINDOW_MS = 6_000L
        private const val ERROR_SHEET_HEALTHY_MIN_NODES = 25
        private const val ERROR_SHEET_GIVEUP_LIMIT = 3
        private const val ERROR_SHEET_PACE_STEP_MS = 400L
        private const val ERROR_SHEET_PACE_CEILING_MS = 2_000L
        private val ERROR_SHEET_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L)
        private const val OFFLINE_TITLE = "no internet connection"
        private const val OFFLINE_SUBTITLE = "check your network"
        private const val OFFLINE_POLL_MS = 3_000L
        private const val OFFLINE_MAX_WAIT_MS = 120_000L
        private const val OFFLINE_LOG_INTERVAL_MS = 15_000L
        private val OFFLINE_BACKOFF_MS = longArrayOf(5_000L, 15_000L, 30_000L)
        private const val SCREEN_READY_MIN_TEXT_NODES = 4
        private const val SCREEN_READY_STABLE_MS = 700L
        private const val SCREEN_READY_RECHECK_MS = 500L
        private const val SCREEN_READY_MAX_WAITS = 6
        private const val SCREEN_READY_LOOK_STALE_MS = 900L
        private const val BLANK_SCREEN_HOLD_MS = 12_000L
        private const val BLANK_SCREEN_RETRY_DELAY_MS = 4_000L
        private const val BLANK_SCREEN_RETRY_LIMIT = 3
        private const val CUSTOMER_SCROLL_SETTLE_MS = 900L
        private const val CUSTOMER_PAGE_LOAD_DELAY_MS = 2000L
        private const val CUSTOMER_DETAIL_OPEN_DELAY_MS = 1400L
        private const val CUSTOMER_PROFILE_TAB_DELAY_MS = 1200L
        private const val CUSTOMER_PROFILE_SWEEP_SETTLE_MS = 700L
        private const val CUSTOMER_SHEET_OPEN_DELAY_MS = 1100L
        private const val CUSTOMER_SHEET_CLOSE_DELAY_MS = 800L
        private const val CUSTOMER_RETURN_DELAY_MS = 1000L
        private const val CUSTOMER_FAILURE_RETRY_MS = 5000L
        private const val CUSTOMER_DASHBOARD_SCROLL_LIMIT = 14
        private const val CUSTOMER_PROFILE_SCROLL_LIMIT = 10
        private const val CUSTOMER_SCROLL_STALL_LIMIT = 2
        private const val CUSTOMER_RETURN_TO_TOP_LIMIT = 20
        private const val CUSTOMER_OPEN_RETRY_LIMIT = 3
        private const val CUSTOMER_PAGE_RETRY_LIMIT = 3
        private const val CUSTOMER_STEP_RETRY_LIMIT = 4
        private const val CUSTOMER_SHEET_CLOSE_LIMIT = 2
        private const val CUSTOMER_SHEET_SETTLE_MS = 2500L
        private const val CUSTOMER_EMPTY_SHEET_LIMIT = 2
        private const val MAX_SHEET_DUMP_NODES = 60
        private const val SHEET_OCR_TOP_FRACTION = 0.55f
        private const val SHEET_OCR_TIMEOUT_MS = 6_000L
        private const val MAX_SHEET_DUMP_DEPTH = 24
        private const val CUSTOMER_RETURN_ATTEMPT_LIMIT = 8
        private const val CUSTOMER_BACK_LIMIT = 2
        private const val CUSTOMER_PAGE_WAIT_LIMIT = 5
        private const val CUSTOMER_DETAIL_TOP_LIMIT = 12
        private const val CUSTOMER_SHEET_LINK_RETRY_LIMIT = 6
        private const val SHEET_SCRIM_Y_RATIO = 0.08f
        private const val CUSTOMER_PAGE_OPTION_MIN_WIDTH_RATIO = 0.7f
        private const val CUSTOMER_PAGE_LIST_OPEN_MIN_RUN = 4
        private const val CUSTOMER_PAGE_OPTION_VIEWPORT_RATIO = 0.24f
        private const val CUSTOMER_PAGE_OPTION_MIN_VIEWPORT_DP = 80f
        private const val CUSTOMER_PAGE_OPTION_DEAD_TAP_LIMIT = 6
        private const val CUSTOMER_SHEET_OCR_MIN_MS = 600L
        private const val SHEET_STALE_TREE_LIMIT = 6
        private const val CUSTOMER_REOPEN_LIMIT = 1
        private const val BUBBLE_MARGIN_DP = 8f
        private const val BUBBLE_BOTTOM_OFFSET_RATIO = 0.5f
        private val CUSTOMER_PAGE_OPTION_REGEX = Regex("^\\d{1,3}$")
        private const val CUSTOMER_AUTOMATION_RECOVERY_LIMIT = 3
        private const val PORTFOLIO_CUSTOMERS_ARROW_X_RATIO = 0.85f
        private const val CUSTOMER_ROW_ARROW_X_MIN_RATIO = 0.75f
        private const val CUSTOMER_ROW_ARROW_X_FALLBACK_RATIO = 0.84f
        private const val CUSTOMER_ROW_ARROW_Y_OFFSET_RATIO = 0.085f
        private const val CUSTOMER_NAME_GAP_RATIO = 0.075f
        private const val PROFILE_TAP_MAX_X_RATIO = 0.6f
        private const val CUSTOMER_TITLE_DASHBOARD = "Customer Dashboard"
        private const val CUSTOMER_TITLE_DETAIL = "Detailed Customer View"
        private const val CUSTOMER_TAB_PROFILE = "Profile"
        private const val CUSTOMER_CALL_LABEL = "Call Customer"
        private const val SHEET_TITLE_EMAIL = "Email ID(s)"
        private const val SHEET_TITLE_ADDRESS = "Address(es)"
        private const val SHEET_TITLE_MOBILE = "Mobile Number(s)"
        private const val PORTFOLIO_POLICIES_ARROW_X_RATIO = 0.42f
        private const val PORTFOLIO_POLICIES_ARROW_Y_FALLBACK_RATIO = 0.245f
        private const val POLICY_FAILURE_RETRY_MS = 5000L
        private const val LOGIN_MPIN_PROMPT = "enter mpin"
        private const val LOGIN_PASSWORD_PROMPT = "enter password"
        private const val LOGIN_BUTTON_LABEL = "Login"
        private const val LOGIN_MPIN_LINK = "Try using mPIN"
        private const val LOGIN_PASSWORD_LINK = "Try using Password"
        private const val LOGIN_ATTEMPT_LIMIT = 1
        private const val LOGIN_SWITCH_LIMIT = 2
        private const val LOGIN_SWITCH_SETTLE_MS = 1400L
        private const val MPIN_ROW_BUCKET_PX = 8
        private const val LOGIN_FILL_FAILURE_LIMIT = 3
        private const val LOGIN_SUBMIT_DELAY_MS = 900L
        private const val LOGIN_SUBMIT_RETRY_LIMIT = 4
        private const val LOGIN_SUBMIT_RETRY_MS = 700L
        private const val LOGIN_FILL_RETRY_MS = 1500L
        private val LOGIN_SETUP_MARKERS = listOf(
            "new mpin",
            "confirm mpin",
            "new password",
            "confirm password",
            "old password",
            "current password"
        )
        private val LOGIN_MPIN_REJECT_MARKERS = listOf(
            "incorrect mpin",
            "invalid mpin",
            "wrong mpin",
            "mpin is incorrect",
            "mpin does not match"
        )
        private val LOGIN_PASSWORD_REJECT_MARKERS = listOf(
            "incorrect password",
            "invalid password",
            "wrong password",
            "password is incorrect",
            "password does not match",
            "invalid credentials",
            "invalid user id or password"
        )
        private const val LOGIN_RELEASE_DELAY_MS = 5000L
        private const val ROOT_DIAGNOSTIC_INTERVAL_MS = 3000L
        private const val ACCESSIBILITY_CAPTURE_DEBOUNCE_MS = 200L
        private const val MAX_CAPTURED_NODES = 10000
        private const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L

        @SuppressLint("StaticFieldLeak")
        @Volatile
        var Instance: ScreenReaderService? = null
            private set

        @Volatile
        var IsCapturing = false
            private set

        @Volatile
        var IsPaused = false
            private set

        val CapturedNodes = CopyOnWriteArrayList<String>()
        var LastPackageName: String = ""

        fun IsServiceRunning(): Boolean = Instance != null
    }

    private var WindowMgr: WindowManager? = null
    private var BubbleView: View? = null
    private var BubbleLayoutParams: WindowManager.LayoutParams? = null
    private var IsOverlayAdded = false
    private var IsBubbleExpanded = false
    private var WakeLockObj: PowerManager.WakeLock? = null

    private var CurrentMode: CaptureMode = CaptureMode.POLICY
    private var CurrentSessionId: String = ""
    private var IsResumedSession = false
    private var CapturePolicyDetailsEnabled = false
    private var OriginActivityName: String = ""
    private var SessionStartedAt: Long = 0L
    private var PausedTotalMs: Long = 0L
    private var PausedAt: Long = 0L
    private var LatestRecords: List<ParsedRecord> = emptyList()
    private var LastParsedNodeCount: Int = -1
    private var IsAutoScrolling = false
    private var AutoScrollRunnable: Runnable? = null
    private var AutoScrollStallCount = 0
    private var LastAutoScrollNodeCount = 0
    private var CurrentAutoScrollScreenSignature = 0
    private var CompletedAutoScrollScreenSignature: Int? = null
    private var HasExpandedCurrentPolicyScreen = false

    private val CapturedPolicyMap = linkedMapOf<String, CustomerPolicy>()
    private val CapturedFupMap = linkedMapOf<String, FupPolicy>()
    private var HasClickedPortfolioPolicies = false
    private var IsPolicyDashboardActive = false
    private var IsPolicyDashboardAutomationRunning = false
    private var IsPolicyDashboardComplete = false
    private var PolicyAutomationRunnable: Runnable? = null
    private var CustomerAutomationRunnable: Runnable? = null
    private var IsCustomerAutomationRunning = false
    private var IsCustomerAutomationComplete = false
    private var IsCustomerDashboardActive = false
    private var HasClickedPortfolioCustomers = false
    private var PortfolioCustomersLastAttemptAt = 0L
    private var PortfolioCustomersClickAttempts = 0
    private var CustomerCurrentPage = 0
    private var CustomerTotalPages = 0
    private var TargetCustomerPage = 0
    private var CustomerScrollAttempts = 0
    private var CustomerScrollStallCount = 0
    private var CustomerPageRetryCount = 0
    private var CustomerPageWaitCount = 0
    private var CustomerPageChipRect: Rect? = null
    private var CustomerPageOptionDeadOffset = 0
    private var CustomerPageOptionPendingOffset = 0
    private var CustomerPageOptionDeadTaps = 0
    private var CustomerPageOptionSignature = ""
    private var CustomerOpenAttempts = 0
    private var CustomerStepAttempts = 0
    private var CustomerAutomationFailureCount = 0
    private var CustomerAutomationRetryAfter = 0L
    private var LatestCustomerVisibleSignature = 0
    private var ActiveCustomerName = ""
    private var ActiveCustomerPolicyNumbers: List<String> = emptyList()
    private var ActiveCustomerRelevantNumbers: List<String> = emptyList()
    private var ActiveSheetKind: CustomerProfileParser.ContactKind? = null
    private var SheetReadRetryCount = 0
    private var SheetLinkRetryCount = 0
    private var SheetOpenedAt = 0L
    private var SheetDismissSignature = 0
    private var SheetStaleTreeCount = 0
    private var SheetsEverYieldedValues = false
    private var EmptySheetReadCount = 0
    private var PendingOcrLines: List<String>? = null
    private var OcrInFlight = false
    private var RevisitFilledEnabled = false
    private var OcrWatchdogRunnable: Runnable? = null
    private val OcrAttemptedKinds = mutableSetOf<CustomerProfileParser.ContactKind>()
    private val EmptySheetKindCounts =
        mutableMapOf<CustomerProfileParser.ContactKind, Int>()
    private var ProfileSweepCount = 0
    private var LastProfileSweepSignature = 0
    private var CustomerStageValue = CustomerStage.IDLE
    private val ProfilePaneNodes = linkedSetOf<String>()
    private val PendingSheetKinds = mutableListOf<CustomerProfileParser.ContactKind>()
    private val ExpectedSheetCounts =
        mutableMapOf<CustomerProfileParser.ContactKind, Int>()
    private val ProcessedCustomerKeys = mutableSetOf<String>()
    private val CustomerReopenCounts = mutableMapOf<String, Int>()
    private var RequeueActiveCustomer = false
    private val SessionPolicyNumbers = mutableSetOf<String>()
    private val FilledPolicyNumbers = mutableSetOf<String>()
    private val VisitedCustomerNames = mutableSetOf<String>()
    private val ProfilePatchMap = linkedMapOf<String, CustomerPolicy>()
    private val ProfilePatchNames = mutableMapOf<String, String>()
    private val OcrExecutor = Executors.newSingleThreadExecutor()

    private var ErrorRetryCount = 0
    private var ErrorRecoveryScheduled = false
    private var ErrorRetryRunnable: Runnable? = null
    private var ErrorBoundsMissCount = 0
    private var ErrorHealthySinceAt = 0L
    private var ConsecutiveErrorGiveUps = 0
    private var ErrorPaceExtraMs = 0L
    private var PaceProfileValue = PaceProfile.NORMAL
    private var OfflineWaitMs = OFFLINE_MAX_WAIT_MS
    private var ErrorRetryLimit = ERROR_SHEET_MAX_RETRIES
    private var ErrorGiveUpLimit = ERROR_SHEET_GIVEUP_LIMIT
    private var ErrorSlowDownEnabled = true
    private var ContactOcrEnabled = true
    private var AgentPackageName = AppLauncherUtils.PS_AGENT_APP_PACKAGES.first()
    private var LastHealthyRecordCount = 0
    private var OfflineSinceAt = 0L
    private var OfflineRetryCount = 0
    private var OfflineRetryRunnable: Runnable? = null
    private var OfflineLastLogAt = 0L
    private var LoginAttemptCount = 0
    private var LoginFillFailureCount = 0
    private var LoginFillInFlight = false
    private var LoginSkipLogged = false
    private var LoginSubmitAttempts = 0
    private var LoginSubmitted = false
    private var LoginRejectedHandled = false
    private var LoginKeyboardHidden = false
    private var LoginSwitchCount = 0
    private var LoginFilledMethod: CredentialStore.Method? = null
    private var LastScreenSignature = 0
    private var LastScreenNodeCount = 0
    private var LastScreenLookAt = 0L
    private var ScreenStableSinceAt = 0L
    private val SessionGapMap = linkedMapOf<String, SessionGap>()
    private var ActiveProfile: CustomerProfile? = null
    private var PolicyCurrentPage = 0
    private var PolicyTotalPages = 0
    private var PolicyExpectedPage = 0
    private var PolicyPageRetryCount = 0
    private var PolicyReturnToTopCount = 0
    private var PolicyScrollStallCount = 0
    private var LatestPolicyVisibleSignature = 0
    private var IsPolicyPageSelectorVisible = false
    private var LatestPolicyPageNumbers: List<String> = emptyList()
    private var PolicyDetailQueue: List<String> = emptyList()
    private var PolicyDetailQueueIndex = 0
    private var PolicyDetailCurrentPolicyNumber = ""
    private var PolicyDetailScrollAttempts = 0
    private var PolicyDetailOpenAttempts = 0
    private var PolicyDetailReturnAttempts = 0
    private var PolicyDetailOriginPage = 0
    private var PolicyJumpTarget = 0
    private var PolicyJumpReason = POLICY_JUMP_NONE
    private var PolicyResumeTargetPage = 0
    private var PolicyResumeJumpAttempts = 0
    private var PolicyResumeWaitCount = 0
    private var PolicySelectorScrollCount = 0
    private var PolicySelectorHighestOption = 0
    private var PolicySelectorScrollStalls = 0
    private var PolicyLastFailurePage = 0
    private var IsPolicyDetailScreenActive = false
    private var IsPolicyDashboardScreenVisible = false
    private var LatestPolicyDetailNodes: List<String> = emptyList()
    private var PolicySectionRetryRounds = 0
    private var PolicyDetailSweepCount = 0
    private var LastPolicyDetailSweepSignature = 0
    private val TargetScope = PolicyTargetScope()
    private val SearchRoute = PolicySearchRoute(HostRef = this)
    private val CustomerNameScope = PolicyTargetScope()
    private val CustomerRoute = CustomerSearchRoute(HostRef = this)
    private var ChainCustomerName = ""
    private var IsChainedCustomerLegStarted = false

    private val IsRestoringPolicyPageAfterDetail: Boolean
        get() = PolicyJumpTarget > 0 && PolicyJumpReason == POLICY_JUMP_DETAIL_RESTORE

    private val IsPolicyPageJumpPending: Boolean
        get() = PolicyJumpTarget > 0

    private val PolicySectionsInFlight = linkedSetOf<String>()
    private val ProcessedPolicyDetailNumbers = linkedSetOf<String>()
    private var PortfolioPoliciesLastAttemptAt = 0L
    private var PortfolioPoliciesClickAttempts = 0
    private var HasClickedHomeNavTab = false
    private var HomeNavLastAttemptAt = 0L
    private var HomeNavRevealAt = 0L
    private var HomeNavRevealCount = 0
    private var HomeNavClickAttempts = 0

    private var IsRenewalAutomationRunning = false
    private var IsRenewalAutomationComplete = false
    private var RenewalAutomationRunnable: Runnable? = null
    private var HasOpenedRenewalHistoryList = false
    private var HasSelectedRenewalDateRange = false
    private var RenewalDashboardScrollCount = 0
    private var RenewalDropdownAttempts = 0
    private var RenewalDropdownBaselineTexts: Set<String> = emptySet()
    private var RenewalDropdownScrollPasses = 0
    private var RenewalDropdownSeenOptions: Set<String> = emptySet()
    private var RenewalChipBounds: Rect? = null
    private var RenewalUnknownScreenCount = 0
    private var LatestRenewalVisibleNodes: List<String> = emptyList()
    private var RenewalCurrentPage = 0
    private var RenewalTotalPages = 0
    private var RenewalExpectedPage = 0
    private var RenewalSkipTargetPage = 0
    private var RenewalSkipWaitCount = 0
    private var RenewalRangeOcrCount = 0
    private var RenewalRangeDays = RenewalDateRange.DEFAULT_SPAN_DAYS
    private var RenewalKnownTotalPages = 0
    private var RenewalPickedRangeLabel = ""
    private var RenewalLastRelistTarget = 0
    private var RenewalRelistStalls = 0
    private val RenewalKnownBadPages = linkedSetOf<Int>()
    private var RenewalKnownBadSpanDays = 0
    private var IsRenewalRangeSheetHidden = false
    private var HasOpenedRenewalDueList = false
    private var HasSwitchedToCustomerView = false
    private var RenewalDueToggleAttempts = 0
    private var RenewalDueRangeDays = RenewalDueRange.DEFAULT_SPAN_DAYS
    private var RenewalDuePickedRangeLabel = ""
    private var RenewalDueActiveCustomer = ""
    private var RenewalDueReturnStalls = 0
    private var RenewalDueTopResetDone = false
    private val RenewalDueTapAttempts = linkedMapOf<String, Int>()
    private var RenewalDueSeekScrolls = 0
    private val VisitedRenewalDueCustomers = linkedSetOf<String>()
    private val CapturedRenewalDueMap = linkedMapOf<String, RenewalDuePolicy>()
    private var BlankSinceAt = 0L
    private var BlankRecoveryAttempts = 0
    private var BlankRecoveryScheduled = false
    private var BlankRecoveryRunnable: Runnable? = null
    private var RenewalManualWaitCount = 0
    private var RenewalRangeBaselineLabel = ""

    private var RenewalRelistCount = 0
    private val RenewalSkippedPages = linkedSetOf<Int>()
    private var RenewalPageRetryCount = 0
    private var RenewalReturnToTopCount = 0
    private var RenewalScrollStallCount = 0
    private var LatestRenewalVisibleSignature = 0
    private var IsRenewalPageSelectorVisible = false
    private var RenewalAutomationRetryAfter = 0L
    private var RenewalAutomationFailureCount = 0
    private var PolicyAutomationRetryAfter = 0L
    private var PolicyAutomationFailureCount = 0
    private var LastDiagnosticScreenSignature = 0
    private var LastRootDiagnosticAt = 0L
    private var LastObservedEventPackage = ""
    private var LastObservedEventAt = 0L
    private var LastAnonymousRootDiagnosticAt = 0L
    private var EventCaptureRunnable: Runnable? = null

    private val MainHandler = Handler(Looper.getMainLooper())
    private var ParseThread: HandlerThread? = null
    private var ParseHandler: Handler? = null

    private var TvBubbleCount: TextView? = null
    private var TvBubbleMeta: TextView? = null
    private var TvBubblePause: TextView? = null
    private var PillContainer: LinearLayout? = null
    private var CardActions: LinearLayout? = null

    private enum class PolicyDetailOpenResult {
        CLICKED,
        NEED_SCROLL,
        FAILED
    }

    private val TickRunnable = object : Runnable {
        override fun run() {
            if (!IsCapturing) return
            val PolicyAutomationOwnsCapture = CurrentMode == CaptureMode.POLICY &&
                    IsPolicyDashboardAutomationRunning
            val RenewalAutomationOwnsCapture = CurrentMode == CaptureMode.FUP &&
                    IsRenewalAutomationRunning
            if (!IsPaused &&
                !PolicyAutomationOwnsCapture &&
                !RenewalAutomationOwnsCapture &&
                EventCaptureRunnable == null
            ) {
                CaptureActiveWindow(ExpectedPackage = ExpectedTargetPackage())
            }
            RefreshBubble()
            RequestIncrementalParse()
            MainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Instance = this

        val ServiceConfigInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 300
        }
        serviceInfo = ServiceConfigInfo

        WindowMgr = getSystemService(WINDOW_SERVICE) as WindowManager
        StartForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !IsCapturing || IsPaused) return

        val PackageNameStr = event.packageName?.toString() ?: return
        if (ShouldIgnorePackage(PackageNameVal = PackageNameStr)) return
        LogObservedAccessibilityEvent(EventObj = event, PackageNameVal = PackageNameStr)
        if (PackageNameStr != ExpectedTargetPackage()) return

        ScheduleEventWindowCapture(ExpectedPackage = PackageNameStr)
    }


    private fun ScheduleEventWindowCapture(ExpectedPackage: String) {
        if (EventCaptureRunnable != null) return
        lateinit var CaptureRunnable: Runnable
        CaptureRunnable = Runnable {
            if (EventCaptureRunnable !== CaptureRunnable) return@Runnable
            EventCaptureRunnable = null
            if (!IsCapturing || IsPaused || ExpectedTargetPackage() != ExpectedPackage) {
                return@Runnable
            }
            CaptureActiveWindow(ExpectedPackage = ExpectedPackage)
        }
        EventCaptureRunnable = CaptureRunnable
        MainHandler.postDelayed(CaptureRunnable, ACCESSIBILITY_CAPTURE_DEBOUNCE_MS)
    }

    private fun CancelEventWindowCapture() {
        EventCaptureRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        EventCaptureRunnable = null
    }

    private fun CaptureActiveWindow(ExpectedPackage: String = ""): Boolean {
        if (!IsCapturing || IsPaused) return false

        val RootNodeInfo = FindReadableRoot(ExpectedPackage = ExpectedPackage) ?: return false
        try {
            val PackageNameStr = ExpectedPackage.takeIf { PackageName -> PackageName.isNotEmpty() }
                ?: RootNodeInfo.packageName?.toString().orEmpty()
            if (PackageNameStr.isEmpty() || ShouldIgnorePackage(PackageNameVal = PackageNameStr)) return false

            val NodeList = mutableListOf<String>()
            TraverseNode(TargetNode = RootNodeInfo, ResultList = NodeList)
            LogVisibleScreenIfChanged(
                PackageNameVal = PackageNameStr,
                VisibleNodes = NodeList,
                SourceName = "active-window"
            )
            StoreCapturedSnapshot(PackageNameVal = PackageNameStr, NodeList = NodeList)
            HandleScreenAutomation(
                PackageNameVal = PackageNameStr,
                RootNode = RootNodeInfo,
                VisibleNodes = NodeList
            )
            return NodeList.isNotEmpty()
        } catch (ExceptionObj: Exception) {
            Log.w(LOG_TAG, "Unable to capture active accessibility window", ExceptionObj)
            DiagnosticWarning(
                EventName = "CAPTURE_ERROR",
                MessageText = "Active window capture failed: ${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
            return false
        } finally {
            RecycleNode(NodeRef = RootNodeInfo)
        }
    }

    private fun FindReadableRoot(ExpectedPackage: String): AccessibilityNodeInfo? {
        val ActiveRoot = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        val ActivePackageName = ActiveRoot?.packageName?.toString().orEmpty()

        var AnonymousApplicationRoot: AccessibilityNodeInfo? = null
        if (ActiveRoot != null) {
            val MatchesExpected = ExpectedPackage.isEmpty() ||
                    ActivePackageName == ExpectedPackage ||
                    NodeTreeContainsPackage(
                        RootNode = ActiveRoot,
                        ExpectedPackage = ExpectedPackage
                    )
            if (MatchesExpected && !ShouldIgnorePackage(PackageNameVal = ActivePackageName)) {
                return ActiveRoot
            }
            val IsDifferentForegroundApp = ExpectedPackage.isNotEmpty() &&
                    ActivePackageName.isNotEmpty() &&
                    ActivePackageName != ExpectedPackage &&
                    ActivePackageName != packageName &&
                    !ShouldIgnorePackage(PackageNameVal = ActivePackageName)
            if (IsDifferentForegroundApp) {
                RecycleNode(NodeRef = ActiveRoot)
                val CurrentTime = System.currentTimeMillis()
                if (CurrentTime - LastRootDiagnosticAt >= ROOT_DIAGNOSTIC_INTERVAL_MS) {
                    LastRootDiagnosticAt = CurrentTime
                    DiagnosticInfo(
                        EventName = "ROOT_OTHER_APP",
                        MessageText = "Waiting because foreground package=$ActivePackageName " +
                                "does not match expected=$ExpectedPackage"
                    )
                }
                return null
            }
            if (ExpectedPackage.isNotEmpty() && ActivePackageName.isEmpty()) {
                AnonymousApplicationRoot = ActiveRoot
            } else {
                RecycleNode(NodeRef = ActiveRoot)
            }
        }


        val WindowList = try {
            windows
        } catch (_: Exception) {
            emptyList()
        }
        val SortedWindows = WindowList.sortedWith(
            compareByDescending<AccessibilityWindowInfo> { WindowRef ->
                when {
                    WindowRef.isFocused -> 3
                    WindowRef.isActive -> 2
                    WindowRef.type == AccessibilityWindowInfo.TYPE_APPLICATION -> 1
                    else -> 0
                }
            }.thenByDescending { WindowRef -> WindowRef.layer }
        )
        val WindowDescriptions = mutableListOf<String>()
        for (WindowRef in SortedWindows) {
            val WindowRoot = try {
                WindowRef.root
            } catch (ExceptionObj: Exception) {
                WindowDescriptions.add(
                    "id=${WindowRef.id},type=${WindowRef.type},layer=${WindowRef.layer}," +
                            "active=${WindowRef.isActive},focused=${WindowRef.isFocused}," +
                            "root=error:${ExceptionObj.javaClass.simpleName}"
                )
                null
            }
            if (WindowRoot == null) {
                if (WindowDescriptions.none { DescriptionText ->
                        DescriptionText.startsWith("id=${WindowRef.id},")
                    }
                ) {
                    WindowDescriptions.add(
                        "id=${WindowRef.id},type=${WindowRef.type},layer=${WindowRef.layer}," +
                                "active=${WindowRef.isActive},focused=${WindowRef.isFocused},root=null"
                    )
                }
                continue
            }
            val WindowPackage = WindowRoot.packageName?.toString().orEmpty()
            val DescendantMatches = WindowPackage != ExpectedPackage &&
                    ExpectedPackage.isNotEmpty() &&
                    NodeTreeContainsPackage(
                        RootNode = WindowRoot,
                        ExpectedPackage = ExpectedPackage
                    )
            WindowDescriptions.add(
                "id=${WindowRef.id},type=${WindowRef.type},layer=${WindowRef.layer}," +
                        "active=${WindowRef.isActive},focused=${WindowRef.isFocused}," +
                        "package=[$WindowPackage],descendantMatch=$DescendantMatches"
            )
            val MatchesExpected = ExpectedPackage.isEmpty() ||
                    WindowPackage == ExpectedPackage ||
                    DescendantMatches
            if (MatchesExpected && !ShouldIgnorePackage(PackageNameVal = WindowPackage)) {
                RecycleNode(NodeRef = AnonymousApplicationRoot)
                return WindowRoot
            }
            val IsAnonymousApplicationWindow = ExpectedPackage.isNotEmpty() &&
                    WindowPackage.isEmpty() &&
                    WindowRef.type == AccessibilityWindowInfo.TYPE_APPLICATION
            if (IsAnonymousApplicationWindow && AnonymousApplicationRoot == null) {
                AnonymousApplicationRoot = WindowRoot
            } else {
                RecycleNode(NodeRef = WindowRoot)
            }
        }
        if (AnonymousApplicationRoot != null) {
            val CurrentTime = System.currentTimeMillis()
            if (CurrentTime - LastAnonymousRootDiagnosticAt >= ROOT_DIAGNOSTIC_INTERVAL_MS) {
                LastAnonymousRootDiagnosticAt = CurrentTime
                DiagnosticInfo(
                    EventName = "ROOT_ANONYMOUS_FALLBACK",
                    MessageText = "Using readable application root with no package name; " +
                            "expected=$ExpectedPackage windows=${WindowDescriptions.joinToString()}"
                )
            }
            return AnonymousApplicationRoot
        }
        val CurrentTime = System.currentTimeMillis()
        if (CurrentTime - LastRootDiagnosticAt >= ROOT_DIAGNOSTIC_INTERVAL_MS) {
            LastRootDiagnosticAt = CurrentTime
            DiagnosticWarning(
                EventName = "ROOT_MISSING",
                MessageText = "expected=$ExpectedPackage active=$ActivePackageName " +
                        "windows=${WindowList.size} candidates=${WindowDescriptions.joinToString()}"
            )
        }
        return null
    }

    private fun NodeTreeContainsPackage(
        RootNode: AccessibilityNodeInfo,
        ExpectedPackage: String
    ): Boolean {
        if (ExpectedPackage.isEmpty()) return false
        val RemainingNodeBudget = intArrayOf(250)
        return NodeTreeContainsPackageInternal(
            CurrentNode = RootNode,
            ExpectedPackage = ExpectedPackage,
            RemainingNodeBudget = RemainingNodeBudget
        )
    }

    private fun NodeTreeContainsPackageInternal(
        CurrentNode: AccessibilityNodeInfo,
        ExpectedPackage: String,
        RemainingNodeBudget: IntArray
    ): Boolean {
        if (RemainingNodeBudget[0]-- <= 0) return false
        if (CurrentNode.packageName?.toString() == ExpectedPackage) return true
        for (ChildIndex in 0 until CurrentNode.childCount) {
            val ChildNode = try {
                CurrentNode.getChild(ChildIndex)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                if (NodeTreeContainsPackageInternal(
                        CurrentNode = ChildNode,
                        ExpectedPackage = ExpectedPackage,
                        RemainingNodeBudget = RemainingNodeBudget
                    )
                ) {
                    return true
                }
            } finally {
                RecycleNode(NodeRef = ChildNode)
            }
        }
        return false
    }

    private fun StoreCapturedSnapshot(PackageNameVal: String, NodeList: List<String>) {
        if (CurrentMode == CaptureMode.RENEWAL_DUE) {
            UpdateRenewalScreenState(VisibleNodes = NodeList)
            CaptureRenewalDueSnapshot(PackageNameVal = PackageNameVal, VisibleNodes = NodeList)
            return
        }
        if (CurrentMode == CaptureMode.FUP) {
            UpdateRenewalScreenState(VisibleNodes = NodeList)
            CaptureRenewalSnapshot(PackageNameVal = PackageNameVal, VisibleNodes = NodeList)
            return
        }
        val IsDetailSnapshot = CurrentMode == CaptureMode.POLICY &&
                IsPolicyDetailScreen(VisibleNodes = NodeList)
        if (IsDetailSnapshot) {
            CapturePolicyDetailSnapshot(
                PackageNameVal = PackageNameVal,
                VisibleNodes = NodeList
            )
            return
        }
        if ((SearchRoute.IsDriving || CustomerRoute.IsDriving) &&
            PolicySearchParser.IsSearchScreen(Nodes = NodeList)
        ) {
            return
        }
        val IsDashboardSnapshot = CurrentMode == CaptureMode.POLICY &&
                (IsPolicyDashboardActive || IsPolicyDashboardScreen(VisibleNodes = NodeList))
        if (IsDashboardSnapshot) {
            IsPolicyDashboardActive = true
            CapturePolicyDashboardSnapshot(PackageNameVal = PackageNameVal, VisibleNodes = NodeList)
        } else {
            AddCapturedNodes(PackageNameVal = PackageNameVal, NodeList = NodeList)
        }
    }

    private fun CapturePolicyDashboardSnapshot(PackageNameVal: String, VisibleNodes: List<String>) {
        LatestPolicyVisibleSignature = VisibleNodes.joinToString(separator = "\u0001").hashCode()

        val PageInfo = ParsePolicyPageInfo(VisibleNodes = VisibleNodes)
        IsPolicyPageSelectorVisible = PageInfo != null
        if (PageInfo != null) {
            val PreviousPage = PolicyCurrentPage
            PolicyCurrentPage = PageInfo.first
            PolicyTotalPages = PageInfo.second
            if (PreviousPage != PolicyCurrentPage) {
                DiagnosticInfo(
                    EventName = "POLICY_PAGE_DETECTED",
                    MessageText = "page=$PolicyCurrentPage total=$PolicyTotalPages"
                )
            }
        }

        val VisiblePolicies = try {
            ScreenDataParser.ParsePolicyDashboard(Nodes = VisibleNodes)
        } catch (ExceptionObj: Exception) {
            Log.w(LOG_TAG, "Unable to parse visible policy cards", ExceptionObj)
            DiagnosticWarning(
                EventName = "POLICY_PARSE_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            emptyList()
        }
        if (VisiblePolicies.isNotEmpty()) {
            LatestPolicyPageNumbers = VisiblePolicies.map { PolicyItem -> PolicyItem.PolicyNumber }
        }

        var PolicyMapChanged = false
        for (IncomingPolicy in VisiblePolicies) {
            val ExistingPolicy = CapturedPolicyMap[IncomingPolicy.PolicyNumber]
            val MergedPolicy = if (ExistingPolicy == null) {
                IncomingPolicy
            } else {
                ScreenDataParser.MergePolicyDashboardRecord(
                    ExistingPolicy = ExistingPolicy,
                    IncomingPolicy = IncomingPolicy
                )
            }
            if (ExistingPolicy != MergedPolicy) {
                CapturedPolicyMap[IncomingPolicy.PolicyNumber] = MergedPolicy
                PolicyMapChanged = true
            }
        }

        if (PolicyMapChanged) {
            RebuildCapturedPolicyNodes()
            LastPackageName = PackageNameVal
            LastParsedNodeCount = -1
            Log.d(LOG_TAG, "Policy dashboard contains ${CapturedPolicyMap.size} unique policies")
            DiagnosticInfo(
                EventName = "POLICIES_CAPTURED",
                MessageText = "visibleParsed=${VisiblePolicies.size} uniqueTotal=${CapturedPolicyMap.size} " +
                        "page=$PolicyCurrentPage/$PolicyTotalPages"
            )
        }
    }

    private fun CapturePolicyDetailSnapshot(
        PackageNameVal: String,
        VisibleNodes: List<String>
    ) {
        IsPolicyDetailScreenActive = true
        IsPolicyPageSelectorVisible = false
        LatestPolicyDetailNodes = VisibleNodes
        val IncomingPolicy = try {
            ScreenDataParser.ParseDetailedPolicyRecord(Nodes = VisibleNodes)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_DETAIL_PARSE_ERROR",
                MessageText = "expected=$PolicyDetailCurrentPolicyNumber " +
                        "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            null
        } ?: return

        val PolicyNumber = IncomingPolicy.PolicyNumber.ifEmpty { PolicyDetailCurrentPolicyNumber }
        val ExistingPolicy = CapturedPolicyMap[PolicyNumber]
        val MergedPolicy = if (ExistingPolicy == null) {
            IncomingPolicy
        } else {
            ScreenDataParser.MergePolicyDashboardRecord(
                ExistingPolicy = ExistingPolicy,
                IncomingPolicy = IncomingPolicy
            )
        }
        CapturedPolicyMap[PolicyNumber] = MergedPolicy
        RebuildCapturedPolicyNodes()
        LastPackageName = PackageNameVal
        LastParsedNodeCount = -1
        val DetailFieldCount = listOf(
            MergedPolicy.SumAssured,
            MergedPolicy.TermPPT,
            MergedPolicy.DateOfCommencement,
            MergedPolicy.EndOfPremiumPayingTerm,
            MergedPolicy.DateOfMaturity,
            MergedPolicy.CommissionDateOfPremiumPayment,
            MergedPolicy.CommissionDateOfPayment,
            MergedPolicy.CommissionType,
            MergedPolicy.BonusCommission,
            MergedPolicy.CommissionPaidAmount
        ).count { FieldText -> FieldText.isNotEmpty() }
        DiagnosticInfo(
            EventName = "POLICY_DETAIL_CAPTURED",
            MessageText = "expected=$PolicyDetailCurrentPolicyNumber actual=$PolicyNumber " +
                    "detailFields=$DetailFieldCount sumAssured=${MergedPolicy.SumAssured} " +
                    "termPpt=${MergedPolicy.TermPPT}"
        )
    }


    private fun CaptureRenewalSnapshot(PackageNameVal: String, VisibleNodes: List<String>) {
        val VisibleRecords = try {
            FupDataParser.ParseRenewalHistory(Nodes = VisibleNodes)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "RENEWAL_PARSE_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            emptyList()
        }
        if (VisibleRecords.isEmpty()) {
            AddCapturedNodes(PackageNameVal = PackageNameVal, NodeList = VisibleNodes)
            return
        }

        var MapChanged = false
        for (IncomingRecord in VisibleRecords) {
            if (IncomingRecord.PolicyNumber.isEmpty()) continue

            val ExistingKey = FindRenewalRecordKey(IncomingRecord = IncomingRecord)
            val ExistingRecord = ExistingKey?.let { KeyText -> CapturedFupMap[KeyText] }
            val MergedRecord = if (ExistingRecord == null) {
                IncomingRecord
            } else {
                FupDataParser.MergeRenewalRecord(
                    ExistingRecord = ExistingRecord,
                    IncomingRecord = IncomingRecord
                )
            }
            val MergedKey = RenewalRecordKey(RecordItem = MergedRecord)
            if (ExistingRecord != MergedRecord || ExistingKey != MergedKey) {
                if (ExistingKey != null && ExistingKey != MergedKey) {
                    CapturedFupMap.remove(ExistingKey)
                }
                CapturedFupMap[MergedKey] = MergedRecord
                MapChanged = true
            }
        }

        if (MapChanged) {
            RebuildCapturedRenewalNodes()
            LastPackageName = PackageNameVal
            LastParsedNodeCount = -1
            DiagnosticInfo(
                EventName = "RENEWALS_CAPTURED",
                MessageText = "visibleParsed=${VisibleRecords.size} " +
                        "uniqueTotal=${CapturedFupMap.size} page=$RenewalCurrentPage/$RenewalTotalPages"
            )
        }
    }

    private fun RenewalRecordKey(RecordItem: FupPolicy): String {
        return RecordMerge.RenewalKey(RecordItem = RecordItem)
    }

    private fun FindRenewalRecordKey(IncomingRecord: FupPolicy): String? {
        val ExactKey = RenewalRecordKey(RecordItem = IncomingRecord)
        if (CapturedFupMap.containsKey(ExactKey)) return ExactKey

        return CapturedFupMap.entries.firstOrNull { MapEntry ->
            MapEntry.value.PolicyNumber == IncomingRecord.PolicyNumber &&
                    (MapEntry.value.PaymentDate.isEmpty() || IncomingRecord.PaymentDate.isEmpty())
        }?.key
    }


    private fun RebuildCapturedRenewalNodes() {
        CapturedNodes.clear()
        for ((PolicyNumber, PlanName, PlanCode, HolderName, PremiumAmount, _, DueDate, PaymentDate, ModeOfPayment, Status) in CapturedFupMap.values) {
            val PlanLabel = PlanIdentity.Combine(
                CodeValue = PlanCode,
                NameValue = PlanName
            )
            val AnchorLine = buildString {
                append(PolicyNumber)
                if (PlanLabel.isNotEmpty()) append(" | ").append(PlanLabel)
            }
            CapturedNodes.add(AnchorLine)
            if (HolderName.isNotEmpty()) CapturedNodes.add(HolderName)
            if (PremiumAmount.isNotEmpty()) {
                CapturedNodes.add("Premium Amount (excl. GST)")
                CapturedNodes.add(PremiumAmount)
            }
            if (DueDate.isNotEmpty()) {
                CapturedNodes.add("Due Date")
                CapturedNodes.add(DueDate)
            }
            if (PaymentDate.isNotEmpty()) {
                CapturedNodes.add("Payment Date")
                CapturedNodes.add(PaymentDate)
            }
            if (ModeOfPayment.isNotEmpty()) {
                CapturedNodes.add("Mode of Payment")
                CapturedNodes.add(ModeOfPayment)
            }
            if (Status.isNotEmpty()) {
                CapturedNodes.add("Status at Time of Payment")
                CapturedNodes.add(Status)
            }
            CapturedNodes.add("Call Customer")
        }
    }

    private fun RebuildCapturedPolicyNodes() {
        CapturedNodes.clear()
        for ((HolderName, _, PolicyNumber, PlanName, _, RenewalDueDate, PremiumAmount, PremiumFrequency, AutoPay, Status, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, NomineeStatus, MobileUpdateStatus, AddressUpdateStatus, KycStatus, NeftStatus, RenewalType) in CapturedPolicyMap.values) {
            if (Status.isNotEmpty()) CapturedNodes.add(Status)
            if (KycStatus.isNotEmpty()) CapturedNodes.add("KYC not updated")
            if (NeftStatus.isNotEmpty()) CapturedNodes.add("NEFT not updated")
            if (NomineeStatus.isNotEmpty()) CapturedNodes.add("Nominee not updated")
            if (MobileUpdateStatus.isNotEmpty()) CapturedNodes.add("Mobile not updated")
            if (AddressUpdateStatus.isNotEmpty()) CapturedNodes.add("Address not updated")

            val PolicyLine = buildString {
                append(PolicyNumber)
                if (PlanName.isNotEmpty()) append(" | ").append(PlanName)
            }
            CapturedNodes.add(PolicyLine)
            if (HolderName.isNotEmpty()) CapturedNodes.add(HolderName)
            if (AutoPay.isNotEmpty()) {
                CapturedNodes.add("Auto Pay")
                CapturedNodes.add(AutoPay)
            }
            if (RenewalType.isNotEmpty()) CapturedNodes.add(RenewalType)
            if (RenewalDueDate.isNotEmpty()) CapturedNodes.add(RenewalDueDate)
            if (PremiumAmount.isNotEmpty()) {
                CapturedNodes.add("Premium Amount (excl. GST)")
                val FrequencyText = PremiumFrequency
                    .takeIf { ValueText -> ValueText.isNotEmpty() }
                    ?.let { ValueText -> "/$ValueText" }
                    .orEmpty()
                CapturedNodes.add("$PremiumAmount$FrequencyText")
            }
            CapturedNodes.add("Send Reminder")
        }
    }

    private fun ParsePolicyPageInfo(VisibleNodes: List<String>): Pair<Int, Int>? {
        val CombinedText = VisibleNodes.joinToString(separator = " ")
        val PageMatch = Regex(
            "(?i)\\bPage\\s*(\\d{1,3})\\D{0,30}?of\\s*(\\d{1,3})\\b"
        ).find(CombinedText) ?: return null
        val CurrentPage = PageMatch.groupValues[1].toIntOrNull() ?: return null
        val TotalPages = PageMatch.groupValues[2].toIntOrNull() ?: return null
        if (CurrentPage <= 0 || TotalPages <= 0 || CurrentPage > TotalPages) return null
        return CurrentPage to TotalPages
    }

    private fun AddCapturedNodes(PackageNameVal: String, NodeList: List<String>) {
        if (NodeList.isEmpty()) return

        var AddedCount = 0
        for (NodeTextItem in NodeList) {
            if (CapturedNodes.size >= MAX_CAPTURED_NODES) break
            if (!CapturedNodes.contains(NodeTextItem)) {
                CapturedNodes.add(NodeTextItem)
                AddedCount++
            }
        }
        if (AddedCount > 0) {
            LastPackageName = PackageNameVal
            Log.d(LOG_TAG, "Captured $AddedCount new nodes from $PackageNameVal")
        }
    }

    private fun TraverseNode(TargetNode: AccessibilityNodeInfo?, ResultList: MutableList<String>) {
        if (TargetNode == null) return
        try {
            val TextContent = TargetNode.text?.toString()?.trim()
            val DescContent = TargetNode.contentDescription?.toString()?.trim()

            if (!TextContent.isNullOrEmpty()) {
                ResultList.add(TextContent)
            } else if (!DescContent.isNullOrEmpty()) {
                ResultList.add(DescContent)
            }

            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                try {
                    TraverseNode(TargetNode = ChildNode, ResultList = ResultList)
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Accessibility node became stale during traversal", ExceptionObj)
        }
    }

    @Suppress("DEPRECATION")
    private fun RecycleNode(NodeRef: AccessibilityNodeInfo?) {
        try {
            NodeRef?.recycle()
        } catch (_: Exception) {
        }
    }

    private fun ShouldIgnorePackage(PackageNameVal: String): Boolean {
        if (PackageNameVal == packageName) return true
        if (PackageNameVal.contains("launcher", ignoreCase = true)) return true
        if (PackageNameVal.contains("systemui", ignoreCase = true)) return true
        if (PackageNameVal.contains("inputmethod", ignoreCase = true)) return true
        if (PackageNameVal.contains("keyboard", ignoreCase = true)) return true
        return false
    }

    private fun LogObservedAccessibilityEvent(
        EventObj: AccessibilityEvent,
        PackageNameVal: String
    ) {
        val CurrentTime = System.currentTimeMillis()
        val ShouldWrite = PackageNameVal != LastObservedEventPackage ||
                CurrentTime - LastObservedEventAt >= ROOT_DIAGNOSTIC_INTERVAL_MS
        if (!ShouldWrite) return
        LastObservedEventPackage = PackageNameVal
        LastObservedEventAt = CurrentTime
        DiagnosticInfo(
            EventName = "EVENT_OBSERVED",
            MessageText = "package=$PackageNameVal expected=${ExpectedTargetPackage()} " +
                    "type=${AccessibilityEvent.eventTypeToString(EventObj.eventType)} " +
                    "windowId=${EventObj.windowId} class=${EventObj.className} " +
                    "text=${EventObj.text.take(5)} " +
                    "description=${EventObj.contentDescription?.toString().orEmpty()}"
        )
    }

    private fun LogVisibleScreenIfChanged(
        PackageNameVal: String,
        VisibleNodes: List<String>,
        SourceName: String
    ) {
        val ScreenName = when {
            IsCustomerDetailScreen(VisibleNodes = VisibleNodes) -> "Detailed Customer View"
            IsCustomerDashboardScreen(VisibleNodes = VisibleNodes) -> "Customer Dashboard"
            IsCustomerPortfolioScreen(VisibleNodes = VisibleNodes) -> "Customer Portfolio"
            IsAgentHomeScreen(VisibleNodes = VisibleNodes) -> "Agent Home"
            IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes) -> "Renewals Dashboard"
            IsRenewalHistoryScreen(VisibleNodes = VisibleNodes) -> "Renewal History"
            IsPolicyDashboardScreen(VisibleNodes = VisibleNodes) -> "Policy Dashboard"
            VisibleNodes.any { NodeText ->
                NodeText.contains("Detailed Policy View", ignoreCase = true)
            } -> "Detailed Policy View"

            VisibleNodes.any { NodeText ->
                NodeText.contains("servicing", ignoreCase = true) ||
                        NodeText.contains("policy status", ignoreCase = true)
            } -> "Policy Servicing"

            VisibleNodes.any { NodeText ->
                NodeText.contains("renewal history", ignoreCase = true) ||
                        NodeText.equals("FUP", ignoreCase = true)
            } -> "FUP"

            VisibleNodes.isEmpty() -> "Empty accessibility tree"
            else -> "Unknown"
        }
        val ScreenSignature = listOf(
            PackageNameVal,
            ScreenName,
            VisibleNodes.joinToString(separator = "\u0001")
        ).hashCode()
        if (ScreenSignature == LastDiagnosticScreenSignature) return
        LastDiagnosticScreenSignature = ScreenSignature

        CaptureDiagnostics.LogVisibleNodes(
            ContextObj = this,
            ScreenName = ScreenName,
            PackageNameVal = PackageNameVal,
            VisibleNodes = VisibleNodes,
            StateText = "source=$SourceName mode=${CurrentMode.name} " +
                    "navTabClicked=$HasClickedHomeNavTab " +
                    "portfolioClicked=$HasClickedPortfolioPolicies " +
                    "policyActive=$IsPolicyDashboardActive " +
                    "automationRunning=$IsPolicyDashboardAutomationRunning " +
                    "automationComplete=$IsPolicyDashboardComplete " +
                    "page=$PolicyCurrentPage/$PolicyTotalPages " +
                    "expectedPage=$PolicyExpectedPage capturedPolicies=${CapturedPolicyMap.size} " +
                    "renewalRunning=$IsRenewalAutomationRunning " +
                    "renewalComplete=$IsRenewalAutomationComplete " +
                    "renewalHistoryOpened=$HasOpenedRenewalHistoryList " +
                    "renewalRangeSelected=$HasSelectedRenewalDateRange " +
                    "renewalPage=$RenewalCurrentPage/$RenewalTotalPages"
        )
    }

    private fun DiagnosticInfo(EventName: String, MessageText: String) {
        Log.d(LOG_TAG, "$EventName: $MessageText")
        CaptureDiagnostics.Log(
            ContextObj = this,
            EventName = EventName,
            MessageText = MessageText
        )
    }

    private fun DiagnosticWarning(EventName: String, MessageText: String) {
        Log.w(LOG_TAG, "$EventName: $MessageText")
        CaptureDiagnostics.Log(
            ContextObj = this,
            EventName = "WARNING/$EventName",
            MessageText = MessageText
        )
    }


    private fun Paced(BaseMs: Long): Long = PaceProfileValue.Scale(BaseMs = BaseMs)

    private fun LoadRunSettings() {
        PaceProfileValue = SettingsStore.PaceOf(ContextRef = this)
        OfflineWaitMs = SettingsStore.OfflineWaitMs(ContextRef = this)
        ErrorRetryLimit = SettingsStore.ErrorRetryLimit(ContextRef = this)
        ErrorGiveUpLimit = SettingsStore.ErrorGiveUpLimit(ContextRef = this)
        ErrorSlowDownEnabled = SettingsStore.IsErrorSlowDownOn(ContextRef = this)
        ContactOcrEnabled = SettingsStore.IsContactOcrOn(ContextRef = this)
        RenewalRangeDays = SettingsStore.RenewalRangeDays(ContextRef = this)
        RenewalDueRangeDays = SettingsStore.RenewalDueRangeDays(ContextRef = this)
        AgentPackageName = AppLauncherUtils.ResolveAgentPackage(ContextRef = this)
    }

    fun StartCaptureSession(
        ModeVal: CaptureMode,
        CapturePolicyDetailsVal: Boolean = false,
        OriginActivityVal: String = "",
        ResumeSessionIdVal: String = "",
        RevisitFilledVal: Boolean = false,
        ResumeFromPageVal: Int = 0,
        TargetPolicyNumbersVal: List<String> = emptyList(),
        TargetNameHintsVal: Map<String, String> = emptyMap(),
        TargetCustomerNamesVal: List<String> = emptyList(),
        ChainCustomerNameVal: String = ""
    ) {
        CurrentMode = ModeVal
        LoadRunSettings()
        RevisitFilledEnabled = RevisitFilledVal
        CancelEventWindowCapture()
        IsResumedSession = ResumeSessionIdVal.isNotBlank()
        CurrentSessionId = ResumeSessionIdVal.ifBlank { UUID.randomUUID().toString() }
        CapturePolicyDetailsEnabled = ModeVal == CaptureMode.POLICY && CapturePolicyDetailsVal
        OriginActivityName = OriginActivityVal
        SessionStartedAt = System.currentTimeMillis()
        PausedTotalMs = 0L
        PausedAt = 0L
        LatestRecords = emptyList()
        LoginAttemptCount = 0
        LoginFillFailureCount = 0
        LoginFillInFlight = false
        LoginSkipLogged = false
        LoginSubmitAttempts = 0
        LoginSubmitted = false
        LoginRejectedHandled = false
        LoginSwitchCount = 0
        LoginFilledMethod = null
        RestoreSoftKeyboard()
        CancelErrorRetry()
        ErrorRetryCount = 0
        ErrorBoundsMissCount = 0
        ErrorHealthySinceAt = 0L
        ConsecutiveErrorGiveUps = 0
        ErrorPaceExtraMs = 0L
        LastHealthyRecordCount = 0
        CancelOfflineWatch()
        OfflineSinceAt = 0L
        OfflineRetryCount = 0
        OfflineLastLogAt = 0L
        LastScreenSignature = 0
        LastScreenNodeCount = 0
        LastScreenLookAt = 0L
        ScreenStableSinceAt = 0L
        LastParsedNodeCount = -1
        LastPackageName = ""
        HasExpandedCurrentPolicyScreen = false
        CurrentAutoScrollScreenSignature = 0
        CompletedAutoScrollScreenSignature = null
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = true)
        CapturedPolicyMap.clear()
        CapturedFupMap.clear()
        CapturedRenewalDueMap.clear()
        VisitedRenewalDueCustomers.clear()
        RenewalDueTapAttempts.clear()
        LatestPolicyPageNumbers = emptyList()
        PolicyDetailQueue = emptyList()
        PolicyDetailQueueIndex = 0
        PolicyDetailCurrentPolicyNumber = ""
        PolicyDetailScrollAttempts = 0
        PolicyDetailOpenAttempts = 0
        PolicyDetailReturnAttempts = 0
        PolicyDetailOriginPage = 0
        ClearPolicyJump()
        PolicyResumeTargetPage = 0
        PolicyResumeJumpAttempts = 0
        PolicyResumeWaitCount = 0
        PolicySelectorScrollCount = 0
        PolicySelectorHighestOption = 0
        PolicySelectorScrollStalls = 0
        PolicyLastFailurePage = 0
        IsPolicyDetailScreenActive = false
        IsPolicyDashboardScreenVisible = false
        LatestPolicyDetailNodes = emptyList()
        PolicySectionRetryRounds = 0
        PolicyDetailSweepCount = 0
        LastPolicyDetailSweepSignature = 0
        PolicySectionsInFlight.clear()
        ProcessedPolicyDetailNumbers.clear()
        HasClickedPortfolioPolicies = false
        PortfolioPoliciesLastAttemptAt = 0L
        PortfolioPoliciesClickAttempts = 0
        HasClickedHomeNavTab = false
        HomeNavLastAttemptAt = 0L
        HomeNavClickAttempts = 0
        HomeNavRevealAt = 0L
        HomeNavRevealCount = 0
        StopRenewalAutomation(ResetStateVal = true)
        StopCustomerAutomation(ResetStateVal = true)
        PolicyAutomationRetryAfter = 0L
        PolicyAutomationFailureCount = 0
        LastDiagnosticScreenSignature = 0
        LastRootDiagnosticAt = 0L
        LastObservedEventPackage = ""
        LastObservedEventAt = 0L
        LastAnonymousRootDiagnosticAt = 0L

        IsCapturing = true
        IsPaused = false
        CapturedNodes.clear()

        CaptureDiagnostics.StartSession(
            ContextObj = this,
            SessionId = CurrentSessionId,
            ModeVal = ModeVal,
            ExpectedPackage = ExpectedTargetPackage(),
            IsResumedVal = IsResumedSession
        )
        DiagnosticInfo(
            EventName = "SESSION_START",
            MessageText = "session=$CurrentSessionId mode=${ModeVal.name} " +
                    "resumed=$IsResumedSession " +
                    "capturePolicyDetails=$CapturePolicyDetailsEnabled " +
                    "resumeFromPage=$ResumeFromPageVal " +
                    "expected=${ExpectedTargetPackage()} " +
                    "origin=$OriginActivityVal"
        )
        val ActiveServiceInfo = serviceInfo
        val IsDeclaredAccessibilityTool = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActiveServiceInfo?.isAccessibilityTool == true
        val CanRetrieveWindowContent = (
                ActiveServiceInfo?.capabilities ?: 0
                ) and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
        DiagnosticInfo(
            EventName = "SERVICE_CAPABILITIES",
            MessageText = "isAccessibilityTool=$IsDeclaredAccessibilityTool " +
                    "canRetrieveWindowContent=$CanRetrieveWindowContent " +
                    "flags=${ActiveServiceInfo?.flags} " +
                    "eventTypes=${ActiveServiceInfo?.eventTypes}"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !IsDeclaredAccessibilityTool) {
            DiagnosticWarning(
                EventName = "ACCESSIBILITY_TOOL_MISSING",
                MessageText = "service is not registered as an accessibility tool; " +
                        "screens that mark their views accessibilityDataSensitive will return " +
                        "a null window root and nothing can be read, tapped or scrolled"
            )
        }


        TargetScope.Reset()
        SearchRoute.Reset()
        CustomerNameScope.Reset()
        CustomerRoute.Reset()
        ChainCustomerName = ChainCustomerNameVal
        IsChainedCustomerLegStarted = false
        if (ModeVal == CaptureMode.CUSTOMER && TargetCustomerNamesVal.isNotEmpty()) {
            CustomerNameScope.Arm(
                NumbersVal = TargetCustomerNamesVal.map { NameText ->
                    NormalisedName(NameText = NameText)
                },
                NameHintsVal = emptyMap()
            )
            CustomerRoute.Arm(TargetsVal = TargetCustomerNamesVal)
        }
        if (ModeVal == CaptureMode.POLICY && CapturePolicyDetailsEnabled) {
            TargetScope.Arm(
                NumbersVal = TargetPolicyNumbersVal,
                NameHintsVal = TargetNameHintsVal
            )
            SearchRoute.Arm(
                TargetsVal = TargetScope.Numbers,
                NameHintsVal = TargetScope.NameHints
            )
        }

        if (IsResumedSession) SeedFromStoredSession()

        if (CustomerNameScope.IsActive) {
            VisitedCustomerNames.removeAll(CustomerNameScope.Numbers.toSet())
        }

        if (ModeVal == CaptureMode.POLICY && ResumeFromPageVal > 1) {
            PolicyResumeTargetPage = ResumeFromPageVal
            DiagnosticInfo(
                EventName = "POLICY_RESUME_TARGET",
                MessageText = "source=session track=${CurrentResumeTrack()} " +
                        "page=$ResumeFromPageVal session=$CurrentSessionId"
            )
        }

        if (ModeVal == CaptureMode.CUSTOMER && ResumeFromPageVal > 1) {
            TargetCustomerPage = ResumeFromPageVal
            DiagnosticInfo(
                EventName = "CUSTOMER_RESUME_TARGET",
                MessageText = "source=session page=$ResumeFromPageVal session=$CurrentSessionId"
            )
        }

        CaptureSessionState.OnSessionStarted(
            ModeVal = ModeVal,
            SessionIdVal = CurrentSessionId
        )
        StartParseThread()
        ShowBubble()
        AcquireWakeLock()

        MainHandler.removeCallbacks(TickRunnable)
        MainHandler.post(TickRunnable)
    }


    private fun SeedFromStoredSession() {
        when (CurrentMode) {
            CaptureMode.POLICY -> {
                val StoredPolicies = PolicyRepository.GetCustomerPolicies(
                    ContextRef = this,
                    SessionId = CurrentSessionId
                )
                for (PolicyItem in StoredPolicies) {
                    if (PolicyItem.PolicyNumber.isEmpty()) continue
                    CapturedPolicyMap[PolicyItem.PolicyNumber] = PolicyItem
                }
                RebuildCapturedPolicyNodes()

                var SkippedDetailCount = 0
                if (CapturePolicyDetailsEnabled) {
                    for (PolicyItem in CapturedPolicyMap.values) {
                        if (RecordMerge.HasCompletePolicyDetails(PolicyItem = PolicyItem)) {
                            ProcessedPolicyDetailNumbers.add(PolicyItem.PolicyNumber)
                            SkippedDetailCount++
                        }
                    }
                }
                DiagnosticInfo(
                    EventName = "SESSION_RESUME",
                    MessageText = "session=$CurrentSessionId mode=POLICY " +
                            "seeded=${CapturedPolicyMap.size} " +
                            "detailsAlreadyComplete=$SkippedDetailCount " +
                            "nodes=${CapturedNodes.size}"
                )
            }

            CaptureMode.FUP -> {
                val StoredRenewals = PolicyRepository.GetFupPolicies(
                    ContextRef = this,
                    SessionId = CurrentSessionId
                )
                for (RenewalItem in StoredRenewals) {
                    if (RenewalItem.PolicyNumber.isEmpty()) continue
                    CapturedFupMap[RenewalRecordKey(RecordItem = RenewalItem)] = RenewalItem
                }
                RebuildCapturedRenewalNodes()
                val SkipRecord = PolicyRepository.GetRenewalSkips(
                    ContextRef = this,
                    SessionId = CurrentSessionId
                )
                if (SkipRecord != null) {
                    RenewalKnownBadPages.addAll(SkipRecord.PageList)
                    RenewalKnownBadSpanDays = SkipRecord.SpanDays ?: 0
                    RenewalKnownTotalPages = SkipRecord.TotalPages ?: 0
                }
                val KnownBadText = RenewalKnownBadPages
                    .joinToString(separator = ",")
                    .ifEmpty { "none" }
                DiagnosticInfo(
                    EventName = "SESSION_RESUME",
                    MessageText = "session=$CurrentSessionId mode=FUP " +
                            "seeded=${CapturedFupMap.size} nodes=${CapturedNodes.size} " +
                            "knownBadPages=$KnownBadText skipSpanDays=$RenewalKnownBadSpanDays"
                )
            }

            CaptureMode.CUSTOMER -> {
                val StoredPolicies = PolicyRepository.GetCustomerPolicies(
                    ContextRef = this,
                    SessionId = CurrentSessionId
                )
                for (PolicyItem in StoredPolicies) {
                    if (PolicyItem.PolicyNumber.isEmpty()) continue
                    CapturedPolicyMap[PolicyItem.PolicyNumber] = PolicyItem
                    SessionPolicyNumbers.add(PolicyItem.PolicyNumber)
                    if (RecordMerge.HasPersonalDetails(PolicyItem = PolicyItem)) {
                        FilledPolicyNumbers.add(PolicyItem.PolicyNumber)
                    }
                }
                VisitedCustomerNames.addAll(
                    PolicyRepository.GetVisitedCustomers(
                        ContextRef = this,
                        SessionId = CurrentSessionId
                    )
                )
                RebuildCapturedPolicyNodes()
                DiagnosticInfo(
                    EventName = "SESSION_RESUME",
                    MessageText = "session=$CurrentSessionId mode=CUSTOMER " +
                            "revisitFilled=$RevisitFilledEnabled " +
                            "scopePolicies=${SessionPolicyNumbers.size} " +
                            "alreadyFilled=${FilledPolicyNumbers.size} " +
                            "visitedCustomers=${VisitedCustomerNames.size} " +
                            "nodes=${CapturedNodes.size}"
                )
            }

            CaptureMode.RENEWAL_DUE -> {
                val StoredDue = PolicyRepository.GetRenewalDuePolicies(
                    ContextRef = this,
                    SessionId = CurrentSessionId
                )
                for (DueItem in StoredDue) {
                    if (DueItem.PolicyNumber.isEmpty()) continue
                    CapturedRenewalDueMap[DueItem.PolicyNumber] = DueItem
                    if (DueItem.HolderName.isNotEmpty()) {
                        VisitedRenewalDueCustomers.add(NormaliseCustomerKey(NameText = DueItem.HolderName))
                    }
                }
                RebuildCapturedRenewalDueNodes()
                DiagnosticInfo(
                    EventName = "SESSION_RESUME",
                    MessageText = "session=$CurrentSessionId mode=RENEWAL_DUE " +
                            "seeded=${CapturedRenewalDueMap.size} " +
                            "customers=${VisitedRenewalDueCustomers.size} " +
                            "nodes=${CapturedNodes.size}"
                )
            }

            CaptureMode.PS -> {
                DiagnosticInfo(
                    EventName = "SESSION_RESUME",
                    MessageText = "session=$CurrentSessionId mode=PS seeded=0 (unsupported)"
                )
            }
        }
    }

    fun SetPaused(PausedVal: Boolean) {
        if (!IsCapturing || IsPaused == PausedVal) return
        IsPaused = PausedVal
        if (PausedVal) {
            PausedAt = System.currentTimeMillis()
            StopAutoScroll()
        } else {
            PausedTotalMs += System.currentTimeMillis() - PausedAt
            PausedAt = 0L
        }
        CaptureSessionState.OnPausedChanged(IsPausedVal = PausedVal)
        DiagnosticInfo(
            EventName = "SESSION_STATE",
            MessageText = if (PausedVal) "Capture paused" else "Capture resumed"
        )
        RefreshBubble()
    }


    fun FinishCaptureSession() {
        if (!IsCapturing) return
        val EndedAt = System.currentTimeMillis()
        val NodeSnapshot = CapturedNodes.toList()

        DiagnosticInfo(
            EventName = "SESSION_FINISH",
            MessageText = "User/automation finished capture; nodes=${NodeSnapshot.size} " +
                    "records=${LatestRecords.size} policies=${CapturedPolicyMap.size}"
        )

        TeardownSession()

        val RecordList = try {
            when (CurrentMode) {
                CaptureMode.POLICY if CapturedPolicyMap.isNotEmpty() -> {
                    CaptureParsers.PreviewPolicies(Policies = CapturedPolicyMap.values.toList())
                }

                CaptureMode.FUP if CapturedFupMap.isNotEmpty() -> {
                    CaptureParsers.PreviewFupRecords(Records = CapturedFupMap.values.toList())
                }

                CaptureMode.RENEWAL_DUE if CapturedRenewalDueMap.isNotEmpty() -> {
                    CaptureParsers.PreviewRenewalDueRecords(
                        Records = CapturedRenewalDueMap.values.toList()
                    )
                }

                CaptureMode.CUSTOMER -> {
                    CaptureParsers.PreviewProfilePatches(
                        Patches = ProfilePatchMap.values.toList(),
                        NameMap = ProfilePatchNames
                    )
                }

                else -> {
                    CaptureParsers.Preview(ModeVal = CurrentMode, Nodes = NodeSnapshot)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

        CaptureSessionState.PublishPending(
            SessionObj = CaptureSession(
                SessionId = CurrentSessionId,
                Mode = CurrentMode,
                StartedAt = SessionStartedAt,
                EndedAt = EndedAt - PausedTotalMs,
                RawNodes = NodeSnapshot,
                Records = RecordList,
                PolicyRecords = if (CurrentMode == CaptureMode.CUSTOMER) {
                    ProfilePatchMap.values.toList()
                } else {
                    CapturedPolicyMap.values.toList()
                },
                FupRecords = CapturedFupMap.values.toList(),
                RenewalDueRecords = CapturedRenewalDueMap.values.toList(),
                GapRecords = SessionGapMap.values.toList(),
                CapturePolicyDetails = if (TargetScope.IsActive) {
                    PolicyRepository.GetSessionReference(
                        ContextRef = this,
                        SessionId = CurrentSessionId
                    )?.CapturePolicyDetails == true
                } else {
                    CapturePolicyDetailsEnabled
                },
                TargetPackage = LastPackageName,
                OriginActivity = OriginActivityName,
                TargetedPolicyNumbers = TargetScope.Numbers,
                ChainCustomerName = ChainCustomerName
            )
        )

        ReturnToOriginActivity()
    }

    fun DiscardCaptureSession() {
        if (!IsCapturing) return
        DiagnosticInfo(
            EventName = "SESSION_DISCARD",
            MessageText = "Capture discarded; nodes=${CapturedNodes.size} policies=${CapturedPolicyMap.size}"
        )
        TeardownSession()
        if (CurrentSessionId.isNotBlank() && CurrentResumeTrack().isNotEmpty()) {
            PolicyRepository.ClearPolicyResumeMark(
                ContextRef = this,
                SessionId = CurrentSessionId,
                TrackVal = CurrentResumeTrack()
            )
        }
        CapturedNodes.clear()
    }

    private fun TeardownSession() {
        IsCapturing = false
        IsPaused = false
        CancelOfflineWatch()
        MainHandler.removeCallbacks(TickRunnable)
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = false)
        StopRenewalAutomation(ResetStateVal = false)
        StopCustomerAutomation(ResetStateVal = false)
        CancelEventWindowCapture()
        HasExpandedCurrentPolicyScreen = false
        LoginFillInFlight = false
        RestoreSoftKeyboard()
        StopParseThread()
        RemoveBubble()
        ReleaseWakeLock()
        CaptureSessionState.OnSessionEnded()
    }

    private fun ReturnToOriginActivity() {
        if (OriginActivityName.isEmpty()) return
        try {
            val ReturnIntent = Intent().apply {
                setClassName(packageName, OriginActivityName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            startActivity(ReturnIntent)
        } catch (_: Exception) {
            // Background activity launch can be refused. The pending session is
            // still parked, and the activity picks it up on its next resume.
        }
    }


    private fun HandleScreenAutomation(
        PackageNameVal: String,
        RootNode: AccessibilityNodeInfo,
        VisibleNodes: List<String>
    ) {
        if (PackageNameVal == AppLauncherUtils.LIC_SUPER_APP_PACKAGE) {
            if (IsOfflineScreen(FreshNodes = VisibleNodes)) {
                HandleOfflineScreen()
                return
            }
            if (IsErrorSheetScreen(FreshNodes = VisibleNodes)) {
                HandleErrorSheet(RootNode = RootNode)
                return
            }
            NoteScreenSubstance(VisibleNodes = VisibleNodes)
        }
        NoteScreenWithoutError(NodeCount = VisibleNodes.size)

        if (PackageNameVal == AppLauncherUtils.LIC_SUPER_APP_PACKAGE) {
            val LoginPageVal = LoginPageOf(VisibleNodes = VisibleNodes)
            if (LoginPageVal != null) {
                HandleLoginScreen(
                    RootNode = RootNode,
                    VisibleNodes = VisibleNodes,
                    PageVal = LoginPageVal
                )
                return
            }
        }

        if (CurrentMode == CaptureMode.POLICY ||
            CurrentMode == CaptureMode.FUP ||
            CurrentMode == CaptureMode.RENEWAL_DUE ||
            CurrentMode == CaptureMode.CUSTOMER
        ) {
            if (PackageNameVal != AppLauncherUtils.LIC_SUPER_APP_PACKAGE) return

            if (CurrentMode == CaptureMode.CUSTOMER &&
                (CustomerRoute.IsArmed || CustomerRoute.IsBackingOut)
            ) {
                val IsBusyCustomerScreen = VisibleSheetKind(VisibleNodes = VisibleNodes) != null ||
                        IsCustomerDetailScreen(VisibleNodes = VisibleNodes)
                val IsEntryCustomerScreen = IsAgentHomeScreen(VisibleNodes = VisibleNodes) ||
                        IsCustomerPortfolioScreen(VisibleNodes = VisibleNodes) ||
                        IsCustomerDashboardScreen(VisibleNodes = VisibleNodes)
                if (CustomerRoute.HandleScreen(
                        VisibleNodes = VisibleNodes,
                        IsEntryVisible = IsEntryCustomerScreen,
                        IsBusyScreen = IsBusyCustomerScreen
                    )
                ) {
                    return
                }
            }

            if (CurrentMode == CaptureMode.POLICY &&
                SearchRoute.IsArmed &&
                !IsPolicyDetailScreen(VisibleNodes = VisibleNodes) &&
                SearchRoute.HandleScreen(
                    VisibleNodes = VisibleNodes,
                    IsEntryVisible = IsAgentHomeScreen(VisibleNodes = VisibleNodes)
                )
            ) {
                return
            }

            if (IsAgentHomeScreen(VisibleNodes = VisibleNodes)) {
                HandleAgentHomeScreen(RootNode = RootNode)
                return
            }
        }

        if (CurrentMode == CaptureMode.CUSTOMER) {
            HandleCustomerScreenAutomation(RootNode = RootNode, VisibleNodes = VisibleNodes)
            return
        }

        if (IsRenewalFamilyMode()) {
            if (PackageNameVal != AppLauncherUtils.LIC_SUPER_APP_PACKAGE) return
            HandleRenewalScreenAutomation(VisibleNodes = VisibleNodes)
            return
        }

        if (CurrentMode == CaptureMode.POLICY) {
            if (IsCustomerPortfolioScreen(VisibleNodes = VisibleNodes)) {
                IsPolicyDashboardScreenVisible = false
                HasExpandedCurrentPolicyScreen = false
                HasClickedHomeNavTab = true
                HomeNavClickAttempts = 0
                HomeNavLastAttemptAt = 0L
                if (IsPolicyDashboardActive || IsPolicyDashboardAutomationRunning) {
                    DiagnosticInfo(
                        EventName = "SCREEN_TRANSITION",
                        MessageText = "Returned to Customer Portfolio; policy dashboard state re-armed"
                    )
                    StopPolicyDashboardAutomation(ResetStateVal = true)
                }

                val CurrentTime = System.currentTimeMillis()
                if (HasClickedPortfolioPolicies &&
                    CurrentTime - PortfolioPoliciesLastAttemptAt >= PORTFOLIO_TRANSITION_TIMEOUT_MS
                ) {
                    DiagnosticWarning(
                        EventName = "POLICIES_TRANSITION_TIMEOUT",
                        MessageText = "Policies click did not leave Customer Portfolio after " +
                                "${PORTFOLIO_TRANSITION_TIMEOUT_MS}ms; allowing another attempt"
                    )
                    HasClickedPortfolioPolicies = false
                }

                val RetryDelayPassed = CurrentTime - PortfolioPoliciesLastAttemptAt >=
                        PORTFOLIO_CLICK_RETRY_MS
                if (!HasClickedPortfolioPolicies && RetryDelayPassed) {
                    PortfolioPoliciesClickAttempts++
                    PortfolioPoliciesLastAttemptAt = CurrentTime
                    DiagnosticInfo(
                        EventName = "POLICIES_CLICK_ATTEMPT",
                        MessageText = "attempt=$PortfolioPoliciesClickAttempts"
                    )
                    HasClickedPortfolioPolicies = ClickPortfolioPoliciesCard(RootNode = RootNode)
                }
                return
            }

            if (IsPolicyDetailScreen(VisibleNodes = VisibleNodes)) {
                IsPolicyDetailScreenActive = true
                IsPolicyDashboardScreenVisible = false
                TryAutoExpandPolicySections(VisibleNodes = VisibleNodes)
                return
            }

            if (SearchRoute.IsArmed &&
                SearchRoute.HandleScreen(
                    VisibleNodes = VisibleNodes,
                    IsEntryVisible = IsPolicyDashboardActive ||
                            IsPolicyDashboardScreen(VisibleNodes = VisibleNodes)
                )
            ) {
                return
            }

            if (IsPolicyDashboardActive || IsPolicyDashboardScreen(VisibleNodes = VisibleNodes)) {
                IsPolicyDashboardActive = true
                IsPolicyDetailScreenActive = false
                IsPolicyDashboardScreenVisible =
                    IsPolicyDashboardScreen(VisibleNodes = VisibleNodes)
                HasClickedPortfolioPolicies = true
                PortfolioPoliciesClickAttempts = 0
                PortfolioPoliciesLastAttemptAt = 0L
                HasExpandedCurrentPolicyScreen = false
                StartPolicyDashboardAutomation()
                return
            }

            return
        }

        HasExpandedCurrentPolicyScreen = false
        if (IsAutoScrollScreenReady(PackageNameVal = PackageNameVal, VisibleNodes = VisibleNodes)) {
            CurrentAutoScrollScreenSignature =
                VisibleNodes.joinToString(separator = "\u0001").hashCode()
            StartAutoScroll(ScreenSignature = CurrentAutoScrollScreenSignature)
        } else if (IsAutoScrolling) {
            StopAutoScroll()
        }
    }

    private enum class LoginPage { MPIN, PASSWORD }

    private fun LoginPageOf(VisibleNodes: List<String>): LoginPage? {
        val IsSetupScreen = VisibleNodes.any { NodeText ->
            LOGIN_SETUP_MARKERS.any { MarkerText ->
                NodeText.contains(MarkerText, ignoreCase = true)
            }
        }
        if (IsSetupScreen) return null

        val HasCompanion = VisibleNodes.any { NodeText ->
            NodeText.contains("Forgot mPIN", ignoreCase = true) ||
                    NodeText.contains("Forgot Password", ignoreCase = true) ||
                    NodeText.contains("OTP To Login", ignoreCase = true) ||
                    NodeText.contains(LOGIN_MPIN_LINK, ignoreCase = true) ||
                    NodeText.contains(LOGIN_PASSWORD_LINK, ignoreCase = true) ||
                    NodeText.trim().equals(LOGIN_BUTTON_LABEL, ignoreCase = true)
        }
        if (!HasCompanion) return null

        val HasMpinPrompt = VisibleNodes.any { NodeText ->
            NodeText.contains(LOGIN_MPIN_PROMPT, ignoreCase = true)
        }
        if (HasMpinPrompt) return LoginPage.MPIN

        val HasPasswordPrompt = VisibleNodes.any { NodeText ->
            NodeText.contains(LOGIN_PASSWORD_PROMPT, ignoreCase = true)
        }
        if (HasPasswordPrompt) return LoginPage.PASSWORD

        return null
    }

    private fun PageForMethod(MethodVal: CredentialStore.Method): LoginPage =
        if (MethodVal == CredentialStore.Method.MPIN) LoginPage.MPIN else LoginPage.PASSWORD

    private fun MethodNameOf(MethodVal: CredentialStore.Method): String =
        if (MethodVal == CredentialStore.Method.MPIN) "mpin" else "password"

    private fun IsLoginRejectionNotice(
        VisibleNodes: List<String>,
        MethodVal: CredentialStore.Method
    ): Boolean {
        val MarkerList = if (MethodVal == CredentialStore.Method.MPIN) {
            LOGIN_MPIN_REJECT_MARKERS
        } else {
            LOGIN_PASSWORD_REJECT_MARKERS
        }
        return VisibleNodes.any { NodeText ->
            MarkerList.any { MarkerText ->
                NodeText.contains(MarkerText, ignoreCase = true)
            }
        }
    }

    private fun HandleRejectedLogin(MethodVal: CredentialStore.Method) {
        LoginRejectedHandled = true
        LoginFillInFlight = false
        LoginAttemptCount = LOGIN_ATTEMPT_LIMIT
        LoginSkipLogged = true
        RestoreSoftKeyboard()
        CredentialStore.MarkRejected(ContextRef = this, MethodVal = MethodVal)
        DiagnosticWarning(
            EventName = "LOGIN_REJECTED",
            MessageText = "method=${MethodNameOf(MethodVal = MethodVal)} the app rejected the " +
                    "saved sign-in; automatic sign-in switched off so repeated attempts cannot " +
                    "lock the account, sign in by hand and save the new one"
        )
        ShowServiceToast(
            MessageText = getString(
                if (MethodVal == CredentialStore.Method.MPIN) {
                    R.string.credentials_rejected_mpin_toast
                } else {
                    R.string.credentials_rejected_password_toast
                }
            ),
            KindVal = AppToast.Kind.Error
        )
    }

    private fun HandleLoginScreen(
        RootNode: AccessibilityNodeInfo,
        VisibleNodes: List<String>,
        PageVal: LoginPage
    ) {
        val FilledMethod = LoginFilledMethod
        if (LoginSubmitted &&
            !LoginRejectedHandled &&
            FilledMethod != null &&
            IsLoginRejectionNotice(VisibleNodes = VisibleNodes, MethodVal = FilledMethod)
        ) {
            HandleRejectedLogin(MethodVal = FilledMethod)
            return
        }
        if (LoginRejectedHandled) return
        if (LoginFillInFlight) return

        val MethodVal = CredentialStore.MethodOf(ContextRef = this)
        val SecretText = CredentialStore.SecretFor(ContextRef = this, MethodVal = MethodVal)
        val AutoEnterOn = CredentialStore.IsAutoEnterOn(ContextRef = this)
        if (SecretText == null || !AutoEnterOn) {
            if (!LoginSkipLogged) {
                LoginSkipLogged = true
                DiagnosticInfo(
                    EventName = "LOGIN_SCREEN",
                    MessageText = if (SecretText == null) {
                        "The login screen is up and no ${MethodNameOf(MethodVal = MethodVal)} " +
                                "is saved; waiting for a manual sign-in"
                    } else {
                        "The login screen is up and automatic sign-in is off; waiting for a " +
                                "manual sign-in"
                    }
                )
            }
            return
        }

        if (LoginAttemptCount >= LOGIN_ATTEMPT_LIMIT) {
            if (!LoginSkipLogged) {
                LoginSkipLogged = true
                DiagnosticWarning(
                    EventName = "LOGIN_NOT_ACCEPTED",
                    MessageText = "The login screen is still up after the saved " +
                            "${MethodNameOf(MethodVal = MethodVal)} was entered; not trying " +
                            "again this run, sign in by hand"
                )
            }
            return
        }

        if (PageVal != PageForMethod(MethodVal = MethodVal)) {
            SwitchLoginMethod(RootNode = RootNode, MethodVal = MethodVal)
            return
        }

        LoginFillInFlight = true
        LoginAttemptCount++
        LoginSkipLogged = false
        LoginSubmitAttempts = 0
        HideSoftKeyboardForLogin()

        val FilledOk = if (MethodVal == CredentialStore.Method.MPIN) {
            FillMpinEntry(RootNode = RootNode, CodeText = SecretText)
        } else {
            FillPasswordEntry(RootNode = RootNode, PasswordText = SecretText)
        }

        if (!FilledOk) {
            LoginAttemptCount--
            LoginFillFailureCount++
            if (LoginFillFailureCount >= LOGIN_FILL_FAILURE_LIMIT) {
                LoginAttemptCount = LOGIN_ATTEMPT_LIMIT
                DiagnosticWarning(
                    EventName = "LOGIN_FILL_FAILED",
                    MessageText = "Could not type the saved " +
                            "${MethodNameOf(MethodVal = MethodVal)} after " +
                            "$LoginFillFailureCount tries; sign in by hand"
                )
            }
            ReleaseLoginFill(DelayMs = LOGIN_FILL_RETRY_MS)
            return
        }

        LoginFillFailureCount = 0
        LoginFilledMethod = MethodVal
        DiagnosticInfo(
            EventName = "LOGIN_FILLED",
            MessageText = "method=${MethodNameOf(MethodVal = MethodVal)} entered on the login " +
                    "screen; tapping Login next"
        )
        MainHandler.postDelayed({ SubmitLogin() }, LOGIN_SUBMIT_DELAY_MS)
    }

    private fun SwitchLoginMethod(
        RootNode: AccessibilityNodeInfo,
        MethodVal: CredentialStore.Method
    ) {
        if (LoginSwitchCount >= LOGIN_SWITCH_LIMIT) {
            if (!LoginSkipLogged) {
                LoginSkipLogged = true
                DiagnosticWarning(
                    EventName = "LOGIN_SWITCH_GAVE_UP",
                    MessageText = "The login screen did not swap to " +
                            "${MethodNameOf(MethodVal = MethodVal)} after $LoginSwitchCount " +
                            "taps; sign in by hand"
                )
            }
            return
        }

        val LabelText = if (MethodVal == CredentialStore.Method.MPIN) {
            LOGIN_MPIN_LINK
        } else {
            LOGIN_PASSWORD_LINK
        }
        LoginFillInFlight = true
        LoginSwitchCount++
        LoginSkipLogged = false

        if (TapLoginLink(RootNode = RootNode, LabelText = LabelText)) {
            DiagnosticInfo(
                EventName = "LOGIN_SWITCH",
                MessageText = "tapped '$LabelText' (attempt $LoginSwitchCount); no sign-in " +
                        "attempt spent"
            )
        } else {
            DiagnosticWarning(
                EventName = "LOGIN_SWITCH_NOT_TAPPED",
                MessageText = "label='$LabelText' attempt=$LoginSwitchCount"
            )
        }
        ReleaseLoginFill(DelayMs = LOGIN_SWITCH_SETTLE_MS)
    }

    private fun TapLoginLink(RootNode: AccessibilityNodeInfo, LabelText: String): Boolean {
        val LinkNodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            CollectNodesContaining(
                TargetNode = RootNode,
                NeedleText = LabelText,
                ResultList = LinkNodes
            )
            val TargetNode = LinkNodes.firstOrNull()
            if (TargetNode == null) {
                DiagnosticWarning(
                    EventName = "LOGIN_SWITCH_NOT_FOUND",
                    MessageText = "No '$LabelText' node on the login screen"
                )
                return false
            }

            val BoundsObj = NodeBoundsOf(NodeRef = TargetNode)
            DiagnosticInfo(
                EventName = "LOGIN_SWITCH_TARGET",
                MessageText = "label='$LabelText' bounds=$BoundsObj " +
                        "clickable=${TargetNode.isClickable}"
            )

            if (ClickNodeOrParent(StartNode = TargetNode)) return true
            if (!IsBoundsOnScreen(BoundsObj = BoundsObj)) return false
            return PerformTapGesture(
                XPos = BoundsObj.exactCenterX(),
                YPos = BoundsObj.exactCenterY()
            )
        } finally {
            for (NodeRef in LinkNodes) RecycleNode(NodeRef = NodeRef)
        }
    }

    private fun CollectNodesContaining(
        TargetNode: AccessibilityNodeInfo,
        NeedleText: String,
        ResultList: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                val MatchesNeedle = NodeTextValue(NodeRef = ChildNode)
                    .contains(NeedleText, ignoreCase = true)
                if (MatchesNeedle &&
                    IsBoundsOnScreen(BoundsObj = NodeBoundsOf(NodeRef = ChildNode))
                ) {
                    ResultList.add(ChildNode)
                    continue
                }
                try {
                    CollectNodesContaining(
                        TargetNode = ChildNode,
                        NeedleText = NeedleText,
                        ResultList = ResultList
                    )
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Node became stale while looking for a login link", ExceptionObj)
        }
    }

    private fun FillPasswordEntry(
        RootNode: AccessibilityNodeInfo,
        PasswordText: String
    ): Boolean {
        val EntryNodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            CollectLoginEntryNodes(TargetNode = RootNode, ResultList = EntryNodes)
            val TargetNode = EntryNodes.minByOrNull { NodeRef ->
                NodeBoundsOf(NodeRef = NodeRef).top
            }
            if (TargetNode == null) {
                DiagnosticWarning(
                    EventName = "LOGIN_FIELD_NOT_FOUND",
                    MessageText = "The password screen exposed no editable field"
                )
                return false
            }
            return SetNodeText(NodeRef = TargetNode, TextValue = PasswordText)
        } finally {
            for (NodeRef in EntryNodes) RecycleNode(NodeRef = NodeRef)
        }
    }

    private fun FillMpinEntry(RootNode: AccessibilityNodeInfo, CodeText: String): Boolean {
        val EntryNodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            CollectLoginEntryNodes(TargetNode = RootNode, ResultList = EntryNodes)
            if (EntryNodes.isEmpty()) return false
            if (EntryNodes.size == 1) {
                return SetNodeText(NodeRef = EntryNodes.first(), TextValue = CodeText)
            }

            val BoxNodes = MpinBoxRow(EntryNodes = EntryNodes)
            if (BoxNodes.size != CodeText.length) {
                DiagnosticWarning(
                    EventName = "LOGIN_BOXES_NOT_FOUND",
                    MessageText = "Login screen exposed ${EntryNodes.size} entry field(s) and no " +
                            "row of ${CodeText.length} boxes"
                )
                return false
            }

            var FilledCount = 0
            for (DigitIndex in CodeText.indices) {
                val DigitText = CodeText[DigitIndex].toString()
                if (SetNodeText(NodeRef = BoxNodes[DigitIndex], TextValue = DigitText)) {
                    FilledCount++
                }
            }
            return FilledCount == CodeText.length
        } finally {
            for (NodeRef in EntryNodes) RecycleNode(NodeRef = NodeRef)
        }
    }

    private fun MpinBoxRow(
        EntryNodes: List<AccessibilityNodeInfo>
    ): List<AccessibilityNodeInfo> {
        return EntryNodes
            .groupBy { NodeRef -> NodeBoundsOf(NodeRef = NodeRef).top / MPIN_ROW_BUCKET_PX }
            .values
            .maxByOrNull { RowNodes -> RowNodes.size }
            .orEmpty()
            .sortedBy { NodeRef -> NodeBoundsOf(NodeRef = NodeRef).left }
    }

    private fun CollectLoginEntryNodes(
        TargetNode: AccessibilityNodeInfo,
        ResultList: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                val ClassNameText = ChildNode.className?.toString().orEmpty()
                val IsTextEntry = ChildNode.isEditable ||
                        ClassNameText.contains("EditText", ignoreCase = true)
                if (IsTextEntry && IsBoundsOnScreen(BoundsObj = NodeBoundsOf(NodeRef = ChildNode))) {
                    ResultList.add(ChildNode)
                    continue
                }
                try {
                    CollectLoginEntryNodes(TargetNode = ChildNode, ResultList = ResultList)
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Node became stale while looking for the mPIN boxes", ExceptionObj)
        }
    }

    private fun NodeBoundsOf(NodeRef: AccessibilityNodeInfo): Rect {
        val BoundsObj = Rect()
        try {
            NodeRef.getBoundsInScreen(BoundsObj)
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Node became stale while reading bounds", ExceptionObj)
        }
        return BoundsObj
    }

    private fun SetNodeText(NodeRef: AccessibilityNodeInfo, TextValue: String): Boolean {
        return try {
            NodeRef.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val ArgsBundle = Bundle()
            ArgsBundle.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                TextValue
            )
            NodeRef.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, ArgsBundle)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "LOGIN_SET_TEXT_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            false
        }
    }

    private fun SubmitLogin() {
        if (!IsCapturing) {
            LoginFillInFlight = false
            RestoreSoftKeyboard()
            return
        }

        if (IsKeyboardWindowVisible()) {
            DiagnosticInfo(
                EventName = "LOGIN_KEYBOARD_DISMISS",
                MessageText = "Keyboard is covering the Login button; dismissing it first"
            )
            performGlobalAction(GLOBAL_ACTION_BACK)
            RetryLoginSubmit(ReasonText = "keyboard was still up")
            return
        }

        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        if (RootNode == null) {
            RetryLoginSubmit(ReasonText = "no readable root")
            return
        }

        val ClickedOk = try {
            ClickLoginButton(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (ClickedOk) {
            LoginSubmitted = true
            DiagnosticInfo(
                EventName = "LOGIN_TAPPED",
                MessageText = "Login tapped after entering the saved mPIN"
            )
            ReleaseLoginFill(DelayMs = LOGIN_RELEASE_DELAY_MS)
            return
        }

        RetryLoginSubmit(ReasonText = "Login button had no usable bounds")
    }

    private fun RetryLoginSubmit(ReasonText: String) {
        LoginSubmitAttempts++
        if (LoginSubmitAttempts >= LOGIN_SUBMIT_RETRY_LIMIT) {
            DiagnosticWarning(
                EventName = "LOGIN_NOT_TAPPED",
                MessageText = "mPIN is filled in but Login could not be tapped after " +
                        "$LoginSubmitAttempts tries ($ReasonText); tap it yourself"
            )
            ReleaseLoginFill(DelayMs = LOGIN_RELEASE_DELAY_MS)
            return
        }
        DiagnosticInfo(
            EventName = "LOGIN_RETRY",
            MessageText = "attempt=$LoginSubmitAttempts reason=$ReasonText"
        )
        MainHandler.postDelayed({ SubmitLogin() }, LOGIN_SUBMIT_RETRY_MS)
    }

    private fun IsKeyboardWindowVisible(): Boolean {
        return try {
            windows.any { WindowRef ->
                WindowRef.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Window list unavailable while checking for the keyboard", ExceptionObj)
            false
        }
    }

    private fun HideSoftKeyboardForLogin() {
        if (LoginKeyboardHidden) return
        LoginKeyboardHidden = try {
            softKeyboardController.setShowMode(SHOW_MODE_HIDDEN)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "LOGIN_KEYBOARD_HIDE_FAILED",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            false
        }
        if (!LoginKeyboardHidden) {
            DiagnosticWarning(
                EventName = "LOGIN_KEYBOARD_HIDE_FAILED",
                MessageText = "Keyboard suppression was refused; Login may stay covered"
            )
        }
    }

    private fun RestoreSoftKeyboard() {
        if (!LoginKeyboardHidden) return
        LoginKeyboardHidden = false
        try {
            softKeyboardController.setShowMode(SHOW_MODE_AUTO)
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Could not restore the keyboard show mode", ExceptionObj)
        }
    }

    private fun ClickLoginButton(RootNode: AccessibilityNodeInfo): Boolean {
        val LabelNodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            CollectLoginButtonNodes(TargetNode = RootNode, ResultList = LabelNodes)
            val TargetNode = LabelNodes.maxByOrNull { NodeRef ->
                NodeBoundsOf(NodeRef = NodeRef).centerY()
            }
            if (TargetNode == null) {
                DiagnosticWarning(
                    EventName = "LOGIN_NOT_FOUND",
                    MessageText = "No Login node on the login screen"
                )
                return false
            }

            val BoundsObj = NodeBoundsOf(NodeRef = TargetNode)
            DiagnosticInfo(
                EventName = "LOGIN_TARGET",
                MessageText = "bounds=$BoundsObj clickable=${TargetNode.isClickable} " +
                        "keyboard=${IsKeyboardWindowVisible()}"
            )

            if (ClickNodeOrParent(StartNode = TargetNode)) return true
            if (!IsBoundsOnScreen(BoundsObj = BoundsObj)) return false
            return PerformTapGesture(
                XPos = BoundsObj.exactCenterX(),
                YPos = BoundsObj.exactCenterY()
            )
        } finally {
            for (NodeRef in LabelNodes) RecycleNode(NodeRef = NodeRef)
        }
    }

    private fun CollectLoginButtonNodes(
        TargetNode: AccessibilityNodeInfo,
        ResultList: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                val IsLoginLabel = NodeTextValue(NodeRef = ChildNode)
                    .trim()
                    .equals(LOGIN_BUTTON_LABEL, ignoreCase = true)
                if (IsLoginLabel && IsBoundsOnScreen(BoundsObj = NodeBoundsOf(NodeRef = ChildNode))) {
                    ResultList.add(ChildNode)
                    continue
                }
                try {
                    CollectLoginButtonNodes(TargetNode = ChildNode, ResultList = ResultList)
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Node became stale while looking for the Login button", ExceptionObj)
        }
    }

    private fun ReleaseLoginFill(DelayMs: Long) {
        MainHandler.postDelayed({
            LoginFillInFlight = false
            RestoreSoftKeyboard()
        }, DelayMs)
    }

    private fun IsAutoScrollScreenReady(
        PackageNameVal: String,
        VisibleNodes: List<String>
    ): Boolean {
        if (PackageNameVal != ExpectedTargetPackage()) return false

        val HasParsedRecord = try {
            CaptureParsers.Preview(ModeVal = CurrentMode, Nodes = VisibleNodes).isNotEmpty()
        } catch (_: Exception) {
            false
        }
        return HasParsedRecord || VisibleNodes.any { NodeText ->
            when (CurrentMode) {
                CaptureMode.PS ->
                    NodeText.contains("servicing", ignoreCase = true) ||
                            NodeText.contains("policy status", ignoreCase = true)

                CaptureMode.FUP ->
                    NodeText.contains("renewal history", ignoreCase = true) ||
                            NodeText.equals("FUP", ignoreCase = true)

                CaptureMode.RENEWAL_DUE ->
                    NodeText.contains("renewals due", ignoreCase = true)

                CaptureMode.POLICY -> false

                CaptureMode.CUSTOMER -> false
            }
        }
    }

    private fun ExpectedTargetPackage(): String {
        return if (CurrentMode == CaptureMode.PS) {
            AgentPackageName
        } else {
            AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        }
    }


    private fun IsCustomerPortfolioScreen(VisibleNodes: List<String>): Boolean {
        val HasPortfolioTitle = VisibleNodes.any { NodeText ->
            NodeText.contains("Customer Portfolio", ignoreCase = true)
        }
        val HasPortfolioCard = VisibleNodes.any { NodeText ->
            NodeText.contains("Your Portfolio", ignoreCase = true)
        }
        val HasPolicies = VisibleNodes.any { NodeText ->
            Regex("(?i)\\bPolicies\\b").containsMatchIn(NodeText)
        }
        val HasCustomers = VisibleNodes.any { NodeText ->
            Regex("(?i)\\bCustomers\\b").containsMatchIn(NodeText)
        }
        return HasPortfolioTitle && HasPortfolioCard && HasPolicies && HasCustomers
    }


    private fun IsAgentHomeScreen(VisibleNodes: List<String>): Boolean {
        if (IsCustomerPortfolioScreen(VisibleNodes = VisibleNodes)) return false
        if (IsPolicyDashboardScreen(VisibleNodes = VisibleNodes)) return false
        if (IsPolicyDetailScreen(VisibleNodes = VisibleNodes)) return false
        if (IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes)) return false
        if (IsRenewalHistoryScreen(VisibleNodes = VisibleNodes)) return false

        val HasHomeMarker = VisibleNodes.any { NodeText ->
            NodeText.contains("Performance Summary", ignoreCase = true) ||
                    NodeText.contains("Quick Links", ignoreCase = true) ||
                    NodeText.contains("Total Commission Earnings", ignoreCase = true)
        }
        return HasHomeMarker
    }

    private fun HomeNavTabLabel(): String {
        return if (IsRenewalFamilyMode()) HOME_TAB_RENEWALS else HOME_TAB_CUSTOMERS
    }

    private fun HandleAgentHomeScreen(RootNode: AccessibilityNodeInfo) {
        IsPolicyDashboardScreenVisible = false
        IsPolicyDetailScreenActive = false
        HasExpandedCurrentPolicyScreen = false

        val TabLabel = HomeNavTabLabel()
        val CurrentTime = System.currentTimeMillis()
        if (HasClickedHomeNavTab &&
            CurrentTime - HomeNavLastAttemptAt >= HOME_NAV_TRANSITION_TIMEOUT_MS
        ) {
            DiagnosticWarning(
                EventName = "HOME_NAV_TRANSITION_TIMEOUT",
                MessageText = "$TabLabel tab click did not leave Home after " +
                        "${HOME_NAV_TRANSITION_TIMEOUT_MS}ms; allowing another attempt"
            )
            HasClickedHomeNavTab = false
            HomeNavRevealCount = 0
        }

        val RetryDelayPassed = CurrentTime - HomeNavLastAttemptAt >= HOME_NAV_CLICK_RETRY_MS
        if (HasClickedHomeNavTab || !RetryDelayPassed) return

        val TabCandidates = RankedBottomNavTabBounds(RootNode = RootNode, TabLabel = TabLabel)
        val TabBounds = TabCandidates.firstOrNull()
        val IsTabTappable = TabBounds != null && IsBottomNavigationBounds(BoundsObj = TabBounds)

        if (!IsTabTappable && HomeNavRevealCount < HOME_NAV_REVEAL_LIMIT) {
            if (CurrentTime - HomeNavRevealAt < HOME_NAV_REVEAL_RETRY_MS) return
            HomeNavRevealAt = CurrentTime
            HomeNavRevealCount++
            val NudgeForward = when {
                TabBounds == null -> HomeNavRevealCount % 2 == 1
                TabBounds.centerY() >= resources.displayMetrics.heightPixels -> true
                else -> false
            }
            val NudgeAccepted = PerformPolicyRevealNudge(ForwardVal = NudgeForward)
            DiagnosticInfo(
                EventName = "HOME_NAV_REVEAL",
                MessageText = "tab=$TabLabel is not tappable yet " +
                        "bounds=${TabBounds ?: "absent"} " +
                        "screenHeight=${resources.displayMetrics.heightPixels} " +
                        "nudge=${if (NudgeForward) "content-up" else "content-down"} " +
                        "attempt=$HomeNavRevealCount accepted=$NudgeAccepted"
            )
            return
        }

        HomeNavClickAttempts++
        HomeNavLastAttemptAt = CurrentTime
        DiagnosticInfo(
            EventName = "HOME_NAV_CLICK_ATTEMPT",
            MessageText = "tab=$TabLabel attempt=$HomeNavClickAttempts " +
                    "bounds=${TabBounds ?: "absent"} tappable=$IsTabTappable " +
                    "candidates=${TabCandidates.size} nudges=$HomeNavRevealCount"
        )
        HasClickedHomeNavTab = ClickHomeBottomNavTab(
            RootNode = RootNode,
            TabLabel = TabLabel,
            RankedBounds = TabCandidates
        )
        if (!HasClickedHomeNavTab) HomeNavRevealCount = 0
    }

    private fun RankedBottomNavTabBounds(
        RootNode: AccessibilityNodeInfo,
        TabLabel: String
    ): List<Rect> {
        val AllBounds = mutableListOf<Rect>()
        CollectBottomNavTabBounds(
            TargetNode = RootNode,
            TabLabel = TabLabel,
            OutList = AllBounds
        )
        val BarBounds = AllBounds.filter { BoundsObj ->
            IsBottomNavigationBounds(BoundsObj = BoundsObj)
        }
        val PoolList = BarBounds.ifEmpty { AllBounds }
        return PoolList.sortedWith(
            compareBy(
                { BoundsObj -> BoundsObj.width() },
                { BoundsObj -> -BoundsObj.centerY() }
            )
        )
    }

    private fun CollectBottomNavTabBounds(
        TargetNode: AccessibilityNodeInfo,
        TabLabel: String,
        OutList: MutableList<Rect>
    ) {
        try {
            val MatchText = NodeTextValue(NodeRef = TargetNode).trim()
            if (MatchText.equals(TabLabel, ignoreCase = true)) {
                val MatchBounds = Rect()
                TargetNode.getBoundsInScreen(MatchBounds)
                if (!MatchBounds.isEmpty) OutList.add(Rect(MatchBounds))
            }
            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                try {
                    CollectBottomNavTabBounds(
                        TargetNode = ChildNode,
                        TabLabel = TabLabel,
                        OutList = OutList
                    )
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Bottom nav label node became stale", ExceptionObj)
        }
    }

    private fun ClickHomeBottomNavTab(
        RootNode: AccessibilityNodeInfo,
        TabLabel: String,
        RankedBounds: List<Rect>
    ): Boolean {
        val PreferAccessibilityClick = HomeNavClickAttempts > 2
        if (RankedBounds.isNotEmpty() && !PreferAccessibilityClick) {
            val CandidateIndex = (HomeNavClickAttempts - 1).coerceAtLeast(0) % RankedBounds.size
            val ChosenBounds = RankedBounds[CandidateIndex]
            DiagnosticInfo(
                EventName = "HOME_NAV_RANKED_CANDIDATE",
                MessageText = "tab=$TabLabel index=$CandidateIndex of ${RankedBounds.size} " +
                        "bounds=$ChosenBounds all=${RankedBounds.joinToString(separator = " ")}"
            )
            if (TapBottomNavTab(TabLabel = TabLabel, TabBounds = ChosenBounds)) {
                DiagnosticInfo(
                    EventName = "HOME_NAV_CLICKED",
                    MessageText = "tab=$TabLabel ranked candidate=$CandidateIndex " +
                            "bounds=$ChosenBounds"
                )
                return true
            }
        }
        val MatchList = try {
            RootNode.findAccessibilityNodeInfosByText(TabLabel)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "HOME_NAV_CANDIDATES",
                MessageText = "Search for [$TabLabel] failed: ${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
            emptyList()
        }
        DiagnosticInfo(
            EventName = "HOME_NAV_CANDIDATES",
            MessageText = "tab=$TabLabel findAccessibilityNodeInfosByText returned " +
                    "${MatchList.size} candidate(s)"
        )
        try {
            for ((CandidateIndex, MatchNode) in MatchList.withIndex()) {
                val MatchText = NodeTextValue(NodeRef = MatchNode)
                if (!MatchText.trim().equals(TabLabel, ignoreCase = true)) continue

                val MatchBounds = Rect()
                MatchNode.getBoundsInScreen(MatchBounds)
                val IsBottomNavArea = IsBottomNavigationBounds(BoundsObj = MatchBounds)
                DiagnosticInfo(
                    EventName = "HOME_NAV_CANDIDATE",
                    MessageText = "index=$CandidateIndex text=[$MatchText] " +
                            "class=${MatchNode.className} clickable=${MatchNode.isClickable} " +
                            "visible=${MatchNode.isVisibleToUser} bounds=$MatchBounds " +
                            "bottomNav=$IsBottomNavArea"
                )
                if (!IsBottomNavArea) continue

                if (PreferAccessibilityClick && ClickNodeOrParent(StartNode = MatchNode)) {
                    DiagnosticInfo(
                        EventName = "HOME_NAV_CLICKED",
                        MessageText = "tab=$TabLabel accessibility click accepted for " +
                                "candidate=$CandidateIndex bounds=$MatchBounds"
                    )
                    return true
                }
                if (TapBottomNavTab(TabLabel = TabLabel, TabBounds = MatchBounds)) {
                    DiagnosticInfo(
                        EventName = "HOME_NAV_CLICKED",
                        MessageText = "tab=$TabLabel coordinate tap accepted for " +
                                "candidate=$CandidateIndex bounds=$MatchBounds"
                    )
                    return true
                }
                if (ClickNodeOrParent(StartNode = MatchNode)) {
                    DiagnosticInfo(
                        EventName = "HOME_NAV_CLICKED",
                        MessageText = "tab=$TabLabel accessibility fallback accepted for " +
                                "candidate=$CandidateIndex bounds=$MatchBounds"
                    )
                    return true
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Unable to click the $TabLabel bottom tab", ExceptionObj)
            DiagnosticWarning(
                EventName = "HOME_NAV_CLICK_ERROR",
                MessageText = "tab=$TabLabel ${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
        } finally {
            for (MatchNode in MatchList) RecycleNode(NodeRef = MatchNode)
        }
        if (ClickHomeBottomNavTabByTraversal(
                TargetNode = RootNode,
                TabLabel = TabLabel,
                PreferAccessibilityClick = PreferAccessibilityClick
            )
        ) {
            return true
        }
        val OffScreenBounds = RankedBounds.firstOrNull()
        if (OffScreenBounds != null && HomeNavClickAttempts < HOME_NAV_BLIND_TAP_AFTER_ATTEMPTS) {
            DiagnosticWarning(
                EventName = "HOME_NAV_TAB_OFFSCREEN",
                MessageText = "tab=$TabLabel bounds=$OffScreenBounds " +
                        "screen=${resources.displayMetrics.widthPixels}x" +
                        "${resources.displayMetrics.heightPixels}; skipping the fixed-position " +
                        "tap and nudging the page instead"
            )
            return false
        }
        if (TapHomeBottomNavTabFallback(TabLabel = TabLabel)) {
            DiagnosticInfo(
                EventName = "HOME_NAV_CLICKED",
                MessageText = "tab=$TabLabel fixed-position fallback accepted"
            )
            return true
        }
        DiagnosticWarning(
            EventName = "HOME_NAV_CLICK_FAILED",
            MessageText = "No usable $TabLabel tab was clicked; retry is still enabled"
        )
        return false
    }

    private fun ClickHomeBottomNavTabByTraversal(
        TargetNode: AccessibilityNodeInfo,
        TabLabel: String,
        PreferAccessibilityClick: Boolean
    ): Boolean {
        try {
            val MatchText = NodeTextValue(NodeRef = TargetNode)
            if (MatchText.trim().equals(TabLabel, ignoreCase = true)) {
                val MatchBounds = Rect()
                TargetNode.getBoundsInScreen(MatchBounds)
                if (IsBottomNavigationBounds(BoundsObj = MatchBounds)) {
                    DiagnosticInfo(
                        EventName = "HOME_NAV_TREE_CANDIDATE",
                        MessageText = "tab=$TabLabel text=[$MatchText] class=${TargetNode.className} " +
                                "clickable=${TargetNode.isClickable} bounds=$MatchBounds " +
                                "preferAccessibility=$PreferAccessibilityClick"
                    )
                    if (PreferAccessibilityClick && ClickNodeOrParent(StartNode = TargetNode)) {
                        return true
                    }
                    if (TapBottomNavTab(TabLabel = TabLabel, TabBounds = MatchBounds)) {
                        return true
                    }
                    if (ClickNodeOrParent(StartNode = TargetNode)) return true
                }
            }

            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                try {
                    if (ClickHomeBottomNavTabByTraversal(
                            TargetNode = ChildNode,
                            TabLabel = TabLabel,
                            PreferAccessibilityClick = PreferAccessibilityClick
                        )
                    ) {
                        return true
                    }
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "$TabLabel tab node became stale during traversal", ExceptionObj)
        }
        return false
    }

    private fun BottomNavTabXRatio(TabLabel: String): Float {
        return if (TabLabel.equals(HOME_TAB_RENEWALS, ignoreCase = true)) {
            HOME_RENEWALS_TAB_X_RATIO
        } else {
            HOME_CUSTOMERS_TAB_X_RATIO
        }
    }

    private fun TapBottomNavTab(TabLabel: String, TabBounds: Rect): Boolean {
        val DisplayMetricsObj = resources.displayMetrics
        val CentreX = TabBounds.centerX()
        val UseNodeCentre = !TabBounds.isEmpty &&
                CentreX > 0 &&
                CentreX < DisplayMetricsObj.widthPixels
        val TargetX =
            if (UseNodeCentre) CentreX.toFloat()
            else DisplayMetricsObj.widthPixels * BottomNavTabXRatio(TabLabel = TabLabel)

        val TapAccepted = PerformTapGesture(
            XPos = TargetX,
            YPos = TabBounds.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "HOME_NAV_TAB_TAP",
            MessageText = "tab=$TabLabel x=$TargetX y=${TabBounds.centerY()} " +
                    "bounds=$TabBounds accepted=$TapAccepted " +
                    "source=${if (UseNodeCentre) "node-bounds" else "width-ratio"}"
        )
        return TapAccepted
    }

    private fun TapHomeBottomNavTabFallback(TabLabel: String): Boolean {
        val DisplayMetricsObj = resources.displayMetrics
        val TabXRatio = BottomNavTabXRatio(TabLabel = TabLabel)
        val TargetX = DisplayMetricsObj.widthPixels * TabXRatio
        val TargetY = DisplayMetricsObj.heightPixels * HOME_NAV_TAB_Y_RATIO
        val TapAccepted = PerformTapGesture(XPos = TargetX, YPos = TargetY)
        DiagnosticInfo(
            EventName = "HOME_NAV_TAB_TAP",
            MessageText = "tab=$TabLabel x=$TargetX y=$TargetY accepted=$TapAccepted " +
                    "attempt=$HomeNavClickAttempts"
        )
        return TapAccepted
    }

    private fun IsBottomNavigationBounds(BoundsObj: Rect): Boolean {
        if (!IsBoundsOnScreen(BoundsObj = BoundsObj)) return false

        val DisplayMetricsObj = resources.displayMetrics
        val RatioThreshold = DisplayMetricsObj.heightPixels * HOME_BOTTOM_NAV_TOP_RATIO
        val BandThreshold = DisplayMetricsObj.heightPixels -
                HOME_BOTTOM_NAV_BAND_DP * DisplayMetricsObj.density
        return BoundsObj.centerY() >= minOf(RatioThreshold, BandThreshold)
    }

    private fun IsPolicyDashboardScreen(VisibleNodes: List<String>): Boolean {
        if (VisibleNodes.any { NodeText ->
                NodeText.contains("Policy Dashboard", ignoreCase = true)
            }
        ) {
            return true
        }

        val HasPolicyCount = VisibleNodes.any { NodeText ->
            NodeText.contains("Policy(ies)", ignoreCase = true) ||
                    NodeText.contains("Based on selected filters", ignoreCase = true)
        }
        return HasPolicyCount && ParsePolicyPageInfo(VisibleNodes = VisibleNodes) != null
    }

    private fun IsPolicyDetailScreen(VisibleNodes: List<String>): Boolean {
        return VisibleNodes.any { NodeText ->
            NodeText.contains("Detailed Policy View", ignoreCase = true)
        }
    }

    private fun ClickPortfolioPoliciesCard(RootNode: AccessibilityNodeInfo): Boolean {
        val MatchList = try {
            RootNode.findAccessibilityNodeInfosByText("Policies")
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICIES_CANDIDATES",
                MessageText = "Search failed: ${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
            emptyList()
        }
        DiagnosticInfo(
            EventName = "POLICIES_CANDIDATES",
            MessageText = "findAccessibilityNodeInfosByText returned ${MatchList.size} candidate(s)"
        )
        try {
            for ((CandidateIndex, MatchNode) in MatchList.withIndex()) {
                val MatchText = NodeTextValue(NodeRef = MatchNode)
                val IsPoliciesLabel = MatchText.equals("Policies", ignoreCase = true) ||
                        MatchText.matches(Regex("(?i)^\\d+\\s+Policies$"))

                val MatchBounds = Rect()
                MatchNode.getBoundsInScreen(MatchBounds)
                DiagnosticInfo(
                    EventName = "POLICIES_CANDIDATE",
                    MessageText = "index=$CandidateIndex text=[$MatchText] " +
                            "class=${MatchNode.className} clickable=${MatchNode.isClickable} " +
                            "enabled=${MatchNode.isEnabled} visible=${MatchNode.isVisibleToUser} " +
                            "bounds=$MatchBounds labelMatch=$IsPoliciesLabel"
                )
                if (!IsPoliciesLabel) continue
                if (MatchBounds.centerY() > resources.displayMetrics.heightPixels * 0.65f) {
                    DiagnosticInfo(
                        EventName = "POLICIES_CANDIDATE_REJECTED",
                        MessageText = "index=$CandidateIndex rejected because it is below the portfolio-card area"
                    )
                    continue
                }


                if (TapPortfolioPoliciesArrow(LabelBounds = MatchBounds)) {
                    DiagnosticInfo(
                        EventName = "POLICIES_CLICKED",
                        MessageText = "Policies-arrow coordinate tap accepted for " +
                                "candidate=$CandidateIndex labelBounds=$MatchBounds"
                    )
                    return true
                }
                if (ClickNodeOrParent(StartNode = MatchNode)) {
                    Log.d(LOG_TAG, "Opened Policies from Customer Portfolio")
                    DiagnosticInfo(
                        EventName = "POLICIES_CLICKED",
                        MessageText = "Accessibility fallback accepted for " +
                                "candidate=$CandidateIndex bounds=$MatchBounds"
                    )
                    return true
                }
                DiagnosticWarning(
                    EventName = "POLICIES_CLICK_REJECTED",
                    MessageText = "Neither accessibility click nor coordinate tap was accepted for " +
                            "candidate=$CandidateIndex bounds=$MatchBounds"
                )
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Unable to click the Policies portfolio card", ExceptionObj)
            DiagnosticWarning(
                EventName = "POLICIES_CLICK_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        } finally {
            for (MatchNode in MatchList) RecycleNode(NodeRef = MatchNode)
        }
        if (ClickPortfolioPoliciesByTraversal(TargetNode = RootNode)) {
            return true
        }
        if (TapPortfolioPoliciesArrow(LabelBounds = null)) {
            DiagnosticInfo(
                EventName = "POLICIES_CLICKED",
                MessageText = "Fixed-position Policies-arrow fallback accepted"
            )
            return true
        }
        DiagnosticWarning(
            EventName = "POLICIES_CLICK_FAILED",
            MessageText = "No usable Policies node was clicked; retry is still enabled"
        )
        return false
    }

    private fun ClickPortfolioPoliciesByTraversal(TargetNode: AccessibilityNodeInfo): Boolean {
        try {
            val MatchText = NodeTextValue(NodeRef = TargetNode)
            val IsPoliciesLabel = MatchText.equals("Policies", ignoreCase = true) ||
                    MatchText.matches(Regex("(?i)^\\d+\\s+Policies$"))
            if (IsPoliciesLabel) {
                val MatchBounds = Rect()
                TargetNode.getBoundsInScreen(MatchBounds)
                val IsPortfolioArea = IsBoundsOnScreen(BoundsObj = MatchBounds) &&
                        MatchBounds.centerY() <= resources.displayMetrics.heightPixels * 0.65f
                DiagnosticInfo(
                    EventName = "POLICIES_TREE_CANDIDATE",
                    MessageText = "text=[$MatchText] class=${TargetNode.className} " +
                            "clickable=${TargetNode.isClickable} visible=${TargetNode.isVisibleToUser} " +
                            "bounds=$MatchBounds portfolioArea=$IsPortfolioArea"
                )
                if (IsPortfolioArea) {
                    if (TapPortfolioPoliciesArrow(LabelBounds = MatchBounds)) {
                        DiagnosticInfo(
                            EventName = "POLICIES_CLICKED",
                            MessageText = "Tree traversal Policies-arrow tap accepted; " +
                                    "labelBounds=$MatchBounds"
                        )
                        return true
                    }
                    if (ClickNodeOrParent(StartNode = TargetNode)) {
                        DiagnosticInfo(
                            EventName = "POLICIES_CLICKED",
                            MessageText = "Tree traversal accessibility fallback accepted; " +
                                    "bounds=$MatchBounds"
                        )
                        return true
                    }
                }
            }

            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    if (ClickPortfolioPoliciesByTraversal(TargetNode = ChildNode)) return true
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICIES_TREE_SEARCH_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
        return false
    }

    private fun TapPortfolioPoliciesArrow(LabelBounds: Rect?): Boolean {
        val DisplayMetricsObj = resources.displayMetrics
        val TargetY = LabelBounds
            ?.takeIf { BoundsObj -> IsBoundsOnScreen(BoundsObj = BoundsObj) }
            ?.centerY()
            ?.toFloat()
            ?: (DisplayMetricsObj.heightPixels * PORTFOLIO_POLICIES_ARROW_Y_FALLBACK_RATIO)
        val TargetX = DisplayMetricsObj.widthPixels * PORTFOLIO_POLICIES_ARROW_X_RATIO
        val TapAccepted = PerformTapGesture(XPos = TargetX, YPos = TargetY)
        DiagnosticInfo(
            EventName = "POLICIES_ARROW_TAP",
            MessageText = "x=$TargetX y=$TargetY labelBounds=${LabelBounds ?: "none"} " +
                    "accepted=$TapAccepted attempt=$PortfolioPoliciesClickAttempts"
        )
        return TapAccepted
    }

    private fun StartPolicyDashboardAutomation() {
        if (SearchRoute.IsArmed) return
        if (IsPolicyDashboardAutomationRunning || IsPolicyDashboardComplete) return
        val CurrentTime = System.currentTimeMillis()
        if (CurrentTime < PolicyAutomationRetryAfter) return

        IsPolicyDashboardAutomationRunning = true
        PolicyAutomationRetryAfter = 0L
        PolicyPageRetryCount = 0
        PolicyReturnToTopCount = 0
        PolicyScrollStallCount = 0
        Log.d(
            LOG_TAG,
            "Policy Dashboard automation started at page $PolicyCurrentPage of $PolicyTotalPages"
        )
        DiagnosticInfo(
            EventName = "POLICY_AUTOMATION_START",
            MessageText = "page=$PolicyCurrentPage total=$PolicyTotalPages " +
                    "captured=${CapturedPolicyMap.size} recoveryCount=$PolicyAutomationFailureCount " +
                    "resumeTarget=$PolicyResumeTargetPage"
        )
        SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
            StartPolicyPageWork()
        }
    }

    private fun StartPolicyPageWork() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        if (MaybeJumpToResumeTarget()) return
        if (!CapturePolicyDetailsEnabled) {
            ScrollPolicyDashboardPage()
            return
        }
        if (PolicyCurrentPage <= 0 || LatestPolicyPageNumbers.isEmpty()) {
            PolicyPageRetryCount++
            if (PolicyPageRetryCount >= POLICY_PAGE_RETRY_LIMIT) {
                FailPolicyDashboardAutomation("Policy cards were not ready for detail capture")
            } else {
                SchedulePolicyAction(DelayMs = POLICY_PAGE_LOAD_DELAY_MS) {
                    StartPolicyPageWork()
                }
            }
            return
        }
        PolicyPageRetryCount = 0
        PreparePolicyDetailsForCurrentPage()
    }

    private fun PreparePolicyDetailsForCurrentPage() {
        PolicyDetailQueue = TargetScope.Filter(
            PolicyNumbers = LatestPolicyPageNumbers
                .distinct()
                .filter { PolicyNumber -> !ProcessedPolicyDetailNumbers.contains(PolicyNumber) }
        )
        PolicyDetailQueueIndex = 0
        PolicyDetailScrollAttempts = 0
        PolicyDetailOpenAttempts = 0
        PolicyDetailReturnAttempts = 0
        DiagnosticInfo(
            EventName = "POLICY_DETAIL_PAGE_START",
            MessageText = "page=$PolicyCurrentPage/$PolicyTotalPages queued=${PolicyDetailQueue.size} " +
                    "alreadyProcessed=${ProcessedPolicyDetailNumbers.size}"
        )
        if (PolicyDetailQueue.isEmpty()) {
            ScrollPolicyDashboardPage()
        } else {
            ProcessNextPolicyDetail()
        }
    }

    private fun ProcessNextPolicyDetail() {
        while (PolicyDetailQueueIndex < PolicyDetailQueue.size &&
            ProcessedPolicyDetailNumbers.contains(PolicyDetailQueue[PolicyDetailQueueIndex])
        ) {
            PolicyDetailQueueIndex++
        }
        if (PolicyDetailQueueIndex >= PolicyDetailQueue.size) {
            PolicyDetailCurrentPolicyNumber = ""
            PolicyDetailScrollAttempts = 0
            DiagnosticInfo(
                EventName = "POLICY_DETAIL_PAGE_COMPLETE",
                MessageText = "page=$PolicyCurrentPage processed=${PolicyDetailQueue.size}"
            )
            SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                ScrollPolicyDashboardPage()
            }
            return
        }

        val TargetPolicyNumber = PolicyDetailQueue[PolicyDetailQueueIndex]
        PolicyDetailCurrentPolicyNumber = TargetPolicyNumber
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            SchedulePolicyAction(DelayMs = POLICY_SCROLL_SETTLE_MS) {
                ProcessNextPolicyDetail()
            }
            return
        }
        val OpenResult = try {
            ClickPolicyDetailArrow(
                RootNode = RootNode,
                PolicyNumber = TargetPolicyNumber
            )
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        when (OpenResult) {
            PolicyDetailOpenResult.CLICKED -> {
                PolicyDetailScrollAttempts = 0
                PolicyDetailOpenAttempts++
                PolicyDetailOriginPage = PolicyCurrentPage
                IsPolicyDetailScreenActive = false
                IsPolicyDashboardScreenVisible = false
                LatestPolicyDetailNodes = emptyList()
                PolicySectionRetryRounds = 0
                PolicyDetailSweepCount = 0
                LastPolicyDetailSweepSignature = 0
                HasExpandedCurrentPolicyScreen = false
                DiagnosticInfo(
                    EventName = "POLICY_DETAIL_OPEN",
                    MessageText = "page=$PolicyCurrentPage index=${PolicyDetailQueueIndex + 1}/" +
                            "${PolicyDetailQueue.size} policy=$TargetPolicyNumber " +
                            "originPage=$PolicyDetailOriginPage"
                )
                SchedulePolicyAction(DelayMs = POLICY_DETAIL_OPEN_DELAY_MS) {
                    WaitForPolicyDetailScreen()
                }
            }

            PolicyDetailOpenResult.NEED_SCROLL -> {
                PolicyDetailScrollAttempts++
                if (PolicyDetailScrollAttempts > POLICY_DETAIL_SCROLL_LIMIT) {
                    SkipCurrentPolicyDetail(ReasonText = "Policy card did not become visible")
                    return
                }
                // Keep every discovery movement in the same direction. The WebView exposes
                // more than one scrollable accessibility node, so ACTION_SCROLL_FORWARD can
                // select a different container and undo the preceding gesture.
                val ScrollAccepted = PerformPolicyRevealNudge()
                DiagnosticInfo(
                    EventName = "POLICY_DETAIL_FIND_SCROLL",
                    MessageText = "policy=$TargetPolicyNumber attempt=$PolicyDetailScrollAttempts " +
                            "method=reveal-nudge " +
                            "accepted=$ScrollAccepted page=$PolicyCurrentPage " +
                            "originPage=$PolicyDetailOriginPage"
                )
                SchedulePolicyAction(DelayMs = POLICY_SCROLL_SETTLE_MS) {
                    ProcessNextPolicyDetail()
                }
            }

            PolicyDetailOpenResult.FAILED -> {
                PolicyDetailScrollAttempts++
                if (PolicyDetailScrollAttempts >= POLICY_PAGE_RETRY_LIMIT) {
                    SkipCurrentPolicyDetail(ReasonText = "Right-arrow tap was not accepted")
                } else {
                    SchedulePolicyAction(DelayMs = POLICY_SCROLL_SETTLE_MS) {
                        ProcessNextPolicyDetail()
                    }
                }
            }
        }
    }

    private fun WaitForPolicyDetailScreen() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        if (!IsPolicyDetailScreenActive) {
            if (SearchRoute.IsDriving) {
                if (PolicyDetailOpenAttempts < POLICY_PAGE_RETRY_LIMIT) {
                    PolicyDetailOpenAttempts++
                    SchedulePolicyAction(DelayMs = POLICY_DETAIL_OPEN_DELAY_MS) {
                        WaitForPolicyDetailScreen()
                    }
                    return
                }
                SearchRoute.OnDetailOpenFailed()
                return
            }
            if (PolicyDetailOpenAttempts >= POLICY_PAGE_RETRY_LIMIT) {
                SkipCurrentPolicyDetail(ReasonText = "Detailed Policy View did not open")
            } else {
                SchedulePolicyAction(DelayMs = POLICY_DETAIL_OPEN_DELAY_MS) {
                    ProcessNextPolicyDetail()
                }
            }
            return
        }

        PolicyDetailScrollAttempts = 0
        PolicyDetailOpenAttempts = 0
        SchedulePolicyAction(DelayMs = POLICY_DETAIL_EXPAND_DELAY_MS) {
            VerifyPolicySectionExpansionAndReturn()
        }
    }

    private fun VerifyPolicySectionExpansionAndReturn() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val MissingSections = MissingExpandedPolicySections(Nodes = LatestPolicyDetailNodes)
        if (MissingSections.isNotEmpty() &&
            PolicySectionRetryRounds < POLICY_SECTION_RETRY_ROUND_LIMIT
        ) {
            PolicySectionRetryRounds++
            DiagnosticWarning(
                EventName = "POLICY_SECTION_CONTENT_MISSING",
                MessageText = "policy=$PolicyDetailCurrentPolicyNumber " +
                        "round=$PolicySectionRetryRounds retrying=" + MissingSections.joinToString()
            )
            val SeekableSections = MissingSections.filter { SectionLabel ->
                !PolicySectionsInFlight.contains(SectionLabel)
            }
            for ((SectionIndex, SectionLabel) in SeekableSections.withIndex()) {
                ScheduleSectionExpansionAttempt(
                    LabelText = SectionLabel,
                    DelayMs = SectionIndex * POLICY_DETAIL_SECTION_STEP_MS,
                    AttemptCount = 0
                )
            }

            val RetryVerificationDelay =
                MissingSections.size * POLICY_DETAIL_SECTION_STEP_MS +
                        POLICY_SECTION_ATTEMPT_LIMIT * POLICY_SECTION_SCROLL_SETTLE_MS +
                        POLICY_DETAIL_EXPAND_RETRY_SETTLE_MS
            SchedulePolicyAction(DelayMs = RetryVerificationDelay) {
                VerifyPolicySectionExpansionAndReturn()
            }
            return
        }

        DiagnosticInfo(
            EventName = "POLICY_SECTION_VERIFIED",
            MessageText = "policy=$PolicyDetailCurrentPolicyNumber " +
                    "rounds=$PolicySectionRetryRounds missingAfterRetry=" +
                    MissingSections.joinToString().ifEmpty { "none" }
        )


        PolicyDetailSweepCount = 0
        LastPolicyDetailSweepSignature = 0
        SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
            SweepPolicyDetailScreen()
        }
    }


    private fun SweepPolicyDetailScreen() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val CurrentSignature = LatestPolicyDetailNodes
            .joinToString(separator = "")
            .hashCode()
        val HasSettled = PolicyDetailSweepCount > 0 &&
                CurrentSignature == LastPolicyDetailSweepSignature
        val ReachedLimit = PolicyDetailSweepCount >= POLICY_DETAIL_SWEEP_LIMIT

        if (HasSettled || ReachedLimit || !IsPolicyDetailScreenActive) {
            DiagnosticInfo(
                EventName = "POLICY_DETAIL_SWEEP_END",
                MessageText = "policy=$PolicyDetailCurrentPolicyNumber " +
                        "steps=$PolicyDetailSweepCount settled=$HasSettled limit=$ReachedLimit " +
                        "detailActive=$IsPolicyDetailScreenActive"
            )
            FinishPolicyDetailAndReturn()
            return
        }

        LastPolicyDetailSweepSignature = CurrentSignature
        PolicyDetailSweepCount++
        val ScrollAccepted = PerformPolicyScroll(
            ForwardVal = true,
            PreferAccessibilityAction = false
        )
        DiagnosticInfo(
            EventName = "POLICY_DETAIL_SWEEP",
            MessageText = "policy=$PolicyDetailCurrentPolicyNumber step=$PolicyDetailSweepCount " +
                    "accepted=$ScrollAccepted"
        )
        SchedulePolicyAction(DelayMs = POLICY_DETAIL_SWEEP_SETTLE_MS) {
            SweepPolicyDetailScreen()
        }
    }

    private fun FinishPolicyDetailAndReturn() {
        ProcessedPolicyDetailNumbers.add(PolicyDetailCurrentPolicyNumber)
        PolicyDetailReturnAttempts = 0
        DiagnosticInfo(
            EventName = "POLICY_DETAIL_READY",
            MessageText = "policy=$PolicyDetailCurrentPolicyNumber " +
                    "returningToPage=$PolicyDetailOriginPage"
        )
        performGlobalAction(GLOBAL_ACTION_BACK)
        SchedulePolicyAction(DelayMs = POLICY_DETAIL_RETURN_DELAY_MS) {
            WaitForPolicyDashboardReturn()
        }
    }


    private fun MissingExpandedPolicySections(Nodes: List<String>): List<String> {
        val MergedRecord = CapturedPolicyMap[PolicyDetailCurrentPolicyNumber]

        val HasPolicyDetails = MergedRecord?.TermPPT?.isNotEmpty() == true ||
                Nodes.any { NodeText ->
                    Regex("Term\\s*/\\s*PPT", RegexOption.IGNORE_CASE).containsMatchIn(NodeText)
                }
        val HasCommissions = MergedRecord?.CommissionType?.isNotEmpty() == true ||
                MergedRecord?.CommissionDateOfPayment?.isNotEmpty() == true ||
                MergedRecord?.CommissionPaidAmount?.isNotEmpty() == true ||
                Nodes.any { NodeText ->
                    NodeText.contains("Commission Type", ignoreCase = true) ||
                            NodeText.contains("Date of Premium Payment", ignoreCase = true)
                }
        val HasKeyDates = MergedRecord?.DateOfCommencement?.isNotEmpty() == true ||
                MergedRecord?.DateOfMaturity?.isNotEmpty() == true ||
                MergedRecord?.EndOfPremiumPayingTerm?.isNotEmpty() == true ||
                Nodes.any { NodeText ->
                    NodeText.contains("Date of Commencement", ignoreCase = true) ||
                            NodeText.contains("Date of Maturity", ignoreCase = true)
                }

        val MissingSections = mutableListOf<String>()
        if (!HasPolicyDetails) MissingSections.add("Policy Details")
        if (!HasCommissions) MissingSections.add("Commissions")
        if (!HasKeyDates) MissingSections.add("Key Dates")
        return MissingSections
    }

    private fun WaitForPolicyDashboardReturn() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        if (SearchRoute.IsDriving) {
            SearchRoute.OnDetailReturn()
            return
        }
        if (IsPolicyDashboardScreenVisible &&
            !IsPolicyDetailScreenActive &&
            IsPolicyPageSelectorVisible
        ) {
            PolicyDetailQueueIndex++
            PolicyDetailScrollAttempts = 0
            PolicyDetailOpenAttempts = 0
            PolicyDetailReturnAttempts = 0
            HasExpandedCurrentPolicyScreen = false
            if (PolicyDetailOriginPage > 0 && PolicyCurrentPage != PolicyDetailOriginPage) {
                SetPolicyJump(
                    TargetPage = PolicyDetailOriginPage,
                    ReasonVal = POLICY_JUMP_DETAIL_RESTORE
                )
                PolicyReturnToTopCount = 0
                PolicyPageRetryCount = 0
                DiagnosticWarning(
                    EventName = "POLICY_DETAIL_PAGE_RESET",
                    MessageText = "policy=$PolicyDetailCurrentPolicyNumber origin=" +
                            "$PolicyDetailOriginPage returned=$PolicyCurrentPage; restoring"
                )
                SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                    ReturnToPolicyPageSelector()
                }
                return
            }
            SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                ProcessNextPolicyDetail()
            }
            return
        }

        PolicyDetailReturnAttempts++
        if (PolicyDetailReturnAttempts >= POLICY_DETAIL_RETURN_LIMIT) {
            FailPolicyDashboardAutomation(
                "Could not return from policy $PolicyDetailCurrentPolicyNumber"
            )
            return
        }
        if (IsPolicyDetailScreenActive) performGlobalAction(GLOBAL_ACTION_BACK)
        SchedulePolicyAction(DelayMs = POLICY_DETAIL_RETURN_DELAY_MS) {
            WaitForPolicyDashboardReturn()
        }
    }

    private fun SkipCurrentPolicyDetail(ReasonText: String) {
        DiagnosticWarning(
            EventName = "POLICY_DETAIL_SKIPPED",
            MessageText = "policy=$PolicyDetailCurrentPolicyNumber page=$PolicyCurrentPage " +
                    "reason=[$ReasonText]"
        )
        if (SearchRoute.IsDriving) {
            PolicyDetailScrollAttempts = 0
            PolicyDetailOpenAttempts = 0
            SearchRoute.OnDetailOpenFailed()
            return
        }
        ProcessedPolicyDetailNumbers.add(PolicyDetailCurrentPolicyNumber)
        PolicyDetailQueueIndex++
        PolicyDetailScrollAttempts = 0
        PolicyDetailOpenAttempts = 0
        SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
            ProcessNextPolicyDetail()
        }
    }

    private fun ScrollPolicyDashboardPage() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val BeforeScrollSignature = LatestPolicyVisibleSignature

        val PreferAccessibilityAction = PolicyScrollStallCount > 0
        val ScrollAccepted = PerformPolicyScroll(
            ForwardVal = true,
            PreferAccessibilityAction = PreferAccessibilityAction
        )
        DiagnosticInfo(
            EventName = "POLICY_SCROLL_FORWARD",
            MessageText = "page=$PolicyCurrentPage/$PolicyTotalPages accepted=$ScrollAccepted " +
                    "method=${if (PreferAccessibilityAction) "accessibility" else "gesture"} " +
                    "visibleSignature=$BeforeScrollSignature captured=${CapturedPolicyMap.size}"
        )
        if (!ScrollAccepted) {
            BeginNextPolicyPage()
            return
        }

        SchedulePolicyAction(DelayMs = POLICY_SCROLL_SETTLE_MS) {
            CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            if (LatestPolicyVisibleSignature == BeforeScrollSignature) {
                PolicyScrollStallCount++
            } else {
                PolicyScrollStallCount = 0
            }
            DiagnosticInfo(
                EventName = "POLICY_SCROLL_RESULT",
                MessageText = "page=$PolicyCurrentPage signatureBefore=$BeforeScrollSignature " +
                        "signatureAfter=$LatestPolicyVisibleSignature stalls=$PolicyScrollStallCount"
            )
            val NewlyVisiblePolicyDetails = CapturePolicyDetailsEnabled &&
                    LatestPolicyPageNumbers.any { PolicyNumber ->
                        !ProcessedPolicyDetailNumbers.contains(PolicyNumber) &&
                                TargetScope.Allows(PolicyNumber = PolicyNumber)
                    }
            if (NewlyVisiblePolicyDetails) {
                DiagnosticInfo(
                    EventName = "POLICY_DETAIL_DISCOVERED",
                    MessageText = "page=$PolicyCurrentPage visible=${LatestPolicyPageNumbers.size} " +
                            "processed=${ProcessedPolicyDetailNumbers.size}"
                )
                PreparePolicyDetailsForCurrentPage()
            } else if (PolicyScrollStallCount >= POLICY_SCROLL_STALL_LIMIT) {
                BeginNextPolicyPage()
            } else {
                SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                    ScrollPolicyDashboardPage()
                }
            }
        }
    }

    private fun BeginNextPolicyPage() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        DiagnosticInfo(
            EventName = "POLICY_PAGE_END",
            MessageText = "page=$PolicyCurrentPage total=$PolicyTotalPages " +
                    "selectorVisible=$IsPolicyPageSelectorVisible captured=${CapturedPolicyMap.size}"
        )
        SavePolicyResumeProgress(IsCompleteVal = false)
        if (TargetScope.IsActive &&
            TargetScope.Numbers.all { PolicyNumber ->
                ProcessedPolicyDetailNumbers.contains(PolicyNumber)
            }
        ) {
            DiagnosticInfo(
                EventName = "POLICY_TARGET_COMPLETE",
                MessageText = "route=page-walk targets=${TargetScope.Describe()} " +
                        "page=$PolicyCurrentPage/$PolicyTotalPages"
            )
            CompletePolicyDashboardAutomation()
            return
        }
        if (PolicyCurrentPage > 0 &&
            PolicyTotalPages > 0 &&
            PolicyCurrentPage >= PolicyTotalPages
        ) {
            CompletePolicyDashboardAutomation()
            return
        }

        PolicyReturnToTopCount = 0
        PolicyPageRetryCount = 0
        SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
            ReturnToPolicyPageSelector()
        }
    }

    private fun ReturnToPolicyPageSelector() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val IsSelectorActuallyVisible = PolicyCurrentPage > 0 &&
                IsPolicyPageSelectorControlVisible(CurrentPage = PolicyCurrentPage)
        DiagnosticInfo(
            EventName = "POLICY_SELECTOR_VISIBILITY",
            MessageText = "page=$PolicyCurrentPage parsedInTree=$IsPolicyPageSelectorVisible " +
                    "controlOnScreen=$IsSelectorActuallyVisible returnAttempts=$PolicyReturnToTopCount"
        )
        if (IsSelectorActuallyVisible) {
            if (!IsPolicyPageJumpPending &&
                PolicyResumeTargetPage <= 0 &&
                PolicyTotalPages > 0 &&
                PolicyCurrentPage >= PolicyTotalPages
            ) {
                CompletePolicyDashboardAutomation()
            } else {
                OpenPolicyPageSelector()
            }
            return
        }

        PolicyReturnToTopCount++
        if (PolicyReturnToTopCount > POLICY_RETURN_TO_TOP_LIMIT) {
            FailPolicyDashboardAutomation("Could not return to the page selector")
            return
        }

        val ScrollAccepted = PerformPolicyScroll(
            ForwardVal = false,
            PreferAccessibilityAction = PolicyReturnToTopCount % 2 == 0
        )
        DiagnosticInfo(
            EventName = "POLICY_SCROLL_BACK",
            MessageText = "attempt=$PolicyReturnToTopCount accepted=$ScrollAccepted " +
                    "page=$PolicyCurrentPage selectorVisible=$IsPolicyPageSelectorVisible"
        )
        if (!ScrollAccepted && PolicyCurrentPage > 0) {
            OpenPolicyPageSelector()
            return
        }

        SchedulePolicyAction(DelayMs = POLICY_SCROLL_SETTLE_MS) {
            ReturnToPolicyPageSelector()
        }
    }

    private fun OpenPolicyPageSelector() {
        PolicyExpectedPage = if (PolicyJumpTarget > 0) {
            PolicyJumpTarget
        } else if (PolicyCurrentPage > 0) {
            PolicyCurrentPage + 1
        } else {
            2
        }

        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            RetryPolicyPageNavigation("Page selector root is unavailable")
            return
        }

        val SelectorClicked: Boolean
        val SelectorAdvanced: Boolean
        try {
            SelectorClicked = ClickPolicyPageSelector(
                RootNode = RootNode,
                CurrentPage = PolicyCurrentPage
            )
            SelectorAdvanced = !SelectorClicked && AdvancePolicyPageSelector(
                RootNode = RootNode,
                CurrentPage = PolicyCurrentPage
            )
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (SelectorClicked) {
            DiagnosticInfo(
                EventName = "POLICY_SELECTOR",
                MessageText = "Selector opened; current=$PolicyCurrentPage expected=$PolicyExpectedPage"
            )
            SchedulePolicyAction(DelayMs = POLICY_PAGE_SELECTOR_DELAY_MS) {
                SelectNextPolicyPage()
            }
        } else if (SelectorAdvanced) {
            DiagnosticInfo(
                EventName = "POLICY_SELECTOR",
                MessageText = "Spinner scroll accepted; current=$PolicyCurrentPage expected=$PolicyExpectedPage"
            )
            WaitForPolicyPageLoad()
        } else {
            RetryPolicyPageNavigation("Could not open the page selector")
        }
    }

    private fun SelectNextPolicyPage() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ResetPolicySelectorScrollState()
            performGlobalAction(GLOBAL_ACTION_BACK)
            RetryPolicyPageNavigation("Page options are unavailable")
            return
        }

        val PageSelected = try {
            ClickPolicyPageOption(RootNode = RootNode, PageNumber = PolicyExpectedPage)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (!PageSelected) {
            if (ScrollPolicyPageSelectorList()) {
                SchedulePolicyAction(DelayMs = POLICY_SELECTOR_SCROLL_SETTLE_MS) {
                    SelectNextPolicyPage()
                }
                return
            }
            ResetPolicySelectorScrollState()
            performGlobalAction(GLOBAL_ACTION_BACK)
            RetryPolicyPageNavigation("Could not select page $PolicyExpectedPage")
            return
        }

        ResetPolicySelectorScrollState()
        DiagnosticInfo(
            EventName = "POLICY_PAGE_SELECTED",
            MessageText = "selected=$PolicyExpectedPage; waiting for dashboard to load"
        )

        WaitForPolicyPageLoad()
    }

    private fun WaitForPolicyPageLoad() {
        SchedulePolicyAction(DelayMs = POLICY_PAGE_LOAD_DELAY_MS) {
            CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)

            if (!IsPolicyPageSelectorVisible || PolicyCurrentPage != PolicyExpectedPage) {
                PolicyPageRetryCount++
                DiagnosticWarning(
                    EventName = "POLICY_PAGE_WAIT",
                    MessageText = "expected=$PolicyExpectedPage actual=$PolicyCurrentPage " +
                            "selectorVisible=$IsPolicyPageSelectorVisible attempt=$PolicyPageRetryCount"
                )
                if (PolicyPageRetryCount >= POLICY_PAGE_RETRY_LIMIT) {
                    FailPolicyDashboardAutomation(
                        "Page $PolicyExpectedPage did not finish loading"
                    )
                } else {
                    WaitForPolicyPageLoad()
                }
                return@SchedulePolicyAction
            }

            if (PolicyTotalPages in 1..<PolicyCurrentPage) {
                CompletePolicyDashboardAutomation()
                return@SchedulePolicyAction
            }

            PolicyPageRetryCount = 0
            PolicyScrollStallCount = 0
            if (PolicyCurrentPage > PolicyLastFailurePage) {
                PolicyAutomationFailureCount = 0
                PolicyLastFailurePage = 0
            }
            Log.d(
                LOG_TAG,
                "Capturing Policy Dashboard page $PolicyCurrentPage of $PolicyTotalPages"
            )
            DiagnosticInfo(
                EventName = "POLICY_PAGE_LOADED",
                MessageText = "page=$PolicyCurrentPage total=$PolicyTotalPages " +
                        "captured=${CapturedPolicyMap.size}"
            )
            if (IsRestoringPolicyPageAfterDetail) {
                DiagnosticInfo(
                    EventName = "POLICY_DETAIL_PAGE_RESTORED",
                    MessageText = "policy=$PolicyDetailCurrentPolicyNumber " +
                            "restoredPage=$PolicyCurrentPage queueIndex=$PolicyDetailQueueIndex"
                )
                ClearPolicyJump()
                PolicyDetailOriginPage = PolicyCurrentPage
                SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                    ProcessNextPolicyDetail()
                }
                return@SchedulePolicyAction
            }
            ClearPolicyJump()
            SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                StartPolicyPageWork()
            }
        }
    }

    private fun SetPolicyJump(TargetPage: Int, ReasonVal: String) {
        PolicyJumpTarget = TargetPage
        PolicyJumpReason = ReasonVal
    }

    private fun ClearPolicyJump() {
        PolicyJumpTarget = 0
        PolicyJumpReason = POLICY_JUMP_NONE
    }

    private fun ResetPolicySelectorScrollState() {
        PolicySelectorScrollCount = 0
        PolicySelectorHighestOption = 0
        PolicySelectorScrollStalls = 0
    }

    private fun CurrentResumeTrack(): String {
        return PolicyResumeTrack.OfMode(
            ModeVal = CurrentMode,
            CapturePolicyDetails = CapturePolicyDetailsEnabled
        )
    }

    private fun SavePolicyResumeProgress(IsCompleteVal: Boolean) {
        if (CurrentMode != CaptureMode.POLICY) return
        if (TargetScope.IsActive) return
        if (CurrentSessionId.isBlank()) return
        if (PolicyCurrentPage <= 0 || PolicyTotalPages <= 0) return
        val TrackVal = CurrentResumeTrack()
        try {
            PolicyRepository.SavePolicyResumeMark(
                ContextRef = this,
                MarkObj = PolicyResumeMark(
                    SessionId = CurrentSessionId,
                    Track = TrackVal,
                    LastCompletedPage = PolicyCurrentPage,
                    TotalPages = PolicyTotalPages,
                    CapturedCount = CapturedPolicyMap.size,
                    SavedAt = System.currentTimeMillis(),
                    IsComplete = IsCompleteVal
                )
            )
            DiagnosticInfo(
                EventName = "POLICY_RESUME_MARK",
                MessageText = "track=$TrackVal page=$PolicyCurrentPage total=$PolicyTotalPages " +
                        "captured=${CapturedPolicyMap.size} complete=$IsCompleteVal"
            )
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_RESUME_MARK_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
    }

    private fun OutstandingCustomerCount(): Int {
        return ProcessedCustomerKeys.count { KeyText ->
            !VisitedCustomerNames.contains(KeyText.substringAfter('|'))
        }
    }

    private fun SaveCustomerResumeProgress(IsCompleteVal: Boolean) {
        if (CurrentMode != CaptureMode.CUSTOMER) return
        if (CustomerNameScope.IsActive) return
        if (CurrentSessionId.isBlank()) return
        if (TargetCustomerPage <= 0 || CustomerTotalPages <= 0) return
        val OutstandingCount = OutstandingCustomerCount()
        try {
            PolicyRepository.SavePolicyResumeMark(
                ContextRef = this,
                MarkObj = PolicyResumeMark(
                    SessionId = CurrentSessionId,
                    Track = PolicyResumeTrack.CUSTOMER,
                    LastCompletedPage = TargetCustomerPage,
                    TotalPages = CustomerTotalPages,
                    CapturedCount = FilledPolicyNumbers.size,
                    OutstandingBefore = OutstandingCount,
                    SavedAt = System.currentTimeMillis(),
                    IsComplete = IsCompleteVal
                )
            )
            DiagnosticInfo(
                EventName = "CUSTOMER_RESUME_MARK",
                MessageText = "page=$TargetCustomerPage total=$CustomerTotalPages " +
                        "filled=${FilledPolicyNumbers.size} outstanding=$OutstandingCount " +
                        "complete=$IsCompleteVal"
            )
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "CUSTOMER_RESUME_MARK_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
    }

    private fun MaybeJumpToResumeTarget(): Boolean {
        if (PolicyResumeTargetPage <= 0) return false

        if (PolicyCurrentPage <= 0 || PolicyTotalPages <= 0) {
            PolicyResumeWaitCount++
            if (PolicyResumeWaitCount > POLICY_RESUME_WAIT_LIMIT) {
                DiagnosticWarning(
                    EventName = "POLICY_RESUME_ABANDONED",
                    MessageText = "reason=[page never detected] target=$PolicyResumeTargetPage " +
                            "waits=$PolicyResumeWaitCount"
                )
                PolicyResumeTargetPage = 0
                return false
            }
            SchedulePolicyAction(DelayMs = POLICY_PAGE_LOAD_DELAY_MS) {
                StartPolicyPageWork()
            }
            return true
        }
        PolicyResumeWaitCount = 0

        val ClampedTarget = PolicyResumeTarget.ClampToTotal(
            TargetPage = PolicyResumeTargetPage,
            TotalPages = PolicyTotalPages
        )
        if (ClampedTarget != PolicyResumeTargetPage) {
            DiagnosticWarning(
                EventName = "POLICY_RESUME_CLAMPED",
                MessageText = "target=$PolicyResumeTargetPage clamped=$ClampedTarget " +
                        "total=$PolicyTotalPages"
            )
            PolicyResumeTargetPage = ClampedTarget
        }

        if (PolicyResumeTargetPage <= 0 || PolicyCurrentPage >= PolicyResumeTargetPage) {
            DiagnosticInfo(
                EventName = "POLICY_RESUME_REACHED",
                MessageText = "page=$PolicyCurrentPage target=$PolicyResumeTargetPage " +
                        "total=$PolicyTotalPages captured=${CapturedPolicyMap.size}"
            )
            PolicyResumeTargetPage = 0
            PolicyResumeJumpAttempts = 0
            return false
        }

        PolicyResumeJumpAttempts++
        if (PolicyResumeJumpAttempts > POLICY_RESUME_JUMP_LIMIT) {
            DiagnosticWarning(
                EventName = "POLICY_RESUME_ABANDONED",
                MessageText = "reason=[jump attempts exhausted] target=$PolicyResumeTargetPage " +
                        "page=$PolicyCurrentPage attempts=$PolicyResumeJumpAttempts"
            )
            PolicyResumeTargetPage = 0
            return false
        }

        DiagnosticInfo(
            EventName = "POLICY_RESUME_JUMP",
            MessageText = "page=$PolicyCurrentPage target=$PolicyResumeTargetPage " +
                    "total=$PolicyTotalPages attempt=$PolicyResumeJumpAttempts"
        )
        SetPolicyJump(TargetPage = PolicyResumeTargetPage, ReasonVal = POLICY_JUMP_RESUME)
        PolicyReturnToTopCount = 0
        PolicyPageRetryCount = 0
        SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
            ReturnToPolicyPageSelector()
        }
        return true
    }

    private fun ScrollPolicyPageSelectorList(): Boolean {
        if (PolicySelectorScrollCount >= POLICY_SELECTOR_SCROLL_LIMIT) {
            DiagnosticWarning(
                EventName = "POLICY_SELECTOR_SCROLL_LIMIT",
                MessageText = "expected=$PolicyExpectedPage scrolls=$PolicySelectorScrollCount " +
                        "highestVisible=$PolicySelectorHighestOption"
            )
            return false
        }

        val OptionList = mutableListOf<Pair<Int, Rect>>()
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return false
        try {
            CollectPolicyPageOptions(TargetNode = RootNode, OutList = OptionList)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        val ModalLeft = OptionList
            .groupingBy { OptionItem -> OptionItem.second.left }
            .eachCount()
            .maxByOrNull { EntryItem -> EntryItem.value }
            ?.key
        val ColumnList = if (ModalLeft == null) {
            emptyList<Pair<Int, Rect>>()
        } else {
            OptionList.filter { OptionItem -> OptionItem.second.left == ModalLeft }
        }
        val ColumnWidthLimit =
            resources.displayMetrics.widthPixels * POLICY_SELECTOR_COLUMN_MAX_WIDTH_RATIO
        val OptionBounds = Rect()
        for (OptionItem in ColumnList) OptionBounds.union(OptionItem.second)
        if (ColumnList.size < POLICY_SELECTOR_OPTION_MIN_COUNT ||
            OptionBounds.isEmpty ||
            OptionBounds.width() > ColumnWidthLimit
        ) {
            DiagnosticWarning(
                EventName = "POLICY_SELECTOR_LIST_MISSING",
                MessageText = "expected=$PolicyExpectedPage options=${ColumnList.size} " +
                        "bounds=$OptionBounds"
            )
            return false
        }

        val HighestOption = ColumnList.maxOf { OptionItem -> OptionItem.first }
        if (HighestOption <= PolicySelectorHighestOption) {
            PolicySelectorScrollStalls++
        } else {
            PolicySelectorScrollStalls = 0
        }
        if (HighestOption > PolicySelectorHighestOption) {
            PolicySelectorHighestOption = HighestOption
        }
        if (PolicySelectorScrollStalls >= POLICY_SCROLL_STALL_LIMIT) {
            DiagnosticWarning(
                EventName = "POLICY_SELECTOR_SCROLL_STALLED",
                MessageText = "expected=$PolicyExpectedPage highestVisible=$PolicySelectorHighestOption"
            )
            return false
        }

        val ScrollAccepted = ScrollNodeCoveringBounds(BoundsObj = OptionBounds) ||
                SwipePolicyPageOptionColumn(BoundsObj = OptionBounds)
        PolicySelectorScrollCount++
        DiagnosticInfo(
            EventName = "POLICY_SELECTOR_SCROLL",
            MessageText = "expected=$PolicyExpectedPage attempt=$PolicySelectorScrollCount " +
                    "options=${ColumnList.size} highestVisible=$HighestOption " +
                    "bounds=$OptionBounds accepted=$ScrollAccepted"
        )
        return ScrollAccepted
    }

    private fun CollectPolicyPageOptions(
        TargetNode: AccessibilityNodeInfo,
        OutList: MutableList<Pair<Int, Rect>>
    ) {
        try {
            val NodeText = NodeTextValue(NodeRef = TargetNode).trim()
            if (NodeText.length in 1..3 &&
                NodeText.all { CharValue -> CharValue.isDigit() }
            ) {
                val OptionNumber = NodeText.toIntOrNull() ?: 0
                val OptionCap = if (PolicyTotalPages > 0) PolicyTotalPages else 999
                if (OptionNumber in 1..OptionCap &&
                    IsRightSidePageOption(NodeRef = TargetNode)
                ) {
                    val NodeBounds = Rect()
                    TargetNode.getBoundsInScreen(NodeBounds)
                    OutList.add(Pair(OptionNumber, NodeBounds))
                }
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    CollectPolicyPageOptions(TargetNode = ChildNode, OutList = OutList)
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun ScrollNodeCoveringBounds(BoundsObj: Rect): Boolean {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return false
        return try {
            ScrollScrollableNodeCovering(TargetNode = RootNode, BoundsObj = BoundsObj)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun ScrollScrollableNodeCovering(
        TargetNode: AccessibilityNodeInfo,
        BoundsObj: Rect
    ): Boolean {
        try {
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    if (ScrollScrollableNodeCovering(
                            TargetNode = ChildNode,
                            BoundsObj = BoundsObj
                        )
                    ) {
                        return true
                    }
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
            if (!TargetNode.isScrollable) return false
            val NodeBounds = Rect()
            TargetNode.getBoundsInScreen(NodeBounds)
            val WidthLimit =
                resources.displayMetrics.widthPixels * POLICY_SELECTOR_SCROLLABLE_MAX_WIDTH_RATIO
            if (NodeBounds.width() > WidthLimit) return false
            if (!NodeBounds.contains(BoundsObj.centerX(), BoundsObj.centerY())) return false
            return TargetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (_: Exception) {
        }
        return false
    }

    private fun SwipePolicyPageOptionColumn(BoundsObj: Rect): Boolean {
        CollapseBubbleForGesture()
        val DisplayMetricsObj = resources.displayMetrics
        val ColumnHeight = BoundsObj.height().toFloat()
        if (ColumnHeight < DisplayMetricsObj.heightPixels * 0.15f) return false

        val StartXVal = BoundsObj.centerX().toFloat()
        val BottomLimit = minOf(
            BoundsObj.bottom.toFloat(),
            DisplayMetricsObj.heightPixels * 0.92f
        )
        val StartYVal = BottomLimit - ColumnHeight * 0.06f
        val EndYVal = BoundsObj.top + ColumnHeight * 0.06f
        if (StartYVal <= EndYVal) return false

        val ScrollPath = Path().apply {
            moveTo(StartXVal, StartYVal)
            lineTo(StartXVal, EndYVal)
        }
        val GestureObj = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    ScrollPath,
                    0,
                    POLICY_SELECTOR_SCROLL_DURATION_MS
                )
            )
            .build()
        return try {
            dispatchGesture(GestureObj, null, null)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_SELECTOR_SCROLL_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            false
        }
    }

    private fun RetryPolicyPageNavigation(ReasonText: String) {
        PolicyPageRetryCount++
        Log.w(LOG_TAG, "$ReasonText (attempt $PolicyPageRetryCount)")
        DiagnosticWarning(
            EventName = "POLICY_NAVIGATION_RETRY",
            MessageText = "$ReasonText; attempt=$PolicyPageRetryCount " +
                    "page=$PolicyCurrentPage expected=$PolicyExpectedPage total=$PolicyTotalPages"
        )
        if (PolicyPageRetryCount >= POLICY_PAGE_RETRY_LIMIT) {
            FailPolicyDashboardAutomation(ReasonText)
            return
        }
        SchedulePolicyAction(DelayMs = POLICY_PAGE_LOAD_DELAY_MS) {
            ReturnToPolicyPageSelector()
        }
    }

    private fun CompletePolicyDashboardAutomation() {
        if (IsPolicyDashboardComplete) return
        IsPolicyDashboardComplete = true
        IsPolicyDashboardAutomationRunning = false
        PolicyResumeTargetPage = 0
        SavePolicyResumeProgress(IsCompleteVal = true)
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        Log.d(LOG_TAG, "Policy Dashboard capture completed with ${CapturedPolicyMap.size} policies")
        DiagnosticInfo(
            EventName = "POLICY_AUTOMATION_COMPLETE",
            MessageText = "captured=${CapturedPolicyMap.size} page=$PolicyCurrentPage/$PolicyTotalPages"
        )

        ShowServiceToast(
            MessageText = "Captured ${CapturedPolicyMap.size} policies",
            KindVal = AppToast.Kind.Success
        )

        if (BeginChainedCustomerLeg()) return

        MainHandler.postDelayed({
            if (IsCapturing && CurrentMode == CaptureMode.POLICY && IsPolicyDashboardComplete) {
                FinishCaptureSession()
            }
        }, POLICY_PAGE_LOAD_DELAY_MS)
    }

    private fun FailPolicyDashboardAutomation(ReasonText: String) {
        IsPolicyDashboardComplete = false
        IsPolicyDashboardAutomationRunning = false
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        PolicyAutomationFailureCount++
        if (CurrentMode == CaptureMode.POLICY && PolicyCurrentPage > 1) {
            PolicyLastFailurePage = PolicyCurrentPage
            if (PolicyResumeTargetPage <= 0) {
                PolicyResumeTargetPage = PolicyCurrentPage
                PolicyResumeJumpAttempts = 0
                PolicyResumeWaitCount = 0
                DiagnosticInfo(
                    EventName = "POLICY_RESUME_TARGET",
                    MessageText = "source=recovery page=$PolicyCurrentPage total=$PolicyTotalPages"
                )
            }
        }
        SavePolicyResumeProgress(IsCompleteVal = false)
        val CanRetryAutomatically = PolicyAutomationFailureCount < POLICY_AUTOMATION_RECOVERY_LIMIT
        PolicyAutomationRetryAfter = if (CanRetryAutomatically) {
            System.currentTimeMillis() + POLICY_FAILURE_RETRY_MS
        } else {
            Long.MAX_VALUE
        }
        PolicyPageRetryCount = 0
        PolicyReturnToTopCount = 0
        PolicyScrollStallCount = 0
        Log.w(LOG_TAG, "Policy Dashboard automation stopped: $ReasonText")
        DiagnosticWarning(
            EventName = "POLICY_AUTOMATION_RECOVERY",
            MessageText = "reason=[$ReasonText] recovery=$PolicyAutomationFailureCount " +
                    "automaticRetry=$CanRetryAutomatically " +
                    "retryInMs=${if (CanRetryAutomatically) POLICY_FAILURE_RETRY_MS else 0L} " +
                    "page=$PolicyCurrentPage " +
                    "expected=$PolicyExpectedPage total=$PolicyTotalPages " +
                    "selectorVisible=$IsPolicyPageSelectorVisible captured=${CapturedPolicyMap.size}"
        )
        ShowServiceToast(
            MessageText = if (CanRetryAutomatically) {
                "Policy automation paused: $ReasonText. Retrying automatically."
            } else {
                "Policy automation stopped after repeated failures. Captured data is preserved."
            },
            KindVal = if (CanRetryAutomatically) AppToast.Kind.Warning else AppToast.Kind.Error
        )
        RefreshBubble()
    }

    override fun SearchRootNode(): AccessibilityNodeInfo? {
        return FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
    }

    override fun SearchRecycleNode(NodeRef: AccessibilityNodeInfo?) {
        RecycleNode(NodeRef = NodeRef)
    }

    override fun SearchTap(XPos: Float, YPos: Float): Boolean {
        return PerformTapGesture(XPos = XPos, YPos = YPos)
    }

    override fun SearchSetText(NodeRef: AccessibilityNodeInfo, TextValue: String): Boolean {
        return SetNodeText(NodeRef = NodeRef, TextValue = TextValue)
    }

    override fun SearchSchedule(DelayMs: Long, ActionRef: () -> Unit) {
        SchedulePolicyAction(DelayMs = DelayMs, ActionRef = ActionRef)
    }

    override fun SearchRefreshScreen() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
    }

    override fun SearchInfo(EventName: String, MessageText: String) {
        DiagnosticInfo(EventName = EventName, MessageText = MessageText)
    }

    override fun SearchWarn(EventName: String, MessageText: String) {
        DiagnosticWarning(EventName = EventName, MessageText = MessageText)
    }

    override fun SearchScreenWidth(): Int {
        return resources.displayMetrics.widthPixels
    }

    override fun SearchScreenHeight(): Int {
        return resources.displayMetrics.heightPixels
    }

    override fun SearchBeginRun() {
        IsPolicyDashboardAutomationRunning = true
        PolicyAutomationRetryAfter = 0L
        PolicyPageRetryCount = 0
    }

    override fun SearchOpenDetail(PolicyNumberVal: String) {
        PolicyDetailCurrentPolicyNumber = PolicyNumberVal
        PolicyDetailOriginPage = 0
        PolicyDetailScrollAttempts = 0
        PolicyDetailOpenAttempts = 1
        PolicyDetailReturnAttempts = 0
        IsPolicyDetailScreenActive = false
        IsPolicyDashboardScreenVisible = false
        LatestPolicyDetailNodes = emptyList()
        PolicySectionRetryRounds = 0
        PolicyDetailSweepCount = 0
        LastPolicyDetailSweepSignature = 0
        HasExpandedCurrentPolicyScreen = false
        SchedulePolicyAction(DelayMs = POLICY_DETAIL_OPEN_DELAY_MS) {
            WaitForPolicyDetailScreen()
        }
    }

    override fun SearchPressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun SearchHandOffToPageWalk() {
        IsPolicyDashboardAutomationRunning = false
        IsPolicyDashboardComplete = false
        PolicyAutomationRetryAfter = 0L
        PolicyPageRetryCount = 0
        PolicyReturnToTopCount = 0
        PolicyScrollStallCount = 0
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        if (IsPolicyDashboardActive) StartPolicyDashboardAutomation()
    }

    override fun SearchNoteRowContact(
        PolicyNumberVal: String,
        MobileText: String,
        AgeText: String
    ) {
        if (PolicyNumberVal.isEmpty()) return
        if (MobileText.isEmpty() && AgeText.isEmpty()) return
        val ExistingPolicy = CapturedPolicyMap[PolicyNumberVal] ?: return
        val WantsMobile = MobileText.isNotEmpty() && ExistingPolicy.MobileNumber.isEmpty()
        val WantsAge = AgeText.isNotEmpty() && ExistingPolicy.Age.isEmpty()
        if (!WantsMobile && !WantsAge) return

        val UpdatedPolicy = ExistingPolicy.copy(
            Age = if (WantsAge) AgeText else ExistingPolicy.Age
        )
        if (WantsMobile) UpdatedPolicy.MobileNumber = MobileText
        CapturedPolicyMap[PolicyNumberVal] = UpdatedPolicy
        RebuildCapturedPolicyNodes()
        LastParsedNodeCount = -1
        DiagnosticInfo(
            EventName = "POLICY_SEARCH_ROW_CONTACT",
            MessageText = "policy=$PolicyNumberVal mobile=${if (WantsMobile) "saved" else "kept"} " +
                    "age=${if (WantsAge) AgeText else "kept"}"
        )
    }

    private fun BeginChainedCustomerLeg(): Boolean {
        if (ChainCustomerName.isBlank()) return false
        if (CurrentMode != CaptureMode.POLICY) return false
        if (!TargetScope.IsActive) return false
        if (IsChainedCustomerLegStarted) return false
        IsChainedCustomerLegStarted = true

        IsPolicyDashboardComplete = true
        IsPolicyDashboardAutomationRunning = false
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        SearchRoute.Reset()

        for (PolicyNumber in TargetScope.Numbers) {
            val PolicyItem = CapturedPolicyMap[PolicyNumber] ?: continue
            ProfilePatchMap[PolicyNumber] = PolicyItem
            ProfilePatchNames[PolicyNumber] = PolicyItem.HolderName.ifEmpty { ChainCustomerName }
        }

        CurrentMode = CaptureMode.CUSTOMER
        RevisitFilledEnabled = false
        SessionPolicyNumbers.clear()
        SessionPolicyNumbers.addAll(CapturedPolicyMap.keys)
        FilledPolicyNumbers.clear()
        for (PolicyItem in CapturedPolicyMap.values) {
            if (RecordMerge.HasPersonalDetails(PolicyItem = PolicyItem)) {
                FilledPolicyNumbers.add(PolicyItem.PolicyNumber)
            }
        }
        VisitedCustomerNames.clear()
        VisitedCustomerNames.addAll(
            PolicyRepository.GetVisitedCustomers(
                ContextRef = this,
                SessionId = CurrentSessionId
            )
        )
        ProcessedCustomerKeys.clear()
        CustomerNameScope.Arm(
            NumbersVal = listOf(NormalisedName(NameText = ChainCustomerName)),
            NameHintsVal = emptyMap()
        )
        VisitedCustomerNames.removeAll(CustomerNameScope.Numbers.toSet())
        CustomerRoute.Arm(TargetsVal = listOf(ChainCustomerName))

        IsCustomerAutomationComplete = false
        IsCustomerAutomationRunning = true
        IsCustomerDashboardActive = false
        HasClickedPortfolioCustomers = true
        HasClickedHomeNavTab = true
        CustomerAutomationRetryAfter = 0L
        CustomerStageValue = CustomerStage.DASHBOARD
        ActiveCustomerName = ""
        ActiveProfile = null
        ProfilePaneNodes.clear()
        PendingSheetKinds.clear()
        RefreshBubble()

        DiagnosticInfo(
            EventName = "CUSTOMER_CHAIN_START",
            MessageText = "customer=$ChainCustomerName policies=${TargetScope.Describe()} " +
                    "patches=${ProfilePatchMap.size}"
        )
        ShowServiceToast(
            MessageText = "Now filling personal details",
            KindVal = AppToast.Kind.Info
        )
        MainHandler.postDelayed({
            if (IsCapturing && CurrentMode == CaptureMode.CUSTOMER) {
                CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            }
        }, POLICY_NAVIGATION_DELAY_MS)
        return true
    }

    override fun SearchFinishRun() {
        if (BeginChainedCustomerLeg()) return
        IsPolicyDashboardComplete = true
        IsPolicyDashboardAutomationRunning = false
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        DiagnosticInfo(
            EventName = "POLICY_AUTOMATION_COMPLETE",
            MessageText = "route=search targets=${TargetScope.Describe()} " +
                    "captured=${CapturedPolicyMap.size}"
        )
        ShowServiceToast(
            MessageText = "Captured the missing policy details",
            KindVal = AppToast.Kind.Success
        )
        MainHandler.postDelayed({
            if (IsCapturing && CurrentMode == CaptureMode.POLICY) {
                FinishCaptureSession()
            }
        }, POLICY_PAGE_LOAD_DELAY_MS)
    }

    private fun StopPolicyDashboardAutomation(ResetStateVal: Boolean) {
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        IsPolicyDashboardAutomationRunning = false

        if (ResetStateVal) {
            IsPolicyDashboardActive = false
            IsPolicyDashboardComplete = false
            PolicyCurrentPage = 0
            PolicyTotalPages = 0
            PolicyExpectedPage = 0
            PolicyPageRetryCount = 0
            PolicyReturnToTopCount = 0
            PolicyScrollStallCount = 0
            LatestPolicyVisibleSignature = 0
            IsPolicyPageSelectorVisible = false
            LatestPolicyPageNumbers = emptyList()
            PolicyDetailQueue = emptyList()
            PolicyDetailQueueIndex = 0
            PolicyDetailCurrentPolicyNumber = ""
            PolicyDetailScrollAttempts = 0
            PolicyDetailOpenAttempts = 0
            PolicyDetailReturnAttempts = 0
            PolicyDetailOriginPage = 0
            ClearPolicyJump()
            ResetPolicySelectorScrollState()
            IsPolicyDetailScreenActive = false
            IsPolicyDashboardScreenVisible = false
            LatestPolicyDetailNodes = emptyList()
            PolicySectionRetryRounds = 0
            PolicyDetailSweepCount = 0
            LastPolicyDetailSweepSignature = 0
            PolicySectionsInFlight.clear()
            ProcessedPolicyDetailNumbers.clear()
        }
    }

    private fun SchedulePolicyAction(DelayMs: Long, ActionRef: () -> Unit) {
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }

        var RenderWaitCount = 0
        lateinit var WrappedRunnable: Runnable
        WrappedRunnable = Runnable {
            if (!IsPolicyDashboardAutomationRunning ||
                !IsCapturing ||
                CurrentMode != CaptureMode.POLICY
            ) {
                return@Runnable
            }
            if (IsPaused) {
                PolicyAutomationRunnable = WrappedRunnable
                MainHandler.postDelayed(WrappedRunnable, TICK_INTERVAL_MS)
                return@Runnable
            }
            if (!IsScreenSettled(WaitCount = RenderWaitCount)) {
                RenderWaitCount++
                PolicyAutomationRunnable = WrappedRunnable
                MainHandler.postDelayed(WrappedRunnable, Paced(BaseMs = SCREEN_READY_RECHECK_MS))
                return@Runnable
            }

            PolicyAutomationRunnable = null
            ActionRef()
        }
        PolicyAutomationRunnable = WrappedRunnable
        MainHandler.postDelayed(WrappedRunnable, Paced(BaseMs = DelayMs))
    }

    private fun ClickPolicyPageSelector(
        RootNode: AccessibilityNodeInfo,
        CurrentPage: Int
    ): Boolean {
        val CandidateLabels = linkedSetOf(
            CurrentPage.toString().padStart(2, '0'),
            CurrentPage.toString()
        )
        for (CandidateLabel in CandidateLabels) {
            val MatchList = try {
                RootNode.findAccessibilityNodeInfosByText(CandidateLabel)
            } catch (_: Exception) {
                emptyList()
            }
            try {
                for (MatchNode in MatchList) {
                    val NodeText = NodeTextValue(NodeRef = MatchNode)
                    if (!IsPageNumberLabel(TextValue = NodeText, PageNumber = CurrentPage)) continue
                    if (!IsTopRightPageControl(NodeRef = MatchNode)) continue
                    if (ClickNodeOrParent(StartNode = MatchNode)) {
                        Log.d(LOG_TAG, "Opened policy page selector")
                        return true
                    }
                    val MatchBounds = Rect()
                    MatchNode.getBoundsInScreen(MatchBounds)
                    if (PerformTapGesture(
                            XPos = MatchBounds.centerX().toFloat(),
                            YPos = MatchBounds.centerY().toFloat()
                        )
                    ) {
                        return true
                    }
                }
            } finally {
                for (MatchNode in MatchList) RecycleNode(NodeRef = MatchNode)
            }
        }
        return ClickPageNumberByTraversal(
            TargetNode = RootNode,
            PageNumber = CurrentPage,
            RequireTopRight = true,
            ActionName = "open-selector"
        ) || ClickSpinnerNode(
            TargetNode = RootNode,
            ActionValue = AccessibilityNodeInfo.ACTION_CLICK
        )
    }

    private fun AdvancePolicyPageSelector(
        RootNode: AccessibilityNodeInfo,
        CurrentPage: Int
    ): Boolean {
        if (ClickSpinnerNode(
                TargetNode = RootNode,
                ActionValue = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            )
        ) {
            Log.d(LOG_TAG, "Advanced policy page selector to ${CurrentPage + 1}")
            return true
        }
        return false
    }

    private fun ClickPolicyPageOption(RootNode: AccessibilityNodeInfo, PageNumber: Int): Boolean {
        val CandidateLabels = linkedSetOf(
            PageNumber.toString().padStart(2, '0'),
            PageNumber.toString()
        )
        for (CandidateLabel in CandidateLabels) {
            val MatchList = try {
                RootNode.findAccessibilityNodeInfosByText(CandidateLabel)
            } catch (_: Exception) {
                emptyList()
            }
            try {
                for (MatchNode in MatchList) {
                    val NodeText = NodeTextValue(NodeRef = MatchNode)
                    if (!IsPageNumberLabel(TextValue = NodeText, PageNumber = PageNumber)) continue
                    if (!IsRightSidePageOption(NodeRef = MatchNode)) continue
                    if (ClickNodeOrParent(StartNode = MatchNode)) {
                        Log.d(LOG_TAG, "Selected policy page $PageNumber")
                        return true
                    }
                    val MatchBounds = Rect()
                    MatchNode.getBoundsInScreen(MatchBounds)
                    if (PerformTapGesture(
                            XPos = MatchBounds.centerX().toFloat(),
                            YPos = MatchBounds.centerY().toFloat()
                        )
                    ) {
                        return true
                    }
                }
            } finally {
                for (MatchNode in MatchList) RecycleNode(NodeRef = MatchNode)
            }
        }
        return ClickPageNumberByTraversal(
            TargetNode = RootNode,
            PageNumber = PageNumber,
            RequireTopRight = false,
            ActionName = "select-option"
        )
    }

    private fun IsPageNumberLabel(TextValue: String, PageNumber: Int): Boolean {
        val NormalizedText = TextValue.trim()
        val PageRegex = Regex(
            "(?i)^(?:Page\\s*)?0*${Regex.escape(PageNumber.toString())}" +
                    "(?:\\s+(?:selected|Check\\s+iconName|arrow[-\\s]?down))?$"
        )
        return PageRegex.matches(NormalizedText)
    }

    private fun IsTopRightPageControl(NodeRef: AccessibilityNodeInfo): Boolean {
        val NodeBounds = Rect()
        NodeRef.getBoundsInScreen(NodeBounds)
        val DisplayMetricsObj = resources.displayMetrics
        return IsBoundsOnScreen(BoundsObj = NodeBounds) &&
                NodeBounds.centerX() >= DisplayMetricsObj.widthPixels * 0.5f &&
                NodeBounds.centerY() <= DisplayMetricsObj.heightPixels * 0.45f
    }

    private fun IsRightSidePageOption(NodeRef: AccessibilityNodeInfo): Boolean {
        val NodeBounds = Rect()
        NodeRef.getBoundsInScreen(NodeBounds)
        return IsBoundsOnScreen(BoundsObj = NodeBounds) &&
                NodeBounds.centerX() >= resources.displayMetrics.widthPixels * 0.45f
    }

    private fun IsBoundsOnScreen(BoundsObj: Rect): Boolean {
        val DisplayMetricsObj = resources.displayMetrics
        return !BoundsObj.isEmpty &&
                BoundsObj.right > 0 &&
                BoundsObj.bottom > 0 &&
                BoundsObj.left < DisplayMetricsObj.widthPixels &&
                BoundsObj.top < DisplayMetricsObj.heightPixels
    }

    private fun IsPolicyPageSelectorControlVisible(CurrentPage: Int): Boolean {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return false
        return try {
            ContainsVisiblePageNumber(
                TargetNode = RootNode,
                PageNumber = CurrentPage,
                RequireTopRight = true
            )
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun ContainsVisiblePageNumber(
        TargetNode: AccessibilityNodeInfo,
        PageNumber: Int,
        RequireTopRight: Boolean
    ): Boolean {
        try {
            val NodeText = NodeTextValue(NodeRef = TargetNode)
            if (IsPageNumberLabel(TextValue = NodeText, PageNumber = PageNumber)) {
                val IsCorrectArea = if (RequireTopRight) {
                    IsTopRightPageControl(NodeRef = TargetNode)
                } else {
                    IsRightSidePageOption(NodeRef = TargetNode)
                }
                if (IsCorrectArea) return true
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    if (ContainsVisiblePageNumber(
                            TargetNode = ChildNode,
                            PageNumber = PageNumber,
                            RequireTopRight = RequireTopRight
                        )
                    ) {
                        return true
                    }
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun ClickPageNumberByTraversal(
        TargetNode: AccessibilityNodeInfo,
        PageNumber: Int,
        RequireTopRight: Boolean,
        ActionName: String
    ): Boolean {
        try {
            val NodeText = NodeTextValue(NodeRef = TargetNode)
            if (IsPageNumberLabel(TextValue = NodeText, PageNumber = PageNumber)) {
                val NodeBounds = Rect()
                TargetNode.getBoundsInScreen(NodeBounds)
                val IsCorrectArea = if (RequireTopRight) {
                    IsTopRightPageControl(NodeRef = TargetNode)
                } else {
                    IsRightSidePageOption(NodeRef = TargetNode)
                }
                DiagnosticInfo(
                    EventName = "POLICY_PAGE_TREE_CANDIDATE",
                    MessageText = "action=$ActionName page=$PageNumber text=[$NodeText] " +
                            "class=${TargetNode.className} clickable=${TargetNode.isClickable} " +
                            "visible=${TargetNode.isVisibleToUser} bounds=$NodeBounds " +
                            "correctArea=$IsCorrectArea"
                )
                if (IsCorrectArea) {
                    val PreferCoordinateTap = PolicyPageRetryCount > 0
                    if (!PreferCoordinateTap && ClickNodeOrParent(StartNode = TargetNode)) {
                        DiagnosticInfo(
                            EventName = "POLICY_PAGE_TREE_ACTION",
                            MessageText = "action=$ActionName page=$PageNumber method=accessibility"
                        )
                        return true
                    }
                    if (PerformTapGesture(
                            XPos = NodeBounds.centerX().toFloat(),
                            YPos = NodeBounds.centerY().toFloat()
                        )
                    ) {
                        DiagnosticInfo(
                            EventName = "POLICY_PAGE_TREE_ACTION",
                            MessageText = "action=$ActionName page=$PageNumber method=coordinate"
                        )
                        return true
                    }
                }
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    if (ClickPageNumberByTraversal(
                            TargetNode = ChildNode,
                            PageNumber = PageNumber,
                            RequireTopRight = RequireTopRight,
                            ActionName = ActionName
                        )
                    ) {
                        return true
                    }
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_PAGE_TREE_ERROR",
                MessageText = "action=$ActionName page=$PageNumber " +
                        "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
        return false
    }

    private fun ClickPolicyDetailArrow(
        RootNode: AccessibilityNodeInfo,
        PolicyNumber: String
    ): PolicyDetailOpenResult {
        val PolicyBoundsList = mutableListOf<Rect>()
        val ArrowBoundsList = mutableListOf<Rect>()
        CollectPolicyDetailTargets(
            TargetNode = RootNode,
            PolicyNumber = PolicyNumber,
            PolicyBoundsList = PolicyBoundsList,
            ArrowBoundsList = ArrowBoundsList
        )
        val ScreenHeight = resources.displayMetrics.heightPixels
        val VisiblePolicyBounds = PolicyBoundsList
            .filter { BoundsObj -> IsPolicyAnchorSafeToTap(BoundsObj = BoundsObj) }
            .sortedBy { BoundsObj -> BoundsObj.width().toLong() * BoundsObj.height().toLong() }
        if (VisiblePolicyBounds.isEmpty()) return PolicyDetailOpenResult.NEED_SCROLL

        var SelectedPolicyBounds: Rect? = null
        var SelectedArrowBounds: Rect? = null
        var SmallestDistance = Int.MAX_VALUE
        for (PolicyBounds in VisiblePolicyBounds) {
            for (ArrowBounds in ArrowBoundsList) {
                if (!IsPolicyArrowSafeToTap(BoundsObj = ArrowBounds)) continue
                val VerticalDistance = ArrowBounds.centerY() - PolicyBounds.centerY()
                if (VerticalDistance < -20) continue
                if (VerticalDistance > ScreenHeight * 0.32f) continue
                if (VerticalDistance < SmallestDistance) {
                    SmallestDistance = VerticalDistance
                    SelectedPolicyBounds = PolicyBounds
                    SelectedArrowBounds = ArrowBounds
                }
            }
        }

        val TapX: Float
        val TapY: Float
        val TapMethod: String
        if (SelectedArrowBounds != null) {
            TapX = SelectedArrowBounds.centerX().toFloat()
            TapY = SelectedArrowBounds.centerY().toFloat()
            TapMethod = "arrow-bounds"
        } else {
            val PolicyBounds = VisiblePolicyBounds.first()
            TapX = resources.displayMetrics.widthPixels * 0.86f
            TapY = PolicyBounds.centerY() + ScreenHeight * 0.11f
            if (TapY > ScreenHeight * 0.82f) {
                DiagnosticInfo(
                    EventName = "POLICY_DETAIL_PARTIAL_CARD",
                    MessageText = "policy=$PolicyNumber anchor=$PolicyBounds calculatedTapY=$TapY"
                )
                return PolicyDetailOpenResult.NEED_SCROLL
            }
            SelectedPolicyBounds = PolicyBounds
            TapMethod = "card-relative-fallback"
        }
        DiagnosticInfo(
            EventName = "POLICY_DETAIL_ARROW_CANDIDATE",
            MessageText = "policy=$PolicyNumber method=$TapMethod policyBounds=$SelectedPolicyBounds " +
                    "arrowBounds=$SelectedArrowBounds allPolicyBounds=${PolicyBoundsList.size} " +
                    "allArrows=${ArrowBoundsList.size} tap=($TapX,$TapY)"
        )
        return if (PerformTapGesture(XPos = TapX, YPos = TapY)) {
            PolicyDetailOpenResult.CLICKED
        } else {
            PolicyDetailOpenResult.FAILED
        }
    }

    private fun IsPolicyAnchorSafeToTap(BoundsObj: Rect): Boolean {
        val ScreenHeight = resources.displayMetrics.heightPixels
        return IsBoundsOnScreen(BoundsObj = BoundsObj) &&
                BoundsObj.centerY() >= ScreenHeight * 0.08f &&
                BoundsObj.centerY() <= ScreenHeight * 0.86f
    }

    private fun IsPolicyArrowSafeToTap(BoundsObj: Rect): Boolean {
        val ScreenHeight = resources.displayMetrics.heightPixels
        return IsBoundsOnScreen(BoundsObj = BoundsObj) &&
                BoundsObj.centerY() >= ScreenHeight * 0.08f &&
                BoundsObj.centerY() <= ScreenHeight * 0.86f
    }

    private fun CollectPolicyDetailTargets(
        TargetNode: AccessibilityNodeInfo,
        PolicyNumber: String,
        PolicyBoundsList: MutableList<Rect>,
        ArrowBoundsList: MutableList<Rect>
    ) {
        try {
            val NodeText = NodeTextValue(NodeRef = TargetNode)
            if (NodeText.contains(PolicyNumber)) {
                val NodeBounds = Rect()
                TargetNode.getBoundsInScreen(NodeBounds)
                if (!NodeBounds.isEmpty) PolicyBoundsList.add(NodeBounds)
            }
            if (NodeText.contains("card right arrow", ignoreCase = true) ||
                NodeText.equals("right arrow icon", ignoreCase = true)
            ) {
                val NodeBounds = Rect()
                TargetNode.getBoundsInScreen(NodeBounds)
                if (!NodeBounds.isEmpty) ArrowBoundsList.add(NodeBounds)
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    CollectPolicyDetailTargets(
                        TargetNode = ChildNode,
                        PolicyNumber = PolicyNumber,
                        PolicyBoundsList = PolicyBoundsList,
                        ArrowBoundsList = ArrowBoundsList
                    )
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_DETAIL_TARGET_ERROR",
                MessageText = "policy=$PolicyNumber ${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
        }
    }

    private fun NodeTextValue(NodeRef: AccessibilityNodeInfo): String {
        return NodeRef.text?.toString()?.trim()
            .takeUnless { TextValue -> TextValue.isNullOrEmpty() }
            ?: NodeRef.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun ClickNodeOrParent(StartNode: AccessibilityNodeInfo): Boolean {
        var CandidateNode: AccessibilityNodeInfo? = StartNode
        var OwnsCandidate = false
        try {
            while (CandidateNode != null) {
                if (CandidateNode.isClickable &&
                    CandidateNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    return true
                }
                val ParentNode = try {
                    CandidateNode.parent
                } catch (_: Exception) {
                    null
                }
                if (OwnsCandidate) RecycleNode(NodeRef = CandidateNode)
                CandidateNode = ParentNode
                OwnsCandidate = true
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Clickable accessibility parent became stale", ExceptionObj)
        } finally {
            if (OwnsCandidate) RecycleNode(NodeRef = CandidateNode)
        }
        return false
    }

    private fun ClickSpinnerNode(TargetNode: AccessibilityNodeInfo, ActionValue: Int): Boolean {
        try {
            val ClassNameText = TargetNode.className?.toString().orEmpty()
            if (ClassNameText.contains("Spinner", ignoreCase = true) &&
                IsTopRightPageControl(NodeRef = TargetNode) &&
                TargetNode.performAction(ActionValue)
            ) {
                return true
            }

            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                try {
                    if (ClickSpinnerNode(
                            TargetNode = ChildNode,
                            ActionValue = ActionValue
                        )
                    ) return true
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Page spinner became stale", ExceptionObj)
        }
        return false
    }


    private fun IsRenewalsDashboardScreen(VisibleNodes: List<String>): Boolean {
        val HasDashboardTitle = VisibleNodes.any { NodeText ->
            NodeText.contains("Renewals Dashboard", ignoreCase = true)
        }
        if (HasDashboardTitle) return true

        val HasMetricsHeader = VisibleNodes.any { NodeText ->
            NodeText.contains("Business Metrics", ignoreCase = true)
        }
        val HasRenewalMetric = VisibleNodes.any { NodeText ->
            NodeText.contains("Renewal Premium Collected", ignoreCase = true) ||
                    NodeText.contains("Renewals Due", ignoreCase = true)
        }
        return HasMetricsHeader && HasRenewalMetric
    }

    private fun IsRenewalHistoryScreen(VisibleNodes: List<String>): Boolean {
        if (IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes)) return false

        val HasHistoryTitle = VisibleNodes.any { NodeText ->
            NodeText.trim().equals("Renewal History", ignoreCase = true)
        }
        if (!HasHistoryTitle) return false

        val HasFilterSummary = VisibleNodes.any { NodeText ->
            NodeText.contains("Based on Selected Filters", ignoreCase = true)
        }
        return HasFilterSummary || ParsePolicyPageInfo(VisibleNodes = VisibleNodes) != null
    }

    private fun UpdateRenewalScreenState(VisibleNodes: List<String>) {
        LatestRenewalVisibleNodes = VisibleNodes
        LatestRenewalVisibleSignature = VisibleNodes.joinToString(separator = "").hashCode()

        val IsPagedListScreen = if (CurrentMode == CaptureMode.RENEWAL_DUE) {
            IsRenewalsDueCustomerScreen(VisibleNodes = VisibleNodes) ||
                    IsAllRenewalsDueScreen(VisibleNodes = VisibleNodes)
        } else {
            IsRenewalHistoryScreen(VisibleNodes = VisibleNodes)
        }
        if (!IsPagedListScreen) {
            IsRenewalPageSelectorVisible = false
            return
        }
        val PageInfo = ParsePolicyPageInfo(VisibleNodes = VisibleNodes)
        IsRenewalPageSelectorVisible = PageInfo != null
        if (PageInfo != null) {
            val PreviousPage = RenewalCurrentPage
            RenewalCurrentPage = PageInfo.first
            RenewalTotalPages = PageInfo.second
            if (RenewalTotalPages > RenewalKnownTotalPages) {
                RenewalKnownTotalPages = RenewalTotalPages
            }
            if (PreviousPage != RenewalCurrentPage) {
                DiagnosticInfo(
                    EventName = "RENEWAL_PAGE_DETECTED",
                    MessageText = "page=$RenewalCurrentPage total=$RenewalTotalPages"
                )
            }
        }
    }

    private fun HandleRenewalScreenAutomation(VisibleNodes: List<String>) {
        if (CurrentMode == CaptureMode.RENEWAL_DUE) {
            HandleRenewalDueScreenAutomation(VisibleNodes = VisibleNodes)
            return
        }

        if (IsRenewalHistoryScreen(VisibleNodes = VisibleNodes)) {
            HasClickedHomeNavTab = true
            HasOpenedRenewalHistoryList = true
            HomeNavClickAttempts = 0
            HomeNavLastAttemptAt = 0L
            StartRenewalAutomation()
            return
        }

        if (IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes)) {
            HasClickedHomeNavTab = true
            HomeNavClickAttempts = 0
            HomeNavLastAttemptAt = 0L
            StartRenewalAutomation()
            return
        }
    }

    private fun StartRenewalAutomation() {
        if (IsRenewalAutomationRunning || IsRenewalAutomationComplete) return
        if (System.currentTimeMillis() < RenewalAutomationRetryAfter) return

        IsRenewalAutomationRunning = true
        RenewalAutomationRetryAfter = 0L
        RenewalPageRetryCount = 0
        RenewalReturnToTopCount = 0
        RenewalScrollStallCount = 0
        RenewalUnknownScreenCount = 0
        DiagnosticInfo(
            EventName = "RENEWAL_AUTOMATION_START",
            MessageText = "openedHistory=$HasOpenedRenewalHistoryList " +
                    "dateRangeSelected=$HasSelectedRenewalDateRange " +
                    "page=$RenewalCurrentPage/$RenewalTotalPages " +
                    "recoveryCount=$RenewalAutomationFailureCount"
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
            if (CurrentMode == CaptureMode.RENEWAL_DUE) {
                RunRenewalDueStep()
            } else {
                RunRenewalAutomationStep()
            }
        }
    }

    private fun RunRenewalAutomationStep() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val VisibleNodes = LatestRenewalVisibleNodes

        if (IsRenewalHistoryScreen(VisibleNodes = VisibleNodes)) {
            RenewalUnknownScreenCount = 0
            HasOpenedRenewalHistoryList = true
            if (!HasSelectedRenewalDateRange) {
                OpenRenewalDateRangeDropdown()
                return
            }
            if (MaybeReselectRenewalRange()) return
            if (MaybeJumpToRenewalSkipTarget()) return
            ScrollRenewalHistoryPage()
            return
        }

        if (IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes)) {
            RenewalUnknownScreenCount = 0
            OpenRenewalHistoryFromDashboard()
            return
        }

        RenewalUnknownScreenCount++
        if (RenewalUnknownScreenCount > RENEWAL_DASHBOARD_SCROLL_LIMIT) {
            FailRenewalAutomation("Neither the Renewals Dashboard nor Renewal History was visible")
            return
        }
        DiagnosticInfo(
            EventName = "RENEWAL_SCREEN_WAIT",
            MessageText = "attempt=$RenewalUnknownScreenCount nodes=${VisibleNodes.size}"
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalAutomationStep()
        }
    }


    private fun OpenRenewalHistoryFromDashboard() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                RunRenewalAutomationStep()
            }
            return
        }

        val ViewAllTapped = try {
            TapSectionViewAll(RootNode = RootNode, HeaderText = "Renewal History")
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (ViewAllTapped) {
            HasOpenedRenewalHistoryList = true
            RenewalDashboardScrollCount = 0
            DiagnosticInfo(
                EventName = "RENEWAL_VIEW_ALL_CLICKED",
                MessageText = "Waiting for the Renewal History page to load"
            )
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalAutomationStep()
            }
            return
        }

        RenewalDashboardScrollCount++
        if (RenewalDashboardScrollCount > RENEWAL_DASHBOARD_SCROLL_LIMIT) {
            FailRenewalAutomation("Could not reach the RENEWAL HISTORY section")
            return
        }

        val ScrollAccepted = PerformPolicyScroll(
            ForwardVal = true,
            PreferAccessibilityAction = RenewalDashboardScrollCount % 2 == 0
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DASHBOARD_SCROLL",
            MessageText = "attempt=$RenewalDashboardScrollCount accepted=$ScrollAccepted"
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
            RunRenewalAutomationStep()
        }
    }

    private fun TapSectionViewAll(RootNode: AccessibilityNodeInfo, HeaderText: String): Boolean {
        val TextNodes = CollectVisibleTextNodes(RootNode = RootNode)
        val HeaderBounds = TextNodes.firstOrNull { NodeEntry ->
            NodeEntry.first.trim().equals(HeaderText, ignoreCase = true)
        }?.second
        if (HeaderBounds == null) {
            DiagnosticInfo(
                EventName = "RENEWAL_VIEW_ALL_SEARCH",
                MessageText = "The $HeaderText header is not on screen yet"
            )
            return false
        }

        val RowTolerance = resources.displayMetrics.heightPixels *
                RENEWAL_SECTION_ROW_TOLERANCE_RATIO
        val ViewAllCandidates = TextNodes.filter { NodeEntry ->
            NodeEntry.first.trim().replace(Regex("\\s+"), " ")
                .equals("View all", ignoreCase = true)
        }
        DiagnosticInfo(
            EventName = "RENEWAL_VIEW_ALL_SEARCH",
            MessageText = "header=$HeaderBounds candidates=${ViewAllCandidates.size} " +
                    "tolerance=$RowTolerance"
        )

        val MatchedViewAll = ViewAllCandidates
            .filter { NodeEntry ->
                abs(NodeEntry.second.centerY() - HeaderBounds.centerY()) <= RowTolerance
            }
            .minByOrNull { NodeEntry ->
                abs(NodeEntry.second.centerY() - HeaderBounds.centerY())
            }
            ?: return false

        val TapAccepted = PerformTapGesture(
            XPos = MatchedViewAll.second.centerX().toFloat(),
            YPos = MatchedViewAll.second.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "RENEWAL_VIEW_ALL_TAP",
            MessageText = "bounds=${MatchedViewAll.second} headerY=${HeaderBounds.centerY()} " +
                    "accepted=$TapAccepted"
        )
        return TapAccepted
    }


    private fun OpenRenewalDateRangeDropdown() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                RunRenewalAutomationStep()
            }
            return
        }

        RenewalDropdownBaselineTexts = LatestRenewalVisibleNodes.toSet()
        RenewalDropdownScrollPasses = 0
        RenewalDropdownSeenOptions = emptySet()
        RenewalChipBounds = null

        val ChipTapped = try {
            TapRenewalDateRangeChip(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (!ChipTapped) {
            RenewalDropdownAttempts++
            if (RenewalDropdownAttempts >= RENEWAL_DROPDOWN_RETRY_LIMIT) {
                DiagnosticWarning(
                    EventName = "RENEWAL_DATE_RANGE_SKIPPED",
                    MessageText = "Date-range chip was not found; continuing with the default filter"
                )
                HasSelectedRenewalDateRange = true
                ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                    RunRenewalAutomationStep()
                }
                return
            }
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalAutomationStep()
            }
            return
        }

        ScheduleRenewalAction(DelayMs = RENEWAL_DROPDOWN_OPEN_DELAY_MS) {
            SelectLastRenewalDateRangeOption()
        }
    }

    private data class RenewalRangeOption(
        val TextValue: String,
        val BoundsObj: Rect,
        val SpanDays: Int
    )

    private fun TapRenewalDateRangeChip(RootNode: AccessibilityNodeInfo): Boolean {
        val TextNodes = CollectVisibleTextNodes(RootNode = RootNode)
        val ChipEntry = TextNodes.firstOrNull { NodeEntry ->
            RenewalDateRange.IsRangeLabel(TextValue = NodeEntry.first)
        } ?: run {
            DiagnosticWarning(
                EventName = "RENEWAL_DATE_RANGE_CHIP",
                MessageText = "No date-range chip matched among ${TextNodes.size} visible nodes"
            )
            return false
        }

        RenewalChipBounds = Rect(ChipEntry.second)

        val TapAccepted = PerformTapGesture(
            XPos = ChipEntry.second.centerX().toFloat(),
            YPos = ChipEntry.second.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_CHIP",
            MessageText = "text=[${ChipEntry.first}] bounds=${ChipEntry.second} accepted=$TapAccepted"
        )
        return TapAccepted
    }



    private fun SelectLastRenewalDateRangeOption() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                SelectLastRenewalDateRangeOption()
            }
            return
        }

        val VisibleEntries = try {
            CollectVisibleTextNodes(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        val ChipCentreY = RenewalChipBounds?.centerY()
        val RangeOptions = VisibleEntries.mapNotNull { NodeEntry ->
            val SpanDays = RenewalDateRange.SpanDays(TextValue = NodeEntry.first)
            when {
                SpanDays == null -> null
                ChipCentreY != null && NodeEntry.second.centerY() <= ChipCentreY -> null
                else -> RenewalRangeOption(
                    TextValue = NodeEntry.first,
                    BoundsObj = NodeEntry.second,
                    SpanDays = SpanDays
                )
            }
        }

        val OptionEntries = VisibleEntries.filter { NodeEntry ->
            val OptionText = NodeEntry.first.trim()
            OptionText.isNotEmpty() &&
                    OptionText.length <= 40 &&
                    !RenewalDropdownBaselineTexts.contains(NodeEntry.first)
        }

        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_OPTIONS",
            MessageText = "ranges=${RangeOptions.map { Entry -> Entry.TextValue }} " +
                    "newOptions=${OptionEntries.size} " +
                    "texts=${OptionEntries.take(12).map { Entry -> Entry.first }} " +
                    "scrollPasses=$RenewalDropdownScrollPasses"
        )

        if (RangeOptions.isEmpty()) {
            val SheetMarkerVisible = VisibleEntries.any { NodeEntry ->
                NodeEntry.first.contains("timeline", ignoreCase = true)
            }
            RenewalDropdownAttempts++
            if (SheetMarkerVisible || RenewalDropdownAttempts >= RENEWAL_DROPDOWN_RETRY_LIMIT) {
                IsRenewalRangeSheetHidden = true
                DiagnosticWarning(
                    EventName = "RENEWAL_DATE_RANGE_HIDDEN",
                    MessageText = "the Timeline sheet exposes no options; sheetMarker=$SheetMarkerVisible " +
                            "ocrAttempts=$RenewalRangeOcrCount visibleTexts=${OptionEntries.size}"
                )
                StartRenewalRangeOcr()
                return
            }
            ScheduleRenewalAction(DelayMs = RENEWAL_DROPDOWN_OPEN_DELAY_MS) {
                SelectLastRenewalDateRangeOption()
            }
            return
        }

        val RangeTexts = RangeOptions.map { Entry -> Entry.TextValue }.toSet()
        val FoundNewOptions =
            RangeOptions.isEmpty() || !RenewalDropdownSeenOptions.containsAll(RangeTexts)
        RenewalDropdownSeenOptions = RenewalDropdownSeenOptions + RangeTexts

        val BottomOption = RangeOptions
            .map { Entry -> Entry.TextValue to Entry.BoundsObj }
            .maxByOrNull { NodeEntry -> NodeEntry.second.centerY() }
            ?: return


        val ScreenHeight = resources.displayMetrics.heightPixels
        val LooksClipped = BottomOption.second.bottom >= ScreenHeight * 0.92f
        if (LooksClipped && FoundNewOptions && RenewalDropdownScrollPasses < 3) {
            RenewalDropdownScrollPasses++
            val ScrollAccepted = PerformPolicyScroll(
                ForwardVal = true,
                PreferAccessibilityAction = true
            )
            DiagnosticInfo(
                EventName = "RENEWAL_DATE_RANGE_SCROLL",
                MessageText = "pass=$RenewalDropdownScrollPasses accepted=$ScrollAccepted " +
                        "bottom=${BottomOption.second.bottom}"
            )
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                SelectLastRenewalDateRangeOption()
            }
            return
        }

        val TargetSpan = RenewalDateRange.ChooseSpanDays(
            AvailableSpans = RangeOptions.map { Entry -> Entry.SpanDays },
            TargetDays = RenewalRangeDays
        )
        val PickedOption = RangeOptions
            .filter { Entry -> TargetSpan == null || Entry.SpanDays == TargetSpan }
            .maxWithOrNull(
                compareBy(
                    { Entry -> Entry.SpanDays },
                    { Entry -> Entry.BoundsObj.centerY() }
                )
            )
        val ChosenOption = PickedOption
            ?.let { Entry -> Entry.TextValue to Entry.BoundsObj }
            ?: BottomOption
        if (RenewalDateRange.SpanDays(TextValue = ChosenOption.first) == null) {
            IsRenewalRangeSheetHidden = true
            DiagnosticWarning(
                EventName = "RENEWAL_DATE_RANGE_HIDDEN",
                MessageText = "no node parsed as a date range; refusing to tap " +
                        "[${ChosenOption.first}] at ${ChosenOption.second}"
            )
            StartRenewalRangeOcr()
            return
        }

        val TapAccepted = PerformTapGesture(
            XPos = ChosenOption.second.centerX().toFloat(),
            YPos = ChosenOption.second.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_SELECTED",
            MessageText = buildString {
                append("text=[${ChosenOption.first}] bounds=${ChosenOption.second} ")
                append("spanDays=${PickedOption?.SpanDays} ")
                append("wanted=$RenewalRangeDays target=$TargetSpan ")
                append("source=${if (PickedOption != null) "settings-range" else "bottom-most"} ")
                append("accepted=$TapAccepted")
            }
        )
        if (!TapAccepted) {
            RenewalDropdownAttempts++
            ScheduleRenewalAction(DelayMs = RENEWAL_DROPDOWN_OPEN_DELAY_MS) {
                SelectLastRenewalDateRangeOption()
            }
            return
        }

        HasSelectedRenewalDateRange = true
        RenewalDropdownAttempts = 0
        RenewalScrollStallCount = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalAutomationStep()
        }
    }

    private fun CollectVisibleTextNodes(
        RootNode: AccessibilityNodeInfo
    ): List<Pair<String, Rect>> {
        val ResultList = mutableListOf<Pair<String, Rect>>()
        CollectVisibleTextNodesInternal(TargetNode = RootNode, ResultList = ResultList)
        return ResultList
    }

    private fun CollectVisibleTextNodesInternal(
        TargetNode: AccessibilityNodeInfo,
        ResultList: MutableList<Pair<String, Rect>>
    ) {
        try {
            val NodeText = NodeTextValue(NodeRef = TargetNode)
            if (NodeText.isNotEmpty()) {
                val NodeBounds = Rect()
                TargetNode.getBoundsInScreen(NodeBounds)
                if (IsBoundsOnScreen(BoundsObj = NodeBounds)) {
                    ResultList.add(NodeText to Rect(NodeBounds))
                }
            }
            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                try {
                    CollectVisibleTextNodesInternal(
                        TargetNode = ChildNode,
                        ResultList = ResultList
                    )
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Node became stale while collecting bounds", ExceptionObj)
        }
    }


    private fun ScrollRenewalHistoryPage() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val BeforeScrollSignature = LatestRenewalVisibleSignature

        val PreferAccessibilityAction = RenewalScrollStallCount > 0
        val ScrollAccepted = PerformPolicyScroll(
            ForwardVal = true,
            PreferAccessibilityAction = PreferAccessibilityAction
        )
        DiagnosticInfo(
            EventName = "RENEWAL_SCROLL_FORWARD",
            MessageText = "page=$RenewalCurrentPage/$RenewalTotalPages accepted=$ScrollAccepted " +
                    "method=${if (PreferAccessibilityAction) "accessibility" else "gesture"} " +
                    "captured=${CapturedNodes.size}"
        )
        if (!ScrollAccepted) {
            BeginNextRenewalPage()
            return
        }

        ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
            CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            if (LatestRenewalVisibleSignature == BeforeScrollSignature) {
                RenewalScrollStallCount++
            } else {
                RenewalScrollStallCount = 0
            }
            DiagnosticInfo(
                EventName = "RENEWAL_SCROLL_RESULT",
                MessageText = "page=$RenewalCurrentPage signatureBefore=$BeforeScrollSignature " +
                        "signatureAfter=$LatestRenewalVisibleSignature stalls=$RenewalScrollStallCount"
            )
            if (RenewalScrollStallCount >= RENEWAL_SCROLL_STALL_LIMIT) {
                BeginNextRenewalPage()
            } else {
                ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                    ScrollRenewalHistoryPage()
                }
            }
        }
    }

    private fun BeginNextRenewalPage() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        DiagnosticInfo(
            EventName = "RENEWAL_PAGE_END",
            MessageText = "page=$RenewalCurrentPage total=$RenewalTotalPages " +
                    "selectorVisible=$IsRenewalPageSelectorVisible captured=${CapturedNodes.size}"
        )
        if (RenewalCurrentPage > 0 &&
            RenewalTotalPages > 0 &&
            RenewalCurrentPage >= RenewalTotalPages
        ) {
            CompleteRenewalAutomation()
            return
        }

        if (RenewalTotalPages <= 0 &&
            IsRenewalHistoryEmpty(VisibleNodes = LatestRenewalVisibleNodes)
        ) {
            DiagnosticInfo(
                EventName = "RENEWAL_HISTORY_EMPTY",
                MessageText = "the filter returned no rows, so there is no page selector to " +
                        "return to; finishing instead of scrolling back"
            )
            CompleteRenewalAutomation()
            return
        }

        RenewalReturnToTopCount = 0
        RenewalPageRetryCount = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
            ReturnToRenewalPageSelector()
        }
    }

    private fun IsRenewalHistoryEmpty(VisibleNodes: List<String>): Boolean {
        if (VisibleNodes.isEmpty()) return false

        val HasEmptyMessage = VisibleNodes.any { NodeText ->
            NodeText.contains("no recent policy renewal", ignoreCase = true) ||
                    NodeText.contains("no data found", ignoreCase = true) ||
                    NodeText.contains("no policies found", ignoreCase = true) ||
                    NodeText.contains("no policies due as per", ignoreCase = true)
        }
        if (HasEmptyMessage) return true

        val HasPoliciesLabel = VisibleNodes.any { NodeText ->
            NodeText.trim().equals("Policies", ignoreCase = true)
        }
        val HasZeroCount = VisibleNodes.any { NodeText ->
            val TrimmedText = NodeText.trim()
            TrimmedText.isNotEmpty() &&
                    TrimmedText.length <= 3 &&
                    TrimmedText.all { CharVal -> CharVal == '0' }
        }
        return HasPoliciesLabel && HasZeroCount
    }

    private fun ReturnToRenewalPageSelector() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val IsSelectorActuallyVisible = RenewalCurrentPage > 0 &&
                IsPolicyPageSelectorControlVisible(CurrentPage = RenewalCurrentPage)
        DiagnosticInfo(
            EventName = "RENEWAL_SELECTOR_VISIBILITY",
            MessageText = "page=$RenewalCurrentPage parsedInTree=$IsRenewalPageSelectorVisible " +
                    "controlOnScreen=$IsSelectorActuallyVisible returnAttempts=$RenewalReturnToTopCount"
        )
        if (IsSelectorActuallyVisible) {
            if (RenewalTotalPages in 1..RenewalCurrentPage) {
                CompleteRenewalAutomation()
            } else {
                OpenRenewalPageSelector()
            }
            return
        }

        RenewalReturnToTopCount++
        if (RenewalReturnToTopCount > RENEWAL_RETURN_TO_TOP_LIMIT) {
            RelistRenewalHistory(
                TargetPage = if (RenewalCurrentPage > 0) RenewalCurrentPage + 1 else 2,
                ReasonText = "Could not return to the renewal page selector"
            )
            return
        }

        val ScrollAccepted = PerformPolicyScroll(
            ForwardVal = false,
            PreferAccessibilityAction = RenewalReturnToTopCount % 2 == 0
        )
        DiagnosticInfo(
            EventName = "RENEWAL_SCROLL_BACK",
            MessageText = "attempt=$RenewalReturnToTopCount accepted=$ScrollAccepted " +
                    "page=$RenewalCurrentPage"
        )
        if (!ScrollAccepted && RenewalCurrentPage > 0) {
            OpenRenewalPageSelector()
            return
        }

        ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
            ReturnToRenewalPageSelector()
        }
    }

    private fun OpenRenewalPageSelector() {
        val TargetPage = if (RenewalSkipTargetPage > RenewalCurrentPage) {
            RenewalSkipTargetPage
        } else {
            NextRenewalPageAfter(CurrentPage = RenewalCurrentPage)
        }
        if (EffectiveRenewalTotalPages() in 1..<TargetPage) {
            DiagnosticInfo(
                EventName = "RENEWAL_PAGE_PRESKIPPED",
                MessageText = "no page left after $RenewalCurrentPage " +
                        "(total=${EffectiveRenewalTotalPages()}); finishing"
            )
            CompleteRenewalAutomation()
            return
        }
        RenewalExpectedPage = TargetPage

        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            RetryRenewalPageNavigation("Renewal page selector root is unavailable")
            return
        }

        val SelectorClicked: Boolean
        val SelectorAdvanced: Boolean
        try {
            SelectorClicked = ClickPolicyPageSelector(
                RootNode = RootNode,
                CurrentPage = RenewalCurrentPage
            )
            SelectorAdvanced = !SelectorClicked && AdvancePolicyPageSelector(
                RootNode = RootNode,
                CurrentPage = RenewalCurrentPage
            )
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (SelectorClicked) {
            ScheduleRenewalAction(DelayMs = POLICY_PAGE_SELECTOR_DELAY_MS) {
                SelectNextRenewalPage()
            }
        } else if (SelectorAdvanced) {
            WaitForRenewalPageLoad()
        } else {
            RetryRenewalPageNavigation("Could not open the renewal page selector")
        }
    }

    private fun SelectNextRenewalPage() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            performGlobalAction(GLOBAL_ACTION_BACK)
            RetryRenewalPageNavigation("Renewal page options are unavailable")
            return
        }

        val PageSelected = try {
            ClickPolicyPageOption(RootNode = RootNode, PageNumber = RenewalExpectedPage)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (!PageSelected) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            RetryRenewalPageNavigation("Could not select renewal page $RenewalExpectedPage")
            return
        }

        DiagnosticInfo(
            EventName = "RENEWAL_PAGE_SELECTED",
            MessageText = "selected=$RenewalExpectedPage; waiting for the list to load"
        )
        WaitForRenewalPageLoad()
    }

    private fun WaitForRenewalPageLoad() {
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)

            if (!IsRenewalPageSelectorVisible || RenewalCurrentPage != RenewalExpectedPage) {
                if (RenewalExpectedPage > 1 &&
                    RenewalKnownTotalPages > 0 &&
                    IsRenewalHistoryEmpty(VisibleNodes = LatestRenewalVisibleNodes)
                ) {
                    DiagnosticWarning(
                        EventName = "RENEWAL_PAGE_EMPTIED",
                        MessageText = "page=$RenewalExpectedPage emptied the list " +
                                "(00 Policies); not retrying"
                    )
                    SkipStuckRenewalPage(StuckPage = RenewalExpectedPage)
                    return@ScheduleRenewalAction
                }
                RenewalPageRetryCount++
                DiagnosticWarning(
                    EventName = "RENEWAL_PAGE_WAIT",
                    MessageText = "expected=$RenewalExpectedPage actual=$RenewalCurrentPage " +
                            "selectorVisible=$IsRenewalPageSelectorVisible attempt=$RenewalPageRetryCount"
                )
                if (RenewalPageRetryCount >= RENEWAL_PAGE_RETRY_LIMIT) {
                    SkipStuckRenewalPage(StuckPage = RenewalExpectedPage)
                } else {
                    WaitForRenewalPageLoad()
                }
                return@ScheduleRenewalAction
            }

            if (RenewalTotalPages in 1..<RenewalCurrentPage) {
                CompleteRenewalAutomation()
                return@ScheduleRenewalAction
            }

            RenewalPageRetryCount = 0
            RenewalScrollStallCount = 0
            RenewalAutomationFailureCount = 0
            if (RenewalSkipTargetPage in 1..RenewalCurrentPage) RenewalSkipTargetPage = 0
            if (RenewalKnownBadPages.remove(RenewalCurrentPage)) {
                DiagnosticInfo(
                    EventName = "RENEWAL_PAGE_RECOVERED",
                    MessageText = "page=$RenewalCurrentPage loaded fine; dropping it from the skip list"
                )
                SaveRenewalSkipRecord()
            }
            DiagnosticInfo(
                EventName = "RENEWAL_PAGE_LOADED",
                MessageText = "page=$RenewalCurrentPage total=$RenewalTotalPages " +
                        "captured=${CapturedNodes.size}"
            )
            ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                ScrollRenewalHistoryPage()
            }
        }
    }

    private fun RetryRenewalPageNavigation(ReasonText: String) {
        RenewalPageRetryCount++
        Log.w(LOG_TAG, "$ReasonText (attempt $RenewalPageRetryCount)")
        DiagnosticWarning(
            EventName = "RENEWAL_NAVIGATION_RETRY",
            MessageText = "$ReasonText; attempt=$RenewalPageRetryCount " +
                    "page=$RenewalCurrentPage expected=$RenewalExpectedPage total=$RenewalTotalPages"
        )
        if (RenewalPageRetryCount >= RENEWAL_PAGE_RETRY_LIMIT) {
            FailRenewalAutomation(ReasonText)
            return
        }
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            ReturnToRenewalPageSelector()
        }
    }

    private fun StartRenewalRangeOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            RenewalRangeOcrCount >= RENEWAL_RANGE_OCR_LIMIT
        ) {
            HoldForManualRenewalRange(ReasonText = "no OCR available for the Timeline sheet")
            return
        }
        RenewalRangeOcrCount++
        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_OCR_START",
            MessageText = "attempt=$RenewalRangeOcrCount"
        )
        try {
            CustomerSheetOcr.ReadSheetBoxes(
                ServiceRef = this,
                ExecutorRef = OcrExecutor,
                TopFraction = RENEWAL_RANGE_OCR_TOP_FRACTION
            ) { OutcomeVal ->
                MainHandler.post { FinishRenewalRangeOcr(OutcomeVal = OutcomeVal) }
            }
        } catch (ExceptionObj: Exception) {
            HoldForManualRenewalRange(
                ReasonText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
    }

    private fun FinishRenewalRangeOcr(OutcomeVal: CustomerSheetOcr.BoxOutcome) {
        if (!IsCapturing || CurrentMode != CaptureMode.FUP) return
        if (OutcomeVal is CustomerSheetOcr.BoxOutcome.Failed) {
            DiagnosticWarning(
                EventName = "RENEWAL_DATE_RANGE_OCR_FAILED",
                MessageText = "reason=[${OutcomeVal.Reason}]"
            )
            HoldForManualRenewalRange(ReasonText = OutcomeVal.Reason)
            return
        }

        val BoxItems = (OutcomeVal as CustomerSheetOcr.BoxOutcome.Boxes).Items
        val RangeBoxes = BoxItems.mapNotNull { BoxItem ->
            val SpanDays = RenewalDateRange.SpanDays(TextValue = BoxItem.TextValue)
            if (SpanDays == null) null else BoxItem to SpanDays
        }
        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_OCR_READ",
            MessageText = "lines=${BoxItems.size} ranges=" +
                    RangeBoxes.joinToString(separator = ",") { Entry -> Entry.first.TextValue }
                        .ifEmpty { "none" }
        )
        if (RangeBoxes.isEmpty()) {
            HoldForManualRenewalRange(ReasonText = "the Timeline sheet had no readable range labels")
            return
        }

        val TargetSpan = RenewalDateRange.ChooseSpanDays(
            AvailableSpans = RangeBoxes.map { Entry -> Entry.second },
            TargetDays = RenewalRangeDays
        )
        val ChosenBox = RangeBoxes
            .filter { Entry -> TargetSpan == null || Entry.second == TargetSpan }
            .maxByOrNull { Entry -> Entry.second }
            ?: RangeBoxes.maxByOrNull { Entry -> Entry.second }
            ?: return
        val ScreenHeight = resources.displayMetrics.heightPixels
        val TapY = ChosenBox.first.Bounds.centerY().toFloat()
        if (TapY <= ScreenHeight * RENEWAL_RANGE_OCR_TOP_FRACTION) {
            HoldForManualRenewalRange(ReasonText = "the OCR match was outside the sheet area")
            return
        }
        val TapAccepted = PerformTapGesture(
            XPos = ChosenBox.first.Bounds.centerX().toFloat(),
            YPos = TapY
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_OCR_PICK",
            MessageText = "text=[${ChosenBox.first.TextValue}] spanDays=${ChosenBox.second} " +
                    "wanted=$RenewalRangeDays target=$TargetSpan " +
                    "bounds=${ChosenBox.first.Bounds} accepted=$TapAccepted"
        )
        if (!TapAccepted) {
            HoldForManualRenewalRange(ReasonText = "the Timeline tap was refused")
            return
        }

        RenewalPickedRangeLabel = ChosenBox.first.TextValue
        HasSelectedRenewalDateRange = true
        RenewalDropdownAttempts = 0
        RenewalScrollStallCount = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalAutomationStep()
        }
    }

    private fun MaybeReselectRenewalRange(): Boolean {
        if (!HasSelectedRenewalDateRange) return false
        if (RenewalPickedRangeLabel.isEmpty()) return false
        if (RenewalCurrentPage > 0 || RenewalTotalPages > 0) return false
        if (!IsRenewalHistoryEmpty(VisibleNodes = LatestRenewalVisibleNodes)) return false
        if (RenewalRangeOcrCount >= RENEWAL_RANGE_OCR_LIMIT) return false

        val CurrentLabel = CurrentRenewalRangeLabel()
        val WantedSpan = RenewalDateRange.SpanDays(TextValue = RenewalPickedRangeLabel)
        val ShowingSpan = RenewalDateRange.SpanDays(TextValue = CurrentLabel)
        if (WantedSpan != null && WantedSpan == ShowingSpan) return false

        DiagnosticWarning(
            EventName = "RENEWAL_DATE_RANGE_LOST",
            MessageText = "picked=[$RenewalPickedRangeLabel] showing=[" +
                    CurrentLabel.ifEmpty { "unknown" } + "] and the list is empty; reopening Timeline"
        )
        HasSelectedRenewalDateRange = false
        RenewalDropdownAttempts = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
            OpenRenewalDateRangeDropdown()
        }
        return true
    }

    private fun HoldForManualRenewalRange(ReasonText: String) {
        IsRenewalRangeSheetHidden = true
        RenewalManualWaitCount = 0
        RenewalRangeBaselineLabel = CurrentRenewalRangeLabel()
        DiagnosticWarning(
            EventName = "RENEWAL_DATE_RANGE_MANUAL",
            MessageText = "reason=[$ReasonText] currentRange=[" +
                    RenewalRangeBaselineLabel.ifEmpty { "unknown" } + "]; waiting for you to pick one"
        )
        ShowServiceToast(
            MessageText = "Pick a date range in the app; capture continues on its own",
            KindVal = AppToast.Kind.Warning
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            WaitForManualRenewalRange()
        }
    }

    private fun WaitForManualRenewalRange() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        val CurrentLabel = CurrentRenewalRangeLabel()
        val LabelChanged = CurrentLabel.isNotEmpty() &&
                !CurrentLabel.equals(RenewalRangeBaselineLabel, ignoreCase = true)
        val ListReady = RenewalTotalPages > 0 || RenewalCurrentPage > 0

        if (LabelChanged || (ListReady && RenewalManualWaitCount >= 2)) {
            DiagnosticInfo(
                EventName = "RENEWAL_DATE_RANGE_MANUAL_DONE",
                MessageText = "range=[${CurrentLabel.ifEmpty { "unchanged" }}] " +
                        "waits=$RenewalManualWaitCount"
            )
            HasSelectedRenewalDateRange = true
            RenewalDropdownAttempts = 0
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalAutomationStep()
            }
            return
        }

        RenewalManualWaitCount++
        if (RenewalManualWaitCount > RENEWAL_MANUAL_RANGE_WAIT_LIMIT) {
            DiagnosticWarning(
                EventName = "RENEWAL_DATE_RANGE_MANUAL_TIMEOUT",
                MessageText = "no range was picked; continuing with [" +
                        CurrentLabel.ifEmpty { "whatever is set" } + "]"
            )
            HasSelectedRenewalDateRange = true
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalAutomationStep()
            }
            return
        }
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            WaitForManualRenewalRange()
        }
    }

    private fun CurrentRenewalRangeLabel(): String {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return ""
        val TextNodes = try {
            CollectVisibleTextNodes(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
        val ScreenHeight = resources.displayMetrics.heightPixels
        return TextNodes
            .filter { NodeEntry ->
                NodeEntry.second.centerY() <= ScreenHeight * 0.35f &&
                        RenewalDateRange.SpanDays(TextValue = NodeEntry.first) != null
            }
            .minByOrNull { NodeEntry -> NodeEntry.second.centerY() }
            ?.first
            .orEmpty()
    }

    private fun SkipStuckRenewalPage(StuckPage: Int) {
        if (StuckPage <= 0) {
            FailRenewalAutomation("Renewal page $StuckPage did not finish loading")
            return
        }
        RenewalSkippedPages.add(StuckPage)
        RenewalKnownBadPages.add(StuckPage)
        SaveRenewalSkipRecord()
        val TotalPagesKnown = EffectiveRenewalTotalPages()
        if (TotalPagesKnown in 1..StuckPage) {
            DiagnosticWarning(
                EventName = "RENEWAL_PAGE_SKIPPED",
                MessageText = "page=$StuckPage total=$TotalPagesKnown was the last page; " +
                        "finishing with what is captured"
            )
            CompleteRenewalAutomation()
            return
        }
        DiagnosticWarning(
            EventName = "RENEWAL_PAGE_SKIPPED",
            MessageText = "page=$StuckPage total=$TotalPagesKnown never rendered; " +
                    "reopening the list and jumping to ${StuckPage + 1}"
        )
        RelistRenewalHistory(
            TargetPage = StuckPage + 1,
            ReasonText = "Renewal page $StuckPage did not finish loading"
        )
    }

    private fun RelistRenewalHistory(TargetPage: Int, ReasonText: String) {
        RenewalRelistCount++
        val RelistCeiling = if (EffectiveRenewalTotalPages() > 0) {
            EffectiveRenewalTotalPages() + RENEWAL_RELIST_LIMIT
        } else {
            RENEWAL_RELIST_LIMIT
        }
        if (RenewalRelistCount > RelistCeiling) {
            FailRenewalAutomation(ReasonText)
            return
        }
        if (TargetPage <= RenewalLastRelistTarget) {
            RenewalRelistStalls++
            if (RenewalRelistStalls >= RENEWAL_RELIST_STALL_LIMIT) {
                DiagnosticWarning(
                    EventName = "RENEWAL_RELIST_STALLED",
                    MessageText = "target=$TargetPage never moved past $RenewalLastRelistTarget"
                )
                FailRenewalAutomation(ReasonText)
                return
            }
        } else {
            RenewalRelistStalls = 0
            RenewalLastRelistTarget = TargetPage
        }
        if (EffectiveRenewalTotalPages() in 1..<TargetPage) {
            DiagnosticInfo(
                EventName = "RENEWAL_RELIST_COMPLETE",
                MessageText = "target=$TargetPage is past total=${EffectiveRenewalTotalPages()}; " +
                        "finishing"
            )
            CompleteRenewalAutomation()
            return
        }
        RenewalSkipTargetPage = TargetPage
        RenewalSkipWaitCount = 0
        RenewalRangeOcrCount = 0
        HasOpenedRenewalHistoryList = false
        HasSelectedRenewalDateRange = false
        RenewalCurrentPage = 0
        RenewalExpectedPage = 0
        RenewalPageRetryCount = 0
        RenewalReturnToTopCount = 0
        RenewalScrollStallCount = 0
        RenewalDashboardScrollCount = 0
        RenewalDropdownAttempts = 0
        RenewalDropdownScrollPasses = 0
        RenewalDropdownSeenOptions = emptySet()
        RenewalChipBounds = null
        RenewalUnknownScreenCount = 0
        IsRenewalPageSelectorVisible = false
        DiagnosticWarning(
            EventName = "RENEWAL_RELIST",
            MessageText = "reason=[$ReasonText] target=$TargetPage attempt=$RenewalRelistCount " +
                    "skipped=${RenewalSkippedPages.joinToString(separator = ",").ifEmpty { "none" }}"
        )
        performGlobalAction(GLOBAL_ACTION_BACK)
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalAutomationStep()
        }
    }

    private fun EffectiveRenewalTotalPages(): Int {
        if (RenewalTotalPages > 0) return RenewalTotalPages
        return RenewalKnownTotalPages
    }

    private fun CurrentRenewalSpanDays(): Int {
        val PickedSpan = RenewalDateRange.SpanDays(TextValue = RenewalPickedRangeLabel)
        if (PickedSpan != null) return PickedSpan
        return RenewalDateRange.SpanDays(TextValue = CurrentRenewalRangeLabel()) ?: 0
    }

    private fun SaveRenewalSkipRecord() {
        if (CurrentSessionId.isBlank()) return
        val SpanDays = if (RenewalKnownBadSpanDays > 0) {
            RenewalKnownBadSpanDays
        } else {
            CurrentRenewalSpanDays()
        }
        RenewalKnownBadSpanDays = SpanDays
        try {
            PolicyRepository.SaveRenewalSkips(
                ContextRef = this,
                SessionId = CurrentSessionId,
                RecordObj = PolicyRepository.RenewalSkipRecord(
                    SpanDays = SpanDays,
                    TotalPages = RenewalKnownTotalPages,
                    Pages = RenewalKnownBadPages.toList().sorted(),
                    SavedAt = System.currentTimeMillis()
                )
            )
            DiagnosticInfo(
                EventName = "RENEWAL_SKIPS_SAVED",
                MessageText = "spanDays=$SpanDays total=$RenewalKnownTotalPages " +
                        "pages=${RenewalKnownBadPages.joinToString(separator = ",")}"
            )
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "RENEWAL_SKIPS_SAVE_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
    }

    private fun IsKnownBadRenewalPage(PageNumber: Int): Boolean {
        if (RenewalKnownBadPages.isEmpty()) return false
        if (!RenewalKnownBadPages.contains(PageNumber)) return false
        if (RenewalKnownBadSpanDays <= 0) return true
        val CurrentSpan = CurrentRenewalSpanDays()
        if (CurrentSpan <= 0) return true
        return CurrentSpan == RenewalKnownBadSpanDays
    }

    private fun NextRenewalPageAfter(CurrentPage: Int): Int {
        var Candidate = if (CurrentPage > 0) CurrentPage + 1 else 2
        val TotalPagesKnown = EffectiveRenewalTotalPages()
        val Ceiling = if (TotalPagesKnown > 0) TotalPagesKnown else Int.MAX_VALUE
        while (Candidate <= Ceiling && IsKnownBadRenewalPage(PageNumber = Candidate)) {
            DiagnosticInfo(
                EventName = "RENEWAL_PAGE_PRESKIPPED",
                MessageText = "page=$Candidate is known bad for this range; trying ${Candidate + 1}"
            )
            Candidate++
        }
        return Candidate
    }

    private fun MaybeJumpToRenewalSkipTarget(): Boolean {
        val TargetPage = RenewalSkipTargetPage
        if (TargetPage <= 1) return false
        if (RenewalCurrentPage <= 0) {
            RenewalSkipWaitCount++
            if (RenewalSkipWaitCount > RENEWAL_SKIP_WAIT_LIMIT) {
                DiagnosticWarning(
                    EventName = "RENEWAL_SKIP_ABANDONED",
                    MessageText = "target=$TargetPage never saw a page number after the relist; " +
                            "walking from the top instead"
                )
                RenewalSkipTargetPage = 0
                return false
            }
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalAutomationStep()
            }
            return true
        }
        RenewalSkipWaitCount = 0
        if (RenewalCurrentPage >= TargetPage) {
            RenewalSkipTargetPage = 0
            return false
        }
        DiagnosticInfo(
            EventName = "RENEWAL_SKIP_JUMP",
            MessageText = "from=$RenewalCurrentPage target=$TargetPage " +
                    "total=$RenewalTotalPages"
        )
        RenewalReturnToTopCount = 0
        OpenRenewalPageSelector()
        return true
    }

    private fun CompleteRenewalAutomation() {
        if (IsRenewalAutomationComplete) return
        IsRenewalAutomationComplete = true
        IsRenewalAutomationRunning = false
        RenewalAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        RenewalAutomationRunnable = null

        val RecordCount = if (CurrentMode == CaptureMode.RENEWAL_DUE) {
            CapturedRenewalDueMap.size
        } else {
            CapturedFupMap.size
        }
        val SkippedText = RenewalSkippedPages.joinToString(separator = ", ")
        DiagnosticInfo(
            EventName = "RENEWAL_AUTOMATION_COMPLETE",
            MessageText = "records=$RecordCount nodes=${CapturedNodes.size} " +
                    "page=$RenewalCurrentPage/$RenewalTotalPages " +
                    "skippedPages=${SkippedText.ifEmpty { "none" }}"
        )
        val RecordNoun = CurrentMode.RecordNounPlural
        ShowServiceToast(
            MessageText = if (SkippedText.isEmpty()) {
                "Captured $RecordCount $RecordNoun"
            } else {
                "Captured $RecordCount $RecordNoun; page $SkippedText would not load"
            },
            KindVal = if (SkippedText.isEmpty()) AppToast.Kind.Success else AppToast.Kind.Warning
        )

        MainHandler.postDelayed({
            if (IsCapturing && IsRenewalFamilyMode() && IsRenewalAutomationComplete) {
                FinishCaptureSession()
            }
        }, RENEWAL_PAGE_LOAD_DELAY_MS)
    }

    private fun FailRenewalAutomation(ReasonText: String) {
        IsRenewalAutomationComplete = false
        IsRenewalAutomationRunning = false
        RenewalAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        RenewalAutomationRunnable = null
        RenewalAutomationFailureCount++
        val CanRetryAutomatically =
            RenewalAutomationFailureCount < RENEWAL_AUTOMATION_RECOVERY_LIMIT
        RenewalAutomationRetryAfter = if (CanRetryAutomatically) {
            System.currentTimeMillis() + RENEWAL_FAILURE_RETRY_MS
        } else {
            Long.MAX_VALUE
        }
        RenewalPageRetryCount = 0
        RenewalReturnToTopCount = 0
        RenewalScrollStallCount = 0
        RenewalUnknownScreenCount = 0
        RenewalDashboardScrollCount = 0
        Log.w(LOG_TAG, "Renewal automation stopped: $ReasonText")
        DiagnosticWarning(
            EventName = "RENEWAL_AUTOMATION_RECOVERY",
            MessageText = "reason=[$ReasonText] recovery=$RenewalAutomationFailureCount " +
                    "automaticRetry=$CanRetryAutomatically page=$RenewalCurrentPage " +
                    "expected=$RenewalExpectedPage total=$RenewalTotalPages " +
                    "nodes=${CapturedNodes.size}"
        )
        ShowServiceToast(
            MessageText = if (CanRetryAutomatically) {
                "Renewal automation paused: $ReasonText. Retrying automatically."
            } else {
                "Renewal automation stopped after repeated failures. Captured data is preserved."
            },
            KindVal = if (CanRetryAutomatically) AppToast.Kind.Warning else AppToast.Kind.Error
        )
        RefreshBubble()
    }


    private fun IsRenewalsDueListScreen(VisibleNodes: List<String>): Boolean {
        if (IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes)) return false
        val HasTitle = VisibleNodes.any { NodeText ->
            NodeText.trim().equals("Renewals Due", ignoreCase = true) ||
                    NodeText.trim().equals("All Renewals Due", ignoreCase = true)
        }
        if (!HasTitle) return false
        return VisibleNodes.any { NodeText ->
            NodeText.contains(RENEWAL_DUE_CUSTOMER_TOGGLE, ignoreCase = true) ||
                    NodeText.contains(RENEWAL_DUE_POLICY_TOGGLE, ignoreCase = true) ||
                    NodeText.contains("Renewal Policies", ignoreCase = true)
        }
    }

    private fun IsAllRenewalsDueScreen(VisibleNodes: List<String>): Boolean {
        return VisibleNodes.any { NodeText ->
            NodeText.trim().equals("All Renewals Due", ignoreCase = true)
        }
    }

    private fun IsRenewalsDueCustomerScreen(VisibleNodes: List<String>): Boolean {
        if (!IsRenewalsDueListScreen(VisibleNodes = VisibleNodes)) return false
        if (IsAllRenewalsDueScreen(VisibleNodes = VisibleNodes)) return false
        val HasPolicyViewChips = VisibleNodes.any { NodeText ->
            NodeText.contains("Filter & Sort", ignoreCase = true) ||
                    NodeText.contains("Campaign Eligible", ignoreCase = true) ||
                    NodeText.contains("Special Revival", ignoreCase = true)
        }
        if (HasPolicyViewChips) return false
        val HasCustomerGroups = VisibleNodes.any { NodeText ->
            NodeText.contains("Renewal Policies", ignoreCase = true)
        }
        val HasTimelineChip = VisibleNodes.any { NodeText ->
            RenewalDueRange.IsRangeLabel(TextValue = NodeText)
        }
        return HasCustomerGroups || HasTimelineChip
    }

    private fun NormaliseCustomerKey(NameText: String): String {
        return NameText.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    private fun CurrentRenewalDueRangeLabel(): String {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return ""
        return try {
            CollectVisibleTextNodes(RootNode = RootNode)
                .firstOrNull { NodeEntry ->
                    RenewalDueRange.SpanDays(TextValue = NodeEntry.first) != null
                }?.first.orEmpty()
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun CaptureRenewalDueSnapshot(PackageNameVal: String, VisibleNodes: List<String>) {
        if (PackageNameVal != AppLauncherUtils.LIC_SUPER_APP_PACKAGE) return
        if (!IsRenewalsDueListScreen(VisibleNodes = VisibleNodes)) return

        val HolderName = if (IsAllRenewalsDueScreen(VisibleNodes = VisibleNodes)) {
            RenewalDueActiveCustomer
        } else {
            ""
        }

        val ParsedList = try {
            if (HolderName.isNotEmpty()) {
                RenewalDueParser.Parse(Nodes = VisibleNodes, HolderName = HolderName)
            } else {
                ParseCustomerViewRenewalDue(VisibleNodes = VisibleNodes)
            }
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "RENEWAL_DUE_PARSE_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            emptyList()
        }
        if (ParsedList.isEmpty()) return

        var NewCount = 0
        for (IncomingRecord in ParsedList) {
            if (IncomingRecord.PolicyNumber.isEmpty()) continue
            val KeyText = RecordMerge.RenewalDueKey(RecordItem = IncomingRecord)
            val ExistingRecord = CapturedRenewalDueMap[KeyText]
            if (ExistingRecord == null) {
                CapturedRenewalDueMap[KeyText] = IncomingRecord
                NewCount++
            } else {
                CapturedRenewalDueMap[KeyText] = RecordMerge.MergeRenewalDue(
                    ExistingItem = ExistingRecord,
                    IncomingItem = IncomingRecord
                ).Record
            }
        }

        if (NewCount > 0) {
            RebuildCapturedRenewalDueNodes()
            DiagnosticInfo(
                EventName = "RENEWALS_DUE_CAPTURED",
                MessageText = "new=$NewCount total=${CapturedRenewalDueMap.size} " +
                        "customer=[${HolderName.ifEmpty { "list" }}] " +
                        "page=$RenewalCurrentPage/$RenewalTotalPages"
            )
        }
    }

    private fun ParseCustomerViewRenewalDue(VisibleNodes: List<String>): List<RenewalDuePolicy> {
        val GroupList = RenewalDueParser.ReadCustomerGroups(Nodes = VisibleNodes)
        if (GroupList.isEmpty()) return emptyList()

        val SingleNames = GroupList
            .filter { GroupItem -> GroupItem.PolicyCount == 1 }
            .map { GroupItem -> GroupItem.HolderName }
        if (SingleNames.isEmpty()) return emptyList()

        val ResultList = mutableListOf<RenewalDuePolicy>()
        for (NameText in SingleNames) {
            val SliceNodes = SliceCustomerBlock(VisibleNodes = VisibleNodes, HolderName = NameText)
            if (SliceNodes.isEmpty()) continue
            val ParsedList = RenewalDueParser.Parse(Nodes = SliceNodes, HolderName = NameText)
            if (ParsedList.size != 1) continue
            ResultList.addAll(ParsedList)
            VisitedRenewalDueCustomers.add(NormaliseCustomerKey(NameText = NameText))
        }
        return ResultList
    }

    private fun SliceCustomerBlock(
        VisibleNodes: List<String>,
        HolderName: String
    ): List<String> {
        val StartIndex = VisibleNodes.indexOfFirst { NodeText ->
            NodeText.trim().equals(HolderName, ignoreCase = true)
        }
        if (StartIndex < 0) return emptyList()

        var EndIndex = VisibleNodes.size
        for (NodeIdx in (StartIndex + 1) until VisibleNodes.size) {
            if (RenewalDueParser.IsPlausibleCustomerName(TextValue = VisibleNodes[NodeIdx]) &&
                !VisibleNodes[NodeIdx].trim().equals(HolderName, ignoreCase = true)
            ) {
                EndIndex = NodeIdx
                break
            }
        }
        return VisibleNodes.subList(StartIndex, EndIndex)
    }

    private fun RebuildCapturedRenewalDueNodes() {
        CapturedNodes.clear()
        for (DueItem in CapturedRenewalDueMap.values) {
            if (DueItem.PolicyNumber.isNotEmpty()) CapturedNodes.add(DueItem.PolicyNumber)
            if (DueItem.HolderName.isNotEmpty()) CapturedNodes.add(DueItem.HolderName)
            if (DueItem.PlanName.isNotEmpty()) CapturedNodes.add(DueItem.PlanName)
            if (DueItem.PremiumAmount.isNotEmpty()) CapturedNodes.add(DueItem.PremiumAmount)
            if (DueItem.DateLabel.isNotEmpty()) CapturedNodes.add(DueItem.DateLabel)
            if (DueItem.DateValue.isNotEmpty()) CapturedNodes.add(DueItem.DateValue)
            if (DueItem.AutoPay.isNotEmpty()) CapturedNodes.add(DueItem.AutoPay)
        }
    }

    private fun HandleRenewalDueScreenAutomation(VisibleNodes: List<String>) {
        if (IsRenewalsDueListScreen(VisibleNodes = VisibleNodes)) {
            HasClickedHomeNavTab = true
            HasOpenedRenewalDueList = true
            HomeNavClickAttempts = 0
            HomeNavLastAttemptAt = 0L
            StartRenewalAutomation()
            return
        }

        if (IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes)) {
            HasClickedHomeNavTab = true
            HomeNavClickAttempts = 0
            HomeNavLastAttemptAt = 0L
            StartRenewalAutomation()
            return
        }
    }

    private fun RunRenewalDueStep() {
        val VisibleNodes = LatestRenewalVisibleNodes

        if (IsAllRenewalsDueScreen(VisibleNodes = VisibleNodes)) {
            RenewalUnknownScreenCount = 0
            WalkAllRenewalsDuePage()
            return
        }

        if (IsRenewalsDueListScreen(VisibleNodes = VisibleNodes)) {
            RenewalUnknownScreenCount = 0
            HasOpenedRenewalDueList = true
            if (!IsRenewalsDueCustomerScreen(VisibleNodes = VisibleNodes)) {
                SwitchToRenewalDueCustomerView()
                return
            }
            HasSwitchedToCustomerView = true
            RenewalDueToggleAttempts = 0
            if (!HasSelectedRenewalDateRange || HasRenewalDueRangeDrifted()) {
                OpenRenewalDueRangeDropdown()
                return
            }
            WalkRenewalDueCustomerList()
            return
        }

        if (IsRenewalsDashboardScreen(VisibleNodes = VisibleNodes)) {
            RenewalUnknownScreenCount = 0
            OpenRenewalsDueFromDashboard()
            return
        }

        RenewalUnknownScreenCount++
        if (RenewalUnknownScreenCount > RENEWAL_DASHBOARD_SCROLL_LIMIT) {
            FailRenewalAutomation("Neither the Renewals Dashboard nor the Renewals Due list was visible")
            return
        }
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_SCREEN_WAIT",
            MessageText = "attempt=$RenewalUnknownScreenCount nodes=${VisibleNodes.size}"
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalDueStep()
        }
    }

    private fun OpenRenewalsDueFromDashboard() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                RunRenewalDueStep()
            }
            return
        }

        val TapAccepted = try {
            TapSectionViewAll(RootNode = RootNode, HeaderText = RENEWAL_DUE_SECTION_HEADER)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
        if (TapAccepted) {
            HasOpenedRenewalDueList = true
            RenewalDashboardScrollCount = 0
            DiagnosticInfo(
                EventName = "RENEWAL_DUE_VIEW_ALL_CLICKED",
                MessageText = "Waiting for the Renewals Due page to load"
            )
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalDueStep()
            }
            return
        }

        RenewalDashboardScrollCount++
        if (RenewalDashboardScrollCount > RENEWAL_DASHBOARD_SCROLL_LIMIT) {
            FailRenewalAutomation("Could not reach the RENEWALS DUE section")
            return
        }
        val ScrollAccepted = ScrollActiveWindow(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_DASHBOARD_SCROLL",
            MessageText = "attempt=$RenewalDashboardScrollCount accepted=$ScrollAccepted"
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
            RunRenewalDueStep()
        }
    }

    private fun SwitchToRenewalDueCustomerView() {
        RenewalDueToggleAttempts++
        if (RenewalDueToggleAttempts > RENEWAL_DUE_TOGGLE_RETRY_LIMIT) {
            FailRenewalAutomation("Could not switch the Renewals Due list to Customer View")
            return
        }

        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                RunRenewalDueStep()
            }
            return
        }

        val TapAccepted = try {
            val ToggleEntry = CollectVisibleTextNodes(RootNode = RootNode).firstOrNull { NodeEntry ->
                NodeEntry.first.trim().equals(RENEWAL_DUE_CUSTOMER_TOGGLE, ignoreCase = true)
            }
            if (ToggleEntry == null) {
                false
            } else {
                PerformTapGesture(
                    XPos = ToggleEntry.second.centerX().toFloat(),
                    YPos = ToggleEntry.second.centerY().toFloat()
                )
            }
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        DiagnosticInfo(
            EventName = "RENEWAL_DUE_CUSTOMER_VIEW",
            MessageText = "attempt=$RenewalDueToggleAttempts accepted=$TapAccepted"
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalDueStep()
        }
    }

    private fun OpenRenewalDueRangeDropdown() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                RunRenewalDueStep()
            }
            return
        }

        RenewalDropdownBaselineTexts = LatestRenewalVisibleNodes.toSet()
        RenewalDropdownScrollPasses = 0
        RenewalDropdownSeenOptions = emptySet()
        RenewalChipBounds = null

        val TapAccepted = try {
            val ChipEntry = CollectVisibleTextNodes(RootNode = RootNode).firstOrNull { NodeEntry ->
                RenewalDueRange.IsRangeLabel(TextValue = NodeEntry.first)
            }
            if (ChipEntry == null) {
                DiagnosticWarning(
                    EventName = "RENEWAL_DUE_RANGE_CHIP",
                    MessageText = "No Timeline chip matched on the Customer View"
                )
                false
            } else {
                RenewalChipBounds = Rect(ChipEntry.second)
                val Accepted = PerformTapGesture(
                    XPos = ChipEntry.second.centerX().toFloat(),
                    YPos = ChipEntry.second.centerY().toFloat()
                )
                DiagnosticInfo(
                    EventName = "RENEWAL_DUE_RANGE_CHIP",
                    MessageText = "text=[${ChipEntry.first}] bounds=${ChipEntry.second} " +
                            "accepted=$Accepted"
                )
                Accepted
            }
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (!TapAccepted) {
            RenewalDropdownAttempts++
            if (RenewalDropdownAttempts >= RENEWAL_DROPDOWN_RETRY_LIMIT) {
                DiagnosticWarning(
                    EventName = "RENEWAL_DUE_RANGE_SKIPPED",
                    MessageText = "Giving up on the Timeline chip; using whatever range is showing"
                )
                HasSelectedRenewalDateRange = true
                ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                    RunRenewalDueStep()
                }
                return
            }
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalDueStep()
            }
            return
        }

        ScheduleRenewalAction(DelayMs = RENEWAL_DROPDOWN_OPEN_DELAY_MS) {
            SelectRenewalDueRangeOption()
        }
    }

    private fun SelectRenewalDueRangeOption() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                SelectRenewalDueRangeOption()
            }
            return
        }

        val OptionEntries = try {
            CollectVisibleTextNodes(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        val ChipCentreY = RenewalChipBounds?.centerY()
        val RangeOptions = OptionEntries.mapNotNull { NodeEntry ->
            val SpanDays = RenewalDueRange.SpanDays(TextValue = NodeEntry.first)
            when {
                SpanDays == null -> null
                ChipCentreY != null && NodeEntry.second.centerY() <= ChipCentreY -> null
                else -> RenewalRangeOption(
                    TextValue = NodeEntry.first,
                    BoundsObj = NodeEntry.second,
                    SpanDays = SpanDays
                )
            }
        }

        DiagnosticInfo(
            EventName = "RENEWAL_DUE_RANGE_OPTIONS",
            MessageText = "candidates=${RangeOptions.size} " +
                    "labels=[${RangeOptions.joinToString(separator = " | ") { it.TextValue }}] " +
                    "scrollPasses=$RenewalDropdownScrollPasses"
        )

        if (RangeOptions.isEmpty()) {
            RenewalDropdownAttempts++
            if (RenewalDropdownAttempts >= RENEWAL_DROPDOWN_RETRY_LIMIT) {
                IsRenewalRangeSheetHidden = true
                DiagnosticWarning(
                    EventName = "RENEWAL_DUE_RANGE_HIDDEN",
                    MessageText = "The Timeline sheet is not in the tree; trying OCR"
                )
                StartRenewalDueRangeOcr()
                return
            }
            ScheduleRenewalAction(DelayMs = RENEWAL_DROPDOWN_OPEN_DELAY_MS) {
                SelectRenewalDueRangeOption()
            }
            return
        }

        val TargetSpan = RenewalDueRange.ChooseSpanDays(
            AvailableSpans = RangeOptions.map { OptionRef -> OptionRef.SpanDays },
            TargetDays = RenewalDueRangeDays
        )
        val ChosenOption = RangeOptions
            .filter { OptionRef -> OptionRef.SpanDays == TargetSpan }
            .minByOrNull { OptionRef -> OptionRef.BoundsObj.centerY() }
            ?: RangeOptions.first()

        val TapAccepted = PerformTapGesture(
            XPos = ChosenOption.BoundsObj.centerX().toFloat(),
            YPos = ChosenOption.BoundsObj.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_RANGE_SELECTED",
            MessageText = "picked=[${ChosenOption.TextValue}] span=${ChosenOption.SpanDays} " +
                    "wanted=$RenewalDueRangeDays target=$TargetSpan accepted=$TapAccepted"
        )

        if (!TapAccepted) {
            RenewalDropdownAttempts++
            ScheduleRenewalAction(DelayMs = RENEWAL_DROPDOWN_OPEN_DELAY_MS) {
                SelectRenewalDueRangeOption()
            }
            return
        }

        RenewalDuePickedRangeLabel = ChosenOption.TextValue
        HasSelectedRenewalDateRange = true
        RenewalDropdownAttempts = 0
        RenewalScrollStallCount = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalDueStep()
        }
    }

    private fun StartRenewalDueRangeOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            RenewalRangeOcrCount >= RENEWAL_RANGE_OCR_LIMIT
        ) {
            HoldForManualRenewalDueRange(ReasonText = "no OCR available for the Timeline sheet")
            return
        }
        RenewalRangeOcrCount++
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_RANGE_OCR_START",
            MessageText = "attempt=$RenewalRangeOcrCount"
        )
        try {
            CustomerSheetOcr.ReadSheetBoxes(
                ServiceRef = this,
                ExecutorRef = OcrExecutor,
                TopFraction = RENEWAL_RANGE_OCR_TOP_FRACTION
            ) { OutcomeVal ->
                MainHandler.post { FinishRenewalDueRangeOcr(OutcomeVal = OutcomeVal) }
            }
        } catch (ExceptionObj: Exception) {
            HoldForManualRenewalDueRange(
                ReasonText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
    }

    private fun FinishRenewalDueRangeOcr(OutcomeVal: CustomerSheetOcr.BoxOutcome) {
        if (!IsCapturing || CurrentMode != CaptureMode.RENEWAL_DUE) return
        if (OutcomeVal is CustomerSheetOcr.BoxOutcome.Failed) {
            DiagnosticWarning(
                EventName = "RENEWAL_DUE_RANGE_OCR_FAILED",
                MessageText = "reason=[${OutcomeVal.Reason}]"
            )
            HoldForManualRenewalDueRange(ReasonText = OutcomeVal.Reason)
            return
        }

        val BoxItems = (OutcomeVal as CustomerSheetOcr.BoxOutcome.Boxes).Items
        val RangeBoxes = BoxItems.mapNotNull { BoxItem ->
            val SpanDays = RenewalDueRange.SpanDays(TextValue = BoxItem.TextValue)
            if (SpanDays == null) null else BoxItem to SpanDays
        }
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_RANGE_OCR_READ",
            MessageText = "lines=${BoxItems.size} ranges=" +
                    RangeBoxes.joinToString(separator = ",") { Entry -> Entry.first.TextValue }
                        .ifEmpty { "none" }
        )
        if (RangeBoxes.isEmpty()) {
            HoldForManualRenewalDueRange(
                ReasonText = "the Timeline sheet had no readable range labels"
            )
            return
        }

        val TargetSpan = RenewalDueRange.ChooseSpanDays(
            AvailableSpans = RangeBoxes.map { PairRef -> PairRef.second },
            TargetDays = RenewalDueRangeDays
        )
        val ChosenBox = RangeBoxes
            .filter { PairRef -> TargetSpan == null || PairRef.second == TargetSpan }
            .minByOrNull { PairRef -> PairRef.first.Bounds.centerY() }
            ?: RangeBoxes.first()

        val TapY = ChosenBox.first.Bounds.centerY().toFloat()
        val ScreenHeight = resources.displayMetrics.heightPixels
        if (TapY <= ScreenHeight * RENEWAL_RANGE_OCR_TOP_FRACTION) {
            HoldForManualRenewalDueRange(ReasonText = "the OCR match was outside the sheet area")
            return
        }

        val TapAccepted = PerformTapGesture(
            XPos = ChosenBox.first.Bounds.centerX().toFloat(),
            YPos = TapY
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_RANGE_OCR_PICK",
            MessageText = "picked=[${ChosenBox.first.TextValue}] span=${ChosenBox.second} " +
                    "wanted=$RenewalDueRangeDays target=$TargetSpan accepted=$TapAccepted"
        )
        if (!TapAccepted) {
            HoldForManualRenewalDueRange(ReasonText = "the Timeline tap was refused")
            return
        }

        RenewalDuePickedRangeLabel = ChosenBox.first.TextValue
        HasSelectedRenewalDateRange = true
        RenewalDropdownAttempts = 0
        RenewalScrollStallCount = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalDueStep()
        }
    }

    private fun HoldForManualRenewalDueRange(ReasonText: String) {
        IsRenewalRangeSheetHidden = true
        RenewalManualWaitCount = 0
        RenewalRangeBaselineLabel = CurrentRenewalDueRangeLabel()
        DiagnosticWarning(
            EventName = "RENEWAL_DUE_RANGE_MANUAL",
            MessageText = "reason=[$ReasonText] showing=[" +
                    RenewalRangeBaselineLabel.ifEmpty { "unknown" } + "]; waiting for you to pick one"
        )
        ShowServiceToast(
            MessageText = "Pick the Timeline range yourself — capture continues after that",
            KindVal = AppToast.Kind.Warning
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            WaitForManualRenewalDueRange()
        }
    }

    private fun WaitForManualRenewalDueRange() {
        val CurrentLabel = CurrentRenewalDueRangeLabel()
        val LabelChanged = CurrentLabel.isNotEmpty() &&
                !CurrentLabel.equals(RenewalRangeBaselineLabel, ignoreCase = true)
        val ListReady = RenewalTotalPages > 0 || RenewalCurrentPage > 0

        if (LabelChanged || (ListReady && RenewalManualWaitCount >= 2)) {
            DiagnosticInfo(
                EventName = "RENEWAL_DUE_RANGE_MANUAL_DONE",
                MessageText = "showing=[$CurrentLabel] waits=$RenewalManualWaitCount"
            )
            RenewalDuePickedRangeLabel = CurrentLabel
            HasSelectedRenewalDateRange = true
            RenewalDropdownAttempts = 0
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalDueStep()
            }
            return
        }

        RenewalManualWaitCount++
        if (RenewalManualWaitCount > RENEWAL_MANUAL_RANGE_WAIT_LIMIT) {
            DiagnosticWarning(
                EventName = "RENEWAL_DUE_RANGE_MANUAL_TIMEOUT",
                MessageText = "Nobody picked a range; continuing with whatever is showing"
            )
            HasSelectedRenewalDateRange = true
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalDueStep()
            }
            return
        }

        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            WaitForManualRenewalDueRange()
        }
    }

    private fun HasRenewalDueRangeDrifted(): Boolean {
        if (RenewalDuePickedRangeLabel.isEmpty()) return false
        val WantedSpan = RenewalDueRange.SpanDays(TextValue = RenewalDuePickedRangeLabel)
            ?: return false
        val ShowingLabel = LatestRenewalVisibleNodes.firstOrNull { NodeText ->
            RenewalDueRange.IsRangeLabel(TextValue = NodeText)
        } ?: return false
        val ShowingSpan = RenewalDueRange.SpanDays(TextValue = ShowingLabel) ?: return false
        if (ShowingSpan == WantedSpan) return false

        DiagnosticInfo(
            EventName = "RENEWAL_DUE_RANGE_LOST",
            MessageText = "picked=[$RenewalDuePickedRangeLabel] showing=[$ShowingLabel]; " +
                    "re-selecting before carrying on"
        )
        HasSelectedRenewalDateRange = false
        RenewalDropdownAttempts = 0
        RenewalRangeOcrCount = 0
        RenewalScrollStallCount = 0
        RenewalDueTopResetDone = false
        RenewalDueSeekScrolls = 0
        return true
    }

    private fun WalkRenewalDueCustomerList() {
        val VisibleNodes = LatestRenewalVisibleNodes
        RenewalDueActiveCustomer = ""

        val PendingGroup = RenewalDueParser
            .ReadCustomerGroups(Nodes = VisibleNodes)
            .firstOrNull { GroupItem ->
                GroupItem.PolicyCount > 1 &&
                        GroupItem.HasViewAll &&
                        !VisitedRenewalDueCustomers.contains(
                            NormaliseCustomerKey(NameText = GroupItem.HolderName)
                        )
            }

        if (PendingGroup != null) {
            OpenRenewalDueCustomer(GroupRef = PendingGroup)
            return
        }

        val BeforeScrollMark = RenewalDueScreenMark()
        val ScrollAccepted = ScrollRenewalDueList()
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_LIST_SCROLL",
            MessageText = "page=$RenewalCurrentPage/$RenewalTotalPages accepted=$ScrollAccepted " +
                    "captured=${CapturedRenewalDueMap.size} " +
                    "visited=${VisitedRenewalDueCustomers.size}"
        )

        ScheduleRenewalAction(DelayMs = RENEWAL_DUE_SCROLL_SETTLE_MS) {
            val ListMoved = ScrollAccepted && RenewalDueScreenMark() != BeforeScrollMark
            if (ListMoved) RenewalScrollStallCount = 0 else RenewalScrollStallCount++
            if (RenewalScrollStallCount >= RENEWAL_SCROLL_STALL_LIMIT) {
                BeginNextRenewalDuePage()
            } else {
                ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                    RunRenewalDueStep()
                }
            }
        }
    }

    private fun OpenRenewalDueCustomer(GroupRef: RenewalDueParser.CustomerGroup) {
        if (CapturedRenewalDueMap.size > RENEWAL_DUE_VIEW_ALL_LIMIT) {
            DiagnosticWarning(
                EventName = "RENEWAL_DUE_CUSTOMER_LIMIT",
                MessageText = "captured=${CapturedRenewalDueMap.size}; stopping the customer walk"
            )
            CompleteRenewalAutomation()
            return
        }

        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                RunRenewalDueStep()
            }
            return
        }

        val TapOutcome = try {
            TapCustomerViewAll(RootNode = RootNode, HolderName = GroupRef.HolderName)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        val CustomerKey = NormaliseCustomerKey(NameText = GroupRef.HolderName)

        // The group list comes from the whole node tree, but only what is painted
        // can be tapped, so a customer further down the page has to be scrolled to
        // first. That is not a refusal and must not spend a tap attempt.
        if (TapOutcome == ViewAllOutcome.OFF_SCREEN) {
            SeekRenewalDueCustomer(GroupRef = GroupRef, CustomerKey = CustomerKey)
            return
        }

        DiagnosticInfo(
            EventName = "RENEWAL_DUE_CUSTOMER_ENTER",
            MessageText = "customer=[${GroupRef.HolderName}] policies=${GroupRef.PolicyCount} " +
                    "outcome=$TapOutcome seekScrolls=$RenewalDueSeekScrolls " +
                    "visited=${VisitedRenewalDueCustomers.size}"
        )

        if (TapOutcome != ViewAllOutcome.TAPPED) {
            val AttemptCount = (RenewalDueTapAttempts[CustomerKey] ?: 0) + 1
            RenewalDueTapAttempts[CustomerKey] = AttemptCount
            if (AttemptCount >= RENEWAL_DUE_TAP_RETRY_LIMIT) {
                DiagnosticWarning(
                    EventName = "RENEWAL_DUE_CUSTOMER_GIVEN_UP",
                    MessageText = "customer=[${GroupRef.HolderName}] policies=${GroupRef.PolicyCount} " +
                            "refused $AttemptCount times; moving on without their extra policies"
                )
                VisitedRenewalDueCustomers.add(CustomerKey)
                RenewalDueSeekScrolls = 0
            }
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                RunRenewalDueStep()
            }
            return
        }

        RenewalDueTapAttempts.remove(CustomerKey)
        RenewalDueActiveCustomer = GroupRef.HolderName
        VisitedRenewalDueCustomers.add(CustomerKey)
        RenewalScrollStallCount = 0
        RenewalDueSeekScrolls = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            RunRenewalDueStep()
        }
    }

    private fun RenewalDueScreenMark(): Int {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return 0
        return try {
            val NodeEntries = CollectVisibleTextNodes(RootNode = RootNode)
            var MarkValue = NodeEntries.size
            for (NodeEntry in NodeEntries) {
                MarkValue = MarkValue * 31 + NodeEntry.first.hashCode()
                MarkValue = MarkValue * 31 + NodeEntry.second.top
            }
            MarkValue
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun ScrollRenewalDueList(
        ForwardVal: Boolean = true,
        SmallStep: Boolean = false
    ): Boolean {
        CollapseBubbleForGesture()
        val DisplayMetricsObj = resources.displayMetrics
        val StartRatio = if (SmallStep) {
            RENEWAL_DUE_SEEK_START_RATIO
        } else {
            RENEWAL_DUE_SCROLL_START_RATIO
        }
        val EndRatio = if (SmallStep) {
            RENEWAL_DUE_SEEK_END_RATIO
        } else {
            RENEWAL_DUE_SCROLL_END_RATIO
        }

        val StartXVal = DisplayMetricsObj.widthPixels * 0.5f
        val LowerYVal = DisplayMetricsObj.heightPixels * StartRatio
        val UpperYVal = DisplayMetricsObj.heightPixels * EndRatio
        val StartYVal = if (ForwardVal) LowerYVal else UpperYVal
        val EndYVal = if (ForwardVal) UpperYVal else LowerYVal

        val ScrollPath = Path().apply {
            moveTo(StartXVal, StartYVal)
            lineTo(StartXVal, EndYVal)
        }
        val GestureObj = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    ScrollPath,
                    0,
                    Paced(BaseMs = RENEWAL_DUE_SCROLL_DURATION_MS)
                )
            )
            .build()

        return try {
            dispatchGesture(GestureObj, null, null)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "RENEWAL_DUE_SCROLL_ERROR",
                MessageText = "direction=${if (ForwardVal) "down" else "up"} " +
                        "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            false
        }
    }

    private fun SeekRenewalDueCustomer(
        GroupRef: RenewalDueParser.CustomerGroup,
        CustomerKey: String
    ) {
        RenewalDueSeekScrolls++
        if (RenewalDueSeekScrolls > RENEWAL_DUE_SEEK_SCROLL_LIMIT) {
            DiagnosticWarning(
                EventName = "RENEWAL_DUE_CUSTOMER_GIVEN_UP",
                MessageText = "customer=[${GroupRef.HolderName}] policies=${GroupRef.PolicyCount} " +
                        "never came on screen in $RenewalDueSeekScrolls scrolls; moving on"
            )
            VisitedRenewalDueCustomers.add(CustomerKey)
            RenewalDueSeekScrolls = 0
            ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                RunRenewalDueStep()
            }
            return
        }

        val BeforeScrollMark = RenewalDueScreenMark()
        val ScrollAccepted = ScrollRenewalDueList(SmallStep = true)
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_CUSTOMER_SEEK",
            MessageText = "customer=[${GroupRef.HolderName}] scroll=$RenewalDueSeekScrolls " +
                    "accepted=$ScrollAccepted captured=${CapturedRenewalDueMap.size}"
        )

        ScheduleRenewalAction(DelayMs = RENEWAL_DUE_SCROLL_SETTLE_MS) {
            val ListMoved = RenewalDueScreenMark() != BeforeScrollMark
            if (!ListMoved) {
                DiagnosticWarning(
                    EventName = "RENEWAL_DUE_CUSTOMER_GIVEN_UP",
                    MessageText = "customer=[${GroupRef.HolderName}] is past the end of the " +
                            "list and never came on screen; moving on"
                )
                VisitedRenewalDueCustomers.add(CustomerKey)
                RenewalDueSeekScrolls = 0
            }
            RunRenewalDueStep()
        }
    }

    private enum class ViewAllOutcome {
        TAPPED,
        OFF_SCREEN,
        REFUSED
    }

    private fun TapCustomerViewAll(
        RootNode: AccessibilityNodeInfo,
        HolderName: String
    ): ViewAllOutcome {
        val TextNodes = CollectVisibleTextNodes(RootNode = RootNode)
        val NameBounds = TextNodes.firstOrNull { NodeEntry ->
            NodeEntry.first.trim().equals(HolderName, ignoreCase = true)
        }?.second
        if (NameBounds == null) {
            DiagnosticInfo(
                EventName = "RENEWAL_DUE_VIEW_ALL_OFFSCREEN",
                MessageText = "customer=[$HolderName] is in the tree but not among the " +
                        "${TextNodes.size} on-screen nodes; scrolling to them"
            )
            return ViewAllOutcome.OFF_SCREEN
        }

        val ViewAllCandidates = TextNodes.filter { NodeEntry ->
            NodeEntry.first.trim().replace(Regex("\\s+"), " ")
                .equals("View All", ignoreCase = true)
        }
        val ViewAllEntry = ViewAllCandidates
            .filter { NodeEntry -> NodeEntry.second.centerY() > NameBounds.centerY() }
            .minByOrNull { NodeEntry -> NodeEntry.second.centerY() }
        if (ViewAllEntry == null) {
            DiagnosticInfo(
                EventName = "RENEWAL_DUE_VIEW_ALL_OFFSCREEN",
                MessageText = "customer=[$HolderName] nameY=${NameBounds.centerY()} " +
                        "candidates=${ViewAllCandidates.size} none of them below the name; " +
                        "scrolling to bring the row fully on screen"
            )
            return ViewAllOutcome.OFF_SCREEN
        }

        val TapAccepted = PerformTapGesture(
            XPos = ViewAllEntry.second.centerX().toFloat(),
            YPos = ViewAllEntry.second.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_VIEW_ALL_TAP",
            MessageText = "customer=[$HolderName] nameY=${NameBounds.centerY()} " +
                    "bounds=${ViewAllEntry.second} accepted=$TapAccepted"
        )
        return if (TapAccepted) ViewAllOutcome.TAPPED else ViewAllOutcome.REFUSED
    }

    private fun WalkAllRenewalsDuePage() {
        val BeforeScrollMark = RenewalDueScreenMark()
        val ScrollAccepted = ScrollRenewalDueList()
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_CUSTOMER_SCROLL",
            MessageText = "customer=[$RenewalDueActiveCustomer] " +
                    "page=$RenewalCurrentPage/$RenewalTotalPages accepted=$ScrollAccepted " +
                    "captured=${CapturedRenewalDueMap.size}"
        )

        ScheduleRenewalAction(DelayMs = RENEWAL_DUE_SCROLL_SETTLE_MS) {
            val ListMoved = ScrollAccepted && RenewalDueScreenMark() != BeforeScrollMark
            if (ListMoved) RenewalScrollStallCount = 0 else RenewalScrollStallCount++
            if (RenewalScrollStallCount >= RENEWAL_SCROLL_STALL_LIMIT) {
                LeaveRenewalDueCustomer()
            } else {
                ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                    RunRenewalDueStep()
                }
            }
        }
    }

    private fun LeaveRenewalDueCustomer() {
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_CUSTOMER_RETURN",
            MessageText = "customer=[$RenewalDueActiveCustomer] " +
                    "captured=${CapturedRenewalDueMap.size} " +
                    "visited=${VisitedRenewalDueCustomers.size}"
        )
        RenewalDueActiveCustomer = ""
        RenewalScrollStallCount = 0
        RenewalDueSeekScrolls = 0
        performGlobalAction(GLOBAL_ACTION_BACK)
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            ResumeRenewalDueCustomerList()
        }
    }

    private fun ResumeRenewalDueCustomerList() {
        val VisibleNodes = LatestRenewalVisibleNodes
        if (IsAllRenewalsDueScreen(VisibleNodes = VisibleNodes)) {
            RenewalDueReturnStalls++
            if (RenewalDueReturnStalls > RENEWAL_DUE_RETURN_STALL_LIMIT) {
                FailRenewalAutomation("Could not get back to the Renewals Due customer list")
                return
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                ResumeRenewalDueCustomerList()
            }
            return
        }

        RenewalDueReturnStalls = 0
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_CUSTOMER_RESUME",
            MessageText = "page=$RenewalCurrentPage/$RenewalTotalPages " +
                    "visited=${VisitedRenewalDueCustomers.size}"
        )
        ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
            RunRenewalDueStep()
        }
    }

    private fun BeginNextRenewalDuePage() {
        RenewalScrollStallCount = 0
        DiagnosticInfo(
            EventName = "RENEWAL_DUE_PAGE_END",
            MessageText = "page=$RenewalCurrentPage total=$RenewalTotalPages " +
                    "selectorVisible=$IsRenewalPageSelectorVisible " +
                    "captured=${CapturedRenewalDueMap.size}"
        )

        val PendingOnThisPage = RenewalDueParser
            .ReadCustomerGroups(Nodes = LatestRenewalVisibleNodes)
            .any { GroupItem ->
                GroupItem.PolicyCount > 1 &&
                        !VisitedRenewalDueCustomers.contains(
                            NormaliseCustomerKey(NameText = GroupItem.HolderName)
                        )
            }
        if (PendingOnThisPage && !RenewalDueTopResetDone) {
            RenewalDueTopResetDone = true
            DiagnosticInfo(
                EventName = "RENEWAL_DUE_LIST_REWIND",
                MessageText = "Unvisited customers are still on this page; scrolling back to the top"
            )
            ScrollRenewalDueList(ForwardVal = false)
            ScheduleRenewalAction(DelayMs = RENEWAL_DUE_SCROLL_SETTLE_MS) {
                RunRenewalDueStep()
            }
            return
        }
        RenewalDueTopResetDone = false

        if (RenewalCurrentPage > 0 &&
            RenewalTotalPages > 0 &&
            RenewalCurrentPage >= RenewalTotalPages
        ) {
            CompleteRenewalAutomation()
            return
        }

        if (RenewalTotalPages <= 0 &&
            IsRenewalHistoryEmpty(VisibleNodes = LatestRenewalVisibleNodes)
        ) {
            DiagnosticInfo(
                EventName = "RENEWAL_DUE_LIST_EMPTY",
                MessageText = "No renewals are due for the selected range"
            )
            CompleteRenewalAutomation()
            return
        }

        RenewalReturnToTopCount = 0
        RenewalPageRetryCount = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
            OpenRenewalDuePageSelector()
        }
    }

    private fun OpenRenewalDuePageSelector() {
        val TargetPage = RenewalCurrentPage + 1
        if (RenewalTotalPages in 1..<TargetPage) {
            CompleteRenewalAutomation()
            return
        }
        RenewalExpectedPage = TargetPage

        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            RetryRenewalDuePageNavigation("Renewals Due page selector root is unavailable")
            return
        }

        val SelectorClicked: Boolean
        val SelectorAdvanced: Boolean
        try {
            SelectorClicked = ClickPolicyPageSelector(
                RootNode = RootNode,
                CurrentPage = RenewalCurrentPage
            )
            SelectorAdvanced = !SelectorClicked && AdvancePolicyPageSelector(
                RootNode = RootNode,
                CurrentPage = RenewalCurrentPage
            )
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (SelectorClicked) {
            ScheduleRenewalAction(DelayMs = POLICY_PAGE_SELECTOR_DELAY_MS) {
                SelectNextRenewalDuePage()
            }
        } else if (SelectorAdvanced) {
            WaitForRenewalDuePageLoad()
        } else {
            RetryRenewalDuePageNavigation("Could not open the Renewals Due page selector")
        }
    }

    private fun SelectNextRenewalDuePage() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            RetryRenewalDuePageNavigation("Renewals Due page options are unavailable")
            return
        }

        val OptionClicked = try {
            ClickPolicyPageOption(RootNode = RootNode, PageNumber = RenewalExpectedPage)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (!OptionClicked) {
            RetryRenewalDuePageNavigation("Could not select Renewals Due page $RenewalExpectedPage")
            return
        }

        DiagnosticInfo(
            EventName = "RENEWAL_DUE_PAGE_SELECTED",
            MessageText = "selected=$RenewalExpectedPage; waiting for the list to load"
        )
        WaitForRenewalDuePageLoad()
    }

    private fun WaitForRenewalDuePageLoad() {
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            if (!IsRenewalPageSelectorVisible || RenewalCurrentPage != RenewalExpectedPage) {
                RenewalPageRetryCount++
                DiagnosticInfo(
                    EventName = "RENEWAL_DUE_PAGE_WAIT",
                    MessageText = "expected=$RenewalExpectedPage actual=$RenewalCurrentPage " +
                            "selectorVisible=$IsRenewalPageSelectorVisible " +
                            "attempt=$RenewalPageRetryCount"
                )
                if (RenewalPageRetryCount >= RENEWAL_PAGE_RETRY_LIMIT) {
                    DiagnosticWarning(
                        EventName = "RENEWAL_DUE_PAGE_SKIPPED",
                        MessageText = "page=$RenewalExpectedPage never loaded; finishing"
                    )
                    CompleteRenewalAutomation()
                } else {
                    WaitForRenewalDuePageLoad()
                }
                return@ScheduleRenewalAction
            }

            RenewalPageRetryCount = 0
            RenewalScrollStallCount = 0
            RenewalAutomationFailureCount = 0
            DiagnosticInfo(
                EventName = "RENEWAL_DUE_PAGE_LOADED",
                MessageText = "page=$RenewalCurrentPage total=$RenewalTotalPages " +
                        "captured=${CapturedRenewalDueMap.size}"
            )
            ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
                RunRenewalDueStep()
            }
        }
    }

    private fun RetryRenewalDuePageNavigation(ReasonText: String) {
        RenewalPageRetryCount++
        Log.w(LOG_TAG, "$ReasonText (attempt $RenewalPageRetryCount)")
        DiagnosticWarning(
            EventName = "RENEWAL_DUE_NAVIGATION_RETRY",
            MessageText = "$ReasonText; attempt=$RenewalPageRetryCount " +
                    "page=$RenewalCurrentPage expected=$RenewalExpectedPage"
        )
        if (RenewalPageRetryCount >= RENEWAL_PAGE_RETRY_LIMIT) {
            CompleteRenewalAutomation()
            return
        }
        ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
            OpenRenewalDuePageSelector()
        }
    }

    private fun StopRenewalAutomation(ResetStateVal: Boolean) {
        RenewalAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        RenewalAutomationRunnable = null
        IsRenewalAutomationRunning = false

        if (ResetStateVal) {
            IsRenewalAutomationComplete = false
            HasOpenedRenewalHistoryList = false
            HasSelectedRenewalDateRange = false
            RenewalDashboardScrollCount = 0
            RenewalDropdownAttempts = 0
            RenewalDropdownScrollPasses = 0
            RenewalDropdownBaselineTexts = emptySet()
            RenewalDropdownSeenOptions = emptySet()
            RenewalChipBounds = null
            RenewalUnknownScreenCount = 0
            RenewalCurrentPage = 0
            RenewalTotalPages = 0
            RenewalExpectedPage = 0
            RenewalSkipTargetPage = 0
            RenewalSkipWaitCount = 0
            RenewalRangeOcrCount = 0
            RenewalKnownTotalPages = 0
            RenewalPickedRangeLabel = ""
            RenewalLastRelistTarget = 0
            RenewalRelistStalls = 0
            RenewalKnownBadPages.clear()
            RenewalKnownBadSpanDays = 0
            IsRenewalRangeSheetHidden = false
            ClearBlankScreenState()
            RenewalManualWaitCount = 0
            RenewalRangeBaselineLabel = ""
            RenewalRelistCount = 0
            RenewalSkippedPages.clear()
            RenewalPageRetryCount = 0
            RenewalReturnToTopCount = 0
            RenewalScrollStallCount = 0
            LatestRenewalVisibleSignature = 0
            LatestRenewalVisibleNodes = emptyList()
            IsRenewalPageSelectorVisible = false
            HasOpenedRenewalDueList = false
            HasSwitchedToCustomerView = false
            RenewalDueToggleAttempts = 0
            RenewalDuePickedRangeLabel = ""
            RenewalDueActiveCustomer = ""
            RenewalDueReturnStalls = 0
            RenewalDueTopResetDone = false
            RenewalDueSeekScrolls = 0
            VisitedRenewalDueCustomers.clear()
            RenewalDueTapAttempts.clear()
            RenewalAutomationRetryAfter = 0L
            RenewalAutomationFailureCount = 0
        }
    }

    private fun IsRenewalFamilyMode(): Boolean {
        return CurrentMode == CaptureMode.FUP || CurrentMode == CaptureMode.RENEWAL_DUE
    }

    private fun ScheduleRenewalAction(DelayMs: Long, ActionRef: () -> Unit) {
        RenewalAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }

        var RenderWaitCount = 0
        lateinit var WrappedRunnable: Runnable
        WrappedRunnable = Runnable {
            if (!IsRenewalAutomationRunning ||
                !IsCapturing ||
                !IsRenewalFamilyMode()
            ) {
                return@Runnable
            }
            if (IsPaused) {
                RenewalAutomationRunnable = WrappedRunnable
                MainHandler.postDelayed(WrappedRunnable, TICK_INTERVAL_MS)
                return@Runnable
            }
            if (!IsScreenSettled(WaitCount = RenderWaitCount)) {
                RenderWaitCount++
                RenewalAutomationRunnable = WrappedRunnable
                MainHandler.postDelayed(WrappedRunnable, Paced(BaseMs = SCREEN_READY_RECHECK_MS))
                return@Runnable
            }

            RenewalAutomationRunnable = null
            ActionRef()
        }
        RenewalAutomationRunnable = WrappedRunnable
        MainHandler.postDelayed(WrappedRunnable, Paced(BaseMs = DelayMs))
    }

    private fun StartAutoScroll(ScreenSignature: Int) {
        if (IsAutoScrolling || CurrentMode == CaptureMode.POLICY) return
        if (CompletedAutoScrollScreenSignature == ScreenSignature) return

        IsAutoScrolling = true
        AutoScrollStallCount = 0
        LastAutoScrollNodeCount = CapturedNodes.size
        Log.d(LOG_TAG, "Auto-scroll armed for ${CurrentMode.name}")

        val RunnableObj = object : Runnable {
            override fun run() {
                if (!IsAutoScrolling || !IsCapturing || IsPaused) {
                    StopAutoScroll()
                    return
                }

                val ExpectedPackage = ExpectedTargetPackage()
                CaptureActiveWindow(ExpectedPackage = ExpectedPackage)
                if (!IsAutoScrolling || !IsCapturing || IsPaused) return
                val ScrollAccepted = ScrollActiveWindow(ExpectedPackage = ExpectedPackage)
                if (!ScrollAccepted) {
                    Log.d(LOG_TAG, "Auto-scroll stopped because the list cannot scroll further")
                    StopAutoScroll(CompletedVal = true)
                    return
                }

                MainHandler.postDelayed({
                    if (!IsAutoScrolling || !IsCapturing || IsPaused) return@postDelayed

                    CaptureActiveWindow(ExpectedPackage = ExpectedPackage)
                    val CurrentNodeCount = CapturedNodes.size
                    if (CurrentNodeCount <= LastAutoScrollNodeCount) {
                        AutoScrollStallCount++
                    } else {
                        AutoScrollStallCount = 0
                    }
                    LastAutoScrollNodeCount = CurrentNodeCount

                    if (AutoScrollStallCount >= AUTO_SCROLL_STALL_LIMIT) {
                        Log.d(LOG_TAG, "Auto-scroll stopped after reaching stable list content")
                        StopAutoScroll(CompletedVal = true)
                    } else {
                        AutoScrollRunnable?.let { NextRunnable ->
                            MainHandler.postDelayed(NextRunnable, TICK_INTERVAL_MS)
                        }
                    }
                }, AUTO_SCROLL_SETTLE_MS)
            }
        }
        AutoScrollRunnable = RunnableObj
        MainHandler.postDelayed(RunnableObj, Paced(BaseMs = AUTO_SCROLL_START_DELAY_MS))
    }

    private fun StopAutoScroll(CompletedVal: Boolean = false) {
        AutoScrollRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        AutoScrollRunnable = null
        IsAutoScrolling = false
        AutoScrollStallCount = 0
        LastAutoScrollNodeCount = 0
        if (CompletedVal) {
            CompletedAutoScrollScreenSignature = CurrentAutoScrollScreenSignature
        }
    }

    private fun ScrollActiveWindow(ExpectedPackage: String, ForwardVal: Boolean = true): Boolean {
        val RootNode = FindReadableRoot(ExpectedPackage = ExpectedPackage) ?: return false
        val NodeScrollAccepted = try {
            PerformScrollOnNode(TargetNode = RootNode, ForwardVal = ForwardVal)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
        return NodeScrollAccepted || PerformAutoScrollGesture(ForwardVal = ForwardVal)
    }

    private fun PerformScrollOnNode(
        TargetNode: AccessibilityNodeInfo,
        ForwardVal: Boolean
    ): Boolean {
        try {
            val ScrollAction = if (ForwardVal) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (TargetNode.isScrollable && TargetNode.performAction(ScrollAction)) {
                return true
            }
            for (ChildIdx in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIdx) ?: continue
                try {
                    if (PerformScrollOnNode(
                            TargetNode = ChildNode,
                            ForwardVal = ForwardVal
                        )
                    ) return true
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Scrollable node became stale", ExceptionObj)
        }
        return false
    }

    fun PerformAutoScrollGesture(ForwardVal: Boolean = true): Boolean {
        val DisplayMetricsObj = resources.displayMetrics
        val StartXVal = DisplayMetricsObj.widthPixels / 2f
        val StartYVal = DisplayMetricsObj.heightPixels * if (ForwardVal) 0.75f else 0.25f
        val EndYVal = DisplayMetricsObj.heightPixels * if (ForwardVal) 0.25f else 0.75f

        val ScrollPath = Path().apply {
            moveTo(StartXVal, StartYVal)
            lineTo(StartXVal, EndYVal)
        }

        val GestureObj = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(ScrollPath, 0, 400))
            .build()

        return try {
            dispatchGesture(GestureObj, null, null)
        } catch (_: Exception) {
            false
        }
    }

    private fun PerformSmoothPolicyScrollGesture(ForwardVal: Boolean): Boolean {
        CollapseBubbleForGesture()
        val DisplayMetricsObj = resources.displayMetrics
        val StartXVal = DisplayMetricsObj.widthPixels * 0.5f
        val UpperYVal = DisplayMetricsObj.heightPixels * POLICY_SMOOTH_SCROLL_END_RATIO
        val LowerYVal = DisplayMetricsObj.heightPixels * POLICY_SMOOTH_SCROLL_START_RATIO
        val StartYVal = if (ForwardVal) LowerYVal else UpperYVal
        val EndYVal = if (ForwardVal) UpperYVal else LowerYVal

        val ScrollPath = Path().apply {
            moveTo(StartXVal, StartYVal)
            lineTo(StartXVal, EndYVal)
        }
        val GestureObj = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    ScrollPath,
                    0,
                    POLICY_SMOOTH_SCROLL_DURATION_MS
                )
            )
            .build()

        return try {
            dispatchGesture(GestureObj, null, null)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_SMOOTH_SCROLL_ERROR",
                MessageText = "direction=${if (ForwardVal) "down" else "up"} " +
                        "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            false
        }
    }

    private fun PerformPolicyRevealNudge(ForwardVal: Boolean = true): Boolean {
        CollapseBubbleForGesture()
        val DisplayMetricsObj = resources.displayMetrics
        val LowerYVal = DisplayMetricsObj.heightPixels * 0.78f
        val UpperYVal = DisplayMetricsObj.heightPixels * 0.58f
        val ScrollPath = Path().apply {
            moveTo(
                DisplayMetricsObj.widthPixels * 0.5f,
                if (ForwardVal) LowerYVal else UpperYVal
            )
            lineTo(
                DisplayMetricsObj.widthPixels * 0.5f,
                if (ForwardVal) UpperYVal else LowerYVal
            )
        }
        val GestureObj = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    ScrollPath,
                    0,
                    POLICY_REVEAL_NUDGE_DURATION_MS
                )
            )
            .build()
        return try {
            dispatchGesture(GestureObj, null, null)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_REVEAL_NUDGE_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            false
        }
    }


    private fun PerformPolicyScroll(
        ForwardVal: Boolean,
        PreferAccessibilityAction: Boolean
    ): Boolean {
        if (!PreferAccessibilityAction) {
            return PerformSmoothPolicyScrollGesture(ForwardVal = ForwardVal)
        }

        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return PerformSmoothPolicyScrollGesture(ForwardVal = ForwardVal)
        val AccessibilityScrollAccepted = try {
            PerformScrollOnNode(TargetNode = RootNode, ForwardVal = ForwardVal)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
        return AccessibilityScrollAccepted ||
                PerformSmoothPolicyScrollGesture(ForwardVal = ForwardVal)
    }

    private fun PerformTapGesture(XPos: Float, YPos: Float): Boolean {
        ShieldBubbleFromGesture(XPos = XPos, YPos = YPos)
        val TapPath = Path().apply { moveTo(XPos, YPos) }
        val GestureObj = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(TapPath, 0, 120))
            .build()
        return try {
            dispatchGesture(GestureObj, null, null)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "TAP_GESTURE_ERROR",
                MessageText = "x=$XPos y=$YPos ${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
            false
        }
    }

    private fun TryAutoExpandPolicySections(
        VisibleNodes: List<String>
    ) {
        val IsDetailedPolicyScreen = VisibleNodes.any { NodeText ->
            NodeText.contains("Detailed Policy View", ignoreCase = true) ||
                    NodeText.equals("Policy Details", ignoreCase = true)
        }
        if (!IsDetailedPolicyScreen) {
            HasExpandedCurrentPolicyScreen = false
            return
        }
        if (HasExpandedCurrentPolicyScreen) return
        val HasExpandableLabel =
            listOf("Policy Details", "Commissions", "Key Dates").any { LabelText ->
                VisibleNodes.any { NodeText -> NodeText.equals(LabelText, ignoreCase = true) }
            }
        if (!HasExpandableLabel) return

        HasExpandedCurrentPolicyScreen = true
        ScheduleExpandSection(LabelText = "Policy Details", DelayMs = 100L)
        ScheduleExpandSection(LabelText = "Commissions", DelayMs = 1000L)
        ScheduleExpandSection(LabelText = "Key Dates", DelayMs = 1900L)
    }

    private fun ScheduleExpandSection(LabelText: String, DelayMs: Long) {
        MainHandler.postDelayed({
            if (!IsCapturing || IsPaused || CurrentMode != CaptureMode.POLICY) return@postDelayed

            val FreshRoot =
                FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
                    ?: return@postDelayed
            val Expanded = try {
                ExpandSection(RootNode = FreshRoot, LabelText = LabelText)
            } finally {
                RecycleNode(NodeRef = FreshRoot)
            }
            DiagnosticInfo(
                EventName = "POLICY_SECTION_RESULT",
                MessageText = "policy=$PolicyDetailCurrentPolicyNumber section=[$LabelText] " +
                        "expanded=$Expanded"
            )
            if (!Expanded) {
                ScheduleSectionExpansionAttempt(
                    LabelText = LabelText,
                    DelayMs = POLICY_SECTION_SCROLL_SETTLE_MS,
                    AttemptCount = 0
                )
            }
        }, DelayMs)
    }


    private fun ScheduleSectionExpansionAttempt(
        LabelText: String,
        DelayMs: Long,
        AttemptCount: Int
    ) {
        if (AttemptCount == 0 && !PolicySectionsInFlight.add(LabelText)) {
            DiagnosticInfo(
                EventName = "POLICY_SECTION_ALREADY_SEEKING",
                MessageText = "policy=$PolicyDetailCurrentPolicyNumber section=[$LabelText]"
            )
            return
        }

        MainHandler.postDelayed({
            if (!IsCapturing || IsPaused || !IsPolicyDetailScreenActive) {
                PolicySectionsInFlight.remove(LabelText)
                return@postDelayed
            }
            if (AttemptCount >= POLICY_SECTION_ATTEMPT_LIMIT) {
                PolicySectionsInFlight.remove(LabelText)
                DiagnosticWarning(
                    EventName = "POLICY_SECTION_ATTEMPTS_EXHAUSTED",
                    MessageText = "policy=$PolicyDetailCurrentPolicyNumber section=[$LabelText] " +
                            "attempts=$AttemptCount"
                )
                return@postDelayed
            }

            val FreshRoot = FindReadableRoot(
                ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
            ) ?: run {
                PolicySectionsInFlight.remove(LabelText)
                return@postDelayed
            }
            val LabelBounds = try {
                FindSectionLabelBounds(TargetNode = FreshRoot, LabelText = LabelText)
            } finally {
                RecycleNode(NodeRef = FreshRoot)
            }

            val ScreenHeight = resources.displayMetrics.heightPixels
            val TopLimit = ScreenHeight * POLICY_SECTION_VIEWPORT_TOP_RATIO
            val BottomLimit = ScreenHeight * POLICY_SECTION_VIEWPORT_BOTTOM_RATIO

            // Not in the tree at all means it is further down the page.
            if (LabelBounds == null) {
                val ScrollAccepted = PerformPolicyScroll(
                    ForwardVal = true,
                    PreferAccessibilityAction = false
                )
                DiagnosticInfo(
                    EventName = "POLICY_SECTION_SEEK",
                    MessageText = "policy=$PolicyDetailCurrentPolicyNumber section=[$LabelText] " +
                            "reason=not-in-tree attempt=$AttemptCount accepted=$ScrollAccepted"
                )
                ScheduleSectionExpansionAttempt(
                    LabelText = LabelText,
                    DelayMs = POLICY_SECTION_SCROLL_SETTLE_MS,
                    AttemptCount = AttemptCount + 1
                )
                return@postDelayed
            }

            val LabelCentreY = LabelBounds.centerY().toFloat()
            if (LabelCentreY !in TopLimit..BottomLimit) {
                val ScrollForward = LabelCentreY > BottomLimit
                val ScrollAccepted = PerformPolicyScroll(
                    ForwardVal = ScrollForward,
                    PreferAccessibilityAction = false
                )
                DiagnosticInfo(
                    EventName = "POLICY_SECTION_SEEK",
                    MessageText = "policy=$PolicyDetailCurrentPolicyNumber section=[$LabelText] " +
                            "reason=out-of-band bounds=$LabelBounds " +
                            "direction=${if (ScrollForward) "down" else "up"} " +
                            "attempt=$AttemptCount accepted=$ScrollAccepted"
                )
                ScheduleSectionExpansionAttempt(
                    LabelText = LabelText,
                    DelayMs = POLICY_SECTION_SCROLL_SETTLE_MS,
                    AttemptCount = AttemptCount + 1
                )
                return@postDelayed
            }

            val TapAccepted = PerformTapGesture(
                XPos = resources.displayMetrics.widthPixels * POLICY_SECTION_CHEVRON_X_RATIO,
                YPos = LabelCentreY
            )
            DiagnosticInfo(
                EventName = "POLICY_SECTION_COORDINATE_RETRY",
                MessageText = "policy=$PolicyDetailCurrentPolicyNumber section=[$LabelText] " +
                        "bounds=$LabelBounds attempt=$AttemptCount accepted=$TapAccepted"
            )
            if (TapAccepted) {
                PolicySectionsInFlight.remove(LabelText)
            } else {
                ScheduleSectionExpansionAttempt(
                    LabelText = LabelText,
                    DelayMs = POLICY_SECTION_SCROLL_SETTLE_MS,
                    AttemptCount = AttemptCount + 1
                )
            }
        }, DelayMs)
    }

    /**
     * On screen is not the same as reachable: the "Pay Premium" bar and the tab
     * strip sit on top of the lower part of the page, so a chevron down there
     * cannot be tapped even though its bounds are technically visible.
     */
    private fun IsSectionHeaderTappable(BoundsObj: Rect): Boolean {
        if (!IsBoundsOnScreen(BoundsObj = BoundsObj)) return false
        val ScreenHeight = resources.displayMetrics.heightPixels
        val CentreY = BoundsObj.centerY().toFloat()
        return CentreY >= ScreenHeight * POLICY_SECTION_VIEWPORT_TOP_RATIO &&
                CentreY <= ScreenHeight * POLICY_SECTION_VIEWPORT_BOTTOM_RATIO
    }

    /**
     * Unlike the on-screen lookup this reports the header wherever it sits, so
     * the caller can decide how to scroll it into reach.
     */
    private fun FindSectionLabelBounds(
        TargetNode: AccessibilityNodeInfo,
        LabelText: String
    ): Rect? {
        try {
            if (NodeTextValue(NodeRef = TargetNode).equals(LabelText, ignoreCase = true)) {
                val NodeBounds = Rect()
                TargetNode.getBoundsInScreen(NodeBounds)
                if (!NodeBounds.isEmpty) return Rect(NodeBounds)
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    val ChildBounds = FindSectionLabelBounds(
                        TargetNode = ChildNode,
                        LabelText = LabelText
                    )
                    if (ChildBounds != null) return ChildBounds
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Policy section label became stale: $LabelText", ExceptionObj)
        }
        return null
    }

    private fun ExpandSection(RootNode: AccessibilityNodeInfo, LabelText: String): Boolean {
        if (ExpandSectionByTraversal(TargetNode = RootNode, LabelText = LabelText)) {
            return true
        }

        val MatchList = try {
            RootNode.findAccessibilityNodeInfosByText(LabelText)
        } catch (_: Exception) {
            emptyList()
        }
        try {
            for (MatchNode in MatchList) {
                var CandidateNode: AccessibilityNodeInfo? = MatchNode
                var OwnsCandidate = false
                while (CandidateNode != null) {
                    if (CandidateNode.isClickable) {
                        val Clicked =
                            CandidateNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (OwnsCandidate) RecycleNode(NodeRef = CandidateNode)
                        if (Clicked) {
                            Log.d(LOG_TAG, "Expanded $LabelText")
                            return true
                        }
                        break
                    }

                    val ParentNode = try {
                        CandidateNode.parent
                    } catch (_: Exception) {
                        null
                    }
                    if (OwnsCandidate) RecycleNode(NodeRef = CandidateNode)
                    CandidateNode = ParentNode
                    OwnsCandidate = true
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Unable to expand $LabelText", ExceptionObj)
        } finally {
            for (MatchNode in MatchList) RecycleNode(NodeRef = MatchNode)
        }
        return false
    }

    private fun ExpandSectionByTraversal(
        TargetNode: AccessibilityNodeInfo,
        LabelText: String
    ): Boolean {
        try {
            val NodeText = NodeTextValue(NodeRef = TargetNode)
            if (NodeText.equals(LabelText, ignoreCase = true)) {
                val NodeBounds = Rect()
                TargetNode.getBoundsInScreen(NodeBounds)
                DiagnosticInfo(
                    EventName = "POLICY_SECTION_CANDIDATE",
                    MessageText = "section=[$LabelText] class=${TargetNode.className} " +
                            "clickable=${TargetNode.isClickable} visible=${TargetNode.isVisibleToUser} " +
                            "bounds=$NodeBounds"
                )
                if (IsSectionHeaderTappable(BoundsObj = NodeBounds)) {
                    if (PerformTapGesture(
                            XPos = resources.displayMetrics.widthPixels *
                                    POLICY_SECTION_CHEVRON_X_RATIO,
                            YPos = NodeBounds.centerY().toFloat()
                        )
                    ) {
                        DiagnosticInfo(
                            EventName = "POLICY_SECTION_EXPANDED",
                            MessageText = "section=[$LabelText] method=coordinate-chevron"
                        )
                        return true
                    }
                    if (ClickNodeOrParent(StartNode = TargetNode)) {
                        DiagnosticInfo(
                            EventName = "POLICY_SECTION_EXPANDED",
                            MessageText = "section=[$LabelText] method=accessibility-fallback"
                        )
                        return true
                    }
                }
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    if (ExpandSectionByTraversal(
                            TargetNode = ChildNode,
                            LabelText = LabelText
                        )
                    ) {
                        return true
                    }
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "POLICY_SECTION_EXPAND_ERROR",
                MessageText = "section=[$LabelText] ${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
        }
        return false
    }


    private fun StartParseThread() {
        StopParseThread()
        val ThreadObj = HandlerThread("CaptureParse").apply { start() }
        ParseThread = ThreadObj
        ParseHandler = Handler(ThreadObj.looper)
    }

    private fun StopParseThread() {
        ParseHandler = null
        ParseThread?.quitSafely()
        ParseThread = null
    }

    private fun RequestIncrementalParse() {
        val SnapshotSize = CapturedNodes.size
        if (SnapshotSize == LastParsedNodeCount || SnapshotSize == 0) return
        LastParsedNodeCount = SnapshotSize

        val NodeSnapshot = CapturedNodes.toList()
        val ModeSnapshot = CurrentMode
        ParseHandler?.post {
            val RecordList = try {
                CaptureParsers.Preview(ModeVal = ModeSnapshot, Nodes = NodeSnapshot)
            } catch (_: Exception) {
                emptyList()
            }
            MainHandler.post {
                if (!IsCapturing) return@post
                LatestRecords = RecordList
                RefreshBubble()
            }
        }
    }

    private fun ElapsedMs(): Long {
        if (SessionStartedAt == 0L) return 0L
        val PausedSoFar = if (IsPaused && PausedAt > 0L) {
            PausedTotalMs + (System.currentTimeMillis() - PausedAt)
        } else {
            PausedTotalMs
        }
        return (System.currentTimeMillis() - SessionStartedAt - PausedSoFar).coerceAtLeast(0L)
    }


    @SuppressLint("InflateParams")
    private fun ShowBubble() {
        if (IsOverlayAdded || WindowMgr == null) return

        try {
            val ThemedContext = ContextThemeWrapper(this, R.style.Theme_DataReaderApp)
            val RootView =
                LayoutInflater.from(ThemedContext).inflate(R.layout.view_capture_bubble, null)
            BubbleView = RootView

            PillContainer = RootView.findViewById(R.id.pillContainer)
            CardActions = RootView.findViewById(R.id.cardActions)
            TvBubbleCount = RootView.findViewById(R.id.tvBubbleCount)
            TvBubbleMeta = RootView.findViewById(R.id.tvBubbleMeta)
            TvBubblePause = RootView.findViewById(R.id.btnBubblePause)
            val LayoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

            val LayoutParamsObj = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LayoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = (resources.displayMetrics.density * BUBBLE_MARGIN_DP).toInt()
                y = (resources.displayMetrics.heightPixels * BUBBLE_BOTTOM_OFFSET_RATIO).toInt()
            }
            BubbleLayoutParams = LayoutParamsObj

            AttachDragHandling(TargetView = PillContainer, LayoutParamsObj = LayoutParamsObj)

            TvBubblePause?.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SetPaused(PausedVal = !IsPaused)
            }
            RootView.findViewById<TextView>(R.id.btnBubbleFinish).setOnClickListener { ViewRef ->
                HapticFeedback.Confirm(ViewRef = ViewRef)
                FinishCaptureSession()
            }
            RootView.findViewById<TextView>(R.id.btnBubbleShareLog).setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                ShareDiagnosticLog()
            }
            RootView.findViewById<TextView>(R.id.btnBubbleDiscard).setOnClickListener { ViewRef ->
                HapticFeedback.Reject(ViewRef = ViewRef)
                DiscardCaptureSession()
            }

            WindowMgr?.addView(RootView, LayoutParamsObj)
            IsOverlayAdded = true
            IsBubbleExpanded = false
            RefreshBubble()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun AttachDragHandling(TargetView: View?, LayoutParamsObj: WindowManager.LayoutParams) {
        if (TargetView == null) return
        val TouchSlopPx = resources.displayMetrics.density * 8f

        var InitialXPos = 0
        var InitialYPos = 0
        var InitialTouchXVal = 0f
        var InitialTouchYVal = 0f

        TargetView.setOnTouchListener { ViewRef, MotionEvt ->
            when (MotionEvt.action) {
                MotionEvent.ACTION_DOWN -> {
                    InitialXPos = LayoutParamsObj.x
                    InitialYPos = LayoutParamsObj.y
                    InitialTouchXVal = MotionEvt.rawX
                    InitialTouchYVal = MotionEvt.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    LayoutParamsObj.x = InitialXPos + (MotionEvt.rawX - InitialTouchXVal).toInt()
                    LayoutParamsObj.y = InitialYPos - (MotionEvt.rawY - InitialTouchYVal).toInt()
                    if (LayoutParamsObj.x < 0) LayoutParamsObj.x = 0
                    if (LayoutParamsObj.y < 0) LayoutParamsObj.y = 0
                    try {
                        WindowMgr?.updateViewLayout(BubbleView, LayoutParamsObj)
                    } catch (_: Exception) {
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val DiffXVal = abs(MotionEvt.rawX - InitialTouchXVal)
                    val DiffYVal = abs(MotionEvt.rawY - InitialTouchYVal)
                    if (DiffXVal < TouchSlopPx && DiffYVal < TouchSlopPx) {
                        ViewRef.performClick()
                        ToggleBubbleExpanded()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun ToggleBubbleExpanded() {
        IsBubbleExpanded = !IsBubbleExpanded
        CardActions?.visibility = if (IsBubbleExpanded) View.VISIBLE else View.GONE
    }

    private fun BubbleWindowRect(): Rect? {
        if (!IsOverlayAdded) return null
        val RootView = BubbleView ?: return null
        if (RootView.width <= 0 || RootView.height <= 0) return null
        val LocationArr = IntArray(2)
        RootView.getLocationOnScreen(LocationArr)
        return Rect(
            LocationArr[0],
            LocationArr[1],
            LocationArr[0] + RootView.width,
            LocationArr[1] + RootView.height
        )
    }

    private fun CollapseBubbleForGesture() {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (!IsBubbleExpanded) return
        IsBubbleExpanded = false
        CardActions?.visibility = View.GONE
    }

    private fun MoveBubbleClearOf(XPos: Float, YPos: Float): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) return false
        val LayoutParamsObj = BubbleLayoutParams ?: return false
        val BubbleRect = BubbleWindowRect() ?: return false
        if (!BubbleRect.contains(XPos.toInt(), YPos.toInt())) return false
        val ScreenHeight = resources.displayMetrics.heightPixels
        val MovedY = if (LayoutParamsObj.y > ScreenHeight / 2) {
            (ScreenHeight * 0.25f).toInt()
        } else {
            (ScreenHeight * 0.75f).toInt()
        }
        LayoutParamsObj.y = MovedY
        try {
            WindowMgr?.updateViewLayout(BubbleView, LayoutParamsObj)
        } catch (_: Exception) {
            return false
        }
        DiagnosticWarning(
            EventName = "BUBBLE_TAP_COLLISION",
            MessageText = "gesture=($XPos,$YPos) bubble=$BubbleRect; " +
                    "moved the bubble to y=$MovedY and retrying from there"
        )
        return true
    }

    private fun ShieldBubbleFromGesture(XPos: Float, YPos: Float) {
        MoveBubbleClearOf(XPos = XPos, YPos = YPos)
        CollapseBubbleForGesture()
    }

    private fun ShareDiagnosticLog() {
        try {
            DiagnosticInfo(
                EventName = "LOG_SHARE",
                MessageText = "User requested the latest capture diagnostic log"
            )
            val LogFiles = CaptureDiagnostics
                .GetSessionLogFiles(ContextObj = this, SessionId = CurrentSessionId)
                .ifEmpty { CaptureDiagnostics.GetActiveLogFiles(ContextObj = this) }
            val ShareIntent = CaptureDiagnostics.BuildShareIntent(
                ContextObj = this,
                LogFiles = LogFiles
            )
            if (ShareIntent == null) {
                ShowServiceToast(
                    MessageText = "No diagnostic log is available yet",
                    KindVal = AppToast.Kind.Warning
                )
                return
            }
            val ChooserIntent = Intent.createChooser(
                ShareIntent,
                "Share diagnostic log"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(ChooserIntent)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "LOG_SHARE_FAILED",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            ShowServiceToast(
                MessageText = "Unable to share the diagnostic log",
                KindVal = AppToast.Kind.Error
            )
        }
    }

    private fun CurrentRecordCount(): Int = when (CurrentMode) {
        CaptureMode.CUSTOMER -> ProfilePatchMap.size
        CaptureMode.RENEWAL_DUE -> CapturedRenewalDueMap.size
        else -> LatestRecords.size
    }

    private fun CustomerCountLabel(): String {
        val ScopeCount = SessionPolicyNumbers.size
        if (ScopeCount == 0) {
            return CurrentMode.DescribeCount(CountVal = ProfilePatchMap.size)
        }
        return getString(
            R.string.bubble_customer_count_format,
            FilledPolicyNumbers.size.coerceAtMost(ScopeCount),
            ScopeCount
        )
    }

    private fun CustomerMetaLabel(ElapsedValue: Long): String {
        val TrailingText = if (ActiveCustomerName.isNotBlank()) {
            ShortCustomerName(NameText = ActiveCustomerName)
        } else {
            getString(R.string.bubble_customer_visited_format, VisitedCustomerNames.size)
        }
        return getString(
            R.string.bubble_customer_meta_format,
            CaptureSession.FormatClock(DurationMsVal = ElapsedValue),
            TrailingText
        )
    }

    private fun ShortCustomerName(NameText: String): String {
        val TrimmedName = NameText.trim()
        if (TrimmedName.length <= MAX_BUBBLE_NAME_LENGTH) return TrimmedName
        return TrimmedName.take(MAX_BUBBLE_NAME_LENGTH).trimEnd() + "\u2026"
    }

    private fun RefreshBubble() {
        if (!IsOverlayAdded) return

        val RecordCount = CurrentRecordCount()
        val NodeCount = CapturedNodes.size
        val ElapsedValue = ElapsedMs()
        val IsCustomerMode = CurrentMode == CaptureMode.CUSTOMER

        TvBubbleCount?.text = when {
            IsPaused -> getString(R.string.bubble_paused)
            IsCustomerMode && SessionPolicyNumbers.isNotEmpty() -> CustomerCountLabel()
            RecordCount == 0 -> getString(R.string.bubble_starting)
            else -> CurrentMode.DescribeCount(CountVal = RecordCount)
        }

        TvBubbleMeta?.text = if (IsCustomerMode) {
            CustomerMetaLabel(ElapsedValue = ElapsedValue)
        } else {
            getString(
                R.string.bubble_meta_format,
                CaptureSession.FormatClock(DurationMsVal = ElapsedValue),
                NodeCount
            )
        }

        TvBubblePause?.text = getString(
            if (IsPaused) R.string.bubble_resume else R.string.bubble_pause
        )

        PillContainer?.setBackgroundResource(
            if (IsPaused) R.drawable.bg_bubble_pill_paused else R.drawable.bg_bubble_pill
        )

        CaptureSessionState.OnProgress(
            RecordCountVal = RecordCount,
            NodeCountVal = NodeCount,
            ElapsedMsVal = ElapsedValue
        )
    }

    private fun ShowServiceToast(MessageText: CharSequence, KindVal: AppToast.Kind) {
        AppToast.ShowOverlay(
            ContextRef = this,
            WindowMgrRef = WindowMgr,
            MessageText = MessageText,
            KindVal = KindVal
        )
    }

    private fun RemoveBubble() {
        if (IsOverlayAdded && BubbleView != null) {
            try {
                WindowMgr?.removeView(BubbleView)
            } catch (_: Exception) {
            }
        }
        IsOverlayAdded = false
        IsBubbleExpanded = false
        BubbleView = null
        BubbleLayoutParams = null
        PillContainer = null
        CardActions = null
        TvBubbleCount = null
        TvBubbleMeta = null
        TvBubblePause = null
    }


    private fun StartForegroundNotification() {
        val ChannelIdStr = "DataReaderServiceChannel"
        val ChannelNameStr = "Screen Reader Automation Service"

        val NotificationMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ChannelObj =
            NotificationChannel(ChannelIdStr, ChannelNameStr, NotificationManager.IMPORTANCE_LOW)
        NotificationMgr.createNotificationChannel(ChannelObj)

        val NotificationObj =
            Notification.Builder(this, ChannelIdStr)
                .setContentTitle("Data Reader Service Active")
                .setContentText("Screen reader accessibility engine is active.")
                .setSmallIcon(R.drawable.ic_accessibility)
                .build()

        startForeground(1001, NotificationObj)
    }

    private fun AcquireWakeLock() {
        try {
            if (WakeLockObj == null) {
                val PowerMgr = getSystemService(POWER_SERVICE) as PowerManager
                WakeLockObj =
                    PowerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DataReaderApp:WakeLock")
            }
            if (WakeLockObj?.isHeld == false) {
                WakeLockObj?.acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (_: Exception) {
        }
    }

    private fun ReleaseWakeLock() {
        try {
            if (WakeLockObj?.isHeld == true) {
                WakeLockObj?.release()
            }
        } catch (_: Exception) {
        }
    }


    private enum class CustomerStage {
        IDLE, DASHBOARD, OPENING_CUSTOMER, READING_POLICIES, OPENING_PROFILE,
        READING_PROFILE, OPENING_SHEET, READING_SHEET, RETURNING
    }

    private fun IsCustomerDashboardScreen(VisibleNodes: List<String>): Boolean {
        if (VisibleNodes.any { NodeText ->
                NodeText.contains(CUSTOMER_TITLE_DETAIL, ignoreCase = true)
            }
        ) {
            return false
        }
        if (VisibleNodes.any { NodeText ->
                NodeText.contains(CUSTOMER_TITLE_DASHBOARD, ignoreCase = true)
            }
        ) {
            return true
        }
        val HasCustomerCount = VisibleNodes.any { NodeText ->
            NodeText.trim().equals("Customers", ignoreCase = true)
        }
        val HasCallAction = VisibleNodes.any { NodeText ->
            NodeText.contains(CUSTOMER_CALL_LABEL, ignoreCase = true)
        }
        return HasCustomerCount && HasCallAction
    }

    private fun IsCustomerDetailScreen(VisibleNodes: List<String>): Boolean {
        return VisibleNodes.any { NodeText ->
            NodeText.contains(CUSTOMER_TITLE_DETAIL, ignoreCase = true)
        }
    }

    private fun VisibleSheetKind(VisibleNodes: List<String>): CustomerProfileParser.ContactKind? {
        for (NodeText in VisibleNodes) {
            val Trimmed = NodeText.trim()
            if (Trimmed.equals(SHEET_TITLE_EMAIL, ignoreCase = true)) {
                return CustomerProfileParser.ContactKind.EMAIL
            }
            if (Trimmed.equals(SHEET_TITLE_ADDRESS, ignoreCase = true)) {
                return CustomerProfileParser.ContactKind.ADDRESS
            }
            if (Trimmed.equals(SHEET_TITLE_MOBILE, ignoreCase = true) ||
                Trimmed.startsWith("Mobile Number(", ignoreCase = true)
            ) {
                return CustomerProfileParser.ContactKind.MOBILE
            }
        }
        return null
    }

    private fun HandleCustomerScreenAutomation(
        RootNode: AccessibilityNodeInfo,
        VisibleNodes: List<String>
    ) {
        if (IsCustomerAutomationComplete) return

        val SheetKind = VisibleSheetKind(VisibleNodes = VisibleNodes)
        if (SheetKind != null) {
            HandleContactSheet(SheetKind = SheetKind, VisibleNodes = VisibleNodes)
            return
        }

        if (IsCustomerDetailScreen(VisibleNodes = VisibleNodes)) {
            IsCustomerDashboardActive = false
            HandleCustomerDetailScreen(RootNode = RootNode, VisibleNodes = VisibleNodes)
            return
        }

        if (IsCustomerDashboardScreen(VisibleNodes = VisibleNodes)) {
            OnCustomerDashboardVisible(VisibleNodes = VisibleNodes)
            return
        }

        if (IsCustomerPortfolioScreen(VisibleNodes = VisibleNodes)) {
            HandleCustomerPortfolioScreen(RootNode = RootNode)
        }
    }

    private fun HandleCustomerPortfolioScreen(RootNode: AccessibilityNodeInfo) {
        IsCustomerDashboardActive = false
        HasClickedHomeNavTab = true
        HomeNavClickAttempts = 0
        HomeNavLastAttemptAt = 0L

        val CurrentTime = System.currentTimeMillis()
        if (HasClickedPortfolioCustomers &&
            CurrentTime - PortfolioCustomersLastAttemptAt >= PORTFOLIO_TRANSITION_TIMEOUT_MS
        ) {
            DiagnosticWarning(
                EventName = "CUSTOMERS_TRANSITION_TIMEOUT",
                MessageText = "Customers click did not leave Customer Portfolio after " +
                        "${PORTFOLIO_TRANSITION_TIMEOUT_MS}ms; allowing another attempt"
            )
            HasClickedPortfolioCustomers = false
        }
        if (HasClickedPortfolioCustomers) return
        if (CurrentTime - PortfolioCustomersLastAttemptAt < PORTFOLIO_CLICK_RETRY_MS) return

        PortfolioCustomersClickAttempts++
        PortfolioCustomersLastAttemptAt = CurrentTime
        DiagnosticInfo(
            EventName = "CUSTOMERS_CLICK_ATTEMPT",
            MessageText = "attempt=$PortfolioCustomersClickAttempts"
        )
        HasClickedPortfolioCustomers = ClickPortfolioCustomersCard(RootNode = RootNode)
    }

    private fun ClickPortfolioCustomersCard(RootNode: AccessibilityNodeInfo): Boolean {
        val ScreenWidth = resources.displayMetrics.widthPixels
        val ScreenHeight = resources.displayMetrics.heightPixels
        val LabelBoundsList = mutableListOf<Rect>()

        for ((first, second) in CollectVisibleTextNodes(RootNode = RootNode)) {
            val LabelText = first.trim()
            val IsCustomersLabel = LabelText.equals("Customers", ignoreCase = true) ||
                    LabelText.matches(Regex("(?i)^\\d+\\s+Customers$"))
            if (!IsCustomersLabel) continue
            if (second.centerY() > ScreenHeight * 0.65f) continue
            LabelBoundsList.add(second)
        }

        DiagnosticInfo(
            EventName = "CUSTOMERS_CANDIDATES",
            MessageText = "labelCandidates=${LabelBoundsList.size}"
        )

        val TapY = LabelBoundsList.firstOrNull()?.centerY()?.toFloat()
            ?: (ScreenHeight * PORTFOLIO_POLICIES_ARROW_Y_FALLBACK_RATIO)
        val TapX = ScreenWidth * PORTFOLIO_CUSTOMERS_ARROW_X_RATIO

        val TapAccepted = PerformTapGesture(XPos = TapX, YPos = TapY)
        DiagnosticInfo(
            EventName = if (TapAccepted) "CUSTOMERS_CLICKED" else "CUSTOMERS_CLICK_REJECTED",
            MessageText = "x=$TapX y=$TapY labelDriven=${LabelBoundsList.isNotEmpty()}"
        )
        return TapAccepted
    }

    private fun OnCustomerDashboardVisible(VisibleNodes: List<String>) {
        IsCustomerDashboardActive = true
        HasClickedPortfolioCustomers = true
        PortfolioCustomersClickAttempts = 0
        PortfolioCustomersLastAttemptAt = 0L

        val PageInfo = ParsePolicyPageInfo(VisibleNodes = VisibleNodes)
        if (PageInfo != null) {
            CustomerCurrentPage = PageInfo.first
            CustomerTotalPages = PageInfo.second
            if (TargetCustomerPage == 0) TargetCustomerPage = PageInfo.first
        }
        StartCustomerAutomation()
    }

    private fun StartCustomerAutomation() {
        if (CustomerRoute.IsArmed) return
        if (IsCustomerAutomationRunning || IsCustomerAutomationComplete) return
        if (CurrentMode != CaptureMode.CUSTOMER) return
        if (System.currentTimeMillis() < CustomerAutomationRetryAfter) return

        IsCustomerAutomationRunning = true
        CustomerStageValue = CustomerStage.DASHBOARD
        DiagnosticInfo(
            EventName = "CUSTOMER_AUTOMATION_START",
            MessageText = "page=$CustomerCurrentPage/$CustomerTotalPages " +
                    "scopePolicies=${SessionPolicyNumbers.size} " +
                    "processed=${ProcessedCustomerKeys.size}"
        )
        ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) { RunCustomerDashboardStep() }
    }

    private fun IsErrorSheetScreen(FreshNodes: List<String>): Boolean {
        val HasTitle = FreshNodes.any { NodeText ->
            NodeText.contains(ERROR_SHEET_TITLE, ignoreCase = true)
        }
        return HasTitle && FreshNodes.any { NodeText ->
            NodeText.trim().equals(ERROR_SHEET_RETRY_LABEL, ignoreCase = true)
        }
    }


    private fun NoteScreenWithoutError(NodeCount: Int) {
        val RecordCountNow = CurrentRecordCount()
        if (RecordCountNow > LastHealthyRecordCount) {
            LastHealthyRecordCount = RecordCountNow
            ConsecutiveErrorGiveUps = 0
        }

        if (ErrorRetryCount == 0 &&
            !ErrorRecoveryScheduled &&
            ErrorBoundsMissCount == 0 &&
            OfflineSinceAt == 0L
        ) return
        if (NodeCount < ERROR_SHEET_HEALTHY_MIN_NODES) return

        val NowMs = System.currentTimeMillis()
        if (ErrorHealthySinceAt == 0L) {
            ErrorHealthySinceAt = NowMs
            CancelErrorRetry()
            DiagnosticInfo(
                EventName = "ERROR_SHEET_CLEARED",
                MessageText = "nodes=$NodeCount retries=$ErrorRetryCount " +
                        "watching for ${ERROR_SHEET_HEALTHY_WINDOW_MS}ms before trusting it"
            )
            ResumeAutomationAfterError()
            return
        }

        if (NowMs - ErrorHealthySinceAt < ERROR_SHEET_HEALTHY_WINDOW_MS) return

        DiagnosticInfo(
            EventName = "ERROR_SHEET_RECOVERED",
            MessageText = "retries=$ErrorRetryCount paceExtraMs=$ErrorPaceExtraMs " +
                    "healthyMs=${NowMs - ErrorHealthySinceAt}"
        )
        ErrorRetryCount = 0
        ErrorBoundsMissCount = 0
        ErrorHealthySinceAt = 0L
        ConsecutiveErrorGiveUps = 0
        if (OfflineSinceAt != 0L) {
            DiagnosticInfo(
                EventName = "OFFLINE_RECOVERED",
                MessageText = "retries=$OfflineRetryCount offlineMs=${NowMs - OfflineSinceAt}"
            )
            CancelOfflineWatch()
            OfflineSinceAt = 0L
            OfflineRetryCount = 0
            OfflineLastLogAt = 0L
        }
    }

    private fun CancelErrorRetry() {
        ErrorRetryRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        ErrorRetryRunnable = null
        ErrorRecoveryScheduled = false
    }

    private fun IsOfflineScreen(FreshNodes: List<String>): Boolean {
        val HasTitle = FreshNodes.any { NodeText ->
            NodeText.contains(OFFLINE_TITLE, ignoreCase = true) ||
                    NodeText.contains(OFFLINE_SUBTITLE, ignoreCase = true)
        }
        return HasTitle && FreshNodes.any { NodeText ->
            NodeText.trim().equals(ERROR_SHEET_RETRY_LABEL, ignoreCase = true)
        }
    }

    private fun HasValidatedNetwork(): Boolean {
        return try {
            val ManagerRef = getSystemService(ConnectivityManager::class.java) ?: return true
            val NetworkRef = ManagerRef.activeNetwork ?: return false
            val CapabilitiesRef = ManagerRef.getNetworkCapabilities(NetworkRef) ?: return false
            CapabilitiesRef.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    CapabilitiesRef.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            true
        }
    }

    private fun HandleOfflineScreen() {
        ErrorHealthySinceAt = 0L
        SuspendAutomationForError()

        if (OfflineSinceAt == 0L) {
            OfflineSinceAt = System.currentTimeMillis()
            OfflineRetryCount = 0
            OfflineLastLogAt = 0L
            DiagnosticWarning(
                EventName = "OFFLINE_SEEN",
                MessageText = "mode=${CurrentMode.name} customer=$ActiveCustomerName " +
                        "network=${HasValidatedNetwork()} " +
                        "page=$PolicyCurrentPage/$PolicyTotalPages nothing is skipped while waiting"
            )
        }

        if (OfflineRetryRunnable != null) return
        ScheduleOfflineWatch(DelayMs = OFFLINE_POLL_MS)
    }

    private fun ScheduleOfflineWatch(DelayMs: Long) {
        CancelOfflineWatch()
        val WatchRunnable = Runnable {
            OfflineRetryRunnable = null
            RunOfflineWatch()
        }
        OfflineRetryRunnable = WatchRunnable
        MainHandler.postDelayed(WatchRunnable, DelayMs)
    }

    private fun CancelOfflineWatch() {
        OfflineRetryRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        OfflineRetryRunnable = null
    }

    private fun RunOfflineWatch() {
        if (!IsCapturing) {
            OfflineSinceAt = 0L
            return
        }
        if (IsPaused) {
            ScheduleOfflineWatch(DelayMs = OFFLINE_POLL_MS)
            return
        }

        val FreshRoot = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        )
        if (FreshRoot == null) {
            ScheduleOfflineWatch(DelayMs = OFFLINE_POLL_MS)
            return
        }

        val WaitedMs = System.currentTimeMillis() - OfflineSinceAt
        try {
            val FreshTexts = CollectVisibleTextNodes(RootNode = FreshRoot)
                .map { NodePair -> NodePair.first }
            if (!IsOfflineScreen(FreshNodes = FreshTexts)) {
                DiagnosticInfo(
                    EventName = "OFFLINE_SCREEN_GONE",
                    MessageText = "retries=$OfflineRetryCount waitedMs=$WaitedMs " +
                            "nodes=${FreshTexts.size} watching before trusting it"
                )
                ResumeAutomationAfterError()
                return
            }

            if (WaitedMs >= OfflineWaitMs) {
                StopSessionForOffline(WaitedMs = WaitedMs)
                return
            }

            if (!HasValidatedNetwork()) {
                val NowMs = System.currentTimeMillis()
                if (NowMs - OfflineLastLogAt >= OFFLINE_LOG_INTERVAL_MS) {
                    OfflineLastLogAt = NowMs
                    DiagnosticInfo(
                        EventName = "OFFLINE_WAITING",
                        MessageText = "waitedMs=$WaitedMs the phone has no usable network; " +
                                "holding the run, no customer is skipped"
                    )
                }
                ScheduleOfflineWatch(DelayMs = OFFLINE_POLL_MS)
                return
            }

            val RetryBounds = FindErrorRetryBounds(RootNode = FreshRoot)
            if (RetryBounds == null) {
                ScheduleOfflineWatch(DelayMs = OFFLINE_POLL_MS)
                return
            }

            val TapAccepted = PerformTapGesture(
                XPos = RetryBounds.exactCenterX(),
                YPos = RetryBounds.exactCenterY()
            )
            OfflineRetryCount++
            DiagnosticInfo(
                EventName = if (TapAccepted) "OFFLINE_RETRIED" else "OFFLINE_RETRY_REJECTED",
                MessageText = "attempt=$OfflineRetryCount waitedMs=$WaitedMs " +
                        "x=${RetryBounds.exactCenterX()} y=${RetryBounds.exactCenterY()}"
            )
            val BackoffMs = OFFLINE_BACKOFF_MS[
                (OfflineRetryCount - 1).coerceIn(0, OFFLINE_BACKOFF_MS.size - 1)
            ]
            ScheduleOfflineWatch(DelayMs = BackoffMs)
        } finally {
            RecycleNode(NodeRef = FreshRoot)
        }
    }

    private fun StopSessionForOffline(WaitedMs: Long) {
        CancelOfflineWatch()
        CancelErrorRetry()
        SuspendAutomationForError()
        OfflineSinceAt = 0L
        DiagnosticWarning(
            EventName = "SESSION_STOPPED_OFFLINE",
            MessageText = "waitedMs=$WaitedMs retries=$OfflineRetryCount " +
                    "mode=${CurrentMode.name} records=${CurrentRecordCount()} " +
                    "nodes=${CapturedNodes.size}"
        )
        ShowServiceToast(
            MessageText = getString(R.string.capture_stopped_no_network),
            KindVal = AppToast.Kind.Warning
        )
        FinishCaptureSession()
    }

    private fun NoteScreenSubstance(VisibleNodes: List<String>) {
        val NowMs = System.currentTimeMillis()
        TrackBlankScreen(NodeCount = VisibleNodes.size, NowMs = NowMs)
        val SignatureVal = VisibleNodes.hashCode()
        LastScreenLookAt = NowMs
        LastScreenNodeCount = VisibleNodes.size
        if (SignatureVal == LastScreenSignature && ScreenStableSinceAt != 0L) return
        LastScreenSignature = SignatureVal
        ScreenStableSinceAt = NowMs
    }

    private fun RefreshScreenSubstanceLook() {
        val FreshRoot = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return
        try {
            val FreshTexts = mutableListOf<String>()
            TraverseNode(TargetNode = FreshRoot, ResultList = FreshTexts)
            NoteScreenSubstance(VisibleNodes = FreshTexts)
        } finally {
            RecycleNode(NodeRef = FreshRoot)
        }
    }

    private fun TrackBlankScreen(NodeCount: Int, NowMs: Long) {
        if (NodeCount >= SCREEN_READY_MIN_TEXT_NODES) {
            if (BlankRecoveryAttempts > 0 || BlankRecoveryScheduled) {
                DiagnosticInfo(
                    EventName = "BLANK_SCREEN_RECOVERED",
                    MessageText = "attempts=$BlankRecoveryAttempts nodes=$NodeCount " +
                            "mode=${CurrentMode.name}"
                )
            }
            ClearBlankScreenState()
            return
        }
        if (BlankRecoveryScheduled) return
        if (!IsBlankWatchActive()) {
            BlankSinceAt = 0L
            return
        }
        if (BlankSinceAt == 0L) {
            BlankSinceAt = NowMs
            return
        }
        if (NowMs - BlankSinceAt < BLANK_SCREEN_HOLD_MS) return

        BlankRecoveryScheduled = true
        DiagnosticWarning(
            EventName = "BLANK_SCREEN_SEEN",
            MessageText = "blankMs=${NowMs - BlankSinceAt} nodes=$NodeCount " +
                    "mode=${CurrentMode.name} records=${CurrentRecordCount()}"
        )
        RunBlankScreenRecovery()
    }

    private fun IsBlankWatchActive(): Boolean {
        if (!IsCapturing || IsPaused) return false
        if (CurrentMode != CaptureMode.POLICY &&
            CurrentMode != CaptureMode.FUP &&
            CurrentMode != CaptureMode.CUSTOMER
        ) return false
        if (OcrInFlight) return false
        if (OfflineSinceAt != 0L) return false
        if (ErrorRecoveryScheduled) return false
        if (IsRenewalRangeSheetHidden) return false
        return true
    }

    private fun RunBlankScreenRecovery() {
        if (!IsCapturing) {
            ClearBlankScreenState()
            return
        }
        BlankRecoveryAttempts++
        if (BlankRecoveryAttempts > BLANK_SCREEN_RETRY_LIMIT) {
            StopSessionForBlankScreen()
            return
        }

        val BackAccepted = performGlobalAction(GLOBAL_ACTION_BACK)
        DiagnosticInfo(
            EventName = "BLANK_SCREEN_RECOVERY",
            MessageText = "attempt=$BlankRecoveryAttempts/$BLANK_SCREEN_RETRY_LIMIT " +
                    "action=back accepted=$BackAccepted mode=${CurrentMode.name}"
        )

        val RunnableRef = Runnable {
            BlankRecoveryRunnable = null
            if (!IsCapturing) {
                ClearBlankScreenState()
                return@Runnable
            }
            RefreshScreenSubstanceLook()
            if (!BlankRecoveryScheduled) return@Runnable
            if (LastScreenNodeCount >= SCREEN_READY_MIN_TEXT_NODES) {
                DiagnosticInfo(
                    EventName = "BLANK_SCREEN_RECOVERED",
                    MessageText = "attempts=$BlankRecoveryAttempts nodes=$LastScreenNodeCount " +
                            "mode=${CurrentMode.name}"
                )
                ClearBlankScreenState()
                return@Runnable
            }
            RunBlankScreenRecovery()
        }
        BlankRecoveryRunnable = RunnableRef
        MainHandler.postDelayed(RunnableRef, BLANK_SCREEN_RETRY_DELAY_MS)
    }

    private fun StopSessionForBlankScreen() {
        val AttemptCount = BlankRecoveryAttempts - 1
        ClearBlankScreenState()
        CancelOfflineWatch()
        CancelErrorRetry()
        SuspendAutomationForError()
        DiagnosticWarning(
            EventName = "SESSION_STOPPED_BLANK_SCREEN",
            MessageText = "attempts=$AttemptCount mode=${CurrentMode.name} " +
                    "records=${CurrentRecordCount()} nodes=${CapturedNodes.size}; " +
                    "session saved so it can be resumed"
        )
        ShowServiceToast(
            MessageText = getString(R.string.capture_stopped_blank_screen),
            KindVal = AppToast.Kind.Warning
        )
        FinishCaptureSession()
    }

    private fun ClearBlankScreenState() {
        BlankRecoveryRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        BlankRecoveryRunnable = null
        BlankRecoveryScheduled = false
        BlankRecoveryAttempts = 0
        BlankSinceAt = 0L
    }

    private fun IsScreenSettled(WaitCount: Int): Boolean {
        if (CurrentMode != CaptureMode.POLICY &&
            CurrentMode != CaptureMode.FUP &&
            CurrentMode != CaptureMode.CUSTOMER
        ) return true

        if (WaitCount >= SCREEN_READY_MAX_WAITS) {
            DiagnosticWarning(
                EventName = "SCREEN_RENDER_TIMEOUT",
                MessageText = "waits=$WaitCount nodes=$LastScreenNodeCount " +
                        "mode=${CurrentMode.name} acting anyway"
            )
            return true
        }

        val NowMs = System.currentTimeMillis()
        if (LastScreenLookAt == 0L || NowMs - LastScreenLookAt > SCREEN_READY_LOOK_STALE_MS) {
            RefreshScreenSubstanceLook()
        }
        if (LastScreenLookAt == 0L) return true

        if (LastScreenNodeCount < SCREEN_READY_MIN_TEXT_NODES) {
            NoteScreenStillRendering(
                ReasonText = "nodes=$LastScreenNodeCount still blank",
                WaitCount = WaitCount
            )
            return false
        }
        if (System.currentTimeMillis() - ScreenStableSinceAt < SCREEN_READY_STABLE_MS) {
            NoteScreenStillRendering(
                ReasonText = "screen still changing",
                WaitCount = WaitCount
            )
            return false
        }
        return true
    }

    private fun NoteScreenStillRendering(ReasonText: String, WaitCount: Int) {
        if (WaitCount > 0) return
        DiagnosticInfo(
            EventName = "SCREEN_RENDERING",
            MessageText = "$ReasonText mode=${CurrentMode.name} nodes=$LastScreenNodeCount " +
                    "holding up to ${SCREEN_READY_MAX_WAITS * SCREEN_READY_RECHECK_MS}ms"
        )
    }

    private fun HandleErrorSheet(RootNode: AccessibilityNodeInfo) {
        ErrorHealthySinceAt = 0L
        if (ErrorRecoveryScheduled) return

        SuspendAutomationForError()

        if (ErrorRetryCount >= ErrorRetryLimit) {
            GiveUpOnErrorSheet(ReasonText = "retries=$ErrorRetryCount exhausted")
            return
        }

        if (FindErrorRetryBounds(RootNode = RootNode) == null) {
            ErrorBoundsMissCount++
            if (ErrorBoundsMissCount < ERROR_SHEET_BOUNDS_MISS_LIMIT) return
            GiveUpOnErrorSheet(ReasonText = "no Try Again bounds after $ErrorBoundsMissCount looks")
            return
        }

        ErrorBoundsMissCount = 0
        val BackoffMs = ERROR_SHEET_BACKOFF_MS[
            ErrorRetryCount.coerceAtMost(ERROR_SHEET_BACKOFF_MS.size - 1)
        ]
        ErrorRetryCount++
        ErrorRecoveryScheduled = true

        DiagnosticWarning(
            EventName = "ERROR_SHEET_SEEN",
            MessageText = "attempt=$ErrorRetryCount of $ErrorRetryLimit " +
                    "backoffMs=$BackoffMs mode=${CurrentMode.name} " +
                    "customer=$ActiveCustomerName"
        )

        val RetryRunnable = Runnable {
            ErrorRetryRunnable = null
            ErrorRecoveryScheduled = false
            TapErrorRetryNow()
        }
        ErrorRetryRunnable = RetryRunnable
        MainHandler.postDelayed(RetryRunnable, BackoffMs)
    }


    private fun TapErrorRetryNow() {
        if (!IsCapturing || IsPaused) return

        val FreshRoot = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        )
        if (FreshRoot == null) {
            DiagnosticInfo(
                EventName = "ERROR_SHEET_RETRY_ABANDONED",
                MessageText = "attempt=$ErrorRetryCount reason=no readable window"
            )
            return
        }

        try {
            val FreshNodes = CollectVisibleTextNodes(RootNode = FreshRoot)
            if (!IsErrorSheetScreen(FreshNodes = FreshNodes.map { NodePair -> NodePair.first })) {
                DiagnosticInfo(
                    EventName = "ERROR_SHEET_RETRY_ABANDONED",
                    MessageText = "attempt=$ErrorRetryCount reason=sheet gone before the tap"
                )
                ResumeAutomationAfterError()
                return
            }

            val RetryBounds = FindErrorRetryBounds(RootNode = FreshRoot)
            if (RetryBounds == null) {
                DiagnosticInfo(
                    EventName = "ERROR_SHEET_RETRY_ABANDONED",
                    MessageText = "attempt=$ErrorRetryCount reason=Try Again no longer on screen"
                )
                ResumeAutomationAfterError()
                return
            }

            val TapAccepted = PerformTapGesture(
                XPos = RetryBounds.exactCenterX(),
                YPos = RetryBounds.exactCenterY()
            )
            DiagnosticInfo(
                EventName = if (TapAccepted) "ERROR_SHEET_RETRIED" else "ERROR_SHEET_RETRY_REJECTED",
                MessageText = "attempt=$ErrorRetryCount x=${RetryBounds.exactCenterX()} " +
                        "y=${RetryBounds.exactCenterY()}"
            )
        } finally {
            RecycleNode(NodeRef = FreshRoot)
        }
    }


    private fun ResumeAutomationAfterError() {
        when (CurrentMode) {
            CaptureMode.POLICY -> {
                if (!IsPolicyDashboardAutomationRunning) return
                SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS + ErrorPaceExtraMs) {
                    StartPolicyPageWork()
                }
            }

            CaptureMode.FUP -> {
                if (!IsRenewalAutomationRunning) return
                ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS + ErrorPaceExtraMs) {
                    RunRenewalAutomationStep()
                }
            }

            CaptureMode.RENEWAL_DUE -> {
                if (!IsRenewalAutomationRunning) return
                ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS + ErrorPaceExtraMs) {
                    RunRenewalDueStep()
                }
            }

            CaptureMode.CUSTOMER -> {
                if (!IsCustomerAutomationRunning) return
                ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                    ReturnToCustomerDashboard(AttemptCount = 0)
                }
            }

            else -> return
        }
        DiagnosticInfo(
            EventName = "AUTOMATION_REARMED",
            MessageText = "mode=${CurrentMode.name} paceExtraMs=$ErrorPaceExtraMs"
        )
    }


    private fun SuspendAutomationForError() {
        StopAutoScroll()
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        RenewalAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        RenewalAutomationRunnable = null
        CustomerAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        CustomerAutomationRunnable = null
    }

    private fun FindErrorRetryBounds(RootNode: AccessibilityNodeInfo): Rect? {
        for ((first, second) in CollectVisibleTextNodes(RootNode = RootNode)) {
            if (!first.trim().equals(ERROR_SHEET_RETRY_LABEL, ignoreCase = true)) continue
            if (second.width() <= 0 || second.height() <= 0) continue
            return second
        }
        return null
    }

    private fun GiveUpOnErrorSheet(ReasonText: String) {
        CancelErrorRetry()
        ErrorRetryCount = 0
        ErrorBoundsMissCount = 0
        ErrorHealthySinceAt = 0L
        ConsecutiveErrorGiveUps++
        if (ErrorSlowDownEnabled) {
            ErrorPaceExtraMs = (ErrorPaceExtraMs + ERROR_SHEET_PACE_STEP_MS)
                .coerceAtMost(ERROR_SHEET_PACE_CEILING_MS)
        }

        DiagnosticWarning(
            EventName = "ERROR_SHEET_GIVEUP",
            MessageText = "$ReasonText consecutive=$ConsecutiveErrorGiveUps " +
                    "paceExtraMs=$ErrorPaceExtraMs mode=${CurrentMode.name} " +
                    "page=$PolicyCurrentPage/$PolicyTotalPages " +
                    "expectedPage=$PolicyExpectedPage customer=$ActiveCustomerName"
        )

        if (ConsecutiveErrorGiveUps >= ErrorGiveUpLimit) {
            StopSessionForErrors()
            return
        }

        SkipItemAfterError()
    }

    private fun SkipItemAfterError() {
        performGlobalAction(GLOBAL_ACTION_BACK)

        if (CurrentMode != CaptureMode.CUSTOMER) {
            DiagnosticInfo(
                EventName = "ERROR_SHEET_DISMISSED",
                MessageText = "mode=${CurrentMode.name} backed out after the error"
            )
            ResumeAutomationAfterError()
            return
        }

        DiagnosticInfo(
            EventName = "CUSTOMER_SKIPPED_AFTER_ERROR",
            MessageText = "customer=$ActiveCustomerName left unvisited so a later run retries it"
        )
        ActiveProfile = null
        ProfilePaneNodes.clear()
        PendingSheetKinds.clear()
        ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
            ReturnToCustomerDashboard(AttemptCount = 0)
        }
    }

    private fun StopSessionForErrors() {
        CancelErrorRetry()
        SuspendAutomationForError()
        DiagnosticWarning(
            EventName = "SESSION_STOPPED_BY_ERRORS",
            MessageText = "consecutive=$ConsecutiveErrorGiveUps mode=${CurrentMode.name} " +
                    "records=${CurrentRecordCount()} nodes=${CapturedNodes.size}"
        )
        ShowServiceToast(
            MessageText = getString(R.string.capture_stopped_app_errors),
            KindVal = AppToast.Kind.Error
        )
        FinishCaptureSession()
    }

    private fun ScheduleCustomerAction(DelayMs: Long, ActionRef: () -> Unit) {
        CustomerAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }

        var RenderWaitCount = 0
        lateinit var WrappedRunnable: Runnable
        WrappedRunnable = Runnable {
            if (!IsCustomerAutomationRunning ||
                !IsCapturing ||
                CurrentMode != CaptureMode.CUSTOMER
            ) {
                return@Runnable
            }
            if (IsPaused) {
                CustomerAutomationRunnable = WrappedRunnable
                MainHandler.postDelayed(WrappedRunnable, TICK_INTERVAL_MS)
                return@Runnable
            }
            if (!IsScreenSettled(WaitCount = RenderWaitCount)) {
                RenderWaitCount++
                CustomerAutomationRunnable = WrappedRunnable
                MainHandler.postDelayed(WrappedRunnable, Paced(BaseMs = SCREEN_READY_RECHECK_MS))
                return@Runnable
            }
            CustomerAutomationRunnable = null
            ActionRef()
        }
        CustomerAutomationRunnable = WrappedRunnable
        MainHandler.postDelayed(WrappedRunnable, Paced(BaseMs = DelayMs) + ErrorPaceExtraMs)
    }

    private data class CustomerRow(
        val NameText: String,
        val NameBounds: Rect,
        val CallBounds: Rect?
    )

    private fun NormalisedName(NameText: String): String {
        return NameText.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
    }

    private fun CustomerKey(NameText: String): String {
        return "$TargetCustomerPage|${NormalisedName(NameText = NameText)}"
    }

    private fun MarkCustomerVisited(NameText: String) {
        if (NameText.isBlank()) return
        if (!VisitedCustomerNames.add(NormalisedName(NameText = NameText))) return
        PolicyRepository.SaveVisitedCustomers(
            ContextRef = this,
            SessionId = CurrentSessionId,
            Names = VisitedCustomerNames
        )
    }

    private fun CollectCustomerRows(TextNodes: List<Pair<String, Rect>>): List<CustomerRow> {
        val AgeRegex = Regex("^\\d{1,3}\\s+Years$", RegexOption.IGNORE_CASE)
        val ScreenHeight = resources.displayMetrics.heightPixels
        val CallBoundsList = TextNodes
            .filter { NodePair -> NodePair.first.trim().startsWith(CUSTOMER_CALL_LABEL, true) }
            .map { NodePair -> NodePair.second }

        val RowList = mutableListOf<CustomerRow>()
        for ((first, AgeBounds) in TextNodes) {
            if (!AgeRegex.matches(first.trim())) continue
            val NameCandidate = TextNodes
                .filter { CandidatePair ->
                    val CandidateBounds = CandidatePair.second
                    val CandidateText = CandidatePair.first.trim()
                    CandidateBounds.bottom <= AgeBounds.top + 8 &&
                            AgeBounds.top - CandidateBounds.bottom <=
                            ScreenHeight * CUSTOMER_NAME_GAP_RATIO &&
                            abs(CandidateBounds.left - AgeBounds.left) <= 32 &&
                            CandidateText.length in 2..60 &&
                            !AgeRegex.matches(CandidateText)
                }
                .maxByOrNull { CandidatePair -> CandidatePair.second.bottom }
                ?: continue
            val RowCallBounds = CallBoundsList
                .filter { BoundsObj -> BoundsObj.top > AgeBounds.top }
                .minByOrNull { BoundsObj -> BoundsObj.top }
            RowList.add(
                CustomerRow(
                    NameText = NameCandidate.first.trim(),
                    NameBounds = Rect(NameCandidate.second),
                    CallBounds = RowCallBounds?.let { BoundsObj -> Rect(BoundsObj) }
                )
            )
        }
        return RowList.sortedBy { RowItem -> RowItem.NameBounds.top }
    }

    private fun CollectCustomerArrowBounds(
        TargetNode: AccessibilityNodeInfo,
        ArrowBoundsList: MutableList<Rect>
    ) {
        try {
            val NodeText = NodeTextValue(NodeRef = TargetNode)
            val IsCallRow = NodeText.contains(CUSTOMER_CALL_LABEL, ignoreCase = true)
            val IsRowArrow = NodeText.trim().equals("arrow-right", ignoreCase = true) ||
                    NodeText.contains("card right arrow", ignoreCase = true) ||
                    NodeText.equals("right arrow icon", ignoreCase = true)
            if (IsRowArrow && !IsCallRow) {
                val NodeBounds = Rect()
                TargetNode.getBoundsInScreen(NodeBounds)
                if (!NodeBounds.isEmpty) ArrowBoundsList.add(NodeBounds)
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    CollectCustomerArrowBounds(
                        TargetNode = ChildNode,
                        ArrowBoundsList = ArrowBoundsList
                    )
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "CUSTOMER_ARROW_SCAN_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
    }

    private fun CurrentScreenNodes(): List<String> {
        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            ?: return emptyList()
        return try {
            val NodeList = mutableListOf<String>()
            TraverseNode(TargetNode = RootNode, ResultList = NodeList)
            NodeList
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun TraverseNodeWithDescriptions(
        TargetNode: AccessibilityNodeInfo?,
        ResultList: MutableList<String>
    ) {
        if (TargetNode == null) return
        try {
            val TextContent = TargetNode.text?.toString()?.trim().orEmpty()
            val DescContent = TargetNode.contentDescription?.toString()?.trim().orEmpty()
            if (TextContent.isNotEmpty()) ResultList.add(TextContent)
            if (DescContent.isNotEmpty() && !DescContent.equals(TextContent, ignoreCase = true)) {
                ResultList.add(DescContent)
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    TraverseNodeWithDescriptions(TargetNode = ChildNode, ResultList = ResultList)
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Node became stale while reading descriptions", ExceptionObj)
        }
    }

    private fun CurrentScreenNodesWithDescriptions(): List<String> {
        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            ?: return emptyList()
        return try {
            val NodeList = mutableListOf<String>()
            TraverseNodeWithDescriptions(TargetNode = RootNode, ResultList = NodeList)
            NodeList
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun CurrentBoundsNodes(): List<Pair<String, Rect>> {
        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            ?: return emptyList()
        return try {
            CollectVisibleTextNodes(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun ProfileTabBounds(TextNodes: List<Pair<String, Rect>>): Rect? {
        val ScreenHeight = resources.displayMetrics.heightPixels
        val ProfileNodes = TextNodes
            .filter { NodePair -> NodePair.first.trim().equals(CUSTOMER_TAB_PROFILE, true) }
            .filter { NodePair -> NodePair.second.centerY() < ScreenHeight * 0.85f }
        val PoliciesNodes = TextNodes
            .filter { NodePair -> NodePair.first.trim().equals("Policies", true) }

        val PairedTab = ProfileNodes.firstOrNull { ProfilePair ->
            PoliciesNodes.any { PoliciesPair ->
                abs(PoliciesPair.second.centerY() - ProfilePair.second.centerY()) <= 40 &&
                        PoliciesPair.second.centerX() < ProfilePair.second.centerX()
            }
        }
        return (PairedTab ?: ProfileNodes.minByOrNull { NodePair -> NodePair.second.top })?.second
    }

    private fun ScrollDetailToTop(AttemptCount: Int, OnReady: () -> Unit) {
        val BoundsNodes = CurrentBoundsNodes()
        val HasAnchor = ProfileTabBounds(TextNodes = BoundsNodes) != null ||
                BoundsNodes.any { NodePair ->
                    NodePair.first.contains(CUSTOMER_TITLE_DETAIL, ignoreCase = true)
                }
        if (HasAnchor || AttemptCount >= CUSTOMER_DETAIL_TOP_LIMIT) {
            if (!HasAnchor) {
                DiagnosticWarning(
                    EventName = "CUSTOMER_DETAIL_TOP_GIVEUP",
                    MessageText = "customer=$ActiveCustomerName could not scroll back to the tab " +
                            "strip after $AttemptCount attempts"
                )
            }
            OnReady()
            return
        }
        PerformPolicyScroll(ForwardVal = false, PreferAccessibilityAction = false)
        ScheduleCustomerAction(DelayMs = CUSTOMER_PROFILE_SWEEP_SETTLE_MS) {
            ScrollDetailToTop(AttemptCount = AttemptCount + 1, OnReady = OnReady)
        }
    }

    private fun RunCustomerDashboardStep() {
        if (!IsCustomerAutomationRunning) return
        CustomerStageValue = CustomerStage.DASHBOARD
        if (CustomerNameScope.IsActive &&
            CustomerNameScope.Numbers.all { NameText ->
                VisitedCustomerNames.contains(NameText)
            }
        ) {
            CompleteCustomerAutomation(ReasonText = "targeted customers filled")
            return
        }
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)

        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        if (RootNode == null) {
            RetryCustomerStep(ReasonText = "dashboard root unavailable")
            return
        }
        try {
            val TextNodes = CollectVisibleTextNodes(RootNode = RootNode)
            if (!EnsureCustomerPageKnown(TextNodes = TextNodes)) return
            if (CustomerCurrentPage != TargetCustomerPage) {
                DiagnosticInfo(
                    EventName = "CUSTOMER_PAGE_RESET",
                    MessageText = "the app came back on page $CustomerCurrentPage but the run is " +
                            "working page $TargetCustomerPage; jumping straight to it " +
                            "processed=${ProcessedCustomerKeys.size}"
                )
                ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                    ReturnToCustomerPageSelector(ScrollCount = 0)
                }
                return
            }
            val RowList = CollectCustomerRows(TextNodes = TextNodes)
            val NextRow = RowList.firstOrNull { RowItem ->
                !ProcessedCustomerKeys.contains(CustomerKey(NameText = RowItem.NameText)) &&
                        !VisitedCustomerNames.contains(NormalisedName(NameText = RowItem.NameText)) &&
                        CustomerNameScope.Allows(
                            PolicyNumber = NormalisedName(NameText = RowItem.NameText)
                        )
            }
            if (NextRow != null) {
                CustomerStepAttempts = 0
                OpenCustomerRow(RootNode = RootNode, RowItem = NextRow)
                return
            }
            AdvanceCustomerDashboard(TextNodes = TextNodes, VisibleRowCount = RowList.size)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun EnsureCustomerPageKnown(TextNodes: List<Pair<String, Rect>>): Boolean {
        val PageInfo = ParsePolicyPageInfo(
            VisibleNodes = TextNodes.map { NodePair -> NodePair.first }
        )
        if (PageInfo != null) {
            CustomerCurrentPage = PageInfo.first
            CustomerTotalPages = PageInfo.second
            if (CustomerTotalPages in 1..<TargetCustomerPage) {
                DiagnosticWarning(
                    EventName = "CUSTOMER_RESUME_CLAMPED",
                    MessageText = "target=$TargetCustomerPage clamped=$CustomerTotalPages " +
                            "total=$CustomerTotalPages"
                )
                TargetCustomerPage = CustomerTotalPages
            }
            if (TargetCustomerPage == 0) {
                if (ProcessedCustomerKeys.isNotEmpty()) {
                    DiagnosticWarning(
                        EventName = "CUSTOMER_PAGE_TARGET_LOST",
                        MessageText = "the working page was cleared mid-run with " +
                                "${ProcessedCustomerKeys.size} customers already processed; " +
                                "adopting the visible page ${PageInfo.first}"
                    )
                }
                TargetCustomerPage = PageInfo.first
            }
            CustomerPageWaitCount = 0
            return true
        }
        if (CustomerCurrentPage > 0) return true

        CustomerPageWaitCount++
        if (CustomerPageWaitCount <= CUSTOMER_PAGE_WAIT_LIMIT) {
            DiagnosticInfo(
                EventName = "CUSTOMER_PAGE_UNKNOWN",
                MessageText = "attempt=$CustomerPageWaitCount nodes=${TextNodes.size}; " +
                        "holding off until the page number renders"
            )
            ScheduleCustomerAction(DelayMs = CUSTOMER_SCROLL_SETTLE_MS) {
                RunCustomerDashboardStep()
            }
            return false
        }

        DiagnosticWarning(
            EventName = "CUSTOMER_PAGE_ASSUMED",
            MessageText = "page number never rendered after $CustomerPageWaitCount checks; " +
                    "treating the list as a single page"
        )
        CustomerCurrentPage = 1
        if (CustomerTotalPages <= 0) CustomerTotalPages = 1
        if (TargetCustomerPage == 0) TargetCustomerPage = 1
        CustomerPageWaitCount = 0
        return true
    }

    private fun OpenCustomerRow(RootNode: AccessibilityNodeInfo, RowItem: CustomerRow) {
        val ScreenWidth = resources.displayMetrics.widthPixels
        val ScreenHeight = resources.displayMetrics.heightPixels

        val ArrowBoundsList = mutableListOf<Rect>()
        CollectCustomerArrowBounds(TargetNode = RootNode, ArrowBoundsList = ArrowBoundsList)

        val RowCeiling = RowItem.NameBounds.centerY()
        val RowFloor = RowItem.CallBounds?.top ?: (RowCeiling + (ScreenHeight * 0.22f).toInt())
        val SelectedArrow = ArrowBoundsList
            .filter { BoundsObj ->
                BoundsObj.centerX() > ScreenWidth * CUSTOMER_ROW_ARROW_X_MIN_RATIO &&
                        BoundsObj.centerY() > RowCeiling &&
                        BoundsObj.centerY() < RowFloor
            }
            .minByOrNull { BoundsObj -> BoundsObj.centerY() }

        val TapX: Float
        val TapY: Float
        if (SelectedArrow != null) {
            TapX = SelectedArrow.centerX().toFloat()
            TapY = SelectedArrow.centerY().toFloat()
        } else {
            TapX = ScreenWidth * CUSTOMER_ROW_ARROW_X_FALLBACK_RATIO
            TapY = RowCeiling + ScreenHeight * CUSTOMER_ROW_ARROW_Y_OFFSET_RATIO
        }

        if (RowItem.CallBounds != null && TapY >= RowItem.CallBounds.top) {
            DiagnosticWarning(
                EventName = "CUSTOMER_OPEN_BLOCKED",
                MessageText = "customer=${RowItem.NameText} tapY=$TapY would hit " +
                        "$CUSTOMER_CALL_LABEL at ${RowItem.CallBounds.top}; scrolling instead"
            )
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                ScrollCustomerDashboard()
            }
            return
        }
        if (RowItem.CallBounds == null && SelectedArrow == null) {
            DiagnosticWarning(
                EventName = "CUSTOMER_OPEN_BLOCKED",
                MessageText = "customer=${RowItem.NameText} has no $CUSTOMER_CALL_LABEL anchor " +
                        "and no arrow; refusing a blind tap"
            )
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                ScrollCustomerDashboard()
            }
            return
        }

        ActiveCustomerName = RowItem.NameText
        OcrAttemptedKinds.clear()
        PendingOcrLines = null
        OcrInFlight = false
        ProcessedCustomerKeys.add(CustomerKey(NameText = RowItem.NameText))
        CustomerOpenAttempts++
        CustomerStageValue = CustomerStage.OPENING_CUSTOMER
        RefreshBubble()

        val TapAccepted = PerformTapGesture(XPos = TapX, YPos = TapY)
        DiagnosticInfo(
            EventName = "CUSTOMER_OPEN",
            MessageText = "customer=${RowItem.NameText} x=$TapX y=$TapY " +
                    "arrow=${SelectedArrow != null} accepted=$TapAccepted " +
                    "page=$CustomerCurrentPage/$CustomerTotalPages target=$TargetCustomerPage"
        )
        ScheduleCustomerAction(DelayMs = CUSTOMER_DETAIL_OPEN_DELAY_MS) {
            WaitForCustomerDetailScreen()
        }
    }

    private fun AdvanceCustomerDashboard(
        TextNodes: List<Pair<String, Rect>>,
        VisibleRowCount: Int
    ) {
        val CurrentSignature = TextNodes
            .joinToString(separator = "\u0001") { NodePair -> NodePair.first }
            .hashCode()
        val HasStalled = CurrentSignature == LatestCustomerVisibleSignature
        LatestCustomerVisibleSignature = CurrentSignature

        if (HasStalled) {
            CustomerScrollStallCount++
        } else {
            CustomerScrollStallCount = 0
        }

        val ReachedScrollLimit = CustomerScrollAttempts >= CUSTOMER_DASHBOARD_SCROLL_LIMIT
        val ReachedStallLimit = CustomerScrollStallCount >= CUSTOMER_SCROLL_STALL_LIMIT
        if (!ReachedScrollLimit && !ReachedStallLimit) {
            ScrollCustomerDashboard()
            return
        }

        DiagnosticInfo(
            EventName = "CUSTOMER_PAGE_DONE",
            MessageText = "page=$CustomerCurrentPage/$CustomerTotalPages " +
                    "target=$TargetCustomerPage rowsVisible=$VisibleRowCount " +
                    "scrolls=$CustomerScrollAttempts stalls=$CustomerScrollStallCount " +
                    "processed=${ProcessedCustomerKeys.size}"
        )
        BeginNextCustomerPage()
    }

    private fun ScrollCustomerDashboard() {
        CustomerScrollAttempts++
        CustomerStageValue = CustomerStage.DASHBOARD
        PerformPolicyScroll(ForwardVal = true, PreferAccessibilityAction = false)
        ScheduleCustomerAction(DelayMs = CUSTOMER_SCROLL_SETTLE_MS) { RunCustomerDashboardStep() }
    }

    private fun BeginNextCustomerPage() {
        SaveCustomerResumeProgress(IsCompleteVal = false)
        if (CustomerTotalPages in 1..TargetCustomerPage) {
            CompleteCustomerAutomation(ReasonText = "last customer page reached")
            return
        }
        TargetCustomerPage += 1
        CustomerScrollAttempts = 0
        CustomerScrollStallCount = 0
        LatestCustomerVisibleSignature = 0
        ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
            ReturnToCustomerPageSelector(ScrollCount = 0)
        }
    }

    private fun ReturnToCustomerPageSelector(ScrollCount: Int) {
        if (ScrollCount >= CUSTOMER_RETURN_TO_TOP_LIMIT) {
            FailCustomerAutomation(ReasonText = "page selector never came back into view")
            return
        }
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        if (IsCustomerPageSelectorVisible()) {
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                OpenCustomerPageSelector()
            }
            return
        }
        PerformPolicyScroll(ForwardVal = false, PreferAccessibilityAction = false)
        ScheduleCustomerAction(DelayMs = CUSTOMER_SCROLL_SETTLE_MS) {
            ReturnToCustomerPageSelector(ScrollCount = ScrollCount + 1)
        }
    }

    private fun IsCustomerPageSelectorVisible(): Boolean {
        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            ?: return false
        return try {
            CustomerPageChipBounds(RootNode = RootNode) != null
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun IsPageLabel(NodeText: String, Labels: Set<String>): Boolean {
        val TrimmedText = NodeText.trim()
        return Labels.any { LabelText ->
            TrimmedText == LabelText || TrimmedText.startsWith("$LabelText ")
        }
    }

    private fun CustomerPageChipBounds(RootNode: AccessibilityNodeInfo): Rect? {
        val ScreenWidth = resources.displayMetrics.widthPixels
        val ScreenHeight = resources.displayMetrics.heightPixels
        val CandidateLabels = setOf(
            CustomerCurrentPage.toString().padStart(2, '0'),
            CustomerCurrentPage.toString()
        )
        return CollectVisibleTextNodes(RootNode = RootNode)
            .filter { NodePair -> IsPageLabel(NodeText = NodePair.first, Labels = CandidateLabels) }
            .filter { NodePair ->
                NodePair.second.centerX() > ScreenWidth * 0.55f &&
                        NodePair.second.centerY() < ScreenHeight * 0.35f
            }
            .minByOrNull { NodePair -> NodePair.second.top }
            ?.second
    }

    private fun OpenCustomerPageSelector() {
        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
        if (RootNode == null) {
            RetryCustomerPageNavigation(ReasonText = "no root for the page selector")
            return
        }
        val ChipBounds = try {
            CustomerPageChipBounds(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
        if (ChipBounds == null) {
            RetryCustomerPageNavigation(ReasonText = "page chip not visible")
            return
        }
        CustomerPageChipRect = Rect(ChipBounds)
        val OpenOptionNodes = CustomerPageOptionNodes(ChipBounds = ChipBounds)
        val OpenOptionCount = OpenOptionNodes.size
        if (IsCustomerPageListOpen(OptionNodes = OpenOptionNodes)) {
            DiagnosticInfo(
                EventName = "CUSTOMER_PAGE_SELECTOR",
                MessageText = "page=$CustomerCurrentPage bounds=$ChipBounds " +
                        "alreadyOpen=$OpenOptionCount; not re-tapping the chip"
            )
            ScheduleCustomerAction(DelayMs = POLICY_PAGE_SELECTOR_DELAY_MS) {
                SelectNextCustomerPage()
            }
            return
        }
        val TapAccepted = PerformTapGesture(
            XPos = ChipBounds.centerX().toFloat(),
            YPos = ChipBounds.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "CUSTOMER_PAGE_SELECTOR",
            MessageText = "page=$CustomerCurrentPage bounds=$ChipBounds accepted=$TapAccepted"
        )
        ScheduleCustomerAction(DelayMs = POLICY_PAGE_SELECTOR_DELAY_MS) { SelectNextCustomerPage() }
    }

    private fun IsCustomerPageOptionBounds(OptionBounds: Rect, ChipBounds: Rect): Boolean {
        val MinWidth = (ChipBounds.width() * CUSTOMER_PAGE_OPTION_MIN_WIDTH_RATIO).toInt()
        return OptionBounds.top >= ChipBounds.bottom &&
                abs(OptionBounds.centerX() - ChipBounds.centerX()) <= ChipBounds.width() / 2 &&
                OptionBounds.width() >= MinWidth
    }

    private fun CustomerPageOptionOffset(OptionBounds: Rect, ChipBounds: Rect): Int {
        return OptionBounds.top - ChipBounds.bottom
    }

    private fun CustomerPageOptionViewportPx(ChipBounds: Rect): Int {
        val DisplayMetricsObj = resources.displayMetrics
        val AssumedPx =
            (DisplayMetricsObj.heightPixels * CUSTOMER_PAGE_OPTION_VIEWPORT_RATIO).toInt()
        val MinimumPx = (CUSTOMER_PAGE_OPTION_MIN_VIEWPORT_DP * DisplayMetricsObj.density).toInt()
        val ToScreenBottom = DisplayMetricsObj.heightPixels - ChipBounds.bottom
        return maxOf(MinimumPx, minOf(AssumedPx, ToScreenBottom))
    }

    private fun IsCustomerPageOptionReachable(OptionBounds: Rect, ChipBounds: Rect): Boolean {
        val OffsetPx = CustomerPageOptionOffset(
            OptionBounds = OptionBounds,
            ChipBounds = ChipBounds
        )
        if (CustomerPageOptionDeadOffset in 1..OffsetPx) return false
        return OffsetPx <= CustomerPageOptionViewportPx(ChipBounds = ChipBounds)
    }

    private fun LongestConsecutiveRun(ValueList: List<Int>): Int {
        val SortedValues = ValueList.distinct().sorted()
        var BestRun = 0
        var CurrentRun = 0
        var PreviousValue = Int.MIN_VALUE
        for (ValueItem in SortedValues) {
            CurrentRun = if (ValueItem == PreviousValue + 1) CurrentRun + 1 else 1
            if (CurrentRun > BestRun) BestRun = CurrentRun
            PreviousValue = ValueItem
        }
        return BestRun
    }

    private fun IsCustomerPageListOpen(OptionNodes: List<Pair<String, Rect>>): Boolean {
        val ValueList = OptionNodes.mapNotNull { NodePair -> NodePair.first.trim().toIntOrNull() }
        val RequiredRun = minOf(
            CUSTOMER_PAGE_LIST_OPEN_MIN_RUN,
            maxOf(2, CustomerTotalPages)
        )
        return LongestConsecutiveRun(ValueList = ValueList) >= RequiredRun
    }

    private fun CustomerPageOptionNodes(ChipBounds: Rect): List<Pair<String, Rect>> {
        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            ?: return emptyList()
        return try {
            CollectVisibleTextNodes(RootNode = RootNode)
                .filter { NodePair ->
                    CUSTOMER_PAGE_OPTION_REGEX.matches(NodePair.first.trim())
                }
                .filter { NodePair -> IsBoundsOnScreen(BoundsObj = NodePair.second) }
                .filter { NodePair ->
                    IsCustomerPageOptionBounds(
                        OptionBounds = NodePair.second,
                        ChipBounds = ChipBounds
                    )
                }
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun ResetCustomerSelectorScrollState() {
        ResetPolicySelectorScrollState()
        CustomerPageOptionSignature = ""
    }

    private fun SelectNextCustomerPage() {
        val ChipBounds = CustomerPageChipRect
        if (ChipBounds == null) {
            RetryCustomerPageNavigation(ReasonText = "page chip bounds unknown")
            return
        }
        val OptionNodes = CustomerPageOptionNodes(ChipBounds = ChipBounds)
        if (!IsCustomerPageListOpen(OptionNodes = OptionNodes)) {
            RetryCustomerPageNavigation(
                ReasonText = "page list did not open (options=${OptionNodes.size})"
            )
            return
        }
        val OptionLabels = setOf(
            TargetCustomerPage.toString().padStart(2, '0'),
            TargetCustomerPage.toString()
        )
        val OptionBounds = OptionNodes
            .filter { NodePair -> IsPageLabel(NodeText = NodePair.first, Labels = OptionLabels) }
            .minByOrNull { NodePair -> NodePair.second.top }
            ?.second
        val ViewportPx = CustomerPageOptionViewportPx(ChipBounds = ChipBounds)
        if (OptionBounds == null ||
            !IsCustomerPageOptionReachable(OptionBounds = OptionBounds, ChipBounds = ChipBounds)
        ) {
            val UnreachableOffset = OptionBounds?.let { BoundsObj ->
                CustomerPageOptionOffset(OptionBounds = BoundsObj, ChipBounds = ChipBounds)
            }
            val HighestVisible = OptionNodes
                .mapNotNull { NodePair -> NodePair.first.trim().toIntOrNull() }
                .maxOrNull() ?: 0
            val ScrollForward = UnreachableOffset != null || HighestVisible < TargetCustomerPage
            if (ScrollCustomerPageOptionList(
                    OptionNodes = OptionNodes,
                    ChipBounds = ChipBounds,
                    ForwardVal = ScrollForward
                )
            ) {
                ScheduleCustomerAction(DelayMs = POLICY_SELECTOR_SCROLL_SETTLE_MS) {
                    SelectNextCustomerPage()
                }
                return
            }
            ResetCustomerSelectorScrollState()
            if (OptionBounds == null ||
                !IsBoundsOnScreen(BoundsObj = OptionBounds) ||
                CustomerPageOptionDeadOffset in 1..CustomerPageOptionOffset(
                    OptionBounds = OptionBounds,
                    ChipBounds = ChipBounds
                )
            ) {
                RetryCustomerPageNavigation(
                    ReasonText = if (UnreachableOffset == null) {
                        "option $TargetCustomerPage missing from " +
                                "${OptionNodes.size} open option(s)"
                    } else {
                        "option $TargetCustomerPage sits ${UnreachableOffset}px below the " +
                                "chip, past the ${ViewportPx}px the list actually draws"
                    }
                )
                return
            }
            DiagnosticWarning(
                EventName = "CUSTOMER_PAGE_OPTION_UNVERIFIED",
                MessageText = "target=$TargetCustomerPage offset=${UnreachableOffset}px is past " +
                        "the ${ViewportPx}px viewport but the option column will not scroll; " +
                        "tapping it anyway and checking the result"
            )
        } else {
            ResetCustomerSelectorScrollState()
        }
        val OffsetPx = CustomerPageOptionOffset(
            OptionBounds = OptionBounds,
            ChipBounds = ChipBounds
        )
        CustomerPageOptionPendingOffset = OffsetPx
        val TapAccepted = PerformTapGesture(
            XPos = OptionBounds.centerX().toFloat(),
            YPos = OptionBounds.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "CUSTOMER_PAGE_OPTION",
            MessageText = "target=$TargetCustomerPage bounds=$OptionBounds offset=$OffsetPx " +
                    "viewport=$ViewportPx deadOffset=$CustomerPageOptionDeadOffset " +
                    "accepted=$TapAccepted"
        )
        ScheduleCustomerAction(DelayMs = CUSTOMER_PAGE_LOAD_DELAY_MS) { WaitForCustomerPageLoad() }
    }

    private fun SwipeCustomerPageOptionColumn(BoundsObj: Rect, ForwardVal: Boolean): Boolean {
        CollapseBubbleForGesture()
        val ColumnHeight = BoundsObj.height().toFloat()
        val MinimumHeight = CUSTOMER_PAGE_OPTION_MIN_VIEWPORT_DP * resources.displayMetrics.density
        if (ColumnHeight < MinimumHeight) return false

        val StartXVal = BoundsObj.centerX().toFloat()
        val NearTop = BoundsObj.top + ColumnHeight * 0.12f
        val NearBottom = BoundsObj.bottom - ColumnHeight * 0.12f
        if (NearBottom <= NearTop) return false

        val ScrollPath = Path().apply {
            moveTo(StartXVal, if (ForwardVal) NearBottom else NearTop)
            lineTo(StartXVal, if (ForwardVal) NearTop else NearBottom)
        }
        val GestureObj = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    ScrollPath,
                    0,
                    POLICY_SELECTOR_SCROLL_DURATION_MS
                )
            )
            .build()
        return try {
            dispatchGesture(GestureObj, null, null)
        } catch (ExceptionObj: Exception) {
            DiagnosticWarning(
                EventName = "CUSTOMER_PAGE_OPTION_SCROLL_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: " +
                        ExceptionObj.message.orEmpty()
            )
            false
        }
    }

    private fun ScrollCustomerPageOptionList(
        OptionNodes: List<Pair<String, Rect>>,
        ChipBounds: Rect,
        ForwardVal: Boolean
    ): Boolean {
        if (PolicySelectorScrollCount >= POLICY_SELECTOR_SCROLL_LIMIT) return false
        if (OptionNodes.size < POLICY_SELECTOR_OPTION_MIN_COUNT) return false

        val ViewportPx = CustomerPageOptionViewportPx(ChipBounds = ChipBounds)
        val DrawnNodes = OptionNodes.filter { NodePair ->
            CustomerPageOptionOffset(
                OptionBounds = NodePair.second,
                ChipBounds = ChipBounds
            ) <= ViewportPx
        }
        if (DrawnNodes.size < POLICY_SELECTOR_OPTION_MIN_COUNT) return false

        val ColumnBounds = Rect()
        for (NodePair in DrawnNodes) ColumnBounds.union(NodePair.second)
        if (ColumnBounds.isEmpty) return false

        val CurrentSignature = DrawnNodes
            .map { NodePair -> NodePair.first.trim() }
            .sorted()
            .joinToString(separator = ",")
        if (CurrentSignature == CustomerPageOptionSignature) {
            PolicySelectorScrollStalls++
        } else {
            PolicySelectorScrollStalls = 0
            CustomerPageOptionSignature = CurrentSignature
        }
        if (PolicySelectorScrollStalls >= POLICY_SCROLL_STALL_LIMIT) {
            DiagnosticWarning(
                EventName = "CUSTOMER_PAGE_OPTION_STALLED",
                MessageText = "target=$TargetCustomerPage drawn=${DrawnNodes.size} " +
                        "bounds=$ColumnBounds; the option column will not move"
            )
            return false
        }

        val ScrollAccepted = SwipeCustomerPageOptionColumn(
            BoundsObj = ColumnBounds,
            ForwardVal = ForwardVal
        )
        PolicySelectorScrollCount++
        DiagnosticInfo(
            EventName = "CUSTOMER_PAGE_OPTION_SCROLL",
            MessageText = "target=$TargetCustomerPage attempt=$PolicySelectorScrollCount " +
                    "direction=${if (ForwardVal) "down" else "up"} " +
                    "drawn=${DrawnNodes.size} of ${OptionNodes.size} " +
                    "viewport=${ViewportPx}px bounds=$ColumnBounds accepted=$ScrollAccepted"
        )
        return ScrollAccepted
    }

    private fun WaitForCustomerPageLoad() {
        val VisibleNodes = CurrentScreenNodes()
        val PageInfo = ParsePolicyPageInfo(VisibleNodes = VisibleNodes)
        if (PageInfo != null && PageInfo.first == TargetCustomerPage) {
            CustomerCurrentPage = PageInfo.first
            CustomerTotalPages = PageInfo.second
            CustomerPageRetryCount = 0
            CustomerPageWaitCount = 0
            CustomerScrollAttempts = 0
            CustomerScrollStallCount = 0
            CustomerPageOptionPendingOffset = 0
            CustomerPageOptionDeadTaps = 0
            LatestCustomerVisibleSignature = 0
            DiagnosticInfo(
                EventName = "CUSTOMER_PAGE_LOADED",
                MessageText = "page=$CustomerCurrentPage/$CustomerTotalPages"
            )
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                RunCustomerDashboardStep()
            }
            return
        }
        val PendingOffset = CustomerPageOptionPendingOffset
        CustomerPageOptionPendingOffset = 0
        val ChipBounds = CustomerPageChipRect
        if (PendingOffset > 0 &&
            ChipBounds != null &&
            CustomerPageOptionDeadTaps < CUSTOMER_PAGE_OPTION_DEAD_TAP_LIMIT &&
            IsCustomerPageListOpen(OptionNodes = CustomerPageOptionNodes(ChipBounds = ChipBounds))
        ) {
            CustomerPageOptionDeadTaps++
            if (CustomerPageOptionDeadOffset == 0 ||
                PendingOffset < CustomerPageOptionDeadOffset
            ) {
                CustomerPageOptionDeadOffset = PendingOffset
            }
            DiagnosticWarning(
                EventName = "CUSTOMER_PAGE_OPTION_DEAD",
                MessageText = "target=$TargetCustomerPage offset=${PendingOffset}px changed " +
                        "nothing and the option list is still open; the list draws fewer rows " +
                        "than the tree reports. reachable<${CustomerPageOptionDeadOffset}px " +
                        "attempt=$CustomerPageOptionDeadTaps"
            )
            ScheduleCustomerAction(DelayMs = POLICY_SELECTOR_SCROLL_SETTLE_MS) {
                SelectNextCustomerPage()
            }
            return
        }
        RetryCustomerPageNavigation(
            ReasonText = "expected page $TargetCustomerPage, saw ${PageInfo?.first}"
        )
    }

    private fun RetryCustomerPageNavigation(ReasonText: String) {
        CustomerPageRetryCount++
        DiagnosticWarning(
            EventName = "CUSTOMER_PAGE_RETRY",
            MessageText = "attempt=$CustomerPageRetryCount reason=$ReasonText"
        )
        if (CustomerPageRetryCount >= CUSTOMER_PAGE_RETRY_LIMIT) {
            FailCustomerAutomation(ReasonText = "page navigation failed: $ReasonText")
            return
        }
        ScheduleCustomerAction(DelayMs = CUSTOMER_PAGE_LOAD_DELAY_MS) {
            ReturnToCustomerPageSelector(ScrollCount = 0)
        }
    }

    private fun HandleCustomerDetailScreen(
        RootNode: AccessibilityNodeInfo,
        VisibleNodes: List<String>
    ) {
        if (CustomerStageValue != CustomerStage.DASHBOARD &&
            CustomerStageValue != CustomerStage.IDLE
        ) {
            return
        }
        DiagnosticWarning(
            EventName = "CUSTOMER_DETAIL_UNEXPECTED",
            MessageText = "stage=$CustomerStageValue customer=$ActiveCustomerName; backing out"
        )
        CustomerStageValue = CustomerStage.RETURNING
        ScheduleCustomerAction(DelayMs = CUSTOMER_RETURN_DELAY_MS) {
            ReturnToCustomerDashboard(AttemptCount = 0)
        }
    }

    private fun WaitForCustomerDetailScreen() {
        val VisibleNodes = CurrentScreenNodes()
        if (IsCustomerDetailScreen(VisibleNodes = VisibleNodes)) {
            CustomerOpenAttempts = 0
            ActiveProfile = null
            ProfilePaneNodes.clear()
            PendingSheetKinds.clear()
            ProfileSweepCount = 0
            LastProfileSweepSignature = 0
            CustomerStageValue = CustomerStage.READING_POLICIES
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                ReadCustomerPolicies(SweepCount = 0, CollectedNumbers = emptySet())
            }
            return
        }
        if (CustomerRoute.IsDriving) {
            if (CustomerOpenAttempts < CUSTOMER_OPEN_RETRY_LIMIT) {
                CustomerOpenAttempts++
                ScheduleCustomerAction(DelayMs = CUSTOMER_DETAIL_OPEN_DELAY_MS) {
                    WaitForCustomerDetailScreen()
                }
                return
            }
            CustomerOpenAttempts = 0
            CustomerRoute.OnDetailOpenFailed()
            return
        }
        if (CustomerOpenAttempts >= CUSTOMER_OPEN_RETRY_LIMIT) {
            DiagnosticWarning(
                EventName = "CUSTOMER_OPEN_FAILED",
                MessageText = "customer=$ActiveCustomerName did not open after " +
                        "$CustomerOpenAttempts attempts; skipping"
            )
            CustomerOpenAttempts = 0
            CustomerStageValue = CustomerStage.DASHBOARD
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                RunCustomerDashboardStep()
            }
            return
        }
        DiagnosticWarning(
            EventName = "CUSTOMER_OPEN_RETRY",
            MessageText = "customer=$ActiveCustomerName attempt=$CustomerOpenAttempts"
        )
        CustomerStageValue = CustomerStage.DASHBOARD
        ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) { RunCustomerDashboardStep() }
    }

    private fun ReadCustomerPolicies(SweepCount: Int, CollectedNumbers: Set<String>) {
        val VisibleNodes = CurrentScreenNodes()
        val FoundNumbers = CollectedNumbers +
                CustomerProfileParser.ParsePolicyNumbers(Nodes = VisibleNodes)

        val DeclaredCount = CustomerPolicyCount(VisibleNodes = VisibleNodes)
        val NeedsMore = DeclaredCount > 0 && FoundNumbers.size < DeclaredCount
        if (NeedsMore && SweepCount < CUSTOMER_PROFILE_SCROLL_LIMIT) {
            PerformPolicyScroll(ForwardVal = true, PreferAccessibilityAction = false)
            ScheduleCustomerAction(DelayMs = CUSTOMER_PROFILE_SWEEP_SETTLE_MS) {
                ReadCustomerPolicies(SweepCount = SweepCount + 1, CollectedNumbers = FoundNumbers)
            }
            return
        }

        ActiveCustomerPolicyNumbers = FoundNumbers.toList()
        ActiveCustomerRelevantNumbers = ActiveCustomerPolicyNumbers
            .filter { NumberText -> SessionPolicyNumbers.contains(NumberText) }

        val GapNumbers = ActiveCustomerPolicyNumbers
            .filterNot { NumberText -> SessionPolicyNumbers.contains(NumberText) }
        for (GapNumber in GapNumbers) {
            if (SessionGapMap.containsKey(GapNumber)) continue
            SessionGapMap[GapNumber] = SessionGap(
                PolicyNumber = GapNumber,
                CustomerName = ActiveCustomerName,
                SeenAt = System.currentTimeMillis()
            )
            DiagnosticInfo(
                EventName = "CUSTOMER_POLICY_GAP",
                MessageText = "policy=$GapNumber customer=$ActiveCustomerName " +
                        "not captured in session=$CurrentSessionId"
            )
        }

        DiagnosticInfo(
            EventName = "CUSTOMER_POLICIES",
            MessageText = "customer=$ActiveCustomerName declared=$DeclaredCount " +
                    "found=${ActiveCustomerPolicyNumbers.size} " +
                    "relevant=${ActiveCustomerRelevantNumbers.size} gaps=${GapNumbers.size}"
        )

        val OutstandingNumbers = ActiveCustomerRelevantNumbers.filterNot { NumberText ->
            FilledPolicyNumbers.contains(NumberText)
        }
        if (!RevisitFilledEnabled &&
            ActiveCustomerRelevantNumbers.isNotEmpty() &&
            OutstandingNumbers.isEmpty()
        ) {
            DiagnosticInfo(
                EventName = "CUSTOMER_ALREADY_FILLED",
                MessageText = "customer=$ActiveCustomerName all " +
                        "${ActiveCustomerRelevantNumbers.size} policy(ies) already hold personal " +
                        "details; not opening the profile"
            )
            MarkCustomerVisited(NameText = ActiveCustomerName)
            CustomerStageValue = CustomerStage.RETURNING
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                ReturnToCustomerDashboard(AttemptCount = 0)
            }
            return
        }

        if (ActiveCustomerRelevantNumbers.isEmpty()) {
            DiagnosticInfo(
                EventName = "CUSTOMER_SKIPPED",
                MessageText = "customer=$ActiveCustomerName has no policy in this session"
            )
            MarkCustomerVisited(NameText = ActiveCustomerName)
            CustomerStageValue = CustomerStage.RETURNING
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                ReturnToCustomerDashboard(AttemptCount = 0)
            }
            return
        }

        CustomerStageValue = CustomerStage.OPENING_PROFILE
        ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
            ScrollDetailToTop(AttemptCount = 0) { TapProfileTab() }
        }
    }

    private fun CustomerPolicyCount(VisibleNodes: List<String>): Int {
        val CombinedText = VisibleNodes.joinToString(separator = " ")
        val CountMatch = Regex("(?i)(\\d{1,3})\\s*Policy\\(ies\\)").find(CombinedText)
        val DeclaredCount = CountMatch?.groupValues?.get(1)?.toIntOrNull()
        if (DeclaredCount != null && DeclaredCount > 0) return DeclaredCount

        for (LabelIndex in VisibleNodes.indices) {
            if (!VisibleNodes[LabelIndex].trim().equals("Policies", ignoreCase = true)) continue
            val NextText = VisibleNodes.getOrNull(LabelIndex + 1)?.trim().orEmpty()
            if (!NextText.matches(Regex("^\\d{1,3}$"))) continue
            val CountValue = NextText.toIntOrNull() ?: continue
            if (CountValue > 0) return CountValue
        }
        return 0
    }

    private fun TapProfileTab() {
        val TextNodes = CurrentBoundsNodes()
        val TabBounds = ProfileTabBounds(TextNodes = TextNodes)
        if (TabBounds == null) {
            DiagnosticWarning(
                EventName = "CUSTOMER_PROFILE_TAB_CANDIDATES",
                MessageText = "customer=$ActiveCustomerName onScreenNodes=${TextNodes.size}; " +
                        "scrolling back towards the tab strip"
            )
            PerformPolicyScroll(ForwardVal = false, PreferAccessibilityAction = false)
            RetryCustomerStep(ReasonText = "Profile tab not visible")
            return
        }
        val TapAccepted = PerformTapGesture(
            XPos = TabBounds.centerX().toFloat(),
            YPos = TabBounds.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "CUSTOMER_PROFILE_TAB",
            MessageText = "customer=$ActiveCustomerName bounds=$TabBounds accepted=$TapAccepted"
        )
        CustomerStageValue = CustomerStage.READING_PROFILE
        CustomerStepAttempts = 0
        ScheduleCustomerAction(DelayMs = CUSTOMER_PROFILE_TAB_DELAY_MS) { SweepProfilePane() }
    }

    private fun SweepProfilePane() {
        val VisibleNodes = CurrentScreenNodesWithDescriptions()
        if (VisibleSheetKind(VisibleNodes = VisibleNodes) != null) {
            DiagnosticWarning(
                EventName = "CUSTOMER_PROFILE_SWEEP_BLOCKED",
                MessageText = "customer=$ActiveCustomerName a sheet is covering the pane; " +
                        "not folding it into the profile read"
            )
            ScheduleCustomerAction(DelayMs = CUSTOMER_PROFILE_SWEEP_SETTLE_MS) { SweepProfilePane() }
            return
        }
        ProfilePaneNodes.addAll(VisibleNodes)
        ProfileSweepCount++

        val CurrentSignature = VisibleNodes.joinToString(separator = "\u0001").hashCode()
        val HasStalled = CurrentSignature == LastProfileSweepSignature
        LastProfileSweepSignature = CurrentSignature

        val CollectedNodes = ProfilePaneNodes.toList()
        val IsComplete = CustomerProfileParser.IsProfilePaneComplete(Nodes = CollectedNodes)
        val ReachedLimit = ProfileSweepCount >= CUSTOMER_PROFILE_SCROLL_LIMIT

        if (!IsComplete && !ReachedLimit && !HasStalled) {
            PerformPolicyScroll(ForwardVal = true, PreferAccessibilityAction = false)
            ScheduleCustomerAction(DelayMs = CUSTOMER_PROFILE_SWEEP_SETTLE_MS) { SweepProfilePane() }
            return
        }

        if (!CustomerProfileParser.IsProfilePane(Nodes = CollectedNodes)) {
            DiagnosticWarning(
                EventName = "CUSTOMER_PROFILE_MISSING",
                MessageText = "customer=$ActiveCustomerName never showed a profile pane " +
                        "after $ProfileSweepCount sweeps"
            )
            FinishActiveCustomer()
            return
        }

        ActiveProfile = CustomerProfileParser.ParseProfilePane(
            Nodes = CollectedNodes,
            CustomerNameVal = ActiveCustomerName
        )
        PendingSheetKinds.clear()
        ExpectedSheetCounts.clear()
        for (KindVal in CustomerProfileParser.ContactKind.values()) {
            ExpectedSheetCounts[KindVal] = CustomerProfileParser.ExpectedValueCount(
                Nodes = CollectedNodes,
                KindVal = KindVal
            )
        }
        if (CustomerProfileParser.NeedsSheet(
                Nodes = CollectedNodes,
                LabelText = CustomerProfileParser.LABEL_MOBILE
            )
        ) {
            PendingSheetKinds.add(CustomerProfileParser.ContactKind.MOBILE)
        }
        if (CustomerProfileParser.NeedsSheet(
                Nodes = CollectedNodes,
                LabelText = CustomerProfileParser.LABEL_EMAIL
            )
        ) {
            PendingSheetKinds.add(CustomerProfileParser.ContactKind.EMAIL)
        }
        if (CustomerProfileParser.NeedsSheet(
                Nodes = CollectedNodes,
                LabelText = CustomerProfileParser.LABEL_ADDRESS
            )
        ) {
            PendingSheetKinds.add(CustomerProfileParser.ContactKind.ADDRESS)
        }

        val PartialCount = listOf(
            ActiveProfile?.Emails.orEmpty(),
            ActiveProfile?.Addresses.orEmpty(),
            ActiveProfile?.Mobiles.orEmpty()
        ).flatten().count { ValueItem -> ValueItem.IsPartial }
        DiagnosticInfo(
            EventName = "CUSTOMER_PROFILE_READ",
            MessageText = "customer=$ActiveCustomerName sweeps=$ProfileSweepCount " +
                    "complete=$IsComplete fields=${ActiveProfile?.FieldCount ?: 0} " +
                    "partialValues=$PartialCount paneNodes=${CollectedNodes.size} " +
                    "email=${ActiveProfile?.Emails?.firstOrNull()?.Value.orEmpty()} " +
                    "sheetsPending=${PendingSheetKinds.map { KindVal -> KindVal.name }} " +
                    "expected=[" + ExpectedSheetCounts.entries.joinToString(", ") { EntryVal ->
                        "${EntryVal.key.name}=${EntryVal.value}"
                    } + "]"
        )
        if (PendingSheetKinds.isEmpty()) {
            FinishActiveCustomer()
            return
        }
        ScrollDetailToTop(AttemptCount = 0) { OpenNextContactSheet() }
    }

    private fun OpenNextContactSheet() {
        val NextKind = PendingSheetKinds.firstOrNull()
        if (NextKind == null) {
            FinishActiveCustomer()
            return
        }
        val DeadKinds = EmptySheetKindCounts.filterValues { CountVal ->
            CountVal >= CUSTOMER_EMPTY_SHEET_LIMIT
        }.keys
        if (!SheetsEverYieldedValues && DeadKinds.isNotEmpty()) {
            val SkippedKinds = PendingSheetKinds.filter { KindVal -> DeadKinds.contains(KindVal) }
            if (SkippedKinds.isNotEmpty()) {
                DiagnosticInfo(
                    EventName = "CUSTOMER_SHEETS_DISABLED",
                    MessageText = "customer=$ActiveCustomerName " +
                            "skipping ${SkippedKinds.joinToString(",") { KindVal -> KindVal.name }}: " +
                            "each exposed no values twice this run, so the inline value is the " +
                            "best this app gives; other field kinds are still tried"
                )
                PendingSheetKinds.removeAll(SkippedKinds)
            }
            if (PendingSheetKinds.isEmpty()) {
                FinishActiveCustomer()
                return
            }
        }
        val LabelText = when (NextKind) {
            CustomerProfileParser.ContactKind.MOBILE -> CustomerProfileParser.LABEL_MOBILE
            CustomerProfileParser.ContactKind.EMAIL -> CustomerProfileParser.LABEL_EMAIL
            CustomerProfileParser.ContactKind.ADDRESS -> CustomerProfileParser.LABEL_ADDRESS
        }
        val LinkBounds = FindViewAllBounds(LabelText = LabelText)
        if (LinkBounds == null) {
            if (SheetLinkRetryCount < CUSTOMER_SHEET_LINK_RETRY_LIMIT) {
                SheetLinkRetryCount++
                DiagnosticInfo(
                    EventName = "CUSTOMER_SHEET_LINK_SEARCH",
                    MessageText = "customer=$ActiveCustomerName field=$LabelText " +
                            "link not on screen; scrolling back attempt=$SheetLinkRetryCount"
                )
                PerformPolicyScroll(ForwardVal = false, PreferAccessibilityAction = false)
                ScheduleCustomerAction(DelayMs = CUSTOMER_PROFILE_SWEEP_SETTLE_MS) {
                    OpenNextContactSheet()
                }
                return
            }
            DiagnosticWarning(
                EventName = "CUSTOMER_SHEET_LINK_MISSING",
                MessageText = "customer=$ActiveCustomerName field=$LabelText " +
                        "has no reachable ${CustomerProfileParser.LINK_VIEW_ALL} link; " +
                        "keeping the inline value"
            )
            SheetLinkRetryCount = 0
            PendingSheetKinds.removeAt(0)
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) { OpenNextContactSheet() }
            return
        }
        SheetLinkRetryCount = 0

        ActiveSheetKind = NextKind
        SheetReadRetryCount = 0
        SheetOpenedAt = System.currentTimeMillis()
        CustomerStageValue = CustomerStage.OPENING_SHEET
        val TapAccepted = PerformTapGesture(
            XPos = LinkBounds.centerX().toFloat(),
            YPos = LinkBounds.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "CUSTOMER_SHEET_OPEN",
            MessageText = "customer=$ActiveCustomerName field=$LabelText " +
                    "bounds=$LinkBounds accepted=$TapAccepted"
        )
        ScheduleCustomerAction(DelayMs = CUSTOMER_SHEET_OPEN_DELAY_MS) { VerifySheetOpened() }
    }

    private fun FindViewAllBounds(LabelText: String): Rect? {
        val ScreenWidth = resources.displayMetrics.widthPixels
        val RootNode = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
            ?: return null
        return try {
            val TextNodes = CollectVisibleTextNodes(RootNode = RootNode)
            val LabelBounds = TextNodes
                .firstOrNull { NodePair -> NodePair.first.trim().equals(LabelText, true) }
                ?.second
                ?: return null
            val NextLabelTop = TextNodes
                .filter { NodePair -> IsProfileFieldLabel(NodeText = NodePair.first) }
                .map { NodePair -> NodePair.second }
                .filter { BoundsObj -> BoundsObj.top > LabelBounds.top }
                .minByOrNull { BoundsObj -> BoundsObj.top }
                ?.top
                ?: Int.MAX_VALUE

            TextNodes
                .filter { NodePair ->
                    NodePair.first.trim().equals(CustomerProfileParser.LINK_VIEW_ALL, true)
                }
                .map { NodePair -> NodePair.second }
                .filter { BoundsObj ->
                    BoundsObj.top > LabelBounds.top &&
                            BoundsObj.top < NextLabelTop &&
                            BoundsObj.centerX() < ScreenWidth * PROFILE_TAP_MAX_X_RATIO
                }
                .minByOrNull { BoundsObj -> BoundsObj.top }
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }


    private fun ShouldTryOcr(SheetKind: CustomerProfileParser.ContactKind): Boolean {
        if (!ContactOcrEnabled) return false
        if (!CustomerSheetOcr.IsSupported()) return false
        if (OcrAttemptedKinds.contains(SheetKind)) return false
        return System.currentTimeMillis() - SheetOpenedAt >= CUSTOMER_SHEET_OCR_MIN_MS
    }

    private fun StartSheetOcr(SheetKind: CustomerProfileParser.ContactKind) {
        OcrAttemptedKinds.add(SheetKind)
        OcrInFlight = true
        CustomerStageValue = CustomerStage.READING_SHEET
        DiagnosticInfo(
            EventName = "CUSTOMER_SHEET_OCR_START",
            MessageText = "customer=$ActiveCustomerName kind=${SheetKind.name}"
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            OcrInFlight = false
            return
        }

        val WatchdogRunnable = Runnable {
            if (!OcrInFlight) return@Runnable
            FinishSheetOcr(
                SheetKind = SheetKind,
                OutcomeVal = CustomerSheetOcr.Outcome.Failed(
                    Reason = "no screenshot callback within ${SHEET_OCR_TIMEOUT_MS}ms"
                )
            )
        }
        OcrWatchdogRunnable = WatchdogRunnable
        MainHandler.postDelayed(WatchdogRunnable, SHEET_OCR_TIMEOUT_MS)

        try {
            CustomerSheetOcr.ReadSheet(
                ServiceRef = this,
                ExecutorRef = OcrExecutor,
                TopFraction = SHEET_OCR_TOP_FRACTION
            ) { OutcomeVal ->
                MainHandler.post { FinishSheetOcr(SheetKind = SheetKind, OutcomeVal = OutcomeVal) }
            }
        } catch (ErrorRef: Exception) {
            FinishSheetOcr(
                SheetKind = SheetKind,
                OutcomeVal = CustomerSheetOcr.Outcome.Failed(
                    Reason = "${ErrorRef.javaClass.simpleName}: ${ErrorRef.message.orEmpty()}"
                )
            )
        }
    }

    private fun FinishSheetOcr(
        SheetKind: CustomerProfileParser.ContactKind,
        OutcomeVal: CustomerSheetOcr.Outcome
    ) {
        OcrInFlight = false
        OcrWatchdogRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        OcrWatchdogRunnable = null
        if (!IsCapturing || ActiveSheetKind != SheetKind) return

        when (OutcomeVal) {
            is CustomerSheetOcr.Outcome.Lines -> {
                DiagnosticInfo(
                    EventName = "CUSTOMER_SHEET_OCR_READ",
                    MessageText = "customer=$ActiveCustomerName kind=${SheetKind.name} " +
                            "lines=${OutcomeVal.TextLines.size} " +
                            "ocr=[" + OutcomeVal.TextLines.joinToString(" | ") + "]"
                )
                PendingOcrLines = OutcomeVal.TextLines
            }

            is CustomerSheetOcr.Outcome.Failed -> {
                DiagnosticWarning(
                    EventName = "CUSTOMER_SHEET_OCR_FAILED",
                    MessageText = "customer=$ActiveCustomerName kind=${SheetKind.name} " +
                            "reason=${OutcomeVal.Reason}"
                )
                PendingOcrLines = emptyList()
            }
        }

        HandleContactSheet(SheetKind = SheetKind, VisibleNodes = CurrentScreenNodes())
    }

    private fun DumpEmptySheet(SheetKind: CustomerProfileParser.ContactKind) {
        EmptySheetKindCounts[SheetKind] = (EmptySheetKindCounts[SheetKind] ?: 0) + 1

        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: return
        try {
            val Descriptions = mutableListOf<String>()
            CollectSheetDump(TargetNode = RootNode, ResultList = Descriptions, DepthVal = 0)
            DiagnosticWarning(
                EventName = "CUSTOMER_SHEET_DUMP",
                MessageText = "customer=$ActiveCustomerName kind=${SheetKind.name} " +
                        "nodes=${Descriptions.size} " +
                        Descriptions.take(MAX_SHEET_DUMP_NODES).joinToString(" || ")
            )
        } catch (_: Exception) {
        } finally {
            RecycleNode(NodeRef = RootNode)
        }
    }

    private fun CollectSheetDump(
        TargetNode: AccessibilityNodeInfo?,
        ResultList: MutableList<String>,
        DepthVal: Int
    ) {
        if (TargetNode == null || DepthVal > MAX_SHEET_DUMP_DEPTH) return
        if (ResultList.size >= MAX_SHEET_DUMP_NODES) return

        val TextValue = TargetNode.text?.toString().orEmpty().trim()
        val DescValue = TargetNode.contentDescription?.toString().orEmpty().trim()
        if (TextValue.isNotEmpty() || DescValue.isNotEmpty()) {
            val BoundsObj = Rect()
            TargetNode.getBoundsInScreen(BoundsObj)
            ResultList.add(
                "[${TargetNode.className}] text='$TextValue' desc='$DescValue' " +
                        "y=${BoundsObj.top}"
            )
        }
        for (ChildIndex in 0 until TargetNode.childCount) {
            val ChildNode = try {
                TargetNode.getChild(ChildIndex)
            } catch (_: Exception) {
                null
            }
            CollectSheetDump(
                TargetNode = ChildNode,
                ResultList = ResultList,
                DepthVal = DepthVal + 1
            )
            RecycleNode(NodeRef = ChildNode)
        }
    }

    private fun IsProfileFieldLabel(NodeText: String): Boolean {
        val Trimmed = NodeText.trim()
        return Trimmed.equals(CustomerProfileParser.LABEL_MOBILE, true) ||
                Trimmed.equals(CustomerProfileParser.LABEL_EMAIL, true) ||
                Trimmed.equals(CustomerProfileParser.LABEL_ADDRESS, true) ||
                Trimmed.equals(CustomerProfileParser.SECTION_PERSONAL, true)
    }

    private fun VerifySheetOpened() {
        val VisibleNodes = CurrentScreenNodes()
        if (VisibleSheetKind(VisibleNodes = VisibleNodes) != null) return

        DiagnosticWarning(
            EventName = "CUSTOMER_SHEET_MISSING",
            MessageText = "customer=$ActiveCustomerName kind=${ActiveSheetKind?.name} " +
                    "sheet never appeared; continuing with the inline value"
        )
        ActiveSheetKind = null
        if (PendingSheetKinds.isNotEmpty()) PendingSheetKinds.removeAt(0)
        CustomerStageValue = CustomerStage.READING_PROFILE
        ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) { OpenNextContactSheet() }
    }

    private fun HandleContactSheet(
        SheetKind: CustomerProfileParser.ContactKind,
        VisibleNodes: List<String>
    ) {
        if (CustomerStageValue != CustomerStage.OPENING_SHEET &&
            CustomerStageValue != CustomerStage.READING_SHEET
        ) {
            return
        }
        if (ActiveSheetKind != null && ActiveSheetKind != SheetKind) return
        if (OcrInFlight) return

        val SheetNodes = CurrentScreenNodesWithDescriptions().ifEmpty { VisibleNodes }
        val SheetRead = CustomerProfileParser.ReadContactSheet(
            Nodes = SheetNodes,
            KindVal = SheetKind,
            SelectedIndexVal = -1
        )

        val OcrLines = PendingOcrLines
        PendingOcrLines = null
        val ValueList = if (OcrLines != null) {
            SheetOcrParser.ParseSheetText(Lines = OcrLines, KindVal = SheetKind)
        } else {
            SheetRead.Values
        }

        if (ValueList.isEmpty() && ShouldTryOcr(SheetKind = SheetKind)) {
            StartSheetOcr(SheetKind = SheetKind)
            return
        }

        val WaitedLongEnough =
            System.currentTimeMillis() - SheetOpenedAt >= CUSTOMER_SHEET_SETTLE_MS
        if (ValueList.isEmpty() &&
            SheetRead.RelatedGroupCount > 0 &&
            !WaitedLongEnough
        ) {
            SheetReadRetryCount++
            CustomerStageValue = CustomerStage.READING_SHEET
            DiagnosticWarning(
                EventName = "CUSTOMER_SHEET_EMPTY",
                MessageText = "customer=$ActiveCustomerName kind=${SheetKind.name} " +
                        "groups=${SheetRead.RelatedGroupCount} but no values exposed; " +
                        "retry=$SheetReadRetryCount"
            )
            return
        }

        CustomerStageValue = CustomerStage.RETURNING

        val ProfileObj = ActiveProfile
        if (ProfileObj != null && ValueList.isNotEmpty()) {
            ActiveProfile = when (SheetKind) {
                CustomerProfileParser.ContactKind.MOBILE -> ProfileObj.copy(Mobiles = ValueList)
                CustomerProfileParser.ContactKind.EMAIL -> ProfileObj.copy(Emails = ValueList)
                CustomerProfileParser.ContactKind.ADDRESS -> ProfileObj.copy(Addresses = ValueList)
            }
        }
        val ExpectedCount = ExpectedSheetCounts[SheetKind] ?: 0
        val UnattributedCount = ValueList.count { ValueItem ->
            ValueItem.RelatedPolicies.isEmpty()
        }
        val CountOk = ExpectedCount <= 0 || ValueList.size >= ExpectedCount
        val AttributionOk = UnattributedCount == 0
        val SheetOk = CountOk && AttributionOk
        val FailReason = when {
            SheetOk -> ""
            !CountOk && !AttributionOk -> "reason=count+attribution "
            !CountOk -> "reason=count "
            else -> "reason=attribution "
        }
        val VerifyText = "customer=$ActiveCustomerName kind=${SheetKind.name} " +
                "expected=$ExpectedCount parsed=${ValueList.size} " +
                "unattributed=$UnattributedCount ok=$SheetOk " + FailReason +
                "source=" + (if (OcrLines != null) "ocr" else "tree") + " " +
                "values=[" + ValueList.joinToString(" | ") { ValueItem ->
                    ValueItem.Value + "->" + ValueItem.RelatedPolicies.joinToString(",")
                } + "]"
        if (SheetOk) {
            DiagnosticInfo(EventName = "CUSTOMER_SHEET_VERIFY", MessageText = VerifyText)
        } else {
            DiagnosticWarning(EventName = "CUSTOMER_SHEET_VERIFY", MessageText = VerifyText)
        }
        if (ValueList.isEmpty()) {
            EmptySheetReadCount++
            DumpEmptySheet(SheetKind = SheetKind)
        } else {
            SheetsEverYieldedValues = true
        }
        val AttributedCount = ValueList.count { ValueItem ->
            ValueItem.RelatedPolicies.isNotEmpty()
        }
        DiagnosticInfo(
            EventName = "CUSTOMER_SHEET_READ",
            MessageText = "customer=$ActiveCustomerName kind=${SheetKind.name} " +
                    "values=${ValueList.size} withRelatedPolicies=$AttributedCount " +
                    "relatedGroups=${SheetRead.RelatedGroupCount} " +
                    "orphanGroups=${SheetRead.OrphanGroupCount} " +
                    "sheetNodes=${SheetNodes.size} defaultReadFromScreen=false"
        )

        ActiveSheetKind = null
        if (PendingSheetKinds.isNotEmpty()) PendingSheetKinds.removeAt(0)
        SheetDismissSignature = CurrentScreenNodes().hashCode()
        SheetStaleTreeCount = 0
        DismissContactSheet(UseBackAction = false)
        ScheduleCustomerAction(DelayMs = CUSTOMER_SHEET_CLOSE_DELAY_MS) {
            ConfirmSheetClosed(AttemptCount = 0)
        }
    }

    private fun DismissContactSheet(UseBackAction: Boolean) {
        if (UseBackAction) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            DiagnosticInfo(
                EventName = "CUSTOMER_SHEET_DISMISS",
                MessageText = "customer=$ActiveCustomerName method=back"
            )
            return
        }
        val DisplayMetricsObj = resources.displayMetrics
        val TapAccepted = PerformTapGesture(
            XPos = DisplayMetricsObj.widthPixels * 0.5f,
            YPos = DisplayMetricsObj.heightPixels * SHEET_SCRIM_Y_RATIO
        )
        DiagnosticInfo(
            EventName = "CUSTOMER_SHEET_DISMISS",
            MessageText = "customer=$ActiveCustomerName method=scrim accepted=$TapAccepted"
        )
    }

    private fun ConfirmSheetClosed(AttemptCount: Int) {
        val VisibleNodes = CurrentScreenNodes()
        val StillOpen = VisibleSheetKind(VisibleNodes = VisibleNodes) != null
        if (StillOpen && VisibleNodes.hashCode() == SheetDismissSignature) {
            SheetStaleTreeCount++
            if (SheetStaleTreeCount <= SHEET_STALE_TREE_LIMIT) {
                DiagnosticInfo(
                    EventName = "CUSTOMER_SHEET_STALE_TREE",
                    MessageText = "customer=$ActiveCustomerName wait=$SheetStaleTreeCount " +
                            "attempt=$AttemptCount; tree unchanged since the dismiss, holding"
                )
                ScheduleCustomerAction(DelayMs = CUSTOMER_SHEET_CLOSE_DELAY_MS) {
                    ConfirmSheetClosed(AttemptCount = AttemptCount)
                }
                return
            }
        }
        if (StillOpen && AttemptCount < CUSTOMER_SHEET_CLOSE_LIMIT) {
            DiagnosticWarning(
                EventName = "CUSTOMER_SHEET_STILL_OPEN",
                MessageText = "customer=$ActiveCustomerName attempt=$AttemptCount; dismissing again"
            )
            SheetDismissSignature = VisibleNodes.hashCode()
            SheetStaleTreeCount = 0
            DismissContactSheet(UseBackAction = false)
            ScheduleCustomerAction(DelayMs = CUSTOMER_SHEET_CLOSE_DELAY_MS) {
                ConfirmSheetClosed(AttemptCount = AttemptCount + 1)
            }
            return
        }
        if (StillOpen) {
            DiagnosticWarning(
                EventName = "CUSTOMER_SHEET_STUCK",
                MessageText = "customer=$ActiveCustomerName sheet would not close; " +
                        "abandoning the remaining sheets for this customer"
            )
            PendingSheetKinds.clear()
        }

        if (IsCustomerDetailScreen(VisibleNodes = VisibleNodes)) {
            CustomerStageValue = CustomerStage.READING_PROFILE
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) { OpenNextContactSheet() }
            return
        }

        val HasLeftDetail = IsCustomerDashboardScreen(VisibleNodes = VisibleNodes) ||
                IsCustomerPortfolioScreen(VisibleNodes = VisibleNodes)
        if (!HasLeftDetail && AttemptCount < CUSTOMER_RETURN_ATTEMPT_LIMIT) {
            DiagnosticInfo(
                EventName = "CUSTOMER_SHEET_SETTLING",
                MessageText = "customer=$ActiveCustomerName attempt=$AttemptCount " +
                        "nodes=${VisibleNodes.size}; waiting for the detail screen to come back"
            )
            ScheduleCustomerAction(DelayMs = CUSTOMER_SHEET_CLOSE_DELAY_MS) {
                ConfirmSheetClosed(AttemptCount = AttemptCount + 1)
            }
            return
        }

        DiagnosticWarning(
            EventName = "CUSTOMER_SHEET_OVERSHOT",
            MessageText = "customer=$ActiveCustomerName left the detail screen while closing " +
                    "a sheet; finishing this customer with what was read"
        )
        val PendingCount = PendingSheetKinds.size
        val ReopenKey = CustomerKey(NameText = ActiveCustomerName)
        val ReopensSoFar = CustomerReopenCounts[ReopenKey] ?: 0
        if (PendingCount > 0 && ReopensSoFar < CUSTOMER_REOPEN_LIMIT) {
            CustomerReopenCounts[ReopenKey] = ReopensSoFar + 1
            RequeueActiveCustomer = true
            DiagnosticInfo(
                EventName = "CUSTOMER_SHEET_REOPEN",
                MessageText = "customer=$ActiveCustomerName pending=$PendingCount " +
                        "attempt=${ReopensSoFar + 1}; reopening to finish the remaining sheet(s)"
            )
        }
        PendingSheetKinds.clear()
        FinishActiveCustomer()
    }

    private fun FinishActiveCustomer() {
        val ProfileObj = ActiveProfile
        var FilledCount = 0
        if (ProfileObj != null) {
            for (PolicyNumber in ActiveCustomerRelevantNumbers) {
                val PatchItem = ProfileObj.ToPolicyPatch(PolicyNumber = PolicyNumber)
                val ExistingPatch = ProfilePatchMap[PolicyNumber]
                ProfilePatchMap[PolicyNumber] = if (ExistingPatch == null) {
                    PatchItem
                } else {
                    RecordMerge.MergePolicy(
                        ExistingItem = ExistingPatch,
                        IncomingItem = PatchItem
                    ).Record
                }
                ProfilePatchNames[PolicyNumber] = ActiveCustomerName
                FilledCount++
            }
        }
        for (PolicyNumber in ActiveCustomerRelevantNumbers) {
            FilledPolicyNumbers.add(PolicyNumber)
        }
        if (RequeueActiveCustomer) {
            RequeueActiveCustomer = false
            ProcessedCustomerKeys.remove(CustomerKey(NameText = ActiveCustomerName))
        } else {
            MarkCustomerVisited(NameText = ActiveCustomerName)
        }
        if (ProfileObj != null) {
            DiagnosticInfo(
                EventName = "CUSTOMER_PROFILE_VERIFY",
                MessageText = "customer=$ActiveCustomerName " +
                        ContactSummary(LabelText = "mobiles", Values = ProfileObj.Mobiles) +
                        " " + ContactSummary(LabelText = "emails", Values = ProfileObj.Emails) +
                        " " + ContactSummary(
                            LabelText = "addresses",
                            Values = ProfileObj.Addresses
                        ) +
                        " dob=${ProfileObj.Dob} gender=${ProfileObj.Gender}" +
                        " education=${ProfileObj.Education}" +
                        " occupation=${ProfileObj.Occupation}" +
                        " marital=${ProfileObj.MaritalStatus}" +
                        " income=${ProfileObj.AnnualIncome}"
            )
            for (PolicyNumber in ActiveCustomerRelevantNumbers) {
                val VerifyPatch = ProfileObj.ToPolicyPatch(PolicyNumber = PolicyNumber)
                DiagnosticInfo(
                    EventName = "CUSTOMER_PATCH_VERIFY",
                    MessageText = "customer=$ActiveCustomerName policy=$PolicyNumber " +
                            "mobile=${VerifyPatch.MobileNumber} " +
                            "mobileOthers=" +
                            VerifyPatch.MobileNumberOthers.orEmpty().joinToString(",") + " " +
                            "email=${VerifyPatch.Email} " +
                            "emailOthers=" +
                            VerifyPatch.EmailOthers.orEmpty().joinToString(",") + " " +
                            "address=${VerifyPatch.Address} " +
                            "addressOthers=" +
                            VerifyPatch.AddressOthers.orEmpty().joinToString(",")
                )
            }
        }
        DiagnosticInfo(
            EventName = "CUSTOMER_DONE",
            MessageText = "customer=$ActiveCustomerName policiesFilled=$FilledCount " +
                    "totalPatches=${ProfilePatchMap.size} gaps=${SessionGapMap.size}"
        )
        ConsecutiveErrorGiveUps = 0
        RefreshBubble()
        if (CustomerRoute.IsDriving) {
            CustomerRoute.OnCustomerFinished()
            return
        }
        CustomerStageValue = CustomerStage.RETURNING
        ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
            ReturnToCustomerDashboard(AttemptCount = 0)
        }
    }

    private fun ContactSummary(
        LabelText: String,
        Values: List<com.bliss.screenreader.data.model.ContactValue>
    ): String {
        if (Values.isEmpty()) return "$LabelText=0[]"
        val DetailText = Values.joinToString(" | ") { ValueItem ->
            ValueItem.Value + "->" + ValueItem.RelatedPolicies.joinToString(",") +
                    (if (ValueItem.IsDefault) "*" else "") +
                    (if (ValueItem.IsPartial) "~" else "")
        }
        return "$LabelText=${Values.size}[$DetailText]"
    }

    private fun ReturnToCustomerDashboard(AttemptCount: Int, BackCount: Int = 0) {
        if (CustomerRoute.IsDriving) {
            CustomerRoute.OnCustomerFinished()
            return
        }
        val VisibleNodes = CurrentScreenNodes()

        if (IsCustomerDashboardScreen(VisibleNodes = VisibleNodes)) {
            ActiveCustomerName = ""
            ActiveProfile = null
            ProfilePaneNodes.clear()
            PendingSheetKinds.clear()
            CustomerStageValue = CustomerStage.DASHBOARD
            RefreshBubble()
            ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                RunCustomerDashboardStep()
            }
            return
        }

        if (AttemptCount >= CUSTOMER_RETURN_ATTEMPT_LIMIT) {
            FailCustomerAutomation(ReasonText = "could not get back to the customer list")
            return
        }

        if (IsCustomerPortfolioScreen(VisibleNodes = VisibleNodes)) {
            DiagnosticInfo(
                EventName = "CUSTOMER_RETURN_OVERSHOT",
                MessageText = "landed on Customer Portfolio; re-arming the Customers card " +
                        "instead of pressing back again"
            )
            HasClickedPortfolioCustomers = false
            PortfolioCustomersLastAttemptAt = 0L
            CustomerStageValue = CustomerStage.DASHBOARD
            ScheduleCustomerAction(DelayMs = CUSTOMER_RETURN_DELAY_MS) {
                ReturnToCustomerDashboard(AttemptCount = AttemptCount + 1, BackCount = BackCount)
            }
            return
        }

        val IsDeeperScreen = IsCustomerDetailScreen(VisibleNodes = VisibleNodes) ||
                VisibleSheetKind(VisibleNodes = VisibleNodes) != null
        if (!IsDeeperScreen || BackCount >= CUSTOMER_BACK_LIMIT) {
            DiagnosticInfo(
                EventName = "CUSTOMER_RETURN_WAIT",
                MessageText = "attempt=$AttemptCount backs=$BackCount nodes=${VisibleNodes.size} " +
                        "screen not identified yet; waiting rather than pressing back"
            )
            ScheduleCustomerAction(DelayMs = CUSTOMER_RETURN_DELAY_MS) {
                ReturnToCustomerDashboard(AttemptCount = AttemptCount + 1, BackCount = BackCount)
            }
            return
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        ScheduleCustomerAction(DelayMs = CUSTOMER_RETURN_DELAY_MS) {
            ReturnToCustomerDashboard(AttemptCount = AttemptCount + 1, BackCount = BackCount + 1)
        }
    }

    private fun RetryCustomerStep(ReasonText: String) {
        CustomerStepAttempts++
        DiagnosticWarning(
            EventName = "CUSTOMER_STEP_RETRY",
            MessageText = "stage=$CustomerStageValue attempt=$CustomerStepAttempts " +
                    "reason=$ReasonText"
        )
        if (CustomerStepAttempts >= CUSTOMER_STEP_RETRY_LIMIT) {
            CustomerStepAttempts = 0
            when (CustomerStageValue) {
                CustomerStage.DASHBOARD -> FailCustomerAutomation(ReasonText = ReasonText)
                else -> {
                    CustomerStageValue = CustomerStage.RETURNING
                    ScheduleCustomerAction(DelayMs = CUSTOMER_NAVIGATION_DELAY_MS) {
                        ReturnToCustomerDashboard(AttemptCount = 0)
                    }
                }
            }
            return
        }
        val StageAtRetry = CustomerStageValue
        ScheduleCustomerAction(DelayMs = CUSTOMER_SCROLL_SETTLE_MS) {
            when (StageAtRetry) {
                CustomerStage.OPENING_PROFILE -> TapProfileTab()
                CustomerStage.READING_PROFILE -> SweepProfilePane()
                else -> RunCustomerDashboardStep()
            }
        }
    }

    private fun CompleteCustomerAutomation(ReasonText: String) {
        if (IsCustomerAutomationComplete) return
        IsCustomerAutomationComplete = true
        IsCustomerAutomationRunning = false
        SaveCustomerResumeProgress(IsCompleteVal = true)
        CustomerStageValue = CustomerStage.IDLE
        CustomerAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        CustomerAutomationRunnable = null

        DiagnosticInfo(
            EventName = "CUSTOMER_AUTOMATION_COMPLETE",
            MessageText = "reason=$ReasonText customers=${ProcessedCustomerKeys.size} " +
                    "policiesFilled=${ProfilePatchMap.size} gaps=${SessionGapMap.size}"
        )
        MainHandler.post { FinishCaptureSession() }
    }

    private fun FailCustomerAutomation(ReasonText: String) {
        CustomerAutomationFailureCount++
        SaveCustomerResumeProgress(IsCompleteVal = false)
        IsCustomerAutomationRunning = false
        CustomerStageValue = CustomerStage.IDLE
        CustomerAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        CustomerAutomationRunnable = null

        DiagnosticWarning(
            EventName = "CUSTOMER_AUTOMATION_FAILED",
            MessageText = "reason=$ReasonText failures=$CustomerAutomationFailureCount"
        )
        if (CustomerAutomationFailureCount >= CUSTOMER_AUTOMATION_RECOVERY_LIMIT) {
            CompleteCustomerAutomation(ReasonText = "recovery limit reached: $ReasonText")
            return
        }
        CustomerAutomationRetryAfter = System.currentTimeMillis() + CUSTOMER_FAILURE_RETRY_MS
        CustomerScrollAttempts = 0
        CustomerScrollStallCount = 0
        CustomerPageRetryCount = 0
        CustomerStepAttempts = 0
        LatestCustomerVisibleSignature = 0
    }

    override fun CustomerSearchRootNode(): AccessibilityNodeInfo? {
        return FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
    }

    override fun CustomerSearchRecycleNode(NodeRef: AccessibilityNodeInfo?) {
        RecycleNode(NodeRef = NodeRef)
    }

    override fun CustomerSearchTap(XPos: Float, YPos: Float): Boolean {
        return PerformTapGesture(XPos = XPos, YPos = YPos)
    }

    override fun CustomerSearchSetText(NodeRef: AccessibilityNodeInfo, TextValue: String): Boolean {
        return SetNodeText(NodeRef = NodeRef, TextValue = TextValue)
    }

    override fun CustomerSearchSchedule(DelayMs: Long, ActionRef: () -> Unit) {
        ScheduleCustomerAction(DelayMs = DelayMs, ActionRef = ActionRef)
    }

    override fun CustomerSearchRefreshScreen() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
    }

    override fun CustomerSearchInfo(EventName: String, MessageText: String) {
        DiagnosticInfo(EventName = EventName, MessageText = MessageText)
    }

    override fun CustomerSearchWarn(EventName: String, MessageText: String) {
        DiagnosticWarning(EventName = EventName, MessageText = MessageText)
    }

    override fun CustomerSearchScreenWidth(): Int {
        return resources.displayMetrics.widthPixels
    }

    override fun CustomerSearchScreenHeight(): Int {
        return resources.displayMetrics.heightPixels
    }

    override fun CustomerSearchBeginRun() {
        IsCustomerAutomationRunning = true
        IsCustomerDashboardActive = false
        HasClickedPortfolioCustomers = true
        HasClickedHomeNavTab = true
        CustomerAutomationRetryAfter = 0L
        CustomerStageValue = CustomerStage.DASHBOARD
    }

    override fun CustomerSearchOpenDetail(NameTextVal: String) {
        ActiveCustomerName = NameTextVal
        ActiveProfile = null
        ProfilePaneNodes.clear()
        PendingSheetKinds.clear()
        OcrAttemptedKinds.clear()
        PendingOcrLines = null
        OcrInFlight = false
        ProfileSweepCount = 0
        LastProfileSweepSignature = 0
        ProcessedCustomerKeys.add(CustomerKey(NameText = NameTextVal))
        CustomerOpenAttempts = 1
        CustomerStepAttempts = 0
        CustomerStageValue = CustomerStage.OPENING_CUSTOMER
        RefreshBubble()
        ScheduleCustomerAction(DelayMs = CUSTOMER_DETAIL_OPEN_DELAY_MS) {
            WaitForCustomerDetailScreen()
        }
    }

    override fun CustomerSearchPressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun CustomerSearchHandOffToDashboard() {
        IsCustomerAutomationRunning = false
        IsCustomerDashboardActive = false
        HasClickedPortfolioCustomers = false
        PortfolioCustomersClickAttempts = 0
        PortfolioCustomersLastAttemptAt = 0L
        CustomerAutomationRetryAfter = 0L
        CustomerStageValue = CustomerStage.IDLE
        CustomerAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        CustomerAutomationRunnable = null
    }

    override fun CustomerSearchFinishRun() {
        CompleteCustomerAutomation(ReasonText = "targeted customer search complete")
    }

    private fun StopCustomerAutomation(ResetStateVal: Boolean) {
        CustomerAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        CustomerAutomationRunnable = null
        IsCustomerAutomationRunning = false
        CustomerStageValue = CustomerStage.IDLE
        if (!ResetStateVal) return

        IsCustomerAutomationComplete = false
        IsCustomerDashboardActive = false
        HasClickedPortfolioCustomers = false
        PortfolioCustomersClickAttempts = 0
        PortfolioCustomersLastAttemptAt = 0L
        CustomerCurrentPage = 0
        CustomerTotalPages = 0
        TargetCustomerPage = 0
        CustomerScrollAttempts = 0
        CustomerScrollStallCount = 0
        CustomerPageRetryCount = 0
        CustomerPageWaitCount = 0
        CustomerPageChipRect = null
        CustomerPageOptionDeadOffset = 0
        CustomerPageOptionPendingOffset = 0
        CustomerPageOptionDeadTaps = 0
        CustomerPageOptionSignature = ""
        CustomerOpenAttempts = 0
        CustomerStepAttempts = 0
        CustomerAutomationFailureCount = 0
        CustomerAutomationRetryAfter = 0L
        LatestCustomerVisibleSignature = 0
        ActiveCustomerName = ""
        ActiveCustomerPolicyNumbers = emptyList()
        ActiveCustomerRelevantNumbers = emptyList()
        ActiveSheetKind = null
        SheetReadRetryCount = 0
        SheetLinkRetryCount = 0
        ActiveProfile = null
        ProfileSweepCount = 0
        LastProfileSweepSignature = 0
        ProfilePaneNodes.clear()
        PendingSheetKinds.clear()
        ProcessedCustomerKeys.clear()
        CustomerReopenCounts.clear()
        RequeueActiveCustomer = false
        CancelErrorRetry()
        ErrorRetryCount = 0
        ErrorBoundsMissCount = 0
        ErrorHealthySinceAt = 0L
        ConsecutiveErrorGiveUps = 0
        ErrorPaceExtraMs = 0L
        LastHealthyRecordCount = 0
        ProfilePatchMap.clear()
        ProfilePatchNames.clear()
        SessionGapMap.clear()
        SessionPolicyNumbers.clear()
        FilledPolicyNumbers.clear()
        VisitedCustomerNames.clear()
        SheetsEverYieldedValues = false
        EmptySheetReadCount = 0
        EmptySheetKindCounts.clear()
        OcrAttemptedKinds.clear()
        PendingOcrLines = null
        OcrInFlight = false
    }

    override fun onInterrupt() {
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = false)
        StopRenewalAutomation(ResetStateVal = false)
        StopCustomerAutomation(ResetStateVal = false)
        CancelEventWindowCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        Instance = null
        AppToast.RemoveOverlay()
        IsCapturing = false
        IsPaused = false
        MainHandler.removeCallbacks(TickRunnable)
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = false)
        StopRenewalAutomation(ResetStateVal = false)
        StopCustomerAutomation(ResetStateVal = false)
        CancelEventWindowCapture()
        StopParseThread()
        RemoveBubble()
        ReleaseWakeLock()
        CaptureSessionState.OnSessionEnded()
    }
}
