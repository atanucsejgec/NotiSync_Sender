// ============================================================
// FILE: data/repository/KeyboardRepository.kt
// Purpose: Orchestrates keyboard sentence operations with offline support
// ============================================================

package com.app.notisync_sender.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.app.notisync_sender.data.local.PendingSentenceBatchEntity
import com.app.notisync_sender.data.local.SentenceDao
import com.app.notisync_sender.data.local.SentenceTypeConverter
import com.app.notisync_sender.data.remote.KeyboardFirestoreDataSource
import com.app.notisync_sender.domain.model.SentenceBatch
import com.app.notisync_sender.service.keyboard.KeyboardBatchProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyboardFirestoreDataSource: KeyboardFirestoreDataSource,
    private val sentenceDao: SentenceDao,
    private val keyboardBatchProcessor: KeyboardBatchProcessor
) {

    companion object {
        private const val TAG = "KeyboardRepository"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isCollecting = false

    fun startCollecting() {
        if (isCollecting) {
            Log.d(TAG, "Already collecting sentences")
            return
        }

        isCollecting = true
        Log.d(TAG, "Starting sentence batch collection")

        scope.launch {
            keyboardBatchProcessor.batchFlow.collect { batch ->
                handleBatch(batch)
            }
        }
    }

    fun stopCollecting() {
        isCollecting = false
        Log.d(TAG, "Stopped sentence batch collection")
    }

    private suspend fun handleBatch(batch: SentenceBatch) {
        Log.d(TAG, "Handling sentence batch: ${batch.sentences.size} sentences")

        if (isNetworkAvailable()) {
            val uploadResult = keyboardFirestoreDataSource.uploadSentenceBatch(batch)

            if (uploadResult.isSuccess) {
                Log.d(TAG, "Sentence batch uploaded successfully")
            } else {
                Log.w(TAG, "Upload failed — saving to local queue")
                saveBatchToLocalQueue(batch)
            }
        } else {
            Log.d(TAG, "Offline — saving sentence batch to local queue")
            saveBatchToLocalQueue(batch)
        }
    }

    private suspend fun saveBatchToLocalQueue(batch: SentenceBatch) {
        try {
            val sentencesJson = SentenceTypeConverter.toJson(batch.sentences)

            val entity = PendingSentenceBatchEntity(
                batchId = batch.batchId,
                userId = batch.userId,
                deviceId = batch.deviceId,
                deviceName = batch.deviceName,
                batchTimestamp = batch.batchTimestamp,
                sentencesJson = sentencesJson,
                sentenceCount = batch.sentences.size,
                isSynced = false,
                retryCount = 0
            )

            sentenceDao.insertPendingBatch(entity)
            Log.d(TAG, "Sentence batch saved to local queue: ${batch.batchId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sentence batch: ${e.message}", e)
        }
    }

    suspend fun retryPendingBatches(): Int {
        Log.d(TAG, "Retrying pending sentence batches...")

        val pendingBatches = sentenceDao.getUnsyncedBatches()

        if (pendingBatches.isEmpty()) {
            Log.d(TAG, "No pending sentence batches")
            return 0
        }

        var successCount = 0

        for (entity in pendingBatches) {
            val batch = entityToBatch(entity)
            val uploadResult = keyboardFirestoreDataSource.uploadSentenceBatch(batch)

            if (uploadResult.isSuccess) {
                sentenceDao.markAsSynced(entity.batchId)
                successCount++
            } else {
                sentenceDao.incrementRetryCount(entity.batchId)
            }
        }

        sentenceDao.deleteSyncedBatches()
        sentenceDao.deleteFailedBatches(maxRetries = 5)

        Log.d(TAG, "Retry complete: $successCount/${pendingBatches.size}")
        return successCount
    }

    private fun entityToBatch(entity: PendingSentenceBatchEntity): SentenceBatch {
        val sentences = SentenceTypeConverter.fromJson(entity.sentencesJson)

        return SentenceBatch(
            batchId = entity.batchId,
            userId = entity.userId,
            deviceId = entity.deviceId,
            deviceName = entity.deviceName,
            batchTimestamp = entity.batchTimestamp,
            sentences = sentences
        )
    }

    fun observePendingBatchCount(): Flow<Int> {
        return sentenceDao.observeUnsyncedCount()
    }

    suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing all local sentence data")
        sentenceDao.deleteAllBatches()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}