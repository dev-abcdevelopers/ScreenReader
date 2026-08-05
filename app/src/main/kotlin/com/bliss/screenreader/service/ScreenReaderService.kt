@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.bliss.screenreader.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

class ScreenReaderService : AccessibilityService() {

    companion object {
        @Volatile
        var Instance: ScreenReaderService? = null
            private set

        @Volatile
        var IsCapturing = false
            private set

        val CapturedNodes = CopyOnWriteArrayList<String>()
        var LastPackageName: String = ""

        fun IsServiceRunning(): Boolean = Instance != null
    }

    private var WindowMgr: WindowManager? = null
    private var FloatingOverlayView: View? = null
    private var IsOverlayAdded = false
    private var WakeLockObj: PowerManager.WakeLock? = null

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

        WindowMgr = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        StartForegroundNotification()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        if (event == null || !IsCapturing) return

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

    fun StartCaptureSession() {
        IsCapturing = true
        CapturedNodes.clear()
        ShowFloatingOverlay()
        AcquireWakeLock()
    }

    fun StopCaptureSession(): String {
        IsCapturing = false
        RemoveFloatingOverlay()
        ReleaseWakeLock()

        val JsonObj = JSONObject()
        JsonObj.put("packageName", LastPackageName)
        val NodesArr = JSONArray()
        for (NodeStr in CapturedNodes) {
            NodesArr.put(NodeStr)
        }
        JsonObj.put("nodes", NodesArr)
        JsonObj.put("timestamp", System.currentTimeMillis())
        return JsonObj.toString()
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

    private fun ShowFloatingOverlay() {
        if (IsOverlayAdded || WindowMgr == null) return

        try {
            val InflaterObj = LayoutInflater.from(this)
            FloatingOverlayView = InflaterObj.inflate(R.layout.view_floating_overlay, null)

            val LayoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val LayoutParamsObj = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LayoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 20
                y = 300
            }

            var InitialXPos = 0
            var InitialYPos = 0
            var InitialTouchXVal = 0f
            var InitialTouchYVal = 0f

            FloatingOverlayView?.setOnTouchListener { ViewRef, MotionEvt ->
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
                        WindowMgr?.updateViewLayout(FloatingOverlayView, LayoutParamsObj)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val DiffXVal = Math.abs(MotionEvt.rawX - InitialTouchXVal)
                        val DiffYVal = Math.abs(MotionEvt.rawY - InitialTouchYVal)
                        if (DiffXVal < 10 && DiffYVal < 10) {
                            ViewRef.performClick()
                        }
                        true
                    }
                    else -> false
                }
            }

            FloatingOverlayView?.setOnClickListener {
                StopCaptureSession()
            }

            WindowMgr?.addView(FloatingOverlayView, LayoutParamsObj)
            IsOverlayAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun RemoveFloatingOverlay() {
        if (IsOverlayAdded && FloatingOverlayView != null) {
            try {
                WindowMgr?.removeView(FloatingOverlayView)
                IsOverlayAdded = false
                FloatingOverlayView = null
            } catch (_: Exception) {}
        }
    }

    private fun StartForegroundNotification() {
        val ChannelIdStr = "DataReaderServiceChannel"
        val ChannelNameStr = "Screen Reader Automation Service"

        val NotificationMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ChannelObj = NotificationChannel(ChannelIdStr, ChannelNameStr, NotificationManager.IMPORTANCE_LOW)
            NotificationMgr.createNotificationChannel(ChannelObj)
        }

        val NotificationObj = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ChannelIdStr)
                .setContentTitle("Data Reader Service Active")
                .setContentText("Screen reader accessibility engine is active.")
                .setSmallIcon(R.drawable.ic_accessibility)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Data Reader Service Active")
                .setSmallIcon(R.drawable.ic_accessibility)
                .build()
        }

        startForeground(1001, NotificationObj)
    }

    private fun AcquireWakeLock() {
        try {
            if (WakeLockObj == null) {
                val PowerMgr = getSystemService(Context.POWER_SERVICE) as PowerManager
                WakeLockObj = PowerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DataReaderApp:WakeLock")
            }
            if (WakeLockObj?.isHeld == false) {
                WakeLockObj?.acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun ReleaseWakeLock() {
        try {
            if (WakeLockObj?.isHeld == true) {
                WakeLockObj?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        Instance = null
        RemoveFloatingOverlay()
        ReleaseWakeLock()
    }
}
