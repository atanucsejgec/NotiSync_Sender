// ============================================================
// FILE: data/remote/CallLogFirestoreDataSource.kt
// Purpose: Handles Firestore operations for call log data
// ============================================================

package com.app.notisync_sender.data.remote

import android.util.Log
import com.app.notisync_sender.domain.model.CallLogBatch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "CallLogFirestoreDS"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_DAILY_CALL_LOGS = "daily_call_logs"
    }

    suspend fun uploadCallLogBatch(batch: CallLogBatch): Result<Unit> {
        return try {
            val batchMap = batch.toFirestoreMap()

            firestore
                .collection(COLLECTION_USERS)
                .document(batch.userId)
                .collection(COLLECTION_DEVICES)
                .document(batch.deviceId)
                .collection(COLLECTION_DAILY_CALL_LOGS)
                .document(batch.batchId)
                .set(batchMap)
                .await()

            Log.d(TAG, "Call log batch uploaded: ${batch.batchId} (${batch.callCount} calls)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload call log batch: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCallLogBatches(
        userId: String,
        deviceId: String,
        limit: Long = 30
    ): Result<List<CallLogBatch>> {
        return try {
            val snapshot = firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_DAILY_CALL_LOGS)
                .orderBy("batchTimestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val batches = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data?.let { CallLogBatch.fromFirestore(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing call log batch: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "Retrieved ${batches.size} call log batches")
            Result.success(batches)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get call log batches: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getBatchByDate(
        userId: String,
        deviceId: String,
        batchDate: String
    ): Result<CallLogBatch?> {
        return try {
            val snapshot = firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_DAILY_CALL_LOGS)
                .whereEqualTo("batchDate", batchDate)
                .limit(1)
                .get()
                .await()

            val batch = snapshot.documents.firstOrNull()?.data?.let {
                CallLogBatch.fromFirestore(it)
            }

            Result.success(batch)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get batch by date: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteOldBatches(
        userId: String,
        deviceId: String,
        keepDays: Int = 30
    ): Result<Int> {
        return try {
            val cutoffTimestamp = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)

            val snapshot = firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_DAILY_CALL_LOGS)
                .whereLessThan("batchTimestamp", cutoffTimestamp)
                .get()
                .await()

            var deletedCount = 0
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
                deletedCount++
            }

            Log.d(TAG, "Deleted $deletedCount old call log batches")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old batches: ${e.message}", e)
            Result.failure(e)
        }
    }
}