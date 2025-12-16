# 🔐 HealthWallet Encryption Architecture

## Overview

This document explains the encryption architecture used in the HealthWallet Android app. This is **critical reading** for any developer working on the codebase, especially when using AI assistants to generate code.

---

## ⚠️ IMPORTANT: Key Design Principle

> **Category Keys are derived from the user's wallet address, NOT randomly generated.**
> 
> This ensures:
> 1. Users can always decrypt their own data by connecting their wallet
> 2. No encryption keys need to be stored on the blockchain
> 3. Data is isolated by category for granular access control

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      USER'S WALLET                               │
│                  (MetaMask, etc.)                                │
│                         │                                        │
│                         ▼                                        │
│              ┌──────────────────────┐                           │
│              │   Wallet Address     │                           │
│              │ 0xABC123...          │                           │
│              └──────────┬───────────┘                           │
│                         │                                        │
│                         ▼                                        │
│         ┌───────────────────────────────────┐                   │
│         │      SHA-256(Salt + Address)       │                  │
│         │         = MASTER KEY               │                  │
│         └─────────────────┬─────────────────┘                   │
│                           │                                      │
│           ┌───────────────┼───────────────┐                     │
│           │               │               │                     │
│           ▼               ▼               ▼                     │
│   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐              │
│   │ HKDF-SHA256 │ │ HKDF-SHA256 │ │ HKDF-SHA256 │              │
│   │ "personal"  │ │ "reports"   │ │ "vaccines"  │ ...          │
│   └──────┬──────┘ └──────┬──────┘ └──────┬──────┘              │
│          │               │               │                      │
│          ▼               ▼               ▼                      │
│   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐              │
│   │ PERSONAL_   │ │ MEDICAL_    │ │ VACCINATION_│              │
│   │ INFO Key    │ │ REPORTS Key │ │ RECORDS Key │              │
│   └─────────────┘ └─────────────┘ └─────────────┘              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Components

### 1. CategoryKeyManager (`utils/CategoryKeyManager.kt`)

The **central key management singleton** that handles all encryption key derivation.

```kotlin
// Get a category-specific encryption key
val reportKey = CategoryKeyManager.getCategoryKey(DataCategory.MEDICAL_REPORTS)
```

**Key Derivation Process:**
1. **Master Key** = SHA-256(SALT + walletAddress)
2. **Category Key** = HKDF-SHA256(masterKey, info="category_name", salt=fixedSalt)

**Why HKDF?**
- Deterministic: Same inputs always produce same key
- Secure: Cryptographically strong key derivation
- Isolated: Each category has an independent key
- Standard: RFC 5869 compliant

### 2. EncryptionHelper (`utils/EncryptionHelper.kt`)

The **encryption/decryption engine** using AES-256-CBC.

```kotlin
// CORRECT: Use category-based methods
val encryptedFile = EncryptionHelper.prepareFileForUploadWithCategory(
    file, cacheDir, DataCategory.MEDICAL_REPORTS
)

val encryptedData = EncryptionHelper.encryptDataWithCategory(
    jsonBytes, DataCategory.MEDICAL_REPORTS
)

// DEPRECATED: Do NOT use random key methods
// val (file, key) = EncryptionHelper.prepareFileForUpload(...)  // ❌ Key is lost!
```

### 3. Data Categories (`BlockchainService.DataCategory`)

Maps to the smart contract's data categories:

```kotlin
enum class DataCategory(val value: Int) {
    PERSONAL_INFO(0),
    MEDICATION_RECORDS(1),
    VACCINATION_RECORDS(2),
    MEDICAL_REPORTS(3),
    ALL_DATA(4)
}
```

---

## Data Flow: Adding a Medical Report

```
┌────────────────┐
│ User selects   │
│ PDF/image file │
└───────┬────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│ 1. ENCRYPT FILE                            │
│    EncryptionHelper.prepareFileForUpload   │
│    WithCategory(file, MEDICAL_REPORTS)     │
│                                            │
│    Uses: CategoryKeyManager.getCategoryKey │
│           (MEDICAL_REPORTS)                │
└───────────────────┬────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────┐
│ 2. UPLOAD TO IPFS                          │
│    Returns: ipfsHash (e.g., "QmXxx...")    │
└───────────────────┬────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────┐
│ 3. CREATE METADATA JSON                    │
│    { fileName, reportType, ipfsHash, ... } │
└───────────────────┬────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────┐
│ 4. ENCRYPT METADATA                        │
│    EncryptionHelper.encryptDataWithCategory│
│    (json.toByteArray(), MEDICAL_REPORTS)   │
└───────────────────┬────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────┐
│ 5. UPLOAD METADATA TO IPFS                 │
│    Returns: metadataIpfsHash               │
└───────────────────┬────────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────────┐
│ 6. STORE ON BLOCKCHAIN                     │
│    addMedicalReport(metadataHash, fileHash)│
│    (hashes are stored, data is on IPFS)    │
└────────────────────────────────────────────┘
```

---

## ❌ Common Mistakes to Avoid

### Mistake 1: Using Random Keys

```kotlin
// ❌ WRONG - Key is lost, data cannot be decrypted!
val randomKey = generateRandomAESKey()
val encrypted = encryptWithKey(data, randomKey)
// What happens to randomKey? It's gone forever!

// ✅ CORRECT - Key derived from wallet, always recoverable
val encrypted = EncryptionHelper.encryptDataWithCategory(
    data, DataCategory.MEDICAL_REPORTS
)
```

