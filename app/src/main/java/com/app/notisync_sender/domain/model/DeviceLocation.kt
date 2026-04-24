// ============================================================
// FILE: domain/model/DeviceLocation.kt
// Purpose: Represents a captured device location
// ============================================================

package com.app.notisync_sender.domain.model

data class DeviceLocation(
    val locationId: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val provider: LocationProvider,
    val requestId: String? = null
) {
    enum class LocationProvider {
        GPS,
        NETWORK,
        FUSED,
        IP,
        CELL_ID,
        UNKNOWN
    }

    fun toFirestoreMap(): Map<String, Any> {
        return mutableMapOf(
            "locationId" to locationId,
            "userId" to userId,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "latitude" to latitude,
            "longitude" to longitude,
            "accuracy" to accuracy,
            "timestamp" to timestamp,
            "provider" to provider.name.lowercase()
        ).apply {
            requestId?.let { put("requestId", it) }
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(map: Map<String, Any>): DeviceLocation {
            val locationId = map["locationId"] as? String ?: ""
            val userId = map["userId"] as? String ?: ""
            val deviceId = map["deviceId"] as? String ?: ""
            val deviceName = map["deviceName"] as? String ?: "Unknown"
            val latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0
            val longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0
            val accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f
            val timestamp = (map["timestamp"] as? Long) ?: 0L
            val providerStr = map["provider"] as? String ?: "unknown"
            val requestId = map["requestId"] as? String

            val provider = when (providerStr.lowercase()) {
                "gps" -> LocationProvider.GPS
                "network" -> LocationProvider.NETWORK
                "fused" -> LocationProvider.FUSED
                "ip" -> LocationProvider.IP
                "cell_id" -> LocationProvider.CELL_ID
                else -> LocationProvider.UNKNOWN
            }

            return DeviceLocation(
                locationId = locationId,
                userId = userId,
                deviceId = deviceId,
                deviceName = deviceName,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                timestamp = timestamp,
                provider = provider,
                requestId = requestId
            )
        }
    }
}
