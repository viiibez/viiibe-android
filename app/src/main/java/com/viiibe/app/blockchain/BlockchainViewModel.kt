package com.viiibe.app.blockchain

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viiibe.app.data.database.ViiibeDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BlockchainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BlockchainViewModel"
    }

    private val blockchainService = BlockchainService(application)
    private val database = ViiibeDatabase.getDatabase(application)
    private val blockchainDao = database.blockchainDao()
    private val workoutDao = database.workoutDao()

    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()

    private val _onChainStats = MutableStateFlow(OnChainStats())
    val onChainStats: StateFlow<OnChainStats> = _onChainStats.asStateFlow()

    private val _currentUserId = MutableStateFlow(0L)

    // PIN/Lock state
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isPinSetup = MutableStateFlow(false)
    val isPinSetup: StateFlow<Boolean> = _isPinSetup.asStateFlow()

    init {
        // Check PIN status (doesn't require network)
        _isPinSetup.value = blockchainService.isPinSetup()
        _isLocked.value = blockchainService.isPinSetup() // Locked if PIN is set

        // Try to load stored wallet and initialize network in background
        viewModelScope.launch {
            // Defer network initialization to avoid SSL issues on app startup
            try {
                blockchainService.initializeNetwork(BlockchainNetwork.AVALANCHE_MAINNET)
            } catch (e: Exception) {
                android.util.Log.e("BlockchainViewModel", "Failed to initialize network", e)
            }
            loadStoredWallet()
            checkAutoLock()
        }
    }

    /**
     * Check if wallet should be auto-locked
     */
    private fun checkAutoLock() {
        if (blockchainService.isPinSetup() && blockchainService.isLockedByTimeout()) {
            _isLocked.value = true
        }
    }

    /**
     * Set the current user ID for user-specific blockchain data
     */
    fun setCurrentUser(userId: Long) {
        _currentUserId.value = userId
        viewModelScope.launch {
            loadUserBlockchainData(userId)
            checkForNewAchievements(userId)
        }
    }

    /**
     * Load stored wallet from encrypted storage
     */
    private suspend fun loadStoredWallet() {
        _walletState.update { it.copy(isLoading = true) }

        blockchainService.loadStoredWallet()
            .onSuccess { address ->
                if (address != null) {
                    _walletState.update {
                        it.copy(
                            isConnected = true,
                            address = address,
                            isLoading = false
                        )
                    }
                    refreshBalance()
                } else {
                    _walletState.update { it.copy(isLoading = false) }
                }
            }
            .onFailure { error ->
                Log.e(TAG, "Failed to load stored wallet", error)
                _walletState.update {
                    it.copy(isLoading = false, error = error.message)
                }
            }
    }

    /**
     * Generate a new wallet
     */
    fun generateWallet() {
        viewModelScope.launch {
            _walletState.update { it.copy(isLoading = true, error = null) }

            blockchainService.generateWallet()
                .onSuccess { result ->
                    _walletState.update {
                        it.copy(
                            isConnected = true,
                            address = result.address,
                            isLoading = false
                        )
                    }

                    // Save wallet to database for current user
                    val userId = _currentUserId.value
                    if (userId > 0) {
                        val wallet = Wallet(
                            userId = userId,
                            address = result.address,
                            encryptedPrivateKey = result.privateKey,
                            isImported = false,
                            network = blockchainService.getCurrentNetwork()
                        )
                        blockchainDao.insertWallet(wallet)
                    }

                    refreshBalance()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to generate wallet", error)
                    _walletState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
        }
    }

    /**
     * Import wallet from private key
     */
    fun importWallet(privateKey: String) {
        viewModelScope.launch {
            _walletState.update { it.copy(isLoading = true, error = null) }

            blockchainService.importWallet(privateKey)
                .onSuccess { address ->
                    _walletState.update {
                        it.copy(
                            isConnected = true,
                            address = address,
                            isLoading = false
                        )
                    }

                    // Save wallet to database for current user
                    val userId = _currentUserId.value
                    if (userId > 0) {
                        val wallet = Wallet(
                            userId = userId,
                            address = address,
                            encryptedPrivateKey = privateKey,
                            isImported = true,
                            network = blockchainService.getCurrentNetwork()
                        )
                        blockchainDao.insertWallet(wallet)
                    }

                    refreshBalance()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to import wallet", error)
                    _walletState.update {
                        it.copy(isLoading = false, error = "Invalid private key")
                    }
                }
        }
    }

    /**
     * Disconnect wallet
     */
    fun disconnectWallet() {
        viewModelScope.launch {
            blockchainService.clearWallet()
            _walletState.value = WalletState()

            // Remove wallet from database
            val userId = _currentUserId.value
            if (userId > 0) {
                blockchainDao.getWalletForUserSync(userId)?.let { wallet ->
                    blockchainDao.deleteWallet(wallet)
                }
            }
        }
    }

    /**
     * Switch blockchain network
     */
    fun switchNetwork(network: BlockchainNetwork) {
        blockchainService.initializeNetwork(network)
        _walletState.update { it.copy(network = network) }

        if (_walletState.value.isConnected) {
            refreshBalance()
        }
    }

    /**
     * Refresh wallet balance (AVAX and $VIIIBE)
     */
    fun refreshBalance() {
        val address = _walletState.value.address ?: return

        viewModelScope.launch {
            // Fetch AVAX balance
            blockchainService.getBalance(address)
                .onSuccess { balance ->
                    _walletState.update { it.copy(balance = balance) }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to get balance", error)
                }

            // Fetch $VIIIBE token balance
            refreshViiibeBalance()
        }
    }

    /**
     * Refresh $VIIIBE token balance
     */
    fun refreshViiibeBalance() {
        val address = _walletState.value.address ?: return

        viewModelScope.launch {
            blockchainService.getViiibeBalance(address)
                .onSuccess { balance ->
                    _walletState.update { it.copy(viiibeBalance = balance) }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to get VIIIBE balance", error)
                }
        }
    }

    /**
     * Transfer $VIIIBE tokens
     */
    fun transferViiibe(toAddress: String, amount: Double) {
        viewModelScope.launch {
            _walletState.update { it.copy(isLoading = true, error = null) }

            blockchainService.transferViiibe(toAddress, amount)
                .onSuccess { txHash ->
                    Log.d(TAG, "VIIIBE transfer sent: $txHash")
                    _walletState.update { it.copy(isLoading = false) }
                    refreshBalance()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to transfer VIIIBE", error)
                    _walletState.update {
                        it.copy(isLoading = false, error = "VIIIBE transfer failed: ${error.message}")
                    }
                }
        }
    }

    /**
     * Send a transaction to another address
     */
    fun sendTransaction(toAddress: String, amount: String) {
        viewModelScope.launch {
            _walletState.update { it.copy(isLoading = true, error = null) }

            blockchainService.sendTransaction(toAddress, amount)
                .onSuccess { txHash ->
                    Log.d(TAG, "Transaction sent: $txHash")
                    _walletState.update { it.copy(isLoading = false) }
                    // Refresh balance after sending
                    refreshBalance()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to send transaction", error)
                    _walletState.update {
                        it.copy(isLoading = false, error = "Transaction failed: ${error.message}")
                    }
                }
        }
    }

    /**
     * Load user-specific blockchain data
     */
    private suspend fun loadUserBlockchainData(userId: Long) {
        // Observe achievements
        blockchainDao.getAchievementsForUser(userId)
            .combine(blockchainDao.getOnChainWorkoutCount(userId)) { achievements, workoutCount ->
                OnChainStats(
                    totalMintedAchievements = achievements.count { it.isMinted },
                    totalOnChainWorkouts = workoutCount,
                    earnedAchievements = achievements,
                    pendingAchievements = findPendingAchievements(userId, achievements)
                )
            }
            .collect { stats ->
                _onChainStats.value = stats
            }
    }

    /**
     * Check for newly earned achievements
     */
    suspend fun checkForNewAchievements(userId: Long) {
        // Get user stats
        val workoutCount = workoutDao.getWorkoutCountForUserSync(userId)
        val totalMinutes = (workoutDao.getTotalMinutesForUserSync(userId) ?: 0) / 60 // Convert to minutes
        val totalCalories = workoutDao.getTotalCaloriesForUserSync(userId) ?: 0
        val totalDistance = workoutDao.getTotalDistanceForUserSync(userId) ?: 0f
        val totalOutput = workoutDao.getTotalOutputForUserSync(userId) ?: 0

        // Check each achievement type
        for (achievementType in AchievementType.entries) {
            // Check if already earned
            val existing = blockchainDao.getAchievementByType(userId, achievementType)
            if (existing != null) continue

            // Check if requirement is met
            val achieved = when (achievementType) {
                // Ride achievements
                AchievementType.FIRST_RIDE,
                AchievementType.TEN_RIDES,
                AchievementType.FIFTY_RIDES,
                AchievementType.HUNDRED_RIDES -> workoutCount >= achievementType.requiredValue

                // Distance achievements
                AchievementType.TEN_MILES,
                AchievementType.FIFTY_MILES,
                AchievementType.HUNDRED_MILES,
                AchievementType.FIVE_HUNDRED_MILES,
                AchievementType.THOUSAND_MILES -> totalDistance >= achievementType.requiredValue

                // Calorie achievements
                AchievementType.THOUSAND_CALS,
                AchievementType.TEN_K_CALS,
                AchievementType.FIFTY_K_CALS -> totalCalories >= achievementType.requiredValue

                // Time achievements
                AchievementType.HOUR_RIDER,
                AchievementType.TEN_HOURS,
                AchievementType.FIFTY_HOURS -> totalMinutes >= achievementType.requiredValue

                // Output achievements
                AchievementType.HUNDRED_KJ,
                AchievementType.THOUSAND_KJ,
                AchievementType.TEN_K_KJ -> totalOutput >= achievementType.requiredValue
            }

            if (achieved) {
                // Create achievement record
                val achievement = Achievement(
                    userId = userId,
                    type = achievementType,
                    isMinted = false
                )
                blockchainDao.insertAchievement(achievement)
                Log.d(TAG, "User earned achievement: ${achievementType.title}")
            }
        }
    }

    /**
     * Find achievements that are close to being earned
     */
    private suspend fun findPendingAchievements(
        userId: Long,
        earnedAchievements: List<Achievement>
    ): List<AchievementType> {
        val earnedTypes = earnedAchievements.map { it.type }.toSet()

        // Get user stats
        val workoutCount = workoutDao.getWorkoutCountForUserSync(userId)
        val totalMinutes = (workoutDao.getTotalMinutesForUserSync(userId) ?: 0) / 60
        val totalCalories = workoutDao.getTotalCaloriesForUserSync(userId) ?: 0
        val totalDistance = workoutDao.getTotalDistanceForUserSync(userId) ?: 0f
        val totalOutput = workoutDao.getTotalOutputForUserSync(userId) ?: 0

        return AchievementType.entries.filter { type ->
            if (type in earnedTypes) return@filter false

            // Calculate progress percentage
            val progress = when (type) {
                AchievementType.FIRST_RIDE,
                AchievementType.TEN_RIDES,
                AchievementType.FIFTY_RIDES,
                AchievementType.HUNDRED_RIDES -> workoutCount.toFloat() / type.requiredValue

                AchievementType.TEN_MILES,
                AchievementType.FIFTY_MILES,
                AchievementType.HUNDRED_MILES,
                AchievementType.FIVE_HUNDRED_MILES,
                AchievementType.THOUSAND_MILES -> totalDistance / type.requiredValue

                AchievementType.THOUSAND_CALS,
                AchievementType.TEN_K_CALS,
                AchievementType.FIFTY_K_CALS -> totalCalories.toFloat() / type.requiredValue

                AchievementType.HOUR_RIDER,
                AchievementType.TEN_HOURS,
                AchievementType.FIFTY_HOURS -> totalMinutes.toFloat() / type.requiredValue

                AchievementType.HUNDRED_KJ,
                AchievementType.THOUSAND_KJ,
                AchievementType.TEN_K_KJ -> totalOutput.toFloat() / type.requiredValue
            }

            // Show if at least 50% progress
            progress >= 0.5f
        }
    }

    /**
     * Record a workout hash on-chain (simulation for now)
     */
    fun recordWorkoutOnChain(workoutId: Long) {
        viewModelScope.launch {
            val userId = _currentUserId.value
            if (userId <= 0) return@launch

            // Check if already recorded
            if (blockchainDao.isWorkoutOnChain(workoutId)) {
                Log.d(TAG, "Workout $workoutId already on chain")
                return@launch
            }

            // Get workout data
            val workout = workoutDao.getWorkoutById(workoutId) ?: return@launch

            // Hash workout data
            val workoutHash = blockchainService.hashWorkoutData(
                userId = userId,
                workoutId = workoutId,
                startTime = workout.startTime,
                durationSeconds = workout.durationSeconds,
                totalOutput = workout.totalOutput,
                totalDistance = workout.totalDistance,
                caloriesBurned = workout.caloriesBurned
            )

            // In a real implementation, this would submit a transaction
            // For now, we'll simulate with a signed message
            blockchainService.signMessage(workoutHash)
                .onSuccess { signature ->
                    val onChainWorkout = OnChainWorkout(
                        userId = userId,
                        workoutId = workoutId,
                        workoutHash = workoutHash,
                        transactionHash = signature, // Using signature as mock tx hash
                        network = blockchainService.getCurrentNetwork()
                    )
                    blockchainDao.insertOnChainWorkout(onChainWorkout)
                    Log.d(TAG, "Recorded workout $workoutId on chain")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to record workout on chain", error)
                }
        }
    }

    /**
     * Get achievements for display
     */
    fun getAchievementsForUser(userId: Long): Flow<List<Achievement>> {
        return blockchainDao.getAchievementsForUser(userId)
    }

    /**
     * Get wallet for user
     */
    fun getWalletForUser(userId: Long): Flow<Wallet?> {
        return blockchainDao.getWalletForUser(userId)
    }

    /**
     * Get explorer URL for address
     */
    fun getAddressExplorerUrl(): String? {
        val address = _walletState.value.address ?: return null
        return blockchainService.getAddressExplorerUrl(address)
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _walletState.update { it.copy(error = null) }
    }

    /**
     * Get private key for export (use with caution)
     * Returns null if locked
     */
    fun getPrivateKey(): String? {
        if (_isLocked.value && _isPinSetup.value) return null
        blockchainService.updateUnlockTime()
        return blockchainService.getPrivateKey()
    }

    // ==================== PIN Security Methods ====================

    /**
     * Set up a new PIN
     */
    fun setupPin(pin: String): Boolean {
        val success = blockchainService.setupPin(pin)
        if (success) {
            _isPinSetup.value = true
            _isLocked.value = false
        }
        return success
    }

    /**
     * Verify PIN and unlock wallet
     */
    fun unlockWithPin(pin: String): Boolean {
        val success = blockchainService.verifyPin(pin)
        if (success) {
            _isLocked.value = false
        }
        return success
    }

    /**
     * Lock wallet manually
     */
    fun lockWallet() {
        if (_isPinSetup.value) {
            _isLocked.value = true
        }
    }

    /**
     * Change PIN
     */
    fun changePin(oldPin: String, newPin: String): Boolean {
        return blockchainService.changePin(oldPin, newPin)
    }

    /**
     * Remove PIN protection
     */
    fun removePin(pin: String): Boolean {
        val success = blockchainService.removePin(pin)
        if (success) {
            _isPinSetup.value = false
            _isLocked.value = false
        }
        return success
    }

    /**
     * Check if currently locked and should show PIN entry
     */
    fun requiresUnlock(): Boolean {
        if (!_isPinSetup.value) return false
        if (blockchainService.isLockedByTimeout()) {
            _isLocked.value = true
        }
        return _isLocked.value
    }

    /**
     * Update activity time to prevent auto-lock
     */
    fun updateActivityTime() {
        if (!_isLocked.value) {
            blockchainService.updateUnlockTime()
        }
    }

    override fun onCleared() {
        super.onCleared()
        blockchainService.shutdown()
    }
}
