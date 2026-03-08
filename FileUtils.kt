package com.rehaan.bluetoothchat.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "unknown_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex) ?: "unknown_file"
                }
            }
        }
        return fileName
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    fun getExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }

    fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return false
            val outputStream = FileOutputStream(destFile)
            val buffer = ByteArray(Constants.BUFFER_SIZE)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            true
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to copy URI to file")
            false
        }
    }

    fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to read bytes from URI")
            null
        }
    }

    fun saveReceivedFile(context: Context, fileName: String, data: ByteArray): File? {
        return try {
            val dir = File(context.filesDir, "received")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(data)
            file
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to save received file")
            null
        }
    }

    fun getReceivedFilesDir(context: Context): File {
        val dir = File(context.filesDir, "received")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getVoiceRecordingDir(context: Context): File {
        val dir = File(context.cacheDir, "voice")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun generateVoiceFileName(): String {
        return "voice_${System.currentTimeMillis()}.3gp"
    }

    fun getFileIcon(fileName: String): Int {
        return when {
            fileName.isImageFile() -> android.R.drawable.ic_menu_gallery
            fileName.isAudioFile() -> android.R.drawable.ic_btn_speak_now
            fileName.isDocumentFile() -> android.R.drawable.ic_menu_edit
            else -> android.R.drawable.ic_menu_agenda
        }
    }

    fun isFileSizeAllowed(sizeBytes: Long): Boolean {
        return sizeBytes <= Constants.MAX_FILE_SIZE_BYTES
    }
}
