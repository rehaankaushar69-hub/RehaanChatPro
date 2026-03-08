package com.rehaan.bluetoothchat.data.local.dao

import androidx.room.*
import com.rehaan.bluetoothchat.data.local.entities.ChatSessionEntity
import com.rehaan.bluetoothchat.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatSync(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND direction = 'RECEIVED'")
    suspend fun markMessagesAsRead(chatId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0 AND direction = 'RECEIVED'")
    suspend fun getUnreadCount(chatId: String): Int

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun getMessageCount(chatId: String): Int

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' AND chatId = :chatId ORDER BY timestamp DESC")
    suspend fun searchMessages(query: String, chatId: String): List<MessageEntity>
}

@Dao
interface ChatSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Delete
    suspend fun deleteSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageTime DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Query("UPDATE chat_sessions SET lastMessage = :message, lastMessageTime = :time WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, message: String, time: Long)

    @Query("UPDATE chat_sessions SET unreadCount = :count WHERE id = :chatId")
    suspend fun updateUnreadCount(chatId: String, count: Int)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}
