package com.rehaan.bluetoothchat.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Message(
    val id: Long = 0,
    val chatId: String,
    val content: String,
    val type: MessageType,
    val direction: MessageDirection,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val mimeType: String? = null,
    val duration: Int = 0, // for voice messages in seconds
    val isDelivered: Boolean = false,
    val isRead: Boolean = false,
    val transferProgress: Int = 100
) : Parcelable

enum class MessageType {
    TEXT,
    IMAGE,
    DOCUMENT,
    VOICE,
    FILE
}

enum class MessageDirection {
    SENT,
    RECEIVED
}

@Parcelize
data class DeviceInfo(
    val name: String,
    val address: String,
    val isPaired: Boolean = false,
    val signalStrength: Int = 0
) : Parcelable

data class ChatSession(
    val id: String,
    val deviceName: String,
    val deviceAddress: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val unreadCount: Int = 0,
    val isConnected: Boolean = false
)

data class FileTransfer(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val isSending: Boolean = true
)

data class VoiceMessage(
    val filePath: String,
    val duration: Int,
    val isPlaying: Boolean = false,
    val playbackProgress: Int = 0
)
