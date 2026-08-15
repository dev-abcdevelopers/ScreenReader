@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.exports

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.FragmentExportsBinding
import com.bliss.screenreader.databinding.SheetSessionTransferBinding
import com.bliss.screenreader.sync.SessionBundleStore
import com.bliss.screenreader.sync.SessionPayloadBuilder
import com.bliss.screenreader.sync.SessionUploader
import com.bliss.screenreader.ui.adapter.ExportRowAdapter
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ExportsFragment : Fragment() {

    private var ViewBindingObj: FragmentExportsBinding? = null
    private var TransferBindingObj: SheetSessionTransferBinding? = null
    private var TransferDialogObj: BottomSheetDialog? = null
    private var PendingImportUri: Uri? = null

    private val AdapterObj = ExportRowAdapter(
        OnOpen = { FileRef -> ShareOrOpen(FileRef = FileRef, ForceChooser = false) },
        OnShare = { FileRef -> ShareOrOpen(FileRef = FileRef, ForceChooser = true) }
    )

    private val ImportPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { SelectedUri -> OnFileChosen(SelectedUri = SelectedUri) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = FragmentExportsBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val BindingObj = ViewBindingObj ?: return

        BindingObj.rvExports.layoutManager = LinearLayoutManager(requireContext())
        BindingObj.rvExports.adapter = AdapterObj
        BindingObj.rvExports.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        BindingObj.emptyState.ivEmptyIcon.setImageResource(R.drawable.ic_folder_open)
        BindingObj.emptyState.tvEmptyTitle.setText(R.string.exports_empty_title)
        BindingObj.emptyState.tvEmptyBody.setText(R.string.exports_empty_body)

        BindingObj.btnTransferSessions.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ShowTransferSheet()
        }
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
            if (SheetBinding.importPreview.visibility == View.VISIBLE) View.VISIBLE else View.GONE
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
        val ActivityRef = activity as? AppCompatActivity ?: return
        TransferBindingObj?.btnExportRun?.isEnabled = false

        SessionBundleStore.ExportAsync(ContextRef = requireContext()) { OutcomeVal ->
            if (!isAdded) return@ExportAsync
            TransferBindingObj?.btnExportRun?.isEnabled = true

            val MessageText = when (OutcomeVal) {
                is SessionBundleStore.ExportOutcome.Ready -> {
                    TransferDialogObj?.dismiss()
                    RenderFiles()
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
            CaptureFlow.ShowMessage(ActivityRef = ActivityRef, MessageVal = MessageText)
        }
    }

    private fun OnFileChosen(SelectedUri: Uri?) {
        val ActivityRef = activity as? AppCompatActivity ?: return
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
                    CaptureFlow.ShowMessage(
                        ActivityRef = ActivityRef,
                        MessageVal = getString(
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
        val ActivityRef = activity as? AppCompatActivity ?: return
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
            CaptureFlow.ShowMessage(ActivityRef = ActivityRef, MessageVal = MessageText)
        }
    }

    override fun onResume() {
        super.onResume()
        RenderFiles()
    }

    private fun RenderFiles() {
        val BindingObj = ViewBindingObj ?: return

        val ExportFiles = requireContext().getExternalFilesDir(null)
            ?.listFiles { FileRef ->
                FileRef.isFile && (
                        FileRef.name.endsWith(".pdf", ignoreCase = true) ||
                                FileRef.name.endsWith(".xlsx", ignoreCase = true) ||
                                SessionBundleStore.IsBundleFile(FileNameVal = FileRef.name)
                        )
            }
            ?.toList()
            .orEmpty()

        val UploadFiles = if (SessionUploader.IsEnabled()) {
            SessionPayloadBuilder.UploadDirectory(ContextRef = requireContext())
                .listFiles { FileRef ->
                    FileRef.isFile && FileRef.name.endsWith(".json", ignoreCase = true)
                }
                ?.toList()
                .orEmpty()
        } else {
            emptyList()
        }

        val FileList = (ExportFiles + UploadFiles).sortedByDescending { it.lastModified() }

        AdapterObj.UpdateData(NewFiles = FileList)
        BindingObj.emptyState.emptyStateRoot.visibility =
            if (FileList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun ShareOrOpen(FileRef: File, ForceChooser: Boolean) {
        val FileUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", FileRef
        )
        val MimeType = when {
            FileRef.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            FileRef.name.endsWith(".json", ignoreCase = true) -> "application/json"
            else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }

        val ActionIntent = if (ForceChooser) {
            Intent(Intent.ACTION_SEND).apply {
                type = MimeType
                putExtra(Intent.EXTRA_STREAM, FileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(FileUri, MimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        try {
            startActivity(
                Intent.createChooser(
                    ActionIntent,
                    getString(if (ForceChooser) R.string.exports_share else R.string.exports_open)
                )
            )
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        TransferDialogObj?.dismiss()
        TransferDialogObj = null
        TransferBindingObj = null
        ViewBindingObj = null
    }
}
