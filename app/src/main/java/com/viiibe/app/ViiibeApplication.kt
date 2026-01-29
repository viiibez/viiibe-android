package com.viiibe.app

import android.app.Application
import android.util.Log
import com.viiibe.app.bluetooth.BikeConnectionService
import com.viiibe.app.data.database.ViiibeDatabase
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class ViiibeApplication : Application() {

    val database: ViiibeDatabase by lazy {
        ViiibeDatabase.getDatabase(this)
    }

    // Reference to bike service, set by MainActivity when bound
    var bikeService: BikeConnectionService? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize BouncyCastle FIRST before any other code runs
        // This is needed for the Peloton device which expects BKS keystore
        setupBouncyCastle()

        instance = this
    }

    private fun setupBouncyCastle() {
        try {
            // Insert BouncyCastle at position 1 so it provides BKS keystore
            // before Conscrypt tries to use it
            val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (provider == null) {
                Security.insertProviderAt(BouncyCastleProvider(), 1)
                Log.d(TAG, "BouncyCastle inserted at position 1")
            } else if (provider.javaClass != BouncyCastleProvider::class.java) {
                // Android has a built-in BouncyCastle that's outdated, replace it
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.insertProviderAt(BouncyCastleProvider(), 1)
                Log.d(TAG, "BouncyCastle replaced at position 1")
            } else {
                Log.d(TAG, "BouncyCastle already configured")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup BouncyCastle", e)
        }
    }

    companion object {
        private const val TAG = "ViiibeApplication"
        lateinit var instance: ViiibeApplication
            private set
    }
}
