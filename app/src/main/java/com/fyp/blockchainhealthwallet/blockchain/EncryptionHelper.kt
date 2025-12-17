package com.fyp.blockchainhealthwallet.blockchain

import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EncryptionHelper provides client-side AES-256 encryption for medical files.
 * 
 * ARCHITECTURE:
 * ============
 * This class works with CategoryKeyManager to provide per-category encryption.
 * 
 * KEY TYPES:
 * - Category Key: Derived from wallet via CategoryKeyManager (deterministic)
 * - Random Key: Generated per-file for backward compatibility
 * 
 * RECOMMENDED FLOW (with CategoryKeyManager):
 * 1. User selects a file to upload
 * 2. Get category-specific key from CategoryKeyManager
 * 3. Encrypt file with category key
 * 4. Upload encrypted file to IPFS
 * 5. Store IPFS hash on blockchain (key can be re-derived from wallet)
 * 
 * LEGACY FLOW (random keys - NOT RECOMMENDED):
 * 1. Generate random AES key
 * 2. Encrypt file with random key
 * 3. Must store key somewhere (blockchain or local) - key loss = data loss
 * 
 * Security: Backend cannot read medical data even though it stores encrypted files on IPFS.
 */
object EncryptionHelper {
    private const val TAG = "EncryptionHelper"
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 16 // bytes
    
    /**
     * Encrypted file result containing the encrypted data and the AES key used.
     */
    data class EncryptionResult(
        val encryptedFile: File,
        val aesKey: SecretKey,
        val iv: ByteArray
    )
    
