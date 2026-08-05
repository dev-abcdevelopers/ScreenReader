@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

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
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.data.parser.CaptureParsers
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("AccessibilityPolicy")
class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TICK_INTERVAL_MS = 1000L
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
    private var OriginActivityName: String = ""
    private var SessionStartedAt: Long = 0L
    private var PausedTotalMs: Long = 0L
    private var PausedAt: Long = 0L
    private var LatestRecords: List<ParsedRecord> = emptyList()
    private var LastParsedNodeCount: Int = -1

    private val MainHandler = Handler(Looper.getMainLooper())
    private var ParseThread: HandlerThread? = null
    private var ParseHandler: Handler? = null

    private var TvBubbleCount: TextView? = null
    private var TvBubbleMeta: TextView? = null
    private var TvBubblePause: TextView? = null
    private var PillContainer: LinearLayout? = null
    private var CardActions: LinearLayout? = null

    private val TickRunnable = object : Runnable {
        override fun run() {
            if (!IsCapturing) return
            RefreshBubble()
            RequestIncrementalParse()
            MainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Instance = this

        val ServiceConfigInfo = AccessibilityServiceInfo().apply {
            eventTypes = android.view.accessibility.AccessibilityEvent.TYPES_ALL_MASK
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

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        if (event == null || !IsCapturing || IsPaused) return

        val PackageNameStr = event.packageName?.toString() ?: return
        if (ShouldIgnorePackage(PackageNameVal = PackageNameStr)) return

        LastPackageName = PackageNameStr
        val RootNodeInfo = rootInActiveWindow ?: return
        val NodeList = mutableListOf<String>()
        TraverseNode(TargetNode = RootNodeInfo, ResultList = NodeList)

        for (NodeTextItem in NodeList) {
            if (!CapturedNodes.contains(NodeTextItem)) {
                CapturedNodes.add(NodeTextItem)
            }
        }
    }

    private fun TraverseNode(TargetNode: android.view.accessibility.AccessibilityNodeInfo?, ResultList: MutableList<String>) {
        if (TargetNode == null) return
        val TextContent = TargetNode.text?.toString()?.trim()
        val DescContent = TargetNode.contentDescription?.toString()?.trim()

        if (!TextContent.isNullOrEmpty() && TextContent.length > 1) {
            ResultList.add(TextContent)
        } else if (!DescContent.isNullOrEmpty() && DescContent.length > 1) {
            ResultList.add(DescContent)
        }

        for (ChildIdx in 0 until TargetNode.childCount) {
            TraverseNode(TargetNode = TargetNode.getChild(ChildIdx), ResultList = ResultList)
        }
    }

    private fun ShouldIgnorePackage(PackageNameVal: String): Boolean {
        if (PackageNameVal == packageName) return true
        if (PackageNameVal.contains("launcher", ignoreCase = true)) return true
        if (PackageNameVal.contains("systemui", ignoreCase = true)) return true
        if (PackageNameVal.contains("keyboard", ignoreCase = true)) return true
        return false
    }

    // ------------------------------------------------------------- lifecycle

    fun StartCaptureSession(ModeVal: CaptureMode, OriginActivityVal: String = "") {
        CurrentMode = ModeVal
        OriginActivityName = OriginActivityVal
        SessionStartedAt = System.currentTimeMillis()
        PausedTotalMs = 0L
        PausedAt = 0L
        LatestRecords = emptyList()
        LastParsedNodeCount = -1

        IsCapturing = true
        IsPaused = false
        CapturedNodes.clear()

        CaptureSessionState.OnSessionStarted(ModeVal = ModeVal)
        StartParseThread()
        ShowBubble()
        AcquireWakeLock()

        MainHandler.removeCallbacks(TickRunnable)
        MainHandler.post(TickRunnable)
    }

    fun SetPaused(PausedVal: Boolean) {
        if (!IsCapturing || IsPaused == PausedVal) return
        IsPaused = PausedVal
        if (PausedVal) {
            PausedAt = System.currentTimeMillis()
        } else {
            PausedTotalMs += System.currentTimeMillis() - PausedAt
            PausedAt = 0L
        }
        CaptureSessionState.OnPausedChanged(IsPausedVal = PausedVal)
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

        TeardownSession()

        val RecordList = try {
            CaptureParsers.Preview(ModeVal = CurrentMode, Nodes = NodeSnapshot)
        } catch (_: Exception) {
            emptyList()
        }

        CaptureSessionState.PublishPending(
            SessionObj = CaptureSession(
                Mode = CurrentMode,
                StartedAt = SessionStartedAt,
                EndedAt = EndedAt - PausedTotalMs,
                RawNodes = NodeSnapshot,
                Records = RecordList,
                TargetPackage = LastPackageName,
                OriginActivity = OriginActivityName
            )
        )

        ReturnToOriginActivity()
    }

    fun DiscardCaptureSession() {
        if (!IsCapturing) return
        TeardownSession()
        CapturedNodes.clear()
    }

    private fun TeardownSession() {
        IsCapturing = false
        IsPaused = false
        MainHandler.removeCallbacks(TickRunnable)
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

    fun PerformAutoScrollGesture() {
        val DisplayMetricsObj = resources.displayMetrics
        val StartXVal = DisplayMetricsObj.widthPixels / 2f
        val StartYVal = DisplayMetricsObj.heightPixels * 0.75f
        val EndYVal = DisplayMetricsObj.heightPixels * 0.25f

        val ScrollPath = Path().apply {
            moveTo(StartXVal, StartYVal)
            lineTo(StartXVal, EndYVal)
        }

        val GestureObj = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(ScrollPath, 0, 400))
            .build()

        dispatchGesture(GestureObj, null, null)
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

            val LayoutType =
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

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

            TvBubblePause?.setOnClickListener { SetPaused(PausedVal = !IsPaused) }
            RootView.findViewById<TextView>(R.id.btnBubbleFinish).setOnClickListener { FinishCaptureSession() }
            RootView.findViewById<TextView>(R.id.btnBubbleDiscard).setOnClickListener { DiscardCaptureSession() }

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
                    val DiffXVal = kotlin.math.abs(MotionEvt.rawX - InitialTouchXVal)
                    val DiffYVal = kotlin.math.abs(MotionEvt.rawY - InitialTouchYVal)
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

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        Instance = null
        IsCapturing = false
        IsPaused = false
        MainHandler.removeCallbacks(TickRunnable)
        StopParseThread()
        RemoveBubble()
        ReleaseWakeLock()
        CaptureSessionState.OnSessionEnded()
    }
}
