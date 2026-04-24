// ============================================================
// FILE: domain/model/LocationRequest.kt
// Purpose: Represents a location request from Receiver to Sender
// ============================================================

package com.app.notisync_sender.domain.model

data class LocationRequest(
    val requestId: String,
    val userId: String,
    val deviceId: String,
    val requestedAt: Long,
    val requestedBy: String,
    val status: LocationRequestStatus,
    val expiresAt: Long
) {
    enum class LocationRequestStatus {
        PENDING,
        FULFILLED,
        EXPIRED
    }

    companion object {
        private const val REQUEST_EXPIRY_MS = 24 * 60 * 60 * 1000L

        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(map: Map<String, Any>): LocationRequest {
            val requestId = map["requestId"] as? String ?: ""
            val userId = map["userId"] as? String ?: ""
            val deviceId = map["deviceId"] as? String ?: ""
            val requestedAt = (map["requestedAt"] as? Long) ?: 0L
            val requestedBy = map["requestedBy"] as? String ?: ""
            val statusStr = map["status"] as? String ?: "pending"
            val expiresAt = (map["expiresAt"] as? Long) ?: (requestedAt + REQUEST_EXPIRY_MS)

            val status = when (statusStr.lowercase()) {
                "fulfilled" -> LocationRequestStatus.FULFILLED
                "expired" -> LocationRequestStatus.EXPIRED
                else -> LocationRequestStatus.PENDING
            }

            return LocationRequest(
                requestId = requestId,
                userId = userId,
                deviceId = deviceId,
                requestedAt = requestedAt,
                requestedBy = requestedBy,
                status = status,
                expiresAt = expiresAt
            )
        }
    }

    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }

    fun isPending(): Boolean {
        return status == LocationRequestStatus.PENDING && !isExpired()
    }
}