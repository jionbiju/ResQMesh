package com.example.resqmesh

import android.app.Application
import com.example.resqmesh.data.repository.ChatRepository
import org.maplibre.android.MapLibre

class ResQmeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize MapLibre SDK globally before any View inflation
        MapLibre.getInstance(this)

        // Load SQLCipher native libraries as early as possible
        System.loadLibrary("sqlcipher")
        
        // Initialize the Secure Local Repository
        ChatRepository.init(this)
    }
}
