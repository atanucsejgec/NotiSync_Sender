// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/di/AppModule.kt
// Purpose: Hilt module providing app-wide dependencies including
// Firebase instances, SharedPreferences, and utility objects
// ============================================================

package com.app.notisync_sender.di

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.app.notisync_sender.domain.model.DeviceInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

/**
 * AppModule — Hilt module providing application-wide dependencies.
 *
 * What: Defines how Hilt creates Firebase Auth, Firestore,
 *       SharedPreferences, and DeviceInfo instances.
 *
 * Why: These are shared resources that must be singletons:
 *      - FirebaseAuth: One auth state per app process
 *      - FirebaseFirestore: One Firestore client with connection pooling
 *      - SharedPreferences: One file handle for device settings
 *      - DeviceInfo: One device identity that stays constant
 *
 * @InstallIn(SingletonComponent::class) — Lives for the entire
 *      application lifecycle. Created once, reused everywhere.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the FirebaseAuth singleton instance.
     *
     * What: Returns the default FirebaseAuth instance connected to
     *       the Firebase project defined in google-services.json.
     *
     * Why: FirebaseAuth manages user sessions, tokens, and login state.
     *      Using getInstance() returns the same instance every time —
     *      Hilt caches it as a singleton for injection convenience.
     *      All auth operations (login, register, getCurrentUser)
     *      go through this single instance.
     *
     * @return FirebaseAuth instance
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    /**
     * Provides the FirebaseFirestore singleton instance.
     *
     * What: Returns the default Firestore instance connected to
     *       the Firebase project defined in google-services.json.
     *
     * Why: Firestore manages all cloud database operations —
     *      writing notification batches, registering devices, etc.
     *      Using getInstance() returns the same client with shared
     *      connection pooling, offline cache, and retry logic.
     *
     * @return FirebaseFirestore instance
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    /**
     * Provides SharedPreferences for storing device settings.
     *
     * What: Returns a SharedPreferences instance named "notisync_sender_prefs".
     *
     * Why: SharedPreferences stores small key-value data that persists
     *      across app restarts — perfect for device ID, device name,
     *      and user preferences. MODE_PRIVATE ensures only this app
     *      can read the data.
     *
     * @param context Application context provided by Hilt
     * @return SharedPreferences instance
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(
            /* Preferences file name */
            "notisync_sender_prefs",
            /* Private mode — only accessible by this app */
            Context.MODE_PRIVATE
        )
    }

    /**
     * Provides DeviceInfo containing a unique device ID and name.
     *
     * What: Creates or retrieves a persistent device identity that
     *       uniquely identifies this sender device in the system.
     *
     * Why: The system supports multiple sender devices per user.
     *      Each device needs a stable unique ID so the Receiver App
     *      can distinguish notifications from different senders.
     *      The device ID is generated once (UUID) and stored in
     *      SharedPreferences so it persists across app restarts.
     *      The device name defaults to the Android device model
     *      (e.g., "Pixel 7", "Samsung Galaxy S23") but can be
     *      changed by the user in Settings.
     *
     * How:
     *   1. Check SharedPreferences for existing device ID
     *   2. If found → reuse it (device already registered)
     *   3. If not found → generate new UUID and store it
     *   4. Device name uses Build.MODEL as default
     *
     * @param sharedPreferences SharedPreferences instance (provided above)
     * @return DeviceInfo with persistent device ID and name
     */
    @Provides
    @Singleton
    fun provideDeviceInfo(
        sharedPreferences: SharedPreferences
    ): DeviceInfo {
        /* Key constants for SharedPreferences storage */
        val keyDeviceId = "device_id"
        val keyDeviceName = "device_name"

        /* Try to retrieve existing device ID from SharedPreferences */
        var deviceId = sharedPreferences.getString(keyDeviceId, null)

        /* If no device ID exists, generate a new UUID and persist it */
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit()
                .putString(keyDeviceId, deviceId)
                .apply()
        }

        /* Retrieve device name — default to Android device model name */
        val deviceName = sharedPreferences.getString(
            keyDeviceName,
            /* Build.MODEL returns the consumer-visible device name (e.g., "Pixel 7") */
            Build.MODEL
        ) ?: Build.MODEL

        /* Return DeviceInfo with persistent ID and name */
        return DeviceInfo(
            deviceId = deviceId,
            deviceName = deviceName
        )
    }
}