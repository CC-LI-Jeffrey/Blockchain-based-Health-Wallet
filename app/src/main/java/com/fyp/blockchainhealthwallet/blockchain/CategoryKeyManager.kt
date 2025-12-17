package com.fyp.blockchainhealthwallet.blockchain

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fyp.blockchainhealthwallet.crypto.ECDHKeyExchange
import com.fyp.blockchainhealthwallet.crypto.PublicKeyRegistry
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CategoryKeyManager - Manages per-category encryption keys for HealthWalletV2
 * 
 * ARCHITECTURE OVERVIEW:
 * =====================
 * 
 * This implements cryptographic isolation per data category:
 * - Each data category (Personal Info, Medications, Vaccinations, Reports) uses a SEPARATE encryption key
 * - Keys are DETERMINISTICALLY derived from a master key using HKDF
 * - Master key is derived from wallet address (can be reproduced anytime)
 * - When sharing data, only the specific category key is shared (encrypted with recipient's public key)
 * 
 * KEY HIERARCHY:
 * =============
 * 
 *   Wallet Address
 *         │
 *         ▼ (SHA-256)
 *   ┌─────────────────┐
 *   │   Master Key    │  (256-bit, stored securely in EncryptedSharedPreferences)
 *   └─────────────────┘
 *         │
 *         ▼ (HKDF-SHA256)
 *   ┌─────┴─────┬─────────────┬─────────────┐
 *   ▼           ▼             ▼             ▼
 * PersonalInfo  Medications  Vaccinations  Reports
 *    Key           Key          Key          Key
 * 
 * SECURITY BENEFITS:
 * =================
 * 1. Compromising one category key doesn't expose other categories
 * 2. Sharing reports with a doctor doesn't expose medications
 * 3. Keys can be re-derived from wallet (no key storage in blockchain)
 * 4. Master key protected by Android KeyStore
 * 
 * USAGE:
 * =====
 * // Initialize once per app session
 * CategoryKeyManager.initialize(context)
 * 
 * // Get key for encrypting/decrypting reports
 * val reportsKey = CategoryKeyManager.getCategoryKey(DataCategory.MEDICAL_REPORTS)
 * 
 * // When sharing with a recipient
 * val encryptedKey = CategoryKeyManager.encryptCategoryKeyForRecipient(
 *     DataCategory.MEDICAL_REPORTS,
 *     recipientPublicKey
 * )
 */
object CategoryKeyManager {
    private const val TAG = "CategoryKeyManager"
    
    // Storage keys
    private const val PREFS_NAME = "category_keys_secure"
    private const val MASTER_KEY_PREF = "master_key"
    private const val WALLET_ADDRESS_PREF = "wallet_address"
    
    // Key sizes
    private const val MASTER_KEY_SIZE = 32 // 256 bits
    private const val CATEGORY_KEY_SIZE = 32 // 256 bits
    
    // Category identifiers for HKDF derivation
    private val CATEGORY_INFO = mapOf(
        BlockchainService.DataCategory.PERSONAL_INFO to "HealthWalletV2-PERSONAL_INFO-v1",
        BlockchainService.DataCategory.MEDICATION_RECORDS to "HealthWalletV2-MEDICATION_RECORDS-v1",
        BlockchainService.DataCategory.VACCINATION_RECORDS to "HealthWalletV2-VACCINATION_RECORDS-v1",
        BlockchainService.DataCategory.MEDICAL_REPORTS to "HealthWalletV2-MEDICAL_REPORTS-v1",
        BlockchainService.DataCategory.ALL_DATA to "HealthWalletV2-ALL_DATA-v1"
    )
    
    private var encryptedPrefs: SharedPreferences? = null
    private var masterKey: ByteArray? = null
    private var cachedCategoryKeys = mutableMapOf<BlockchainService.DataCategory, SecretKey>()
    
    /**
     * Initialize the CategoryKeyManager. Must be called before using any key operations.
     * @param context Android context
     */
    fun initialize(context: Context) {
        try {
            // Create master key for EncryptedSharedPreferences
            val masterKeyAlias = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKeyAlias,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            Log.d(TAG, "CategoryKeyManager initialized successfully")
            
            // Check if we need to regenerate master key (wallet changed)
            checkAndUpdateMasterKey()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CategoryKeyManager", e)
            throw RuntimeException("Failed to initialize secure storage", e)
        }
    }
    
    /**
     * Check if wallet address changed and regenerate master key if needed
     */
    private fun checkAndUpdateMasterKey() {
        val currentWallet = WalletManager.getAddress()
        val storedWallet = encryptedPrefs?.getString(WALLET_ADDRESS_PREF, null)
        
        if (currentWallet != null && currentWallet != storedWallet) {
            Log.d(TAG, "Wallet changed, regenerating master key")
            generateAndStoreMasterKey(currentWallet)
        } else if (currentWallet != null) {
            // Load existing master key
            loadMasterKey()
        }
    }
    
    /**
     * Generate master key from wallet address and store securely
     */
    private fun generateAndStoreMasterKey(walletAddress: String) {
        // Derive master key from wallet address using SHA-256
        // This ensures the same wallet always produces the same master key
        val masterKeyBytes = deriveKeyFromWallet(walletAddress)
        
        // Store encrypted in SharedPreferences
        val masterKeyBase64 = Base64.encodeToString(masterKeyBytes, Base64.NO_WRAP)
        encryptedPrefs?.edit()?.apply {
            putString(MASTER_KEY_PREF, masterKeyBase64)
            putString(WALLET_ADDRESS_PREF, walletAddress)
            apply()
        }
        
        masterKey = masterKeyBytes
        cachedCategoryKeys.clear() // Clear cache when master key changes
        
        Log.d(TAG, "Master key generated and stored for wallet: ${walletAddress.take(10)}...")
    }
    
    /**
     * Load master key from secure storage
     */
    private fun loadMasterKey() {
        val masterKeyBase64 = encryptedPrefs?.getString(MASTER_KEY_PREF, null)
        if (masterKeyBase64 != null) {
            masterKey = Base64.decode(masterKeyBase64, Base64.NO_WRAP)
            Log.d(TAG, "Master key loaded from secure storage")
        }
    }
    
    /**
     * Derive master key from wallet address using SHA-256
     * This is deterministic - same wallet always produces same key
     */
    private fun deriveKeyFromWallet(walletAddress: String): ByteArray {
        // Use a deterministic derivation: SHA-256(salt + walletAddress)
        // The salt ensures we don't directly use the address hash
        val salt = "HealthWalletV2-MasterKey-Derivation-Salt-v1"
        val input = "$salt$walletAddress"
        
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Derive a category-specific key using HKDF (HMAC-based Key Derivation Function)
     * 
     * @param category The data category to derive key for
     * @return SecretKey for the specified category
     */
    fun getCategoryKey(category: BlockchainService.DataCategory): SecretKey {
        // Check cache first
        cachedCategoryKeys[category]?.let { return it }
        
        // Ensure master key is available
        val mk = masterKey ?: run {
            // Try to load or generate
            val walletAddress = WalletManager.getAddress()
                ?: throw IllegalStateException("No wallet connected - cannot derive category key")
            generateAndStoreMasterKey(walletAddress)
            masterKey!!
        }
        
        // Get category-specific info string
        val info = CATEGORY_INFO[category]
            ?: throw IllegalArgumentException("Unknown category: $category")
        
        // Derive category key using HKDF
        val categoryKeyBytes = hkdfExpand(mk, info.toByteArray(Charsets.UTF_8), CATEGORY_KEY_SIZE)
        val categoryKey = SecretKeySpec(categoryKeyBytes, "AES")
        
        // Cache for future use
        cachedCategoryKeys[category] = categoryKey
        
        Log.d(TAG, "Derived category key for: ${category.name}")
        return categoryKey
    }
    
    /**
     * HKDF-Expand function (RFC 5869)
     * Expands a pseudo-random key into output keying material
     */
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        
        val hashLength = 32 // SHA-256 output size
        val n = (outputLength + hashLength - 1) / hashLength
        
        var t = ByteArray(0)
        val okm = ByteArray(outputLength)
        var okmOffset = 0
        
        for (i in 1..n) {
            hmac.reset()
            hmac.update(t)
            hmac.update(info)
            hmac.update(i.toByte())
            t = hmac.doFinal()
            
            val copyLength = minOf(hashLength, outputLength - okmOffset)
            System.arraycopy(t, 0, okm, okmOffset, copyLength)
            okmOffset += copyLength
        }
        
        return okm
    }
    
    /**
     * Encrypt a category key for sharing with a recipient using ECDH.
     * The recipient will decrypt this using their ECDH private key and our public key.
     * 
     * @param category The category whose key to share
     * @param recipientPublicKeyHex Recipient's ECDH public key in hex (64 bytes)
     * @return Encrypted category key as Base64 string (contains IV + ciphertext)
     */
    fun encryptCategoryKeyForRecipient(
        category: BlockchainService.DataCategory,
        recipientPublicKeyHex: String
    ): String {
        val categoryKey = getCategoryKey(category)
        val categoryKeyBytes = categoryKey.encoded

        // Get our ECDH private key
        val ownerPrivateKey = PublicKeyRegistry.getPrivateKey()

        // Encrypt using ECDH
        val encryptedKey = ECDHKeyExchange.encryptCategoryKey(
            categoryKeyBytes,
            ownerPrivateKey,
            recipientPublicKeyHex
        )
        
        Log.d(TAG, "✅ Category key encrypted with ECDH for recipient")
        return encryptedKey
    }
    
    /**
     * Decrypt a shared category key as a recipient using ECDH.
     * 
     * @param encryptedKeyBase64 The encrypted category key (Base64 with IV + ciphertext)
     * @param ownerPublicKeyHex Owner's ECDH public key in hex
     * @return Decrypted SecretKey
     */
    fun decryptSharedCategoryKey(
        encryptedKeyBase64: String,
        ownerPublicKeyHex: String
    ): SecretKey {
        // Get our ECDH private key
        val recipientPrivateKey = PublicKeyRegistry.getPrivateKey()
        
        // Decrypt using ECDH
        val decryptedKeyBytes = ECDHKeyExchange.decryptCategoryKey(
            encryptedKeyBase64,
            recipientPrivateKey,
            ownerPublicKeyHex
        )
        
        Log.d(TAG, "✅ Category key decrypted with ECDH from owner")
        return SecretKeySpec(decryptedKeyBytes, "AES")
    }
    
    /**
     * Get the category key as a Base64 string (for internal storage/debugging)
     */
    fun getCategoryKeyAsString(category: BlockchainService.DataCategory): String {
        return Base64.encodeToString(getCategoryKey(category).encoded, Base64.NO_WRAP)
    }
    
    /**
     * Refresh master key when wallet connects/changes
     * Should be called from WalletManager when connection state changes
     */
    fun onWalletConnected(walletAddress: String) {
        Log.d(TAG, "Wallet connected: ${walletAddress.take(10)}...")
        
        val storedWallet = encryptedPrefs?.getString(WALLET_ADDRESS_PREF, null)
        if (walletAddress != storedWallet) {
            Log.d(TAG, "New wallet detected, regenerating keys")
            generateAndStoreMasterKey(walletAddress)
        } else {
            loadMasterKey()
        }
    }
    
    /**
     * Clear all cached keys (call on logout/wallet disconnect)
     */
    fun clearCache() {
        masterKey = null
        cachedCategoryKeys.clear()
        Log.d(TAG, "Key cache cleared")
    }
    
    /**
     * Check if CategoryKeyManager is ready to use
     */
    fun isInitialized(): Boolean {
        return encryptedPrefs != null && (masterKey != null || WalletManager.getAddress() != null)
    }
}
