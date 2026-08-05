@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ItemDocumentBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentAdapter(
    private var FileList: List<File> = emptyList(),
    private val OnShareClick: (File) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.ViewHolder>() {

    fun UpdateData(NewFiles: List<File>) {
        this.FileList = NewFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ViewBindingObj = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(BindingRef = ViewBindingObj)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.Bind(TargetFile = FileList[position], OnShareCallback = OnShareClick)
    }

    override fun getItemCount(): Int = FileList.size

    class ViewHolder(private val BindingRef: ItemDocumentBinding) :
        RecyclerView.ViewHolder(BindingRef.root) {

        fun Bind(TargetFile: File, OnShareCallback: (File) -> Unit) {
            BindingRef.tvDocName.text = TargetFile.name
            val SizeKb = TargetFile.length() / 1024
            val LastModifiedStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(TargetFile.lastModified()))
            val ExtensionStr = TargetFile.extension.uppercase(Locale.ROOT)
            BindingRef.tvDocSub.text = "$ExtensionStr Document • ${SizeKb} KB • $LastModifiedStr"

            if (ExtensionStr == "PDF") {
                BindingRef.ivDocIcon.setImageResource(R.drawable.ic_policy)
            } else {
                BindingRef.ivDocIcon.setImageResource(R.drawable.ic_export)
            }

            BindingRef.btnShare.setOnClickListener {
                OnShareCallback(TargetFile)
            }
        }
    }
}
