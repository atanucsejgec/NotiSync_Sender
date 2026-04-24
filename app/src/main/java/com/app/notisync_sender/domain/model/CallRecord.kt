// ============================================================
// FILE: domain/model/CallRecord.kt
// Purpose: Represents a single call log entry
// ============================================================

package com.app.notisync_sender.domain.model

data class CallRecord(
    val id: String,
    val callerName: String?,
    val phoneNumber: String,
    val callType: CallType,
    val callDuration: Int,
    val timestamp: Long,
    val uniqueKey: String
) {
    enum class CallType {
        INCOMING,
        OUTGOING,
        MISSED,
        REJECTED,
        UNKNOWN
    }

    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "callerName" to callerName,
            "phoneNumber" to phoneNumber,
            "callType" to callType.name.lowercase(),
            "callDuration" to callDuration,
            "timestamp" to timestamp,
            "uniqueKey" to uniqueKey
        )
    }

    companion object {
        fun generateUniqueKey(phoneNumber: String, timestamp: Long, duration: Int): String {
            return "$phoneNumber|$timestamp|$duration"
        }

        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(map: Map<String, Any?>): CallRecord {
            val id = map["id"] as? String ?: ""
            val callerName = map["callerName"] as? String
            val phoneNumber = map["phoneNumber"] as? String ?: ""
            val callTypeStr = map["callType"] as? String ?: "unknown"
            val callDuration = (map["callDuration"] as? Number)?.toInt() ?: 0
            val timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
            val uniqueKey = map["uniqueKey"] as? String ?: ""

            val callType = when (callTypeStr.lowercase()) {
                "incoming" -> CallType.INCOMING
                "outgoing" -> CallType.OUTGOING
                "missed" -> CallType.MISSED
                "rejected" -> CallType.REJECTED
                else -> CallType.UNKNOWN
            }

            return CallRecord(
                id = id,
                callerName = callerName,
                phoneNumber = phoneNumber,
                callType = callType,
                callDuration = callDuration,
                timestamp = timestamp,
                uniqueKey = uniqueKey
            )
        }
    }
}