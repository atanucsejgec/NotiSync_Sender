// ============================================================
// FILE: domain/model/CapturedSentence.kt
// Purpose: Represents a complete sentence typed by the user
// ============================================================

package com.app.notisync_sender.domain.model

data class CapturedSentence(
    val text: String,
    val capturedAt: Long,
    val appPackage: String
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "text" to text,
            "capturedAt" to capturedAt,
            "appPackage" to appPackage
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromFirestore(map: Map<String, Any>): CapturedSentence {
            return CapturedSentence(
                text = map["text"] as? String ?: "",
                capturedAt = (map["capturedAt"] as? Long) ?: 0L,
                appPackage = map["appPackage"] as? String ?: ""
            )
        }
    }
}