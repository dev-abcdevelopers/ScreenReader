@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import androidx.core.util.AtomicFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class BlobFileStore(private val DirRef: File) {

    private val LockMap = ConcurrentHashMap<String, Any>()

    fun LockFor(KeyText: String): Any = LockMap.computeIfAbsent(KeyText) { Any() }

    fun Exists(KeyText: String): Boolean = FileFor(KeyText = KeyText).exists()

    fun Read(KeyText: String): String? {
        val FileRef = FileFor(KeyText = KeyText)
        if (!FileRef.exists()) return null
        return try {
            String(AtomicFile(FileRef).readFully(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun Write(KeyText: String, StoredText: String): Boolean {
        if (!DirRef.exists() && !DirRef.mkdirs() && !DirRef.exists()) return false
        val AtomicRef = AtomicFile(FileFor(KeyText = KeyText))
        val StreamRef = try {
            AtomicRef.startWrite()
        } catch (_: Exception) {
            return false
        }
        return try {
            StreamRef.write(StoredText.toByteArray(Charsets.UTF_8))
            AtomicRef.finishWrite(StreamRef)
            true
        } catch (_: Exception) {
            AtomicRef.failWrite(StreamRef)
            false
        }
    }

    fun WriteVerified(KeyText: String, StoredText: String): Boolean {
        if (!Write(KeyText = KeyText, StoredText = StoredText)) return false
        return Read(KeyText = KeyText) == StoredText
    }

    fun Delete(KeyText: String) {
        runCatching { AtomicFile(FileFor(KeyText = KeyText)).delete() }
    }

    fun Keys(): List<String> {
        val FileList = DirRef.listFiles() ?: return emptyList()
        return FileList
            .filter { FileRef -> FileRef.isFile && FileRef.name.endsWith(SUFFIX) }
            .mapNotNull { FileRef -> DecodeName(NameText = FileRef.name.removeSuffix(SUFFIX)) }
    }

    fun DeleteAll() {
        for (KeyText in Keys()) Delete(KeyText = KeyText)
    }

    private fun FileFor(KeyText: String): File = File(DirRef, EncodeName(KeyText = KeyText) + SUFFIX)

    companion object {
        private const val SUFFIX = ".blob"
        private const val HEX_DIGITS = "0123456789abcdef"

        fun EncodeName(KeyText: String): String {
            val BytesVal = KeyText.toByteArray(Charsets.UTF_8)
            val Builder = StringBuilder(BytesVal.size * 2)
            for (ByteVal in BytesVal) {
                val IntVal = ByteVal.toInt() and 0xFF
                Builder.append(HEX_DIGITS[IntVal ushr 4])
                Builder.append(HEX_DIGITS[IntVal and 0x0F])
            }
            return Builder.toString()
        }

        fun DecodeName(NameText: String): String? {
            if (NameText.isEmpty() || NameText.length % 2 != 0) return null
            val BytesVal = ByteArray(NameText.length / 2)
            for (Index in BytesVal.indices) {
                val High = HEX_DIGITS.indexOf(NameText[Index * 2])
                val Low = HEX_DIGITS.indexOf(NameText[Index * 2 + 1])
                if (High < 0 || Low < 0) return null
                BytesVal[Index] = ((High shl 4) or Low).toByte()
            }
            return String(BytesVal, Charsets.UTF_8)
        }
    }
}
