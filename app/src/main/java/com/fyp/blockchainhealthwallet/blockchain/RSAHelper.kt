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
                Log.d(TAG, "RSA key pair already exists")
                return getPublicKey()
            }
            
            Log.d(TAG, "Generating RSA-$KEY_SIZE key pair...")
            
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
                    KeyProperties.DIGEST_SHA1  // Required for MGF1-SHA1 compatibility
                )
                .setUserAuthenticationRequired(false)
                .build()
            
            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            Log.d(TAG, "RSA key pair generated successfully")
            
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
            Log.d(TAG, "Encrypting AES key with recipient's public key")
            
            // Decode the recipient's public key from Base64
            val publicKeyBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
            
            val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            
            // Encrypt using RSA OAEP with parameters matching Android KeyStore
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            
            // CRITICAL: Match Android KeyStore's default OAEP parameters
            // KeyStore uses SHA-256 digest with MGF1-SHA1 by default
            val oaepParams = javax.crypto.spec.OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                java.security.spec.MGF1ParameterSpec.SHA1,  // Use SHA1 for MGF to match KeyStore
                javax.crypto.spec.PSource.PSpecified.DEFAULT
            )
            Log.d(TAG, "  - Using OAEP parameters to match KeyStore:")
            Log.d(TAG, "    * Digest: SHA-256")
            Log.d(TAG, "    * MGF: MGF1")
            Log.d(TAG, "    * MGF1 Digest: SHA1 (KeyStore default)")
            
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams)
            val params = cipher.parameters
            if (params != null) {
                Log.d(TAG, "  - Parameters: ${params}")
                Log.d(TAG, "  - Parameters Class: ${params.javaClass.name}")
                try {
                    val paramsSpec = params.getParameterSpec(javax.crypto.spec.OAEPParameterSpec::class.java)
                    Log.d(TAG, "  - OAEP Digest: ${paramsSpec.digestAlgorithm}")
                    Log.d(TAG, "  - OAEP MGF: ${paramsSpec.mgfAlgorithm}")
                    Log.d(TAG, "  - OAEP MGF Parameters: ${paramsSpec.mgfParameters}")
                    if (paramsSpec.mgfParameters is java.security.spec.MGF1ParameterSpec) {
                        val mgf1 = paramsSpec.mgfParameters as java.security.spec.MGF1ParameterSpec
                        Log.d(TAG, "  - MGF1 Digest: ${mgf1.digestAlgorithm}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "  - Could not extract OAEPParameterSpec: ${e.message}")
                }
            } else {
                Log.d(TAG, "  - Parameters: null")
            }
            
            val aesKeyBytes = aesKey.encoded
            val encryptedKeyBytes = cipher.doFinal(aesKeyBytes)
            val result = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP)
            
            Log.d(TAG, "AES key encrypted successfully")
            
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
            Log.d(TAG, "========== RSA DECRYPTION START ==========")
            Log.d(TAG, "Key alias: $keyAlias")
            Log.d(TAG, "Input encrypted key length: ${encryptedKeyBase64.length} chars")
            
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            if (!keyStore.containsAlias(keyAlias)) {
                throw IllegalStateException("RSA key pair not found")
            }
            
            Log.d(TAG, "KeyStore loaded, alias found")
            
            val entry = keyStore.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
            val privateKey = entry.privateKey
            
            Log.d(TAG, "Private Key Details:")
            Log.d(TAG, "  - Algorithm: ${privateKey.algorithm}")
            Log.d(TAG, "  - Format: ${privateKey.format}")
            if (privateKey is java.security.interfaces.RSAPrivateKey) {
                Log.d(TAG, "  - Modulus bit length: ${privateKey.modulus.bitLength()}")
            }
            
            // Get and log public key for comparison
            val publicKey = entry.certificate.publicKey
            if (publicKey is java.security.interfaces.RSAPublicKey) {
                Log.d(TAG, "Public Key (for verification):")
                Log.d(TAG, "  - Modulus bit length: ${publicKey.modulus.bitLength()}")
                Log.d(TAG, "  - Modulus: ${publicKey.modulus.toString(16).take(32)}...")
            }
            
            // Decrypt the AES key
            // IMPORTANT: Use simple init without explicit OAEP parameters
            // Let Android KeyStore use the same parameters as key generation
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            Log.d(TAG, "Cipher Details:")
            Log.d(TAG, "  - Algorithm: ${cipher.algorithm}")
            Log.d(TAG, "  - Provider: ${cipher.provider.name}")
            Log.d(TAG, "  - Transformation: $RSA_TRANSFORMATION")
            Log.d(TAG, "  - Using KeyStore default OAEP parameters (matches key generation)")
            
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            
            val params = cipher.parameters
            if (params != null) {
                Log.d(TAG, "  - Cipher Parameters: ${params}")
                Log.d(TAG, "  - Cipher Parameters Class: ${params.javaClass.name}")
                try {
                    val paramsSpec = params.getParameterSpec(javax.crypto.spec.OAEPParameterSpec::class.java)
                    Log.d(TAG, "  - OAEP Digest: ${paramsSpec.digestAlgorithm}")
                    Log.d(TAG, "  - OAEP MGF: ${paramsSpec.mgfAlgorithm}")
                    Log.d(TAG, "  - OAEP MGF Parameters: ${paramsSpec.mgfParameters}")
                    if (paramsSpec.mgfParameters is java.security.spec.MGF1ParameterSpec) {
                        val mgf1 = paramsSpec.mgfParameters as java.security.spec.MGF1ParameterSpec
                        Log.d(TAG, "  - MGF1 Digest: ${mgf1.digestAlgorithm}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "  - Could not extract OAEPParameterSpec: ${e.message}")
                }
            } else {
                Log.d(TAG, "  - Cipher Parameters: null")
            }
            
            val encryptedKeyBytes = Base64.decode(encryptedKeyBase64.trim(), Base64.NO_WRAP)
            Log.d(TAG, "Encrypted data details:")
            Log.d(TAG, "  - Decoded bytes length: ${encryptedKeyBytes.size} bytes")
            Log.d(TAG, "  - Expected for RSA-2048: 256 bytes")
            Log.d(TAG, "  - First 10 bytes: ${encryptedKeyBytes.take(10).joinToString(",")}")
            Log.d(TAG, "  - Last 10 bytes: ${encryptedKeyBytes.takeLast(10).joinToString(",")}")
            
            Log.d(TAG, "Calling cipher.doFinal()...")
            
            // Try decryption - if it fails, try with alternate MGF parameters
            val decryptedKeyBytes = try {
                cipher.doFinal(encryptedKeyBytes)
            } catch (e: Exception) {
                Log.w(TAG, "Decryption failed with KeyStore defaults, trying MGF1-SHA256...")
                
                // Retry with MGF1-SHA256 (for data encrypted with software provider defaults)
                val oaepParamsSHA256 = javax.crypto.spec.OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    java.security.spec.MGF1ParameterSpec.SHA256,
                    javax.crypto.spec.PSource.PSpecified.DEFAULT
                )
                
                val cipher2 = Cipher.getInstance(RSA_TRANSFORMATION)
                cipher2.init(Cipher.DECRYPT_MODE, privateKey, oaepParamsSHA256)
                
                try {
                    Log.d(TAG, "Retrying with MGF1-SHA256...")
                    cipher2.doFinal(encryptedKeyBytes)
                } catch (e2: Exception) {
                    Log.e(TAG, "Both decryption attempts failed")
                    Log.e(TAG, "  - MGF1-SHA1 (KeyStore default): ${e.message}")
                    Log.e(TAG, "  - MGF1-SHA256 (software default): ${e2.message}")
                    throw e  // Throw original exception
                }
            }
            
            Log.d(TAG, "Decrypted data details:")
            Log.d(TAG, "  - Decrypted bytes length: ${decryptedKeyBytes.size} bytes")
            Log.d(TAG, "  - First 10 bytes: ${decryptedKeyBytes.take(10).joinToString(",")}")
            Log.d(TAG, "✅ AES key decrypted successfully")
            Log.d(TAG, "========== RSA DECRYPTION END ==========")
            
            return javax.crypto.spec.SecretKeySpec(decryptedKeyBytes, "AES")
            
        } catch (e: Exception) {
            Log.e(TAG, "========== RSA DECRYPTION ERROR ==========")
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause}")
            if (e is java.security.InvalidKeyException) {
                Log.e(TAG, "InvalidKeyException detected - Key mismatch or invalid key format")
            } else if (e is javax.crypto.BadPaddingException) {
                Log.e(TAG, "BadPaddingException detected - Padding scheme mismatch or corrupted data")
            } else if (e is javax.crypto.IllegalBlockSizeException) {
                Log.e(TAG, "IllegalBlockSizeException detected - Data size incompatible with cipher")
            } else if (e is java.security.KeyStoreException) {
                Log.e(TAG, "KeyStoreException detected - KeyStore operation failed")
            }
            Log.e(TAG, "Stack trace:")
            e.stackTrace.take(5).forEach { element ->
                Log.e(TAG, "  at ${element}")
            }
            Log.e(TAG, "========== RSA DECRYPTION ERROR END ==========")
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
     * Regenerate RSA key pair (delete old one and create new)
     * WARNING: This will make all previously shared data inaccessible!
     * Only use for testing or key rotation
     * 
     * @return New public key in Base64 format
     */
    fun regenerateKeyPair(): String {
        Log.d(TAG, "========== REGENERATING RSA KEY PAIR ==========")
        deleteKeyPair()
        val newPublicKey = generateKeyPair()
        Log.d(TAG, "========== RSA KEY PAIR REGENERATED ==========")
        return newPublicKey
    }
    
    /**
     * Initialize RSA keys if they don't exist
     * Should be called during app initialization or profile setup
     * 
     * @return Public key in Base64 format
     */
    fun ensureKeyPairExists(): String {
        return if (hasKeyPair()) {
            // Check if key is compatible with current OAEP configuration
            try {
                Log.d(TAG, "Checking key compatibility...")
                // Try a test encryption/decryption to verify key works
                val testKey = javax.crypto.KeyGenerator.getInstance("AES").apply {
                    init(256)
                }.generateKey()
                
                val publicKeyBase64 = getPublicKey()
                val encrypted = encryptKeyWithPublicKey(testKey, publicKeyBase64)
                decryptKeyWithPrivateKey(encrypted)
                
                Log.d(TAG, "✅ Existing RSA key pair is compatible")
                publicKeyBase64
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Existing key incompatible with current OAEP config: ${e.message}")
                Log.w(TAG, "Regenerating RSA key pair...")
                regenerateKeyPair()
            }
        } else {
            Log.d(TAG, "Generating new RSA key pair")
            generateKeyPair()
        }
    }
}
