package com.viiibe.app.version

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.viiibe.app.BuildConfig
import com.viiibe.app.util.AppVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ViewModel for managing app version state and update checks.
 *
 * This ViewModel:
 * - Checks the backend /api/version endpoint for version requirements
 * - Stores version check results app-wide
 * - Provides helper methods to determine if features are available
 * - Provides download URL for updates
 */
class VersionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VersionVM"
        private val BACKEND_BASE_URL = BuildConfig.BACKEND_BASE_URL
        private const val GITHUB_RELEASES_URL = "https://github.com/viiibe-app/viiibe-android/releases"
        private const val PREFS_NAME = "version_cache"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_MIN_MULTIPLAYER = "min_version_multiplayer"
        private const val KEY_MIN_STATS = "min_version_stats"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_RELEASE_NOTES = "release_notes"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val CACHE_TTL_MS = 60 * 60 * 1000L  // 1 hour cache TTL
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Current app version from BuildConfig
    val currentVersion: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    // Version check state
    private val _versionState = MutableStateFlow(VersionState())
    val versionState: StateFlow<VersionState> = _versionState.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Load cached version state first for immediate availability
        loadCachedVersionState()
        // Then check for fresh version info
        checkVersion()
    }

    /**
     * Load cached version state from SharedPreferences.
     * Provides immediate version info while fresh check is in progress.
     */
    private fun loadCachedVersionState() {
        val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
        val cachedLatestVersion = prefs.getString(KEY_LATEST_VERSION, null)

        if (cachedLatestVersion != null) {
            val isCacheValid = System.currentTimeMillis() - lastCheckTime < CACHE_TTL_MS
            val cachedResponse = VersionResponse(
                latestVersion = cachedLatestVersion,
                minVersionMultiplayer = prefs.getString(KEY_MIN_MULTIPLAYER, "") ?: "",
                minVersionStats = prefs.getString(KEY_MIN_STATS, "") ?: "",
                downloadUrl = prefs.getString(KEY_DOWNLOAD_URL, null),
                releaseNotes = prefs.getString(KEY_RELEASE_NOTES, null)
            )
            val state = calculateVersionState(cachedResponse).copy(
                lastCheckFailed = !isCacheValid,  // Mark as failed if cache is stale
                lastCheckTimeMs = lastCheckTime
            )
            _versionState.value = state
            Log.d(TAG, "Loaded cached version state (valid: $isCacheValid): $state")
        }
    }

    /**
     * Save version state to SharedPreferences cache.
     */
    private fun cacheVersionState(response: VersionResponse) {
        prefs.edit()
            .putString(KEY_LATEST_VERSION, response.latestVersion)
            .putString(KEY_MIN_MULTIPLAYER, response.minVersionMultiplayer)
            .putString(KEY_MIN_STATS, response.minVersionStats)
            .putString(KEY_DOWNLOAD_URL, response.downloadUrl)
            .putString(KEY_RELEASE_NOTES, response.releaseNotes)
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * Check the backend for version requirements.
     * Call this on startup or when connecting to the backend.
     */
    fun checkVersion() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = fetchVersionInfo()
                if (response != null) {
                    // Cache the response for offline use
                    cacheVersionState(response)
                    val state = calculateVersionState(response).copy(
                        lastCheckTimeMs = System.currentTimeMillis()
                    )
                    _versionState.value = state
                    Log.d(TAG, "Version check complete: $state")
                } else {
                    // If we can't reach the server, assume current version is fine
                    // but mark that we couldn't verify
                    _versionState.value = _versionState.value.copy(
                        lastCheckFailed = true
                    )
                    Log.w(TAG, "Version check failed - server unreachable")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Version check error", e)
                _error.value = "Failed to check for updates: ${e.message}"
                _versionState.value = _versionState.value.copy(
                    lastCheckFailed = true
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fetch version info from the backend
     */
    private suspend fun fetchVersionInfo(): VersionResponse? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/api/version")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    return@withContext gson.fromJson(body, VersionResponse::class.java)
                }
            } else {
                Log.w(TAG, "Version check failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching version info", e)
        }
        null
    }

    /**
     * Calculate version state by comparing current version with requirements.
     * Uses AppVersionInfo.compareVersions() for robust semantic version comparison
     * that handles versions like 1.9.0 vs 1.10.0 correctly.
     */
    private fun calculateVersionState(response: VersionResponse): VersionState {
        // Use AppVersionInfo for robust semantic version comparison
        // This correctly handles cases like 1.9.0 < 1.10.0
        val updateAvailable = AppVersionInfo.compareVersions(
            response.latestVersion,
            currentVersion
        ) > 0

        val multiplayerAllowed = AppVersionInfo.compareVersions(
            currentVersion,
            response.minVersionMultiplayer
        ) >= 0

        val statsSyncAllowed = AppVersionInfo.compareVersions(
            currentVersion,
            response.minVersionStats
        ) >= 0

        return VersionState(
            latestVersion = response.latestVersion,
            minVersionMultiplayer = response.minVersionMultiplayer,
            minVersionStats = response.minVersionStats,
            downloadUrl = response.downloadUrl ?: GITHUB_RELEASES_URL,
            releaseNotes = response.releaseNotes,
            updateAvailable = updateAvailable,
            multiplayerAllowed = multiplayerAllowed,
            statsSyncAllowed = statsSyncAllowed,
            lastCheckFailed = false
        )
    }

    /**
     * Open the download URL in browser
     */
    fun openDownloadUrl() {
        val url = _versionState.value.downloadUrl
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download URL", e)
            _error.value = "Failed to open download page"
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Check if multiplayer is allowed for current version
     */
    fun isMultiplayerAllowed(): Boolean {
        return _versionState.value.multiplayerAllowed
    }

    /**
     * Check if stats sync is allowed for current version
     */
    fun isStatsSyncAllowed(): Boolean {
        return _versionState.value.statsSyncAllowed
    }

    /**
     * Check if an update is available
     */
    fun isUpdateAvailable(): Boolean {
        return _versionState.value.updateAvailable
    }
}

