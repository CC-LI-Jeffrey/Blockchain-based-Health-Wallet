package com.fyp.blockchainhealthwallet.blockchain

import android.util.Log
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.models.request.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BlockchainService handles all interactions with the HealthWallet smart contract.
 * User transactions are signed by the user's wallet (via Reown AppKit).
 * This ensures true data ownership - users control their own records.
 */
object BlockchainService {
    private const val TAG = "BlockchainService"
    
    // Contract configuration
    private const val CONTRACT_ADDRESS = "0xfdD271249a32a626D7B6884a5cbC8C6C79049087"
    
    // RPC endpoint - change based on your setup:
    // Android Emulator + Ganache on localhost:8545 -> "http://10.0.2.2:8545"
    // Real Android Device + Ganache -> "http://YOUR_COMPUTER_IP:8545"
    // TODO: Replace YOUR_COMPUTER_IP with your actual IP (e.g., 192.168.1.100)
    private const val RPC_URL = "http://10.0.2.2:8545" // Change if using real device!
    
    // Initialize Web3j for read operations
    private val web3j: Web3j by lazy {
        Web3j.build(HttpService(RPC_URL))
    }
    
    // RecordType enum matching Solidity contract
    enum class RecordType(val value: Int) {
        LAB_REPORT(0),
        PRESCRIPTION(1),
        MEDICAL_IMAGE(2),
        DIAGNOSIS(3),
        VACCINATION(4),
        VISIT_SUMMARY(5)
    }
    
    // AuditAction enum matching Solidity contract
    enum class AuditAction(val value: Int) {
        VIEW(0),
        DOWNLOAD(1),
        SHARE(2),
        UPDATE(3),
        DELETE(4)
    }
    
    /**
     * HealthRecord data class matching Solidity struct
     */
    data class HealthRecord(
        val recordId: BigInteger,
        val patientAddress: String,
        val ipfsHash: String,
        val recordType: RecordType,
        val timestamp: BigInteger,
        val issuedBy: String,
        val isActive: Boolean,
        val encryptedKey: String,
        val version: BigInteger
    )
    
    /**
     * AccessGrant data class matching Solidity struct
     */
    data class AccessGrant(
        val grantee: String,
        val recordIds: List<BigInteger>,
        val expiryTime: BigInteger,
        val isActive: Boolean
    )
    
    /**
     * Add a new health record to the blockchain.
     * User signs this transaction with their wallet.
     * 
     * @param ipfsHash The IPFS hash of the encrypted medical file
     * @param recordType Type of medical record
     * @param encryptedKey Encrypted symmetric key (encrypted with user's public key)
     * @return Transaction hash
     */
    suspend fun addHealthRecord(
        ipfsHash: String,
        recordType: RecordType,
        encryptedKey: String
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Adding health record for user: $userAddress")
        
        // Encode function call
        val function = org.web3j.abi.datatypes.Function(
            "addHealthRecord",
            listOf(
                Utf8String(ipfsHash),
                Uint8(BigInteger.valueOf(recordType.value.toLong())),
                Utf8String(encryptedKey)
            ),
            emptyList()
        )
        
        val encodedFunction = FunctionEncoder.encode(function)
        
        // Send transaction via user's wallet
        sendTransaction(
            from = userAddress,
            to = CONTRACT_ADDRESS,
            data = encodedFunction,
            value = "0x0"
        )
    }
    
    /**
     * Update an existing health record.
     * Only the record owner can update.
     */
    suspend fun updateHealthRecord(
        recordId: BigInteger,
        newIpfsHash: String,
        newEncryptedKey: String
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "updateHealthRecord",
            listOf(
                Uint256(recordId),
                Utf8String(newIpfsHash),
                Utf8String(newEncryptedKey)
            ),
            emptyList()
        )
        
        val encodedFunction = FunctionEncoder.encode(function)
        
