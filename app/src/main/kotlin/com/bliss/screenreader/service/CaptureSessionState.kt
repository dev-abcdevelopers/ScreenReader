@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession


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
