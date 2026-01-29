package com.viiibe.app.blockchain

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.viiibe.app.blockchain.contracts.ContractAddresses
import com.viiibe.app.blockchain.contracts.ViiibeTokenContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import okhttp3.OkHttpClient
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BlockchainService(private val context: Context) {

    companion object {
        private const val TAG = "BlockchainService"
        private const val PREFS_NAME = "viiibe_wallet_prefs"
        private const val KEY_WALLET_ADDRESS = "wallet_address"
        private const val KEY_ENCRYPTED_PRIVATE_KEY = "encrypted_private_key"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_LAST_UNLOCK_TIME = "last_unlock_time"
        private const val AUTO_LOCK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        // Note: BouncyCastle is set up in ViiibeApplication.onCreate()
    }

    private var web3j: Web3j? = null
    private var currentNetwork: BlockchainNetwork = BlockchainNetwork.AVALANCHE_MAINNET
    private var credentials: Credentials? = null

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

    private val httpClient by lazy {
        try {
            // Create a trust manager that trusts all certificates
            // This is needed because the Peloton device has SSL configuration issues
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SSL OkHttpClient, using basic client", e)
            // Fallback to basic client without SSL customization
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Initialize Web3j connection for the specified network
     */
    fun initializeNetwork(network: BlockchainNetwork) {
        currentNetwork = network
        web3j?.shutdown()
        web3j = Web3j.build(HttpService(network.rpcUrl, httpClient))
        Log.d(TAG, "Initialized Web3j for network: ${network.displayName}")
    }

    /**
     * Generate a new wallet
     */
    suspend fun generateWallet(): Result<WalletGenerationResult> = withContext(Dispatchers.IO) {
        try {
            val ecKeyPair = Keys.createEcKeyPair()
            val walletAddress = "0x" + Keys.getAddress(ecKeyPair)
            val privateKey = Numeric.toHexStringWithPrefix(ecKeyPair.privateKey)

            credentials = Credentials.create(ecKeyPair)

            // Store encrypted private key
            encryptedPrefs.edit()
                .putString(KEY_WALLET_ADDRESS, walletAddress)
                .putString(KEY_ENCRYPTED_PRIVATE_KEY, privateKey)
                .apply()

            Log.d(TAG, "Generated new wallet: $walletAddress")

            Result.success(
                WalletGenerationResult(
                    address = walletAddress,
                    privateKey = privateKey
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate wallet", e)
            Result.failure(e)
        }
    }

    /**
     * Import wallet from private key
     */
    suspend fun importWallet(privateKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = if (privateKey.startsWith("0x")) {
                privateKey.substring(2)
            } else {
                privateKey
            }

            val ecKeyPair = ECKeyPair.create(BigInteger(cleanKey, 16))
            credentials = Credentials.create(ecKeyPair)
            val walletAddress = credentials!!.address

            // Store encrypted
            encryptedPrefs.edit()
                .putString(KEY_WALLET_ADDRESS, walletAddress)
                .putString(KEY_ENCRYPTED_PRIVATE_KEY, "0x$cleanKey")
                .apply()

            Log.d(TAG, "Imported wallet: $walletAddress")
            Result.success(walletAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import wallet", e)
            Result.failure(e)
        }
    }

    /**
     * Load existing wallet from encrypted storage
     */
    suspend fun loadStoredWallet(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val address = encryptedPrefs.getString(KEY_WALLET_ADDRESS, null)
            val privateKey = encryptedPrefs.getString(KEY_ENCRYPTED_PRIVATE_KEY, null)

            if (address != null && privateKey != null) {
                val cleanKey = if (privateKey.startsWith("0x")) {
                    privateKey.substring(2)
                } else {
                    privateKey
                }
                val ecKeyPair = ECKeyPair.create(BigInteger(cleanKey, 16))
                credentials = Credentials.create(ecKeyPair)
                Log.d(TAG, "Loaded stored wallet: $address")
                Result.success(address)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load stored wallet", e)
            Result.failure(e)
        }
    }

    /**
     * Get wallet balance with fallback RPC support
     */
    suspend fun getBalance(address: String): Result<String> = withContext(Dispatchers.IO) {
        // Try primary RPC first
        val allRpcUrls = listOf(currentNetwork.rpcUrl) + currentNetwork.fallbackRpcUrls
        var lastException: Exception? = null

        for (rpcUrl in allRpcUrls) {
            try {
                val web3 = Web3j.build(HttpService(rpcUrl, httpClient))
                val balance = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()

                if (balance.hasError()) {
                    throw Exception(balance.error.message)
                }

                val balanceInEther = Convert.fromWei(
                    BigDecimal(balance.balance),
                    Convert.Unit.ETHER
                )

                val formattedBalance = "%.4f".format(balanceInEther)
                Log.d(TAG, "Balance for $address: $formattedBalance ${currentNetwork.currencySymbol} (via $rpcUrl)")
                return@withContext Result.success(formattedBalance)
            } catch (e: Exception) {
                Log.w(TAG, "RPC $rpcUrl failed: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All RPCs failed to get balance", lastException)
        Result.failure(lastException ?: Exception("Failed to get balance from all RPCs"))
    }

    /**
     * Hash workout data for on-chain verification
     */
    fun hashWorkoutData(
        userId: Long,
        workoutId: Long,
        startTime: Long,
        durationSeconds: Int,
        totalOutput: Int,
        totalDistance: Float,
        caloriesBurned: Int
    ): String {
        val data = "$userId|$workoutId|$startTime|$durationSeconds|$totalOutput|$totalDistance|$caloriesBurned"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return Numeric.toHexString(hashBytes)
    }

    /**
     * Sign a message with the wallet
     */
    suspend fun signMessage(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val creds = credentials ?: throw IllegalStateException("Wallet not loaded")
            val messageHash = Hash.sha3(message.toByteArray())
            val signature = Sign.signPrefixedMessage(message.toByteArray(), creds.ecKeyPair)

            val signatureBytes = ByteArray(65)
            System.arraycopy(signature.r, 0, signatureBytes, 0, 32)
            System.arraycopy(signature.s, 0, signatureBytes, 32, 32)
            signatureBytes[64] = signature.v[0]

            val signatureHex = Numeric.toHexString(signatureBytes)
            Log.d(TAG, "Signed message: ${message.take(50)}...")
            Result.success(signatureHex)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign message", e)
            Result.failure(e)
        }
    }

    /**
     * Send a transaction to another address
     */
    suspend fun sendTransaction(toAddress: String, amountEther: String): Result<String> = withContext(Dispatchers.IO) {
        val allRpcUrls = listOf(currentNetwork.rpcUrl) + currentNetwork.fallbackRpcUrls
        var lastException: Exception? = null

        for (rpcUrl in allRpcUrls) {
            try {
                val creds = credentials ?: throw IllegalStateException("Wallet not loaded")
                val web3 = Web3j.build(HttpService(rpcUrl, httpClient))

                // Get nonce
                val nonce = web3.ethGetTransactionCount(
                    creds.address,
                    DefaultBlockParameterName.LATEST
                ).send().transactionCount

                // Get gas price
                val gasPrice = web3.ethGasPrice().send().gasPrice

                // Convert amount to wei
                val amountWei = Convert.toWei(amountEther, Convert.Unit.ETHER).toBigInteger()

                // Standard gas limit for ETH transfer
                val gasLimit = BigInteger.valueOf(21000)

                // Create raw transaction
                val rawTransaction = RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    toAddress,
                    amountWei
                )

                // Sign transaction
                val signedMessage = TransactionEncoder.signMessage(
                    rawTransaction,
                    currentNetwork.chainId,
                    creds
                )
                val hexValue = Numeric.toHexString(signedMessage)

                // Send transaction
                val transactionResponse = web3.ethSendRawTransaction(hexValue).send()

                if (transactionResponse.hasError()) {
                    throw Exception(transactionResponse.error.message)
                }

                val txHash = transactionResponse.transactionHash
                Log.d(TAG, "Transaction sent: $txHash (via $rpcUrl)")
                return@withContext Result.success(txHash)
            } catch (e: Exception) {
                Log.w(TAG, "RPC $rpcUrl failed for send: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All RPCs failed to send transaction", lastException)
        Result.failure(lastException ?: Exception("Failed to send transaction"))
    }

    // ==================== $VIIIBE Token Methods ====================

    private var tokenContract: ViiibeTokenContract? = null

    /**
     * Initialize the $VIIIBE token contract
     */
    private fun getTokenContract(): ViiibeTokenContract {
        if (tokenContract == null) {
            val web3 = web3j ?: throw IllegalStateException("Web3j not initialized")
            val isTestnet = currentNetwork != BlockchainNetwork.AVALANCHE_MAINNET
            tokenContract = ViiibeTokenContract(
                web3,
                ContractAddresses.getTokenAddress(isTestnet)
            )
        }
        return tokenContract!!
    }

    /**
     * Get $VIIIBE token balance for an address
     */
    suspend fun getViiibeBalance(address: String): Result<Double> = withContext(Dispatchers.IO) {
        val allRpcUrls = listOf(currentNetwork.rpcUrl) + currentNetwork.fallbackRpcUrls
        var lastException: Exception? = null

        for (rpcUrl in allRpcUrls) {
            try {
                val web3 = Web3j.build(HttpService(rpcUrl, httpClient))
                val isTestnet = currentNetwork != BlockchainNetwork.AVALANCHE_MAINNET
                val contract = ViiibeTokenContract(
                    web3,
                    ContractAddresses.getTokenAddress(isTestnet)
                )
                val balance = contract.getBalance(address)
                val formattedBalance = ViiibeTokenContract.fromTokenUnits(balance)
                Log.d(TAG, "VIIIBE balance for $address: $formattedBalance (via $rpcUrl)")
                return@withContext Result.success(formattedBalance)
            } catch (e: Exception) {
                Log.w(TAG, "RPC $rpcUrl failed for VIIIBE balance: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All RPCs failed to get VIIIBE balance", lastException)
        Result.failure(lastException ?: Exception("Failed to get VIIIBE balance"))
    }

    /**
     * Transfer $VIIIBE tokens to another address
     */
    suspend fun transferViiibe(toAddress: String, amount: Double): Result<String> = withContext(Dispatchers.IO) {
        val allRpcUrls = listOf(currentNetwork.rpcUrl) + currentNetwork.fallbackRpcUrls
        var lastException: Exception? = null

        for (rpcUrl in allRpcUrls) {
            try {
                val creds = credentials ?: throw IllegalStateException("Wallet not loaded")
                val web3 = Web3j.build(HttpService(rpcUrl, httpClient))
                val isTestnet = currentNetwork != BlockchainNetwork.AVALANCHE_MAINNET
                val contract = ViiibeTokenContract(
                    web3,
                    ContractAddresses.getTokenAddress(isTestnet)
                )

                val amountInUnits = ViiibeTokenContract.toTokenUnits(amount)
                val txHash = contract.transfer(creds, toAddress, amountInUnits)

                Log.d(TAG, "VIIIBE transfer sent: $txHash (via $rpcUrl)")
                return@withContext Result.success(txHash)
            } catch (e: Exception) {
                Log.w(TAG, "RPC $rpcUrl failed for VIIIBE transfer: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All RPCs failed to send VIIIBE transfer", lastException)
        Result.failure(lastException ?: Exception("Failed to send VIIIBE transfer"))
    }

    /**
     * Approve $VIIIBE tokens for spending by a contract (e.g., ViiibeCore for wagering)
     */
    suspend fun approveViiibe(spenderAddress: String, amount: Double): Result<String> = withContext(Dispatchers.IO) {
        val allRpcUrls = listOf(currentNetwork.rpcUrl) + currentNetwork.fallbackRpcUrls
        var lastException: Exception? = null

        for (rpcUrl in allRpcUrls) {
            try {
                val creds = credentials ?: throw IllegalStateException("Wallet not loaded")
                val web3 = Web3j.build(HttpService(rpcUrl, httpClient))
                val isTestnet = currentNetwork != BlockchainNetwork.AVALANCHE_MAINNET
                val contract = ViiibeTokenContract(
                    web3,
                    ContractAddresses.getTokenAddress(isTestnet)
                )

                val amountInUnits = ViiibeTokenContract.toTokenUnits(amount)
                val txHash = contract.approve(creds, spenderAddress, amountInUnits)

                Log.d(TAG, "VIIIBE approval sent: $txHash (via $rpcUrl)")
                return@withContext Result.success(txHash)
            } catch (e: Exception) {
                Log.w(TAG, "RPC $rpcUrl failed for VIIIBE approval: ${e.message}")
                lastException = e
            }
        }

        Log.e(TAG, "All RPCs failed to send VIIIBE approval", lastException)
        Result.failure(lastException ?: Exception("Failed to approve VIIIBE"))
    }

    /**
     * Check $VIIIBE allowance for a spender
     */
    suspend fun getViiibeAllowance(ownerAddress: String, spenderAddress: String): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val web3 = web3j ?: throw IllegalStateException("Web3j not initialized")
            val isTestnet = currentNetwork != BlockchainNetwork.AVALANCHE_MAINNET
            val contract = ViiibeTokenContract(
                web3,
                ContractAddresses.getTokenAddress(isTestnet)
            )
            val allowance = contract.getAllowance(ownerAddress, spenderAddress)
            val formattedAllowance = ViiibeTokenContract.fromTokenUnits(allowance)
            Result.success(formattedAllowance)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get VIIIBE allowance", e)
            Result.failure(e)
        }
    }

    /**
     * Clear stored wallet data
     */
    fun clearWallet() {
        encryptedPrefs.edit().clear().apply()
        credentials = null
        Log.d(TAG, "Cleared wallet data")
    }

    /**
     * Get current wallet address
     */
    fun getWalletAddress(): String? = credentials?.address

    /**
     * Get private key for export (use with extreme caution)
     */
    fun getPrivateKey(): String? {
        return encryptedPrefs.getString(KEY_ENCRYPTED_PRIVATE_KEY, null)
    }

    /**
     * Check if wallet is connected
     */
    fun isWalletConnected(): Boolean = credentials != null

    /**
     * Get current network
     */
    fun getCurrentNetwork(): BlockchainNetwork = currentNetwork

    /**
     * Get explorer URL for a transaction
     */
    fun getTransactionExplorerUrl(txHash: String): String {
        return "${currentNetwork.explorerUrl}/tx/$txHash"
    }

    /**
     * Get explorer URL for an address
     */
    fun getAddressExplorerUrl(address: String): String {
        return "${currentNetwork.explorerUrl}/address/$address"
    }

    /**
     * Shutdown web3j connection
     */
    fun shutdown() {
        web3j?.shutdown()
        web3j = null
    }

    // ==================== PIN Security Methods ====================

    /**
     * Check if PIN is set up
     */
    fun isPinSetup(): Boolean {
        return encryptedPrefs.getString(KEY_PIN_HASH, null) != null
    }

    /**
     * Set up a new PIN
     */
    fun setupPin(pin: String): Boolean {
        return try {
            val salt = generateSalt()
            val hash = hashPin(pin, salt)
            encryptedPrefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, salt)
                .putLong(KEY_LAST_UNLOCK_TIME, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "PIN set up successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up PIN", e)
            false
        }
    }

    /**
     * Verify PIN
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = encryptedPrefs.getString(KEY_PIN_SALT, null) ?: return false
        val inputHash = hashPin(pin, salt)
        val isValid = storedHash == inputHash
        if (isValid) {
            // Update last unlock time
            encryptedPrefs.edit()
                .putLong(KEY_LAST_UNLOCK_TIME, System.currentTimeMillis())
                .apply()
        }
        return isValid
    }

    /**
     * Change PIN (requires old PIN verification)
     */
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        return setupPin(newPin)
    }

    /**
     * Check if wallet should be locked due to timeout
     */
    fun isLockedByTimeout(): Boolean {
        if (!isPinSetup()) return false
        val lastUnlock = encryptedPrefs.getLong(KEY_LAST_UNLOCK_TIME, 0L)
        return System.currentTimeMillis() - lastUnlock > AUTO_LOCK_TIMEOUT_MS
    }

    /**
     * Update last unlock time (call when user interacts with wallet)
     */
    fun updateUnlockTime() {
        if (isPinSetup()) {
            encryptedPrefs.edit()
                .putLong(KEY_LAST_UNLOCK_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Remove PIN (requires PIN verification)
     */
    fun removePin(pin: String): Boolean {
        if (!verifyPin(pin)) return false
        encryptedPrefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_LAST_UNLOCK_TIME)
            .apply()
        Log.d(TAG, "PIN removed")
        return true
    }

    /**
     * Generate a random salt for PIN hashing
     */
    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return Numeric.toHexString(bytes)
    }

    /**
     * Hash PIN with salt using SHA-256
     */
    private fun hashPin(pin: String, salt: String): String {
        val data = "$pin$salt"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return Numeric.toHexString(hashBytes)
    }

    /**
     * Get auto-lock timeout in milliseconds
     */
    fun getAutoLockTimeout(): Long = AUTO_LOCK_TIMEOUT_MS
}

data class WalletGenerationResult(
    val address: String,
    val privateKey: String
)
