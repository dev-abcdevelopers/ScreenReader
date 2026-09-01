@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.sync

import android.content.Context
import android.os.Build
import com.bliss.screenreader.BuildConfig
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.security.DeviceIdentity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.Locale

object SessionPayloadBuilder {

    const val SCHEMA_VERSION = 2

    private const val UPLOAD_DIR_NAME = "Upload"
    private const val FALLBACK_AGENCY_CODE = "unknown"
    private const val MAX_CODE_LENGTH = 64
    private const val PAYLOAD_EXTENSION = ".json"

    private val GsonInstance: Gson = GsonBuilder().serializeNulls().create()

    fun NormalizeAgencyCode(AgencyCode: String): String {
        val LoweredCode = AgencyCode.trim().lowercase(Locale.ROOT)
        val SafeCode = LoweredCode.replace(Regex("[^a-z0-9_-]"), "")
        if (SafeCode.isEmpty()) return FALLBACK_AGENCY_CODE
        return SafeCode.take(MAX_CODE_LENGTH)
    }

    fun ObjectKeyFor(AgencyCode: String): String =
        "${NormalizeAgencyCode(AgencyCode = AgencyCode)}$PAYLOAD_EXTENSION"

    fun Build(
        ContextRef: Context,
        SessionId: String,
        AgencyCode: String
    ): SessionUploadPayload {
        val AppContext = ContextRef.applicationContext
        val SessionRef = PolicyRepository.GetSessionReference(
            ContextRef = AppContext,
            SessionId = SessionId
        )

        val ChangeMap = HashMap<String, List<RecordFieldChange>>()
        for (ModeVal in CaptureMode.entries) {
            val ChangeList = PolicyRepository.GetFieldChanges(
                ContextRef = AppContext,
                ModeVal = ModeVal,
                SessionId = SessionId
            )
            if (ChangeList.isNotEmpty()) ChangeMap[ModeVal.name] = ChangeList
        }

        return SessionUploadPayload(
            SchemaVersion = SCHEMA_VERSION,
            AgencyCode = NormalizeAgencyCode(AgencyCode = AgencyCode),
            GeneratedAt = System.currentTimeMillis(),
            App = UploadAppInfo(
                PackageName = BuildConfig.APPLICATION_ID,
                VersionName = BuildConfig.VERSION_NAME,
                VersionCode = BuildConfig.VERSION_CODE,
                Flavour = BuildConfig.FLAVOR
            ),
            Device = UploadDeviceInfo(
                RegistrationId = DeviceIdentity.RegistrationId(ContextRef = AppContext),
                Manufacturer = Build.MANUFACTURER.orEmpty(),
                Model = Build.MODEL.orEmpty(),
                AndroidRelease = Build.VERSION.RELEASE.orEmpty(),
                SdkInt = Build.VERSION.SDK_INT
            ),
            Session = UploadSessionInfo(
                SessionId = SessionId,
                Mode = (SessionRef?.Mode ?: CaptureMode.POLICY).name,
                SavedAt = SessionRef?.SavedAt ?: 0L,
                LastResumedAt = SessionRef?.LastResumedAt ?: 0L,
                RecordCount = SessionRef?.RecordCount ?: 0,
                CapturePolicyDetails = SessionRef?.CapturePolicyDetails ?: false,
                ChangeCount = SessionRef?.ChangeCount ?: 0
            ),
            Policies = PolicyRepository.GetCustomerPolicies(
                ContextRef = AppContext,
                SessionId = SessionId
            ),
            Renewals = PolicyRepository.GetFupPolicies(
                ContextRef = AppContext,
                SessionId = SessionId
            ),
            RenewalsDue = PolicyRepository.GetRenewalDuePolicies(
                ContextRef = AppContext,
                SessionId = SessionId
            ),
            Servicing = PolicyRepository.GetPsPolicies(
                ContextRef = AppContext,
                SessionId = SessionId
            ),
            Gaps = PolicyRepository.GetSessionGaps(
                ContextRef = AppContext,
                SessionId = SessionId
            ),
            Changes = ChangeMap,
            VisitedCustomers = PolicyRepository.GetVisitedCustomers(
                ContextRef = AppContext,
                SessionId = SessionId
            ).sorted()
        )
    }

    fun UploadDirectory(ContextRef: Context): File {
        val AppContext = ContextRef.applicationContext
        val TargetDir = AppContext.getExternalFilesDir(UPLOAD_DIR_NAME)
            ?: File(AppContext.filesDir, UPLOAD_DIR_NAME)
        if (!TargetDir.exists()) TargetDir.mkdirs()
        return TargetDir
    }

    fun WriteJsonFile(ContextRef: Context, PayloadObj: SessionUploadPayload): File {
        val TargetFile = File(
            UploadDirectory(ContextRef = ContextRef),
            ObjectKeyFor(AgencyCode = PayloadObj.AgencyCode)
        )
        TargetFile.writeText(GsonInstance.toJson(PayloadObj), Charsets.UTF_8)
        return TargetFile
    }
}
