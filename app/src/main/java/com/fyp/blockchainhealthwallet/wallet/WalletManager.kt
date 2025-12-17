package com.fyp.blockchainhealthwallet.wallet

import android.util.Log
import com.fyp.blockchainhealthwallet.blockchain.CategoryKeyManager
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.android.CoreClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.Continuation

object WalletManager : AppKit.ModalDelegate {
    private const val TAG = "WalletManager"
    
    // State flows for reactive state management
    private val _connectionState = MutableStateFlow<WalletConnectionState>(WalletConnectionState.Disconnected)
    val connectionState: StateFlow<WalletConnectionState> = _connectionState.asStateFlow()
    
    private val _currentSession = MutableStateFlow<Modal.Model.ApprovedSession?>(null)
    val currentSession: StateFlow<Modal.Model.ApprovedSession?> = _currentSession.asStateFlow()
    
    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()
    
    private val _chainId = MutableStateFlow<String?>(null)
    val chainId: StateFlow<String?> = _chainId.asStateFlow()
    
    // Track pending transaction request continuations
    private val pendingTransactions = mutableMapOf<Long, Continuation<String>>()
    
    sealed class WalletConnectionState {
        object Disconnected : WalletConnectionState()
        object Connecting : WalletConnectionState()
        data class Connected(val address: String, val chainId: String) : WalletConnectionState()
        data class Error(val message: String) : WalletConnectionState()
    }
    
    fun initialize() {
        Log.d(TAG, "WalletManager initializing - setting delegate")
        AppKit.setDelegate(this)
        Log.d(TAG, "Delegate set. Checking for existing session...")
        
        // Check for existing session from AppKit's storage
        checkExistingSession()
    }
    
