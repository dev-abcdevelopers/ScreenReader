@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.sync

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.PsPolicy
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.model.SessionGap

data class UploadAppInfo(
    val PackageName: String,
    val VersionName: String,
    val VersionCode: Int,
    val Flavour: String
)

data class UploadDeviceInfo(
    val RegistrationId: String,
    val Manufacturer: String,
    val Model: String,
    val AndroidRelease: String,
    val SdkInt: Int
)

data class UploadSessionInfo(
    val SessionId: String,
    val Mode: String,
    val SavedAt: Long,
    val LastResumedAt: Long,
    val RecordCount: Int,
    val CapturePolicyDetails: Boolean,
    val ChangeCount: Int
)

data class SessionUploadPayload(
    val SchemaVersion: Int,
    val AgencyCode: String,
    val GeneratedAt: Long,
    val App: UploadAppInfo,
    val Device: UploadDeviceInfo,
    val Session: UploadSessionInfo,
    val Policies: List<CustomerPolicy>,
    val Renewals: List<FupPolicy>,
    val Servicing: List<PsPolicy>,
    val Gaps: List<SessionGap>,
    val Changes: Map<String, List<RecordFieldChange>>,
    val VisitedCustomers: List<String>
) {
    val TotalRecordCount: Int
        get() = Policies.size + Renewals.size + Servicing.size
}
