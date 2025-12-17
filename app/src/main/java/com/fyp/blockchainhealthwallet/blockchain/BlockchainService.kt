package com.fyp.blockchainhealthwallet.blockchain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.models.request.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    
    // Store context for opening wallet
    private var appContext: Context? = null
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "BlockchainService initialized with context")
    }
    
    // ============================================
    // CONTRACT CONFIGURATION - SEPOLIA TESTNET
    // ============================================
    // HealthWallet V1 address = 0xed41D59378f36b04567DAB79077d8057eA3E70D6
    // HealthWallet V2 address = 0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B
    private const val CONTRACT_ADDRESS = "0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B"
    
    // Sepolia RPC endpoints - using multiple public endpoints for reliability
    private const val RPC_URL = "https://ethereum-sepolia-rpc.publicnode.com"
    private const val RPC_URL_FALLBACK = "https://rpc.sepolia.org"
    private const val RPC_URL_FALLBACK2 = "https://rpc2.sepolia.org"
    
    // Create OkHttpClient with timeout settings
    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    // Initialize Web3j for read operations with primary endpoint
    private val web3j: Web3j by lazy {
        Web3j.build(HttpService(RPC_URL, okHttpClient))
    }
    
    // Fallback Web3j instances
    private val web3jFallback: Web3j by lazy {
        Web3j.build(HttpService(RPC_URL_FALLBACK, okHttpClient))
    }
    
    private val web3jFallback2: Web3j by lazy {
        Web3j.build(HttpService(RPC_URL_FALLBACK2, okHttpClient))
    }
    
    // ============================================
    // ENUMS - Matching HealthWalletV2 Contract
    // ============================================
    
    // RecordType enum (for categorizing health data)
    enum class RecordType(val value: Int) {
        PERSONAL_INFO(0),
        MEDICATION(1),
        VACCINATION(2),
        MEDICAL_REPORT(3)
    }
    
    // ReportType enum (for medical report subcategories)
    enum class ReportType(val value: Int) {
        LAB_RESULT(0),
        DOCTOR_NOTE(1),
        PRESCRIPTION(2),
        IMAGING(3),
        PATHOLOGY(4),
        CONSULTATION(5),
        DISCHARGE_SUMMARY(6),
        OTHER(7)
    }
    
    // RecipientType enum (for data sharing recipients)
    enum class RecipientType(val value: Int) {
        DOCTOR(0),
        HOSPITAL(1),
        CLINIC(2),
        INSURANCE_COMPANY(3),
        PHARMACY(4),
        LABORATORY(5),
        OTHER(6)
    }
    
    // AccessLevel enum (for share permissions)
    enum class AccessLevel(val value: Int) {
        VIEW_ONLY(0),
        FULL_ACCESS(1),
        EMERGENCY_ONLY(2)
    }
    
    // ShareStatus enum (for share record status)
    enum class ShareStatus(val value: Int) {
        ACTIVE(0),
        EXPIRED(1),
        REVOKED(2)
    }
    
    // DataCategory enum (for specifying which data to share)
    enum class DataCategory(val value: Int) {
        PERSONAL_INFO(0),
        MEDICATION_RECORDS(1),
        VACCINATION_RECORDS(2),
        MEDICAL_REPORTS(3),
        ALL_DATA(4)
    }
    
    // ============================================
    // DATA CLASSES - Matching HealthWalletV2 Contract Structs
    // ============================================
    // All sensitive data is encrypted and stored on IPFS
    // Only metadata and IPFS hashes stored on-chain
    
    /**
     * PersonalInfoRef - Reference to encrypted personal information on IPFS
     */
    data class PersonalInfoRef(
        val encryptedDataIpfsHash: String,
        val publicKeyHash: String,  // bytes32 as hex string
        val createdAt: BigInteger,
        val lastUpdated: BigInteger,
        val exists: Boolean
    )
    
    /**
     * MedicationRecordRef - Reference to encrypted medication data on IPFS
     */
    data class MedicationRecordRef(
        val id: BigInteger,
        val encryptedDataIpfsHash: String,
        val isActive: Boolean,
        val startDate: BigInteger,
        val endDate: BigInteger,
        val createdAt: BigInteger
    )
    
    /**
     * VaccinationRecordRef - Reference to encrypted vaccination data on IPFS
     */
    data class VaccinationRecordRef(
        val id: BigInteger,
        val encryptedDataIpfsHash: String,
        val encryptedCertificateIpfsHash: String,
        val vaccinationDate: BigInteger,
        val createdAt: BigInteger
    )
    
    /**
     * MedicalReportRef - Reference to encrypted medical report on IPFS
     */
    data class MedicalReportRef(
        val id: BigInteger,
        val encryptedDataIpfsHash: String,
        val encryptedFileIpfsHash: String,
        val reportType: ReportType,
        val hasFile: Boolean,
        val reportDate: BigInteger,
        val createdAt: BigInteger
    )
    
    /**
     * ShareRecord - Data sharing record with cryptographic isolation per category
     */
    data class ShareRecord(
        val id: BigInteger,
        val recipientAddress: String,
        val recipientNameHash: String,  // bytes32 as hex string
        val encryptedRecipientDataIpfsHash: String,
        val recipientType: RecipientType,
        val sharedDataCategory: DataCategory,
        val shareDate: BigInteger,
        val expiryDate: BigInteger,
        val accessLevel: AccessLevel,
        val status: ShareStatus,
        val encryptedCategoryKey: String
    )
    
    /**
     * AccessLog - Immutable audit trail of data access
     */
    data class AccessLog(
        val id: BigInteger,
        val accessorAddress: String,
        val encryptedDetailsIpfsHash: String,
        val accessedCategory: DataCategory,
        val accessTime: BigInteger,
        val dataIntegrityHash: String  // bytes32 as hex string
    )
    
    /**
     * Helper function to execute eth_call with automatic fallback to alternative RPCs
     */
    private suspend fun executeEthCallWithFallback(
        encodedFunction: String,
        contractAddress: String,
        fromAddress: String? = null  // Optional: set msg.sender for the call
    ): org.web3j.protocol.core.methods.response.EthCall = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            Triple(web3j, RPC_URL, "Primary"),
            Triple(web3jFallback, RPC_URL_FALLBACK, "Fallback 1"),
            Triple(web3jFallback2, RPC_URL_FALLBACK2, "Fallback 2")
        )
        
        var lastException: Exception? = null
        
        for ((client, url, name) in endpoints) {
            try {
                Log.d(TAG, "Trying $name RPC: $url")
                if (fromAddress != null) {
                    Log.d(TAG, "Using from address (msg.sender): $fromAddress")
                } else {
                    Log.d(TAG, "Using from address (msg.sender): 0x0000000000000000000000000000000000000000")
                }
                val startTime = System.currentTimeMillis()
                
                val response = client.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        fromAddress,  // This sets msg.sender in the contract
                        contractAddress,
                        encodedFunction
                    ),
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                ).send()
                
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ $name RPC responded in ${elapsed}ms")
                
                return@withContext response
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Log.w(TAG, "❌ $name RPC failed: $errorMsg")
                lastException = e
                // Continue to next endpoint
            }
        }
        
        // All endpoints failed
        throw lastException ?: Exception("All RPC endpoints failed")
    }
    
    // ============================================
    // PERSONAL INFO FUNCTIONS - HealthWalletV2
    // ============================================
    
    /**
     * Set or update personal information (encrypted and stored on IPFS)
     * @param encryptedDataIpfsHash IPFS hash of encrypted personal data JSON
     * @param publicKeyHash Hash of user's public encryption key (bytes32)
     * @return Transaction hash
     */
    suspend fun setPersonalInfo(
        encryptedDataIpfsHash: String,
        publicKeyHash: String  // Must be 32 bytes hex string (0x + 64 chars)
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Setting personal info for user: $userAddress")
        
        // Convert hex string to bytes32
        val keyHashBytes = Numeric.hexStringToByteArray(publicKeyHash)
        require(keyHashBytes.size == 32) { "publicKeyHash must be 32 bytes" }
        
        val function = org.web3j.abi.datatypes.Function(
            "setPersonalInfo",
            listOf(
                Utf8String(encryptedDataIpfsHash),
                org.web3j.abi.datatypes.generated.Bytes32(keyHashBytes)
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
     * Get personal info reference for a user (read-only)
     * @param userAddress Address of the user
     * @return PersonalInfoRef or null if not found
     */
    suspend fun getPersonalInfoRef(userAddress: String): PersonalInfoRef? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "BLOCKCHAIN: getPersonalInfoRef")
            Log.d(TAG, "========================================")
            Log.d(TAG, "Contract: $CONTRACT_ADDRESS")
            Log.d(TAG, "User: $userAddress")
            Log.d(TAG, "RPC: $RPC_URL")
            
            val function = org.web3j.abi.datatypes.Function(
                "getPersonalInfoRef",
                listOf(Address(userAddress)),
                listOf(
                    object : TypeReference<Utf8String>() {},  // encryptedDataIpfsHash
                    object : TypeReference<org.web3j.abi.datatypes.generated.Bytes32>() {},  // publicKeyHash
                    object : TypeReference<Uint256>() {},  // createdAt
                    object : TypeReference<Uint256>() {},  // lastUpdated
                    object : TypeReference<Bool>() {}  // exists
                )
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            Log.d(TAG, "Encoded function: $encodedFunction")
            
            Log.d(TAG, "Calling blockchain with automatic fallback...")
            Log.d(TAG, "Using msg.sender = $userAddress (to pass access control)")
            
            val response = executeEthCallWithFallback(
                encodedFunction = encodedFunction,
                contractAddress = CONTRACT_ADDRESS,
                fromAddress = userAddress  // Set msg.sender to user's address
            )
            
            if (response.hasError()) {
                Log.e(TAG, "❌ RPC Error: ${response.error.message}")
                Log.e(TAG, "Error code: ${response.error.code}")
                Log.e(TAG, "Error data: ${response.error.data}")
                return@withContext null
            }
            
            val result = response.value
            Log.d(TAG, "RPC Response value: $result")
            Log.d(TAG, "Response is null or empty: ${result.isNullOrEmpty()}")
            
            if (result.isNullOrEmpty() || result == "0x") {
                Log.w(TAG, "⚠️ Empty response from blockchain")
                return@withContext null
            }
            
            Log.d(TAG, "Decoding response (manual parsing due to tuple wrapper)...")
            
            // Manually decode the tuple fields from the hex response
            // Response format: 
            // 0-64: offset to tuple (32 bytes)
            // 64-128: offset to string (32 bytes)
            // 128-192: bytes32 publicKeyHash (32 bytes)
            // 192-256: uint256 createdAt (32 bytes)
            // 256-320: uint256 lastUpdated (32 bytes)
            // 320-384: bool exists (32 bytes) <-- HERE!
            // 384+: string length + data
            
            val cleanHex = result.substring(2) // Remove 0x prefix
            
            // Parse exists flag at position 320-384
            val existsHex = cleanHex.substring(320, 384)
            val exists = existsHex.trim('0') == "1"
            
            Log.d(TAG, "exists hex: $existsHex")
            Log.d(TAG, "exists flag: $exists")
            
            Log.d(TAG, "exists flag: $exists")
            
            if (!exists) {
                Log.w(TAG, "⚠️ exists=false, no data stored")
                return@withContext null
            }
            
            // Manually parse the IPFS hash string from hex
            // String starts at position 384 (after the 5 fixed fields)
            // 384-448: string length (32 bytes)
            // 448+: string content in hex
            
            val stringLengthHex = cleanHex.substring(384, 448)
            val stringLength = stringLengthHex.toLong(16).toInt() * 2 // Convert to hex char count
            val stringDataStart = 448
            val stringDataEnd = stringDataStart + stringLength
            
            if (stringDataEnd > cleanHex.length) {
                Log.e(TAG, "❌ String data out of bounds")
                return@withContext null
            }
            
            val stringHex = cleanHex.substring(stringDataStart, stringDataEnd)
            val ipfsHash = stringHex.chunked(2)
                .map { it.toInt(16).toChar() }
                .joinToString("")
            
            Log.d(TAG, "Parsed IPFS hash: $ipfsHash")
            
            // Now decode using Web3j for the numeric fields only
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.size < 5) {
                Log.e(TAG, "❌ Invalid decoded result size: ${decodedResult.size}")
                return@withContext null
            }
            
            // Extract numeric fields (skip string at index 0)
            val publicKeyHash = Numeric.toHexString((decodedResult[1] as org.web3j.abi.datatypes.generated.Bytes32).value)
            val createdAt = (decodedResult[2] as Uint256).value
            val lastUpdated = (decodedResult[3] as Uint256).value
            
            Log.d(TAG, "✅ Successfully decoded PersonalInfoRef:")
            Log.d(TAG, "  - IPFS Hash: $ipfsHash")
            Log.d(TAG, "  - Public Key Hash: $publicKeyHash")
            Log.d(TAG, "  - Created At: $createdAt")
            Log.d(TAG, "  - Last Updated: $lastUpdated")
            Log.d(TAG, "========================================")
            
            PersonalInfoRef(
                encryptedDataIpfsHash = ipfsHash,
                publicKeyHash = publicKeyHash,
                createdAt = createdAt,
                lastUpdated = lastUpdated,
                exists = exists
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting personal info", e)
            null
        }
    }
    
    // ============================================
    // MEDICATION FUNCTIONS - HealthWalletV2
    // ============================================
    
    /**
     * Add a medication record (encrypted and stored on IPFS)
     * @return Medication ID
     */
    suspend fun addMedication(
        encryptedDataIpfsHash: String,
        isActive: Boolean,
        startDate: BigInteger,
        endDate: BigInteger
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Adding medication for user: $userAddress")
        
        val function = org.web3j.abi.datatypes.Function(
            "addMedication",
            listOf(
                Utf8String(encryptedDataIpfsHash),
                Bool(isActive),
                Uint256(startDate),
                Uint256(endDate)
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
     * Update an existing medication record
     */
    suspend fun updateMedication(
        medicationId: BigInteger,
        encryptedDataIpfsHash: String,
        isActive: Boolean,
        startDate: BigInteger,
        endDate: BigInteger
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "updateMedication",
            listOf(
                Uint256(medicationId),
                Utf8String(encryptedDataIpfsHash),
                Bool(isActive),
                Uint256(startDate),
                Uint256(endDate)
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
     * Get all medication IDs for a user (read-only)
     */
    suspend fun getMedicationIds(userAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getMedicationIds",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<DynamicArray<Uint256>>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting medication IDs: ${response.error.message}")
                return@withContext emptyList()
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext emptyList()
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.isEmpty()) {
                return@withContext emptyList()
            }
            
            @Suppress("UNCHECKED_CAST")
            val ids = (decodedResult[0] as DynamicArray<Uint256>).value
            ids.map { it.value }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting medication IDs", e)
            emptyList()
        }
    }
    
    /**
     * Get medication record reference (read-only)
     */
    suspend fun getMedicationRef(medicationId: BigInteger): MedicationRecordRef? = withContext(Dispatchers.IO) {
        try {
            val fromAddress = WalletManager.getAddress()

            val function = org.web3j.abi.datatypes.Function(
                "getMedicationRef",
                listOf(Uint256(medicationId)),
                listOf(
                    object : TypeReference<Uint256>() {},  // id
                    object : TypeReference<Utf8String>() {},  // encryptedDataIpfsHash
                    object : TypeReference<Bool>() {},  // isActive
                    object : TypeReference<Uint256>() {},  // startDate
                    object : TypeReference<Uint256>() {},  // endDate
                    object : TypeReference<Uint256>() {}  // createdAt
                )
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    fromAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting medication ref: ${response.error.message}")
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
            
            if (decodedResult.size < 6) {
                return@withContext null
            }
            
            MedicationRecordRef(
                id = (decodedResult[0] as Uint256).value,
                encryptedDataIpfsHash = (decodedResult[1] as Utf8String).value,
                isActive = (decodedResult[2] as Bool).value,
                startDate = (decodedResult[3] as Uint256).value,
                endDate = (decodedResult[4] as Uint256).value,
                createdAt = (decodedResult[5] as Uint256).value
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting medication ref", e)
            null
        }
    }
    
    /**
     * DUMMY TEST METHOD: Test HealthWalletV2 by calling setPersonalInfo with dummy data
     */
    suspend fun sendDummyTestRecord(): String {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🧪 TESTING: Sending dummy personal info to HealthWalletV2...")
        Log.d(TAG, "========================================")
        
        // FORCE WalletManager to refresh its state
        Log.d(TAG, "🔍 Forcing session state refresh...")
        WalletManager.forceRefreshSessionState()
        
        withContext(Dispatchers.Main) {
            delay(500)
        }
        
        if (!WalletManager.isConnected()) {
            Log.e(TAG, "❌ NOT CONNECTED!")
            throw Exception("Wallet not connected. Please connect your wallet first.")
        }
        
        val account = try {
            AppKit.getAccount()
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Cannot get account from AppKit", e)
            throw Exception("WalletConnect session not available. Please reconnect your wallet.")
        }
        
        if (account == null) {
            Log.e(TAG, "❌ CRITICAL: AppKit.getAccount() returned null!")
            throw Exception("No wallet account available. Please connect your wallet first.")
        }
        
        val userAddress = account.address
        Log.d(TAG, "✓ Address: $userAddress")
        
        val selectedChain = try {
            AppKit.getSelectedChain()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get selected chain: ${e.message}")
            null
        }
        
        val chainId = selectedChain?.chainReference ?: WalletManager.getChainId()
        Log.d(TAG, "✓ Chain ID: $chainId")
        
        if (chainId == null) {
            throw Exception("No chain ID available - try reconnecting wallet.")
        }
        
        if (chainId != "11155111") {
            Log.e(TAG, "⚠️ WRONG NETWORK! Current: $chainId, Expected: 11155111 (Sepolia)")
            throw Exception("Wrong network! Please switch to Sepolia testnet. Current: $chainId")
        }
        
        Log.d(TAG, "✓ On Sepolia network")
        
        val nonce = try {
            withContext(Dispatchers.IO) {
                val ethGetTransactionCount = web3j.ethGetTransactionCount(
                    userAddress,
                    org.web3j.protocol.core.DefaultBlockParameterName.PENDING
                ).send()
                
                val nonceValue = ethGetTransactionCount.transactionCount
                Log.d(TAG, "✓ Nonce: $nonceValue")
                "0x${nonceValue.toString(16)}"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch nonce: ${e.message}")
            null
        }
        
        // Dummy data for HealthWalletV2 setPersonalInfo
        val dummyIpfsHash = "QmTestPersonalInfo123456789"
        val dummyPublicKeyHash = "0x" + "1234567890abcdef".repeat(4)  // 32 bytes hex
        
        Log.d(TAG, "🧪 Dummy IPFS hash: $dummyIpfsHash")
        Log.d(TAG, "🧪 Dummy public key hash: $dummyPublicKeyHash")
        
        // Encode setPersonalInfo function call
        val keyHashBytes = Numeric.hexStringToByteArray(dummyPublicKeyHash)
        val function = org.web3j.abi.datatypes.Function(
            "setPersonalInfo",
            listOf(
                Utf8String(dummyIpfsHash),
                org.web3j.abi.datatypes.generated.Bytes32(keyHashBytes)
            ),
            emptyList()
        )
        
        val encodedFunction = FunctionEncoder.encode(function)
        Log.d(TAG, "🧪 Encoded data: ${encodedFunction.take(66)}...")
        
        return sendTransaction(
            from = userAddress,
            to = CONTRACT_ADDRESS,
            data = encodedFunction,
            value = "0x0",
            nonce = nonce
        )
    }
    
    // ============================================
    // VACCINATION FUNCTIONS - HealthWalletV2
    // ============================================
    
    /**
     * Add a vaccination record (encrypted and stored on IPFS)
     * @return Vaccination ID
     */
    suspend fun addVaccination(
        encryptedDataIpfsHash: String,
        encryptedCertificateIpfsHash: String,
        vaccinationDate: BigInteger
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Adding vaccination for user: $userAddress")
        
        val function = org.web3j.abi.datatypes.Function(
            "addVaccination",
            listOf(
                Utf8String(encryptedDataIpfsHash),
                Utf8String(encryptedCertificateIpfsHash),
                Uint256(vaccinationDate)
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
     * Update an existing vaccination record
     */
    suspend fun updateVaccination(
        vaccinationId: BigInteger,
        encryptedDataIpfsHash: String,
        encryptedCertificateIpfsHash: String,
        vaccinationDate: BigInteger
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "updateVaccination",
            listOf(
                Uint256(vaccinationId),
                Utf8String(encryptedDataIpfsHash),
                Utf8String(encryptedCertificateIpfsHash),
                Uint256(vaccinationDate)
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
     * Get all vaccination IDs for a user (read-only)
     */
    suspend fun getVaccinationIds(userAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getVaccinationIds",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<DynamicArray<Uint256>>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting vaccination IDs: ${response.error.message}")
                return@withContext emptyList()
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext emptyList()
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.isEmpty()) {
                return@withContext emptyList()
            }
            
            @Suppress("UNCHECKED_CAST")
            val ids = (decodedResult[0] as DynamicArray<Uint256>).value
            ids.map { it.value }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting vaccination IDs", e)
            emptyList()
        }
    }
    
    /**
     * Get vaccination record reference (read-only)
     */
    suspend fun getVaccinationRef(vaccinationId: BigInteger): VaccinationRecordRef? = withContext(Dispatchers.IO) {
        try {
            val fromAddress = WalletManager.getAddress()

            val function = org.web3j.abi.datatypes.Function(
                "getVaccinationRef",
                listOf(Uint256(vaccinationId)),
                listOf(
                    object : TypeReference<Uint256>() {},  // id
                    object : TypeReference<Utf8String>() {},  // encryptedDataIpfsHash
                    object : TypeReference<Utf8String>() {},  // encryptedCertificateIpfsHash
                    object : TypeReference<Uint256>() {},  // vaccinationDate
                    object : TypeReference<Uint256>() {}  // createdAt
                )
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    fromAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting vaccination ref: ${response.error.message}")
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
            
            if (decodedResult.size < 5) {
                return@withContext null
            }
            
            VaccinationRecordRef(
                id = (decodedResult[0] as Uint256).value,
                encryptedDataIpfsHash = (decodedResult[1] as Utf8String).value,
                encryptedCertificateIpfsHash = (decodedResult[2] as Utf8String).value,
                vaccinationDate = (decodedResult[3] as Uint256).value,
                createdAt = (decodedResult[4] as Uint256).value
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting vaccination ref", e)
            null
        }
    }
    
    // ============================================
    // MEDICAL REPORT FUNCTIONS - HealthWalletV2
    // ============================================
    
    /**
     * Add a medical report (encrypted and stored on IPFS)
     * @return Report ID
     */
    suspend fun addReport(
        encryptedDataIpfsHash: String,
        encryptedFileIpfsHash: String,
        reportType: ReportType,
        hasFile: Boolean,
        reportDate: BigInteger
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Adding medical report for user: $userAddress")
        
        val function = org.web3j.abi.datatypes.Function(
            "addReport",
            listOf(
                Utf8String(encryptedDataIpfsHash),
                Utf8String(encryptedFileIpfsHash),
                Uint8(reportType.value.toLong()),  // Enum encoded as uint8
                Bool(hasFile),
                Uint256(reportDate)
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
     * Update an existing medical report
     */
    suspend fun updateReport(
        reportId: BigInteger,
        encryptedDataIpfsHash: String,
        encryptedFileIpfsHash: String,
        reportType: ReportType,
        hasFile: Boolean,
        reportDate: BigInteger
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "updateReport",
            listOf(
                Uint256(reportId),
                Utf8String(encryptedDataIpfsHash),
                Utf8String(encryptedFileIpfsHash),
                Uint8(reportType.value.toLong()),
                Bool(hasFile),
                Uint256(reportDate)
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
     * Get all report IDs for a user (read-only)
     *
     * Note: If contract's getReportIds has access control, this will fail.
     * Workaround: Fetch from ReportAdded events instead.
     */
    suspend fun getReportIds(userAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Getting report IDs for: $userAddress")

            // Try direct contract call first
            val function = org.web3j.abi.datatypes.Function(
                "getReportIds",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<DynamicArray<Uint256>>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            Log.d(TAG, "📤 Encoded function: $encodedFunction")

            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "❌ Error getting report IDs: ${response.error.message}")
                Log.e(TAG, "Error code: ${response.error.code}, Data: ${response.error.data}")

                // If access control error, try fetching from events
                if (response.error.message.contains("No access") || response.error.message.contains("revert")) {
                    Log.w(TAG, "⚠️ Access control blocking read, trying events...")
                    return@withContext getReportIdsFromEvents(userAddress)
                }

                return@withContext emptyList()
            }
            
            val result = response.value
            Log.d(TAG, "📥 Response value: $result")

            if (result.isNullOrEmpty() || result == "0x") {
                Log.w(TAG, "⚠️ Empty response - trying events...")
                return@withContext getReportIdsFromEvents(userAddress)
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.isEmpty()) {
                Log.w(TAG, "⚠️ Decoded result is empty - trying events...")
                return@withContext getReportIdsFromEvents(userAddress)
            }
            
            @Suppress("UNCHECKED_CAST")
            val ids = (decodedResult[0] as DynamicArray<Uint256>).value
            val reportIds = ids.map { it.value }
            Log.d(TAG, "✅ Found ${reportIds.size} report IDs: $reportIds")
            reportIds
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting report IDs, trying events...", e)
            getReportIdsFromEvents(userAddress)
        }
    }

    /**
     * Get report IDs by scanning ReportAdded events
     * This works even if getReportIds has access control
     */
    private suspend fun getReportIdsFromEvents(userAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📡 Fetching reports from ReportAdded events for $userAddress")

            // Create event filter for ReportAdded(address indexed patient, uint256 indexed reportId, ...)
            val event = org.web3j.abi.datatypes.Event(
                "ReportAdded",
                listOf(
                    object : TypeReference<Address>(true) {},  // indexed patient
                    object : TypeReference<Uint256>(true) {},  // indexed reportId
                    object : TypeReference<Uint8>() {},        // reportType
                    object : TypeReference<Uint256>() {}       // timestamp
                )
            )

            val eventFilter = org.web3j.protocol.core.methods.request.EthFilter(
                org.web3j.protocol.core.DefaultBlockParameterName.EARLIEST,
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST,
                CONTRACT_ADDRESS
            )

            // Add topic filter for patient address
            val eventSignature = org.web3j.abi.EventEncoder.encode(event)
            // Manually encode address as topic (32 bytes, padded with leading zeros)
            val addressWithout0x = userAddress.removePrefix("0x").lowercase()
            val addressTopic = "0x" + "0".repeat(24) + addressWithout0x
            eventFilter.addSingleTopic(eventSignature)
            eventFilter.addSingleTopic(addressTopic)

            val logs = web3j.ethGetLogs(eventFilter).send()

            if (logs.hasError()) {
                Log.e(TAG, "❌ Error fetching events: ${logs.error.message}")
                return@withContext emptyList()
            }

            val reportIds = logs.logs.mapNotNull { logResult ->
                try {
                    val log = logResult as org.web3j.protocol.core.methods.response.EthLog.LogObject
                    // reportId is the second indexed parameter (topic[2])
                    if (log.topics.size >= 3) {
                        val reportIdHex = log.topics[2]
                        BigInteger(reportIdHex.substring(2), 16)
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing log", e)
                    null
                }
            }.distinct().sorted()

            Log.d(TAG, "✅ Found ${reportIds.size} report IDs from events: $reportIds")
            reportIds

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching report IDs from events", e)
            emptyList()
        }
    }
    
    /**
     * Get medical report reference (read-only)
     */
    suspend fun getReportRef(reportId: BigInteger): MedicalReportRef? = withContext(Dispatchers.IO) {
        try {
            // Get current user address to pass as 'from' for access control
            val userAddress = getUserAddress()

            // Create the function call
            val function = org.web3j.abi.datatypes.Function(
                "getReportRef",
                listOf(Uint256(reportId)),
                emptyList() // Don't specify return type, we'll decode manually
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,  // Pass user address to satisfy access control
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting report ref: ${response.error.message}")
                return@withContext null
            }
            
            val result = response.value
            Log.d(TAG, "📦 Raw response for report $reportId: $result")
            Log.d(TAG, "📐 Response length: ${result?.length}")

            if (result.isNullOrEmpty() || result == "0x") {
                Log.w(TAG, "Empty response for report $reportId")
                return@withContext null
            }
            
            // Manually decode the struct
            // Struct layout:
            // 0-31: offset to struct data (0x20 = 32)
            // 32-63: id (uint256)
            // 64-95: offset to encryptedDataIpfsHash (dynamic)
            // 96-127: offset to encryptedFileIpfsHash (dynamic)
            // 128-159: reportType (uint8 padded to 32 bytes)
            // 160-191: hasFile (bool padded to 32 bytes)
            // 192-223: reportDate (uint256)
            // 224-255: createdAt (uint256)
            // Then the actual string data at their respective offsets
            
            val hex = result.removePrefix("0x")
            
            // Skip the first 32 bytes (offset pointer to struct)
            val structData = hex.substring(64)
            
            // Extract fields (each is 64 hex chars = 32 bytes)
            val idHex = structData.substring(0, 64)
            val dataIpfsOffsetHex = structData.substring(64, 128)
            val fileIpfsOffsetHex = structData.substring(128, 192)
            val reportTypeHex = structData.substring(192, 256)
            val hasFileHex = structData.substring(256, 320)
            val reportDateHex = structData.substring(320, 384)
            val createdAtHex = structData.substring(384, 448)
            
            // Parse static values
            val id = BigInteger(idHex, 16)
            val reportTypeValue = BigInteger(reportTypeHex, 16).toInt()
            val hasFile = BigInteger(hasFileHex, 16) != BigInteger.ZERO
            val reportDate = BigInteger(reportDateHex, 16)
            val createdAt = BigInteger(createdAtHex, 16)
            
            // Parse dynamic strings
            val dataIpfsOffset = BigInteger(dataIpfsOffsetHex, 16).toInt() * 2 // Convert to hex chars
            val fileIpfsOffset = BigInteger(fileIpfsOffsetHex, 16).toInt() * 2
            
            // String format: 32 bytes length, then data
            val dataIpfsLengthHex = structData.substring(dataIpfsOffset, dataIpfsOffset + 64)
            val dataIpfsLength = BigInteger(dataIpfsLengthHex, 16).toInt() * 2 // Hex chars
            val dataIpfsHex = structData.substring(dataIpfsOffset + 64, dataIpfsOffset + 64 + dataIpfsLength)
            val encryptedDataIpfsHash = String(dataIpfsHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            
            val fileIpfsLengthHex = structData.substring(fileIpfsOffset, fileIpfsOffset + 64)
            val fileIpfsLength = BigInteger(fileIpfsLengthHex, 16).toInt() * 2
            val fileIpfsHex = structData.substring(fileIpfsOffset + 64, fileIpfsOffset + 64 + fileIpfsLength)
            val encryptedFileIpfsHash = String(fileIpfsHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            
            val reportType = ReportType.values().getOrNull(reportTypeValue) ?: ReportType.OTHER
            
            Log.d(TAG, "✅ Decoded report: id=$id, type=$reportType, hasFile=$hasFile")
            
            MedicalReportRef(
                id = id,
                encryptedDataIpfsHash = encryptedDataIpfsHash,
                encryptedFileIpfsHash = encryptedFileIpfsHash,
                reportType = reportType,
                hasFile = hasFile,
                reportDate = reportDate,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting report ref", e)
            null
        }
    }
    
    // ============================================
    // DATA SHARING FUNCTIONS - HealthWalletV2
    // ============================================
    
    /**
     * Share data with a recipient (healthcare provider, hospital, etc.)
     * Each data category uses a different encryption key for cryptographic isolation
     * @return Share ID
     */
    suspend fun shareData(
        recipientAddress: String,
        recipientNameHash: String,  // bytes32 hash of recipient name
        encryptedRecipientDataIpfsHash: String,
        recipientType: RecipientType,
        dataCategory: DataCategory,
        expiryDate: BigInteger,  // Unix timestamp
        accessLevel: AccessLevel,
        encryptedCategoryKey: String  // Category-specific key encrypted with recipient's public key
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Sharing data with: $recipientAddress")
        
        // Convert hex string to bytes32
        val nameHashBytes = Numeric.hexStringToByteArray(recipientNameHash)
        require(nameHashBytes.size == 32) { "recipientNameHash must be 32 bytes" }
        
        val function = org.web3j.abi.datatypes.Function(
            "shareData",
            listOf(
                Address(recipientAddress),
                org.web3j.abi.datatypes.generated.Bytes32(nameHashBytes),
                Utf8String(encryptedRecipientDataIpfsHash),
                Uint8(recipientType.value.toLong()),  // Enum as uint8
                Uint8(dataCategory.value.toLong()),  // Enum as uint8
                Uint256(expiryDate),
                Uint8(accessLevel.value.toLong()),  // Enum as uint8
                Utf8String(encryptedCategoryKey)
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
     * Revoke a data share
     */
    suspend fun revokeShare(shareId: BigInteger): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val function = org.web3j.abi.datatypes.Function(
            "revokeShare",
            listOf(Uint256(shareId)),
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
     * Get all share record IDs for a user (read-only)
     */
    suspend fun getShareIds(userAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== getShareIds called ===")
            Log.d(TAG, "User Address: $userAddress")
            Log.d(TAG, "Contract Address: $CONTRACT_ADDRESS")
            Log.d(TAG, "RPC URL: $RPC_URL")

            val function = org.web3j.abi.datatypes.Function(
                "getShareIds",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<DynamicArray<Uint256>>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            Log.d(TAG, "Encoded function data: $encodedFunction")

            // IMPORTANT: Set 'from' address to match the user being queried
            // This allows the contract's "msg.sender == _user" check to pass
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,  // Set from address to user's address
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "❌ RPC Error getting share IDs: ${response.error.message}")
                Log.e(TAG, "Error code: ${response.error.code}")
                Log.e(TAG, "Error data: ${response.error.data}")
                return@withContext emptyList()
            }
            
            val result = response.value
            Log.d(TAG, "Raw response value: $result")

            if (result.isNullOrEmpty() || result == "0x") {
                Log.w(TAG, "⚠️ Empty response from blockchain - no shares found")
                return@withContext emptyList()
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            Log.d(TAG, "Decoded result size: ${decodedResult.size}")

            if (decodedResult.isEmpty()) {
                Log.w(TAG, "⚠️ Decoded result is empty")
                return@withContext emptyList()
            }
            
            @Suppress("UNCHECKED_CAST")
            val ids = (decodedResult[0] as DynamicArray<Uint256>).value
            val shareIds = ids.map { it.value }

            Log.d(TAG, "✅ Successfully retrieved ${shareIds.size} share IDs: $shareIds")
            shareIds
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception getting share IDs for $userAddress", e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Get share record details (read-only)
     * @param shareId The share ID to retrieve
     * @param callerAddress Optional: Address of the caller (for auth check). If null, uses connected wallet.
     */
    suspend fun getShareRecord(shareId: BigInteger, callerAddress: String? = null): ShareRecord? = withContext(Dispatchers.IO) {
        try {
            // Use provided address or get from wallet
            val fromAddress = callerAddress ?: WalletManager.getAddress()

            Log.d(TAG, "Getting share record $shareId (caller: $fromAddress)")

            // Minimal function definition for encoding the call
            val function = org.web3j.abi.datatypes.Function(
                "getShareRecord",
                listOf(Uint256(shareId)),
                emptyList()  // We'll manually parse the response
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            // IMPORTANT: Set 'from' address so contract auth check passes
            // Contract requires: msg.sender == owner || msg.sender == recipient
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    fromAddress,  // Set caller address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting share record: ${response.error.message}")
                return@withContext null
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                Log.w(TAG, "Empty response for share record $shareId")
                return@withContext null
            }
            
            Log.d(TAG, "Raw hex response: $result")
            Log.d(TAG, "Response length: ${result.length}")

            // Manual parsing - ShareRecord struct has dynamic strings which Web3j struggles with
            val cleanHex = result.substring(2) // Remove 0x
            Log.d(TAG, "Clean hex length: ${cleanHex.length}")

            // When Solidity returns a struct, it starts with an offset to the actual tuple data
            // Format: 0-64: offset to tuple (usually 0x20 = 32 bytes)
            // Then the actual struct fields start at that offset

            val tupleOffset = BigInteger(cleanHex.substring(0, 64), 16).toInt() * 2
            Log.d(TAG, "Tuple offset: $tupleOffset")

            // Struct fields (starting from tupleOffset):
            // 0-64: id (uint256)
            // 64-128: recipientAddress (address)
            // 128-192: recipientNameHash (bytes32)
            // 192-256: offset to string 1 (encryptedRecipientDataIpfsHash) - relative to tuple start
            // 256-320: recipientType (uint8)
            // 320-384: sharedDataCategory (uint8)
            // 384-448: shareDate (uint256)
            // 448-512: expiryDate (uint256)
            // 512-576: accessLevel (uint8)
            // 576-640: status (uint8)
            // 640-704: offset to string 2 (encryptedCategoryKey) - relative to tuple start

            val base = tupleOffset
            val id = BigInteger(cleanHex.substring(base, base + 64), 16)
            val recipientAddress = "0x" + cleanHex.substring(base + 64 + 24, base + 128) // Address is last 20 bytes
            val recipientNameHash = "0x" + cleanHex.substring(base + 128, base + 192)
            val recipientType = cleanHex.substring(base + 256, base + 320).takeLast(2).toInt(16)
            val sharedDataCategory = cleanHex.substring(base + 320, base + 384).takeLast(2).toInt(16)
            val shareDate = BigInteger(cleanHex.substring(base + 384, base + 448), 16)
            val expiryDate = BigInteger(cleanHex.substring(base + 448, base + 512), 16)
            val accessLevel = cleanHex.substring(base + 512, base + 576).takeLast(2).toInt(16)
            val status = cleanHex.substring(base + 576, base + 640).takeLast(2).toInt(16)

            // Parse first string - offset is relative to tuple start
            val string1RelativeOffset = BigInteger(cleanHex.substring(base + 192, base + 256), 16).toInt() * 2
            val string1AbsoluteOffset = base + string1RelativeOffset
            Log.d(TAG, "String1 relative offset: $string1RelativeOffset, absolute: $string1AbsoluteOffset")

            val string1LengthHex = cleanHex.substring(string1AbsoluteOffset, string1AbsoluteOffset + 64)
            val string1Length = BigInteger(string1LengthHex, 16).toInt() * 2
            Log.d(TAG, "String1 length: $string1Length chars")

            val string1Data = cleanHex.substring(string1AbsoluteOffset + 64, string1AbsoluteOffset + 64 + string1Length)
            val encryptedRecipientDataIpfsHash = string1Data.chunked(2)
                .map { it.toInt(16).toChar() }
                .joinToString("")

            // Parse second string - offset is relative to tuple start
            val string2RelativeOffset = BigInteger(cleanHex.substring(base + 640, base + 704), 16).toInt() * 2
            val string2AbsoluteOffset = base + string2RelativeOffset
            Log.d(TAG, "String2 relative offset: $string2RelativeOffset, absolute: $string2AbsoluteOffset")

            val string2LengthHex = cleanHex.substring(string2AbsoluteOffset, string2AbsoluteOffset + 64)
            val string2Length = BigInteger(string2LengthHex, 16).toInt() * 2
            Log.d(TAG, "String2 length: $string2Length chars")

            val string2Data = cleanHex.substring(string2AbsoluteOffset + 64, string2AbsoluteOffset + 64 + string2Length)
            val encryptedCategoryKey = string2Data.chunked(2)
                .map { it.toInt(16).toChar() }
                .joinToString("")

            val shareRecord = ShareRecord(
                id = id,
                recipientAddress = recipientAddress,
                recipientNameHash = recipientNameHash,
                encryptedRecipientDataIpfsHash = encryptedRecipientDataIpfsHash,
                recipientType = RecipientType.values().getOrNull(recipientType) ?: RecipientType.OTHER,
                sharedDataCategory = DataCategory.values().getOrNull(sharedDataCategory) ?: DataCategory.ALL_DATA,
                shareDate = shareDate,
                expiryDate = expiryDate,
                accessLevel = AccessLevel.values().getOrNull(accessLevel) ?: AccessLevel.VIEW_ONLY,
                status = ShareStatus.values().getOrNull(status) ?: ShareStatus.EXPIRED,
                encryptedCategoryKey = encryptedCategoryKey
            )

            Log.d(TAG, "✅ Successfully retrieved share record $shareId")
            shareRecord

        } catch (e: Exception) {
            Log.e(TAG, "Error getting share record $shareId", e)
            null
        }
    }

    /**
     * Get total number of shares in the contract
     */
    suspend fun getTotalShareCount(): BigInteger = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting total share count from contract...")

            val function = org.web3j.abi.datatypes.Function(
                "getTotalCounts",
                emptyList(),
                listOf(
                    object : TypeReference<Uint256>() {}, // medications
                    object : TypeReference<Uint256>() {}, // vaccinations
                    object : TypeReference<Uint256>() {}, // reports
                    object : TypeReference<Uint256>() {}, // shares
                    object : TypeReference<Uint256>() {}  // accessLogs
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
                Log.e(TAG, "Error getting total counts: ${response.error.message}")
                return@withContext BigInteger.ZERO
            }

            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext BigInteger.ZERO
            }

            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.size < 4) {
                return@withContext BigInteger.ZERO
            }
            
            // Return share count (4th value, index 3)
            val shareCount = (decodedResult[3] as Uint256).value
            Log.d(TAG, "Total share count: $shareCount")
            shareCount

        } catch (e: Exception) {
            Log.e(TAG, "Error getting total share count", e)
            BigInteger.ZERO
        }
    }
    
    // ============================================
    // ACCESS LOGGING FUNCTIONS - HealthWalletV2
    // ============================================
    
    /**
     * Log data access (immutable audit trail)
     * @param ownerAddress Owner of the data being accessed
     * @param encryptedDetailsIpfsHash IPFS hash of encrypted access details
     * @param accessedCategory Category of data accessed
     * @param dataIntegrityHash Hash for data integrity verification (bytes32)
     * @return Access log ID
     */
    suspend fun logDataAccess(
        ownerAddress: String,
        encryptedDetailsIpfsHash: String,
        accessedCategory: DataCategory,
        dataIntegrityHash: String  // bytes32 hex string
    ): String = withContext(Dispatchers.IO) {
        val userAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        Log.d(TAG, "Logging data access for owner: $ownerAddress")
        
        // Convert hex string to bytes32
        val integrityHashBytes = Numeric.hexStringToByteArray(dataIntegrityHash)
        require(integrityHashBytes.size == 32) { "dataIntegrityHash must be 32 bytes" }
        
        val function = org.web3j.abi.datatypes.Function(
            "logDataAccess",
            listOf(
                Address(ownerAddress),
                Utf8String(encryptedDetailsIpfsHash),
                Uint8(accessedCategory.value.toLong()),  // Enum as uint8
                org.web3j.abi.datatypes.generated.Bytes32(integrityHashBytes)
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
     * Get all access log IDs for a user (read-only)
     */
    suspend fun getAccessLogIds(userAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getAccessLogIds",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<DynamicArray<Uint256>>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting access log IDs: ${response.error.message}")
                return@withContext emptyList()
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext emptyList()
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.isEmpty()) {
                return@withContext emptyList()
            }
            
            @Suppress("UNCHECKED_CAST")
            val ids = (decodedResult[0] as DynamicArray<Uint256>).value
            ids.map { it.value }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access log IDs", e)
            emptyList()
        }
    }
    
    /**
     * Get access log details (read-only)
     */
    suspend fun getAccessLog(logId: BigInteger): AccessLog? = withContext(Dispatchers.IO) {
        try {
            val fromAddress = WalletManager.getAddress()

            val function = org.web3j.abi.datatypes.Function(
                "getAccessLog",
                listOf(Uint256(logId)),
                listOf(
                    object : TypeReference<Uint256>() {},  // id
                    object : TypeReference<Address>() {},  // accessorAddress
                    object : TypeReference<Utf8String>() {},  // encryptedDetailsIpfsHash
                    object : TypeReference<Uint8>() {},  // accessedCategory
                    object : TypeReference<Uint256>() {},  // accessTime
                    object : TypeReference<org.web3j.abi.datatypes.generated.Bytes32>() {}  // dataIntegrityHash
                )
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    fromAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting access log: ${response.error.message}")
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
            
            if (decodedResult.size < 6) {
                return@withContext null
            }
            
            val categoryValue = (decodedResult[3] as Uint8).value.toInt()
            
            AccessLog(
                id = (decodedResult[0] as Uint256).value,
                accessorAddress = (decodedResult[1] as Address).value,
                encryptedDetailsIpfsHash = (decodedResult[2] as Utf8String).value,
                accessedCategory = DataCategory.values().getOrNull(categoryValue) ?: DataCategory.ALL_DATA,
                accessTime = (decodedResult[4] as Uint256).value,
                dataIntegrityHash = Numeric.toHexString((decodedResult[5] as org.web3j.abi.datatypes.generated.Bytes32).value)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access log", e)
            null
        }
    }
    
    // ============================================
    // UTILITY FUNCTIONS - HealthWalletV2
    // ============================================
    
    /**
     * Set emergency contact address
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
     * Get emergency contact address for a user (read-only)
     */
    suspend fun getEmergencyContact(userAddress: String): String? = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getEmergencyContact",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<Address>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting emergency contact: ${response.error.message}")
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
            
            if (decodedResult.isEmpty()) {
                return@withContext null
            }
            
            val address = (decodedResult[0] as Address).value
            if (address == "0x0000000000000000000000000000000000000000") null else address
        } catch (e: Exception) {
            Log.e(TAG, "Error getting emergency contact", e)
            null
        }
    }
    
    /**
     * Check if a user has set their personal info (read-only)
     */
    suspend fun hasPersonalInfo(userAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Checking personal info for address: $userAddress")
            Log.d(TAG, "🔗 Using RPC: $RPC_URL")
            Log.d(TAG, "📝 Contract: $CONTRACT_ADDRESS")
            
            val function = org.web3j.abi.datatypes.Function(
                "hasPersonalInfo",
                listOf(Address(userAddress)),
                listOf(object : TypeReference<Bool>() {})
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            Log.d(TAG, "📤 Encoded function: ${encodedFunction.take(20)}...")
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    userAddress,  // Set from address for auth
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            Log.d(TAG, "📥 Response received")
            
            if (response.hasError()) {
                Log.e(TAG, "❌ RPC Error: ${response.error.message}")
                Log.e(TAG, "❌ Error code: ${response.error.code}")
                // RPC error - don't block the user, assume they have personal info
                Log.w(TAG, "⚠️ RPC failed, allowing user to proceed")
                return@withContext true  // Allow user to try
            }
            
            val result = response.value
            Log.d(TAG, "📦 Raw result: $result")
            
            if (result.isNullOrEmpty() || result == "0x") {
                Log.w(TAG, "⚠️ Empty result from RPC")
                return@withContext true  // Allow user to try
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.isEmpty()) {
                Log.w(TAG, "⚠️ Could not decode result")
                return@withContext true  // Allow user to try
            }
            
            val hasInfo = (decodedResult[0] as Bool).value
            Log.d(TAG, "✅ hasPersonalInfo result: $hasInfo")
            hasInfo
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception checking personal info: ${e.message}", e)
            // Network error - don't block the user
            Log.w(TAG, "⚠️ Exception occurred, allowing user to proceed")
            true  // Allow user to try - blockchain will reject if no personal info
        }
    }
    
    /**
     * Get total counts of all record types (read-only)
     * Returns: (medications, vaccinations, reports, shares, accessLogs)
     */
    suspend fun getTotalCounts(): Map<String, BigInteger> = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getTotalCounts",
                emptyList(),
                listOf(
                    object : TypeReference<Uint256>() {},  // medications
                    object : TypeReference<Uint256>() {},  // vaccinations
                    object : TypeReference<Uint256>() {},  // reports
                    object : TypeReference<Uint256>() {},  // shares
                    object : TypeReference<Uint256>() {}   // accessLogs
                )
            )
            
            val encodedFunction = FunctionEncoder.encode(function)
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    WalletManager.getAddress(),  // Set from address
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting total counts: ${response.error.message}")
                return@withContext emptyMap()
            }
            
            val result = response.value
            if (result.isNullOrEmpty() || result == "0x") {
                return@withContext emptyMap()
            }
            
            val decodedResult = org.web3j.abi.FunctionReturnDecoder.decode(
                result,
                function.outputParameters
            )
            
            if (decodedResult.size < 5) {
                return@withContext emptyMap()
            }
            
            mapOf(
                "medications" to (decodedResult[0] as Uint256).value,
                "vaccinations" to (decodedResult[1] as Uint256).value,
                "reports" to (decodedResult[2] as Uint256).value,
                "shares" to (decodedResult[3] as Uint256).value,
                "accessLogs" to (decodedResult[4] as Uint256).value
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total counts", e)
            emptyMap()
        }
    }
    
    // ============================================
    // TRANSACTION HANDLER
    // ============================================
    
    /**
     * Send a transaction using the user's connected wallet.
     * This prompts the user to sign the transaction in their wallet app.
     */
    private suspend fun sendTransaction(
        from: String,
        to: String,
        data: String,
        value: String = "0x0",
        nonce: String? = null
    ): String = withTimeout(120000) { // 2 minute timeout for user to sign
        suspendCancellableCoroutine { continuation ->
            var isResumed = false
            
            try {
                // Get current chain ID
                val chainId = WalletManager.getChainId() ?: "1"
                
                Log.d(TAG, "========================================")
                Log.d(TAG, "⚠️ TRANSACTION DEBUG INFO")
                Log.d(TAG, "========================================")
                Log.d(TAG, "Wallet Address: $from")
                Log.d(TAG, "Contract Address: $to")
                Log.d(TAG, "Chain ID: $chainId (Sepolia = 11155111)")
                Log.d(TAG, "Expected Chain: Sepolia (11155111)")
                
                if (chainId != "11155111") {
                    Log.e(TAG, "⚠️⚠️⚠️ CHAIN MISMATCH! ⚠️⚠️⚠️")
                    Log.e(TAG, "Current chain: $chainId, Expected: 11155111 (Sepolia)")
                    Log.e(TAG, "Please switch to Sepolia network in MetaMask!")
                }
                
                // Estimate gas (500,000 gas units)
                val gasLimit = "0x${BigInteger.valueOf(500000).toString(16)}"
                val gasPrice = "0x${BigInteger.valueOf(20000000000).toString(16)}" // 20 Gwei
                
                Log.d(TAG, "Transaction details:")
                Log.d(TAG, "  From: $from")
                Log.d(TAG, "  To: $to")
                Log.d(TAG, "  Data: ${data.take(66)}...") // Log first part of data
                Log.d(TAG, "  Chain ID: $chainId")
                Log.d(TAG, "  Gas Limit: $gasLimit (${BigInteger.valueOf(500000)})")
                Log.d(TAG, "  Gas Price: $gasPrice (20 Gwei)")
                if (nonce != null) {
                    Log.d(TAG, "  Nonce: $nonce")
                }
                
                // Build transaction params with optional nonce
                val transactionMap = mutableMapOf(
                    "from" to from,
                    "to" to to,
                    "data" to data,
                    "value" to value,
                    "gas" to gasLimit,
                    "gasPrice" to gasPrice,
                    "chainId" to "0x${BigInteger(chainId).toString(16)}"
                )
                
                // Add nonce if provided
                if (nonce != null) {
                    transactionMap["nonce"] = nonce
                    Log.d(TAG, "  ✓ Using explicit nonce: $nonce")
                }
                
                // Convert to PROPER JSON array containing object
                // Format: [{"from":"0x...","to":"0x..."}]
                // NOT: ["from":"0x...","to":"0x..."] (invalid JSON!)
                val transactionObject = transactionMap.entries.joinToString(",", "{", "}") { 
                    "\"${it.key}\":\"${it.value}\"" 
                }
                val transactionParams = "[$transactionObject]"
                
                Log.d(TAG, "Transaction params JSON: $transactionParams")
                
                // Create request for eth_sendTransaction
                val request = Request(
                    method = "eth_sendTransaction",
                    params = transactionParams
                )
                
                Log.d(TAG, "========================================")
                Log.d(TAG, "📤 SENDING TO WALLETCONNECT...")
                Log.d(TAG, "Method: eth_sendTransaction")
                Log.d(TAG, "========================================")
                
                // Send transaction via AppKit (this queues it in WalletConnect)
                AppKit.request(
                    request = request,
                    onSuccess = { result ->
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "✅ SUCCESS - TRANSACTION REQUEST SENT!")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "Raw result type: ${result::class.java.simpleName}")
                        Log.d(TAG, "Raw result: $result")
                        
                        Log.i(TAG, "========================================")
                        Log.i(TAG, "⏳ Transaction Status: PENDING USER APPROVAL")
                        Log.i(TAG, "📱 Please open MetaMask to approve transaction")
                        Log.i(TAG, "========================================")
                        
                        if (!isResumed) {
                            isResumed = true
                            continuation.resume("PENDING_USER_APPROVAL")
                        }
                    },
                    onError = { error: Throwable ->
                        if (!isResumed) {
                            isResumed = true
                            Log.e(TAG, "========================================")
                            Log.e(TAG, "❌ TRANSACTION FAILED OR REJECTED")
                            Log.e(TAG, "========================================")
                            Log.e(TAG, "Error type: ${error.javaClass.simpleName}")
                            Log.e(TAG, "Error message: ${error.message}")
                            Log.e(TAG, "Full error: ", error)
                            
                            // Handle the redirect error specifically
                            val errorMessage = when {
                                error.message?.contains("redirect", ignoreCase = true) == true -> {
                                    Log.w(TAG, "Got redirect error - wallet may need manual opening")
                                    "Wallet redirect required - please check MetaMask manually"
                                }
                                error.message?.contains("rejected", ignoreCase = true) == true || 
                                error.message?.contains("User rejected", ignoreCase = true) == true -> {
                                    Log.w(TAG, "User explicitly rejected the transaction")
                                    "User rejected the transaction in wallet"
                                }
                                error.message?.contains("Session", ignoreCase = true) == true -> {
                                    Log.e(TAG, "Session error - connection may be broken")
                                    "WalletConnect session error - try reconnecting wallet"
                                }
                                else -> {
                                    Log.e(TAG, "Unknown error during transaction")
                                    error.message ?: "Unknown transaction error"
                                }
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

    /**
     * Get the contract address for debugging
     */
    fun getContractAddress(): String = CONTRACT_ADDRESS

    /**
     * Get the current RPC URL for debugging
     */
    fun getRpcUrl(): String = RPC_URL

    // ============================================
    // TEST & VERIFICATION HELPERS
    // ============================================

    /**
     * Verify that a share was created successfully
     * Returns detailed information about the share for testing
     */
    suspend fun verifyShareCreation(
        expectedRecipient: String,
        expectedCategory: DataCategory
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val userAddress = WalletManager.getAddress()
                ?: return@withContext Pair(false, "No wallet connected")

            Log.d(TAG, "Verifying share for user: $userAddress")

            // Get all share IDs
            val shareIds = getShareIds(userAddress)

            if (shareIds.isEmpty()) {
                return@withContext Pair(false, "No shares found on blockchain")
            }

            // Check the most recent share
            val latestShareId = shareIds.last()
            val share = getShareRecord(latestShareId)
                ?: return@withContext Pair(false, "Could not retrieve share record #$latestShareId")

            // Verify details
            val recipientMatches = share.recipientAddress.equals(expectedRecipient, ignoreCase = true)
            val categoryMatches = share.sharedDataCategory == expectedCategory

            val result = StringBuilder()
            result.append("✓ Share Found on Blockchain!\n\n")
            result.append("Share ID: $latestShareId\n")
            result.append("Total Shares: ${shareIds.size}\n\n")
            result.append("Share Details:\n")
            result.append("• Recipient: ${share.recipientAddress}\n")
            result.append("  ${if (recipientMatches) "✓" else "✗"} Expected: $expectedRecipient\n\n")
            result.append("• Category: ${share.sharedDataCategory.name}\n")
            result.append("  ${if (categoryMatches) "✓" else "✗"} Expected: ${expectedCategory.name}\n\n")
            result.append("• Status: ${share.status.name}\n")
            result.append("• Access Level: ${share.accessLevel.name}\n")
            result.append("• Share Date: ${share.shareDate}\n")
            result.append("• Expiry Date: ${share.expiryDate}\n")

            val success = recipientMatches && categoryMatches
            Pair(success, result.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Error verifying share", e)
            Pair(false, "Verification error: ${e.message}")
        }
    }

    /**
     * Get a summary of all shares for testing/debugging
     */
    suspend fun getSharesSummary(userAddress: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val address = userAddress ?: WalletManager.getAddress()
                ?: return@withContext "No wallet connected"

            val shareIds = getShareIds(address)

            if (shareIds.isEmpty()) {
                return@withContext "No shares found for address: ${address.take(10)}..."
            }

            val summary = StringBuilder()
            summary.append("=== SHARE SUMMARY ===\n")
            summary.append("User: ${address.take(10)}...\n")
            summary.append("Total Shares: ${shareIds.size}\n\n")

            shareIds.forEachIndexed { index, shareId ->
                val share = getShareRecord(shareId)
                if (share != null) {
                    summary.append("${index + 1}. Share ID: $shareId\n")
                    summary.append("   Recipient: ${share.recipientAddress.take(10)}...\n")
                    summary.append("   Category: ${share.sharedDataCategory.name}\n")
                    summary.append("   Status: ${share.status.name}\n")
                    summary.append("   Expires: ${share.expiryDate}\n\n")
                }
            }

            summary.toString()

        } catch (e: Exception) {
            "Error getting summary: ${e.message}"
        }
    }
}
