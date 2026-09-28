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
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.service.GattClientManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val rssi: Int,
    val hops: Int = 1,
    val relayedBy: String? = null
)

class BleScanner(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val _foundPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val foundPeers = _foundPeers.asStateFlow()

    fun addRelayedPeer(peerId: String, peerName: String, relayedByAddress: String) {
        if (peerId.isBlank() || peerId == "02:00:00:00:00:00" || peerId == "ME" || peerId == "BROADCAST" || peerId == relayedByAddress) return
        val currentList = _foundPeers.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == peerId }
        
        if (existingIndex == -1) {
            val relayedPeer = DiscoveredPeer(
                id = peerId,
                name = peerName,
                rssi = -85,
                hops = 2,
                relayedBy = relayedByAddress
            )
            currentList.add(relayedPeer)
            _foundPeers.value = currentList
            android.util.Log.d("BleScanner", "Discovered Relayed Peer (2 Hops): $peerName ($peerId) via $relayedByAddress")
        }
    }

    fun updatePeerName(peerId: String, newName: String) {
        if (newName.isBlank() || newName == "User" || newName == "ResQmesh Node") return
        val currentList = _foundPeers.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == peerId }
        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            if (existing.name != newName) {
                currentList[existingIndex] = existing.copy(name = newName)
                _foundPeers.value = currentList
                android.util.Log.d("BleScanner", "Updated peer $peerId name to: $newName")
            }
        }
    }

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

                    // STORE-AND-FORWARD: Forward pending messages to newly discovered peer
                    forwardPendingMessagesToPeer(device.address)
                } else {
                    // Update RSSI and Name if name was generic ("User" or "ResQmesh Node")
                    val existingPeer = currentList[existingIndex]
                    val isGeneric = existingPeer.name == "ResQmesh Node" || existingPeer.name == "User"
                    val isBetter = peerName != "ResQmesh Node" && peerName != "User" && peerName.isNotBlank()

                    if (isGeneric && isBetter) {
                        currentList[existingIndex] = existingPeer.copy(name = peerName, rssi = result.rssi)
                        _foundPeers.value = currentList
                    } else {
                        // Always keep RSSI fresh
                        currentList[existingIndex] = existingPeer.copy(rssi = result.rssi)
                        _foundPeers.value = currentList
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }

    private fun forwardPendingMessagesToPeer(peerAddress: String) {
        val pendingMessages = ChatRepository.getPendingStoreAndForwardMessages()
        if (pendingMessages.isNotEmpty()) {
            android.util.Log.d("BleScanner", "Store-and-Forward: Forwarding ${pendingMessages.size} pending messages to $peerAddress")
            val clientManager = GattClientManager(context)
            for (msg in pendingMessages) {
                val forwardMsg = msg.copy(ttl = msg.ttl - 1)
                clientManager.relayMeshMessage(peerAddress, forwardMsg)
            }
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
