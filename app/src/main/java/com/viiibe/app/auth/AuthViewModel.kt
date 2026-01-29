package com.viiibe.app.auth

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing X (Twitter) authentication state.
 * Handles login, logout, and profile retrieval.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "auth_refresh_prefs"
        private const val KEY_LAST_PROFILE_REFRESH = "last_profile_picture_refresh"
        private const val PROFILE_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val xAuthManager = XAuthManager(application)

    // Encrypted preferences for storing refresh timestamps
    private val refreshPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            application,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * UI State for X authentication
     */
    data class XAuthState(
        val isAuthenticated: Boolean = false,
        val isLoading: Boolean = false,
        val profile: XAuthManager.XProfile? = null,
        val error: String? = null,
        val profilePictureUrl: String? = null
    )

    private val _authState = MutableStateFlow(XAuthState())
    val authState: StateFlow<XAuthState> = _authState.asStateFlow()

    // Track if profile picture needs refresh
    private val _needsProfilePictureRefresh = MutableStateFlow(false)
    val needsProfilePictureRefresh: StateFlow<Boolean> = _needsProfilePictureRefresh.asStateFlow()

    init {
        // Initialize the auth manager and check existing auth state
        xAuthManager.initialize()
        checkAuthState()
        checkProfilePictureRefreshNeeded()
    }

    /**
     * Check if profile picture refresh is needed (older than 24 hours)
     */
    private fun checkProfilePictureRefreshNeeded() {
        val lastRefresh = refreshPrefs.getLong(KEY_LAST_PROFILE_REFRESH, 0L)
        val timeSinceRefresh = System.currentTimeMillis() - lastRefresh
        _needsProfilePictureRefresh.value = timeSinceRefresh > PROFILE_REFRESH_INTERVAL_MS
    }

    /**
     * Check if profile picture should be refreshed on app launch
     */
    fun shouldRefreshProfilePicture(): Boolean {
        val lastRefresh = refreshPrefs.getLong(KEY_LAST_PROFILE_REFRESH, 0L)
        return System.currentTimeMillis() - lastRefresh > PROFILE_REFRESH_INTERVAL_MS
    }

    /**
     * Mark profile picture as refreshed
     */
    fun markProfilePictureRefreshed() {
        refreshPrefs.edit()
            .putLong(KEY_LAST_PROFILE_REFRESH, System.currentTimeMillis())
            .apply()
        _needsProfilePictureRefresh.value = false
    }

    /**
     * Refresh profile picture - call on app launch if needed and after X re-authentication
     */
    fun refreshProfilePicture() {
        if (!_authState.value.isAuthenticated) return

        viewModelScope.launch {
            try {
                // Use the access token to fetch fresh profile data
                val accessToken = xAuthManager.getAccessToken()
                if (accessToken != null) {
                    // The XAuthManager's fetchXProfile will get fresh data
                    // For now, we'll just refresh using the existing tokens
                    val result = xAuthManager.refreshTokens()
                    if (result.isSuccess) {
                        // Re-fetch profile with new token
                        val profile = xAuthManager.getStoredProfile()
                        if (profile != null) {
                            _authState.value = _authState.value.copy(
                                profile = profile,
                                profilePictureUrl = profile.profileImageUrl
                            )
                        }
                        markProfilePictureRefreshed()
                    }
                }
            } catch (e: Exception) {
                // Silent failure - profile picture refresh is non-critical
            }
        }
    }

    /**
     * Perform app launch sync - refresh profile picture if needed
     */
    fun performAppLaunchSync() {
        if (_authState.value.isAuthenticated && shouldRefreshProfilePicture()) {
            refreshProfilePicture()
        }
    }

    /**
     * Check if user is already authenticated and load stored profile
     */
    private fun checkAuthState() {
        val isAuth = xAuthManager.isAuthenticated()
        val profile = xAuthManager.getStoredProfile()
        _authState.value = XAuthState(
            isAuthenticated = isAuth,
            profile = profile,
            profilePictureUrl = profile?.profileImageUrl
        )
    }

    /**
     * Build the authorization intent for X OAuth
     * Call this to get the intent to launch for authentication
     */
    fun buildAuthIntent(): Intent {
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        return xAuthManager.buildAuthIntent()
    }

    /**
     * Handle the authorization response from the OAuth callback
     * Call this from the Activity's onActivityResult or ActivityResultLauncher
     */
    fun handleAuthResponse(intent: Intent) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val result = xAuthManager.handleAuthResponse(intent)

            result.fold(
                onSuccess = { profile ->
                    _authState.value = XAuthState(
                        isAuthenticated = true,
                        isLoading = false,
                        profile = profile,
                        error = null,
                        profilePictureUrl = profile.profileImageUrl
                    )
                    // Mark profile picture as refreshed after successful auth
                    markProfilePictureRefreshed()
                },
                onFailure = { exception ->
                    _authState.value = XAuthState(
                        isAuthenticated = false,
                        isLoading = false,
                        profile = null,
                        error = exception.message ?: "Authentication failed"
                    )
                }
            )
        }
    }

    /**
     * Link the X account to a wallet address
     */
    fun linkWallet(walletAddress: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val result = xAuthManager.linkWalletToXAccount(walletAddress)

            result.fold(
                onSuccess = { profile ->
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        profile = profile,
                        error = null
                    )
                },
                onFailure = { exception ->
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to link wallet"
                    )
                }
            )
        }
    }

    /**
     * Fetch linked profile for a wallet address
     */
    fun fetchLinkedProfile(walletAddress: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val result = xAuthManager.fetchLinkedProfile(walletAddress)

            result.fold(
                onSuccess = { profile ->
                    _authState.value = _authState.value.copy(
                        isAuthenticated = profile != null,
                        isLoading = false,
                        profile = profile,
                        error = null
                    )
                },
                onFailure = { exception ->
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to fetch profile"
                    )
                }
            )
        }
    }

    /**
     * Refresh the access token
     */
    fun refreshToken() {
        viewModelScope.launch {
            val result = xAuthManager.refreshTokens()
            if (result.isFailure) {
                // Token refresh failed, user needs to re-authenticate
                _authState.value = XAuthState(
                    isAuthenticated = false,
                    isLoading = false,
                    profile = null,
                    error = "Session expired. Please link your X account again."
                )
            }
        }
    }

    /**
     * Logout from X and clear stored data
     */
    fun logout() {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true)

            xAuthManager.logout()

            _authState.value = XAuthState(
                isAuthenticated = false,
                isLoading = false,
                profile = null,
                error = null
            )
        }
    }

    /**
     * Unlink wallet from X account
     */
    fun unlinkWallet(walletAddress: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            val result = xAuthManager.unlinkWallet(walletAddress)

            result.fold(
                onSuccess = {
                    _authState.value = XAuthState(
                        isAuthenticated = false,
                        isLoading = false,
                        profile = null,
                        error = null
                    )
                },
                onFailure = { exception ->
                    _authState.value = _authState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to unlink wallet"
                    )
                }
            )
        }
    }

    /**
     * Clear any displayed error
     */
    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

    /**
     * Clear loading state (useful if auth flow was cancelled)
     */
    fun cancelAuthFlow() {
        _authState.value = _authState.value.copy(isLoading = false)
    }

    override fun onCleared() {
        super.onCleared()
        xAuthManager.dispose()
    }
}
