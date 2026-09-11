@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.utils

import android.content.Context
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.repository.PolicyRepository

object SessionLabels {

    data class Label(val TitleText: String, val CapsuleText: String) {
        val HasCapsule: Boolean get() = CapsuleText.isNotEmpty()
    }

    fun TypeText(
        ContextRef: Context,
        ModeVal: CaptureMode,
        CapturePolicyDetails: Boolean
    ): String {
        return ContextRef.getString(
            when {
                ModeVal == CaptureMode.FUP -> R.string.sessions_type_renewals
                ModeVal == CaptureMode.RENEWAL_DUE -> R.string.sessions_type_renewals_due
                CapturePolicyDetails -> R.string.sessions_type_full
                else -> R.string.sessions_type_fast
            }
        )
    }

    fun OriginalTitle(
        ContextRef: Context,
        ModeVal: CaptureMode,
        RecordCount: Int,
        CapturePolicyDetails: Boolean
    ): String {
        return ContextRef.getString(
            R.string.sessions_title_mode_format,
            ModeVal.DescribeCount(CountVal = RecordCount),
            TypeText(
                ContextRef = ContextRef,
                ModeVal = ModeVal,
                CapturePolicyDetails = CapturePolicyDetails
            )
        )
    }

    fun OriginalTitle(
        ContextRef: Context,
        SessionRef: PolicyRepository.CaptureSessionReference
    ): String {
        return OriginalTitle(
            ContextRef = ContextRef,
            ModeVal = SessionRef.Mode,
            RecordCount = SessionRef.RecordCount,
            CapturePolicyDetails = SessionRef.CapturePolicyDetails
        )
    }

    fun Of(
        ContextRef: Context,
        SessionRef: PolicyRepository.CaptureSessionReference
    ): Label {
        val CustomName = PolicyRepository.GetSessionName(
            ContextRef = ContextRef,
            SessionId = SessionRef.SessionId
        )
        if (CustomName.isEmpty()) {
            return Label(
                TitleText = OriginalTitle(ContextRef = ContextRef, SessionRef = SessionRef),
                CapsuleText = ""
            )
        }
        return Label(
            TitleText = CustomName,
            CapsuleText = TypeText(
                ContextRef = ContextRef,
                ModeVal = SessionRef.Mode,
                CapturePolicyDetails = SessionRef.CapturePolicyDetails
            )
        )
    }

    fun TitleOf(
        ContextRef: Context,
        SessionRef: PolicyRepository.CaptureSessionReference
    ): String = Of(ContextRef = ContextRef, SessionRef = SessionRef).TitleText

    fun NameOrFallback(
        ContextRef: Context,
        SessionId: String,
        FallbackText: String
    ): String {
        val CustomName = PolicyRepository.GetSessionName(
            ContextRef = ContextRef,
            SessionId = SessionId
        )
        return CustomName.ifEmpty { FallbackText }
    }

    fun RowDescription(ContextRef: Context, LabelRef: Label): String {
        if (!LabelRef.HasCapsule) return LabelRef.TitleText
        return ContextRef.getString(
            R.string.sessions_row_description_format,
            LabelRef.TitleText,
            LabelRef.CapsuleText
        )
    }
}