### Mistake 2: Storing Keys on Blockchain

```kotlin
// ❌ WRONG - Exposes encryption key on public blockchain!
contract.addReport(ipfsHash, encryptionKey)

// ✅ CORRECT - Only store IPFS hashes
contract.addReport(metadataIpfsHash, fileIpfsHash)
// Key is derived from wallet address, not stored
```

### Mistake 3: Same Key for All Data

```kotlin
// ❌ WRONG - No access control granularity
val masterKey = deriveMasterKey(walletAddress)
encryptAll(data, masterKey)

// ✅ CORRECT - Each category has isolated key
encryptData(personalInfo, CategoryKeyManager.getCategoryKey(PERSONAL_INFO))
encryptData(reports, CategoryKeyManager.getCategoryKey(MEDICAL_REPORTS))
// User can share MEDICAL_REPORTS key without exposing PERSONAL_INFO
```

### Mistake 4: Not Initializing CategoryKeyManager

```kotlin
// ❌ WRONG - Will crash with "not initialized" error
CategoryKeyManager.getCategoryKey(DataCategory.MEDICAL_REPORTS)

// ✅ CORRECT - Initialize in Application.onCreate()
class HealthWalletApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CategoryKeyManager.initialize(this)  // REQUIRED!
    }
}
```

---

## Security Model

### What's Protected

| Data | Storage | Encryption | Who Can Decrypt |
|------|---------|------------|-----------------|
| Medical reports | IPFS | AES-256-CBC with MEDICAL_REPORTS key | Owner only |
| Personal info | IPFS | AES-256-CBC with PERSONAL_INFO key | Owner only |
| Vaccinations | IPFS | AES-256-CBC with VACCINATION_RECORDS key | Owner only |
| IPFS hashes | Blockchain | None (hashes are references) | Public |

### Key Security

| Key Type | Storage | Derivation |
|----------|---------|------------|
| Master Key | EncryptedSharedPreferences | SHA-256(salt + wallet) |
| Category Keys | Cached in memory | HKDF from master |
| Wallet Private Key | User's wallet app | Not in our app |

### Access Control

- **Owner**: Can decrypt all their data using wallet-derived keys
- **Healthcare Provider**: Can decrypt shared categories (TODO: RSA/ECDH)
- **Public**: Can only see IPFS hashes, not data content

---

## Future: Sharing Data with Recipients

> 🚧 **Not Yet Implemented** - Currently placeholder

The design supports sharing specific categories with recipients (doctors, hospitals):

```kotlin
// Future implementation
fun shareCategory(category: DataCategory, recipientPublicKey: PublicKey): String {
    val categoryKey = CategoryKeyManager.getCategoryKey(category)
    // Encrypt category key with recipient's public key (RSA/ECDH)
    return encryptForRecipient(categoryKey, recipientPublicKey)
}
```

The recipient would:
1. Decrypt the category key using their private key
2. Use the category key to decrypt data from IPFS

---

## Testing Encryption

### Unit Test: Key Derivation Consistency

```kotlin
@Test
fun testKeyDerivationConsistency() {
    val wallet = "0x1234567890abcdef..."
    
    CategoryKeyManager.onWalletConnected(wallet)
    val key1 = CategoryKeyManager.getCategoryKey(DataCategory.MEDICAL_REPORTS)
    
    // Disconnect and reconnect
    CategoryKeyManager.onWalletDisconnected()
    CategoryKeyManager.onWalletConnected(wallet)
    val key2 = CategoryKeyManager.getCategoryKey(DataCategory.MEDICAL_REPORTS)
    
    // Keys must be identical for same wallet
    assertEquals(key1, key2)
}
```

### Integration Test: Encrypt/Decrypt Roundtrip

```kotlin
@Test
fun testEncryptDecryptRoundtrip() {
    val original = "Patient medical data..."
    
    val encrypted = EncryptionHelper.encryptDataWithCategory(
        original.toByteArray(), DataCategory.MEDICAL_REPORTS
    )
    
    val decrypted = EncryptionHelper.decryptDataWithCategory(
        encrypted, DataCategory.MEDICAL_REPORTS
    )
    
    assertEquals(original, String(decrypted))
}
```

---

## Quick Reference

### For AI Assistants

When generating code that involves encryption:

1. **ALWAYS** use `EncryptionHelper.*WithCategory()` methods
2. **NEVER** use deprecated `prepareFileForUpload()` method
3. **ALWAYS** specify the correct `DataCategory` for the data type
4. **NEVER** store encryption keys on the blockchain
5. **ENSURE** `CategoryKeyManager.initialize(context)` is called in Application

### File Locations

```
app/src/main/java/com/fyp/blockchainhealthwallet/
├── utils/
│   ├── CategoryKeyManager.kt    # Key derivation
│   └── EncryptionHelper.kt      # AES encryption
├── services/
│   └── BlockchainService.kt     # DataCategory enum
├── HealthWalletApplication.kt   # Initialization
└── wallet/
    └── WalletManager.kt         # Wallet connect/disconnect
```

---

## Contact

For questions about the encryption architecture, refer to this document or contact the project maintainers.

**Last Updated**: 2024
**Version**: 1.0 (CategoryKeyManager Implementation)
