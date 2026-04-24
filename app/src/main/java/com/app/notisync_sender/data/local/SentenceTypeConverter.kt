// ============================================================
// FILE: data/local/SentenceTypeConverter.kt
// Purpose: Converts CapturedSentence list to/from JSON
// ============================================================

package com.app.notisync_sender.data.local

import com.app.notisync_sender.domain.model.CapturedSentence
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SentenceTypeConverter {

    private val gson = Gson()

    private val listType = object : TypeToken<List<CapturedSentence>>() {}.type

    fun toJson(sentences: List<CapturedSentence>): String {
        return gson.toJson(sentences)
    }

    fun fromJson(json: String?): List<CapturedSentence> {
        if (json.isNullOrBlank()) return emptyList()

        return try {
            gson.fromJson(json, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }
}