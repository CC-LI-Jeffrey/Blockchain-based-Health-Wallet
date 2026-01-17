package com.fyp.blockchainhealthwallet.blockchain

import android.util.Log
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * SimpleKeyManager - Simplified key management for HealthWallet
 *
 * SECURITY MODEL:
 * ===============
 * - Single user key derived from wallet address using SHA-256
 * - Each record uses a random AES key encrypted with the user key
 * - User key can be re-derived from wallet address anytime
 *
 * KEY DERIVATION:
 * ===============
 *   Wallet Address
 *        │
 *        ▼ SHA-256(salt + wallet)
 *   ┌─────────────────┐
 *   │   User Key      │  (256-bit AES key)
 *   └─────────────────┘
 *        │
 *        ▼ Encrypts
 *   ┌─────────────────┐
 *   │  Record Keys    │  (Random per record)
 *   └─────────────────┘
 *
 * SECURITY TRADE-OFFS:
 * ====================
 * ✅ Simple and reliable
 * ✅ Keys always recoverable from wallet
 * ✅ No complex HKDF needed
 * ⚠️  Wallet addresses are public
 * ⚠️  SHA-256(wallet) is deterministic
 *
 * FUTURE: Consider PBKDF2 with user password for stronger security
 */
object SimpleKeyManager {
    private const val TAG = "SimpleKeyManager"
    private const val SALT = "HealthWalletV2-SimpleKey-v1"

    private var cachedUserKey: SecretKey? = null
    private var cachedWalletAddress: String? = null

    /**
     * Get the user's encryption key derived from their wallet address.
     * Key is cached for performance but invalidated when wallet changes.
     */
    fun getUserKey(): SecretKey {
        val currentAddress = WalletManager.getAddress()

        // Return cached key if wallet hasn't changed
        if (cachedUserKey != null && cachedWalletAddress == currentAddress) {
            return cachedUserKey!!
        }

        // Derive new key
        if (currentAddress == null) {
            throw IllegalStateException("No wallet connected - cannot derive user key")
        }

        cachedUserKey = deriveUserKey(currentAddress)
        cachedWalletAddress = currentAddress

        Log.d(TAG, "Derived user key for wallet: ${currentAddress.take(10)}...")
        return cachedUserKey!!
    }

    /**
     * Derive user key from wallet address using SHA-256.
     * This is deterministic - same wallet always produces same key.
     */
    private fun deriveUserKey(walletAddress: String): SecretKey {
        val input = (SALT + walletAddress).toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(input)
        return SecretKeySpec(hash, "AES")
    }

    /**
     * Clear cached keys (call when wallet disconnects).
     */
    fun clearCache() {
        cachedUserKey = null
        cachedWalletAddress = null
        Log.d(TAG, "Key cache cleared")
    }

    /**
     * Get wallet address currently cached.
     */
    fun getCachedWalletAddress(): String? = cachedWalletAddress
}