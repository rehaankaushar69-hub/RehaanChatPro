package com.rehaan.bluetoothchat.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Fragment.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    requireContext().showToast(message, duration)
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun Long.toFormattedTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toReadableFileSize(): String {
    return when {
        this < 1024 -> "$this B"
        this < 1024 * 1024 -> "${this / 1024} KB"
        this < 1024 * 1024 * 1024 -> "${this / (1024 * 1024)} MB"
        else -> "${this / (1024 * 1024 * 1024)} GB"
    }
}

fun String.isImageFile(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
           lower.endsWith(".png") || lower.endsWith(".webp") ||
           lower.endsWith(".gif")
}

fun String.isAudioFile(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".mp3") || lower.endsWith(".aac") ||
           lower.endsWith(".wav") || lower.endsWith(".ogg") ||
           lower.endsWith(".3gp") || lower.endsWith(".m4a")
}

fun String.isDocumentFile(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".pdf") || lower.endsWith(".txt") ||
           lower.endsWith(".doc") || lower.endsWith(".docx") ||
           lower.endsWith(".xls") || lower.endsWith(".xlsx")
}

fun ByteArray.toHexString(): String {
    return joinToString("") { "%02X".format(it) }
}
