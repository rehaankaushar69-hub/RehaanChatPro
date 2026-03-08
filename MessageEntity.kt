package com.rehaan.bluetoothchat.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rehaan.bluetoothchat.domain.model.Message
import com.rehaan.bluetoothchat.domain.model.MessageDirection
import com.rehaan.bluetoothchat.domain.model.MessageType

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String,
    val content: String,
    val type: String,
    val direction: String,
    val timestamp: Long,
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val mimeType: String? = null,
    val duration: Int = 0,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    chatId = chatId,
    content = content,
    type = MessageType.valueOf(type),
    direction = MessageDirection.valueOf(direction),
    timestamp = timestamp,
    filePath = filePath,
    fileName = fileName,
    fileSize = fileSize,
    mimeType = mimeType,
    duration = duration,
    isDelivered = isDelivered,
    isRead = isRead
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    content = content,
    type = type.name,
    direction = direction.name,
    timestamp = timestamp,
    filePath = filePath,
    fileName = fileName,
    fileSize = fileSize,
    mimeType = mimeType,
    duration = duration,
    isDelivered = isDelivered,
    isRead = isRead
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val deviceName: String,
    val deviceAddress: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val unreadCount: Int = 0
)
