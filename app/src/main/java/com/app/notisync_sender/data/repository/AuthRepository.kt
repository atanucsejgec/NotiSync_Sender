// ============================================================
// FILE: app/src/main/java/com/app/notisync_sender/data/repository/AuthRepository.kt
// Purpose: Manages Firebase Authentication operations including
// login, registration, session management, and logout
// ============================================================

package com.app.notisync_sender.data.repository

import android.util.Log
import com.app.notisync_sender.data.remote.FirestoreDataSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthRepository — Manages all Firebase Authentication operations.
 *
 * What: Provides methods for user login, registration, logout, and
 *       session state observation. Wraps FirebaseAuth SDK calls in
 *       coroutine-friendly suspend functions with proper error handling.
 *
 * Why: The ViewModel should not directly interact with Firebase SDK.
 *      The Repository layer abstracts the data source, making the
 *      ViewModel testable and independent of the auth implementation.
 *      If Firebase Auth were replaced with a custom backend, only
 *      this repository would change — ViewModels stay untouched.
 *
 * State Management: Exposes authState as a StateFlow<AuthState> that
 *      ViewModels observe. UI reacts to state changes (Loading,
 *      Authenticated, Unauthenticated, Error) automatically via
 *      Compose recomposition.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestoreDataSource: FirestoreDataSource
) {

    /**
     * Tag for logcat logging
     */
    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Mutable state flow holding the current authentication state.
     * Private — only this repository can modify the auth state.
     */
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)

    /**
     * Public read-only StateFlow for observing authentication state.
     * ViewModels collect this flow to update UI based on auth status.
     */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Initializer block — checks if user is already logged in when
     * the repository is created (app startup).
     *
     * Why: FirebaseAuth persists login sessions across app restarts.
     *      If the user was previously logged in, currentUser will be
     *      non-null and we immediately set state to Authenticated.
     *      This allows the app to skip the login screen on restart.
     */
    init {
        /* Check if a user session already exists from a previous app launch */
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            /* User is already logged in — set authenticated state */
            _authState.value = AuthState.Authenticated(currentUser)
            Log.d(TAG, "Existing session found: ${currentUser.email}")
        } else {
            /* No existing session — user needs to log in */
            _authState.value = AuthState.Unauthenticated
            Log.d(TAG, "No existing session — user must log in")
        }
    }

    /**
     * Returns the currently logged-in Firebase user, or null if not logged in.
     *
     * What: Convenience accessor for the current FirebaseUser.
     *
     * Why: Used by services and repositories that need the user ID
     *      without subscribing to the full auth state flow.
     *
     * @return FirebaseUser if logged in, null otherwise
     */
    fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }

    /**
     * Returns the current user's UID, or null if not logged in.
     *
     * What: Extracts just the UID string from the current user.
     *
     * Why: Most Firestore operations only need the UID string,
     *      not the full FirebaseUser object. This is a convenience
     *      shortcut used by NotificationRepository and BatchProcessor.
     *
     * @return User UID string if logged in, null otherwise
     */
    fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    /**
     * Logs in a user with email and password.
     *
     * What: Authenticates the user via Firebase Auth using email/password
     *       credentials. Updates authState based on the result.
     *
     * Why: Email/password auth is the simplest Firebase Auth method.
     *      It works offline (cached tokens) and requires no additional
     *      SDKs or OAuth configuration.
     *
     * Flow:
     *   1. Set state to Loading (UI shows progress indicator)
     *   2. Call Firebase signInWithEmailAndPassword()
     *   3. await() suspends until Firebase responds
     *   4. On success → set state to Authenticated
     *   5. On failure → set state to Error with message
     *
     * @param email User's email address
     * @param password User's password
     * @return Result indicating success or failure
     */
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            /* Set loading state — UI shows progress indicator */
            _authState.value = AuthState.Loading

            Log.d(TAG, "Attempting login for: $email")

            /* Authenticate with Firebase — await() converts Task to suspend */
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            /* Extract the authenticated user from the result */
            val user = authResult.user

            if (user != null) {
                /* Login successful — update state to Authenticated */
                _authState.value = AuthState.Authenticated(user)
                Log.d(TAG, "Login successful: ${user.email}")

                /* Return success with the user object */
                Result.success(user)
            } else {
                /* Unexpected null user — should not happen but handle gracefully */
                val error = Exception("Login succeeded but user is null")
                _authState.value = AuthState.Error(error.message ?: "Unknown error")
                Log.e(TAG, "Login returned null user")

                Result.failure(error)
            }
        } catch (e: Exception) {
            /* Login failed — update state to Error */
            _authState.value = AuthState.Error(e.message ?: "Login failed")
            Log.e(TAG, "Login failed: ${e.message}", e)

            /* Return failure with the exception */
            Result.failure(e)
        }
    }

    /**
     * Registers a new user with email and password.
     *
     * What: Creates a new Firebase Auth account and user profile
     *       document in Firestore.
     *
     * Why: New users must create an account before they can send
     *      or receive notifications. The Firestore user profile
     *      is created immediately after registration to initialize
     *      the user's data structure.
     *
     * Flow:
     *   1. Set state to Loading
     *   2. Call Firebase createUserWithEmailAndPassword()
     *   3. On success → create user profile in Firestore
     *   4. Set state to Authenticated
     *   5. On failure → set state to Error
     *
     * @param email User's email address
     * @param password User's password (min 6 characters, enforced by Firebase)
     * @return Result indicating success or failure
     */
    suspend fun register(email: String, password: String): Result<FirebaseUser> {
        return try {
            /* Set loading state */
            _authState.value = AuthState.Loading

            Log.d(TAG, "Attempting registration for: $email")

            /* Create new account with Firebase Auth */
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()

            /* Extract the newly created user */
            val user = authResult.user

            if (user != null) {
                /* Create user profile document in Firestore */
                firestoreDataSource.createUserProfile(
                    userId = user.uid,
                    email = email
                )

                /* Registration successful — update state */
                _authState.value = AuthState.Authenticated(user)
                Log.d(TAG, "Registration successful: ${user.email}")

                Result.success(user)
            } else {
                /* Unexpected null user */
                val error = Exception("Registration succeeded but user is null")
                _authState.value = AuthState.Error(error.message ?: "Unknown error")
                Log.e(TAG, "Registration returned null user")

                Result.failure(error)
            }
        } catch (e: Exception) {
            /* Registration failed — update state */
            _authState.value = AuthState.Error(e.message ?: "Registration failed")
            Log.e(TAG, "Registration failed: ${e.message}", e)

            Result.failure(e)
        }
    }

    /**
     * Logs out the current user.
     *
     * What: Signs out from Firebase Auth and resets auth state.
     *
     * Why: Clears the cached auth token and session data.
     *      After logout, the user must re-enter credentials
     *      to access the system. The auth state change triggers
     *      UI navigation back to the Login screen.
     */
    fun logout() {
        Log.d(TAG, "Logging out user: ${firebaseAuth.currentUser?.email}")

        /* Sign out from Firebase Auth — clears cached token */
        firebaseAuth.signOut()

        /* Update state to Unauthenticated — UI navigates to Login */
        _authState.value = AuthState.Unauthenticated
    }

    /**
     * Clears any error state and resets to Unauthenticated.
     *
     * What: Changes auth state from Error back to Unauthenticated.
     *
     * Why: After displaying an error message to the user, the UI
     *      needs to reset so the user can try again. Without this,
     *      the error state would persist and block further attempts.
     */
    fun clearError() {
        _authState.value = AuthState.Unauthenticated
    }
}

/**
 * AuthState — Sealed class representing all possible authentication states.
 *
 * What: Defines a finite set of states the auth system can be in.
 *
 * Why: Using a sealed class with a when() expression ensures the compiler
 *      verifies that all states are handled in the UI. This prevents
 *      runtime crashes from unhandled states and makes the state machine
 *      explicit and readable.
 *
 * States:
 *   Initial         → App just launched, checking for existing session
 *   Loading         → Auth operation in progress (login/register)
 *   Authenticated   → User is logged in (holds FirebaseUser reference)
 *   Unauthenticated → No user session exists
 *   Error           → Auth operation failed (holds error message)
 */
sealed class AuthState {

    /** App just started — checking for existing session */
    data object Initial : AuthState()

    /** Auth operation in progress — show loading indicator */
    data object Loading : AuthState()

    /** User is logged in — holds the FirebaseUser for accessing UID/email */
    data class Authenticated(val user: FirebaseUser) : AuthState()

    /** No user session — show login screen */
    data object Unauthenticated : AuthState()

    /** Auth operation failed — holds the error message for display */
    data class Error(val message: String) : AuthState()
}