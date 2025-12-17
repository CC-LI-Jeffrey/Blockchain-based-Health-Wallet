package com.fyp.blockchainhealthwallet.crypto

import android.util.Base64
import android.util.Log
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ECDHKeyExchange - Implements Elliptic Curve Diffie-Hellman key exchange
 * using secp256k1 curve (same as Ethereum wallets).
 * 
 * SECURITY ARCHITECTURE:
 * =====================
 * 
 * ECDH allows two parties to derive a shared secret without transmitting the secret:
 * 
 *   Owner (Alice)                          Recipient (Bob)
 *   ─────────────                          ───────────────
 *   Private Key: a                         Private Key: b
 *   Public Key: A = a*G                    Public Key: B = b*G
 *   
 *   Shared Secret = a*B = a*(b*G)          Shared Secret = b*A = b*(a*G)
 *                 = (a*b)*G                             = (a*b)*G
 *                 
 *   Both derive the SAME shared secret!
 * 
 * USAGE FLOW:
 * ==========
 * 
 * 1. Owner wants to share data with Recipient
 * 2. Owner gets Recipient's PUBLIC KEY (from our backend registry)
 * 3. Owner computes: sharedSecret = ECDH(ownerPrivateKey, recipientPublicKey)
 * 4. Owner encrypts: encryptedCategoryKey = AES-GCM(categoryKey, sharedSecret)
 * 5. Owner stores encryptedCategoryKey on blockchain
 * 6. Recipient computes: sharedSecret = ECDH(recipientPrivateKey, ownerPublicKey)
 * 7. Recipient decrypts: categoryKey = AES-GCM-Decrypt(encryptedCategoryKey, sharedSecret)
 * 8. Recipient uses categoryKey to decrypt IPFS data
 * 
 * NOTE: We use a signing-based approach since WalletConnect doesn't expose private keys.
 * The wallet signs a deterministic message, and we derive keys from the signature.
 */
object ECDHKeyExchange {
    private const val TAG = "ECDHKeyExchange"
    
    // Constants for key derivation
    private const val ECDH_DOMAIN_SEPARATOR = "HealthWalletV2-ECDH-KeyExchange-v1"
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_SIZE = 12
    private const val GCM_TAG_SIZE = 128
    
    /**
     * Data class to hold encrypted data with IV for AES-GCM
     */
    data class EncryptedData(
        val ciphertext: ByteArray,
        val iv: ByteArray
    ) {
        /**
         * Serialize to Base64 string for storage (IV + ciphertext)
         */
        fun toBase64(): String {
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }
        
        companion object {
            /**
             * Deserialize from Base64 string
             */
            fun fromBase64(base64: String): EncryptedData {
                val combined = Base64.decode(base64, Base64.NO_WRAP)
                val iv = combined.copyOfRange(0, GCM_IV_SIZE)
                val ciphertext = combined.copyOfRange(GCM_IV_SIZE, combined.size)
                return EncryptedData(ciphertext, iv)
            }
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedData) return false
            return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
        }
        
