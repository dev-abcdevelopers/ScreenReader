@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.update

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentManager
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.SheetAppUpdateBinding
import com.bliss.screenreader.update.UpdateChecker
import com.bliss.screenreader.update.UpdateInstaller
import com.bliss.screenreader.update.UpdateManifest
import com.bliss.screenreader.update.UpdateVersion
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File

class UpdateSheet : BottomSheetDialogFragment() {

    private enum class SheetState { Available, Downloading, Ready, Failed }

    private var ViewBindingObj: SheetAppUpdateBinding? = null
    private var ManifestObj: UpdateManifest? = null
    private var SizeBytes = 0L
    private var ForceUpdate = false
    private var StateVal = SheetState.Available
    private var DownloadedFile: File? = null
    private var ErrorText = ""
    private var SettingsRequested = false

    private val InstallSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val ContextRef = context ?: return@registerForActivityResult
            if (UpdateInstaller.CanInstallPackages(ContextRef = ContextRef)) {
                ErrorText = ""
                OnInstallClicked()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ArgsRef = arguments ?: Bundle()

        ManifestObj = UpdateManifest(
            AppName = ArgsRef.getString(ARG_APP_NAME).orEmpty(),
            VersionCode = ArgsRef.getInt(ARG_VERSION_CODE),
            VersionName = ArgsRef.getString(ARG_VERSION_NAME).orEmpty(),
            DownloadUrl = ArgsRef.getString(ARG_DOWNLOAD_URL).orEmpty(),
            ForceUpdate = ArgsRef.getBoolean(ARG_FORCE),
            ChangeLog = ArgsRef.getStringArrayList(ARG_NOTES).orEmpty()
        )
        SizeBytes = ArgsRef.getLong(ARG_SIZE_BYTES)
        ForceUpdate = ArgsRef.getBoolean(ARG_FORCE)
        isCancelable = !ForceUpdate
    }

    override fun getTheme(): Int = R.style.Theme_DataReaderApp_BottomSheet_Update

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val DialogRef = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        DialogRef.setOnShowListener {
            val BehaviorRef = DialogRef.behavior
            BehaviorRef.skipCollapsed = true
            BehaviorRef.state = BottomSheetBehavior.STATE_EXPANDED
            if (ForceUpdate) BehaviorRef.isDraggable = false
        }
        if (ForceUpdate) DialogRef.setCanceledOnTouchOutside(false)
        return DialogRef
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = SheetAppUpdateBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val BindingObj = ViewBindingObj ?: return
        val ManifestRef = ManifestObj ?: return

        val ReadyFile = UpdateChecker.ApkFileFor(
            ContextRef = requireContext(),
            ManifestObj = ManifestRef
        )
        if (!UpdateChecker.IsDownloading() && ReadyFile.exists() && ReadyFile.length() > 0L) {
            DownloadedFile = ReadyFile
            StateVal = SheetState.Ready
        }

        BindingObj.tvChangeLog.text = BuildNotesText(NotesList = ManifestRef.ChangeLog)
        BindingObj.btnUpdatePrimary.setOnClickListener { OnPrimaryClicked() }
        BindingObj.btnUpdateSecondary.setOnClickListener { OnSecondaryClicked() }

