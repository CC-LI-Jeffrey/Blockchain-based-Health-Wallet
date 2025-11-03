package com.fyp.blockchainhealthwallet.wallet

import android.util.Log
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    
    sealed class WalletConnectionState {
        object Disconnected : WalletConnectionState()
        object Connecting : WalletConnectionState()
        data class Connected(val address: String, val chainId: String) : WalletConnectionState()
        data class Error(val message: String) : WalletConnectionState()
    }
    
    fun initialize() {
        Log.d(TAG, "WalletManager initializing - setting delegate")
        AppKit.setDelegate(this)
        Log.d(TAG, "Delegate set. Waiting for session events from AppKit...")
    }
    
    private fun checkExistingSession() {
        try {
            // SDK will notify us of existing sessions via delegate callbacks
            // AppKit automatically manages sessions and will trigger onSessionUpdate
            // or onSessionEvent callbacks when a session exists
            Log.d(TAG, "WalletManager ready - waiting for session callbacks")
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkExistingSession", e)
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
                _walletAddress.value = address
                _chainId.value = "1" // Ethereum mainnet
                _connectionState.value = WalletConnectionState.Connected(address, "1")
                Log.d(TAG, "Extracted address: $address")
            } else {
                // Fallback: just show as connected
                _connectionState.value = WalletConnectionState.Connected("Unknown", "1")
                Log.w(TAG, "Could not extract address from session")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting session info: ${e.message}")
            _connectionState.value = WalletConnectionState.Connected("Error", "1")
        }
    }
    
    fun disconnectWallet() {
        try {
            val session = _currentSession.value
            if (session != null) {
                AppKit.disconnect(
                    onSuccess = {
                        Log.d(TAG, "Successfully disconnected")
                        clearSessionData()
                    },
                    onError = { error ->
                        Log.e(TAG, "Error disconnecting: ${error.message}")
                        clearSessionData()
                    }
                )
            } else {
                clearSessionData()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
            clearSessionData()
        }
    }
    
    private fun clearSessionData() {
        _currentSession.value = null
        _walletAddress.value = null
        _chainId.value = null
        _connectionState.value = WalletConnectionState.Disconnected
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
        Log.d(TAG, "Session deleted")
        clearSessionData()
    }
    
    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
        Log.d(TAG, "Session request response received")
    }
    
    override fun onSessionAuthenticateResponse(response: Modal.Model.SessionAuthenticateResponse) {
        Log.d(TAG, "Authentication response received")
        when (response) {
            is Modal.Model.SessionAuthenticateResponse.Result -> {
                // Log the session info
                Log.d(TAG, "Session authenticated: ${response.session}")
                // Note: response.session is Modal.Model.Session, not ApprovedSession
                _connectionState.value = WalletConnectionState.Connected("Unknown", "Unknown")
            }
            is Modal.Model.SessionAuthenticateResponse.Error -> {
                Log.e(TAG, "Authentication error: $response")
                _connectionState.value = WalletConnectionState.Error("Authentication failed")
            }
        }
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
        return _chainId.value
    }
}
