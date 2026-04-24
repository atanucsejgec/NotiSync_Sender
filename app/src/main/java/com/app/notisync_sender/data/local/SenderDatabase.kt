
// ============================================================
// FILE: data/local/SenderDatabase.kt
// Purpose: Room database definition with all entities
// ============================================================

package com.app.notisync_sender.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PendingBatchEntity::class,
        PendingLocationEntity::class,
        ProcessedLocationRequestEntity::class,
        // YOUR EXISTING: Call log entities
        PendingCallLogBatchEntity::class,
        SyncedCallLogEntity::class,
        // (1) NEW: Sentence batch entity
        PendingSentenceBatchEntity::class
    ],
    // (2) NEW: Bumped version from 3 to 4
    version = 4,
    exportSchema = false
)
abstract class SenderDatabase : RoomDatabase() {

    abstract fun pendingBatchDao(): PendingBatchDao

    abstract fun locationDao(): LocationDao

    // YOUR EXISTING: Call log DAO
    abstract fun callLogDao(): CallLogDao

    // (3) NEW: Sentence DAO
    abstract fun sentenceDao(): SentenceDao
}