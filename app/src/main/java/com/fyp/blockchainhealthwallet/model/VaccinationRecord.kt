package com.fyp.blockchainhealthwallet.model

import java.math.BigInteger

/**
 * VaccinationRecord - Matches HealthWalletV2.05 blockchain structure
 * 
 * BLOCKCHAIN STRUCTURE:
 * - uint256 id
 * - string encryptedDataIpfsHash (IPFS hash of encrypted JSON with all details)
 * - string encryptedCertificateIpfsHash (IPFS hash of encrypted certificate file)
 * - uint256 vaccinationDate (Unix timestamp)
 * - uint256 createdAt
 * - string encryptedKey (Encrypted random AES key for this record)
 */
data class VaccinationRecord(
    // Blockchain fields
    val blockchainId: BigInteger? = null,              // Smart contract ID
    val encryptedDataIpfsHash: String? = null,         // IPFS hash of encrypted data
    val encryptedCertificateIpfsHash: String? = null,  // IPFS hash of encrypted certificate
    val vaccinationDate: Long? = null,                 // Unix timestamp
    val createdAt: Long? = null,                       // Unix timestamp
    val encryptedKey: String? = null,                  // Encrypted AES key
    
    // Decrypted local fields (for display after decryption)
    val id: String = "",                               // Local display ID
    val date: String = "",                             // Formatted date string
    val vaccineName: String = "",
    val vaccineNameEn: String = "",
    val vaccineFullName: String = "",
    val manufacturer: String = "",
    val country: String = "",
    val provider: String = "",
    val location: String = "",
    val batchNumber: String = "",
    val certificateUrl: String? = null,                // Local certificate file path
    
    // Encryption status
    val isEncrypted: Boolean = false,                  // True if data is encrypted
    val isOnBlockchain: Boolean = false,               // True if saved to blockchain
    
    // Delete status
    val isDeleted: Boolean = false                     // Soft delete flag from blockchain
)
