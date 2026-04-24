// ============================================================
// FILE: data/local/CallLogTypeConverter.kt
// Purpose: Converts CallRecord list to/from JSON for Room storage
// ============================================================

package com.app.notisync_sender.data.local

import com.app.notisync_sender.domain.model.CallRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CallLogTypeConverter {

    private val gson = Gson()

    private val listType = object : TypeToken<List<CallRecordJson>>() {}.type

    fun toJson(calls: List<CallRecord>): String {
        val jsonList = calls.map { it.toJsonModel() }
        return gson.toJson(jsonList)
    }

    fun fromJson(json: String?): List<CallRecord> {
        if (json.isNullOrBlank()) return emptyList()

        return try {
            val jsonList: List<CallRecordJson> = gson.fromJson(json, listType)
            jsonList.map { it.toCallRecord() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun CallRecord.toJsonModel(): CallRecordJson {
        return CallRecordJson(
            id = this.id,
            callerName = this.callerName,
            phoneNumber = this.phoneNumber,
            callType = this.callType.name,
            callDuration = this.callDuration,
            timestamp = this.timestamp,
            uniqueKey = this.uniqueKey
        )
    }

    private fun CallRecordJson.toCallRecord(): CallRecord {
        val type = when (this.callType.uppercase()) {
            "INCOMING" -> CallRecord.CallType.INCOMING
            "OUTGOING" -> CallRecord.CallType.OUTGOING
            "MISSED" -> CallRecord.CallType.MISSED
            "REJECTED" -> CallRecord.CallType.REJECTED
            else -> CallRecord.CallType.UNKNOWN
        }

        return CallRecord(
            id = this.id,
            callerName = this.callerName,
            phoneNumber = this.phoneNumber,
            callType = type,
            callDuration = this.callDuration,
            timestamp = this.timestamp,
            uniqueKey = this.uniqueKey
        )
    }

    private data class CallRecordJson(
        val id: String,
        val callerName: String?,
        val phoneNumber: String,
        val callType: String,
        val callDuration: Int,
        val timestamp: Long,
        val uniqueKey: String
    )
}