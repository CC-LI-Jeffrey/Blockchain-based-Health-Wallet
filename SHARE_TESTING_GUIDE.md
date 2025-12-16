# Testing Guide: Verify Share Record Success

This guide explains **5 different ways** to verify that sharing health records works successfully.

---

## ✅ Method 1: Transaction Hash Verification (Immediate)

### Steps:
1. **Share a record** from the app
2. **Success dialog appears** with transaction hash
3. **Check the full TX hash** in:
   - Logcat: Search for `"Share transaction successful"`
   - Success message now shows full TX hash

### What to Look For:
```
Share transaction successful: 0x1234567890abcdef...
Shared VACCINATION_RECORDS with 0xRecipientAddress
```

### Verify on Blockchain Explorer:
- Open: https://sepolia.etherscan.io/
- Paste transaction hash
- Check status is ✅ Success
- View contract interaction details

---

## ✅ Method 2: Long-Press Verification (Built-in Test)

### Steps:
1. Open **Share Record Activity**
2. **Long-press** on the "Import Record" card (hold for 1-2 seconds)
3. Automatic verification runs

### What it Shows:
```
✓ Share Verification Successful!

Total Shares: 3
Last Share ID: 2

Last Share Details:
• Recipient: 0x1234567...
• Category: VACCINATION_RECORDS
• Status: ACTIVE
• Access Level: VIEW_ONLY
• Expiry: 1735660800

✓ Sharing is working correctly!
```

### If No Shares:
```
✗ No shares found on blockchain.

You haven't shared any records yet.
```

---

## ✅ Method 3: Check Logcat for Details

### Steps:
1. Open **Android Studio** > **Logcat**
2. **Filter by tag**: `BlockchainHelper` or `ShareRecordActivity`
3. Share a record
4. Look for success logs

### Expected Output:
```
D/BlockchainHelper: Share transaction successful: 0xabcd1234...
D/BlockchainHelper: Shared MEDICATION_RECORDS with 0x742d35Cc...
D/ShareRecordActivity: Found 2 shares on blockchain
D/ShareRecordActivity: Loading share 1
D/ShareRecordActivity: Loading share 2
```

### Error Indicators:
```
E/BlockchainService: Error getting share IDs: ...
E/ShareRecordActivity: Error loading blockchain shares
```

---

## ✅ Method 4: List Refresh Test

### Steps:
1. **Note current share count** in Share Record Activity
2. **Share a new record**
3. Success dialog has **"OK" button** that refreshes the list
4. **Click OK**
5. **Verify:**
   - Share list reloads automatically
   - New share appears in the list
   - Statistics update (Total Shares count increases)

### Before Sharing:
```
Total Shares: 2
Active: 2
Expired: 0
```

### After Sharing & Refresh:
```
Total Shares: 3  ← Increased!
Active: 3
Expired: 0
```

---

## ✅ Method 5: Manual Blockchain Query (Advanced)

### Option A: Using BlockchainService Helpers

Add this test code in your activity:

```kotlin
// In ShareRecordActivity or any activity
lifecycleScope.launch {
    // Get summary of all shares
    val summary = BlockchainService.getSharesSummary()
    Log.d("TEST", summary)
    
    // Verify specific share
    val (success, details) = BlockchainService.verifyShareCreation(
        expectedRecipient = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEbB",
        expectedCategory = BlockchainService.DataCategory.VACCINATION_RECORDS
    )
    
    Log.d("TEST", "Verification: $success")
    Log.d("TEST", details)
}
```

### Option B: Using Web3 Provider Directly

1. Install MetaMask browser extension
2. Connect to Sepolia network
3. Use contract address: `0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B`
4. Call `getShareIds(yourAddress)` function
5. Check returned array of share IDs

### Option C: Using Etherscan

1. Go to: https://sepolia.etherscan.io/address/0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B
2. Click **"Contract"** tab
3. Click **"Read Contract"**
4. Find `getShareIds` function
5. Enter your wallet address
6. Click "Query"
7. See list of share IDs

---

## 🧪 Complete Testing Workflow

### Step-by-Step Test:

