# Data Sharing Demo Guide

## Overview
This guide demonstrates the blockchain-based data sharing functionality for your mid-project presentation.

## Features Implemented
✅ **Share Data** - Share personal health information with recipients (doctors, hospitals, etc.)  
✅ **View Shares** - See all active data shares with details  
✅ **Revoke Access** - Revoke recipient access at any time  
✅ **Blockchain Storage** - All shares recorded on Ethereum Sepolia testnet  
✅ **Category-Based Sharing** - Share specific data categories (Personal Info, Medications, etc.)

## Demo Flow

### 1. Setup Personal Information
1. Open the app and connect your wallet
2. Navigate to **Profile/Personal Information** screen
3. Click the edit button (✏️)
4. Choose one:
   - **Quick Demo**: Click "Create Sample Profile" for instant test data
   - **Real Data**: Fill in the form fields manually
5. Click "Save" - transaction will be sent to blockchain
6. Wait for confirmation (shows IPFS hash and transaction hash)

### 2. Share Data with Recipient
1. Navigate to **Share Records** screen
2. Click the **"Share New"** button
3. In the dialog that appears:
   - **Recipient Address**: Enter a valid Ethereum address (e.g., `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb2`)
   - **Category**: "Personal Info (for testing)" is selected by default
   - **Recipient Type**: Choose type (Doctor, Hospital, Clinic, etc.)
   - **Duration**: Enter number of days for access (e.g., 30)
4. Click **"Share Data"**
5. Approve the transaction in WalletConnect
6. Wait for confirmation - you'll see the new share appear in the list

### 3. View Active Shares
1. On the **Share Records** screen, you'll see all active shares
2. Each card shows:
   - Recipient address
   - What data was shared (category)
   - When it was shared (date & time)
   - Share ID
3. The list auto-refreshes when you return to the screen

### 4. Revoke Access
1. Click on any share card to view details
2. Review the share information:
   - Recipient details
   - Shared data category
   - Access level
   - Share & expiry dates
   - Current status (Active/Expired/Revoked)
3. Click **"Revoke Access"** button
4. Confirm in the dialog
5. Approve the transaction in WalletConnect
6. Wait for confirmation - status changes to "Revoked" and the detail screen closes
7. The revoked share will no longer appear in the active list

## Smart Contract Details

**Contract**: HealthWalletV2  
**Address**: `0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B`  
**Network**: Ethereum Sepolia Testnet  
**Explorer**: [View on Sepolia Etherscan](https://sepolia.etherscan.io/address/0x9BFD8A68543f4b7989d567588E8c3e7Cd4c65f9B)

## Key Functions Used
- `setPersonalInfo(bytes32 ipfsHash)` - Store profile IPFS reference
- `getPersonalInfoRef()` - Retrieve profile reference
- `shareData(address recipient, uint8 category, uint8 recipientType, uint256 duration)` - Create share
- `getShareIds(address owner)` - Get all share IDs for user
- `getShareRecord(uint256 shareId)` - Get share details
- `revokeShare(uint256 shareId)` - Revoke access

## Data Categories Available (Current Implementation)
1. **PERSONAL_INFO** (0) - Name, DOB, blood type, contact info ✅ *Available for testing*
2. **MEDICATION_RECORDS** (1) - Prescription history 🔄 *In development*
3. **VACCINATION_RECORDS** (2) - Immunization records 🔄 *In development*
4. **MEDICAL_REPORTS** (3) - Lab results, diagnoses 🔄 *In development*

**Note:** Currently using Personal Info for testing the sharing functionality. Medical records implementation is in progress.

## Recipient Types
- DOCTOR (0)
- HOSPITAL (1)
- CLINIC (2)
- PHARMACY (3)
- INSURANCE (4)
- FAMILY (5)
- OTHER (6)

## Demo Tips

### For Quick Demo
1. **Use Sample Profile**: Fastest way to get data on blockchain
2. **Test Address**: Use `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb2` as recipient
3. **Short Duration**: Enter 1 day for quick expiry testing
4. **Share Personal Info**: Currently the only implemented category for testing sharing functionality

### Talking Points
- **Blockchain Security**: All shares are cryptographically secured on Ethereum
- **User Control**: Only you can revoke access, not the recipient
- **Transparency**: Every share creates an immutable audit trail
- **Category Isolation**: Share only what's needed (principle of least privilege)
- **Time-Limited**: Access automatically expires after duration
- **IPFS Storage**: Actual data stored on distributed network, only hash on chain

### Common Questions

**Q: Why use blockchain?**  
A: Immutable audit trail, user sovereignty, no central authority can modify/delete shares

**Q: What happens to revoked data?**  
A: Recipient can no longer access it via the app, share marked inactive on chain

**Q: Can recipients re-share my data?**  
A: No, the smart contract only allows original owner to create/revoke shares

**Q: What if I lose my wallet?**  
A: You lose access to your health data - this demonstrates importance of key management

**Q: Is this HIPAA compliant?**  
A: This is a research prototype. Production system would need additional privacy layers.

## Troubleshooting

### Transaction Fails
- Ensure you have Sepolia ETH for gas fees
- Check wallet is connected to Sepolia network
- Verify recipient address is valid Ethereum address

### Share Not Appearing
- Pull down to refresh the list
- Check transaction was confirmed on Sepolia Etherscan
- Verify you're viewing shares for the correct wallet address

### RPC Errors
- App uses 3 fallback RPC endpoints
- If all fail, check network connectivity
- Try again in a few seconds

## Next Steps (Post-Demo)

Future enhancements to discuss:
1. **Encryption**: Add end-to-end encryption for IPFS data
2. **Access Logs**: Show who accessed what and when
3. **Recipient View**: Allow recipients to see data shared with them
4. **Emergency Override**: Special category for emergency medical access
5. **Multi-sig**: Require multiple approvals for sensitive shares
6. **ZK Proofs**: Prove data properties without revealing data

## Demo Checklist

Before presentation:
- [ ] Wallet connected with Sepolia ETH
- [ ] Sample profile created and confirmed
- [ ] At least one active share created
- [ ] Test revoke flow once to ensure familiarity
- [ ] Have backup recipient address ready
- [ ] Internet connection stable
- [ ] Sepolia network accessible

Good luck with your presentation! 🚀