/**
 * Response from /api/version endpoint
 */
data class VersionResponse(
    @SerializedName("latest_version")
    val latestVersion: String,

    @SerializedName("min_version_multiplayer")
    val minVersionMultiplayer: String,

    @SerializedName("min_version_stats")
    val minVersionStats: String,

    @SerializedName("download_url")
    val downloadUrl: String?,

    @SerializedName("release_notes")
    val releaseNotes: String?
)

/**
 * Version state for the app
 */
data class VersionState(
    val latestVersion: String = "",
    val minVersionMultiplayer: String = "",
    val minVersionStats: String = "",
    val downloadUrl: String = "https://github.com/viiibe-app/viiibe-android/releases",
    val releaseNotes: String? = null,
    val updateAvailable: Boolean = false,
    val multiplayerAllowed: Boolean = true,  // Default to true for graceful degradation
    val statsSyncAllowed: Boolean = true,     // Default to true for graceful degradation
    val lastCheckFailed: Boolean = false,
    val lastCheckTimeMs: Long = 0  // Timestamp of last successful check (0 = never checked)
) {
    /**
     * Returns true if version info was successfully checked (from network or valid cache)
     */
    val hasValidVersionInfo: Boolean
        get() = latestVersion.isNotEmpty() && !lastCheckFailed

    /**
     * Returns a human-readable string describing how long ago the version was checked.
     * Returns null if never checked.
     */
    fun getLastCheckAgo(): String? {
        if (lastCheckTimeMs == 0L) return null
        val elapsedMs = System.currentTimeMillis() - lastCheckTimeMs
        val minutes = elapsedMs / (60 * 1000)
        val hours = elapsedMs / (60 * 60 * 1000)
        val days = elapsedMs / (24 * 60 * 60 * 1000)

        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            else -> "Just now"
        }
    }
}