    /**
     * Generate a random AES-256 key for encrypting a file.
     */
    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(KEY_SIZE)
        return keyGenerator.generateKey()
    }
    
    /**
     * Generate a random initialization vector for AES encryption.
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        return iv
    }
    
    /**
     * Encrypt a file using AES-256 encryption.
     * 
     * @param inputFile The file to encrypt
     * @param outputFile Where to save the encrypted file
     * @param secretKey The AES key to use for encryption
     * @return EncryptionResult containing encrypted file info and key
     */
    fun encryptFile(
        inputFile: File,
        outputFile: File,
        secretKey: SecretKey = generateAESKey()
    ): EncryptionResult {
        try {
            val iv = generateIV()
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    // Write IV at the beginning of the file (not encrypted)
                    fos.write(iv)
                    
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (encryptedBytes != null) {
                            fos.write(encryptedBytes)
                        }
                    }
                    
                    // Write final block
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        fos.write(finalBytes)
                    }
                }
            }
            
            Log.d(TAG, "File encrypted successfully: ${outputFile.absolutePath}")
            
            return EncryptionResult(
                encryptedFile = outputFile,
                aesKey = secretKey,
                iv = iv
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting file", e)
            throw e
        }
    }
    
    /**
     * Decrypt a file using AES-256 encryption.
     * 
     * @param encryptedFile The encrypted file
     * @param outputFile Where to save the decrypted file
     * @param secretKey The AES key used for encryption
     */
    fun decryptFile(
        encryptedFile: File,
        outputFile: File,
        secretKey: SecretKey
    ) {
        try {
            FileInputStream(encryptedFile).use { fis ->
                // Read IV from beginning of file
                val iv = ByteArray(IV_SIZE)
                fis.read(iv)
                
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
                
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (decryptedBytes != null) {
                            fos.write(decryptedBytes)
                        }
                    }
                    
                    // Write final block
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        fos.write(finalBytes)
                    }
                }
            }
            
            Log.d(TAG, "File decrypted successfully: ${outputFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting file", e)
            throw e
        }
    }
    
    /**
     * Convert SecretKey to Base64 string for storage.
     */
    fun keyToString(key: SecretKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }
    
    /**
     * Convert Base64 string back to SecretKey.
     */
    fun stringToKey(keyString: String): SecretKey {
        val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
        return SecretKeySpec(keyBytes, "AES")
    }
    
    /**
     * Encrypt the AES key itself for storage on blockchain.
     * 
     * For now, this just converts the key to Base64.
     * TODO: In production, encrypt this with the user's wallet public key
     * so only the user can decrypt it.
     * 
     * @param aesKey The AES key to encrypt
     * @param userPublicKey User's public key (from wallet)
     * @return Encrypted key as Base64 string
     */
    fun encryptKeyForBlockchain(aesKey: SecretKey, userPublicKey: String? = null): String {
        // TODO: Implement RSA encryption using user's wallet public key
        // For now, just encode to Base64
        // In production, use: RSA.encrypt(aesKey, userPublicKey)
        
        Log.w(TAG, "WARNING: AES key is not encrypted with user's public key yet")
        return keyToString(aesKey)
    }
    
    /**
     * Decrypt the AES key from blockchain storage.
     * 
     * @param encryptedKey The encrypted key from blockchain
     * @param userPrivateKey User's private key (from wallet)
     * @return Decrypted AES key
     */
    fun decryptKeyFromBlockchain(encryptedKey: String, userPrivateKey: String? = null): SecretKey {
        // TODO: Implement RSA decryption using user's wallet private key
        // For now, just decode from Base64
        // In production, use: RSA.decrypt(encryptedKey, userPrivateKey)
        
        return stringToKey(encryptedKey)
    }
    
    /**
     * @deprecated Use prepareFileForUploadWithCategory() instead for proper key management
     * 
     * Encrypt file and return both encrypted file and encrypted key.
     * WARNING: This uses a RANDOM key - the key will be lost after this call!
     * 
     * @param sourceFile Original medical file to encrypt
     * @param outputDir Directory to save encrypted file
     * @return Pair of (encrypted file, encrypted key for blockchain)
     */
    @Deprecated("Use prepareFileForUploadWithCategory() for deterministic keys")
    fun prepareFileForUpload(
        sourceFile: File,
        outputDir: File
    ): Pair<File, String> {
        // Generate random AES key - WARNING: this key cannot be recovered!
        val aesKey = generateAESKey()
        
        // Create encrypted file
        val encryptedFileName = "${sourceFile.nameWithoutExtension}_encrypted"
        val encryptedFile = File(outputDir, encryptedFileName)
        
        // Encrypt the file
        val result = encryptFile(sourceFile, encryptedFile, aesKey)
        
        // Return key as Base64 (not truly encrypted - caller must store this!)
        val keyString = keyToString(result.aesKey)
        
        Log.w(TAG, "⚠️ Using deprecated prepareFileForUpload() - key must be stored or will be lost!")
        Log.d(TAG, "File prepared for upload: encrypted file = ${encryptedFile.absolutePath}")
        
        return Pair(encryptedFile, keyString)
    }
    
    /**
     * RECOMMENDED: Encrypt file using category-specific key from CategoryKeyManager
     * The key can be re-derived from wallet address, so no key storage needed!
     * 
     * @param sourceFile Original medical file to encrypt
     * @param outputDir Directory to save encrypted file  
     * @param category The data category (determines which key to use)
     * @return Encrypted file (key is derived from wallet, not returned)
     */
    fun prepareFileForUploadWithCategory(
        sourceFile: File,
        outputDir: File,
        category: BlockchainService.DataCategory
    ): File {
        // Get category-specific key (deterministic, can be re-derived)
        val categoryKey = CategoryKeyManager.getCategoryKey(category)
        
        // Create encrypted file
        val encryptedFileName = "${sourceFile.nameWithoutExtension}_encrypted_${System.currentTimeMillis()}"
        val encryptedFile = File(outputDir, encryptedFileName)
        
        // Encrypt the file with category key
        encryptFile(sourceFile, encryptedFile, categoryKey)
        
        Log.d(TAG, "✅ File encrypted with ${category.name} key")
        Log.d(TAG, "File prepared for upload: ${encryptedFile.absolutePath}")
        
        return encryptedFile
    }
    
    /**
     * Encrypt data (String/JSON) using category-specific key
     * Returns the encrypted data as Base64 string
     * 
     * @param data The data to encrypt (e.g., JSON metadata)
     * @param category The data category
     * @return Base64 encoded encrypted data (includes IV)
     */
    fun encryptDataWithCategory(
        data: String,
        category: BlockchainService.DataCategory
    ): String {
        val categoryKey = CategoryKeyManager.getCategoryKey(category)
        
        val iv = ByteArray(IV_SIZE)
        java.security.SecureRandom().nextBytes(iv)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, categoryKey, IvParameterSpec(iv))
        
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        // Combine IV + encrypted data
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt data that was encrypted with encryptDataWithCategory()
     * 
     * @param encryptedDataBase64 The Base64 encrypted data (includes IV)
     * @param category The data category
     * @return Decrypted string
     */
    fun decryptDataWithCategory(
        encryptedDataBase64: String,
        category: BlockchainService.DataCategory
    ): String {
        val categoryKey = CategoryKeyManager.getCategoryKey(category)
        
        val combined = Base64.decode(encryptedDataBase64, Base64.NO_WRAP)
        
        // Extract IV and encrypted data
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encryptedBytes = combined.copyOfRange(IV_SIZE, combined.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, categoryKey, IvParameterSpec(iv))
        
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
    
    /**
     * Decrypt raw bytes that were encrypted with category key
     * Used when IPFS returns binary data instead of Base64 string
     * 
     * @param encryptedBytes The encrypted bytes (includes IV)
     * @param category The data category
     * @return Decrypted string
     */
    fun decryptBytesWithCategory(
        encryptedBytes: ByteArray,
        category: BlockchainService.DataCategory
    ): String {
        val categoryKey = CategoryKeyManager.getCategoryKey(category)
        
        // Extract IV and encrypted data
        val iv = encryptedBytes.copyOfRange(0, IV_SIZE)
        val ciphertext = encryptedBytes.copyOfRange(IV_SIZE, encryptedBytes.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, categoryKey, IvParameterSpec(iv))
        
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }
    
    /**
     * Decrypt a file that was encrypted with category key
     * 
     * @param encryptedFile The encrypted file from IPFS
     * @param category The data category used for encryption
     * @param outputFile Where to save decrypted file
     */
    fun decryptFileWithCategory(
        encryptedFile: File,
        category: BlockchainService.DataCategory,
        outputFile: File
    ) {
        val categoryKey = CategoryKeyManager.getCategoryKey(category)
        decryptFile(encryptedFile, outputFile, categoryKey)
        Log.d(TAG, "✅ File decrypted with ${category.name} key")
    }

    /**
     * Decrypt a file downloaded from IPFS.
     * 
     * @param encryptedFile The encrypted file from IPFS
     * @param encryptedKeyFromBlockchain The encrypted AES key from blockchain
     * @param outputFile Where to save decrypted file
     */
    fun decryptDownloadedFile(
        encryptedFile: File,
        encryptedKeyFromBlockchain: String,
        outputFile: File
    ) {
        // Decrypt the AES key
        val aesKey = decryptKeyFromBlockchain(encryptedKeyFromBlockchain)
        
        // Decrypt the file
        decryptFile(encryptedFile, outputFile, aesKey)
        
        Log.d(TAG, "Downloaded file decrypted: ${outputFile.absolutePath}")
    }
}