    private fun checkExistingSession() {
        try {
            Log.d(TAG, "Attempting to detect existing sessions...")
            
            // CRITICAL FIX: Validate session on app startup
            // AppKit may have cached session data that is no longer valid
            val account = try {
                AppKit.getAccount()
            } catch (e: Exception) {
                Log.w(TAG, "Cannot get account: ${e.message}")
                null
            }
            
            if (account != null) {
                // Session appears to exist - verify it's actually valid
                Log.d(TAG, "Found cached account: ${account.address}")
                
                try {
                    val selectedChain = AppKit.getSelectedChain()
                    // Default to Sepolia testnet (11155111) instead of mainnet
                    val chainId = selectedChain?.chainReference ?: "11155111"
                    
                    _walletAddress.value = account.address
                    _chainId.value = chainId
                    _connectionState.value = WalletConnectionState.Connected(account.address, chainId)
                    
                    Log.d(TAG, "Session restored successfully")
                    Log.d(TAG, "   Address: ${account.address}")
                    Log.d(TAG, "   Chain: $chainId")
                } catch (e: Exception) {
                    Log.w(TAG, "Session appears stale, clearing: ${e.message}")
                    clearAndDisconnectStaleSession()
                }
            } else {
                // No account = no valid session
                Log.d(TAG, "No existing session found")
                _connectionState.value = WalletConnectionState.Disconnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkExistingSession: ${e.message}", e)
            _connectionState.value = WalletConnectionState.Disconnected
        }
    }
    
    private fun updateSessionInfo(session: Modal.Model.ApprovedSession) {
        _currentSession.value = session
        
        // Log the session object to understand its structure
        Log.d(TAG, "Session received: $session")
        
        // Try to extract address from the session's toString representation
        // The session likely contains account info in format "namespace:chainId:address"
        try {
            val sessionStr = session.toString()
            Log.d(TAG, "Session full string: $sessionStr")
            
            // Try to find ethereum address pattern (0x followed by 40 hex characters)
            val addressRegex = Regex("(0x[a-fA-F0-9]{40})")
            val matchResult = addressRegex.find(sessionStr)
            
            if (matchResult != null) {
                val address = matchResult.value
                // Try to get actual chain ID
                val selectedChain = try { AppKit.getSelectedChain() } catch (e: Exception) { null }
                // Default to Sepolia testnet (11155111) instead of mainnet
                val chainId = selectedChain?.chainReference ?: "11155111"
                
                _walletAddress.value = address
                _chainId.value = chainId
                _connectionState.value = WalletConnectionState.Connected(address, chainId)
                Log.d(TAG, "Extracted address: $address")
                Log.d(TAG, "Chain ID: $chainId")
                
                // Notify CategoryKeyManager that wallet connected
                // This derives master key from wallet address for encryption
                CategoryKeyManager.onWalletConnected(address)
            } else {
                // Fallback: just show as connected with Sepolia
                _connectionState.value = WalletConnectionState.Connected("Unknown", "11155111")
                Log.w(TAG, "Could not extract address from session")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting session info: ${e.message}")
            _connectionState.value = WalletConnectionState.Connected("Error", "11155111")
        }
    }
    
    fun disconnectWallet() {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "DISCONNECTING WALLET")
            Log.d(TAG, "========================================")
            
            // CRITICAL: Clear session data FIRST to update UI immediately
            // This prevents race conditions where UI shows disconnected but session persists
            clearSessionData()
            
            // Then disconnect from AppKit and all pairings
            try {
                AppKit.disconnect(
                    onSuccess = {
                        Log.d(TAG, "AppKit.disconnect() successful")
                        disconnectAllPairings()
                        Log.d(TAG, "Session fully disconnected")
                    },
                    onError = { error ->
                        Log.e(TAG, "Error disconnecting: ${error.message}")
                        // Still try to clear pairings
                        disconnectAllPairings()
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Exception calling AppKit.disconnect(): ${e.message}")
                // Still try to disconnect pairings
                disconnectAllPairings()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
            
            // Force cleanup even on error
            clearSessionData()
            disconnectAllPairings()
        }
    }
    
    /**
     * CRITICAL: Disconnect all pairings to fully terminate WalletConnect sessions
     * This ensures MetaMask also sees the disconnection
     */
    private fun disconnectAllPairings() {
        try {
            Log.d(TAG, "Cleaning up all pairings...")
            
            // Access the Core API to get and disconnect all pairings
            val pairings = CoreClient.Pairing.getPairings()
            
            Log.d(TAG, "Found ${pairings.size} pairing(s) to disconnect")
            
            pairings.forEach { pairing ->
                try {
                    Log.d(TAG, "  Disconnecting pairing: ${pairing.topic.take(10)}...")
                    CoreClient.Pairing.disconnect(pairing.topic) { error ->
                        if (error != null) {
                            Log.w(TAG, "    Error disconnecting pairing: ${error.throwable.message}")
                        } else {
                            Log.d(TAG, "    Pairing disconnected")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "    ⚠️ Exception disconnecting pairing: ${e.message}")
                }
            }
            
            Log.d(TAG, "Pairing cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing pairings: ${e.message}", e)
        }
    }
    
    /**
     * Clear stale session that appears connected but isn't valid
     */
    private fun clearAndDisconnectStaleSession() {
        Log.w(TAG, "Clearing stale session...")
        
        try {
            // Force disconnect
            AppKit.disconnect(
                onSuccess = {
                    Log.d(TAG, "Stale session disconnected")
                    disconnectAllPairings()
                },
                onError = { 
                    Log.w(TAG, "Error disconnecting stale session: ${it.message}")
                    disconnectAllPairings()
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Exception clearing stale session: ${e.message}")
            disconnectAllPairings()
        }
        
        clearSessionData()
    }
    
    private fun clearSessionData() {
        _currentSession.value = null
        _walletAddress.value = null
        _chainId.value = null
        _connectionState.value = WalletConnectionState.Disconnected
        
        // Clear cached encryption keys on wallet disconnect
        CategoryKeyManager.clearCache()
        
        Log.d(TAG, "Session data cleared")
    }
    
    // ModalDelegate implementation
    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
        Log.d(TAG, "Session approved: ${approvedSession}")
        
        // ApprovedSession itself IS the session we need
        Log.d(TAG, "Processing approved session")
        updateSessionInfo(approvedSession)
    }
    
    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
        Log.d(TAG, "Session rejected: ${rejectedSession.reason}")
        _connectionState.value = WalletConnectionState.Error("Session rejected: ${rejectedSession.reason}")
    }
    
    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
        Log.d(TAG, "Session updated")
        // Reload session info
    }
    
    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
        Log.d(TAG, "Session deleted: $deletedSession")
        clearSessionData()
    }
    
    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
        Log.d(TAG, "Session request response: $response")
        // Handle transaction responses or other session requests
    }
    
    override fun onSIWEAuthenticationResponse(response: Modal.Model.SIWEAuthenticateResponse) {
        Log.d(TAG, "SIWE authentication response received")
        when (response) {
            is Modal.Model.SIWEAuthenticateResponse.Result -> {
                Log.d(TAG, "SIWE signature received")
            }
            is Modal.Model.SIWEAuthenticateResponse.Error -> {
                Log.e(TAG, "SIWE error: $response")
            }
        }
    }
    
    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {
        Log.d(TAG, "Session event received: ${sessionEvent.name}")
        // SessionEvent contains event data emitted by the wallet during an active session
        // This is for custom events, not for extracting session info
    }
    
    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {
        Log.d(TAG, "Proposal expired")
        _connectionState.value = WalletConnectionState.Error("Connection proposal expired")
    }
    
    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {
        Log.d(TAG, "Request expired")
    }
    
    override fun onSessionExtend(session: Modal.Model.Session) {
        Log.d(TAG, "Session extended: $session")
        // Session extended, just log it - don't update session info
        // since Modal.Model.Session is different from Modal.Model.ApprovedSession
    }
    
    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {
        Log.d(TAG, "Connection state changed: $state")
        
        // When connection state changes, try to get session info
        // This might indicate an existing session has been restored by AppKit
        if (state.isAvailable) {
            Log.d(TAG, "Connection is available, validating actual session...")
            
            try {
                // CRITICAL: Verify the session is actually valid by checking account
                val account = AppKit.getAccount()
                
                if (account == null) {
                    // Session appears available but account is null = stale session
                    Log.w(TAG, "Session appears available but account is NULL - clearing stale session")
                    clearSessionData()
                    
                    // Try to force disconnect the stale session
                    try {
                        AppKit.disconnect(
                            onSuccess = { Log.d(TAG, "Cleared stale session") },
                            onError = { Log.w(TAG, "Error clearing stale session: ${it.message}") }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not disconnect stale session: ${e.message}")
                    }
                    return
                }
                
                val selectedChain = AppKit.getSelectedChain()
                val address = account.address
                // Default to Sepolia testnet (11155111) instead of mainnet
                val chainId = selectedChain?.chainReference ?: "11155111"
                
                _walletAddress.value = address
                _chainId.value = chainId
                _connectionState.value = WalletConnectionState.Connected(address, chainId)
                
                Log.d(TAG, "========================================")
                Log.d(TAG, "CONNECTION STATE CHANGED TO CONNECTED")
                Log.d(TAG, "========================================")
                Log.d(TAG, "Address: $address")
                Log.d(TAG, "Chain ID: $chainId")
                Log.d(TAG, "Chain Name: ${selectedChain?.chainName}")
                Log.d(TAG, "Full Chain Object: $selectedChain")
                Log.d(TAG, "========================================")
            } catch (e: Exception) {
                Log.w(TAG, "Could not retrieve session info on connection state change: ${e.message}")
                Log.w(TAG, "Clearing potentially invalid session")
                clearSessionData()
            }
        } else {
            // Connection is NOT available - clear session data
            Log.d(TAG, "Connection is NOT available - clearing session data")
            clearSessionData()
        }
    }
    
    override fun onError(error: Modal.Model.Error) {
        val errorMsg = error.toString()
        Log.e(TAG, "AppKit error: $errorMsg")
        _connectionState.value = WalletConnectionState.Error(errorMsg)
    }
    
    // Helper methods
    fun isConnected(): Boolean {
        return _connectionState.value is WalletConnectionState.Connected
    }
    
    fun forceRefreshSessionState() {
        Log.d(TAG, "🔄 FORCE REFRESH: Checking actual session state...")
        try {
            val account = AppKit.getAccount()
            if (account == null) {
                Log.w(TAG, "🔄 No account found - clearing stale connection state")
                clearSessionData()
            } else {
                Log.d(TAG, "🔄 Account found: ${account.address}")
                // Session is valid, state should already be correct
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔄 Error during force refresh - clearing state", e)
            clearSessionData()
        }
    }
    
    fun getActiveSession(): Modal.Model.ApprovedSession? {
        return _currentSession.value
    }
    
    fun getAddress(): String? {
        return _walletAddress.value
    }
    
    fun getFormattedAddress(): String? {
        val address = _walletAddress.value ?: return null
        return if (address.length > 10) {
            "${address.substring(0, 6)}...${address.substring(address.length - 4)}"
        } else {
            address
        }
    }
    
    fun getChainId(): String? {
        val chainId = _chainId.value
        Log.d(TAG, "getChainId() called - returning: $chainId")
        
        // Also check what AppKit reports
        try {
            val selectedChain = AppKit.getSelectedChain()
            Log.d(TAG, "  AppKit.getSelectedChain(): ${selectedChain?.chainReference}")
            Log.d(TAG, "  AppKit chain name: ${selectedChain?.chainName}")
        } catch (e: Exception) {
            Log.e(TAG, "  Error getting selected chain: ${e.message}")
        }
        
        return chainId
    }
}
