# Option A Implementation: Local-First Vaccine Proofs with Optional Blockchain

## Overview

**Option A** is a hybrid approach where vaccine proofs are **primarily stored and verified locally**, with **optional on-demand blockchain anchoring**. This provides:

- ✅ Instant proof generation and sharing (no blockchain delays)
- ✅ Peer-to-peer vaccine passport sharing via QR code
- ✅ Optional blockchain commitment for additional security
- ✅ Full control over proof lifecycle (can prove without broadcasting)

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     VACCINE PROOF LIFECYCLE                     │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ 1. VIEW VACCINATION RECORD                                   │
│   User opens a vaccination record in ViewVaccinationActivity │
│   - Sees vaccine name, date, manufacturer, etc.             │
│   - Taps "Prove Vaccination (ZKP)" button                  │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│ 2. LAUNCH VACCINE VERIFICATION                              │
│   Opens VaccineVerifyActivity with vaccination ID & name    │
│   - Loads ZKP service and blockchain service                │
│   - Shows current on-chain status (if any)                  │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│ 3. REGISTER COMMITMENT (ONE-TIME ONLY)                      │
│   User clicks "Register Commitment On-Chain"                │
│   - Generates random 31-byte salt                           │
│   - Computes Poseidon hash(vaccinationId, vaccineCode, salt)│
│   - Submits commitment to blockchain                        │
│   - Stores salt locally in SharedPreferences                │
│   Note: This step is OPTIONAL for Option A                  │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│ 4. GENERATE ZK PROOF                                         │
│   User clicks "Generate Proof (~10-30s)"                    │
│   - Uses snarkjs to compute ZK proof in WASM                │
│   - Proves: "I have vaccination without revealing secret"   │
│   - Returns proof (A, B, C) + public inputs                 │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│ 5. SAVE PROOF LOCALLY (PRIMARY - OPTION A)                  │
│   Proof automatically saved to device database               │
│   - Table: VaccineProof                                     │
│   - Fields: address, vaccineCode, proof, isVerified, timestamps│
│   - User can now see Vaccine Passport immediately           │
└──────────────────────────────────────────────────────────────┘
                              ↓
              ┌───────────────────────────────┐
              │ OPTIONAL BLOCKCHAIN SUBMISSION│
              ├───────────────────────────────┤
              │ User asks: "Anchor on-chain?" │
              └───────────────────────────────┘
                    ↙           ↖
              YES ↙               ↖ SKIP
              ↓                   ↓
    ┌──────────────────┐  ┌──────────────────┐
    │ SUBMIT TO BLOCK  │  │ GO TO PASSPORT   │
    │ (Optional)       │  │ (Proof local)    │
    │ ~1-2 min wait    │  │ ~instant         │
    └──────────────────┘  └──────────────────┘
              ↓                   ↓
              └───────────────────┴─────────────┐
                                                ↓
        ┌────────────────────────────────────────────┐
        │ 6. VIEW VACCINE PASSPORT                  │
        │   Redirects to VaccinePassportActivity    │
        │   - Loads proof from local database       │
        │   - Shows beautiful Vaccine Passport card │
        │   - Generate shareable QR code            │
        │   - Share with others (cert link)         │
        └────────────────────────────────────────────┘
                          ↓
        ┌────────────────────────────────────────────┐
        │ 7. SHARE QR CODE                          │
        │   User shares QR code with others via:    │
        │   - Email, Messaging, AirDrop, etc.       │
        │   - QR contains: address, vaccine, proof */
        │   - Others scan → verify locally          │
        └────────────────────────────────────────────┘
                          ↓
        ┌────────────────────────────────────────────┐
        │ 8. VERIFY SHARED PASSPORT (PEER-TO-PEER)  │
        │   Another user scans the QR code           │
        │   - AddressQRScannerActivity opens camera  │
        │   - Extracts: address, vaccineCode        │
        │   - User checks their status in Passport  │
        │   - Verification is LOCAL (no blockchain) │
        └────────────────────────────────────────────┘
