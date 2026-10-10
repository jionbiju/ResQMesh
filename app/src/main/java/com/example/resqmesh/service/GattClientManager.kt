package com.example.resqmesh.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.security.CryptoHelper
import com.example.resqmesh.util.ResQStorage
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.*

class GattClientManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val gson = Gson()
    private val cryptoHelper = CryptoHelper()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val HEADER_SIZE = 5 // "SOLO:", "STRT:", "DATA:", "DONE:"
    }

    private fun refreshGattCache(gatt: BluetoothGatt?): Boolean {
        return try {
            val refreshMethod = gatt?.javaClass?.getMethod("refresh")
            refreshMethod?.invoke(gatt) as? Boolean ?: false
        } catch (e: Exception) {
            Log.e("GattClient", "Error refreshing GATT cache: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun sendChatMessage(
        deviceAddress: String,
        chatMessage: ChatMessage,
        attempt: Int = 1,
        onResult: (Boolean) -> Unit = {}
    ) {
        val activePeers = MeshManager.getScanner()?.foundPeers?.value ?: emptyList()

        // RESOLVE ADDRESS: Direct MACs connect directly; 2H targets resolve via 1H Relay MAC
        val resolvedAddress: String = run {
            // 1. If deviceAddress is already a valid Bluetooth MAC, connect DIRECTLY to it!
            if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
                return@run deviceAddress
            }

            // 2. Look up in scanner peers
            val peerMatch = activePeers.firstOrNull { 
                it.id == deviceAddress || it.name.equals(deviceAddress, ignoreCase = true) 
            }

            if (peerMatch != null) {
                // Direct 1-Hop peer with valid MAC
                if (peerMatch.hops == 1 && BluetoothAdapter.checkBluetoothAddress(peerMatch.id)) {
                    return@run peerMatch.id
                }
                
                // Relayed 2-Hop peer: Route through 1H Relay neighbor's MAC
                if (peerMatch.hops > 1 && !peerMatch.relayedBy.isNullOrBlank()) {
                    val relayPeer = activePeers.firstOrNull { 
                        (it.id == peerMatch.relayedBy || it.name.equals(peerMatch.relayedBy, ignoreCase = true)) &&
                        it.hops == 1 && BluetoothAdapter.checkBluetoothAddress(it.id)
                    }
                    if (relayPeer != null) {
                        Log.d("GattClient", "Target '$deviceAddress' (2H) routed via 1H Relay MAC '${relayPeer.id}'")
                        return@run relayPeer.id
                    }
                    if (BluetoothAdapter.checkBluetoothAddress(peerMatch.relayedBy)) {
                        return@run peerMatch.relayedBy
                    }
                }
            }

            // 3. Fallback: Find ANY active 1-Hop direct neighbor MAC
            val direct1Hop = activePeers.firstOrNull { it.hops == 1 && BluetoothAdapter.checkBluetoothAddress(it.id) }?.id
            if (direct1Hop != null) {
                Log.d("GattClient", "Fallback: Routing via 1H direct neighbor MAC '$direct1Hop' for target '$deviceAddress'")
                return@run direct1Hop
            }

            deviceAddress
        }

        val device = try {
            if (resolvedAddress.isNotBlank() && BluetoothAdapter.checkBluetoothAddress(resolvedAddress)) {
                bluetoothAdapter?.getRemoteDevice(resolvedAddress)
            } else null
        } catch (e: Exception) {
            Log.e("GattClient", "Invalid device address '$resolvedAddress': ${e.message}")
            null
        }

        if (device == null) {
            Log.e("GattClient", "Could not resolve remote Bluetooth device for '$deviceAddress'")
            onResult(false)
            return
        }

        val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
        val encryptedText = cryptoHelper.encrypt(chatMessage.text, dummySecret)

        val storage = ResQStorage(context)
        val myProfileName = runBlocking { storage.userName.first() } ?: "User"

        // Wire payload transmits actual profile name in senderId for clean identity mapping
        val wireMessage = chatMessage.copy(
            senderId = if (chatMessage.senderId == "ME") myProfileName else chatMessage.senderId,
            text = encryptedText,
            isFromMe = false
        )

        val jsonPayload = gson.toJson(wireMessage).toByteArray(Charsets.UTF_8)

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            private var negotiatedChunkSize = 15 // 20 byte default MTU payload limit - 5 byte header
            private var currentOffset = 0
            private var isDone = false
            private var discoverTries = 0

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mainHandler.postDelayed({
                        val mtuOk = gatt?.requestMtu(512) ?: false
                        if (!mtuOk) {
                            Log.w("GattClient", "Xiaomi/MIUI MTU request skipped. Discovering services directly...")
                            gatt?.discoverServices()
                        }
                    }, 150)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!isDone) {
                        isDone = true
                        if (attempt < 4) {
                            Log.w("GattClient", "Connection to '$resolvedAddress' failed/disconnected (attempt $attempt/4). Retrying in ${800 * attempt}ms...")
                            mainHandler.postDelayed({
                                sendChatMessage(deviceAddress, chatMessage, attempt + 1, onResult)
                            }, 800L * attempt)
                        } else {
                            mainHandler.post { onResult(false) }
                        }
                    }
                    gatt?.close()
                    // Auto-restart BLE Advertiser & Scanner so radio resumes clean state
                    try {
                        val myName = MeshManager.activeUserName
                        MeshManager.getAdvertiser()?.startAdvertising(myName)
                        MeshManager.getScanner()?.startScan()
                    } catch (e: Exception) {
                        Log.e("GattClient", "Error restarting advertiser/scanner: ${e.message}")
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) {
                    negotiatedChunkSize = (mtu - 3 - HEADER_SIZE).coerceAtMost(480)
                    Log.d("GattClient", "Negotiated MTU $mtu. Chunk payload size set to $negotiatedChunkSize")
                } else {
                    negotiatedChunkSize = 15 // 20 byte limit - 5 byte header
                    Log.w("GattClient", "MTU negotiation default. Using safe chunk payload size $negotiatedChunkSize")
                }
                mainHandler.postDelayed({ gatt?.discoverServices() }, 200)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                if (service == null) {
                    if (++discoverTries > 2) {
                        Log.e("GattClient", "ResQmesh service null after 2 discovery tries. Disconnecting...")
                        gatt?.disconnect()
                        return
                    }
                    Log.w("GattClient", "ResQmesh GATT Service null on attempt $attempt. Clearing stale cache...")
                    refreshGattCache(gatt)
                    mainHandler.postDelayed({ gatt?.discoverServices() }, 250)
                } else {
                    currentOffset = 0
                    sendNextChunk(gatt)
                }
            }

            private fun sendNextChunk(gatt: BluetoothGatt?) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                val char = service?.getCharacteristic(GattServerManager.MESSAGE_CHARACTERISTIC_UUID)
                
                if (char != null && currentOffset < jsonPayload.size) {
                    val endIdx = (currentOffset + negotiatedChunkSize).coerceAtMost(jsonPayload.size)
                    val chunkData = jsonPayload.sliceArray(currentOffset until endIdx)
                    
                    val isFirst = currentOffset == 0
                    val isLast = endIdx >= jsonPayload.size

                    val header = when {
                        isFirst && isLast -> "SOLO:"
                        isFirst -> "STRT:"
                        isLast -> "DONE:"
                        else -> "DATA:"
                    }
                    
                    char.value = header.toByteArray(Charsets.UTF_8) + chunkData
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    val writeOk = gatt.writeCharacteristic(char)
                    if (!writeOk) {
                        Log.e("GattClient", "writeCharacteristic returned false! Disconnecting...")
                        gatt.disconnect()
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val bytesWritten = (currentOffset + negotiatedChunkSize).coerceAtMost(jsonPayload.size) - currentOffset
                    currentOffset += if (bytesWritten > 0) bytesWritten else negotiatedChunkSize
                    if (currentOffset < jsonPayload.size) {
                        sendNextChunk(gatt)
                    } else {
                        isDone = true
                        mainHandler.post { onResult(true) }
                        gatt?.disconnect()
                    }
                } else {
                    Log.e("GattClient", "onCharacteristicWrite failed with status $status")
                    isDone = true
                    mainHandler.post { onResult(false) }
                    gatt?.disconnect()
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(
        deviceAddress: String, 
        messageText: String, 
        isBroadcast: Boolean = false, 
        isEmergency: Boolean = false,
        onResult: (Boolean) -> Unit
    ) {
        val msg = ChatMessage(
            messageId = UUID.randomUUID().toString(),
            senderId = "ME",
            destinationId = if (isBroadcast) "BROADCAST" else deviceAddress,
            text = messageText,
            isFromMe = true,
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            isEmergency = isEmergency
        )
        sendChatMessage(deviceAddress, msg, 1, onResult)
    }

    fun broadcastToAll(peers: List<String>, messageText: String, isEmergency: Boolean = false) {
        if (peers.isEmpty()) return
        sendToPeer(peers, 0, messageText, isEmergency)
    }

    private fun sendToPeer(peers: List<String>, index: Int, text: String, isEmergency: Boolean) {
        if (index >= peers.size) return
        sendMessage(peers[index], text, true, isEmergency) {
            sendToPeer(peers, index + 1, text, isEmergency)
        }
    }

    // MULTI-HOP RELAY TRANSMISSION
    @SuppressLint("MissingPermission")
    fun relayMeshMessage(
        deviceAddress: String,
        relayedMessage: ChatMessage,
        attempt: Int = 1,
        onResult: (Boolean) -> Unit = {}
    ) {
        val device = try {
            bluetoothAdapter?.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            Log.e("GattClient", "Invalid relay target address '$deviceAddress': ${e.message}")
            onResult(false)
            return
        } ?: run {
            onResult(false)
            return
        }
        val jsonPayload = gson.toJson(relayedMessage).toByteArray(Charsets.UTF_8)

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            private var negotiatedChunkSize = 20
            private var currentOffset = 0
            private var isDone = false

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mainHandler.postDelayed({
                        val mtuOk = gatt?.requestMtu(512) ?: false
                        if (!mtuOk) {
                            gatt?.discoverServices()
                        }
                    }, 150)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!isDone) {
                        isDone = true
                        if (attempt < 3) {
                            Log.w("GattClient", "Relay connection to '$deviceAddress' disconnected (attempt $attempt/3). Retrying in ${500 * attempt}ms...")
                            mainHandler.postDelayed({
                                relayMeshMessage(deviceAddress, relayedMessage, attempt + 1, onResult)
                            }, 500L * attempt)
                        } else {
                            mainHandler.post { onResult(false) }
                        }
                    }
                    gatt?.close()
                    // Auto-restart BLE Advertiser so this device remains discoverable for replies!
                    try {
                        val storage = ResQStorage(context)
                        val myName = runBlocking { storage.userName.first() } ?: "User"
                        MeshManager.getAdvertiser()?.startAdvertising(myName)
                    } catch (e: Exception) {
                        Log.e("GattClient", "Error restarting advertiser: ${e.message}")
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) {
                    negotiatedChunkSize = (mtu - 10).coerceAtMost(480)
                } else {
                    negotiatedChunkSize = 18
                }
                mainHandler.postDelayed({ gatt?.discoverServices() }, 200)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                if (service == null) {
                    Log.w("GattClient", "ResQmesh GATT Service null on attempt $attempt. Clearing stale cache...")
                    refreshGattCache(gatt)
                    mainHandler.postDelayed({ gatt?.discoverServices() }, 250)
                } else {
                    currentOffset = 0
                    sendNextChunk(gatt)
                }
            }

            private fun sendNextChunk(gatt: BluetoothGatt?) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                val char = service?.getCharacteristic(GattServerManager.MESSAGE_CHARACTERISTIC_UUID)
                
                if (char != null && currentOffset < jsonPayload.size) {
                    val endIdx = (currentOffset + negotiatedChunkSize).coerceAtMost(jsonPayload.size)
                    val chunkData = jsonPayload.sliceArray(currentOffset until endIdx)
                    
                    val isFirst = currentOffset == 0
                    val isLast = endIdx >= jsonPayload.size

                    val header = when {
                        isFirst && isLast -> "SOLO:"
                        isFirst -> "STRT:"
                        isLast -> "DONE:"
                        else -> "DATA:"
                    }
                    
                    char.value = header.toByteArray(Charsets.UTF_8) + chunkData
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    val writeOk = gatt.writeCharacteristic(char)
                    if (!writeOk) {
                        gatt.disconnect()
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val bytesWritten = (currentOffset + negotiatedChunkSize).coerceAtMost(jsonPayload.size) - currentOffset
                    currentOffset += if (bytesWritten > 0) bytesWritten else negotiatedChunkSize
                    if (currentOffset < jsonPayload.size) {
                        sendNextChunk(gatt)
                    } else {
                        isDone = true
                        mainHandler.post { onResult(true) }
                        gatt?.disconnect()
                    }
                } else {
                    isDone = true
                    mainHandler.post { onResult(false) }
                    gatt?.disconnect()
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    fun relayToAllPeers(peers: List<String>, relayedMessage: ChatMessage) {
        if (peers.isEmpty()) return
        relayToPeerIndex(peers, 0, relayedMessage)
    }

    private fun relayToPeerIndex(peers: List<String>, index: Int, relayedMessage: ChatMessage) {
        if (index >= peers.size) return
        relayMeshMessage(peers[index], relayedMessage) {
            mainHandler.postDelayed({
                relayToPeerIndex(peers, index + 1, relayedMessage)
            }, 300)
        }
    }
}
