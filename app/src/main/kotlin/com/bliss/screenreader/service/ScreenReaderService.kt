@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SameParameterValue"
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
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
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
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.data.parser.CaptureParsers
import com.bliss.screenreader.data.parser.FupDataParser
import com.bliss.screenreader.data.parser.PlanIdentity
import com.bliss.screenreader.data.parser.RecordMerge
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.data.parser.ScreenDataParser
import com.bliss.screenreader.utils.AppLauncherUtils
import com.bliss.screenreader.utils.HapticFeedback
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import kotlin.math.abs

@SuppressLint("AccessibilityPolicy")
class ScreenReaderService : AccessibilityService() {

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

        // A collapsed section header must sit inside this band to be tappable.
        // Below it the "Pay Premium" bar and the tab strip overlay the content,
        // so a chevron that looks visible is not actually hittable.
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
        private const val PORTFOLIO_CLICK_RETRY_MS = 3000L
        private const val PORTFOLIO_TRANSITION_TIMEOUT_MS = 4000L
        private const val HOME_NAV_CLICK_RETRY_MS = 3000L
        private const val HOME_NAV_TRANSITION_TIMEOUT_MS = 4000L
        private const val HOME_NAV_TAB_Y_RATIO = 0.952f
        private const val HOME_BOTTOM_NAV_TOP_RATIO = 0.85f
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
        private const val RENEWAL_DROPDOWN_RETRY_LIMIT = 3
        private const val RENEWAL_AUTOMATION_RECOVERY_LIMIT = 3
        private const val RENEWAL_FAILURE_RETRY_MS = 5000L
        private const val RENEWAL_SECTION_ROW_TOLERANCE_RATIO = 0.06f
        private const val PORTFOLIO_POLICIES_ARROW_X_RATIO = 0.42f
        private const val PORTFOLIO_POLICIES_ARROW_Y_FALLBACK_RATIO = 0.245f
        private const val POLICY_FAILURE_RETRY_MS = 5000L
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
    private var PolicyPageRestoreTarget = 0
    private var IsRestoringPolicyPageAfterDetail = false
    private var IsPolicyDetailScreenActive = false
    private var IsPolicyDashboardScreenVisible = false
    private var LatestPolicyDetailNodes: List<String> = emptyList()
    private var PolicySectionRetryRounds = 0
    private var PolicyDetailSweepCount = 0
    private var LastPolicyDetailSweepSignature = 0

    /**
     * Sections with a seek/tap chain still running. A verification round that
     * fired while a chain was mid-scroll would start a second chain for the
     * same header, and the extra tap collapses what the first one opened.
     */
    private val PolicySectionsInFlight = linkedSetOf<String>()
    private val ProcessedPolicyDetailNumbers = linkedSetOf<String>()
    private var PortfolioPoliciesLastAttemptAt = 0L
    private var PortfolioPoliciesClickAttempts = 0
    private var HasClickedHomeNavTab = false
    private var HomeNavLastAttemptAt = 0L
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
    private var RenewalUnknownScreenCount = 0
    private var LatestRenewalVisibleNodes: List<String> = emptyList()
    private var RenewalCurrentPage = 0
    private var RenewalTotalPages = 0
    private var RenewalExpectedPage = 0
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

    /**
     * Flutter/WebView animations can emit dozens of accessibility events for
     * one visual change. Walking the complete tree for each event blocks the
     * accessibility overlay, so capture one settled window per event burst.
     */
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

        // Accessibility and application overlays can make the active root
        // transiently unavailable. Inspect the interactive windows as a safe
        // fallback and prefer the focused target application window.
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

    /**
     * Renewal cards repeat values across records - two customers can share a
     * payment mode, an amount or a date - and [AddCapturedNodes] de-duplicates
     * the flat node list, which would silently strip fields from every card
     * after the first. So renewal rows are parsed per snapshot and merged into
     * a policy-number keyed map instead, exactly as the policy dashboard does.
     */
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
                // A card first seen mid-scroll can arrive without its payment
                // date and gain one later, which changes its identity.
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

    /**
     * A policy can appear in renewal history more than once, so a record is
     * identified by its policy number *and* the date the premium was paid.
     */
    private fun RenewalRecordKey(RecordItem: FupPolicy): String {
        // Delegated so the capture-time key and the commit-time key cannot
        // drift apart and start treating the same row as two records.
        return RecordMerge.RenewalKey(RecordItem = RecordItem)
    }

