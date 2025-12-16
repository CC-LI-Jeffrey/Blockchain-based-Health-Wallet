# Encryption Architecture Fix Summary

## Problem

The original encryption implementation had a **critical flaw**: encryption keys were randomly generated per file and then **discarded**. This meant:

1. **Metadata key was lost**: `val (encryptedFile, _) = prepareFileForUpload(...)` - the `_` discarded the key
2. **No way to decrypt**: Without the key, encrypted data on IPFS becomes permanently inaccessible
3. **No key storage**: Keys weren't stored on blockchain (correct) but also weren't derivable (incorrect)

## Solution: CategoryKeyManager with HKDF

We implemented a **deterministic key derivation** system:

```
Wallet Address → SHA-256 → Master Key → HKDF → Category Keys
```

### Files Changed

#### 1. `CategoryKeyManager.kt` (NEW)
- Derives master key from wallet address using SHA-256
- Uses HKDF-SHA256 to derive per-category keys
- Stores keys securely in EncryptedSharedPreferences
- Clears keys on wallet disconnect

#### 2. `EncryptionHelper.kt` (UPDATED)
- Deprecated `prepareFileForUpload()` (random key, key lost)
- Added `prepareFileForUploadWithCategory()` - uses category key
- Added `encryptDataWithCategory()` - encrypts raw data
- Added `decryptDataWithCategory()` - decrypts raw data
- Added `decryptFileWithCategory()` - decrypts files

#### 3. `AddReportActivity.kt` (UPDATED)
- Updated `encryptAndUploadFile()` to use `prepareFileForUploadWithCategory()`
- Updated `uploadEncryptedMetadata()` to use `encryptDataWithCategory()`
- Removed `encryptedKeyForBlockchain` variable (no longer needed)
- Both file and metadata now use `DataCategory.MEDICAL_REPORTS` key

#### 4. `HealthWalletApplication.kt` (UPDATED)
- Added `CategoryKeyManager.initialize(this)` in `onCreate()`

#### 5. `WalletManager.kt` (UPDATED)
- Added `CategoryKeyManager.onWalletConnected(address)` when wallet connects
- Added `CategoryKeyManager.onWalletDisconnected()` when wallet disconnects

#### 6. `ENCRYPTION_ARCHITECTURE.md` (NEW)
- Comprehensive documentation for collaborators
- Explains key derivation, data flow, common mistakes
- Includes diagrams and code examples

## Key Benefits

| Before | After |
|--------|-------|
| Random key per file | Deterministic key from wallet |
| Key discarded after encryption | Key always derivable |
| Can't decrypt own data | Can always decrypt with wallet |
| Same key for all data | Isolated keys per category |
| No access control | Granular category-based sharing |

## Usage Example

```kotlin
// CORRECT: Category-based encryption (keys derived from wallet)
val encryptedFile = EncryptionHelper.prepareFileForUploadWithCategory(
    file, cacheDir, BlockchainService.DataCategory.MEDICAL_REPORTS
)

val encryptedData = EncryptionHelper.encryptDataWithCategory(
    jsonBytes, BlockchainService.DataCategory.MEDICAL_REPORTS
)

// DEPRECATED: Random key encryption (DO NOT USE)
// val (file, key) = EncryptionHelper.prepareFileForUpload(...)  // ❌ Key lost!
```

## Testing

To test the fix:

1. Connect wallet
2. Add a medical report with attachment
3. Verify report saves to blockchain successfully
4. Disconnect and reconnect wallet
5. View the report - should decrypt correctly (because key is derived from wallet)

## TODO

- [ ] Implement RSA/ECDH for sharing category keys with recipients (doctors)
- [ ] Update other activities (medications, vaccinations) to use category keys
- [ ] Add unit tests for key derivation consistency
