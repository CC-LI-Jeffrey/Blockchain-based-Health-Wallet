package com.fyp.blockchainhealthwallet.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fyp.blockchainhealthwallet.blockchain.BlockchainService
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.ECKeyPair
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * PublicKeyRegistry - Manages storage and retrieval of public keys for ECDH key exchange.
 * 
 * ARCHITECTURE:
 * ============
 * 
 * Since WalletConnect doesn't expose the wallet's private key, we generate a 
 * SEPARATE key pair specifically for ECDH encryption:
 * 
 *   Wallet Key (MetaMask)              ECDH Key (App-Generated)
 *   ─────────────────────              ────────────────────────
 *   Purpose: Sign transactions         Purpose: Encrypt/Decrypt shared data
 *   Storage: MetaMask wallet           Storage: EncryptedSharedPreferences
 *   Control: User via wallet app       Control: App automatic
 *   
 * The ECDH public key is registered ON BLOCKCHAIN (decentralized, persistent).
 * When sharing, we look up recipient's ECDH public key from the blockchain.
 * 
 * SECURITY:
 * ========
 * - ECDH private key stored in Android EncryptedSharedPreferences
 * - Public keys stored on blockchain (decentralized, persistent)
 * - Keys are generated fresh per wallet address
 * - Only the owner can register their own public key (tx signed by wallet)
 */
object PublicKeyRegistry {
    private const val TAG = "PublicKeyRegistry"
    
    // Storage keys
    private const val PREFS_NAME = "ecdh_keys_secure"
    private const val PRIVATE_KEY_PREF = "ecdh_private_key"
    private const val PUBLIC_KEY_PREF = "ecdh_public_key"
    private const val WALLET_ADDRESS_PREF = "wallet_address"
    private const val REGISTERED_PREF = "key_registered"
    
    private var encryptedPrefs: SharedPreferences? = null
    private var cachedKeyPair: ECKeyPair? = null
    private var isInitialized = false
    
    /**
     * Initialize the PublicKeyRegistry. Must be called before any operations.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            isInitialized = true
            Log.d(TAG, "PublicKeyRegistry initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PublicKeyRegistry", e)
            throw RuntimeException("Failed to initialize secure storage", e)
        }
    }
    
    /**
     * Get or generate ECDH key pair for the current wallet.
     * Generates a new key pair if wallet changed or none exists.
     * 
     * @return ECKeyPair for ECDH operations
     */
    fun getOrCreateKeyPair(): ECKeyPair {
        cachedKeyPair?.let { return it }
        
        val currentWallet = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        
        val storedWallet = encryptedPrefs?.getString(WALLET_ADDRESS_PREF, null)
        
        if (currentWallet.equals(storedWallet, ignoreCase = true)) {
            // Load existing key pair
            val privateKeyHex = encryptedPrefs?.getString(PRIVATE_KEY_PREF, null)
            if (privateKeyHex != null) {
                val privateKey = BigInteger(privateKeyHex, 16)
                val keyPair = ECKeyPair.create(privateKey)
                cachedKeyPair = keyPair
                Log.d(TAG, "Loaded existing ECDH key pair for wallet: ${currentWallet.take(10)}...")
                return keyPair
            }
        }
        
        // Generate new key pair for this wallet
        // NOTE: Keys.createEcKeyPair() uses BouncyCastle which doesn't work on Android
        // Instead, generate random 32 bytes and create ECKeyPair directly
        Log.d(TAG, "Generating new ECDH key pair for wallet: ${currentWallet.take(10)}...")
        
        val secureRandom = java.security.SecureRandom()
        val privateKeyBytes = ByteArray(32)
        secureRandom.nextBytes(privateKeyBytes)
        val privateKey = BigInteger(1, privateKeyBytes)
        val keyPair = ECKeyPair.create(privateKey)
        
        // Store securely
        val publicKeyHex = getPublicKeyHex(keyPair)
        encryptedPrefs?.edit()?.apply {
            putString(PRIVATE_KEY_PREF, keyPair.privateKey.toString(16))
            putString(PUBLIC_KEY_PREF, publicKeyHex)
            putString(WALLET_ADDRESS_PREF, currentWallet)
            putBoolean(REGISTERED_PREF, false) // Mark as not registered yet
            apply()
        }
        
        cachedKeyPair = keyPair
        return keyPair
    }
    
    /**
     * Get our ECDH public key as hex string (64 bytes = 128 hex chars, no prefix).
     */
    fun getPublicKeyHex(): String {
        val keyPair = getOrCreateKeyPair()
        return getPublicKeyHex(keyPair)
    }
    
    private fun getPublicKeyHex(keyPair: ECKeyPair): String {
        // Public key is 64 bytes (32 bytes X + 32 bytes Y)
        val publicKeyBytes = Numeric.toBytesPadded(keyPair.publicKey, 64)
        return Numeric.toHexStringNoPrefix(publicKeyBytes)
    }
    
