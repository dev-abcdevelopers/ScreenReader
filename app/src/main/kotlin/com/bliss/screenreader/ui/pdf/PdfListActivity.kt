@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.pdf

import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.databinding.ActivityPdfListBinding
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.adapter.DocumentAdapter
import java.io.File
import java.util.Locale

class PdfListActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityPdfListBinding
    private lateinit var DocumentAdapterObj: DocumentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityPdfListBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        DocumentAdapterObj = DocumentAdapter(OnShareClick = { FileRef ->
            ShareFile(FileObj = FileRef)
        })

        ViewBindingObj.rvDocuments.layoutManager = LinearLayoutManager(this)
        ViewBindingObj.rvDocuments.adapter = DocumentAdapterObj

        LoadExportedFiles()
    }

    private fun LoadExportedFiles() {
        val ExternalDir = getExternalFilesDir(null) ?: return
        val FileList = ExternalDir.listFiles { _, name ->
            name.endsWith(".pdf", ignoreCase = true) || name.endsWith(".xlsx", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        DocumentAdapterObj.UpdateData(NewFiles = FileList)
    }

    private fun ShareFile(FileObj: File) {
        val ExtStr = FileObj.extension.lowercase(Locale.ROOT)
        val MimeTypeStr = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ExtStr) ?: "*/*"
        val FileUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", FileObj)

        val ShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypeStr
            putExtra(Intent.EXTRA_STREAM, FileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(ShareIntent, "Share Document via"))
    }
}