    /**
     * Finds the entry an incoming card belongs to. An exact key match wins;
     * otherwise a same-policy entry whose payment date is still blank is
     * treated as the same partially-read card.
     */
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
        for (RecordItem in CapturedFupMap.values) {
            val PlanLabel = PlanIdentity.Combine(
                CodeValue = RecordItem.PlanCode,
                NameValue = RecordItem.PlanName
            )
            val AnchorLine = buildString {
                append(RecordItem.PolicyNumber)
                if (PlanLabel.isNotEmpty()) append(" | ").append(PlanLabel)
            }
            CapturedNodes.add(AnchorLine)
            if (RecordItem.HolderName.isNotEmpty()) CapturedNodes.add(RecordItem.HolderName)
            if (RecordItem.PremiumAmount.isNotEmpty()) {
                CapturedNodes.add("Premium Amount (excl. GST)")
                CapturedNodes.add(RecordItem.PremiumAmount)
            }
            if (RecordItem.DueDate.isNotEmpty()) {
                CapturedNodes.add("Due Date")
                CapturedNodes.add(RecordItem.DueDate)
            }
            if (RecordItem.PaymentDate.isNotEmpty()) {
                CapturedNodes.add("Payment Date")
                CapturedNodes.add(RecordItem.PaymentDate)
            }
            if (RecordItem.ModeOfPayment.isNotEmpty()) {
                CapturedNodes.add("Mode of Payment")
                CapturedNodes.add(RecordItem.ModeOfPayment)
            }
            if (RecordItem.Status.isNotEmpty()) {
                CapturedNodes.add("Status at Time of Payment")
                CapturedNodes.add(RecordItem.Status)
            }
            CapturedNodes.add("Call Customer")
        }
    }

    private fun RebuildCapturedPolicyNodes() {
        CapturedNodes.clear()
        for (PolicyItem in CapturedPolicyMap.values) {
            if (PolicyItem.Status.isNotEmpty()) CapturedNodes.add(PolicyItem.Status)
            if (PolicyItem.KycStatus.isNotEmpty()) CapturedNodes.add("KYC not updated")
            if (PolicyItem.NeftStatus.isNotEmpty()) CapturedNodes.add("NEFT not updated")
            if (PolicyItem.NomineeStatus.isNotEmpty()) CapturedNodes.add("Nominee not updated")
            if (PolicyItem.MobileUpdateStatus.isNotEmpty()) CapturedNodes.add("Mobile not updated")
            if (PolicyItem.AddressUpdateStatus.isNotEmpty()) CapturedNodes.add("Address not updated")

            val PolicyLine = buildString {
                append(PolicyItem.PolicyNumber)
                if (PolicyItem.PlanName.isNotEmpty()) append(" | ").append(PolicyItem.PlanName)
            }
            CapturedNodes.add(PolicyLine)
            if (PolicyItem.HolderName.isNotEmpty()) CapturedNodes.add(PolicyItem.HolderName)
            if (PolicyItem.AutoPay.isNotEmpty()) {
                CapturedNodes.add("Auto Pay")
                CapturedNodes.add(PolicyItem.AutoPay)
            }
            if (PolicyItem.RenewalType.isNotEmpty()) CapturedNodes.add(PolicyItem.RenewalType)
            if (PolicyItem.RenewalDueDate.isNotEmpty()) CapturedNodes.add(PolicyItem.RenewalDueDate)
            if (PolicyItem.PremiumAmount.isNotEmpty()) {
                CapturedNodes.add("Premium Amount (excl. GST)")
                val FrequencyText = PolicyItem.PremiumFrequency
                    .takeIf { ValueText -> ValueText.isNotEmpty() }
                    ?.let { ValueText -> "/$ValueText" }
                    .orEmpty()
                CapturedNodes.add("${PolicyItem.PremiumAmount}$FrequencyText")
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

    // ------------------------------------------------------------- lifecycle

    fun StartCaptureSession(
        ModeVal: CaptureMode,
        CapturePolicyDetailsVal: Boolean = false,
        OriginActivityVal: String = "",
        ResumeSessionIdVal: String = ""
    ) {
        CurrentMode = ModeVal
        CancelEventWindowCapture()
        IsResumedSession = ResumeSessionIdVal.isNotBlank()
        CurrentSessionId = ResumeSessionIdVal.ifBlank { UUID.randomUUID().toString() }
        CapturePolicyDetailsEnabled = ModeVal == CaptureMode.POLICY && CapturePolicyDetailsVal
        OriginActivityName = OriginActivityVal
        SessionStartedAt = System.currentTimeMillis()
        PausedTotalMs = 0L
        PausedAt = 0L
        LatestRecords = emptyList()
        LastParsedNodeCount = -1
        LastPackageName = ""
        HasExpandedCurrentPolicyScreen = false
        CurrentAutoScrollScreenSignature = 0
        CompletedAutoScrollScreenSignature = null
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = true)
        CapturedPolicyMap.clear()
        CapturedFupMap.clear()
        LatestPolicyPageNumbers = emptyList()
        PolicyDetailQueue = emptyList()
        PolicyDetailQueueIndex = 0
        PolicyDetailCurrentPolicyNumber = ""
        PolicyDetailScrollAttempts = 0
        PolicyDetailOpenAttempts = 0
        PolicyDetailReturnAttempts = 0
        PolicyDetailOriginPage = 0
        PolicyPageRestoreTarget = 0
        IsRestoringPolicyPageAfterDetail = false
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
        StopRenewalAutomation(ResetStateVal = true)
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
                    "expected=${ExpectedTargetPackage()} " +
                    "origin=$OriginActivityVal"
        )
        val ActiveServiceInfo = serviceInfo
        val IsDeclaredAccessibilityTool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActiveServiceInfo?.isAccessibilityTool == true
        } else {
            false
        }
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

        // Seeding must happen before the first capture tick: the Rebuild…Nodes
        // helpers clear CapturedNodes, so anything loaded after a tick has
        // already run would be wiped.
        if (IsResumedSession) SeedFromStoredSession()

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
                DiagnosticInfo(
                    EventName = "SESSION_RESUME",
                    MessageText = "session=$CurrentSessionId mode=FUP " +
                            "seeded=${CapturedFupMap.size} nodes=${CapturedNodes.size}"
                )
            }

            CaptureMode.PS -> {
                // PS has no keyed map, so there is nothing safe to seed. The
                // commit still merges by key, it just cannot show prior rows
                // in the live count.
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

    /**
     * Ends the capture and parks the result for review. Nothing is written to
     * storage here; the review sheet decides.
     */
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
                PolicyRecords = CapturedPolicyMap.values.toList(),
                FupRecords = CapturedFupMap.values.toList(),
                CapturePolicyDetails = CapturePolicyDetailsEnabled,
                TargetPackage = LastPackageName,
                OriginActivity = OriginActivityName
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
        CapturedNodes.clear()
    }

    private fun TeardownSession() {
        IsCapturing = false
        IsPaused = false
        MainHandler.removeCallbacks(TickRunnable)
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = false)
        StopRenewalAutomation(ResetStateVal = false)
        CancelEventWindowCapture()
        HasExpandedCurrentPolicyScreen = false
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

    // ---------------------------------------------------------- automation

    private fun HandleScreenAutomation(
        PackageNameVal: String,
        RootNode: AccessibilityNodeInfo,
        VisibleNodes: List<String>
    ) {
        // Both the policy and renewal flows begin on the agent app's Home
        // dashboard and branch apart at the bottom navigation bar.
        if (CurrentMode == CaptureMode.POLICY || CurrentMode == CaptureMode.FUP) {
            if (PackageNameVal != AppLauncherUtils.LIC_SUPER_APP_PACKAGE) return

            if (IsAgentHomeScreen(VisibleNodes = VisibleNodes)) {
                HandleAgentHomeScreen(RootNode = RootNode)
                return
            }
        }

        if (CurrentMode == CaptureMode.FUP) {
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

            if (IsPolicyDashboardActive || IsPolicyDashboardScreen(VisibleNodes = VisibleNodes)) {
                IsPolicyDashboardActive = true
                IsPolicyDetailScreenActive = false
                IsPolicyDashboardScreenVisible = IsPolicyDashboardScreen(VisibleNodes = VisibleNodes)
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
            CurrentAutoScrollScreenSignature = VisibleNodes.joinToString(separator = "\u0001").hashCode()
            StartAutoScroll(ScreenSignature = CurrentAutoScrollScreenSignature)
        } else if (IsAutoScrolling) {
            StopAutoScroll()
        }
    }

    private fun IsAutoScrollScreenReady(PackageNameVal: String, VisibleNodes: List<String>): Boolean {
        if (PackageNameVal != ExpectedTargetPackage()) return false

        val HasParsedRecord = try {
            CaptureParsers.Preview(ModeVal = CurrentMode, Nodes = VisibleNodes).isNotEmpty()
        } catch (_: Exception) {
            false
        }
        if (HasParsedRecord) return true

        return VisibleNodes.any { NodeText ->
            when (CurrentMode) {
                CaptureMode.PS ->
                    NodeText.contains("servicing", ignoreCase = true) ||
                            NodeText.contains("policy status", ignoreCase = true)

                CaptureMode.FUP ->
                    NodeText.contains("renewal history", ignoreCase = true) ||
                            NodeText.equals("FUP", ignoreCase = true)

                CaptureMode.POLICY -> false
            }
        }
    }

    private fun ExpectedTargetPackage(): String {
        return if (CurrentMode == CaptureMode.PS) {
            AppLauncherUtils.PS_AGENT_APP_PACKAGE
        } else {
            AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        }
    }

    // ------------------------------------------------ policy dashboard flow

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

    /**
     * The agent app's landing screen: a greeting/performance card above the
     * Home | Customers | Renewals | Profile bottom navigation bar.
     */
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
        val HasHomeTab = VisibleNodes.any { NodeText ->
            NodeText.trim().equals("Home", ignoreCase = true)
        }
        val HasCustomersTab = VisibleNodes.any { NodeText ->
            NodeText.trim().equals("Customers", ignoreCase = true)
        }
        return HasHomeMarker && HasHomeTab && HasCustomersTab
    }

    /**
     * The policy flow lives behind the Customers tab and the renewal flow
     * behind the Renewals tab, so the Home screen handler is shared and only
     * the target label differs.
     */
    private fun HomeNavTabLabel(): String {
        return if (CurrentMode == CaptureMode.FUP) HOME_TAB_RENEWALS else HOME_TAB_CUSTOMERS
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
        }

        val RetryDelayPassed = CurrentTime - HomeNavLastAttemptAt >= HOME_NAV_CLICK_RETRY_MS
        if (!HasClickedHomeNavTab && RetryDelayPassed) {
            HomeNavClickAttempts++
            HomeNavLastAttemptAt = CurrentTime
            DiagnosticInfo(
                EventName = "HOME_NAV_CLICK_ATTEMPT",
                MessageText = "tab=$TabLabel attempt=$HomeNavClickAttempts"
            )
            HasClickedHomeNavTab = ClickHomeBottomNavTab(
                RootNode = RootNode,
                TabLabel = TabLabel
            )
        }
    }

    private fun ClickHomeBottomNavTab(
        RootNode: AccessibilityNodeInfo,
        TabLabel: String
    ): Boolean {
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

                // Flutter tab bars often report the label as non-clickable, so
                // tap the visible tab rectangle before the semantic fallback.
                if (PerformTapGesture(
                        XPos = MatchBounds.centerX().toFloat(),
                        YPos = MatchBounds.centerY().toFloat()
                    )
                ) {
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
        if (ClickHomeBottomNavTabByTraversal(TargetNode = RootNode, TabLabel = TabLabel)) {
            return true
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
        TabLabel: String
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
                                "clickable=${TargetNode.isClickable} bounds=$MatchBounds"
                    )
                    if (PerformTapGesture(
                            XPos = MatchBounds.centerX().toFloat(),
                            YPos = MatchBounds.centerY().toFloat()
                        )
                    ) {
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
                            TabLabel = TabLabel
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

    private fun TapHomeBottomNavTabFallback(TabLabel: String): Boolean {
        val DisplayMetricsObj = resources.displayMetrics
        val TabXRatio = if (TabLabel.equals(HOME_TAB_RENEWALS, ignoreCase = true)) {
            HOME_RENEWALS_TAB_X_RATIO
        } else {
            HOME_CUSTOMERS_TAB_X_RATIO
        }
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
        return IsBoundsOnScreen(BoundsObj = BoundsObj) &&
                BoundsObj.centerY() >= resources.displayMetrics.heightPixels *
                HOME_BOTTOM_NAV_TOP_RATIO
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

                // Flutter can report ACTION_CLICK as accepted without invoking the card.
                // Target the visible yellow arrow first; it is the actual navigation control.
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
                    "captured=${CapturedPolicyMap.size} recoveryCount=$PolicyAutomationFailureCount"
        )
        SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
            StartPolicyPageWork()
        }
    }

    private fun StartPolicyPageWork() {
        CaptureActiveWindow(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
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
        PolicyDetailQueue = LatestPolicyPageNumbers
            .distinct()
            .filter { PolicyNumber -> !ProcessedPolicyDetailNumbers.contains(PolicyNumber) }
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

        // Key Dates is the third accordion and sits below the fold, so its
        // header only becomes tappable once the screen has been scrolled -
        // and expanding the two sections above pushes it further down. Retry
        // in rounds rather than once, letting each attempt scroll itself into
        // view, instead of giving up on whatever was off-screen.
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
            // Wait past the worst-case seek chain so the next verification does
            // not fire while a header is still being scrolled into view.
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

        // Expanded values can extend past the bottom of the screen, so walk the
        // whole detail page before leaving it.
        PolicyDetailSweepCount = 0
        LastPolicyDetailSweepSignature = 0
        SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
            SweepPolicyDetailScreen()
        }
    }

    /**
     * Scrolls the expanded detail page to the bottom, capturing at each step.
     * Stops early once the visible content stops changing.
     */
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

    /**
     * Judged against the merged record for this policy, not just the latest
     * snapshot. Scrolling to reach a lower section pushes an earlier one out of
     * view, and treating that as "missing" would re-tap its header and collapse
     * a section that had already been read correctly.
     */
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
                PolicyPageRestoreTarget = PolicyDetailOriginPage
                IsRestoringPolicyPageAfterDetail = true
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
                        !ProcessedPolicyDetailNumbers.contains(PolicyNumber)
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
            if (!IsRestoringPolicyPageAfterDetail &&
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
        PolicyExpectedPage = if (IsRestoringPolicyPageAfterDetail && PolicyPageRestoreTarget > 0) {
            PolicyPageRestoreTarget
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
            SelectorAdvanced = if (!SelectorClicked) {
                AdvancePolicyPageSelector(
                    RootNode = RootNode,
                    CurrentPage = PolicyCurrentPage
                )
            } else {
                false
            }
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
            performGlobalAction(GLOBAL_ACTION_BACK)
            RetryPolicyPageNavigation("Could not select page $PolicyExpectedPage")
            return
        }

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
            PolicyAutomationFailureCount = 0
            Log.d(LOG_TAG, "Capturing Policy Dashboard page $PolicyCurrentPage of $PolicyTotalPages")
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
                IsRestoringPolicyPageAfterDetail = false
                PolicyPageRestoreTarget = 0
                PolicyDetailOriginPage = PolicyCurrentPage
                SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                    ProcessNextPolicyDetail()
                }
                return@SchedulePolicyAction
            }
            SchedulePolicyAction(DelayMs = POLICY_NAVIGATION_DELAY_MS) {
                StartPolicyPageWork()
            }
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
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PolicyAutomationRunnable = null
        Log.d(LOG_TAG, "Policy Dashboard capture completed with ${CapturedPolicyMap.size} policies")
        DiagnosticInfo(
            EventName = "POLICY_AUTOMATION_COMPLETE",
            MessageText = "captured=${CapturedPolicyMap.size} page=$PolicyCurrentPage/$PolicyTotalPages"
        )
        // The agent is looking at the target app, not this one, so the finish
        // signal has to be felt rather than seen.
        HapticFeedback.Success(ContextRef = this)
        Toast.makeText(
            this,
            "Captured ${CapturedPolicyMap.size} policies",
            Toast.LENGTH_LONG
        ).show()

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
        HapticFeedback.Failure(ContextRef = this)
        Toast.makeText(
            this,
            if (CanRetryAutomatically) {
                "Policy automation paused: $ReasonText. Retrying automatically."
            } else {
                "Policy automation stopped after repeated failures. Captured data is preserved."
            },
            Toast.LENGTH_LONG
        ).show()
        RefreshBubble()
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
            PolicyPageRestoreTarget = 0
            IsRestoringPolicyPageAfterDetail = false
            IsPolicyDetailScreenActive = false
            IsPolicyDashboardScreenVisible = false
            LatestPolicyDetailNodes = emptyList()
            PolicySectionRetryRounds = 0
            PolicyDetailSweepCount = 0
            LastPolicyDetailSweepSignature = 0
            PolicySectionsInFlight.clear()
            ProcessedPolicyDetailNumbers.clear()
            PolicyAutomationRetryAfter = 0L
            PolicyAutomationFailureCount = 0
        }
    }

    private fun SchedulePolicyAction(DelayMs: Long, ActionRef: () -> Unit) {
        PolicyAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }

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

            PolicyAutomationRunnable = null
            ActionRef()
        }
        PolicyAutomationRunnable = WrappedRunnable
        MainHandler.postDelayed(WrappedRunnable, DelayMs)
    }

    private fun ClickPolicyPageSelector(RootNode: AccessibilityNodeInfo, CurrentPage: Int): Boolean {
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
        if (ClickPageNumberByTraversal(
                TargetNode = RootNode,
                PageNumber = CurrentPage,
                RequireTopRight = true,
                ActionName = "open-selector"
            )
        ) {
            return true
        }
        return ClickSpinnerNode(TargetNode = RootNode, ActionValue = AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun AdvancePolicyPageSelector(RootNode: AccessibilityNodeInfo, CurrentPage: Int): Boolean {
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
                    if (ClickNodeOrParent(StartNode = MatchNode)) {
                        Log.d(LOG_TAG, "Selected policy page $PageNumber")
                        return true
                    }
                    val MatchBounds = Rect()
                    MatchNode.getBoundsInScreen(MatchBounds)
                    if (IsRightSidePageOption(NodeRef = MatchNode) && PerformTapGesture(
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
                BoundsObj.centerY() <= ScreenHeight * 0.70f
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
                    if (ClickSpinnerNode(TargetNode = ChildNode, ActionValue = ActionValue)) return true
                } finally {
                    RecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            Log.v(LOG_TAG, "Page spinner became stale", ExceptionObj)
        }
        return false
    }

    // ----------------------------------------------- renewal history flow

    /**
     * Landing screen behind the Renewals bottom tab. It stacks BUSINESS
     * METRICS, RENEWALS DUE and RENEWAL HISTORY, so it is distinguished from
     * the Renewal History list page by the metrics header rather than by the
     * section title, which appears on both.
     */
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

        if (!IsRenewalHistoryScreen(VisibleNodes = VisibleNodes)) {
            IsRenewalPageSelectorVisible = false
            return
        }
        val PageInfo = ParsePolicyPageInfo(VisibleNodes = VisibleNodes)
        IsRenewalPageSelectorVisible = PageInfo != null
        if (PageInfo != null) {
            val PreviousPage = RenewalCurrentPage
            RenewalCurrentPage = PageInfo.first
            RenewalTotalPages = PageInfo.second
            if (PreviousPage != RenewalCurrentPage) {
                DiagnosticInfo(
                    EventName = "RENEWAL_PAGE_DETECTED",
                    MessageText = "page=$RenewalCurrentPage total=$RenewalTotalPages"
                )
            }
        }
    }

    private fun HandleRenewalScreenAutomation(VisibleNodes: List<String>) {
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
            RunRenewalAutomationStep()
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
            } else {
                ScrollRenewalHistoryPage()
            }
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

    // ------------------------------------ dashboard -> renewal history list

    /**
     * The dashboard carries two "View all" links. The RENEWALS DUE one sits
     * higher up, so the tap is anchored to the row occupied by the RENEWAL
     * HISTORY header instead of taking the first match.
     */
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
            TapRenewalHistoryViewAll(RootNode = RootNode)
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

    private fun TapRenewalHistoryViewAll(RootNode: AccessibilityNodeInfo): Boolean {
        val TextNodes = CollectVisibleTextNodes(RootNode = RootNode)
        val HeaderBounds = TextNodes.firstOrNull { NodeEntry ->
            NodeEntry.first.trim().equals("Renewal History", ignoreCase = true)
        }?.second
        if (HeaderBounds == null) {
            DiagnosticInfo(
                EventName = "RENEWAL_VIEW_ALL_SEARCH",
                MessageText = "RENEWAL HISTORY header is not on screen yet"
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

    // -------------------------------------------- date-range filter dropdown

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

        val ChipTapped = try {
            TapRenewalDateRangeChip(RootNode = RootNode)
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        if (!ChipTapped) {
            RenewalDropdownAttempts++
            if (RenewalDropdownAttempts >= RENEWAL_DROPDOWN_RETRY_LIMIT) {
                // The default range still yields rows, so capture what is on
                // screen rather than abandoning the session entirely.
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

    private fun TapRenewalDateRangeChip(RootNode: AccessibilityNodeInfo): Boolean {
        val TextNodes = CollectVisibleTextNodes(RootNode = RootNode)
        val ChipEntry = TextNodes.firstOrNull { NodeEntry ->
            IsDateRangeLabel(TextValue = NodeEntry.first)
        } ?: run {
            DiagnosticWarning(
                EventName = "RENEWAL_DATE_RANGE_CHIP",
                MessageText = "No date-range chip matched among ${TextNodes.size} visible nodes"
            )
            return false
        }

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

    private fun IsDateRangeLabel(TextValue: String): Boolean {
        val NormalisedText = TextValue.trim().replace(Regex("\\s+"), " ")
        return Regex("(?i)^Last\\s+\\d+\\s+(Day|Days|Week|Weeks|Month|Months|Year|Years)$")
            .matches(NormalisedText) ||
                Regex("(?i)^(Today|Yesterday|This Month|This Year|All Time|Custom)$")
                    .matches(NormalisedText)
    }

    /**
     * The dropdown is a Flutter overlay, so its options are identified as the
     * text nodes that were not present before the chip was tapped. The
     * bottom-most of those is the last entry.
     */
    private fun SelectLastRenewalDateRangeOption() {
        val RootNode = FindReadableRoot(
            ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        ) ?: run {
            ScheduleRenewalAction(DelayMs = RENEWAL_SCROLL_SETTLE_MS) {
                SelectLastRenewalDateRangeOption()
            }
            return
        }

        val OptionEntries = try {
            CollectVisibleTextNodes(RootNode = RootNode).filter { NodeEntry ->
                val OptionText = NodeEntry.first.trim()
                OptionText.isNotEmpty() &&
                        OptionText.length <= 40 &&
                        !RenewalDropdownBaselineTexts.contains(NodeEntry.first)
            }
        } finally {
            RecycleNode(NodeRef = RootNode)
        }

        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_OPTIONS",
            MessageText = "newOptions=${OptionEntries.size} " +
                    "texts=${OptionEntries.take(12).map { Entry -> Entry.first }} " +
                    "scrollPasses=$RenewalDropdownScrollPasses"
        )

        if (OptionEntries.isEmpty()) {
            RenewalDropdownAttempts++
            if (RenewalDropdownAttempts >= RENEWAL_DROPDOWN_RETRY_LIMIT) {
                DiagnosticWarning(
                    EventName = "RENEWAL_DATE_RANGE_SKIPPED",
                    MessageText = "Dropdown options never appeared; continuing with the default filter"
                )
                performGlobalAction(GLOBAL_ACTION_BACK)
                HasSelectedRenewalDateRange = true
                ScheduleRenewalAction(DelayMs = RENEWAL_PAGE_LOAD_DELAY_MS) {
                    RunRenewalAutomationStep()
                }
                return
            }
            ScheduleRenewalAction(DelayMs = RENEWAL_DROPDOWN_OPEN_DELAY_MS) {
                SelectLastRenewalDateRangeOption()
            }
            return
        }

        val BottomOption = OptionEntries.maxByOrNull { NodeEntry -> NodeEntry.second.centerY() }
            ?: return

        // A list that runs to the bottom edge probably has more entries below,
        // so scroll once and re-read before committing to a choice.
        val ScreenHeight = resources.displayMetrics.heightPixels
        val LooksClipped = BottomOption.second.bottom >= ScreenHeight * 0.92f
        if (LooksClipped && RenewalDropdownScrollPasses < 3) {
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

        val TapAccepted = PerformTapGesture(
            XPos = BottomOption.second.centerX().toFloat(),
            YPos = BottomOption.second.centerY().toFloat()
        )
        DiagnosticInfo(
            EventName = "RENEWAL_DATE_RANGE_SELECTED",
            MessageText = "text=[${BottomOption.first}] bounds=${BottomOption.second} " +
                    "accepted=$TapAccepted"
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

    // ------------------------------------------ renewal history pagination

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

        RenewalReturnToTopCount = 0
        RenewalPageRetryCount = 0
        ScheduleRenewalAction(DelayMs = RENEWAL_NAVIGATION_DELAY_MS) {
            ReturnToRenewalPageSelector()
        }
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
            FailRenewalAutomation("Could not return to the renewal page selector")
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
        RenewalExpectedPage = if (RenewalCurrentPage > 0) RenewalCurrentPage + 1 else 2

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
            SelectorAdvanced = if (!SelectorClicked) {
                AdvancePolicyPageSelector(
                    RootNode = RootNode,
                    CurrentPage = RenewalCurrentPage
                )
            } else {
                false
            }
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
                RenewalPageRetryCount++
                DiagnosticWarning(
                    EventName = "RENEWAL_PAGE_WAIT",
                    MessageText = "expected=$RenewalExpectedPage actual=$RenewalCurrentPage " +
                            "selectorVisible=$IsRenewalPageSelectorVisible attempt=$RenewalPageRetryCount"
                )
                if (RenewalPageRetryCount >= RENEWAL_PAGE_RETRY_LIMIT) {
                    FailRenewalAutomation("Renewal page $RenewalExpectedPage did not finish loading")
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

    private fun CompleteRenewalAutomation() {
        if (IsRenewalAutomationComplete) return
        IsRenewalAutomationComplete = true
        IsRenewalAutomationRunning = false
        RenewalAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        RenewalAutomationRunnable = null

        val RecordCount = CapturedFupMap.size
        DiagnosticInfo(
            EventName = "RENEWAL_AUTOMATION_COMPLETE",
            MessageText = "records=$RecordCount nodes=${CapturedNodes.size} " +
                    "page=$RenewalCurrentPage/$RenewalTotalPages"
        )
        HapticFeedback.Success(ContextRef = this)
        Toast.makeText(
            this,
            "Captured $RecordCount renewal records",
            Toast.LENGTH_LONG
        ).show()

        MainHandler.postDelayed({
            if (IsCapturing && CurrentMode == CaptureMode.FUP && IsRenewalAutomationComplete) {
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
        val CanRetryAutomatically = RenewalAutomationFailureCount < RENEWAL_AUTOMATION_RECOVERY_LIMIT
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
        HapticFeedback.Failure(ContextRef = this)
        Toast.makeText(
            this,
            if (CanRetryAutomatically) {
                "Renewal automation paused: $ReasonText. Retrying automatically."
            } else {
                "Renewal automation stopped after repeated failures. Captured data is preserved."
            },
            Toast.LENGTH_LONG
        ).show()
        RefreshBubble()
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
            RenewalUnknownScreenCount = 0
            RenewalCurrentPage = 0
            RenewalTotalPages = 0
            RenewalExpectedPage = 0
            RenewalPageRetryCount = 0
            RenewalReturnToTopCount = 0
            RenewalScrollStallCount = 0
            LatestRenewalVisibleSignature = 0
            LatestRenewalVisibleNodes = emptyList()
            IsRenewalPageSelectorVisible = false
            RenewalAutomationRetryAfter = 0L
            RenewalAutomationFailureCount = 0
        }
    }

    private fun ScheduleRenewalAction(DelayMs: Long, ActionRef: () -> Unit) {
        RenewalAutomationRunnable?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }

        lateinit var WrappedRunnable: Runnable
        WrappedRunnable = Runnable {
            if (!IsRenewalAutomationRunning ||
                !IsCapturing ||
                CurrentMode != CaptureMode.FUP
            ) {
                return@Runnable
            }
            if (IsPaused) {
                RenewalAutomationRunnable = WrappedRunnable
                MainHandler.postDelayed(WrappedRunnable, TICK_INTERVAL_MS)
                return@Runnable
            }

            RenewalAutomationRunnable = null
            ActionRef()
        }
        RenewalAutomationRunnable = WrappedRunnable
        MainHandler.postDelayed(WrappedRunnable, DelayMs)
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
        MainHandler.postDelayed(RunnableObj, AUTO_SCROLL_START_DELAY_MS)
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

    private fun PerformScrollOnNode(TargetNode: AccessibilityNodeInfo, ForwardVal: Boolean): Boolean {
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
                    if (PerformScrollOnNode(TargetNode = ChildNode, ForwardVal = ForwardVal)) return true
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

    private fun PerformPolicyRevealNudge(): Boolean {
        val DisplayMetricsObj = resources.displayMetrics
        val ScrollPath = Path().apply {
            moveTo(
                DisplayMetricsObj.widthPixels * 0.5f,
                DisplayMetricsObj.heightPixels * 0.78f
            )
            lineTo(
                DisplayMetricsObj.widthPixels * 0.5f,
                DisplayMetricsObj.heightPixels * 0.58f
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

    /**
     * Keeps the normal movement smooth, but switches to the WebView's native
     * accessibility scroll action after a gesture produces no new content.
     * This is the automatic equivalent of the small manual nudge previously
     * needed to wake the dashboard list.
     */
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

        // Verify that at least one of the labels belongs to this accessibility
        // tree before scheduling clicks against fresh roots.
        val HasExpandableLabel = listOf("Policy Details", "Commissions", "Key Dates").any { LabelText ->
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

            val FreshRoot = FindReadableRoot(ExpectedPackage = AppLauncherUtils.LIC_SUPER_APP_PACKAGE)
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
                // Usually means the header is below the fold. Hand over to the
                // scroll-aware attempt rather than dropping the section.
                ScheduleSectionExpansionAttempt(
                    LabelText = LabelText,
                    DelayMs = POLICY_SECTION_SCROLL_SETTLE_MS,
                    AttemptCount = 0
                )
            }
        }, DelayMs)
    }

    /**
     * Brings a section header into the tappable band and then taps its chevron,
     * re-posting itself after each scroll. The previous version only looked for
     * headers already on screen and abandoned anything below the fold, which is
     * why Key Dates was never expanded.
     */
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
        // A gesture on the visible chevron is more dependable for this WebView. Its
        // accessibility ACTION_CLICK often reports success without changing the section.
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
                        val Clicked = CandidateNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

    // ------------------------------------------------------- live parse loop

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

    /**
     * Reparses on a worker thread so the bubble can show records rather than
     * raw node counts. Skipped when nothing new arrived since the last pass.
     */
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

    // --------------------------------------------------------------- overlay

    @SuppressLint("InflateParams")
    private fun ShowBubble() {
        if (IsOverlayAdded || WindowMgr == null) return

        try {
            val ThemedContext = ContextThemeWrapper(this, R.style.Theme_DataReaderApp)
            val RootView = LayoutInflater.from(ThemedContext).inflate(R.layout.view_capture_bubble, null)
            BubbleView = RootView

            PillContainer = RootView.findViewById(R.id.pillContainer)
            CardActions = RootView.findViewById(R.id.cardActions)
            TvBubbleCount = RootView.findViewById(R.id.tvBubbleCount)
            TvBubbleMeta = RootView.findViewById(R.id.tvBubbleMeta)
            TvBubblePause = RootView.findViewById(R.id.btnBubblePause)

            // An accessibility overlay is trusted by the accessibility
            // framework and does not require the separate draw-over-apps
            // permission used by ordinary application overlays.
            val LayoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

            val LayoutParamsObj = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LayoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 220
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

    /**
     * Drag lives on the pill only, so the buttons in the expanded card keep
     * their own click handling. A tap under the slop threshold toggles the card
     * instead of ending the session outright.
     */
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
                    LayoutParamsObj.x = InitialXPos - (MotionEvt.rawX - InitialTouchXVal).toInt()
                    LayoutParamsObj.y = InitialYPos + (MotionEvt.rawY - InitialTouchYVal).toInt()
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
                Toast.makeText(this, "No diagnostic log is available yet", Toast.LENGTH_SHORT)
                    .show()
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
            Toast.makeText(this, "Unable to share the diagnostic log", Toast.LENGTH_LONG).show()
        }
    }

    private fun RefreshBubble() {
        if (!IsOverlayAdded) return

        val RecordCount = LatestRecords.size
        val NodeCount = CapturedNodes.size
        val ElapsedValue = ElapsedMs()

        TvBubbleCount?.text = when {
            IsPaused -> getString(R.string.bubble_paused)
            RecordCount == 0 -> getString(R.string.bubble_starting)
            else -> CurrentMode.DescribeCount(CountVal = RecordCount)
        }

        TvBubbleMeta?.text = getString(
            R.string.bubble_meta_format,
            CaptureSession.FormatClock(DurationMsVal = ElapsedValue),
            NodeCount
        )

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

    // ---------------------------------------------------------------- system

    private fun StartForegroundNotification() {
        val ChannelIdStr = "DataReaderServiceChannel"
        val ChannelNameStr = "Screen Reader Automation Service"

        val NotificationMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ChannelObj = NotificationChannel(ChannelIdStr, ChannelNameStr, NotificationManager.IMPORTANCE_LOW)
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
                WakeLockObj = PowerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DataReaderApp:WakeLock")
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

    override fun onInterrupt() {
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = false)
        StopRenewalAutomation(ResetStateVal = false)
        CancelEventWindowCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        Instance = null
        IsCapturing = false
        IsPaused = false
        MainHandler.removeCallbacks(TickRunnable)
        StopAutoScroll()
        StopPolicyDashboardAutomation(ResetStateVal = false)
        StopRenewalAutomation(ResetStateVal = false)
        CancelEventWindowCapture()
        StopParseThread()
        RemoveBubble()
        ReleaseWakeLock()
        CaptureSessionState.OnSessionEnded()
    }
}