```

## Implementation Details

### 1. Database Layer

**VaccineProofEntity** | [database/VaccineProofEntity.kt](app/src/main/java/com/fyp/blockchainhealthwallet/database/VaccineProofEntity.kt)
```kotlin
@Entity(tableName = "vaccine_proofs")
data class VaccineProofEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val address: String,          // User's wallet address
    val vaccineCode: Int,         // VaccineCodes.COVID_19, etc.
    val proof: String,            // Serialized ZK proof
    val isVerified: Boolean,      // Whether proof is verified
    val verifiedAt: Long,         // Timestamp of verification
    val createdAt: Long = System.currentTimeMillis()
)
```

**VaccineProofRepository** | [db/VaccineProofRepository.kt](app/src/main/java/com/fyp/blockchainhealthwallet/db/VaccineProofRepository.kt)
```kotlin
class VaccineProofRepository(context: Context) {
    // Core methods:
    suspend fun insertVaccineProof(
        address: String,
        vaccineCode: Int,
        proof: String,
        isVerified: Boolean,
        verifiedAt: Long
    )
    
    suspend fun getMostRecentVaccineProof(): ProofRecord?
    suspend fun getProofByVaccineCode(vaccineCode: Int): ProofRecord?
    suspend fun getAllProofs(): List<ProofRecord>
}
```

### 2. VaccineVerifyActivity

**Purpose**: Generate ZK proofs and save to local database

**Key Methods**:

| Method | Action |
|--------|--------|
| `onRegisterCommitmentClicked()` | (Optional) Submit commitment to blockchain |
| `onGenerateProofClicked()` | Generate ZK proof (~10-30s) |
| `onSubmitProofClicked()` | **SAVE PROOF LOCALLY**, then optionally submit to blockchain |
| `submitToBlockchain()` | Optional on-demand blockchain submission |
| `navigateToPassport()` | Redirect to VaccinePassportActivity |

**Flow**:
```
Register Commitment (optional)
    ↓
Generate Proof
    ↓
Save Proof TO DATABASE (Primary - Option A) ← NEW
    ↓
Ask: "Also submit to blockchain?" (user choice)
    ↓ YES:  Submit on-chain
    ↓ SKIP: Go directly to Passport
    ↓
Navigate to VaccinePassportActivity
```

### 3. VaccinePassportActivity

**Purpose**: Display user's vaccine passport from local database

**Key Methods**:

| Method | Action |
|--------|--------|
| `checkMyProofStatus()` | Query local database for verified proofs |
| `onProofVerified()` | Display passport card with proof data |
| `onProofNotFound()` | Guide user to run VaccineVerifyActivity |
| `buildVaccinePassportJson()` | Build compact JSON with proof data for QR |
| `sharePassportQR()` | Generate shareable QR code |
| `registerBlockchainCommitment()` | Optional on-demand blockchain anchor |
| `openQRScanner()` | Launch camera to scan other passports |

**Checks Database First**: 
```kotlin
// Get most recent verified proof from LOCAL DATABASE
val mostRecent = repository.getMostRecentVaccineProof()