        sendTransaction(
            from = userAddress,
            to = CONTRACT_ADDRESS,
            data = encodedFunction,
            value = "0x0"
        )
    }
    
    /**
     * Soft delete a health record.
     * Only the record owner can delete.
     */
    suspend fun deleteHealthRecord(recordId: BigInteger): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "deleteHealthRecord",
            listOf(Uint256(recordId)),
            emptyList()
        )
        
        val encodedFunction = FunctionEncoder.encode(function)
        
        sendTransaction(
            from = userAddress,
            to = CONTRACT_ADDRESS,
            data = encodedFunction,
            value = "0x0"
        )
    }
    
    /**
     * Grant access to multiple records for a provider.
     * User signs this transaction.
     * 
     * @param granteeAddress Address of the provider to grant access to
     * @param recordIds List of record IDs to grant access to
     * @param durationInDays How many days the access should last
     */
    suspend fun grantAccess(
        granteeAddress: String,
        recordIds: List<BigInteger>,
        durationInDays: BigInteger
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Granting access to $granteeAddress for ${recordIds.size} records")
        
        val function = org.web3j.abi.datatypes.Function(
            "grantAccess",
            listOf(
                Address(granteeAddress),
                DynamicArray(Uint256::class.java, recordIds.map { Uint256(it) }),
                Uint256(durationInDays)
            ),
            emptyList()
        )
        
        val encodedFunction = FunctionEncoder.encode(function)
        
        sendTransaction(
            from = userAddress,
            to = CONTRACT_ADDRESS,
            data = encodedFunction,
            value = "0x0"
        )
    }
    
    /**
     * Revoke all access previously granted to a provider.
     * User signs this transaction.
     */
    suspend fun revokeAccess(granteeAddress: String): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "revokeAccess",
            listOf(Address(granteeAddress)),
            emptyList()
        )
        
        val encodedFunction = FunctionEncoder.encode(function)
        
        sendTransaction(
            from = userAddress,
            to = CONTRACT_ADDRESS,
            data = encodedFunction,
            value = "0x0"
        )
    }
    
    /**
     * Set emergency contact address.
     * User signs this transaction.
     */
    suspend fun setEmergencyContact(contactAddress: String): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "setEmergencyContact",
            listOf(Address(contactAddress)),
            emptyList()
        )
        
        val encodedFunction = FunctionEncoder.encode(function)
        
        sendTransaction(
            from = userAddress,
            to = CONTRACT_ADDRESS,
            data = encodedFunction,
            value = "0x0"
        )
    }
    
    /**
     * Get total number of records in the system.
     * This is a read-only call, no signature needed.
     */
    suspend fun getTotalRecords(): BigInteger = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getTotalRecords",
                emptyList(),
                listOf(object : TypeReference<Uint256>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting total records: ${response.error.message}")
                return@withContext BigInteger.ZERO
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext BigInteger.ZERO
            }
            
            Numeric.decodeQuantity(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total records", e)
            BigInteger.ZERO
        }
    }
    
    /**
     * Get all record IDs owned by a patient.
     * Read-only operation.
     */
    suspend fun getPatientRecords(patientAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getPatientRecords",
                listOf(Address(patientAddress)),
                listOf(object : TypeReference<DynamicArray<Uint256>>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting patient records: ${response.error.message}")
                return@withContext emptyList()
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext emptyList()
            }
            
            // Decode the dynamic array of uint256
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.isEmpty()) {
                return@withContext emptyList()
            }
            
            @Suppress("UNCHECKED_CAST")
            val recordIds = (decodedResult[0] as DynamicArray<Uint256>).value
            recordIds.map { it.value }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting patient records", e)
            emptyList()
        }
    }
    
    /**
     * Get details of a specific health record.
     * Read-only operation.
     */
    suspend fun getHealthRecord(recordId: BigInteger): HealthRecord? = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getHealthRecord",
                listOf(Uint256(recordId)),
                listOf(
                    object : TypeReference<Uint256>() {},
                    object : TypeReference<Address>() {},
                    object : TypeReference<Utf8String>() {},
                    object : TypeReference<Uint8>() {},
                    object : TypeReference<Uint256>() {},
                    object : TypeReference<Address>() {},
                    object : TypeReference<Bool>() {},
                    object : TypeReference<Utf8String>() {},
                    object : TypeReference<Uint256>() {}
                )
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting health record: ${response.error.message}")
                return@withContext null
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext null
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.size < 9) {
                return@withContext null
            }
            
            HealthRecord(
                recordId = (decodedResult[0] as Uint256).value,
                patientAddress = (decodedResult[1] as Address).value,
                ipfsHash = (decodedResult[2] as Utf8String).value,
                recordType = RecordType.values().getOrNull((decodedResult[3] as Uint8).value.toInt()) 
                    ?: RecordType.LAB_REPORT,
                timestamp = (decodedResult[4] as Uint256).value,
                issuedBy = (decodedResult[5] as Address).value,
                isActive = (decodedResult[6] as Bool).value,
                encryptedKey = (decodedResult[7] as Utf8String).value,
                version = (decodedResult[8] as Uint256).value
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting health record", e)
            null
        }
    }
    
    /**
     * Check if an address has access to a specific record.
     * Read-only operation.
     */
    suspend fun hasAccess(
        patientAddress: String,
        requesterAddress: String,
        recordId: BigInteger
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "hasAccess",
                listOf(
                    Address(patientAddress),
                    Address(requesterAddress),
                    Uint256(recordId)
                ),
                listOf(object : TypeReference<Bool>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error checking access: ${response.error.message}")
                return@withContext false
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext false
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.isEmpty()) {
                return@withContext false
            }
            
            (decodedResult[0] as Bool).value
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking access", e)
            false
        }
    }
    
    /**
     * Get access grant details between patient and provider.
     * Read-only operation.
     */
    suspend fun getAccessGrant(
        patientAddress: String,
        granteeAddress: String
    ): AccessGrant? = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getAccessGrant",
                listOf(
                    Address(patientAddress),
                    Address(granteeAddress)
                ),
                listOf(
                    object : TypeReference<Address>() {},
                    object : TypeReference<DynamicArray<Uint256>>() {},
                    object : TypeReference<Uint256>() {},
                    object : TypeReference<Bool>() {}
                )
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting access grant: ${response.error.message}")
                return@withContext null
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext null
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.size < 4) {
                return@withContext null
            }
            
            @Suppress("UNCHECKED_CAST")
            val recordIds = (decodedResult[1] as DynamicArray<Uint256>).value.map { it.value }
            
            AccessGrant(
                grantee = (decodedResult[0] as Address).value,
                recordIds = recordIds,
                expiryTime = (decodedResult[2] as Uint256).value,
                isActive = (decodedResult[3] as Bool).value
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access grant", e)
            null
        }
    }
    
    /**
     * Send a transaction using the user's connected wallet.
     * This prompts the user to sign the transaction in their wallet app.
     */
    private suspend fun sendTransaction(
        from: String,
        to: String,
        data: String,
        value: String = "0x0"
    ): String = withTimeout(120000) { // 2 minute timeout for user to sign
        suspendCancellableCoroutine { continuation ->
            var isResumed = false
            
            try {
                // Get current chain ID
                val chainId = WalletManager.getChainId() ?: "1"
                
                // Estimate gas (500,000 gas units)
                val gasLimit = "0x${BigInteger.valueOf(500000).toString(16)}"
                val gasPrice = "0x${BigInteger.valueOf(20000000000).toString(16)}" // 20 Gwei
                
                Log.d(TAG, "Preparing transaction:")
                Log.d(TAG, "  From: $from")
                Log.d(TAG, "  To: $to")
                Log.d(TAG, "  Data: ${data.take(66)}...") // Log first part of data
                Log.d(TAG, "  Chain ID: $chainId")
                Log.d(TAG, "  Gas Limit: $gasLimit")
                Log.d(TAG, "  Gas Price: $gasPrice")
                
                // Create transaction parameters as JSON array
                val transactionParams = """
                    [{
                        "from": "$from",
                        "to": "$to",
                        "data": "$data",
                        "value": "$value",
                        "gas": "$gasLimit",
                        "gasPrice": "$gasPrice"
                    }]
                """.trimIndent()
                
                Log.d(TAG, "Transaction params: $transactionParams")
                
                // Create request for eth_sendTransaction
                val request = Request(
                    method = "eth_sendTransaction",
                    params = transactionParams
                )
                
                Log.d(TAG, "========================================")
                Log.d(TAG, "SENDING TRANSACTION REQUEST TO WALLET")
                Log.d(TAG, "========================================")
                Log.i(TAG, "📱 User must open their wallet app to see this request!")
                Log.i(TAG, "The transaction will NOT auto-appear - manual action required")
                
                // Send transaction via AppKit
                // This will send the request to the wallet
                // User needs to open their wallet app manually to approve
                AppKit.request(
                    request = request,
                    onSuccess = {
                        Log.d(TAG, "✓ Transaction request sent successfully to WalletConnect")
                        Log.i(TAG, "========================================")
                        Log.i(TAG, "📱 ACTION REQUIRED: OPEN YOUR WALLET APP NOW!")
                        Log.i(TAG, "========================================")
                        Log.i(TAG, "Look for a pending transaction request in your wallet")
                        Log.i(TAG, "App: MetaMask, Trust Wallet, Rainbow, etc.")
                        
                        // Generate a pending transaction identifier
                        // The actual transaction will be processed when user approves in wallet
                        val pendingTxId = "pending_${System.currentTimeMillis()}"
                        
                        if (!isResumed) {
                            isResumed = true
                            Log.d(TAG, "Resuming with pending transaction ID: $pendingTxId")
                            continuation.resume(pendingTxId)
                        }
                    } as () -> Unit,
                    onError = { error: Throwable ->
                        if (!isResumed) {
                            isResumed = true
                            Log.e(TAG, "Transaction request failed", error)
                            
                            // Handle the redirect error specifically
                            val errorMessage = if (error.message?.contains("redirect") == true) {
                                "Please open your wallet app manually to approve the transaction"
                            } else {
                                error.message ?: "Unknown error"
                            }
                            
                            continuation.resumeWithException(
                                Exception("Transaction request failed: $errorMessage", error)
                            )
                        } else {
                            Log.w(TAG, "Continuation already resumed, ignoring error: ${error.message}")
                        }
                    }
                )
                
                // Handle cancellation
                continuation.invokeOnCancellation {
                    Log.w(TAG, "Transaction request cancelled")
                }
                
            } catch (e: Exception) {
                if (!isResumed) {
                    isResumed = true
                    Log.e(TAG, "Error preparing transaction", e)
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * Get the user's wallet address.
     * Convenience method delegating to WalletManager.
     */
    fun getUserAddress(): String? = WalletManager.getAddress()
    
    /**
     * Check if user has a connected wallet.
     */
    fun isWalletConnected(): Boolean = WalletManager.getAddress() != null
}