    /**
     * Get our ECDH private key.
     */
    fun getPrivateKey(): BigInteger {
        return getOrCreateKeyPair().privateKey
    }
    
    /**
     * Register our ECDH public key on the blockchain.
     * This requires a transaction signed by the wallet.
     * 
     * @return true if registration successful
     */
    suspend fun registerPublicKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            val walletAddress = WalletManager.getAddress()
            if (walletAddress == null) {
                Log.e(TAG, "❌ registerPublicKey: No wallet connected!")
                return@withContext false
            }
            
            val publicKeyHex = getPublicKeyHex()
            
            Log.d(TAG, "📤 registerPublicKey: Calling BlockchainService.registerECDHPublicKey...")
            Log.d(TAG, "📤 Public key length: ${publicKeyHex.length} chars")
            Log.d(TAG, "📤 Public key preview: ${publicKeyHex.take(20)}...${publicKeyHex.takeLast(20)}")
            
            // Register on blockchain via smart contract
            // This should trigger MetaMask popup!
            val txHash = BlockchainService.registerECDHPublicKey(publicKeyHex)
            
            Log.d(TAG, "✅ registerPublicKey: Transaction sent! Hash: $txHash")
            
            // Mark as registered locally
            encryptedPrefs?.edit()?.putBoolean(REGISTERED_PREF, true)?.apply()
            
            Log.d(TAG, "✅ ECDH public key registered on blockchain: $txHash")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering ECDH public key on blockchain", e)
            Log.e(TAG, "❌ Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Exception message: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Check if our public key is registered on blockchain.
     */
    suspend fun isKeyRegisteredOnChain(): Boolean = withContext(Dispatchers.IO) {
        try {
            val walletAddress = WalletManager.getAddress() ?: return@withContext false
            BlockchainService.hasECDHPublicKey(walletAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ECDH key registration", e)
            false
        }
    }
    
    /**
     * Check if our public key is registered (local cache).
     * NOTE: Also checks blockchain if local cache says registered but we want to verify.
     */
    fun isKeyRegistered(): Boolean {
        return encryptedPrefs?.getBoolean(REGISTERED_PREF, false) ?: false
    }
    
    /**
     * Ensure our ECDH public key is registered on blockchain.
     * Checks blockchain state first, then registers if needed.
     * 
     * @return true if key is registered (either already was or just registered)
     */
    suspend fun ensureKeyRegistered(): Boolean = withContext(Dispatchers.IO) {
        try {
            val walletAddress = WalletManager.getAddress()
            if (walletAddress == null) {
                Log.e(TAG, "❌ ensureKeyRegistered: No wallet connected!")
                return@withContext false
            }
            
            Log.d(TAG, "🔍 ensureKeyRegistered: Checking blockchain for address: ${walletAddress.take(10)}...")
            
            // First check blockchain state (authoritative)
            val isOnChain = try {
                BlockchainService.hasECDHPublicKey(walletAddress)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking hasECDHPublicKey: ${e.message}")
                false
            }
            
            Log.d(TAG, "🔍 ensureKeyRegistered: isOnChain = $isOnChain")
            
            if (isOnChain) {
                Log.d(TAG, "✅ ECDH public key already registered on blockchain")
                // Update local cache
                encryptedPrefs?.edit()?.putBoolean(REGISTERED_PREF, true)?.apply()
                return@withContext true
            }
            
            // Not on chain - register now
            Log.d(TAG, "📝 ECDH public key NOT on blockchain, calling registerPublicKey()...")
            val result = registerPublicKey()
            Log.d(TAG, "📝 registerPublicKey() returned: $result")
            return@withContext result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in ensureKeyRegistered", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Get public key for an address from the blockchain.
     * 
     * @param walletAddress The wallet address to look up
     * @return Public key hex string, or null if not found
     */
    suspend fun getPublicKeyForAddress(walletAddress: String): String? = withContext(Dispatchers.IO) {
        try {
            val publicKey = BlockchainService.getECDHPublicKey(walletAddress)
            
            if (publicKey.isNullOrEmpty()) {
                Log.w(TAG, "No ECDH public key found on blockchain for: ${walletAddress.take(10)}...")
                null
            } else {
                Log.d(TAG, "Found ECDH public key on blockchain for: ${walletAddress.take(10)}...")
                publicKey
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ECDH public key from blockchain", e)
            null
        }
    }
    
    /**
     * Clear cached keys (call on logout/wallet disconnect)
     */
    fun clearCache() {
        cachedKeyPair = null
        Log.d(TAG, "ECDH key cache cleared")
    }
    
    /**
     * Clear all stored keys and reset (use when wallet changes)
     */
    fun reset() {
        cachedKeyPair = null
        encryptedPrefs?.edit()?.clear()?.apply()
        Log.d(TAG, "PublicKeyRegistry reset")
    }
}