        override fun hashCode(): Int {
            return 31 * ciphertext.contentHashCode() + iv.contentHashCode()
        }
    }
    
    /**
     * Compute ECDH shared secret from private key and peer's public key.
     * Uses secp256k1 curve (Ethereum's curve).
     * 
     * @param privateKey Our private key as BigInteger
     * @param peerPublicKeyHex Peer's public key as hex string (64 bytes uncompressed, or 65 with 04 prefix)
     * @return 32-byte shared secret derived via HKDF
     */
    fun computeSharedSecret(privateKey: BigInteger, peerPublicKeyHex: String): ByteArray {
        try {
            // Parse peer's public key
            val peerPublicKeyBytes = Numeric.hexStringToByteArray(peerPublicKeyHex)
            
            // Handle different formats of public key
            val publicKeyX: BigInteger
            val publicKeyY: BigInteger
            
            when (peerPublicKeyBytes.size) {
                64 -> {
                    // Uncompressed without prefix: X (32 bytes) + Y (32 bytes)
                    publicKeyX = BigInteger(1, peerPublicKeyBytes.copyOfRange(0, 32))
                    publicKeyY = BigInteger(1, peerPublicKeyBytes.copyOfRange(32, 64))
                }
                65 -> {
                    // Uncompressed with 0x04 prefix
                    if (peerPublicKeyBytes[0] != 0x04.toByte()) {
                        throw IllegalArgumentException("Invalid public key prefix")
                    }
                    publicKeyX = BigInteger(1, peerPublicKeyBytes.copyOfRange(1, 33))
                    publicKeyY = BigInteger(1, peerPublicKeyBytes.copyOfRange(33, 65))
                }
                else -> throw IllegalArgumentException("Invalid public key length: ${peerPublicKeyBytes.size}")
            }
            
            // Perform ECDH: sharedPoint = privateKey * peerPublicKey
            // Using web3j's curve parameters
            val ecSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1")
            val curve = ecSpec.curve
            val peerPoint = curve.createPoint(publicKeyX, publicKeyY)
            
            // Multiply peer's public key point by our private key
            val sharedPoint = peerPoint.multiply(privateKey).normalize()
            
            // Get x-coordinate of shared point (standard ECDH)
            val sharedX = sharedPoint.affineXCoord.encoded
            
            // Derive a proper key using HKDF
            return hkdfDerive(sharedX, ECDH_DOMAIN_SEPARATOR.toByteArray(Charsets.UTF_8), 32)
            
        } catch (e: Exception) {
            Log.e(TAG, "ECDH computation failed", e)
            throw RuntimeException("Failed to compute ECDH shared secret", e)
        }
    }
    
    /**
     * Encrypt data using AES-256-GCM with the shared secret.
     * 
     * @param plaintext Data to encrypt
     * @param sharedSecret 32-byte shared secret from ECDH
     * @return EncryptedData containing ciphertext and IV
     */
    fun encrypt(plaintext: ByteArray, sharedSecret: ByteArray): EncryptedData {
        val iv = ByteArray(GCM_IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(sharedSecret, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedData(ciphertext, iv)
    }
    
    /**
     * Decrypt data using AES-256-GCM with the shared secret.
     * 
     * @param encryptedData EncryptedData containing ciphertext and IV
     * @param sharedSecret 32-byte shared secret from ECDH
     * @return Decrypted plaintext
     */
    fun decrypt(encryptedData: EncryptedData, sharedSecret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(sharedSecret, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, encryptedData.iv)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(encryptedData.ciphertext)
    }
    
    /**
     * Encrypt a category key for a recipient using ECDH.
     * 
     * @param categoryKeyBytes The category key to encrypt (32 bytes)
     * @param ownerPrivateKey Owner's private key
     * @param recipientPublicKeyHex Recipient's public key as hex
     * @return Base64-encoded encrypted key (IV + ciphertext)
     */
    fun encryptCategoryKey(
        categoryKeyBytes: ByteArray,
        ownerPrivateKey: BigInteger,
        recipientPublicKeyHex: String
    ): String {
        val sharedSecret = computeSharedSecret(ownerPrivateKey, recipientPublicKeyHex)
        val encrypted = encrypt(categoryKeyBytes, sharedSecret)
        
        Log.d(TAG, "Category key encrypted for recipient")
        return encrypted.toBase64()
    }
    
    /**
     * Decrypt a category key as a recipient using ECDH.
     * 
     * @param encryptedKeyBase64 Base64-encoded encrypted key
     * @param recipientPrivateKey Recipient's private key
     * @param ownerPublicKeyHex Owner's public key as hex
     * @return Decrypted category key bytes
     */
    fun decryptCategoryKey(
        encryptedKeyBase64: String,
        recipientPrivateKey: BigInteger,
        ownerPublicKeyHex: String
    ): ByteArray {
        val sharedSecret = computeSharedSecret(recipientPrivateKey, ownerPublicKeyHex)
        val encryptedData = EncryptedData.fromBase64(encryptedKeyBase64)
        
        Log.d(TAG, "Category key decrypted from owner")
        return decrypt(encryptedData, sharedSecret)
    }
    
    /**
     * HKDF-based key derivation (simplified version using HMAC-SHA256)
     */
    private fun hkdfDerive(inputKeyMaterial: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        // Extract
        val hmac = Mac.getInstance("HmacSHA256")
        val salt = ByteArray(32) // zero salt
        hmac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = hmac.doFinal(inputKeyMaterial)
        
        // Expand
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        hmac.update(info)
        hmac.update(0x01.toByte())
        return hmac.doFinal().copyOf(outputLength)
    }
    
    /**
     * Generate a new key pair for testing/development.
     * In production, users use their wallet keys.
     * 
     * @return ECKeyPair with private and public keys
     */
    fun generateKeyPair(): ECKeyPair {
        return Keys.createEcKeyPair()
    }
    
    /**
     * Get public key hex from an ECKeyPair (64 bytes, no prefix)
     */
    fun getPublicKeyHex(keyPair: ECKeyPair): String {
        return Numeric.toHexStringNoPrefix(keyPair.publicKey.toByteArray()).takeLast(128)
    }
    
    /**
     * Derive an Ethereum address from a public key
     */
    fun publicKeyToAddress(publicKeyHex: String): String {
        val publicKeyBytes = Numeric.hexStringToByteArray(publicKeyHex)
        val hash = MessageDigest.getInstance("KECCAK-256").digest(publicKeyBytes)
        return "0x" + Numeric.toHexString(hash).takeLast(40)
    }
}
