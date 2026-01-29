package com.viiibe.app.arcade.p2p

import android.content.Context
import android.util.Log
import com.viiibe.app.blockchain.BlockchainNetwork
import com.viiibe.app.blockchain.BlockchainService
import com.viiibe.app.blockchain.contracts.ContractAddresses
import com.viiibe.app.blockchain.contracts.ViiibeCoreContract
import com.viiibe.app.blockchain.contracts.ViiibeTokenContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.math.BigInteger
import java.security.MessageDigest

class WagerManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "WagerManager"
    }

    private val blockchainService = BlockchainService(context)
    private var web3j: Web3j? = null
    private var tokenContract: ViiibeTokenContract? = null
    private var coreContract: ViiibeCoreContract? = null

    private var isTestnet = false

    fun initialize(network: BlockchainNetwork) {
        blockchainService.initializeNetwork(network)
        isTestnet = network != BlockchainNetwork.AVALANCHE_MAINNET

        web3j = Web3j.build(HttpService(network.rpcUrl))
        tokenContract = ViiibeTokenContract(
            web3j!!,
            ContractAddresses.getTokenAddress(isTestnet)
        )
        coreContract = ViiibeCoreContract(
            web3j!!,
            ContractAddresses.getCoreAddress(isTestnet)
        )
    }

    suspend fun checkViiibeBalance(address: String): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val balance = tokenContract?.getBalance(address) ?: BigInteger.ZERO
            val formatted = ViiibeTokenContract.fromTokenUnits(balance)
            Result.success(formatted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check balance", e)
            Result.failure(e)
        }
    }

    suspend fun approveStake(amount: Double): Result<String> = withContext(Dispatchers.IO) {
        try {
            val coreAddress = ContractAddresses.getCoreAddress(isTestnet)
            val result = blockchainService.approveViiibe(coreAddress, amount)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to approve stake", e)
            Result.failure(e)
        }
    }

    suspend fun createWageredGame(
        stake: Double,
        gameType: String
    ): Result<WagerCreationResult> = withContext(Dispatchers.IO) {
        try {
            // First approve the stake
            val approvalResult = approveStake(stake)
            if (approvalResult.isFailure) {
                return@withContext Result.failure(
                    approvalResult.exceptionOrNull() ?: Exception("Approval failed")
                )
            }

            // Convert game type to bytes32
            val gameTypeBytes = gameType.toByteArray().copyOf(32)
            val stakeInUnits = ViiibeTokenContract.toTokenUnits(stake)

            // For now, simulate the contract call
            // In production, this would call coreContract.createGame()
            val mockGameId = System.currentTimeMillis()
            val mockTxHash = generateMockTxHash()

            Log.d(TAG, "Created wagered game: $mockGameId, stake: $stake VIIIBE")

            Result.success(
                WagerCreationResult(
                    gameId = mockGameId.toString(),
                    txHash = mockTxHash,
                    stake = stake
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create wagered game", e)
            Result.failure(e)
        }
    }

    suspend fun joinWageredGame(
        gameId: String,
        stake: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Approve stake first
            val approvalResult = approveStake(stake)
            if (approvalResult.isFailure) {
                return@withContext Result.failure(
                    approvalResult.exceptionOrNull() ?: Exception("Approval failed")
                )
            }

            // For now, simulate the contract call
            val mockTxHash = generateMockTxHash()

            Log.d(TAG, "Joined wagered game: $gameId")

            Result.success(mockTxHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join wagered game", e)
            Result.failure(e)
        }
    }

    suspend fun settleGame(
        gameId: String,
        winnerAddress: String,
        gameProof: GameProof
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Create proof bytes from game data
            val proofBytes = createProofBytes(gameProof)

            // For now, simulate the contract call
            val mockTxHash = generateMockTxHash()

            Log.d(TAG, "Settled game $gameId, winner: $winnerAddress")

            Result.success(mockTxHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to settle game", e)
            Result.failure(e)
        }
    }

    suspend fun raiseDispute(
        gameId: String,
        evidence: DisputeEvidence
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val evidenceBytes = createEvidenceBytes(evidence)

            // For now, simulate the contract call
            val mockTxHash = generateMockTxHash()

            Log.d(TAG, "Raised dispute for game $gameId")

            Result.success(mockTxHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to raise dispute", e)
            Result.failure(e)
        }
    }

    suspend fun getGameInfo(gameId: String): Result<GameWagerInfo> = withContext(Dispatchers.IO) {
        try {
            // For now, return mock data
            // In production, this would call coreContract.getGameState()
            Result.success(
                GameWagerInfo(
                    gameId = gameId,
                    host = "",
                    guest = "",
                    stake = 0.0,
                    status = WagerStatus.PENDING,
                    winner = null,
                    spectatorPoolA = 0.0,
                    spectatorPoolB = 0.0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get game info", e)
            Result.failure(e)
        }
    }

    suspend fun placeBet(
        gameId: String,
        predictedWinner: String,
        amount: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Approve tokens for betting
            val approvalResult = approveStake(amount)
            if (approvalResult.isFailure) {
                return@withContext Result.failure(
                    approvalResult.exceptionOrNull() ?: Exception("Approval failed")
                )
            }

            // For now, simulate the contract call
            val mockTxHash = generateMockTxHash()

            Log.d(TAG, "Placed bet on game $gameId for $predictedWinner: $amount VIIIBE")

            Result.success(mockTxHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place bet", e)
            Result.failure(e)
        }
    }

    suspend fun claimWinnings(gameId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // For now, simulate the contract call
            val mockTxHash = generateMockTxHash()

            Log.d(TAG, "Claimed winnings for game $gameId")

            Result.success(mockTxHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to claim winnings", e)
            Result.failure(e)
        }
    }

    suspend fun getOdds(gameId: String): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            // For now, return default odds
            // In production, this would call coreContract.getOdds()
            Result.success(Pair(1.5, 2.5))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get odds", e)
            Result.failure(e)
        }
    }

    fun calculatePayout(stake: Double, isWinner: Boolean): Double {
        return if (isWinner) {
            stake * 2 * (1.0 - ViiibeCoreContract.PLAYER_FEE_PERCENT / 100.0)
        } else {
            0.0
        }
    }

    fun calculateSpectatorPayout(betAmount: Double, odds: Double, isWinner: Boolean): Double {
        return if (isWinner) {
            betAmount * odds * (1.0 - ViiibeCoreContract.SPECTATOR_FEE_PERCENT / 100.0)
        } else {
            0.0
        }
    }

    private fun createProofBytes(proof: GameProof): ByteArray {
        val data = "${proof.sessionId}|${proof.player1Score}|${proof.player2Score}|${proof.timestamp}|${proof.gameHash}"
        return data.toByteArray()
    }

    private fun createEvidenceBytes(evidence: DisputeEvidence): ByteArray {
        val data = "${evidence.gameId}|${evidence.reason}|${evidence.claimedScore}|${evidence.timestamp}"
        return data.toByteArray()
    }

    private fun generateMockTxHash(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return "0x" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun shutdown() {
        web3j?.shutdown()
        blockchainService.shutdown()
    }
}

data class WagerCreationResult(
    val gameId: String,
    val txHash: String,
    val stake: Double
)

data class GameWagerInfo(
    val gameId: String,
    val host: String,
    val guest: String,
    val stake: Double,
    val status: WagerStatus,
    val winner: String?,
    val spectatorPoolA: Double,
    val spectatorPoolB: Double
)

enum class WagerStatus {
    PENDING,
    MATCHED,
    PLAYING,
    SETTLED,
    DISPUTED,
    CANCELLED
}

data class GameProof(
    val sessionId: String,
    val player1Address: String,
    val player2Address: String,
    val player1Score: Int,
    val player2Score: Int,
    val timestamp: Long,
    val gameHash: String
)

data class DisputeEvidence(
    val gameId: String,
    val reason: String,
    val claimedScore: Int,
    val timestamp: Long
)