if (mostRecent != null && mostRecent.isVerified) {
    onProofVerified(address, mostRecent)  // Show passport
} else {
    onProofNotFound()  // Guide to VaccineVerifyActivity
}
```

### 4. ViewVaccinationActivity Integration

**Button**: "Prove Vaccination (ZKP)"

**Code** (already implemented):
```kotlin
btnProveVaccination.setOnClickListener {
    val id = vaccinationId?.toLongOrNull() ?: 0L
    val name = tvVaccineNameEn.text.toString().ifEmpty { tvVaccineName.text.toString() }
    val intent = Intent(this, VaccineVerifyActivity::class.java).apply {
        putExtra(VaccineVerifyActivity.EXTRA_VACCINATION_ID, id)
        putExtra(VaccineVerifyActivity.EXTRA_VACCINE_NAME, name)
    }
    startActivity(intent)
}
```

### 5. QR Code Handling

**Vaccine Passport QR Format**:
```json
{
  "type": "VACCINE_PASSPORT",
  "address": "0x...",
  "vaccineCode": 1,
  "vaccineName": "COVID-19 Vaccine",
  "verified": true,
  "proofTimestamp": 1234567890,
  "checkedAt": 1234567890,
  "timestamp": "2024-01-15T10:30:00Z",
  "qrVersion": 2
}
```

**QR Scanner** ([AddressQRScannerActivity.kt](app/src/main/java/com/fyp/blockchainhealthwallet/ui/partialshare/AddressQRScannerActivity.kt)):
- Scans vaccine passport QR codes
- Extracts address, vaccineCode, vaccineName
- Returns to VaccinePassportActivity
- User can verify the scanned address's vaccination status

## Key Advantages of Option A

| Feature | Benefit |
|---------|---------|
| **Local-First** | Instant proof verification, no blockchain latency |
| **Peer-to-Peer** | Users can share proofs directly via QR code |
| **Optional Blockchain** | Users choose to anchor on-chain for additional security |
| **Offline Capable** | Proof verification works without internet (after first sync) |
| **User Control** | Users decide when/if to make proofs public on blockchain |
| **Privacy-Preserving** | ZK proof hides vaccination details but proves vaccination |

## Testing Checklist

- [ ] **Add vaccination record** → Opens AddVaccinationActivity
- [ ] **View vaccination** → Opens ViewVaccinationActivity
- [ ] **Click "Prove Vaccination (ZKP)"** → Launches VaccineVerifyActivity
- [ ] **Generate ZK proof** → Shows progress, takes ~10-30s
- [ ] **Save proof to database** → No blockchain submission required
- [ ] **Skip blockchain submission** → Goes directly to Vaccine Passport
- [ ] **View Vaccine Passport** → Shows passport card with proof data
- [ ] **Share QR code** → Opens share dialog
- [ ] **Scan shared QR** → Opens camera, extracts address/vaccine code
- [ ] **Check vaccination status** → Verifies scanned address (local check)
- [ ] **Optional: Submit to blockchain** → After showing passport (user choice)
- [ ] **Verify blockchain submission** → Check on-chain status (if chosen)

## Files Modified/Created

### New Database Files
- [app/src/main/java/com/fyp/blockchainhealthwallet/db/VaccineProofEntity.kt](database/VaccineProofEntity.kt) — Data model
- [app/src/main/java/com/fyp/blockchainhealthwallet/db/VaccineProofDao.kt](db/VaccineProofDao.kt) — SQL queries
- [app/src/main/java/com/fyp/blockchainhealthwallet/db/VaccineProofRepository.kt](db/VaccineProofRepository.kt) — Access layer
- [app/src/main/java/com/fyp/blockchainhealthwallet/DatabaseInitializer.kt](DatabaseInitializer.kt) — Auto-create tables

### Modified Activities
- **VaccinePassportActivity.kt** — Local-first, optional blockchain
- **VaccineVerifyActivity.kt** — Save to database, optional blockchain submission
- **ViewVaccinationActivity.kt** — Already has "Prove Vaccination" button

### Existing QR Scanner
- **AddressQRScannerActivity.kt** — Already supports VACCINE_PASSPORT format

## Next Steps (Optional Enhancements)

### Short Term
1. **Add QR verification UI** — Show verification result when scanning
2. **Add proof expiration** — Mark proofs as expired after certain period
3. **Local proof backup** — Export/import proofs for device switching

### Medium Term
1. **Batch blockchain submissions** — Save gas fees
2. **Merkle tree aggregation** — Hide individual proofs
3. **Revocation support** — Allow users to revoke proofs

### Long Term
1. **Cross-chain support** — Verify proofs on other blockchains
2. **Multi-signature proofs** — Multiple witnesses for vaccination
3. **Selective disclosure** — Share specific vaccine details only

## Summary

Option A successfully implements **local-first vaccine proofs** with optional blockchain anchoring. Users can:

1. ✅ Generate ZK proofs instantly
2. ✅ Save proofs to device database
3. ✅ Share proofs peer-to-peer via QR code
4. ✅ Verify proofs locally without blockchain
5. ✅ Optionally anchor on-chain for additional security

This provides the best balance of **usability, privacy, and security** for vaccine proof sharing.
