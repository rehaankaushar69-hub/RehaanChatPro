package com.rehaan.bluetoothchat.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.rehaan.bluetoothchat.data.local.dao.ChatSessionDao
import com.rehaan.bluetoothchat.data.local.dao.MessageDao
import com.rehaan.bluetoothchat.data.local.entities.ChatSessionEntity
import com.rehaan.bluetoothchat.data.local.entities.MessageEntity
import com.rehaan.bluetoothchat.utils.Constants

@Database(
    entities = [MessageEntity::class, ChatSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun chatSessionDao(): ChatSessionDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
