package com.viiibe.app.blockchain.contracts

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

class ViiibeCoreContract(
    private val web3j: Web3j,
    private val contractAddress: String
) {
    companion object {
        const val PLAYER_FEE_PERCENT = 5   // 5% house fee on player wagers
        const val SPECTATOR_FEE_PERCENT = 3 // 3% house fee on spectator bets
    }

    data class GameInfo(
        val gameId: BigInteger,
        val gameType: ByteArray,
        val host: String,
        val guest: String,
        val stake: BigInteger,
        val status: Int,           // 0=Created, 1=Matched, 2=Playing, 3=Settled, 4=Disputed, 5=Cancelled
        val winner: String,
        val spectatorPoolA: BigInteger,
        val spectatorPoolB: BigInteger
    )

    suspend fun createGame(
        credentials: Credentials,
        stake: BigInteger,
        gameType: ByteArray,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(300000)
    ): String {
        val function = Function(
            "createGame",
            listOf(Uint256(stake), Bytes32(gameType.copyOf(32))),
            emptyList()
        )

        return executeTransaction(credentials, function, gasPrice, gasLimit)
    }

    suspend fun joinGame(
        credentials: Credentials,
        gameId: BigInteger,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(250000)
    ): String {
        val function = Function(
            "joinGame",
            listOf(Uint256(gameId)),
            emptyList()
        )

        return executeTransaction(credentials, function, gasPrice, gasLimit)
    }

    suspend fun settleGame(
        credentials: Credentials,
        gameId: BigInteger,
        winner: String,
        proof: ByteArray,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(350000)
    ): String {
        val function = Function(
            "settleGame",
            listOf(Uint256(gameId), Address(winner), DynamicBytes(proof)),
            emptyList()
        )

        return executeTransaction(credentials, function, gasPrice, gasLimit)
    }

    suspend fun disputeGame(
        credentials: Credentials,
        gameId: BigInteger,
        evidence: ByteArray,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(200000)
    ): String {
        val function = Function(
            "disputeGame",
            listOf(Uint256(gameId), DynamicBytes(evidence)),
            emptyList()
        )

        return executeTransaction(credentials, function, gasPrice, gasLimit)
    }

    suspend fun placeBet(
        credentials: Credentials,
        gameId: BigInteger,
        predictedWinner: String,
        amount: BigInteger,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(200000)
    ): String {
        val function = Function(
            "placeBet",
            listOf(Uint256(gameId), Address(predictedWinner), Uint256(amount)),
            emptyList()
        )

        return executeTransaction(credentials, function, gasPrice, gasLimit)
    }

    suspend fun claimWinnings(
        credentials: Credentials,
        gameId: BigInteger,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(150000)
    ): String {
        val function = Function(
            "claimWinnings",
            listOf(Uint256(gameId)),
            emptyList()
        )

        return executeTransaction(credentials, function, gasPrice, gasLimit)
    }

    suspend fun getGameState(gameId: BigInteger): GameInfo? {
        val function = Function(
            "getGameState",
            listOf(Uint256(gameId)),
            listOf(
                TypeReference.create(Uint256::class.java),   // gameId
                TypeReference.create(Bytes32::class.java),   // gameType
                TypeReference.create(Address::class.java),   // host
                TypeReference.create(Address::class.java),   // guest
                TypeReference.create(Uint256::class.java),   // stake
                TypeReference.create(Uint256::class.java),   // status
                TypeReference.create(Address::class.java),   // winner
                TypeReference.create(Uint256::class.java),   // spectatorPoolA
                TypeReference.create(Uint256::class.java)    // spectatorPoolB
            )
        )

        val result = callContract(function)
        if (result.isEmpty()) return null

        return GameInfo(
            gameId = (result[0] as Uint256).value,
            gameType = (result[1] as Bytes32).value,
            host = (result[2] as Address).value,
            guest = (result[3] as Address).value,
            stake = (result[4] as Uint256).value,
            status = (result[5] as Uint256).value.toInt(),
            winner = (result[6] as Address).value,
            spectatorPoolA = (result[7] as Uint256).value,
            spectatorPoolB = (result[8] as Uint256).value
        )
    }

    suspend fun getOdds(gameId: BigInteger): Pair<Double, Double> {
        val function = Function(
            "getOdds",
            listOf(Uint256(gameId)),
            listOf(
                TypeReference.create(Uint256::class.java),  // oddsA (scaled by 100)
                TypeReference.create(Uint256::class.java)   // oddsB (scaled by 100)
            )
        )

        val result = callContract(function)
        if (result.size < 2) return Pair(1.0, 1.0)

        val oddsA = (result[0] as Uint256).value.toDouble() / 100.0
        val oddsB = (result[1] as Uint256).value.toDouble() / 100.0

        return Pair(oddsA, oddsB)
    }

    suspend fun getBetAmount(gameId: BigInteger, bettor: String): BigInteger {
        val function = Function(
            "getBetAmount",
            listOf(Uint256(gameId), Address(bettor)),
            listOf(TypeReference.create(Uint256::class.java))
        )

        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Uint256).value
        } else BigInteger.ZERO
    }

    suspend fun hasClaimed(gameId: BigInteger, bettor: String): Boolean {
        val function = Function(
            "hasClaimed",
            listOf(Uint256(gameId), Address(bettor)),
            listOf(TypeReference.create(Bool::class.java))
        )

        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Bool).value
        } else false
    }

    private suspend fun executeTransaction(
        credentials: Credentials,
        function: Function,
        gasPrice: BigInteger,
        gasLimit: BigInteger
    ): String {
        val nonce = web3j.ethGetTransactionCount(
            credentials.address,
            DefaultBlockParameterName.PENDING
        ).send().transactionCount

        val data = FunctionEncoder.encode(function)

        val rawTransaction = RawTransaction.createTransaction(
            nonce,
            gasPrice,
            gasLimit,
            contractAddress,
            BigInteger.ZERO,
            data
        )

        val signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials)
        val hexValue = Numeric.toHexString(signedMessage)

        val response = web3j.ethSendRawTransaction(hexValue).send()

        if (response.hasError()) {
            throw Exception("Transaction failed: ${response.error.message}")
        }

        return response.transactionHash
    }

    private suspend fun callContract(function: Function): List<org.web3j.abi.datatypes.Type<*>> {
        val encodedFunction = FunctionEncoder.encode(function)

        val ethCall: EthCall = web3j.ethCall(
            Transaction.createEthCallTransaction(
                "0x0000000000000000000000000000000000000000",
                contractAddress,
                encodedFunction
            ),
            DefaultBlockParameterName.LATEST
        ).send()

        return if (ethCall.hasError()) {
            emptyList()
        } else {
            FunctionReturnDecoder.decode(ethCall.value, function.outputParameters)
        }
    }
}
