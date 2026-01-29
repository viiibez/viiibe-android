package com.viiibe.app.blockchain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viiibe.app.data.model.User

/**
 * Represents a user's connected blockchain wallet
 */
@Entity(
    tableName = "wallets",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class Wallet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val address: String,
    val encryptedPrivateKey: String? = null, // For locally generated wallets
    val isImported: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val network: BlockchainNetwork = BlockchainNetwork.AVALANCHE_MAINNET
)

/**
 * Supported blockchain networks - Avalanche mainnet only
 */
enum class BlockchainNetwork(
    val displayName: String,
    val chainId: Long,
    val rpcUrl: String,
    val fallbackRpcUrls: List<String>,
    val explorerUrl: String,
    val currencySymbol: String
) {
    AVALANCHE_MAINNET(
        displayName = "Avalanche C-Chain",
        chainId = 43114,
        rpcUrl = "https://api.avax.network/ext/bc/C/rpc",
        fallbackRpcUrls = listOf(
            "https://avalanche-c-chain-rpc.publicnode.com",
            "https://rpc.ankr.com/avalanche",
            "https://avax.meowrpc.com",
            "https://1rpc.io/avax/c"
        ),
        explorerUrl = "https://snowtrace.io",
        currencySymbol = "AVAX"
    )
}

/**
 * Achievement types that can be minted as NFTs
 */
enum class AchievementType(
    val title: String,
    val description: String,
    val requiredValue: Int,
    val iconName: String
) {
    // Ride count achievements
    FIRST_RIDE("First Ride", "Completed your first workout", 1, "pedal_bike"),
    TEN_RIDES("Dedicated Rider", "Completed 10 workouts", 10, "fitness_center"),
    FIFTY_RIDES("Fitness Warrior", "Completed 50 workouts", 50, "military_tech"),
    HUNDRED_RIDES("Century Club", "Completed 100 workouts", 100, "emoji_events"),

    // Distance achievements (in miles)
    TEN_MILES("First Ten", "Rode 10 total miles", 10, "route"),
    FIFTY_MILES("Half Century", "Rode 50 total miles", 50, "map"),
    HUNDRED_MILES("Century Rider", "Rode 100 total miles", 100, "public"),
    FIVE_HUNDRED_MILES("Road Warrior", "Rode 500 total miles", 500, "explore"),
    THOUSAND_MILES("Iron Legs", "Rode 1,000 total miles", 1000, "star"),

    // Calorie achievements
    THOUSAND_CALS("Calorie Crusher", "Burned 1,000 total calories", 1000, "local_fire_department"),
    TEN_K_CALS("Furnace", "Burned 10,000 total calories", 10000, "whatshot"),
    FIFTY_K_CALS("Incinerator", "Burned 50,000 total calories", 50000, "bolt"),

    // Time achievements (in minutes)
    HOUR_RIDER("First Hour", "Rode for 60 total minutes", 60, "timer"),
    TEN_HOURS("Dedicated", "Rode for 600 total minutes", 600, "schedule"),
    FIFTY_HOURS("Committed", "Rode for 3,000 total minutes", 3000, "hourglass_full"),

    // Output achievements (in kJ)
    HUNDRED_KJ("Power Starter", "Generated 100 total kJ", 100, "flash_on"),
    THOUSAND_KJ("Power House", "Generated 1,000 total kJ", 1000, "electric_bolt"),
    TEN_K_KJ("Generator", "Generated 10,000 total kJ", 10000, "offline_bolt")
}

/**
 * Represents an earned achievement (minted or pending)
 */
@Entity(
    tableName = "achievements",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class Achievement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val type: AchievementType,
    val earnedAt: Long = System.currentTimeMillis(),
    val isMinted: Boolean = false,
    val mintTransactionHash: String? = null,
    val tokenId: String? = null,
    val network: BlockchainNetwork? = null
)

/**
 * Represents a workout that has been recorded on-chain
 */
@Entity(
    tableName = "onchain_workouts",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["workoutId"])]
)
data class OnChainWorkout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val workoutId: Long,
    val workoutHash: String, // Hash of workout data for verification
    val transactionHash: String,
    val network: BlockchainNetwork,
    val recordedAt: Long = System.currentTimeMillis()
)

/**
 * UI state for wallet connection
 */
data class WalletState(
    val isConnected: Boolean = false,
    val address: String? = null,
    val balance: String = "0",
    val viiibeBalance: Double = 0.0,  // $VIIIBE token balance
    val network: BlockchainNetwork = BlockchainNetwork.AVALANCHE_MAINNET,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Statistics for on-chain activity
 */
data class OnChainStats(
    val totalMintedAchievements: Int = 0,
    val totalOnChainWorkouts: Int = 0,
    val earnedAchievements: List<Achievement> = emptyList(),
    val pendingAchievements: List<AchievementType> = emptyList()
)
