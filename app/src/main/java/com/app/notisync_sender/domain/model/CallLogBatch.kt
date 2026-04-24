// ============================================================
// FILE: domain/model/CallLogBatch.kt
// Purpose: Represents a batch of call logs for a single day
// ============================================================

package com.app.notisync_sender.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CallLogBatch(
    val batchId: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val batchDate: String,
    val batchTimestamp: Long,
    val calls: List<CallRecord>,
    val isSynced: Boolean = false
) {
    val callCount: Int get() = calls.size

    val isEmpty: Boolean get() = calls.isEmpty()

    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "batchId" to batchId,
            "userId" to userId,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "batchDate" to batchDate,
            "batchTimestamp" to batchTimestamp,
            "callCount" to callCount,
            "calls" to calls.map { it.toFirestoreMap() }
        )
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun create(
            userId: String,
            deviceId: String,
            deviceName: String,
            calls: List<CallRecord>
        ): CallLogBatch {
            val now = System.currentTimeMillis()
            return CallLogBatch(
                batchId = UUID.randomUUID().toString(),
                userId = userId,
                deviceId = deviceId,
                deviceName = deviceName,
                batchDate = dateFormat.format(Date(now)),
                batchTimestamp = now,
                calls = calls,
                isSynced = false
            )
        }

        fun getBatchDateForTimestamp(timestamp: Long): String {
            return dateFormat.format(Date(timestamp))
        }

        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(map: Map<String, Any>): CallLogBatch {
            val batchId = map["batchId"] as? String ?: ""
            val userId = map["userId"] as? String ?: ""
            val deviceId = map["deviceId"] as? String ?: ""
            val deviceName = map["deviceName"] as? String ?: "Unknown"
            val batchDate = map["batchDate"] as? String ?: ""
            val batchTimestamp = (map["batchTimestamp"] as? Number)?.toLong() ?: 0L

            val callsList = map["calls"] as? List<Map<String, Any?>> ?: emptyList()
            val calls = callsList.map { CallRecord.fromFirestore(it) }

            return CallLogBatch(
                batchId = batchId,
                userId = userId,
                deviceId = deviceId,
                deviceName = deviceName,
                batchDate = batchDate,
                batchTimestamp = batchTimestamp,
                calls = calls,
                isSynced = true
            )
        }
    }
}