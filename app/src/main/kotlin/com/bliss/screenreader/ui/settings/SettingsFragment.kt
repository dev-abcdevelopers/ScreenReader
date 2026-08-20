@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SpellCheckingInspection", "UnusedVariable", "UsePropertyAccessSyntax", "unused"
)

package com.bliss.screenreader.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bliss.screenreader.BuildConfig
import com.bliss.screenreader.R
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.FragmentSettingsBinding
import com.bliss.screenreader.databinding.PartialFieldRowBinding
import com.bliss.screenreader.databinding.PartialSettingsChoiceRowBinding
import com.bliss.screenreader.databinding.PartialSettingsRowBinding
import com.bliss.screenreader.databinding.PartialSettingsSectionBinding
import com.bliss.screenreader.databinding.SheetAgencyCodeBinding
import com.bliss.screenreader.databinding.SheetSettingsDetailBinding
import com.bliss.screenreader.databinding.SheetSessionTransferBinding
import com.bliss.screenreader.security.AuthManager
import com.bliss.screenreader.security.BlissLicenceStore
import com.bliss.screenreader.security.DeviceIdentity
import com.bliss.screenreader.security.LicenceGate
import com.bliss.screenreader.security.MpinStore
import com.bliss.screenreader.service.CaptureDiagnostics
import com.bliss.screenreader.service.CustomerSheetOcr
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.settings.PaceProfile
import com.bliss.screenreader.settings.SettingsStore
import com.bliss.screenreader.sync.SessionBundleStore
import com.bliss.screenreader.sync.SessionUploader
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.ui.mpin.MpinActivity
import com.bliss.screenreader.ui.raw.RawCaptureActivity
import com.bliss.screenreader.ui.update.UpdateSheet
import com.bliss.screenreader.update.UpdateChecker
import com.bliss.screenreader.update.UpdateInstaller
import com.bliss.screenreader.update.UpdateVersion
import com.bliss.screenreader.utils.AppLauncherUtils
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.isEmpty

class SettingsFragment : Fragment() {

    private var ViewBindingObj: FragmentSettingsBinding? = null
    private var TransferBindingObj: SheetSessionTransferBinding? = null
    private var TransferDialogObj: BottomSheetDialog? = null
    private var PendingImportUri: Uri? = null

    private val ImportPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { SelectedUri -> OnFileChosen(SelectedUri = SelectedUri) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = FragmentSettingsBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onResume() {
        super.onResume()
        RenderAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        TransferDialogObj?.dismiss()
        TransferDialogObj = null
        TransferBindingObj = null
        ViewBindingObj = null
    }


    private fun RenderAll() {
        val BindingObj = ViewBindingObj ?: return
        BindingObj.settingsContainer.removeAllViews()

        RenderIdentity()
        RenderCaptureSection()
        RenderLoginSection()
        RenderPermissionsSection()
        RenderDataSection()
        RenderSecuritySection()
        RenderAppearanceSection()
        RenderSupportSection()

        BindingObj.tvSettingsFooter.text = getString(
            R.string.settings_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.FLAVOR
        )
    }

    private fun RenderIdentity() {
        val BindingObj = ViewBindingObj ?: return
        BindingObj.tvIdentityName.text = getString(R.string.app_name)
        BindingObj.tvIdentityVersion.text = getString(
            R.string.settings_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.FLAVOR
        )
        BindingObj.tvIdentityLicence.text = LicenceSummary()
    }

    private fun LicenceSummary(): CharSequence {
        val ContextRef = requireContext()
        if (!LicenceGate.IsUrlGate) {
            if (!AuthManager.IsActivated(ContextRef = ContextRef)) {
                return getString(R.string.settings_licence_not_activated)
            }
            return getString(
                R.string.settings_licence_activated_format,
                AuthManager.ExpiryText(ContextRef = ContextRef)
            )
        }

        val NowMillis = System.currentTimeMillis()
        val LastOkAt = BlissLicenceStore.LastOkAt(ContextRef = ContextRef)
        return when (BlissLicenceStore.StateOf(ContextRef = ContextRef)) {
            BlissLicenceStore.CacheState.Fresh -> {
                val DaysAgo = BlissLicenceStore.DaysSinceLastCheck(
                    LastOkAt = LastOkAt,
                    NowMillis = NowMillis
                )
                val WhenText = if (DaysAgo <= 0) {
                    getString(R.string.settings_confirmed_today)
                } else {
                    getString(R.string.settings_confirmed_days_format, DaysAgo)
                }
                getString(R.string.settings_licence_ok_format, WhenText)
            }

            BlissLicenceStore.CacheState.InGrace -> getString(
                R.string.settings_licence_grace_format,
                BlissLicenceStore.GraceDaysLeft(LastOkAt = LastOkAt, NowMillis = NowMillis)
            )

            else -> getString(R.string.settings_licence_stale)
        }
    }


    private fun AddSection(LabelRes: Int, FooterText: CharSequence = ""): LinearLayout? {
        val BindingObj = ViewBindingObj ?: return null
        val SectionBinding = PartialSettingsSectionBinding.inflate(
            layoutInflater, BindingObj.settingsContainer, false
        )
        SectionBinding.tvSectionLabel.setText(LabelRes)
        if (FooterText.isNotEmpty()) {
            SectionBinding.tvSectionFooter.visibility = View.VISIBLE
            SectionBinding.tvSectionFooter.text = FooterText
        }
        BindingObj.settingsContainer.addView(SectionBinding.root)
        return SectionBinding.sectionRows
    }

    private fun AddRow(
        ContainerRef: LinearLayout?,
        TitleText: CharSequence,
        DescText: CharSequence = "",
        ValueText: CharSequence = "",
        BadgeText: CharSequence = "",
        BadgeBackgroundRes: Int = R.drawable.bg_badge_neutral,
        BadgeColorRes: Int = R.color.text_muted,
        IconRes: Int = 0,
        IconTintRes: Int = R.color.primary,
        TitleColorRes: Int = 0,
        SwitchState: Boolean? = null,
        ShowChevron: Boolean = false,
        IsEnabled: Boolean = true,
        OnClick: (() -> Unit)? = null
    ) {
        val ParentRef = ContainerRef ?: return
        val ContextRef = ParentRef.context
        val RowBinding = PartialSettingsRowBinding.inflate(layoutInflater, ParentRef, false)

        RowBinding.rowDivider.visibility =
            if (ParentRef.isEmpty()) View.GONE else View.VISIBLE

        if (IconRes == 0) {
            RowBinding.ivRowIcon.visibility = View.GONE
        } else {
            RowBinding.ivRowIcon.setImageResource(IconRes)
            RowBinding.ivRowIcon.imageTintList = ContextCompat.getColorStateList(
                ContextRef, IconTintRes
            )
        }

        RowBinding.tvRowTitle.text = TitleText
        if (TitleColorRes != 0) {
            RowBinding.tvRowTitle.setTextColor(ContextCompat.getColor(ContextRef, TitleColorRes))
        }

        if (DescText.isEmpty()) {
            RowBinding.tvRowDesc.visibility = View.GONE
        } else {
            RowBinding.tvRowDesc.text = DescText
        }

        if (ValueText.isNotEmpty()) {
            RowBinding.tvRowValue.visibility = View.VISIBLE
            RowBinding.tvRowValue.text = ValueText
        }

        if (BadgeText.isNotEmpty()) {
            RowBinding.tvRowBadge.visibility = View.VISIBLE
            RowBinding.tvRowBadge.text = BadgeText
            RowBinding.tvRowBadge.setBackgroundResource(BadgeBackgroundRes)
            RowBinding.tvRowBadge.setTextColor(ContextCompat.getColor(ContextRef, BadgeColorRes))
        }

        if (SwitchState != null) {
            RowBinding.swRow.visibility = View.VISIBLE
            RowBinding.swRow.isChecked = SwitchState
            RowBinding.swRow.isEnabled = IsEnabled
        }

        RowBinding.ivRowChevron.visibility = if (ShowChevron) View.VISIBLE else View.GONE
        RowBinding.ivRowChevron.imageTintList =
            ContextCompat.getColorStateList(ContextRef, R.color.text_faint)

        RowBinding.settingsRow.alpha = if (IsEnabled) 1f else 0.5f
        if (OnClick == null || !IsEnabled) {
            RowBinding.settingsRow.isClickable = false
            RowBinding.settingsRow.background = null
        } else {
            RowBinding.settingsRow.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                OnClick()
            }
        }

        ParentRef.addView(RowBinding.root)
    }


