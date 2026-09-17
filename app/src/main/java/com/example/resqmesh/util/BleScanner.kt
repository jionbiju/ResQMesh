package com.example.resqmesh.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
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
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val _foundPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val foundPeers = _foundPeers.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanRecord = result.scanRecord
            val serviceUuids = scanRecord?.serviceUuids
            
            // Manual filter for our Service UUID
            if (serviceUuids?.any { it.uuid == BleAdvertiser.SERVICE_UUID } == true) {
                val device = result.device
                val peerName = scanRecord.deviceName ?: device.name ?: "ResQmesh Node"
                val newPeer = DiscoveredPeer(device.address, peerName, result.rssi)
                
                val currentList = _foundPeers.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.id == newPeer.id }
                
                if (existingIndex == -1) {
                    currentList.add(newPeer)
                    _foundPeers.value = currentList
                    android.util.Log.d("BleScanner", "Found new peer: $peerName (${device.address})")
                } else {
                    // Update RSSI and Name if it was unknown
                    val existingPeer = currentList[existingIndex]
                    if (existingPeer.name == "ResQmesh Node" && peerName != "ResQmesh Node") {
                        currentList[existingIndex] = newPeer
                        _foundPeers.value = currentList
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // Could add error reporting here
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        android.util.Log.d("BleScanner", "Attempting to start scan...")
        val scanner = bleScanner
        if (scanner == null) {
            android.util.Log.e("BleScanner", "BluetoothLeScanner is null. Is Bluetooth ON?")
            return
        }

        if (bluetoothAdapter?.isEnabled == true) {
            _foundPeers.value = emptyList()
            
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build()

            try {
                // Scanning without filter and filtering manually in onScanResult for maximum compatibility
                scanner.startScan(null, settings, scanCallback)
                android.util.Log.d("BleScanner", "Scan started successfully (no-filter mode)")
            } catch (e: Exception) {
                android.util.Log.e("BleScanner", "Error starting scan: ${e.message}")
            }
        } else {
            android.util.Log.w("BleScanner", "Bluetooth is disabled, cannot start scan")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
    }
}
