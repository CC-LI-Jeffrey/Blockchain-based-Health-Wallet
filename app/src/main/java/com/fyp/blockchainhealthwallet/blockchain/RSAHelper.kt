package com.fyp.blockchainhealthwallet.blockchain

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * RSAHelper - Manages RSA key pairs for secure data sharing
 * 
 * Features:
 * - Generate RSA key pair (2048-bit) stored in Android KeyStore
 * - Encrypt AES keys with recipient's RSA public key for sharing
 * - Decrypt AES keys with own RSA private key when receiving shares
 * - Export public key for blockchain storage
 * 
 * Security:
 * - Private key never leaves device (stored in KeyStore)
 * - Public key can be shared for encryption
 * - Supports key rotation via keyVersion
 */
object RSAHelper {
    
    private const val TAG = "RSAHelper"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val RSA_ALGORITHM = "RSA"
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val KEY_SIZE = 2048
    
    /**
     * Get the key alias for the current user
     * Uses wallet address to ensure unique keys per user
     */
    private fun getKeyAlias(): String {
        val walletAddress = WalletManager.getAddress()
            ?: throw IllegalStateException("No wallet connected")
        return "rsa_key_$walletAddress"
    }
    
    /**
     * Generate RSA key pair and store in Android KeyStore
     * Should be called once during initial profile setup
     * 
     * @return Public key as Base64-encoded X.509 format
     */
    fun generateKeyPair(): String {
        val keyAlias = getKeyAlias()
        
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            // Check if key already exists
            if (keyStore.containsAlias(keyAlias)) {
                Log.d(TAG, "RSA key pair already exists for alias: $keyAlias")
                return getPublicKey()
            }
            
            Log.d(TAG, "Generating new RSA key pair for alias: $keyAlias")
            
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                KEYSTORE_PROVIDER
            )
            
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(KEY_SIZE)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA512
                )
                .setUserAuthenticationRequired(false) // No biometric required for this prototype
                .build()
            
            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            Log.d(TAG, "✅ RSA key pair generated successfully")
            
            // Return public key as Base64
            val publicKey = keyPair.public
            val publicKeyBytes = publicKey.encoded
            return Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating RSA key pair", e)
            throw RuntimeException("Failed to generate RSA key pair: ${e.message}", e)
        }
    }
    
    /**
     * Get the user's RSA public key in Base64 format
     * 
     * @return Public key as Base64-encoded X.509 format
     * @throws IllegalStateException if key doesn't exist
     */
    fun getPublicKey(): String {
        val keyAlias = getKeyAlias()
        
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            if (!keyStore.containsAlias(keyAlias)) {
                throw IllegalStateException("RSA key pair not found. Call generateKeyPair() first.")
            }
            
            val entry = keyStore.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
            val publicKey = entry.certificate.publicKey
            val publicKeyBytes = publicKey.encoded
            
            return Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting public key", e)
            throw RuntimeException("Failed to get public key: ${e.message}", e)
        }
    }
    
    /**
     * Get SHA-256 hash of the public key
     * Used for blockchain verification
     * 
     * @return 32-byte hash as hex string (0x prefix + 64 chars)
     */
    fun getPublicKeyHash(): String {
        val publicKeyBase64 = getPublicKey()
        val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(publicKeyBytes)
        
        // Convert to hex string with 0x prefix
        return "0x" + hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Check if RSA key pair exists for current user
     * 
     * @return true if key pair exists, false otherwise
     */
    fun hasKeyPair(): Boolean {
        val keyAlias = getKeyAlias()
        
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.containsAlias(keyAlias)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key pair existence", e)
            false
        }
    }
    
    /**
     * Encrypt an AES key with a recipient's RSA public key
     * Used when sharing data - encrypts the AES key so only recipient can decrypt
     * 
     * @param aesKey The AES symmetric key to encrypt
     * @param recipientPublicKeyBase64 Recipient's public key in Base64 format
     * @return Encrypted key as Base64 string
     */
    fun encryptKeyWithPublicKey(aesKey: SecretKey, recipientPublicKeyBase64: String): String {
        try {
            Log.d(TAG, "Encrypting AES key with recipient's RSA public key")
            
            // Decode the recipient's public key from Base64
            val publicKeyBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            
            // Encrypt the AES key bytes
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            
            val aesKeyBytes = aesKey.encoded
            val encryptedKeyBytes = cipher.doFinal(aesKeyBytes)
            
            val result = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)
            Log.d(TAG, "✅ AES key encrypted successfully (${encryptedKeyBytes.size} bytes)")
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting key with public key", e)
            throw RuntimeException("Failed to encrypt key: ${e.message}", e)
        }
    }
    
    /**
     * Decrypt an AES key that was encrypted with our RSA public key
     * Used when receiving shared data
     * 
     * @param encryptedKeyBase64 The encrypted AES key in Base64 format
     * @return Decrypted AES SecretKey
     */
    fun decryptKeyWithPrivateKey(encryptedKeyBase64: String): SecretKey {
        val keyAlias = getKeyAlias()
        
        try {
            Log.d(TAG, "Decrypting AES key with RSA private key")
            
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            if (!keyStore.containsAlias(keyAlias)) {
                throw IllegalStateException("RSA key pair not found")
            }
            
            val entry = keyStore.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
            val privateKey = entry.privateKey
            
            // Decrypt the AES key
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            
            val encryptedKeyBytes = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val decryptedKeyBytes = cipher.doFinal(encryptedKeyBytes)
            
            Log.d(TAG, "✅ AES key decrypted successfully")
            
            return javax.crypto.spec.SecretKeySpec(decryptedKeyBytes, "AES")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting key with private key", e)
            throw RuntimeException("Failed to decrypt key: ${e.message}", e)
        }
    }
    
    /**
     * Delete the RSA key pair for current user
     * WARNING: This will make all shared data inaccessible!
     * Only use for testing or key rotation
     */
    fun deleteKeyPair() {
        val keyAlias = getKeyAlias()
        
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
                Log.d(TAG, "⚠️ RSA key pair deleted for alias: $keyAlias")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting key pair", e)
            throw RuntimeException("Failed to delete key pair: ${e.message}", e)
        }
    }
    
    /**
     * Initialize RSA keys if they don't exist
     * Should be called during app initialization or profile setup
     * 
     * @return Public key in Base64 format
     */
    fun ensureKeyPairExists(): String {
        return if (hasKeyPair()) {
            Log.d(TAG, "RSA key pair already exists")
            getPublicKey()
        } else {
            Log.d(TAG, "Generating new RSA key pair")
            generateKeyPair()
        }
    }
}
