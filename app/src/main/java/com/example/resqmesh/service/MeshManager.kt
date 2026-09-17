package com.example.resqmesh.service

import android.content.Context
import com.example.resqmesh.util.BleAdvertiser
import com.example.resqmesh.util.BleScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager to persist Mesh Network state across screens.
 */
object MeshManager {
    private var bleScanner: BleScanner? = null
    private var bleAdvertiser: BleAdvertiser? = null
    private var gattServer: GattServerManager? = null

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive = _isMeshActive.asStateFlow()

    fun init(context: Context) {
        if (bleScanner == null) {
            val appTile = context.applicationContext
            bleScanner = BleScanner(appTile)
            bleAdvertiser = BleAdvertiser(appTile)
            gattServer = GattServerManager(appTile)
        }
    }

    fun isInitialized(): Boolean = bleScanner != null

    fun getScanner() = bleScanner
    fun getAdvertiser() = bleAdvertiser
    fun getGattServer() = gattServer

    fun toggleMesh(userName: String) {
        val newState = !_isMeshActive.value
        _isMeshActive.value = newState

        if (newState) {
            bleScanner?.startScan()
            bleAdvertiser?.startAdvertising(userName)
            gattServer?.startServer()
        } else {
            bleScanner?.stopScan()
            bleAdvertiser?.stopAdvertising()
            gattServer?.stopServer()
        }
    }
}
