// ============================================================
// FILE: di/DatabaseModule.kt
// Purpose: Hilt module providing Room database and all DAOs
// ============================================================

package com.app.notisync_sender.di

import android.content.Context
import androidx.room.Room
import com.app.notisync_sender.data.local.CallLogDao
import com.app.notisync_sender.data.local.LocationDao
import com.app.notisync_sender.data.local.PendingBatchDao
// (1) NEW: Import SentenceDao
import com.app.notisync_sender.data.local.SentenceDao
import com.app.notisync_sender.data.local.SenderDatabase
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
    fun provideSenderDatabase(
        @ApplicationContext context: Context
    ): SenderDatabase {
        return Room.databaseBuilder(
            context,
            SenderDatabase::class.java,
            "notisync_sender_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun providePendingBatchDao(
        database: SenderDatabase
    ): PendingBatchDao {
        return database.pendingBatchDao()
    }

    @Provides
    @Singleton
    fun provideLocationDao(
        database: SenderDatabase
    ): LocationDao {
        return database.locationDao()
    }

    // YOUR EXISTING: Provide CallLogDao for call history operations
    @Provides
    @Singleton
    fun provideCallLogDao(
        database: SenderDatabase
    ): CallLogDao {
        return database.callLogDao()
    }

    // (2) NEW: Provide SentenceDao for keyboard capture operations
    @Provides
    @Singleton
    fun provideSentenceDao(
        database: SenderDatabase
    ): SentenceDao {
        return database.sentenceDao()
    }
}