package com.fyp.blockchainhealthwallet

import android.app.Application
import android.util.Log
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.presets.AppKitChainsPresets

class HealthWalletApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Temporarily disable WalletConnect to test if it's causing the crash
        // TODO: Re-enable after fixing the crash
        /*
        try {
            initializeWalletConnect()
        } catch (e: Exception) {
            Log.e("HealthWalletApp", "Failed to initialize WalletConnect", e)
        }
        */
        Log.d("HealthWalletApp", "Application started (WalletConnect disabled)")
    }
    
    private fun initializeWalletConnect() {
        try {
            val projectId = "6c3de96f85d19576c9ad76f1143a6d37"

            val appMetaData = Core.Model.AppMetaData(
                name = "Kotlin.AppKit",
                description = "Kotlin AppKit Implementation",
                url = "kotlin.reown.com",
                icons = listOf("https://gblobscdn.gitbook.com/spaces%2F-LJJeCjcLrr53DcT1Ml7%2Favatar.png?alt=media"),
                redirect = "kotlin-modal-wc://request"
            )

            CoreClient.initialize(
                application = this,
                projectId = projectId,
                metaData = appMetaData
            ) { error ->
                Log.e("CoreClient", "Initialization error: ${error.throwable.message}", error.throwable)
            }

            AppKit.initialize(
                init = Modal.Params.Init(CoreClient),
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
        // Set up supported blockchain networks
        AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
    }
}
