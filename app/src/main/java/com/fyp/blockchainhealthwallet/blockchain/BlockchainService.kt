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
    // HealthWallet 2 address = 0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B
    private const val CONTRACT_ADDRESS = "0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B"
    
    // Sepolia RPC endpoint - using PublicNode (more reliable than rpc.sepolia.org)
    private const val RPC_URL = "https://ethereum-sepolia-rpc.publicnode.com"
    
    // Initialize Web3j for read operations
    private val web3j: Web3j by lazy {
        Web3j.build(HttpService(RPC_URL))
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
            
            val response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    CONTRACT_ADDRESS,
                    encodedFunction
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            
            if (response.hasError()) {
                Log.e(TAG, "Error getting personal info: ${response.error.message}")
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
            
            val exists = (decodedResult[4] as Bool).value
            if (!exists) {
                return@withContext null
            }
            
            PersonalInfoRef(
                encryptedDataIpfsHash = (decodedResult[0] as Utf8String).value,
                publicKeyHash = Numeric.toHexString((decodedResult[1] as org.web3j.abi.datatypes.generated.Bytes32).value),
                createdAt = (decodedResult[2] as Uint256).value,
                lastUpdated = (decodedResult[3] as Uint256).value,
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
                    null,
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
                    null,
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
                    null,
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
                    null,
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
     */
    suspend fun getReportIds(userAddress: String): List<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getReportIds",
                listOf(Address(userAddress)),
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
                Log.e(TAG, "Error getting report IDs: ${response.error.message}")
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
            Log.e(TAG, "Error getting report IDs", e)
            emptyList()
        }
    }
    
    /**
     * Get medical report reference (read-only)
     */
    suspend fun getReportRef(reportId: BigInteger): MedicalReportRef? = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getReportRef",
                listOf(Uint256(reportId)),
                listOf(
                    object : TypeReference<Uint256>() {},  // id
                    object : TypeReference<Utf8String>() {},  // encryptedDataIpfsHash
                    object : TypeReference<Utf8String>() {},  // encryptedFileIpfsHash
                    object : TypeReference<Uint8>() {},  // reportType
                    object : TypeReference<Bool>() {},  // hasFile
                    object : TypeReference<Uint256>() {},  // reportDate
                    object : TypeReference<Uint256>() {}  // createdAt
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
                Log.e(TAG, "Error getting report ref: ${response.error.message}")
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
            
            if (decodedResult.size < 7) {
                return@withContext null
            }
            
            val reportTypeValue = (decodedResult[3] as Uint8).value.toInt()
            val reportType = ReportType.values().getOrNull(reportTypeValue) ?: ReportType.OTHER
            
            MedicalReportRef(
                id = (decodedResult[0] as Uint256).value,
                encryptedDataIpfsHash = (decodedResult[1] as Utf8String).value,
                encryptedFileIpfsHash = (decodedResult[2] as Utf8String).value,
                reportType = reportType,
                hasFile = (decodedResult[4] as Bool).value,
                reportDate = (decodedResult[5] as Uint256).value,
                createdAt = (decodedResult[6] as Uint256).value
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
            val function = org.web3j.abi.datatypes.Function(
                "getShareIds",
                listOf(Address(userAddress)),
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
                Log.e(TAG, "Error getting share IDs: ${response.error.message}")
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
            Log.e(TAG, "Error getting share IDs", e)
            emptyList()
        }
    }
    
    /**
     * Get share record details (read-only)
     */
    suspend fun getShareRecord(shareId: BigInteger): ShareRecord? = withContext(Dispatchers.IO) {
        try {
            val function = org.web3j.abi.datatypes.Function(
                "getShareRecord",
                listOf(Uint256(shareId)),
                listOf(
                    object : TypeReference<Uint256>() {},  // id
                    object : TypeReference<Address>() {},  // recipientAddress
                    object : TypeReference<org.web3j.abi.datatypes.generated.Bytes32>() {},  // recipientNameHash
                    object : TypeReference<Utf8String>() {},  // encryptedRecipientDataIpfsHash
                    object : TypeReference<Uint8>() {},  // recipientType
                    object : TypeReference<Uint8>() {},  // sharedDataCategory
                    object : TypeReference<Uint256>() {},  // shareDate
                    object : TypeReference<Uint256>() {},  // expiryDate
                    object : TypeReference<Uint8>() {},  // accessLevel
                    object : TypeReference<Uint8>() {},  // status
                    object : TypeReference<Utf8String>() {}  // encryptedCategoryKey
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
                Log.e(TAG, "Error getting share record: ${response.error.message}")
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
            
            if (decodedResult.size < 11) {
                return@withContext null
            }
            
            val recipientTypeValue = (decodedResult[4] as Uint8).value.toInt()
            val dataCategoryValue = (decodedResult[5] as Uint8).value.toInt()
            val accessLevelValue = (decodedResult[8] as Uint8).value.toInt()
            val statusValue = (decodedResult[9] as Uint8).value.toInt()
            
            ShareRecord(
                id = (decodedResult[0] as Uint256).value,
                recipientAddress = (decodedResult[1] as Address).value,
                recipientNameHash = Numeric.toHexString((decodedResult[2] as org.web3j.abi.datatypes.generated.Bytes32).value),
                encryptedRecipientDataIpfsHash = (decodedResult[3] as Utf8String).value,
                recipientType = RecipientType.values().getOrNull(recipientTypeValue) ?: RecipientType.OTHER,
                sharedDataCategory = DataCategory.values().getOrNull(dataCategoryValue) ?: DataCategory.ALL_DATA,
                shareDate = (decodedResult[6] as Uint256).value,
                expiryDate = (decodedResult[7] as Uint256).value,
                accessLevel = AccessLevel.values().getOrNull(accessLevelValue) ?: AccessLevel.VIEW_ONLY,
                status = ShareStatus.values().getOrNull(statusValue) ?: ShareStatus.EXPIRED,
                encryptedCategoryKey = (decodedResult[10] as Utf8String).value
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting share record", e)
            null
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
                    null,
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
                    null,
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
                    null,
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
                    null,
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
                    null,
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
}
