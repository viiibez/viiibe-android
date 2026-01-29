package com.viiibe.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.viiibe.app.bluetooth.BikeConnectionService
import com.viiibe.app.ui.ViiibeApp
import com.viiibe.app.ui.theme.ViiibeTheme

class MainActivity : ComponentActivity() {

    private var bikeService: BikeConnectionService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BikeConnectionService.LocalBinder
            bikeService = binder.getService()
            serviceBound = true

            // Set reference in Application for cross-component access
            (application as ViiibeApplication).bikeService = bikeService

            // Check if returning from overlay mode
            if (bikeService?.isInOverlayMode() == true) {
                bikeService?.stopOverlayMode()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bikeService = null
            serviceBound = false
            (application as ViiibeApplication).bikeService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during workouts
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Bind to the bike connection service
        Intent(this, BikeConnectionService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            ViiibeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ViiibeApp()
                }
            }
        }

        // Handle intent if returning from notification
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Always hide overlay when app is visible
        bikeService?.stopOverlayMode()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_RETURN_FROM_OVERLAY -> {
                // Stop overlay mode when returning to app
                bikeService?.stopOverlayMode()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    companion object {
        const val ACTION_RETURN_FROM_OVERLAY = "com.viiibe.app.ACTION_RETURN_FROM_OVERLAY"
    }
}