        Render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ViewBindingObj = null
    }

    private fun OnPrimaryClicked() {
        when (StateVal) {
            SheetState.Available, SheetState.Failed -> StartDownload()
            SheetState.Ready -> OnInstallClicked()
            SheetState.Downloading -> Unit
        }
    }

    private fun OnSecondaryClicked() {
        when {
            StateVal == SheetState.Downloading -> {
                UpdateChecker.CancelDownload()
            }

            ForceUpdate -> {
                activity?.finishAffinity()
            }

            else -> dismissAllowingStateLoss()
        }
    }

    private fun StartDownload() {
        val ManifestRef = ManifestObj ?: return
        ErrorText = ""
        StateVal = SheetState.Downloading
        isCancelable = false
        dialog?.setCancelable(false)
        PrepareIndicator(TotalBytes = SizeBytes)
        Render()

        UpdateChecker.Download(ContextRef = requireContext(), ManifestObj = ManifestRef) { StateRef ->
            if (!isAdded) return@Download
            when (StateRef) {
                is UpdateChecker.DownloadState.Running -> ApplyProgress(
                    ReceivedBytes = StateRef.ReceivedBytes,
                    TotalBytes = StateRef.TotalBytes
                )

                is UpdateChecker.DownloadState.Done -> {
                    DownloadedFile = StateRef.FileRef
                    StateVal = SheetState.Ready
                    RestoreCancelable()
                    Render()
                }

                is UpdateChecker.DownloadState.Failed -> {
                    ErrorText = StateRef.MessageText
                    StateVal = SheetState.Failed
                    RestoreCancelable()
                    Render()
                }

                UpdateChecker.DownloadState.Cancelled -> {
                    ErrorText = getString(R.string.update_cancelled)
                    StateVal = SheetState.Available
                    RestoreCancelable()
                    Render()
                }
            }
        }
    }

    private fun RestoreCancelable() {
        isCancelable = !ForceUpdate
        dialog?.setCancelable(!ForceUpdate)
        if (ForceUpdate) dialog?.setCanceledOnTouchOutside(false)
    }

    private fun OnInstallClicked() {
        val FileRef = DownloadedFile ?: return
        val ContextRef = context ?: return

        if (!UpdateInstaller.CanInstallPackages(ContextRef = ContextRef)) {
            ErrorText = getString(R.string.update_install_blocked)
            Render()
            if (!SettingsRequested) {
                SettingsRequested = true
                InstallSettingsLauncher.launch(
                    UpdateInstaller.BuildUnknownSourcesIntent(ContextRef = ContextRef)
                )
            }
            return
        }

        val Started = UpdateInstaller.Install(ContextRef = ContextRef, FileRef = FileRef)
        if (!Started) {
            ErrorText = getString(R.string.update_install_failed)
            Render()
        }
    }

    private fun PrepareIndicator(TotalBytes: Long) {
        val BindingObj = ViewBindingObj ?: return
        val IndicatorRef = BindingObj.progressDownload
        IndicatorRef.visibility = View.GONE
        try {
            IndicatorRef.isIndeterminate = TotalBytes <= 0L
        } catch (_: Exception) {
            IndicatorRef.isIndeterminate = false
        }
        IndicatorRef.progress = 0
    }

    private fun ApplyProgress(ReceivedBytes: Long, TotalBytes: Long) {
        val BindingObj = ViewBindingObj ?: return
        val IndicatorRef = BindingObj.progressDownload
        val WantIndeterminate = TotalBytes <= 0L

        if (IndicatorRef.isIndeterminate != WantIndeterminate) {
            IndicatorRef.visibility = View.GONE
            try {
                IndicatorRef.isIndeterminate = WantIndeterminate
            } catch (_: Exception) {
                return
            }
            IndicatorRef.visibility = View.VISIBLE
        }

        if (WantIndeterminate) {
            BindingObj.tvHeroPercent.visibility = View.GONE
            BindingObj.tvHeroSubtitle.text = getString(
                R.string.update_subtitle_progress_unknown,
                FormatSize(BytesVal = ReceivedBytes)
            )
            return
        }

        val PercentVal = ((ReceivedBytes * 100L) / TotalBytes).toInt().coerceIn(0, 100)
        IndicatorRef.setProgressCompat(PercentVal, true)
        BindingObj.tvHeroPercent.visibility = View.VISIBLE
        BindingObj.tvHeroPercent.text = getString(R.string.update_percent_format, PercentVal)
        BindingObj.tvHeroSubtitle.text = getString(
            R.string.update_subtitle_progress,
            FormatSize(BytesVal = ReceivedBytes),
            FormatSize(BytesVal = TotalBytes)
        )
    }

    private fun Render() {
        val BindingObj = ViewBindingObj ?: return
        val ManifestRef = ManifestObj ?: return
        val ContextRef = requireContext()

        BindingObj.heroBand.setBackgroundResource(
            if (ForceUpdate) R.drawable.bg_update_hero_forced else R.drawable.bg_update_hero
        )
        BindingObj.viewHandle.visibility =
            if (ForceUpdate || StateVal == SheetState.Downloading) View.GONE else View.VISIBLE
        BindingObj.tvHeroSubtitle.setTextColor(
            ContextCompat.getColor(
                ContextRef,
                if (ForceUpdate) R.color.update_hero_subtitle_forced else R.color.update_hero_subtitle
            )
        )
        BindingObj.tvVersionPill.visibility = View.VISIBLE
        BindingObj.tvVersionPill.text = BuildVersionPill(ManifestRef = ManifestRef)

        when (StateVal) {
            SheetState.Available -> RenderAvailable(ManifestRef = ManifestRef)
            SheetState.Downloading -> RenderDownloading()
            SheetState.Ready -> RenderReady()
            SheetState.Failed -> RenderFailed()
        }

        BindingObj.tvUpdateError.visibility = if (ErrorText.isEmpty()) View.GONE else View.VISIBLE
        BindingObj.tvUpdateError.text = ErrorText
    }

    private fun RenderAvailable(ManifestRef: UpdateManifest) {
        val BindingObj = ViewBindingObj ?: return

        SetHeroIcon(IconRes = if (ForceUpdate) R.drawable.ic_lock else R.drawable.ic_update)
        BindingObj.tvHeroTitle.setText(
            if (ForceUpdate) R.string.update_title_required else R.string.update_title_available
        )
        BindingObj.tvHeroSubtitle.text =
            if (ForceUpdate) getString(R.string.update_subtitle_required)
            else BuildSizeSubtitle(ManifestRef = ManifestRef)

        BindingObj.tvHeroPercent.visibility = View.GONE
        BindingObj.progressDownload.visibility = View.GONE
        BindingObj.phaseList.visibility = View.GONE

        BindingObj.btnUpdatePrimary.visibility = View.VISIBLE
        BindingObj.btnUpdatePrimary.setText(R.string.update_action_download)
        BindingObj.btnUpdatePrimary.setIconResource(R.drawable.ic_update)
        BindingObj.btnUpdateSecondary.visibility = View.VISIBLE
        BindingObj.btnUpdateSecondary.setText(
            if (ForceUpdate) R.string.update_action_close_app else R.string.update_action_later
        )
    }

    private fun RenderDownloading() {
        val BindingObj = ViewBindingObj ?: return

        SetHeroIcon(IconRes = R.drawable.ic_update)
        BindingObj.tvHeroTitle.setText(R.string.update_title_downloading)
        BindingObj.progressDownload.visibility = View.VISIBLE
        BindingObj.phaseList.visibility = View.VISIBLE
        if (SizeBytes > 0L) {
            BindingObj.tvHeroSubtitle.text = getString(
                R.string.update_subtitle_progress,
                FormatSize(BytesVal = 0L),
                FormatSize(BytesVal = SizeBytes)
            )
        }

        TintPhase(
            IconView = BindingObj.ivPhaseCheck,
            LabelView = BindingObj.tvPhaseCheck,
            IconRes = R.drawable.ic_check_circle,
            ColourRes = R.color.status_green_text,
            Strong = false
        )
        TintPhase(
            IconView = BindingObj.ivPhaseDownload,
            LabelView = BindingObj.tvPhaseDownload,
            IconRes = R.drawable.ic_record,
            ColourRes = R.color.primary,
            Strong = true
        )
        TintPhase(
            IconView = BindingObj.ivPhaseInstall,
            LabelView = BindingObj.tvPhaseInstall,
            IconRes = R.drawable.ic_phase_pending,
            ColourRes = R.color.text_faint,
            Strong = false
        )

        BindingObj.btnUpdatePrimary.visibility = View.GONE
        BindingObj.btnUpdateSecondary.visibility = View.VISIBLE
        BindingObj.btnUpdateSecondary.setText(R.string.update_action_cancel)
    }

    private fun RenderReady() {
        val BindingObj = ViewBindingObj ?: return

        SetHeroIcon(IconRes = R.drawable.ic_check_circle)
        BindingObj.tvHeroTitle.setText(R.string.update_title_ready)
        BindingObj.tvHeroSubtitle.setText(R.string.update_subtitle_ready)
        BindingObj.tvHeroPercent.visibility = View.GONE
        BindingObj.progressDownload.visibility = View.GONE
        BindingObj.phaseList.visibility = View.VISIBLE

        TintPhase(
            IconView = BindingObj.ivPhaseCheck,
            LabelView = BindingObj.tvPhaseCheck,
            IconRes = R.drawable.ic_check_circle,
            ColourRes = R.color.status_green_text,
            Strong = false
        )
        TintPhase(
            IconView = BindingObj.ivPhaseDownload,
            LabelView = BindingObj.tvPhaseDownload,
            IconRes = R.drawable.ic_check_circle,
            ColourRes = R.color.status_green_text,
            Strong = false
        )
        TintPhase(
            IconView = BindingObj.ivPhaseInstall,
            LabelView = BindingObj.tvPhaseInstall,
            IconRes = R.drawable.ic_record,
            ColourRes = R.color.primary,
            Strong = true
        )

        BindingObj.btnUpdatePrimary.visibility = View.VISIBLE
        BindingObj.btnUpdatePrimary.setText(R.string.update_action_install)
        BindingObj.btnUpdatePrimary.setIconResource(R.drawable.ic_install)
        BindingObj.btnUpdateSecondary.visibility = View.VISIBLE
        BindingObj.btnUpdateSecondary.setText(
            if (ForceUpdate) R.string.update_action_close_app else R.string.update_action_later
        )
    }

    private fun RenderFailed() {
        val BindingObj = ViewBindingObj ?: return

        SetHeroIcon(IconRes = R.drawable.ic_alert)
        BindingObj.tvHeroTitle.setText(R.string.update_title_failed)
        BindingObj.tvHeroSubtitle.text = ManifestObj?.let { ManifestRef ->
            BuildSizeSubtitle(ManifestRef = ManifestRef)
        }.orEmpty()
        BindingObj.tvHeroPercent.visibility = View.GONE
        BindingObj.progressDownload.visibility = View.GONE
        BindingObj.phaseList.visibility = View.GONE

        BindingObj.btnUpdatePrimary.visibility = View.VISIBLE
        BindingObj.btnUpdatePrimary.setText(R.string.update_action_retry)
        BindingObj.btnUpdatePrimary.setIconResource(R.drawable.ic_update)
        BindingObj.btnUpdateSecondary.visibility = View.VISIBLE
        BindingObj.btnUpdateSecondary.setText(
            if (ForceUpdate) R.string.update_action_close_app else R.string.update_action_later
        )
    }

    private fun SetHeroIcon(@DrawableRes IconRes: Int) {
        ViewBindingObj?.ivHeroIcon?.setImageResource(IconRes)
    }

    private fun TintPhase(
        IconView: android.widget.ImageView,
        LabelView: android.widget.TextView,
        @DrawableRes IconRes: Int,
        @ColorRes ColourRes: Int,
        Strong: Boolean
    ) {
        val ContextRef = context ?: return
        IconView.setImageResource(IconRes)
        ImageViewCompat.setImageTintList(
            IconView,
            ColorStateList.valueOf(ContextCompat.getColor(ContextRef, ColourRes))
        )
        LabelView.setTextColor(
            ContextCompat.getColor(
                ContextRef,
                if (Strong) R.color.text_primary else R.color.text_secondary
            )
        )
        LabelView.typeface =
            if (Strong) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun BuildSizeSubtitle(ManifestRef: UpdateManifest): String {
        val NameText = ManifestRef.AppName.ifBlank { getString(R.string.app_name) }
        return if (SizeBytes > 0L) {
            getString(R.string.update_subtitle_size, NameText, FormatSize(BytesVal = SizeBytes))
        } else {
            getString(R.string.update_subtitle_size_unknown, NameText)
        }
    }

    private fun BuildVersionPill(ManifestRef: UpdateManifest): CharSequence {
        val ContextRef = requireContext()
        val LocalText = UpdateVersion.Describe(
            VersionName = UpdateChecker.LocalVersionName(ContextRef = ContextRef),
            VersionCode = UpdateChecker.LocalVersionCode(ContextRef = ContextRef)
        )
        val RemoteText = UpdateVersion.Describe(
            VersionName = ManifestRef.VersionName,
            VersionCode = ManifestRef.VersionCode
        )
        val FullText = getString(R.string.update_version_arrow, LocalText, RemoteText)
        val SpannableRef = SpannableString(FullText)
        SpannableRef.setSpan(
            StrikethroughSpan(),
            0,
            LocalText.length.coerceAtMost(FullText.length),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return SpannableRef
    }

    private fun BuildNotesText(NotesList: List<String>): String {
        if (NotesList.isEmpty()) return getString(R.string.update_notes_none)
        return NotesList.joinToString("\n") { NoteText ->
            getString(R.string.update_note_bullet, NoteText)
        }
    }

    private fun FormatSize(BytesVal: Long): String {
        val MegaBytes = BytesVal.toDouble() / (1024.0 * 1024.0)
        return getString(R.string.update_size_mb, "%.1f".format(MegaBytes))
    }

    companion object {
        const val TAG = "UpdateSheet"

        private const val ARG_APP_NAME = "arg_app_name"
        private const val ARG_VERSION_CODE = "arg_version_code"
        private const val ARG_VERSION_NAME = "arg_version_name"
        private const val ARG_DOWNLOAD_URL = "arg_download_url"
        private const val ARG_FORCE = "arg_force"
        private const val ARG_NOTES = "arg_notes"
        private const val ARG_SIZE_BYTES = "arg_size_bytes"

        fun NewInstance(ManifestObj: UpdateManifest, SizeBytes: Long): UpdateSheet {
            return UpdateSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_APP_NAME, ManifestObj.AppName)
                    putInt(ARG_VERSION_CODE, ManifestObj.VersionCode)
                    putString(ARG_VERSION_NAME, ManifestObj.VersionName)
                    putString(ARG_DOWNLOAD_URL, ManifestObj.DownloadUrl)
                    putBoolean(ARG_FORCE, ManifestObj.ForceUpdate)
                    putStringArrayList(ARG_NOTES, ArrayList(ManifestObj.ChangeLog))
                    putLong(ARG_SIZE_BYTES, SizeBytes)
                }
            }
        }

        fun Show(ManagerRef: FragmentManager, ManifestObj: UpdateManifest, SizeBytes: Long) {
            if (ManagerRef.isStateSaved) return
            if (ManagerRef.findFragmentByTag(TAG) != null) return
            NewInstance(ManifestObj = ManifestObj, SizeBytes = SizeBytes)
                .show(ManagerRef, TAG)
        }
    }
}
