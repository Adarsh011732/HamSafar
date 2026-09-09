package com.thechameleons.chameleonnav

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

class ChameleonNavApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            Configuration.getInstance().load(this, getSharedPreferences("osmdroid_cfg", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = packageName
            Configuration.getInstance().osmdroidBasePath = File(cacheDir, "osmdroid")
            Configuration.getInstance().osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        } catch (_: Exception) {
            // Offline fallback
        }
    }
}
