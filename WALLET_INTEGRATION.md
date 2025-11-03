# WalletConnect AppKit Integration - Complete Implementation

## Overview
This project includes a comprehensive WalletConnect/Reown AppKit integration with session management, one-click authentication (SIWE), and wallet state management.

## Features Implemented

### 1. **Wallet Connection**
- Connect to MetaMask, Zerion, Crypto.com DeFi Wallet
- Only Ethereum mainnet supported
- Automatic session restoration on app restart
- Clean connection/disconnection flow

### 2. **Session Management** (`WalletManager`)
- Centralized wallet state management using Kotlin Flow
- Real-time connection state updates
- Persistent session handling across app restarts
- Automatic cleanup on disconnect

### 3. **One-Click Authentication (SIWE)**
- Sign-In With Ethereum (EIP-4361) support
- One-click auth for compatible wallets
- Automatic fallback to session proposal for non-compatible wallets
- Message signing for address ownership verification

### 4. **Delegate System**
All wallet events are handled through `AppKit.ModalDelegate`:
- `onSessionApproved` - Wallet connection approved
- `onSessionRejected` - Connection rejected
- `onSessionUpdate` - Session updated
- `onSessionDelete` - Session terminated
- `onSessionAuthenticateResponse` - One-click auth response
- `onSIWEAuthenticationResponse` - SIWE message signing response
- `onConnectionStateChange` - Connection state changes
- `onError` - Error handling

### 5. **UI Integration**
- Real-time wallet status display
- Connected address formatting (0x1234...5678)
- Connect/Disconnect button with state management
- Toast notifications for all wallet events
- Disconnect confirmation dialog
- **Detailed wallet information screen** with:
  - Full wallet address with copy functionality
  - Connection status indicator (green/orange/gray/red)
  - Network/Chain information
  - Session details (topic, expiry time)
  - Disconnect option

## Project Structure

```
app/src/main/java/com/fyp/blockchainhealthwallet/
├── HealthWalletApplication.kt    # AppKit initialization & config
├── MainActivity.kt                # UI integration & wallet controls
├── WalletInfoActivity.kt         # Detailed wallet information screen
├── wallet/
│   └── WalletManager.kt          # Session & state management
└── MainFragment.kt                # Navigation placeholder
```

## Key Classes

### `WalletManager` (Singleton)
Manages all wallet-related state and delegates:

**State Flows:**
- `connectionState: StateFlow<WalletConnectionState>` - Current connection state
- `currentSession: StateFlow<ApprovedSession?>` - Active session
- `walletAddress: StateFlow<String?>` - Connected wallet address
- `chainId: StateFlow<String?>` - Current chain ID

**Methods:**
- `initialize()` - Set up delegate and restore sessions
- `disconnectWallet()` - Disconnect all active sessions
- `isConnected()` - Check connection status
- `getAddress()` - Get full wallet address
- `getFormattedAddress()` - Get shortened address (0x1234...5678)

### `HealthWalletApplication`
Initializes CoreClient and AppKit with:
- Project ID configuration
- Recommended wallets list
- One-Click Auth (SIWE) configuration
- Ethereum mainnet only
- WalletManager initialization

### `MainActivity`
UI integration with:
- Wallet connection button
- Real-time status updates using Flow collection
- Disconnect dialog
- Navigation setup for AppKit modal
- Quick access to wallet info screen

### `WalletInfoActivity`
Detailed wallet information screen showing:
- Full wallet address with copy-to-clipboard
- Connection status with visual indicator
- Network/Chain information
- Session details (topic and expiry)
- Disconnect functionality

## Configuration

### Project ID
Set in `HealthWalletApplication.kt`:
```kotlin
val projectId = "6c3de96f85d19576c9ad76f1143a6d37"
```
Get yours at: https://dashboard.reown.com/

### Recommended Wallets
Currently configured:
- MetaMask: `c57ca95b47569778a828d19178114f4db188b89b763c899ba0be274e97267d96`
- Zerion: `ecc4036f814562b41a5268adc86270fba1365471402006302e70169465b7ac18`
- Crypto.com: `a395dbfc92b5519cbd1cc6937a4e79830187daaeb2c6fcdf9b9cee0f143844f7`

Find more wallet IDs at: https://walletguide.reown.com

