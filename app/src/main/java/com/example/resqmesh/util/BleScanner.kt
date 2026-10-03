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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
        
        // SELF-FILTER: Do NOT add local user to their own peer list!
        val storage = ResQStorage(context)
        val myName = runBlocking { storage.userName.first() } ?: ""
        if (myName.isNotBlank() && (peerId.equals(myName, ignoreCase = true) || peerName.equals(myName, ignoreCase = true))) {
            return
        }

        val currentList = _foundPeers.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { 
            it.id == peerId || it.name.equals(peerName, ignoreCase = true) || it.name.equals(peerId, ignoreCase = true) 
        }
        
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
        } else {
            val existing = currentList[existingIndex]
            // Keep 1-Hop Direct classification if already direct!
            if (existing.hops > 1) {
                currentList[existingIndex] = existing.copy(relayedBy = relayedByAddress)
                _foundPeers.value = currentList
            }
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
                
                // Do NOT add local user to their own scanner list!
                val storage = ResQStorage(context)
                val myName = runBlocking { storage.userName.first() } ?: ""
                if (myName.isNotBlank() && peerName.equals(myName, ignoreCase = true)) {
                    return
                }

                val newPeer = DiscoveredPeer(device.address, peerName, result.rssi)
                
                val currentList = _foundPeers.value.toMutableList()
                val hasRealName = peerName.isNotBlank() && peerName != "ResQmesh Node" && peerName != "User"
                
                // Deduplicate by MAC ID OR by Person Profile Name (handles Android MAC randomization)
                val existingIndex = currentList.indexOfFirst { 
                    it.id == newPeer.id || (hasRealName && it.name.equals(peerName, ignoreCase = true)) 
                }
                
                if (existingIndex == -1) {
                    currentList.add(newPeer)
                    _foundPeers.value = currentList
                    android.util.Log.d("BleScanner", "Found new peer: $peerName (${device.address})")

                    // STORE-AND-FORWARD: Forward pending messages to newly discovered peer
                    forwardPendingMessagesToPeer(device.address)
                } else {
                    // Update MAC address (if rotated), Name, and RSSI on existing person entry
                    val existing = currentList[existingIndex]
                    val updatedName = if (hasRealName) peerName else existing.name
                    
                    currentList[existingIndex] = existing.copy(
                        id = newPeer.id, // Keep active MAC address updated!
                        name = updatedName,
                        rssi = result.rssi
                    )
                    _foundPeers.value = currentList
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
            // Keep existing 2-hop relayed peers when refreshing 1-hop scan
            _foundPeers.value = _foundPeers.value.filter { it.hops > 1 }
            
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
