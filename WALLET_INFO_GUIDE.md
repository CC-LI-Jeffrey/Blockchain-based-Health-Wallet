# Wallet Information Feature

## Overview
A dedicated screen to view detailed information about your connected wallet.

## How to Access

1. **Connect your wallet first** - Click "Connect Wallet" on the main screen
2. **View wallet details** - After connecting, the button changes to "View Wallet" (green)
3. **Click "View Wallet"** or **tap on your wallet address** in the header

## Wallet Information Display

### Connection Status
- **Green indicator** = Connected
- **Orange indicator** = Connecting
- **Gray indicator** = Disconnected
- **Red indicator** = Error

### Wallet Address Section
- Full wallet address displayed in monospace font
- **Copy Address button** - One-click copy to clipboard
- Shows formatted address (0x1234...5678) in main screen
- Shows full address in wallet info screen

### Network Information
- Current blockchain network (e.g., "Ethereum Mainnet (1)")
- Chain ID displayed
- Supported networks:
  - Ethereum Mainnet (1)
  - Goerli Testnet (5)
  - Sepolia Testnet (11155111)

### Session Information
- **Session Topic** - Unique identifier for the WalletConnect session
- **Session Expiry** - When the connection will expire
  - Format: MMM dd, yyyy HH:mm:ss
  - Example: "Nov 04, 2025 14:30:45"

## Actions Available

### Copy Address
- Click "Copy Address" button
- Address is copied to clipboard
- Confirmation toast appears

### Disconnect Wallet
- Click red "Disconnect Wallet" button at bottom
- Confirmation dialog appears
- Choosing "Disconnect" will:
  - End the WalletConnect session
  - Clear all wallet data
  - Return to main screen
  - Update UI to "not connected" state

## UI Flow

```
Main Screen
    ↓
Connected wallet address shown in header
    ↓
Click "View Wallet" button (green)
    OR
Click on wallet address in header
    ↓
Wallet Info Screen opens
    ↓
View full details:
- Connection status
- Full wallet address
- Network/Chain info
- Session details
    ↓
Copy address or Disconnect
```

## Real-time Updates

The wallet information screen automatically updates when:
- Connection state changes
- Wallet gets disconnected
- Network switches (if multi-chain enabled)
- Session information changes

All updates happen through Kotlin Flow, so the UI is always in sync with the actual wallet state.

## Error Handling

If any errors occur:
- Connection status shows "Error: [message]"
- Red indicator appears
- Copy and disconnect buttons are disabled
- Error message displayed in status

## Tips

1. **Check expiry regularly** - Sessions expire after a certain time
2. **Copy address carefully** - Always verify the copied address
3. **Session topic** - Keep this for debugging if issues occur
4. **Disconnect when done** - For security, disconnect when not using the app

## Troubleshooting

**Address not showing?**
- Wait a moment, it may still be connecting
- Check your internet connection
- Try disconnecting and reconnecting

**Session expired?**
- Disconnect and reconnect your wallet
- This creates a fresh session

**Can't copy address?**
- Make sure wallet is fully connected
- Button is only enabled when connected

**Back button not working?**
- Use the back arrow in the header
- Or use device back button
