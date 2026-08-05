@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.service.ScreenReaderService
import com.google.android.material.snackbar.Snackbar

/**
 * Shared entry and exit points for the three capture screens, so each activity
 * only has to say which [CaptureMode] it wants.
 */
object CaptureFlow {

    @SuppressLint("ObsoleteSdkInt")
    fun CanDrawOverlay(ContextRef: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ContextRef)
        } else {
            true
        }
    }

    fun RequestOverlayPermission(ActivityRef: AppCompatActivity) {
        try {
            ActivityRef.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${ActivityRef.packageName}".toUri()
                )
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Starts a capture and hands control to the bubble. Returns false when a
     * prerequisite is missing, having already told the user which one.
     */
    fun Start(
        ActivityRef: AppCompatActivity,
        ModeVal: CaptureMode,
        LaunchTarget: Boolean = false,
        OriginOverride: String = ""
    ): Boolean {
        val ServiceInstance = ScreenReaderService.Instance
        if (ServiceInstance == null) {
            ShowMessage(ActivityRef = ActivityRef, MessageVal = ActivityRef.getString(R.string.capture_service_disabled))
            return false
        }

        if (!CanDrawOverlay(ContextRef = ActivityRef)) {
            ShowAction(
                ActivityRef = ActivityRef,
                MessageVal = ActivityRef.getString(R.string.capture_overlay_required),
                ActionLabel = ActivityRef.getString(R.string.btn_settings)
            ) { RequestOverlayPermission(ActivityRef = ActivityRef) }
            return false
        }

        // A screen that finishes itself on start must nominate somewhere else to
        // come back to, or the service reopens a dead activity.
        ServiceInstance.StartCaptureSession(
            ModeVal = ModeVal,
            OriginActivityVal = OriginOverride.ifEmpty { ActivityRef.javaClass.name }
        )

        if (LaunchTarget) {
            com.bliss.screenreader.utils.AppLauncherUtils.LaunchTargetApp(ContextRef = ActivityRef)
        }
        return true
    }

    fun Finish(ActivityRef: AppCompatActivity): Boolean {
        val ServiceInstance = ScreenReaderService.Instance
        if (ServiceInstance == null || !ScreenReaderService.IsCapturing) {
            ShowMessage(ActivityRef = ActivityRef, MessageVal = ActivityRef.getString(R.string.capture_no_session))
            return false
        }
        ServiceInstance.FinishCaptureSession()
        return true
    }

    /**
     * Call from onResume. Presents the review sheet if a finished capture for
     * this mode is waiting, and reports back how many records were saved.
     */
    fun ShowPendingReview(
        ActivityRef: AppCompatActivity,
        ModeVal: CaptureMode,
        OnResult: (Int) -> Unit
    ) {
        val SessionObj = CaptureSessionState.PendingSession ?: return
        if (SessionObj.Mode != ModeVal) return
        if (ActivityRef.supportFragmentManager.isStateSaved) return

        // A sheet can survive a rotation while its callback does not, so an
        // existing instance is re-attached rather than skipped.
        val ExistingSheet = ActivityRef.supportFragmentManager
            .findFragmentByTag(CaptureReviewSheet.TAG) as? CaptureReviewSheet
        if (ExistingSheet != null) {
            ExistingSheet.SetResultListener { SavedCount -> OnResult(SavedCount) }
            return
        }

        val SheetObj = CaptureReviewSheet.NewInstance()
        SheetObj.SetResultListener { SavedCount -> OnResult(SavedCount) }
        SheetObj.show(ActivityRef.supportFragmentManager, CaptureReviewSheet.TAG)
    }

    fun ShowMessage(ActivityRef: AppCompatActivity, MessageVal: String) {
        Snackbar.make(
            ActivityRef.findViewById(android.R.id.content),
            MessageVal,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun ShowAction(
        ActivityRef: AppCompatActivity,
        MessageVal: String,
        ActionLabel: String,
        ActionRef: () -> Unit
    ) {
        Snackbar.make(
            ActivityRef.findViewById(android.R.id.content),
            MessageVal,
            Snackbar.LENGTH_LONG
        ).setAction(ActionLabel) { ActionRef() }.show()
    }
}
