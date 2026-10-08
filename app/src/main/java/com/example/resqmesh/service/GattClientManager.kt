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
        val chunkSize = 150 
        val chunks = jsonPayload.indices.step(chunkSize).map { 
            jsonPayload.sliceArray(it until (it + chunkSize).coerceAtMost(jsonPayload.size))
        }

        var currentChunk = 0
        var isDone = false

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!isDone) {
                        isDone = true
                        if (attempt < 3) {
                            Log.w("GattClient", "Connection to '$resolvedAddress' failed/disconnected (attempt $attempt/3). Retrying in ${500 * attempt}ms...")
                            mainHandler.postDelayed({
                                sendChatMessage(deviceAddress, chatMessage, attempt + 1, onResult)
                            }, 500L * attempt)
                        } else {
                            mainHandler.post { onResult(false) }
                        }
                    }
                    gatt?.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                mainHandler.postDelayed({ gatt?.discoverServices() }, 200)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                sendNext(gatt)
            }

            private fun sendNext(gatt: BluetoothGatt?) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                val char = service?.getCharacteristic(GattServerManager.MESSAGE_CHARACTERISTIC_UUID)
                
                if (char != null && currentChunk < chunks.size) {
                    val header = when {
                        chunks.size == 1 -> "SOLO:"
                        currentChunk == 0 -> "STRT:"
                        currentChunk == chunks.size - 1 -> "DONE:"
                        else -> "DATA:"
                    }
                    char.value = header.toByteArray(Charsets.UTF_8) + chunks[currentChunk]
                    gatt.writeCharacteristic(char)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    currentChunk++
                    if (currentChunk < chunks.size) {
                        sendNext(gatt)
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
        
        val chunkSize = 150 
        val chunks = jsonPayload.indices.step(chunkSize).map { 
            jsonPayload.sliceArray(it until (it + chunkSize).coerceAtMost(jsonPayload.size))
        }

        var currentChunk = 0
        var isDone = false

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.requestMtu(512)
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
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                mainHandler.postDelayed({ gatt?.discoverServices() }, 200)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                sendNext(gatt)
            }

            private fun sendNext(gatt: BluetoothGatt?) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                val char = service?.getCharacteristic(GattServerManager.MESSAGE_CHARACTERISTIC_UUID)
                
                if (char != null && currentChunk < chunks.size) {
                    val header = when {
                        chunks.size == 1 -> "SOLO:"
                        currentChunk == 0 -> "STRT:"
                        currentChunk == chunks.size - 1 -> "DONE:"
                        else -> "DATA:"
                    }
                    char.value = header.toByteArray(Charsets.UTF_8) + chunks[currentChunk]
                    gatt.writeCharacteristic(char)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    currentChunk++
                    if (currentChunk < chunks.size) {
                        sendNext(gatt)
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
