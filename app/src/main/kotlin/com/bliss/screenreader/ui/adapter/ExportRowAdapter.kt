@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.annotation.SuppressLint
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ItemExportRowBinding
import com.bliss.screenreader.utils.HapticFeedback
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportRowAdapter(
    private var FileList: List<File> = emptyList(),
    private val OnOpen: (File) -> Unit = {},
    private val OnShare: (File) -> Unit = {}
) : RecyclerView.Adapter<ExportRowAdapter.ExportViewHolder>() {

    class ExportViewHolder(val BindingRef: ItemExportRowBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExportViewHolder {
        val BindingObj = ItemExportRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ExportViewHolder(BindingRef = BindingObj)
    }

    override fun getItemCount(): Int = FileList.size

    override fun onBindViewHolder(holder: ExportViewHolder, position: Int) {
        val FileRef = FileList[position]
        val ContextRef = holder.BindingRef.root.context
        val KindLabel = ContextRef.getString(KindRes(FileRef = FileRef))

        holder.BindingRef.tvExportKind.text = KindLabel

        holder.BindingRef.tvExportTitle.text = FriendlyName(FileRef = FileRef)

        val SizeLabel = Formatter.formatShortFileSize(ContextRef, FileRef.length())
        val DateLabel = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
            .format(Date(FileRef.lastModified()))
        holder.BindingRef.tvExportMeta.text = ContextRef.getString(
            R.string.exports_meta_format,
            KindLabel,
            SizeLabel,
            DateLabel
        )

        holder.BindingRef.exportRowRoot.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnOpen(FileRef)
        }
        holder.BindingRef.btnExportShare.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnShare(FileRef)
        }
    }

    private fun KindRes(FileRef: File): Int = when {
        FileRef.name.endsWith(".pdf", ignoreCase = true) -> R.string.exports_kind_pdf
        FileRef.name.endsWith(".json", ignoreCase = true) -> R.string.exports_kind_json
        else -> R.string.exports_kind_excel
    }

    private fun FriendlyName(FileRef: File): String {
        return FileRef.name
            .substringBeforeLast('.')
            .replace(Regex("_\\d{8}_\\d{6}$"), "")
            .replace(Regex("_\\d{8}$"), "")
            .replace('_', ' ')
            .trim()
            .ifEmpty { FileRef.name }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateData(NewFiles: List<File>) {
        FileList = NewFiles
        notifyDataSetChanged()
    }
}
