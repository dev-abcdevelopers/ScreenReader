@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession

/**
 * The bridge between the accessibility service and whichever activity started
 * the capture. The service publishes here; activities observe.
 *
 * A finished session parks in [PendingSession] until the user either saves or
 * discards it in the review sheet. Nothing is written to storage before that,
 * so a bad parse costs the user a tap rather than the whole capture.
 */
object CaptureSessionState {

    private val MutableIsCapturing = MutableLiveData(false)
    val IsCapturingLive: LiveData<Boolean> get() = MutableIsCapturing

    private val MutableIsPaused = MutableLiveData(false)
    val IsPausedLive: LiveData<Boolean> get() = MutableIsPaused

    private val MutableRecordCount = MutableLiveData(0)
    val RecordCountLive: LiveData<Int> get() = MutableRecordCount

    private val MutableNodeCount = MutableLiveData(0)
    val NodeCountLive: LiveData<Int> get() = MutableNodeCount

    private val MutableElapsedMs = MutableLiveData(0L)
    val ElapsedMsLive: LiveData<Long> get() = MutableElapsedMs

    private val MutablePendingSession = MutableLiveData<CaptureSession?>(null)
    val PendingSessionLive: LiveData<CaptureSession?> get() = MutablePendingSession

    @Volatile
    var ActiveMode: CaptureMode = CaptureMode.POLICY
        private set

    @Volatile
    var ActiveSessionId: String = ""
        private set

    /** Read directly by activities in onResume, before any observer fires. */
    @Volatile
    var PendingSession: CaptureSession? = null
        private set

    fun OnSessionStarted(ModeVal: CaptureMode, SessionIdVal: String) {
        ActiveMode = ModeVal
        ActiveSessionId = SessionIdVal
        MutableIsCapturing.postValue(true)
        MutableIsPaused.postValue(false)
        MutableRecordCount.postValue(0)
        MutableNodeCount.postValue(0)
        MutableElapsedMs.postValue(0L)
    }

    fun OnProgress(RecordCountVal: Int, NodeCountVal: Int, ElapsedMsVal: Long) {
        MutableRecordCount.postValue(RecordCountVal)
        MutableNodeCount.postValue(NodeCountVal)
        MutableElapsedMs.postValue(ElapsedMsVal)
    }

    fun OnPausedChanged(IsPausedVal: Boolean) {
        MutableIsPaused.postValue(IsPausedVal)
    }

    fun OnSessionEnded() {
        MutableIsCapturing.postValue(false)
        MutableIsPaused.postValue(false)
    }

    fun PublishPending(SessionObj: CaptureSession) {
        PendingSession = SessionObj
        MutablePendingSession.postValue(SessionObj)
    }

    fun ConsumePending() {
        PendingSession = null
        MutablePendingSession.postValue(null)
    }
}