    private fun RenderCaptureSection() {
        val ContextRef = requireContext()
        val IsRunning = ScreenReaderService.IsCapturing
        val SectionRef = AddSection(
            LabelRes = R.string.settings_section_capture,
            FooterText = if (IsRunning) getString(R.string.settings_capture_running) else ""
        )

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_depth_title),
            DescText = getString(R.string.settings_depth_desc),
            ValueText = DepthLabel(DepthVal = SettingsStore.DepthOf(ContextRef = ContextRef)),
            IconRes = R.drawable.ic_policy,
            ShowChevron = true,
            IsEnabled = !IsRunning
        ) { ShowDepthSheet() }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_pace_title),
            DescText = getString(R.string.settings_pace_desc),
            ValueText = PaceLabel(ProfileVal = SettingsStore.PaceOf(ContextRef = ContextRef)),
            IconRes = R.drawable.ic_history,
            ShowChevron = true,
            IsEnabled = !IsRunning
        ) { ShowPaceSheet() }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_recovery_title),
            DescText = getString(R.string.settings_recovery_desc),
            IconRes = R.drawable.ic_alert,
            ShowChevron = true,
            IsEnabled = !IsRunning
        ) { ShowRecoverySheet() }

        val OcrSupported = CustomerSheetOcr.IsSupported()
        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_ocr_title),
            DescText = if (OcrSupported) {
                getString(R.string.settings_ocr_desc)
            } else {
                getString(R.string.settings_ocr_unsupported_format, Build.VERSION.RELEASE.orEmpty())
            },
            IconRes = R.drawable.ic_person,
            SwitchState = OcrSupported && SettingsStore.IsContactOcrOn(ContextRef = ContextRef),
            IsEnabled = OcrSupported && !IsRunning
        ) {
            SettingsStore.SetContactOcr(
                ContextRef = ContextRef,
                EnabledVal = !SettingsStore.IsContactOcrOn(ContextRef = ContextRef)
            )
            RenderAll()
        }
    }

    private fun DepthLabel(DepthVal: SettingsStore.CaptureDepth): String = when (DepthVal) {
        SettingsStore.CaptureDepth.FAST -> getString(R.string.policy_capture_fast)
        SettingsStore.CaptureDepth.FULL -> getString(R.string.policy_capture_full)
        else -> getString(R.string.settings_depth_ask)
    }

    private fun PaceLabel(ProfileVal: PaceProfile): String = when (ProfileVal) {
        PaceProfile.FAST -> getString(R.string.settings_pace_fast)
        PaceProfile.PATIENT -> getString(R.string.settings_pace_patient)
        else -> getString(R.string.settings_pace_normal)
    }


    private fun RenderLoginSection() {
        val ContextRef = requireContext()
        val SectionRef = AddSection(LabelRes = R.string.settings_section_login)

        val HasMpin = MpinStore.HasMpin(ContextRef = ContextRef)
        val AutoOn = MpinStore.IsAutoEnterOn(ContextRef = ContextRef)
        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_mpin_title),
            DescText = when {
                !HasMpin -> getString(R.string.settings_mpin_desc_none)
                AutoOn -> getString(R.string.settings_mpin_desc_auto)
                else -> getString(R.string.settings_mpin_desc_saved)
            },
            BadgeText = if (HasMpin && AutoOn) getString(R.string.settings_mpin_badge) else "",
            BadgeBackgroundRes = R.drawable.bg_badge_inforce,
            BadgeColorRes = R.color.status_green_text,
            IconRes = R.drawable.ic_lock,
            ShowChevron = true
        ) { startActivity(Intent(ContextRef, MpinActivity::class.java)) }

        val TargetInstalled = AppLauncherUtils.IsInstalled(
            ContextRef = ContextRef,
            PackageNameVal = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        )
        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_target_title),
            DescText = AppLauncherUtils.LIC_SUPER_APP_PACKAGE,
            ValueText = if (TargetInstalled) getString(R.string.settings_app_open) else "",
            BadgeText = if (TargetInstalled) "" else getString(R.string.settings_app_missing),
            IconRes = R.drawable.ic_launch,
            IsEnabled = TargetInstalled
        ) {
            AppLauncherUtils.LaunchTargetApp(
                ContextRef = ContextRef,
                PackageNameVal = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
            )
        }

        val AgentPackage = AppLauncherUtils.ResolveAgentPackage(ContextRef = ContextRef)
        val AgentInstalled = AppLauncherUtils.IsInstalled(
            ContextRef = ContextRef,
            PackageNameVal = AgentPackage
        )
        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_agent_title),
            DescText = if (AgentInstalled) {
                AgentPackage
            } else {
                getString(R.string.settings_agent_desc)
            },
            ValueText = if (AgentInstalled) getString(R.string.settings_app_open) else "",
            BadgeText = if (AgentInstalled) "" else getString(R.string.settings_app_missing),
            IconRes = R.drawable.ic_launch,
            IsEnabled = AgentInstalled
        ) {
            AppLauncherUtils.LaunchTargetApp(
                ContextRef = ContextRef,
                PackageNameVal = AgentPackage
            )
        }
    }


    private fun RenderPermissionsSection() {
        val ContextRef = requireContext()
        val ServiceOn = ScreenReaderService.IsServiceRunning()
        val BatteryOk = !AppLauncherUtils.IsBatteryOptimized(ContextRef = ContextRef)
        val InstallOk = UpdateInstaller.CanInstallPackages(ContextRef = ContextRef)
        val OcrSupported = CustomerSheetOcr.IsSupported()
        val AllClear = ServiceOn && BatteryOk && InstallOk && OcrSupported

        val SectionRef = AddSection(
            LabelRes = R.string.settings_section_permissions,
            FooterText = if (AllClear) getString(R.string.settings_permissions_clear) else ""
        )

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.title_accessibility_service),
            DescText = getString(
                if (ServiceOn) R.string.settings_accessibility_desc_on
                else R.string.settings_accessibility_desc_off
            ),
            ValueText = if (ServiceOn) "" else getString(R.string.settings_permission_fix),
            BadgeText = if (ServiceOn) getString(R.string.settings_permission_on) else "",
            BadgeBackgroundRes = R.drawable.bg_badge_inforce,
            BadgeColorRes = R.color.status_green_text,
            IconRes = R.drawable.ic_accessibility,
            IconTintRes = if (ServiceOn) R.color.status_green_text else R.color.status_red_text
        ) { OpenAccessibilitySettings() }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.preflight_battery_title),
            DescText = getString(
                if (BatteryOk) R.string.settings_battery_desc_on
                else R.string.settings_battery_desc_off
            ),
            ValueText = if (BatteryOk) "" else getString(R.string.settings_permission_fix),
            BadgeText = if (BatteryOk) getString(R.string.settings_permission_on) else "",
            BadgeBackgroundRes = R.drawable.bg_badge_inforce,
            BadgeColorRes = R.color.status_green_text,
            IconRes = R.drawable.ic_battery,
            IconTintRes = if (BatteryOk) R.color.status_green_text else R.color.status_red_text
        ) { AppLauncherUtils.RequestBatteryOptimizationExemption(ContextRef = ContextRef) }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_install_title),
            DescText = getString(
                if (InstallOk) R.string.settings_install_desc_on
                else R.string.settings_install_desc_off
            ),
            ValueText = if (InstallOk) "" else getString(R.string.settings_permission_allow),
            BadgeText = if (InstallOk) getString(R.string.settings_permission_on) else "",
            BadgeBackgroundRes = R.drawable.bg_badge_inforce,
            BadgeColorRes = R.color.status_green_text,
            IconRes = R.drawable.ic_install,
            IconTintRes = if (InstallOk) R.color.status_green_text else R.color.status_amber_text
        ) { OpenUnknownSources() }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_contact_title),
            DescText = getString(
                R.string.settings_contact_desc_format,
                Build.VERSION.RELEASE.orEmpty()
            ),
            BadgeText = getString(
                if (OcrSupported) R.string.settings_permission_supported
                else R.string.settings_permission_unsupported
            ),
            BadgeBackgroundRes = if (OcrSupported) {
                R.drawable.bg_badge_inforce
            } else {
                R.drawable.bg_badge_lapsed
            },
            BadgeColorRes = if (OcrSupported) R.color.status_green_text else R.color.status_red_text,
            IconRes = R.drawable.ic_check_circle,
            IconTintRes = if (OcrSupported) R.color.status_green_text else R.color.status_red_text
        )
    }

    private fun OpenAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun OpenUnknownSources() {
        try {
            startActivity(
                UpdateInstaller.BuildUnknownSourcesIntent(ContextRef = requireContext())
            )
        } catch (_: Exception) {
        }
    }


    private fun RenderDataSection() {
        val ContextRef = requireContext()
        val SectionRef = AddSection(LabelRes = R.string.settings_section_data)
        val SummaryObj = PolicyRepository.SummariseStorage(ContextRef = ContextRef)

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_storage_title),
            DescText = if (SummaryObj.SessionCount == 0) {
                getString(R.string.settings_storage_empty)
            } else {
                getString(
                    R.string.settings_storage_format,
                    SummaryObj.SessionCount,
                    SummaryObj.RecordCount
                )
            },
            IconRes = R.drawable.ic_folder
        )

        val DefaultAgency = PolicyRepository.GetDefaultAgencyCode(ContextRef = ContextRef)
        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_agency_title),
            DescText = getString(R.string.settings_agency_desc),
            ValueText = DefaultAgency.ifEmpty { getString(R.string.settings_agency_none) },
            IconRes = R.drawable.ic_code,
            ShowChevron = true
        ) { ShowAgencySheet(CurrentCode = DefaultAgency) }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_transfer_title),
            DescText = getString(R.string.settings_transfer_desc),
            IconRes = R.drawable.ic_export,
            ShowChevron = true
        ) { ShowTransferSheet() }

        if (SessionUploader.IsEnabled()) {
            val UploadReady = SessionUploader.IsConfigured()
            AddRow(
                ContainerRef = SectionRef,
                TitleText = getString(R.string.settings_upload_title),
                DescText = getString(
                    if (UploadReady) R.string.settings_upload_desc_ready
                    else R.string.settings_upload_desc_missing
                ),
                IconRes = R.drawable.ic_cloud_upload,
                IconTintRes = if (UploadReady) R.color.primary else R.color.text_faint
            )
        }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_delete_title),
            DescText = getString(R.string.settings_delete_desc),
            IconRes = R.drawable.ic_delete,
            IconTintRes = R.color.status_red_text,
            TitleColorRes = R.color.status_red_text,
            ShowChevron = true
        ) { ShowDeleteSheet(SummaryObj = SummaryObj) }
    }


    private fun RenderSecuritySection() {
        val ContextRef = requireContext()
        val SectionRef = AddSection(LabelRes = R.string.settings_section_security)

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_licence_title),
            DescText = getString(R.string.settings_licence_row_desc),
            IconRes = R.drawable.ic_licence_shield,
            ShowChevron = true
        ) { ShowLicenceSheet() }

        if (!LicenceGate.IsUrlGate) {
            AddRow(
                ContainerRef = SectionRef,
                TitleText = getString(R.string.settings_idle_title),
                DescText = getString(R.string.settings_idle_desc),
                ValueText = MinutesLabel(ValueMs = SettingsStore.IdleLockMs(ContextRef = ContextRef)),
                IconRes = R.drawable.ic_lock,
                ShowChevron = true
            ) { ShowIdleLockSheet() }
        }
    }

    private fun MinutesLabel(ValueMs: Long): String = getString(
        R.string.settings_offline_minutes_format,
        (ValueMs / 60_000L).toInt()
    )


    private fun RenderAppearanceSection() {
        val ContextRef = requireContext()
        val SectionRef = AddSection(LabelRes = R.string.settings_section_appearance)

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_theme_title),
            DescText = getString(R.string.settings_theme_desc),
            ValueText = ThemeLabel(ThemeVal = SettingsStore.ThemeOf(ContextRef = ContextRef)),
            IconRes = R.drawable.ic_settings,
            ShowChevron = true
        ) { ShowThemeSheet() }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_haptics_title),
            DescText = getString(R.string.settings_haptics_desc),
            IconRes = R.drawable.ic_record,
            SwitchState = SettingsStore.IsHapticsOn(ContextRef = ContextRef)
        ) {
            SettingsStore.SetHaptics(
                ContextRef = ContextRef,
                EnabledVal = !SettingsStore.IsHapticsOn(ContextRef = ContextRef)
            )
            RenderAll()
        }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_secure_title),
            DescText = getString(R.string.settings_secure_desc),
            IconRes = R.drawable.ic_lock,
            SwitchState = SettingsStore.IsSecureWindowOn(ContextRef = ContextRef)
        ) {
            val NextValue = !SettingsStore.IsSecureWindowOn(ContextRef = ContextRef)
            SettingsStore.SetSecureWindow(ContextRef = ContextRef, EnabledVal = NextValue)
            ShowMessage(MessageText = getString(R.string.settings_secure_restart))
            RenderAll()
        }
    }

    private fun ThemeLabel(ThemeVal: SettingsStore.ThemeChoice): String = when (ThemeVal) {
        SettingsStore.ThemeChoice.LIGHT -> getString(R.string.settings_theme_light)
        SettingsStore.ThemeChoice.DARK -> getString(R.string.settings_theme_dark)
        else -> getString(R.string.settings_theme_system)
    }


    private fun RenderSupportSection() {
        val ContextRef = requireContext()
        val SectionRef = AddSection(LabelRes = R.string.settings_section_support)

        val LogFiles = CaptureDiagnostics.AllLogFiles(ContextObj = ContextRef)
        val TotalBytes = LogFiles.sumOf { FileRef -> FileRef.length() }
        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_logs_title),
            DescText = if (LogFiles.isEmpty()) {
                getString(R.string.settings_logs_desc_empty)
            } else {
                getString(
                    R.string.settings_logs_desc_format,
                    LogFiles.size,
                    Formatter.formatShortFileSize(ContextRef, TotalBytes)
                )
            },
            IconRes = R.drawable.ic_folder_open,
            ShowChevron = true
        ) { ShowLogsSheet() }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_raw_title),
            DescText = getString(R.string.settings_raw_desc),
            BadgeText = getString(R.string.settings_raw_badge),
            IconRes = R.drawable.ic_code,
            ShowChevron = true
        ) { startActivity(Intent(ContextRef, RawCaptureActivity::class.java)) }

        if (UpdateChecker.IsConfigured()) {
            AddRow(
                ContainerRef = SectionRef,
                TitleText = getString(R.string.update_check_now),
                DescText = UpdateVersion.Describe(
                    VersionName = UpdateChecker.LocalVersionName(ContextRef = ContextRef),
                    VersionCode = UpdateChecker.LocalVersionCode(ContextRef = ContextRef)
                ),
                IconRes = R.drawable.ic_update,
                ShowChevron = true
            ) { RunManualUpdateCheck() }
        }

        if (BuildConfig.SUPPORT_PHONE.isNotBlank()) {
            AddRow(
                ContainerRef = SectionRef,
                TitleText = getString(R.string.licence_call_support),
                DescText = BuildConfig.SUPPORT_PHONE_DISPLAY,
                IconRes = R.drawable.ic_phone,
                IconTintRes = R.color.status_green_text,
                ShowChevron = true
            ) { CallSupport() }
        }

        AddRow(
            ContainerRef = SectionRef,
            TitleText = getString(R.string.settings_about_title),
            DescText = getString(R.string.settings_about_desc),
            IconRes = R.drawable.ic_check_circle,
            ShowChevron = true
        ) { ShowAboutSheet() }
    }

    private fun CallSupport() {
        val NumberText = BuildConfig.SUPPORT_PHONE
        if (NumberText.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_DIAL, "tel:$NumberText".toUri()))
        } catch (_: Exception) {
        }
    }

    private fun RunManualUpdateCheck() {
        val ContextRef = requireContext()
        ShowMessage(MessageText = getString(R.string.update_checking))
        UpdateChecker.Check(ContextRef = ContextRef, ManualCheck = true) { OutcomeRef ->
            if (!isAdded) return@Check
            when (OutcomeRef) {
                is UpdateChecker.Outcome.Available -> UpdateSheet.Show(
                    ManagerRef = parentFragmentManager,
                    ManifestObj = OutcomeRef.ManifestObj,
                    SizeBytes = OutcomeRef.SizeBytes
                )

                is UpdateChecker.Outcome.Failed -> ShowMessage(
                    MessageText = getString(R.string.update_check_failed, OutcomeRef.MessageText)
                )

                UpdateChecker.Outcome.NotConfigured -> ShowMessage(
                    MessageText = getString(R.string.update_not_configured)
                )

                else -> ShowMessage(
                    MessageText = getString(
                        R.string.update_up_to_date,
                        UpdateVersion.Describe(
                            VersionName = UpdateChecker.LocalVersionName(ContextRef = ContextRef),
                            VersionCode = UpdateChecker.LocalVersionCode(ContextRef = ContextRef)
                        )
                    )
                )
            }
        }
    }

    private fun ShowMessage(MessageText: String) {
        val ActivityRef = activity as? AppCompatActivity ?: return
        CaptureFlow.ShowMessage(ActivityRef = ActivityRef, MessageVal = MessageText)
    }

    private fun DetailSheet(
        TitleText: CharSequence,
        BodyText: CharSequence = ""
    ): Pair<BottomSheetDialog, SheetSettingsDetailBinding>? {
        val ActivityRef = activity as? AppCompatActivity ?: return null
        val SheetBinding = SheetSettingsDetailBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)
        SheetBinding.tvDetailTitle.text = TitleText
        if (BodyText.isNotEmpty()) {
            SheetBinding.tvDetailBody.visibility = View.VISIBLE
            SheetBinding.tvDetailBody.text = BodyText
        }
        SheetBinding.btnDetailClose.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        return SheetDialog to SheetBinding
    }

    private fun ShowChoiceSheet(
        TitleText: CharSequence,
        BodyText: CharSequence,
        NoteText: CharSequence,
        Labels: List<CharSequence>,
        Descriptions: List<CharSequence>,
        SelectedIndex: Int,
        OnPick: (Int) -> Unit
    ) {
        val SheetPair = DetailSheet(TitleText = TitleText, BodyText = BodyText) ?: return
        val SheetDialog = SheetPair.first
        val SheetBinding = SheetPair.second

        for (OptionIndex in Labels.indices) {
            val ChoiceBinding = PartialSettingsChoiceRowBinding.inflate(
                layoutInflater, SheetBinding.detailContainer, false
            )
            ChoiceBinding.tvChoiceTitle.text = Labels[OptionIndex]
            val DescText = Descriptions.getOrElse(OptionIndex) { "" }
            if (DescText.isEmpty()) {
                ChoiceBinding.tvChoiceDesc.visibility = View.GONE
            } else {
                ChoiceBinding.tvChoiceDesc.visibility = View.VISIBLE
                ChoiceBinding.tvChoiceDesc.text = DescText
            }
            ChoiceBinding.rbChoice.isChecked = OptionIndex == SelectedIndex
            ChoiceBinding.choiceRow.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                OnPick(OptionIndex)
            }
            SheetBinding.detailContainer.addView(ChoiceBinding.root)
        }

        if (NoteText.isNotEmpty()) {
            SheetBinding.tvDetailNote.visibility = View.VISIBLE
            SheetBinding.tvDetailNote.text = NoteText
        }
        SheetDialog.show()
    }

    private fun ShowDepthSheet() {
        val ContextRef = requireContext()
        val Options = listOf(
            SettingsStore.CaptureDepth.ASK,
            SettingsStore.CaptureDepth.FAST,
            SettingsStore.CaptureDepth.FULL
        )
        ShowChoiceSheet(
            TitleText = getString(R.string.settings_depth_title),
            BodyText = getString(R.string.policy_capture_sheet_subtitle),
            NoteText = "",
            Labels = listOf(
                getString(R.string.settings_depth_ask),
                getString(R.string.policy_capture_fast),
                getString(R.string.policy_capture_full)
            ),
            Descriptions = listOf(
                getString(R.string.settings_depth_ask_desc),
                getString(R.string.settings_depth_fast_desc),
                getString(R.string.settings_depth_full_desc)
            ),
            SelectedIndex = Options.indexOf(SettingsStore.DepthOf(ContextRef = ContextRef))
        ) { PickedIndex ->
            SettingsStore.SetDepth(ContextRef = ContextRef, DepthVal = Options[PickedIndex])
            RenderAll()
        }
    }

    private fun ShowPaceSheet() {
        val ContextRef = requireContext()
        val Options = listOf(PaceProfile.FAST, PaceProfile.NORMAL, PaceProfile.PATIENT)
        ShowChoiceSheet(
            TitleText = getString(R.string.settings_pace_title),
            BodyText = getString(R.string.settings_pace_body),
            NoteText = getString(R.string.settings_pace_note),
            Labels = listOf(
                getString(R.string.settings_pace_fast),
                getString(R.string.settings_pace_normal),
                getString(R.string.settings_pace_patient)
            ),
            Descriptions = listOf(
                getString(R.string.settings_pace_fast_desc),
                getString(R.string.settings_pace_normal_desc),
                getString(R.string.settings_pace_patient_desc)
            ),
            SelectedIndex = Options.indexOf(SettingsStore.PaceOf(ContextRef = ContextRef))
        ) { PickedIndex ->
            SettingsStore.SetPace(ContextRef = ContextRef, ProfileVal = Options[PickedIndex])
            RenderAll()
        }
    }

    private fun ShowThemeSheet() {
        val ContextRef = requireContext()
        val Options = listOf(
            SettingsStore.ThemeChoice.SYSTEM,
            SettingsStore.ThemeChoice.LIGHT,
            SettingsStore.ThemeChoice.DARK
        )
        ShowChoiceSheet(
            TitleText = getString(R.string.settings_theme_title),
            BodyText = getString(R.string.settings_theme_desc),
            NoteText = "",
            Labels = listOf(
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
            ),
            Descriptions = emptyList(),
            SelectedIndex = Options.indexOf(SettingsStore.ThemeOf(ContextRef = ContextRef))
        ) { PickedIndex ->
            SettingsStore.SetTheme(ContextRef = ContextRef, ThemeVal = Options[PickedIndex])
        }
    }

    private fun ShowIdleLockSheet() {
        val ContextRef = requireContext()
        val Options = SettingsStore.IDLE_LOCK_CHOICES
        ShowChoiceSheet(
            TitleText = getString(R.string.settings_idle_title),
            BodyText = getString(R.string.settings_idle_body),
            NoteText = "",
            Labels = Options.map { ValueMs -> MinutesLabel(ValueMs = ValueMs) },
            Descriptions = emptyList(),
            SelectedIndex = Options.indexOf(SettingsStore.IdleLockMs(ContextRef = ContextRef))
        ) { PickedIndex ->
            SettingsStore.SetIdleLockMs(ContextRef = ContextRef, ValueMs = Options[PickedIndex])
            RenderAll()
        }
    }

    private fun ShowRecoverySheet() {
        val SheetPair = DetailSheet(
            TitleText = getString(R.string.settings_recovery_title),
            BodyText = getString(R.string.settings_recovery_desc)
        ) ?: return
        val SheetDialog = SheetPair.first
        val SheetBinding = SheetPair.second
        RenderRecoveryRows(ContainerRef = SheetBinding.detailContainer)
        SheetDialog.show()
    }

    private fun RenderRecoveryRows(ContainerRef: LinearLayout) {
        val ContextRef = requireContext()
        ContainerRef.removeAllViews()

        val OfflineWaitMs = SettingsStore.OfflineWaitMs(ContextRef = ContextRef)
        AddRow(
            ContainerRef = ContainerRef,
            TitleText = getString(R.string.settings_offline_title),
            DescText = getString(R.string.settings_offline_desc),
            ValueText = OfflineWaitLabel(ValueMs = OfflineWaitMs),
            ShowChevron = true
        ) { ShowOfflineWaitSheet(ContainerRef = ContainerRef) }

        AddRow(
            ContainerRef = ContainerRef,
            TitleText = getString(R.string.settings_retry_title),
            DescText = getString(R.string.settings_retry_desc),
            ValueText = SettingsStore.ErrorRetryLimit(ContextRef = ContextRef).toString(),
            ShowChevron = true
        ) { ShowRetrySheet(ContainerRef = ContainerRef) }

        AddRow(
            ContainerRef = ContainerRef,
            TitleText = getString(R.string.settings_giveup_title),
            DescText = getString(R.string.settings_giveup_desc),
            ValueText = SettingsStore.ErrorGiveUpLimit(ContextRef = ContextRef).toString(),
            ShowChevron = true
        ) { ShowGiveUpSheet(ContainerRef = ContainerRef) }

        AddRow(
            ContainerRef = ContainerRef,
            TitleText = getString(R.string.settings_slowdown_title),
            DescText = getString(R.string.settings_slowdown_desc),
            SwitchState = SettingsStore.IsErrorSlowDownOn(ContextRef = ContextRef)
        ) {
            SettingsStore.SetErrorSlowDown(
                ContextRef = ContextRef,
                EnabledVal = !SettingsStore.IsErrorSlowDownOn(ContextRef = ContextRef)
            )
            RenderRecoveryRows(ContainerRef = ContainerRef)
        }

        AddRow(
            ContainerRef = ContainerRef,
            TitleText = getString(R.string.settings_ps_title),
            DescText = getString(R.string.settings_ps_desc),
            SwitchState = SettingsStore.IsPsModeVisible(ContextRef = ContextRef)
        ) {
            SettingsStore.SetPsModeVisible(
                ContextRef = ContextRef,
                EnabledVal = !SettingsStore.IsPsModeVisible(ContextRef = ContextRef)
            )
            RenderRecoveryRows(ContainerRef = ContainerRef)
        }
    }

    private fun OfflineWaitLabel(ValueMs: Long): String {
        if (ValueMs <= 0L) return getString(R.string.settings_permission_off)
        return getString(R.string.settings_offline_minutes_format, (ValueMs / 60_000L).toInt())
    }

    private fun ShowOfflineWaitSheet(ContainerRef: LinearLayout) {
        val ContextRef = requireContext()
        val Options = SettingsStore.OFFLINE_WAIT_CHOICES
        ShowChoiceSheet(
            TitleText = getString(R.string.settings_offline_title),
            BodyText = getString(R.string.settings_offline_body),
            NoteText = "",
            Labels = Options.map { ValueMs -> OfflineWaitLabel(ValueMs = ValueMs) },
            Descriptions = emptyList(),
            SelectedIndex = Options.indexOf(SettingsStore.OfflineWaitMs(ContextRef = ContextRef))
        ) { PickedIndex ->
            SettingsStore.SetOfflineWaitMs(ContextRef = ContextRef, ValueMs = Options[PickedIndex])
            RenderRecoveryRows(ContainerRef = ContainerRef)
        }
    }

    private fun ShowRetrySheet(ContainerRef: LinearLayout) {
        val ContextRef = requireContext()
        val Options = SettingsStore.RETRY_CHOICES
        ShowChoiceSheet(
            TitleText = getString(R.string.settings_retry_title),
            BodyText = getString(R.string.settings_retry_body),
            NoteText = "",
            Labels = Options.map { ValueVal -> ValueVal.toString() },
            Descriptions = emptyList(),
            SelectedIndex = Options.indexOf(SettingsStore.ErrorRetryLimit(ContextRef = ContextRef))
        ) { PickedIndex ->
            SettingsStore.SetErrorRetryLimit(
                ContextRef = ContextRef,
                ValueVal = Options[PickedIndex]
            )
            RenderRecoveryRows(ContainerRef = ContainerRef)
        }
    }

    private fun ShowGiveUpSheet(ContainerRef: LinearLayout) {
        val ContextRef = requireContext()
        val Options = SettingsStore.GIVEUP_CHOICES
        ShowChoiceSheet(
            TitleText = getString(R.string.settings_giveup_title),
            BodyText = getString(R.string.settings_giveup_body),
            NoteText = "",
            Labels = Options.map { ValueVal -> ValueVal.toString() },
            Descriptions = emptyList(),
            SelectedIndex = Options.indexOf(SettingsStore.ErrorGiveUpLimit(ContextRef = ContextRef))
        ) { PickedIndex ->
            SettingsStore.SetErrorGiveUpLimit(
                ContextRef = ContextRef,
                ValueVal = Options[PickedIndex]
            )
            RenderRecoveryRows(ContainerRef = ContainerRef)
        }
    }


    private fun ShowAgencySheet(CurrentCode: String) {
        val ActivityRef = activity as? AppCompatActivity ?: return
        val ContextRef = requireContext()
        val SheetBinding = SheetAgencyCodeBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)

        SheetBinding.tvAgencyBody.setText(R.string.settings_agency_body)
        SheetBinding.btnAgencyExport.setText(R.string.settings_agency_save)
        SheetBinding.btnAgencyExport.icon = null
        SheetBinding.etAgencyCode.setText(CurrentCode)
        SheetBinding.etAgencyCode.setSelection(CurrentCode.length)

        SheetBinding.btnAgencyExport.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            val EnteredCode = SheetBinding.etAgencyCode.text?.toString()?.trim().orEmpty()
            PolicyRepository.SetDefaultAgencyCode(
                ContextRef = ContextRef,
                AgencyCode = EnteredCode
            )
            SheetDialog.dismiss()
            ShowMessage(MessageText = getString(R.string.settings_agency_saved))
            RenderAll()
        }
        SheetBinding.btnAgencyCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }


    private fun ShowDeleteSheet(SummaryObj: PolicyRepository.StorageSummary) {
        if (SummaryObj.SessionCount == 0) {
            ShowMessage(MessageText = getString(R.string.settings_delete_nothing))
            return
        }

        val ContextRef = requireContext()
        val SheetPair = DetailSheet(
            TitleText = getString(R.string.settings_delete_sheet_title),
            BodyText = getString(
                R.string.settings_delete_sheet_body,
                SummaryObj.SessionCount,
                SummaryObj.RecordCount
            )
        ) ?: return
        val SheetDialog = SheetPair.first
        val SheetBinding = SheetPair.second

        SheetBinding.tvDetailNote.visibility = View.VISIBLE
        SheetBinding.tvDetailNote.setText(R.string.settings_delete_note)
        SheetBinding.tilDetailInput.visibility = View.VISIBLE
        SheetBinding.tilDetailInput.hint = getString(R.string.settings_delete_hint)
        SheetBinding.btnDetailPrimary.visibility = View.VISIBLE
        SheetBinding.btnDetailPrimary.setText(R.string.settings_delete_confirm)
        SheetBinding.btnDetailPrimary.isEnabled = false
        SheetBinding.btnDetailPrimary.setBackgroundColor(
            ContextCompat.getColor(ContextRef, R.color.status_red_text)
        )

        val ConfirmWord = getString(R.string.settings_delete_word)
        SheetBinding.etDetailInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                SheetBinding.btnDetailPrimary.isEnabled =
                    s?.toString()?.trim().orEmpty().equals(ConfirmWord, ignoreCase = false)
            }
        })

        SheetBinding.btnDetailPrimary.setOnClickListener { ViewRef ->
            HapticFeedback.Reject(ViewRef = ViewRef)
            val DeletedCount = PolicyRepository.DeleteAllSessions(ContextRef = ContextRef)
            SheetDialog.dismiss()
            ShowMessage(
                MessageText = getString(R.string.settings_delete_done_format, DeletedCount)
            )
            RenderAll()
        }
        SheetDialog.show()
    }


    private fun ShowLicenceSheet() {
        val ContextRef = requireContext()
        val SheetPair = DetailSheet(
            TitleText = getString(R.string.settings_licence_title),
            BodyText = LicenceSummary()
        ) ?: return
        val SheetDialog = SheetPair.first
        val SheetBinding = SheetPair.second

        val DeviceIdText = DeviceIdentity.RegistrationId(ContextRef = ContextRef)
        AddFieldRow(
            ContainerRef = SheetBinding.detailContainer,
            LabelText = getString(R.string.settings_licence_device_label),
            ValueText = DeviceIdentity.GroupForDisplay(IdText = DeviceIdText)
        )
        if (!LicenceGate.IsUrlGate) {
            AddFieldRow(
                ContainerRef = SheetBinding.detailContainer,
                LabelText = getString(R.string.settings_licence_expiry_label),
                ValueText = AuthManager.ExpiryText(ContextRef = ContextRef)
                    .ifEmpty { getString(R.string.settings_licence_not_activated) }
            )
        }

        SheetBinding.tvDetailNote.visibility = View.VISIBLE
        SheetBinding.tvDetailNote.setText(R.string.settings_licence_device_body)

        SheetBinding.btnDetailPrimary.visibility = View.VISIBLE
        SheetBinding.btnDetailPrimary.setText(R.string.licence_copy_device_id)
        SheetBinding.btnDetailPrimary.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            CopyDeviceId(DeviceIdText = DeviceIdText)
        }

        if (BuildConfig.SUPPORT_PHONE.isNotBlank()) {
            SheetBinding.btnDetailSecondary.visibility = View.VISIBLE
            SheetBinding.btnDetailSecondary.setText(R.string.licence_call_support)
            SheetBinding.btnDetailSecondary.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                CallSupport()
            }
        }
        SheetDialog.show()
    }

    private fun CopyDeviceId(DeviceIdText: String) {
        val ContextRef = requireContext()
        val ClipboardRef = ContextRef.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        ClipboardRef.setPrimaryClip(
            ClipData.newPlainText(
                "device id",
                DeviceIdentity.GroupForDisplay(IdText = DeviceIdText)
            )
        )
        ShowMessage(MessageText = getString(R.string.licence_device_id_copied))
    }

    private fun AddFieldRow(
        ContainerRef: LinearLayout,
        LabelText: CharSequence,
        ValueText: CharSequence
    ) {
        val FieldBinding = PartialFieldRowBinding.inflate(layoutInflater, ContainerRef, false)
        FieldBinding.tvFieldLabel.text = LabelText
        FieldBinding.tvFieldValue.text = ValueText
        ContainerRef.addView(FieldBinding.root)
    }


    private fun ShowLogsSheet() {
        val ContextRef = requireContext()
        val LogFiles = CaptureDiagnostics.AllLogFiles(ContextObj = ContextRef)
        if (LogFiles.isEmpty()) {
            ShowMessage(MessageText = getString(R.string.settings_logs_empty))
            return
        }

        val SheetPair = DetailSheet(
            TitleText = getString(R.string.settings_logs_title),
            BodyText = getString(R.string.settings_logs_body)
        ) ?: return
        val SheetDialog = SheetPair.first
        val SheetBinding = SheetPair.second
        val StampFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

        for (LogFile in LogFiles) {
            AddRow(
                ContainerRef = SheetBinding.detailContainer,
                TitleText = StampFormat.format(Date(LogFile.lastModified())),
                DescText = getString(
                    R.string.settings_logs_row_format,
                    LogFile.name,
                    Formatter.formatShortFileSize(ContextRef, LogFile.length())
                ),
                IconRes = R.drawable.ic_share,
                ShowChevron = false
            ) {
                SheetDialog.dismiss()
                ShareLogs(LogFiles = listOf(LogFile))
            }
        }

        SheetBinding.tvDetailNote.visibility = View.VISIBLE
        SheetBinding.tvDetailNote.setText(R.string.settings_logs_privacy)

        SheetBinding.btnDetailPrimary.visibility = View.VISIBLE
        SheetBinding.btnDetailPrimary.setText(R.string.settings_logs_share_newest)
        SheetBinding.btnDetailPrimary.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            ShareLogs(LogFiles = listOf(LogFiles.first()))
        }

        SheetBinding.btnDetailSecondary.visibility = View.VISIBLE
        SheetBinding.btnDetailSecondary.setText(R.string.settings_logs_clear)
        SheetBinding.btnDetailSecondary.setOnClickListener { ViewRef ->
            HapticFeedback.Reject(ViewRef = ViewRef)
            CaptureDiagnostics.DeleteAllLogs(ContextObj = ContextRef)
            SheetDialog.dismiss()
            ShowMessage(MessageText = getString(R.string.settings_logs_cleared))
            RenderAll()
        }
        SheetDialog.show()
    }

    private fun ShareLogs(LogFiles: List<File>) {
        val ContextRef = requireContext()
        val ShareIntent = CaptureDiagnostics.BuildShareIntent(
            ContextObj = ContextRef,
            LogFiles = LogFiles
        ) ?: return
        try {
            startActivity(
                Intent.createChooser(ShareIntent, getString(R.string.sessions_share_log_title))
            )
        } catch (_: Exception) {
        }
    }


    private fun ShowAboutSheet() {
        val ContextRef = requireContext()
        val SheetPair = DetailSheet(TitleText = getString(R.string.settings_about_title)) ?: return
        val SheetDialog = SheetPair.first
        val ContainerRef = SheetPair.second.detailContainer

        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_app),
            ValueText = getString(R.string.app_name)
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_version),
            ValueText = BuildConfig.VERSION_NAME
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_build),
            ValueText = BuildConfig.VERSION_CODE.toString()
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_flavour),
            ValueText = BuildConfig.FLAVOR
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_package),
            ValueText = BuildConfig.APPLICATION_ID
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_android),
            ValueText = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_device),
            ValueText = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_service),
            ValueText = getString(R.string.title_accessibility_service)
        )
        AddFieldRow(
            ContainerRef = ContainerRef,
            LabelText = getString(R.string.settings_about_target),
            ValueText = AppLauncherUtils.LIC_SUPER_APP_PACKAGE
        )
        SheetDialog.show()
    }


    private fun ShowTransferSheet() {
        val ActivityRef = activity as? AppCompatActivity ?: return

        val SheetBinding = SheetSessionTransferBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)
        TransferBindingObj = SheetBinding
        TransferDialogObj = SheetDialog
        PendingImportUri = null

        ShowPane(ExportSelected = true)

        SheetBinding.toggleTransfer.addOnButtonCheckedListener { _, CheckedId, IsChecked ->
            if (!IsChecked) return@addOnButtonCheckedListener
            ShowPane(ExportSelected = CheckedId == R.id.btnTabExport)
        }

        SheetBinding.btnExportRun.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ExportSessions()
        }
        SheetBinding.btnChooseFile.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ImportPicker.launch(arrayOf("*/*"))
        }
        SheetBinding.btnChooseOther.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ImportPicker.launch(arrayOf("*/*"))
        }
        SheetBinding.btnImportRun.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            RunImport()
        }

        SheetDialog.setOnDismissListener {
            TransferBindingObj = null
            TransferDialogObj = null
            PendingImportUri = null
        }
        SheetDialog.show()
    }

    private fun ShowPane(ExportSelected: Boolean) {
        val SheetBinding = TransferBindingObj ?: return
        SheetBinding.exportPane.visibility = if (ExportSelected) View.VISIBLE else View.GONE
        SheetBinding.importPane.visibility = if (ExportSelected) View.GONE else View.VISIBLE

        if (ExportSelected) {
            RenderDeviceStats()
            return
        }
        SheetBinding.statsRow.visibility =
            if (SheetBinding.importPreview.isVisible) View.VISIBLE else View.GONE
    }

    private fun RenderDeviceStats() {
        val SheetBinding = TransferBindingObj ?: return
        val SessionList = PolicyRepository.GetSessionHistory(ContextRef = requireContext())

        SheetBinding.statsRow.visibility = View.VISIBLE
        SheetBinding.tvStatOneValue.text = SessionList.size.toString()
        SheetBinding.tvStatOneLabel.setText(R.string.transfer_stat_sessions)
        SheetBinding.tvStatTwoValue.text =
            SessionList.sumOf { SessionRef -> SessionRef.RecordCount }.toString()
        SheetBinding.tvStatTwoLabel.setText(R.string.transfer_stat_records)
        SheetBinding.tvStatThreeValue.text = LastExportLabel()
        SheetBinding.tvStatThreeLabel.setText(R.string.transfer_stat_last_export)
    }

    private fun LastExportLabel(): String {
        val NewestBundle = requireContext().getExternalFilesDir(null)
            ?.listFiles { FileRef ->
                FileRef.isFile && SessionBundleStore.IsBundleFile(FileNameVal = FileRef.name)
            }
            ?.maxByOrNull { FileRef -> FileRef.lastModified() }
            ?: return getString(R.string.transfer_stat_none)

        return SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(NewestBundle.lastModified()))
    }

    private fun ExportSessions() {
        TransferBindingObj?.btnExportRun?.isEnabled = false

        SessionBundleStore.ExportAsync(ContextRef = requireContext()) { OutcomeVal ->
            if (!isAdded) return@ExportAsync
            TransferBindingObj?.btnExportRun?.isEnabled = true

            val MessageText = when (OutcomeVal) {
                is SessionBundleStore.ExportOutcome.Ready -> {
                    TransferDialogObj?.dismiss()
                    getString(
                        R.string.exports_backup_done_format,
                        OutcomeVal.SessionCount,
                        OutcomeVal.RecordCount
                    )
                }

                is SessionBundleStore.ExportOutcome.Failed -> getString(
                    R.string.exports_backup_failed_format, OutcomeVal.Message
                )

                SessionBundleStore.ExportOutcome.NothingToExport ->
                    getString(R.string.exports_backup_empty)
            }
            ShowMessage(MessageText = MessageText)
        }
    }

    private fun OnFileChosen(SelectedUri: Uri?) {
        val SourceUri = SelectedUri ?: return

        SessionBundleStore.PreviewAsync(
            ContextRef = requireContext(),
            SourceUri = SourceUri
        ) { OutcomeVal ->
            if (!isAdded) return@PreviewAsync
            when (OutcomeVal) {
                is SessionBundleStore.PreviewOutcome.Ready -> {
                    if (TransferBindingObj == null) ShowTransferSheet()
                    PendingImportUri = SourceUri
                    TransferBindingObj?.toggleTransfer?.check(R.id.btnTabImport)
                    RenderPreview(PreviewObj = OutcomeVal.PreviewObj)
                }

                is SessionBundleStore.PreviewOutcome.Failed -> {
                    PendingImportUri = null
                    HapticFeedback.Reject(ViewRef = TransferBindingObj?.root)
                    ShowMessage(
                        MessageText = getString(
                            R.string.transfer_preview_failed_format, OutcomeVal.Message
                        )
                    )
                }
            }
        }
    }

    private fun RenderPreview(PreviewObj: SessionBundleStore.BundlePreview) {
        val SheetBinding = TransferBindingObj ?: return

        SheetBinding.statsRow.visibility = View.VISIBLE
        SheetBinding.tvStatOneValue.text = PreviewObj.SessionCount.toString()
        SheetBinding.tvStatOneLabel.setText(R.string.transfer_stat_sessions)
        SheetBinding.tvStatTwoValue.text = PreviewObj.RecordCount.toString()
        SheetBinding.tvStatTwoLabel.setText(R.string.transfer_stat_records)
        SheetBinding.tvStatThreeValue.text = PreviewObj.ReplacedCount.toString()
        SheetBinding.tvStatThreeLabel.setText(R.string.transfer_stat_replaced)

        SheetBinding.tvFileName.text = PreviewObj.FileName
        SheetBinding.tvNewCount.text = getString(
            R.string.transfer_preview_new_count, PreviewObj.NewCount
        )
        SheetBinding.tvReplaceCount.text = getString(
            R.string.transfer_preview_replace_count, PreviewObj.ReplacedCount
        )
        SheetBinding.replaceRow.visibility =
            if (PreviewObj.ReplacedCount > 0) View.VISIBLE else View.GONE

        SheetBinding.btnImportRun.text = getString(
            R.string.transfer_import_action, PreviewObj.SessionCount
        )
        SheetBinding.importIntro.visibility = View.GONE
        SheetBinding.importPreview.visibility = View.VISIBLE
    }

    private fun RunImport() {
        val SourceUri = PendingImportUri ?: return
        TransferBindingObj?.btnImportRun?.isEnabled = false

        SessionBundleStore.ImportAsync(
            ContextRef = requireContext(),
            SourceUri = SourceUri
        ) { OutcomeVal ->
            if (!isAdded) return@ImportAsync
            TransferBindingObj?.btnImportRun?.isEnabled = true

            val MessageText = when (OutcomeVal) {
                is SessionBundleStore.ImportOutcome.Restored -> {
                    TransferDialogObj?.dismiss()
                    getString(
                        R.string.exports_restore_done_format,
                        OutcomeVal.AddedCount,
                        OutcomeVal.ReplacedCount
                    )
                }

                is SessionBundleStore.ImportOutcome.Failed -> {
                    HapticFeedback.Reject(ViewRef = TransferBindingObj?.root)
                    getString(R.string.exports_restore_failed_format, OutcomeVal.Message)
                }
            }
            ShowMessage(MessageText = MessageText)
            RenderAll()
        }
    }
}
