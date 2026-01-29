package com.viiibe.app.blockchain.contracts

object ContractAddresses {
    // Avalanche C-Chain Mainnet
    object Mainnet {
        const val VIIIBE_TOKEN = "0x0000000000000000000000000000000000000000" // TODO: Deploy and update
        const val VIIIBE_CORE = "0x0000000000000000000000000000000000000000"  // TODO: Deploy and update
    }

    // Avalanche Fuji Testnet
    object Testnet {
        const val VIIIBE_TOKEN = "0x0000000000000000000000000000000000000000" // TODO: Deploy and update
        const val VIIIBE_CORE = "0x0000000000000000000000000000000000000000"  // TODO: Deploy and update
    }

    // Get addresses based on network
    fun getTokenAddress(isTestnet: Boolean): String {
        return if (isTestnet) Testnet.VIIIBE_TOKEN else Mainnet.VIIIBE_TOKEN
    }

    fun getCoreAddress(isTestnet: Boolean): String {
        return if (isTestnet) Testnet.VIIIBE_CORE else Mainnet.VIIIBE_CORE
    }
}
