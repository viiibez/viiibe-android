package com.viiibe.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages X (Twitter) OAuth 2.0 authentication with PKCE flow.
 * Handles login, token storage, and profile retrieval.
 */
class XAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "XAuthManager"

        // X OAuth 2.0 endpoints
        private const val X_AUTH_ENDPOINT = "https://twitter.com/i/oauth2/authorize"
        private const val X_TOKEN_ENDPOINT = "https://api.twitter.com/2/oauth2/token"
        private const val X_REVOKE_ENDPOINT = "https://api.twitter.com/2/oauth2/revoke"

        // OAuth configuration - these would typically come from your backend
        // The client ID is public for mobile apps using PKCE
        private const val CLIENT_ID = "YOUR_X_CLIENT_ID" // Replace with your X API client ID
        private const val REDIRECT_URI = "com.viiibe.app://oauth/callback"

        // Scopes for X OAuth 2.0
        private const val SCOPES = "tweet.read users.read offline.access"

        // Encrypted SharedPreferences keys
        private const val PREFS_NAME = "x_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_X_USER_ID = "x_user_id"
        private const val KEY_X_USERNAME = "x_username"
        private const val KEY_X_PROFILE_IMAGE = "x_profile_image"
        private const val KEY_X_NAME = "x_name"

        // Backend API endpoints
        private const val BACKEND_BASE_URL = "https://viiibe-backend-production.up.railway.app"
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Encrypted SharedPreferences for secure token storage
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // AppAuth service configuration
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(X_AUTH_ENDPOINT),
        Uri.parse(X_TOKEN_ENDPOINT)
    )

    private var authService: AuthorizationService? = null
    private var currentCodeVerifier: String? = null

    /**
     * Data class representing the X user profile
     */
    data class XProfile(
        val userId: String,
        val username: String,
        val name: String,
        val profileImageUrl: String?
    )

    /**
     * Data class for token response
     */
    data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Long,
        @SerializedName("token_type") val tokenType: String,
        @SerializedName("scope") val scope: String?
    )

    /**
     * Data class for X user API response
     */
    data class XUserResponse(
        val data: XUserData?
    )

    data class XUserData(
        val id: String,
        val username: String,
        val name: String,
        @SerializedName("profile_image_url") val profileImageUrl: String?
    )

    /**
     * Backend API response for profile linking
     */
    data class LinkWalletRequest(
        val walletAddress: String,
        val xAccessToken: String
    )

    data class LinkWalletResponse(
        val success: Boolean,
        val message: String?,
        val profile: XProfile?
    )

    /**
     * Initialize the auth service
     */
    fun initialize() {
        authService = AuthorizationService(context)
    }

    /**
     * Clean up resources
     */
    fun dispose() {
        authService?.dispose()
        authService = null
    }

    /**
     * Check if user is authenticated with X
     */
    fun isAuthenticated(): Boolean {
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return accessToken != null && System.currentTimeMillis() < expiry
    }

    /**
     * Get the stored X profile
     */
    fun getStoredProfile(): XProfile? {
        val userId = encryptedPrefs.getString(KEY_X_USER_ID, null) ?: return null
        val username = encryptedPrefs.getString(KEY_X_USERNAME, null) ?: return null
        val name = encryptedPrefs.getString(KEY_X_NAME, null) ?: return null
        val profileImage = encryptedPrefs.getString(KEY_X_PROFILE_IMAGE, null)

        return XProfile(userId, username, name, profileImage)
    }

    /**
     * Generate PKCE code verifier (43-128 characters)
     */
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Generate PKCE code challenge from verifier using SHA256
     */
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Build the authorization request intent
     */
    fun buildAuthIntent(): Intent {
        // Generate PKCE code verifier and challenge
        currentCodeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(currentCodeVerifier!!)

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScope(SCOPES)
            .setCodeVerifier(currentCodeVerifier, codeChallenge, "S256")
            .build()

        return authService!!.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handle the authorization response from the callback
     */
    suspend fun handleAuthResponse(intent: Intent): Result<XProfile> = withContext(Dispatchers.IO) {
        try {
            val response = AuthorizationResponse.fromIntent(intent)
            val exception = AuthorizationException.fromIntent(intent)

            if (exception != null) {
                Log.e(TAG, "Authorization failed: ${exception.errorDescription}")
                return@withContext Result.failure(Exception(exception.errorDescription ?: "Authorization failed"))
            }

            if (response == null) {
                return@withContext Result.failure(Exception("No authorization response"))
            }

            // Exchange authorization code for tokens
            val tokenResponse = exchangeCodeForTokens(response.authorizationCode!!)

            // Store tokens securely
            storeTokens(tokenResponse)

            // Fetch user profile
            val profile = fetchXProfile(tokenResponse.accessToken)

            // Store profile
            storeProfile(profile)

            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling auth response", e)
            Result.failure(e)
        }
    }

    /**
     * Exchange authorization code for access and refresh tokens
     */
    private suspend fun exchangeCodeForTokens(authCode: String): TokenResponse = withContext(Dispatchers.IO) {
        val tokenRequest = TokenRequest.Builder(
            serviceConfig,
            CLIENT_ID
        )
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setAuthorizationCode(authCode)
            .setRedirectUri(Uri.parse(REDIRECT_URI))
            .setCodeVerifier(currentCodeVerifier)
            .build()

        suspendCancellableCoroutine { continuation ->
            authService!!.performTokenRequest(tokenRequest) { response, exception ->
                if (exception != null) {
                    continuation.resumeWithException(
                        Exception(exception.errorDescription ?: "Token exchange failed")
                    )
                    return@performTokenRequest
                }

                if (response == null) {
                    continuation.resumeWithException(Exception("No token response"))
                    return@performTokenRequest
                }

                val tokenResponse = TokenResponse(
                    accessToken = response.accessToken!!,
                    refreshToken = response.refreshToken,
                    expiresIn = response.accessTokenExpirationTime?.let {
                        (it - System.currentTimeMillis()) / 1000
                    } ?: 7200,
                    tokenType = response.tokenType ?: "Bearer",
                    scope = response.scope
                )

                continuation.resume(tokenResponse)
            }
        }
    }

    /**
     * Fetch X user profile using access token
     */
    private suspend fun fetchXProfile(accessToken: String): XProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.twitter.com/2/users/me?user.fields=profile_image_url")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Failed to fetch profile: ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("Empty response body")
        val userResponse = gson.fromJson(body, XUserResponse::class.java)

        val userData = userResponse.data ?: throw Exception("No user data in response")

        XProfile(
            userId = userData.id,
            username = userData.username,
            name = userData.name,
            profileImageUrl = userData.profileImageUrl?.replace("_normal", "_bigger")
        )
    }

    /**
     * Store tokens in encrypted preferences
     */
    private fun storeTokens(tokenResponse: TokenResponse) {
        val expiryTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)

        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTime)
            .apply()
    }

    /**
     * Store profile in encrypted preferences
     */
    private fun storeProfile(profile: XProfile) {
        encryptedPrefs.edit()
            .putString(KEY_X_USER_ID, profile.userId)
            .putString(KEY_X_USERNAME, profile.username)
            .putString(KEY_X_NAME, profile.name)
            .putString(KEY_X_PROFILE_IMAGE, profile.profileImageUrl)
            .apply()
    }

    /**
     * Refresh the access token using the refresh token
     */
    suspend fun refreshTokens(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
                ?: return@withContext Result.failure(Exception("No refresh token available"))

            val tokenRequest = TokenRequest.Builder(
                serviceConfig,
                CLIENT_ID
            )
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .build()

            suspendCancellableCoroutine { continuation ->
                authService!!.performTokenRequest(tokenRequest) { response, exception ->
                    if (exception != null) {
                        continuation.resumeWithException(
                            Exception(exception.errorDescription ?: "Token refresh failed")
                        )
                        return@performTokenRequest
                    }

                    if (response == null) {
                        continuation.resumeWithException(Exception("No token response"))
                        return@performTokenRequest
                    }

                    val tokenResponse = TokenResponse(
                        accessToken = response.accessToken!!,
                        refreshToken = response.refreshToken ?: refreshToken,
                        expiresIn = response.accessTokenExpirationTime?.let {
                            (it - System.currentTimeMillis()) / 1000
                        } ?: 7200,
                        tokenType = response.tokenType ?: "Bearer",
                        scope = response.scope
                    )

                    storeTokens(tokenResponse)
                    continuation.resume(Unit)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing tokens", e)
            Result.failure(e)
        }
    }

    /**
     * Get the current access token, refreshing if necessary
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)

        // If token is expired or about to expire (within 5 minutes), refresh it
        if (System.currentTimeMillis() > expiry - (5 * 60 * 1000)) {
            val result = refreshTokens()
            if (result.isFailure) {
                return@withContext null
            }
        }

        encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Link X account to a wallet address via backend API
     */
    suspend fun linkWalletToXAccount(walletAddress: String): Result<XProfile> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated with X"))

            val requestBody = gson.toJson(LinkWalletRequest(walletAddress, accessToken))

            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/auth/link-wallet")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to link wallet: ${response.code} - $body")
                )
            }

            val linkResponse = gson.fromJson(body, LinkWalletResponse::class.java)

            if (!linkResponse.success) {
                return@withContext Result.failure(
                    Exception(linkResponse.message ?: "Failed to link wallet")
                )
            }

            linkResponse.profile?.let { storeProfile(it) }

            Result.success(linkResponse.profile ?: getStoredProfile()!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error linking wallet", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch linked profile from backend for a wallet address
     */
    suspend fun fetchLinkedProfile(walletAddress: String): Result<XProfile?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/auth/profile/$walletAddress")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.code == 404) {
                return@withContext Result.success(null)
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to fetch profile: ${response.code}")
                )
            }

            val body = response.body?.string()
            val profile = gson.fromJson(body, XProfile::class.java)

            profile?.let { storeProfile(it) }

            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching linked profile", e)
            Result.failure(e)
        }
    }

    /**
     * Revoke tokens and clear stored data (logout/unlink)
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

            // Attempt to revoke token at X API
            if (accessToken != null) {
                try {
                    val request = Request.Builder()
                        .url(X_REVOKE_ENDPOINT)
                        .post("token=$accessToken&token_type_hint=access_token"
                            .toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                    httpClient.newCall(request).execute()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to revoke token at X API", e)
                }
            }

            // Clear stored data
            encryptedPrefs.edit().clear().apply()
            currentCodeVerifier = null

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout", e)
            Result.failure(e)
        }
    }

    /**
     * Unlink X account from wallet via backend
     */
    suspend fun unlinkWallet(walletAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()

            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/auth/unlink-wallet")
                .post(gson.toJson(mapOf(
                    "walletAddress" to walletAddress,
                    "xAccessToken" to accessToken
                )).toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to unlink wallet: ${response.code}")
                )
            }

            // Clear local stored data
            logout()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error unlinking wallet", e)
            Result.failure(e)
        }
    }
}
