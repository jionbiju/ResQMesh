package com.example.resqmesh

import android.app.Application
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.service.MeshManager
import com.example.resqmesh.util.ResQStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        // Asynchronously populate active user profile name into MeshManager
        val storage = ResQStorage(this)
        CoroutineScope(Dispatchers.IO).launch {
            val savedName = storage.userName.first()
            if (!savedName.isNullOrBlank()) {
                MeshManager.activeUserName = savedName
            }
        }
    }
}