1. **Connect Wallet**
   ```
   ✓ Wallet connected
   ✓ Address: 0x1234...
   ```

2. **Share a Record**
   - Recipient: `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEbB`
   - Category: Vaccination Records
   - Duration: 90 days
   - Click "Share"

3. **Approve in Wallet**
   ```
   ✓ MetaMask opens
   ✓ Review transaction
   ✓ Click "Confirm"
   ✓ Wait for transaction...
   ```

4. **Verify Success Dialog**
   ```
   ✓ "Data Shared Successfully!"
   ✓ Transaction hash shown
   ✓ Full TX displayed
   ```

5. **Check Transaction on Etherscan**
   - Copy TX hash from dialog
   - Open Etherscan
   - Verify status = Success

6. **Long-Press Verify**
   - Long-press "Import Record" card
   - Check verification shows your share

7. **Check List Updates**
   - Click OK in success dialog
   - List refreshes
   - New share appears

---

## 🔍 Common Issues & Solutions

### Issue: "No shares found"
**Solutions:**
- Ensure transaction was confirmed (check Etherscan)
- Wait 15-30 seconds for blockchain confirmation
- Pull to refresh the list
- Check you're using the correct wallet address

### Issue: "Transaction Failed"
**Causes:**
- User rejected in wallet → Approve transaction
- Insufficient gas → Add test ETH from faucet
- Network issues → Try again

### Issue: Share appears in blockchain but not in app
**Solutions:**
1. Pull to refresh
2. Close and reopen activity
3. Check Logcat for errors
4. Verify RPC connection is working

### Issue: "Error loading shares"
**Solutions:**
- Check internet connection
- Verify RPC endpoint is accessible
- Check Logcat for specific error
- Falls back to demo data (expected)

---

## 📊 Expected Test Results

### ✅ Success Indicators:
- Transaction hash received
- Success dialog appears
- Share appears in list within 30 seconds
- Statistics update correctly
- Long-press verification shows share
- Etherscan shows "Success" status

### ❌ Failure Indicators:
- Transaction error dialog
- No TX hash received
- Share doesn't appear after 2 minutes
- Verification shows "No shares found"
- Etherscan shows "Failed" status

---

## 🛠️ Debug Mode

### Enable Verbose Logging:

Add to your activity:

```kotlin
// In onCreate or before sharing
Log.d("TEST", "=== STARTING SHARE TEST ===")
Log.d("TEST", "User Address: ${WalletManager.getAddress()}")
Log.d("TEST", "Contract: ${BlockchainService.CONTRACT_ADDRESS}")

// After sharing
lifecycleScope.launch {
    delay(5000) // Wait 5 seconds
    val summary = BlockchainService.getSharesSummary()
    Log.d("TEST", "Blockchain Summary:\n$summary")
}
```

### Check All Logs:
```bash
# In Android Studio terminal
adb logcat | grep -E "(BlockchainService|ShareRecord|TEST)"
```

---

## 🎯 Quick Test Checklist

Use this for rapid testing:

- [ ] Wallet connected
- [ ] Share button works
- [ ] Wallet opens for approval
- [ ] Transaction confirms
- [ ] Success dialog shows TX hash
- [ ] TX hash verifiable on Etherscan
- [ ] Long-press verification works
- [ ] Share appears in list
- [ ] Statistics update
- [ ] Can view share details

---

## 💡 Tips

1. **Use Sepolia testnet** - Free test ETH available
2. **Get test ETH** from: https://sepoliafaucet.com/
3. **Keep transaction hashes** for debugging
4. **Test multiple shares** to verify list handling
5. **Test expiry dates** with different durations
6. **Test different categories** (Vaccination, Medication, etc.)

---

## 📞 Support

If testing fails consistently:
1. Check contract is deployed at: `0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B`
2. Verify Sepolia RPC is accessible
3. Check wallet has sufficient test ETH
4. Review Logcat for detailed errors
5. Use long-press verification to see blockchain state

---

**Last Updated:** December 17, 2025
**Contract Version:** HealthWalletV2
**Network:** Sepolia Testnet
