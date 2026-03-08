package com.rehaan.bluetoothchat.data.repository

import com.rehaan.bluetoothchat.data.local.dao.ChatSessionDao
import com.rehaan.bluetoothchat.data.local.dao.MessageDao
import com.rehaan.bluetoothchat.data.local.entities.ChatSessionEntity
import com.rehaan.bluetoothchat.data.local.entities.toDomain
import com.rehaan.bluetoothchat.data.local.entities.toEntity
import com.rehaan.bluetoothchat.domain.model.ChatSession
import com.rehaan.bluetoothchat.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val chatSessionDao: ChatSessionDao
) {

    fun getMessagesForChat(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun insertMessage(message: Message): Long {
        val id = messageDao.insertMessage(message.toEntity())
        updateLastMessageInSession(message.chatId, message.content, message.timestamp)
        return id
    }

    suspend fun updateMessage(message: Message) {
        messageDao.updateMessage(message.toEntity())
    }

    suspend fun deleteMessage(message: Message) {
        messageDao.deleteMessage(message.toEntity())
    }

    suspend fun deleteMessagesForChat(chatId: String) {
        messageDao.deleteMessagesForChat(chatId)
    }

    suspend fun markMessagesAsRead(chatId: String) {
        messageDao.markMessagesAsRead(chatId)
        chatSessionDao.updateUnreadCount(chatId, 0)
    }

    suspend fun getUnreadCount(chatId: String): Int {
        return messageDao.getUnreadCount(chatId)
    }

    suspend fun searchMessages(query: String, chatId: String): List<Message> {
        return messageDao.searchMessages(query, chatId).map { it.toDomain() }
    }

    fun getAllChatSessions(): Flow<List<ChatSession>> {
        return chatSessionDao.getAllSessions().map { entities ->
            entities.map { entity ->
                ChatSession(
                    id = entity.id,
                    deviceName = entity.deviceName,
                    deviceAddress = entity.deviceAddress,
                    lastMessage = entity.lastMessage,
                    lastMessageTime = entity.lastMessageTime,
                    unreadCount = entity.unreadCount
                )
            }
        }
    }

    suspend fun createOrUpdateSession(deviceName: String, deviceAddress: String) {
        val chatId = generateChatId(deviceAddress)
        val existing = chatSessionDao.getSessionById(chatId)
        if (existing == null) {
            chatSessionDao.insertSession(
                ChatSessionEntity(
                    id = chatId,
                    deviceName = deviceName,
                    deviceAddress = deviceAddress
                )
            )
        }
    }

    suspend fun deleteSession(chatId: String) {
        val session = chatSessionDao.getSessionById(chatId) ?: return
        chatSessionDao.deleteSession(session)
        deleteMessagesForChat(chatId)
    }

    private suspend fun updateLastMessageInSession(chatId: String, content: String, time: Long) {
        chatSessionDao.updateLastMessage(chatId, content, time)
        val unread = messageDao.getUnreadCount(chatId)
        chatSessionDao.updateUnreadCount(chatId, unread)
    }

    companion object {
        fun generateChatId(deviceAddress: String): String {
            return "chat_${deviceAddress.replace(":", "_")}"
        }
    }
}