### One-Click Auth Configuration
```kotlin
val authPayloadParams = Modal.Model.AuthPayloadParams(
    chains = listOf("eip155:1"),
    domain = "kotlin.reown.com",
    nonce = generateNonce(),
    uri = "https://kotlin.reown.com",
    statement = "Sign in to Blockchain Health Wallet",
    resources = emptyList(),
    methods = listOf("personal_sign", "eth_signTypedData")
)
```

## Usage

### Connect Wallet
```kotlin
// In Activity/Fragment
val navHostFragment = supportFragmentManager
    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
    
navHostFragment?.navController?.openAppKit(
    shouldOpenChooseNetwork = true,
    onError = { error -> 
        // Handle error
    }
)
```

### View Wallet Information
```kotlin
// After wallet is connected
startActivity(Intent(this, WalletInfoActivity::class.java))
```

The button automatically changes:
- **"Connect Wallet"** (blue) when disconnected
- **"View Wallet"** (green) when connected

You can also click on the wallet address in the header to open the wallet info screen.

### Observe Wallet State
```kotlin
lifecycleScope.launch {
    WalletManager.connectionState.collect { state ->
        when (state) {
            is WalletConnectionState.Connected -> {
                val address = state.address
                val chainId = state.chainId
            }
            is WalletConnectionState.Disconnected -> {
                // Wallet disconnected
            }
            is WalletConnectionState.Connecting -> {
                // Connection in progress
            }
            is WalletConnectionState.Error -> {
                // Handle error: state.message
            }
        }
    }
}
```

### Disconnect Wallet
```kotlin
WalletManager.disconnectWallet()
```

### Get Wallet Info
```kotlin
val isConnected = WalletManager.isConnected()
val address = WalletManager.getAddress()
val shortAddress = WalletManager.getFormattedAddress()
val chainId = WalletManager.getChainId()
val session = WalletManager.getActiveSession()
```

## Dependencies

```kotlin
// Reown AppKit
implementation(platform("com.reown:android-bom:1.4.12"))
implementation("com.reown:android-core")
implementation("com.reown:appkit")

// Navigation
implementation("androidx.navigation:navigation-fragment-ktx:2.8.4")
implementation("androidx.navigation:navigation-ui-ktx:2.8.4")

// Lifecycle & Coroutines
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

## Flow Diagram

```
App Launch
    ↓
HealthWalletApplication.onCreate()
    ↓
Initialize CoreClient → Initialize AppKit → Initialize WalletManager
    ↓
Check for existing sessions → Restore if found
    ↓
MainActivity.onCreate()
    ↓
Observe WalletManager.connectionState (Flow)
    ↓
User clicks "Connect Wallet"
    ↓
Open AppKit Modal → User selects wallet
    ↓
Wallet approves connection
    ↓
onSessionApproved() called → Update WalletManager state
    ↓
UI updates automatically via Flow collection
    ↓
Connected state displayed
```

## Security Notes

1. **Nonce Generation**: Random 32-character alphanumeric string for SIWE
2. **Session Persistence**: Sessions are automatically restored on app restart
3. **Address Verification**: One-Click Auth verifies address ownership
4. **SIWE Compliance**: Follows EIP-4361 standard

## Troubleshooting

### Session not persisting
- Ensure `CoreClient.initialize()` is called in Application class
- Check that `WalletManager.initialize()` is called after AppKit initialization

### Modal not opening
- Verify `nav_host_fragment` exists in layout
- Check navigation graph is set up correctly
- Ensure Fragment container has correct ID

### Wallet not connecting
- Verify Project ID is valid
- Check internet connection
- Ensure wallet app is installed on device
- Check AndroidManifest has INTERNET permission

## Future Enhancements

Potential additions:
- [ ] Theme customization (dark/light mode)
- [ ] Multiple chain support
- [ ] Transaction signing
- [ ] Smart contract interactions
- [ ] ENS name resolution
- [ ] Wallet balance display
- [ ] Transaction history
- [ ] Gas estimation

## References

- [Reown AppKit Documentation](https://docs.reown.com/appkit/android)
- [One-Click Auth Guide](https://docs.reown.com/appkit/android/core/one-click-auth)
- [WalletGuide](https://walletguide.reown.com)
- [EIP-4361 (SIWE)](https://eips.ethereum.org/EIPS/eip-4361)
