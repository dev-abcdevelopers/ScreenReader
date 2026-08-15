@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.exports

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.FragmentExportsBinding
import com.bliss.screenreader.sync.SessionPayloadBuilder
import com.bliss.screenreader.sync.SessionUploader
import com.bliss.screenreader.ui.adapter.ExportRowAdapter
import java.io.File


class ExportsFragment : Fragment() {

    private var ViewBindingObj: FragmentExportsBinding? = null
    private val AdapterObj = ExportRowAdapter(
        OnOpen = { FileRef -> ShareOrOpen(FileRef = FileRef, ForceChooser = false) },
        OnShare = { FileRef -> ShareOrOpen(FileRef = FileRef, ForceChooser = true) }
    )

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
                                FileRef.name.endsWith(".xlsx", ignoreCase = true)
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
        ViewBindingObj = null
    }
}
