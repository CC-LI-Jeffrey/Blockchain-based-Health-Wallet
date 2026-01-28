package com.fyp.blockchainhealthwallet

import android.app.Application
import android.util.Log
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.presets.AppKitChainsPresets

class HealthWalletApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize WalletConnect first
            initializeWalletConnect()
            // Initialize WalletManager after AppKit
            WalletManager.initialize()
        } catch (e: Exception) {
            Log.e("HealthWalletApp", "Failed to initialize WalletConnect", e)
        }
    }
    
    private fun initializeWalletConnect() {
        try {
            val projectId = "6c3de96f85d19576c9ad76f1143a6d37"

            val appMetaData = Core.Model.AppMetaData(
                name = "Health Wallet",
                description = "Blockchain-based Health Records",
                url = "https://healthwallet.app",
                icons = listOf("https://gblobscdn.gitbook.com/spaces%2F-LJJeCjcLrr53DcT1Ml7%2Favatar.png?alt=media"),
                redirect = "healthwallet://request"  // MUST match AndroidManifest scheme
            )

            CoreClient.initialize(
                application = this,
                projectId = projectId,
                metaData = appMetaData
            ) { error ->
                Log.e("CoreClient", "Initialization error: ${error.throwable.message}", error.throwable)
            }

            // Specific wallet IDs - only show MetaMask, Zerion, Crypto.com, and Onchain
            val recommendedWalletIds = listOf(
                "c57ca95b47569778a828d19178114f4db188b89b763c899ba0be274e97267d96", // MetaMask
                "ecc4036f814562b41a5268adc86270fba1365471402006302e70169465b7ac18", // Zerion
                "a395dbfc92b5519cbd1cc6937a4e79830187daaeb2c6fcdf9b9cee0f143844f7"  // Crypto.com DeFi Wallet
                // Note: Add Onchain wallet ID if available from https://walletguide.reown.com
            )

        // Configure initialization parameters
        val initParams = Modal.Params.Init(
            core = CoreClient,
            recommendedWalletsIds = recommendedWalletIds
        )
        
        AppKit.initialize(
            init = initParams,
            onSuccess = {
                try {
                    setupChains()
                    Log.d("AppKit", "Successfully initialized")
                } catch (e: Exception) {
                    Log.e("AppKit", "Error setting up chains", e)
                }
            },
            onError = { error ->
                Log.e("AppKit", "Initialization error: $error")
            }
        )
        } catch (e: Exception) {
            Log.e("HealthWalletApp", "Error during WalletConnect initialization", e)
        }
    }
    
    private fun setupChains() {
        val chains = mutableListOf<Modal.Model.Chain>()
        
        // ONLY ADD SEPOLIA - Remove mainnet to prevent wrong network transactions
        // Sepolia Testnet
        val sepoliaChain = Modal.Model.Chain(
            chainNamespace = "eip155",
            chainReference = "11155111",
            chainName = "Sepolia",
            requiredMethods = listOf(),  // NO required methods
            optionalMethods = listOf(
                "eth_sendTransaction",
                "personal_sign",
                "eth_signTypedData",
                "eth_signTypedData_v4",
                "eth_sign"
            ),
            events = listOf("chainChanged", "accountsChanged"),
            token = Modal.Model.Token(
                name = "Sepolia ETH",
                symbol = "ETH",
                decimal = 18
            ),
            rpcUrl = "https://rpc.sepolia.org",
            blockExplorerUrl = "https://sepolia.etherscan.io"
        )
        chains.add(sepoliaChain)
        
        // Set chains in AppKit
        AppKit.setChains(chains)
        
        Log.d("AppKit", "Configured Sepolia Testnet (Chain ID: 11155111)")
    }
}