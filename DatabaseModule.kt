package com.rehaan.bluetoothchat.di

import android.content.Context
import com.rehaan.bluetoothchat.data.local.ChatDatabase
import com.rehaan.bluetoothchat.data.local.dao.ChatSessionDao
import com.rehaan.bluetoothchat.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase {
        return ChatDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: ChatDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideChatSessionDao(database: ChatDatabase): ChatSessionDao {
        return database.chatSessionDao()
    }
}
