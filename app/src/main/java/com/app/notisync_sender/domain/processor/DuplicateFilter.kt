// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/domain/processor/DuplicateFilter.kt
// Purpose: Filters out duplicate notifications within the same
// batch window using uniqueKey-based deduplication
// ============================================================

package com.app.notisync_sender.domain.processor

import com.app.notisync_sender.domain.model.CapturedNotification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DuplicateFilter — Removes duplicate notifications during the
 * 60-second batch collection window.
 *
 * What: Maintains an in-memory HashSet of uniqueKeys seen during
 *       the current batch window. When a new notification arrives,
 *       its uniqueKey is checked against the set. If already present,
 *       the notification is discarded as a duplicate.
 *
 * Why: Many apps (WhatsApp, Telegram, etc.) post multiple identical
 *      notifications within the same minute — for example, a message
 *      notification is re-posted every time the notification shade
 *      is opened. Without filtering, the Receiver would show the
 *      same message multiple times.
 *
 * Algorithm:
 *   1. Incoming notification → compute uniqueKey (appName|title|message|minuteKey)
 *   2. Check if uniqueKey exists in seenKeys HashSet
 *   3. If exists → discard (duplicate)
 *   4. If not exists → add to seenKeys and accept
 *   5. When batch is sent → clear seenKeys for next window
 *
 * Complexity: O(1) per notification check using HashSet.
 *
 * Thread Safety: All access is synchronized to handle concurrent
 *                notifications from NotificationListenerService.
 */
@Singleton
class DuplicateFilter @Inject constructor() {

    /**
     * HashSet storing all uniqueKeys seen during the current batch window.
     * Using a HashSet provides O(1) lookup and insertion time.
     * This is the core data structure for deduplication.
     */
    private val seenKeys = HashSet<String>()

    /**
     * Lock object for thread-safe access to seenKeys.
     * NotificationListenerService can post notifications from any thread,
     * so concurrent access must be synchronized.
     */
    private val lock = Any()

    /**
     * Checks if a notification is a duplicate and filters it.
     *
     * What: Attempts to add the notification's uniqueKey to the seenKeys set.
     *       Returns true if the notification is NEW (not a duplicate).
     *       Returns false if the notification is a DUPLICATE (already seen).
     *
     * Why: This is the main entry point for the dedup algorithm.
     *      Called by BatchProcessor for every incoming notification.
     *
     * How: HashSet.add() returns true if the element was added (new),
     *      false if it already existed (duplicate). This gives us
     *      O(1) dedup checking in a single operation.
     *
     * @param notification The captured notification to check
     * @return true if notification is new and should be kept, false if duplicate
     */
    fun isNew(notification: CapturedNotification): Boolean {
        synchronized(lock) {
            /* add() returns true if uniqueKey was not already in the set (new notification) */
            /* add() returns false if uniqueKey already existed (duplicate notification) */
            return seenKeys.add(notification.uniqueKey)
        }
    }

    /**
     * Clears all tracked uniqueKeys for the next batch window.
     *
     * What: Resets the seenKeys HashSet to empty.
     *
     * Why: Called when a batch is assembled and sent. The next
     *      60-second window starts fresh — notifications that were
     *      duplicates in the previous window are allowed again
     *      because they represent a new time period.
     */
    fun reset() {
        synchronized(lock) {
            seenKeys.clear()
        }
    }

    /**
     * Returns the count of unique notifications tracked in the current window.
     *
     * What: Returns the size of the seenKeys set.
     *
     * Why: Used for logging and UI display — shows how many unique
     *      notifications have been captured so far in the current batch.
     */
    fun currentUniqueCount(): Int {
        synchronized(lock) {
            return seenKeys.size
        }
    }
}