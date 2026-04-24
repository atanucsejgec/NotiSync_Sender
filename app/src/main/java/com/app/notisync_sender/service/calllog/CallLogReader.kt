// ============================================================
// FILE: service/calllog/CallLogReader.kt
// Purpose: Reads call logs from Android CallLog ContentProvider
// ============================================================

package com.app.notisync_sender.service.calllog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.app.notisync_sender.domain.model.CallRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "CallLogReader"

        private val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )
    }

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCallLogsForLast24Hours(): Result<List<CallRecord>> {
        return getCallLogsSince(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
    }

    fun getCallLogsSince(sinceTimestamp: Long): Result<List<CallRecord>> {
        if (!hasCallLogPermission()) {
            Log.e(TAG, "Call log permission not granted")
            return Result.failure(SecurityException("Call log permission not granted"))
        }

        return try {
            val calls = mutableListOf<CallRecord>()

            val selection = "${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf(sinceTimestamp.toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                PROJECTION,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(CallLog.Calls._ID)
                val nameIndex = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)

                while (it.moveToNext()) {
                    try {
                        val callRecord = parseCallRecord(
                            cursor = it,
                            idIndex = idIndex,
                            nameIndex = nameIndex,
                            numberIndex = numberIndex,
                            typeIndex = typeIndex,
                            durationIndex = durationIndex,
                            dateIndex = dateIndex
                        )
                        calls.add(callRecord)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing call record: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Read ${calls.size} call logs since $sinceTimestamp")
            Result.success(calls)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call logs: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getCallLogsBetween(startTimestamp: Long, endTimestamp: Long): Result<List<CallRecord>> {
        if (!hasCallLogPermission()) {
            Log.e(TAG, "Call log permission not granted")
            return Result.failure(SecurityException("Call log permission not granted"))
        }

        return try {
            val calls = mutableListOf<CallRecord>()

            val selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?"
            val selectionArgs = arrayOf(startTimestamp.toString(), endTimestamp.toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                PROJECTION,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(CallLog.Calls._ID)
                val nameIndex = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)

                while (it.moveToNext()) {
                    try {
                        val callRecord = parseCallRecord(
                            cursor = it,
                            idIndex = idIndex,
                            nameIndex = nameIndex,
                            numberIndex = numberIndex,
                            typeIndex = typeIndex,
                            durationIndex = durationIndex,
                            dateIndex = dateIndex
                        )
                        calls.add(callRecord)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing call record: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Read ${calls.size} call logs between $startTimestamp and $endTimestamp")
            Result.success(calls)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call logs: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseCallRecord(
        cursor: Cursor,
        idIndex: Int,
        nameIndex: Int,
        numberIndex: Int,
        typeIndex: Int,
        durationIndex: Int,
        dateIndex: Int
    ): CallRecord {
        val id = cursor.getLong(idIndex).toString()
        val callerName = cursor.getString(nameIndex)
        val phoneNumber = cursor.getString(numberIndex) ?: "Unknown"
        val callTypeInt = cursor.getInt(typeIndex)
        val duration = cursor.getInt(durationIndex)
        val timestamp = cursor.getLong(dateIndex)

        val callType = mapCallType(callTypeInt)
        // (FIX) Use row ID + phoneNumber + timestamp for stronger uniqueness
        val uniqueKey = "$id|$phoneNumber|$timestamp"

        return CallRecord(
            id = UUID.randomUUID().toString(),
            callerName = callerName,
            phoneNumber = phoneNumber,
            callType = callType,
            callDuration = duration,
            timestamp = timestamp,
            uniqueKey = uniqueKey
        )
    }

    private fun mapCallType(type: Int): CallRecord.CallType {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> CallRecord.CallType.INCOMING
            CallLog.Calls.OUTGOING_TYPE -> CallRecord.CallType.OUTGOING
            CallLog.Calls.MISSED_TYPE -> CallRecord.CallType.MISSED
            CallLog.Calls.REJECTED_TYPE -> CallRecord.CallType.REJECTED
            else -> CallRecord.CallType.UNKNOWN
        }
    }

    fun getTotalCallCount(): Int {
        if (!hasCallLogPermission()) return 0

        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                null,
                null,
                null
            )
            cursor?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting call count: ${e.message}")
            0
        }
    }
}
