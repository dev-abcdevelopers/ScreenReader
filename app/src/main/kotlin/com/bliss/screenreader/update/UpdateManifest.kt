@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.update

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class UpdateManifest(
    val AppName: String,
    val VersionCode: Int,
    val VersionName: String,
    val DownloadUrl: String,
    val ForceUpdate: Boolean,
    val ChangeLog: List<String>
) {

    companion object {

        private const val FORCE_KEY = "ForceUserToUpdateAppWhenNewerVersionAvailable"
        private val NUMBER_PREFIX = Regex("^\\s*\\d+\\s*[.)\\-]\\s*")

        fun Parse(JsonText: String): UpdateManifest? {
            if (JsonText.isBlank()) return null

            val RootObject = try {
                JsonParser.parseString(JsonText)
            } catch (_: Exception) {
                null
            }?.takeIf { ElementRef -> ElementRef.isJsonObject }?.asJsonObject ?: return null

            val VersionObject = ReadObject(ParentObject = RootObject, KeyName = "Version")
            val CodeVal = ReadInt(
                ParentObject = VersionObject ?: RootObject,
                KeyName = "VersionCode"
            ) ?: return null
            val NameVal = ReadString(
                ParentObject = VersionObject ?: RootObject,
                KeyName = "VersionName"
            ).trim()
            val UrlVal = ReadString(ParentObject = RootObject, KeyName = "DownloadUrl").trim()
            if (UrlVal.isEmpty()) return null

            return UpdateManifest(
                AppName = ReadString(ParentObject = RootObject, KeyName = "AppName").trim(),
                VersionCode = CodeVal,
                VersionName = NameVal,
                DownloadUrl = UrlVal,
                ForceUpdate = ReadBoolean(ParentObject = RootObject, KeyName = FORCE_KEY),
                ChangeLog = ReadChangeLog(RootObject = RootObject)
            )
        }

        fun ReadChangeLog(RootObject: JsonObject): List<String> {
            val ChangeElement = RootObject.get("ChangeLog") ?: return emptyList()
            val RawText = when {
                ChangeElement.isJsonNull -> ""
                ChangeElement.isJsonPrimitive -> ChangeElement.asString
                ChangeElement.isJsonArray -> ChangeElement.asJsonArray
                    .filter { ItemElement -> ItemElement.isJsonPrimitive }
                    .joinToString("\n") { ItemElement -> ItemElement.asString }

                ChangeElement.isJsonObject -> {
                    val ChangeObject = ChangeElement.asJsonObject
                    val NoteText = ReadString(ParentObject = ChangeObject, KeyName = "Note")
                    if (NoteText.isNotBlank()) NoteText
                    else ChangeObject.entrySet()
                        .mapNotNull { EntryRef -> AsPlainString(ElementRef = EntryRef.value) }
                        .joinToString("\n")
                }

                else -> ""
            }
            return SplitNotes(RawText = RawText)
        }

        fun SplitNotes(RawText: String): List<String> {
            if (RawText.isBlank()) return emptyList()
            return RawText
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .map { LineText -> LineText.trim().replace(NUMBER_PREFIX, "") }
                .filter { LineText -> LineText.isNotEmpty() }
        }

        private fun AsPlainString(ElementRef: JsonElement?): String? {
            if (ElementRef == null || !ElementRef.isJsonPrimitive) return null
            return ElementRef.asString.takeIf { ValueText -> ValueText.isNotBlank() }
        }

        private fun ReadObject(ParentObject: JsonObject, KeyName: String): JsonObject? {
            val ElementRef = ParentObject.get(KeyName) ?: return null
            return if (ElementRef.isJsonObject) ElementRef.asJsonObject else null
        }

        private fun ReadString(ParentObject: JsonObject, KeyName: String): String {
            val ElementRef = ParentObject.get(KeyName) ?: return ""
            if (!ElementRef.isJsonPrimitive) return ""
            return try {
                ElementRef.asString
            } catch (_: Exception) {
                ""
            }
        }

        private fun ReadInt(ParentObject: JsonObject, KeyName: String): Int? {
            val ElementRef = ParentObject.get(KeyName) ?: return null
            if (!ElementRef.isJsonPrimitive) return null
            return try {
                ElementRef.asInt
            } catch (_: Exception) {
                ElementRef.asString.trim().toIntOrNull()
            }
        }

        private fun ReadBoolean(ParentObject: JsonObject, KeyName: String): Boolean {
            val ElementRef = ParentObject.get(KeyName) ?: return false
            if (!ElementRef.isJsonPrimitive) return false
            return try {
                ElementRef.asBoolean
            } catch (_: Exception) {
                ElementRef.asString.trim().equals("true", ignoreCase = true)
            }
        }
    }
}
