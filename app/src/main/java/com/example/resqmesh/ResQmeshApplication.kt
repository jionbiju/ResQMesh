package com.example.resqmesh

import android.app.Application
import com.example.resqmesh.data.repository.ChatRepository

class ResQmeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Load SQLCipher native libraries as early as possible
        System.loadLibrary("sqlcipher")
        
        // Initialize the Secure Local Repository
        ChatRepository.init(this)
    }
}
