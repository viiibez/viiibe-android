package com.viiibe.app.data.database

import androidx.room.TypeConverter
import com.viiibe.app.blockchain.AchievementType
import com.viiibe.app.blockchain.BlockchainNetwork

class Converters {

    @TypeConverter
    fun fromBlockchainNetwork(network: BlockchainNetwork?): String? {
        return network?.name
    }

    @TypeConverter
    fun toBlockchainNetwork(value: String?): BlockchainNetwork? {
        return value?.let {
            try {
                BlockchainNetwork.valueOf(it)
            } catch (e: IllegalArgumentException) {
                BlockchainNetwork.AVALANCHE_MAINNET
            }
        }
    }

    @TypeConverter
    fun fromAchievementType(type: AchievementType): String {
        return type.name
    }

    @TypeConverter
    fun toAchievementType(value: String): AchievementType {
        return try {
            AchievementType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AchievementType.FIRST_RIDE
        }
    }
}
