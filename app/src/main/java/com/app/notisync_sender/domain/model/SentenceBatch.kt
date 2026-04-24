// ============================================================
// FILE: domain/model/SentenceBatch.kt
// Purpose: Represents a 2-hour batch of captured sentences
// ============================================================

package com.app.notisync_sender.domain.model

import java.util.UUID

data class SentenceBatch(
    val batchId: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val batchTimestamp: Long,
    val sentences: List<CapturedSentence>
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "batchId" to batchId,
            "userId" to userId,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "batchTimestamp" to batchTimestamp,
            "sentences" to sentences.map { it.toFirestoreMap() }
        )
    }

    companion object {
        fun create(
            userId: String,
            deviceId: String,
            deviceName: String,
            sentences: List<CapturedSentence>
        ): SentenceBatch {
            return SentenceBatch(
                batchId = UUID.randomUUID().toString(),
                userId = userId,
                deviceId = deviceId,
                deviceName = deviceName,
                batchTimestamp = System.currentTimeMillis(),
                sentences = sentences
            )
        }
    }
}