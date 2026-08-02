package com.example.resqmesh.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val rssi: Int
)

class BleScanner(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val _foundPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val foundPeers = _foundPeers.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val peerName = try { device.name } catch (e: Exception) { null } ?: "ResQmesh Device"
            val newPeer = DiscoveredPeer(device.address, peerName, result.rssi)
            
            val currentList = _foundPeers.value.toMutableList()
            if (!currentList.any { it.id == newPeer.id }) {
                currentList.add(newPeer)
                _foundPeers.value = currentList
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter?.isEnabled == true) {
            _foundPeers.value = emptyList()
            bleScanner?.startScan(scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
    }
}
