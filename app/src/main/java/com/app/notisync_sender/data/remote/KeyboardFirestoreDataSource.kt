// ============================================================
// FILE: data/remote/KeyboardFirestoreDataSource.kt
// Purpose: Handles Firestore operations for keyboard sentence data
// ============================================================

package com.app.notisync_sender.data.remote

import android.util.Log
import com.app.notisync_sender.domain.model.SentenceBatch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "KeyboardFirestoreDS"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_KEYBOARD_SENTENCES = "keyboard_sentences"
    }

    suspend fun uploadSentenceBatch(batch: SentenceBatch): Result<Unit> {
        return try {
            val batchMap = batch.toFirestoreMap()

            firestore
                .collection(COLLECTION_USERS)
                .document(batch.userId)
                .collection(COLLECTION_DEVICES)
                .document(batch.deviceId)
                .collection(COLLECTION_KEYBOARD_SENTENCES)
                .document(batch.batchId)
                .set(batchMap)
                .await()

            Log.d(TAG, "Sentence batch uploaded: ${batch.batchId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload sentence batch: ${e.message}", e)
            Result.failure(e)
        }
    }
}