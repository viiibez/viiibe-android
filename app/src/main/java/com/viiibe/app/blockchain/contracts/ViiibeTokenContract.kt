package com.viiibe.app.blockchain.contracts

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger

class ViiibeTokenContract(
    private val web3j: Web3j,
    private val contractAddress: String
) {
    companion object {
        const val TOKEN_NAME = "Viiibe"
        const val TOKEN_SYMBOL = "VIIIBE"
        const val DECIMALS = 18

        fun toTokenUnits(amount: Double): BigInteger {
            return BigInteger.valueOf((amount * Math.pow(10.0, DECIMALS.toDouble())).toLong())
        }

        fun fromTokenUnits(amount: BigInteger): Double {
            return amount.toDouble() / Math.pow(10.0, DECIMALS.toDouble())
        }
    }

    suspend fun getName(): String {
        val function = Function(
            "name",
            emptyList(),
            listOf(TypeReference.create(Utf8String::class.java))
        )
        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Utf8String).value
        } else TOKEN_NAME
    }

    suspend fun getSymbol(): String {
        val function = Function(
            "symbol",
            emptyList(),
            listOf(TypeReference.create(Utf8String::class.java))
        )
        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Utf8String).value
        } else TOKEN_SYMBOL
    }

    suspend fun getDecimals(): Int {
        val function = Function(
            "decimals",
            emptyList(),
            listOf(TypeReference.create(Uint8::class.java))
        )
        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Uint8).value.toInt()
        } else DECIMALS
    }

    suspend fun getTotalSupply(): BigInteger {
        val function = Function(
            "totalSupply",
            emptyList(),
            listOf(TypeReference.create(Uint256::class.java))
        )
        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Uint256).value
        } else BigInteger.ZERO
    }

    suspend fun getBalance(address: String): BigInteger {
        val function = Function(
            "balanceOf",
            listOf(Address(address)),
            listOf(TypeReference.create(Uint256::class.java))
        )
        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Uint256).value
        } else BigInteger.ZERO
    }

    suspend fun getAllowance(owner: String, spender: String): BigInteger {
        val function = Function(
            "allowance",
            listOf(Address(owner), Address(spender)),
            listOf(TypeReference.create(Uint256::class.java))
        )
        val result = callContract(function)
        return if (result.isNotEmpty()) {
            (result[0] as Uint256).value
        } else BigInteger.ZERO
    }

    fun encodeTransfer(to: String, amount: BigInteger): String {
        val function = Function(
            "transfer",
            listOf(Address(to), Uint256(amount)),
            emptyList()
        )
        return FunctionEncoder.encode(function)
    }

    fun encodeApprove(spender: String, amount: BigInteger): String {
        val function = Function(
            "approve",
            listOf(Address(spender), Uint256(amount)),
            emptyList()
        )
        return FunctionEncoder.encode(function)
    }

    fun encodeTransferFrom(from: String, to: String, amount: BigInteger): String {
        val function = Function(
            "transferFrom",
            listOf(Address(from), Address(to), Uint256(amount)),
            emptyList()
        )
        return FunctionEncoder.encode(function)
    }

    suspend fun transfer(
        credentials: Credentials,
        to: String,
        amount: BigInteger,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(100000)
    ): String {
        val nonce = web3j.ethGetTransactionCount(
            credentials.address,
            DefaultBlockParameterName.PENDING
        ).send().transactionCount

        val data = encodeTransfer(to, amount)

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
            throw Exception("Transfer failed: ${response.error.message}")
        }

        return response.transactionHash
    }

    suspend fun approve(
        credentials: Credentials,
        spender: String,
        amount: BigInteger,
        gasPrice: BigInteger = DefaultGasProvider.GAS_PRICE,
        gasLimit: BigInteger = BigInteger.valueOf(60000)
    ): String {
        val nonce = web3j.ethGetTransactionCount(
            credentials.address,
            DefaultBlockParameterName.PENDING
        ).send().transactionCount

        val data = encodeApprove(spender, amount)

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
            throw Exception("Approve failed: ${response.error.message}")
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
