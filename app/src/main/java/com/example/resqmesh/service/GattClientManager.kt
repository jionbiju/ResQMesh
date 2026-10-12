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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class GattClientManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val gson = Gson()
    private val cryptoHelper = CryptoHelper()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val HEADER_SIZE = 5 // "SOLO:", "STRT:", "DATA:", "DONE:"
        private const val PREFERRED_MTU = 247 // Crisis-Connect preferred MTU size
        private const val CONNECTION_TIMEOUT_MS = 2500L // Fast 2.5-second timeout boundary
        
        // Serializes BLE transmission tasks across all manager instances
        private val queueMutex = Mutex()
    }

    private fun refreshGattCache(gatt: BluetoothGatt?): Boolean {
        return try {
            val refreshMethod = gatt?.javaClass?.getMethod("refresh")
            (refreshMethod?.invoke(gatt) as? Boolean) ?: false
        } catch (e: Exception) {
            Log.e("GattClient", "Error refreshing GATT cache: ${e.message}")
            false
        }
    }

    private fun resolveTargetAddress(deviceAddress: String, destinationId: String = ""): String {
        val activePeers = MeshManager.getScanner()?.foundPeers?.value ?: emptyList()

        val peerMatch = activePeers.firstOrNull { 
            it.id == deviceAddress || 
            it.name.equals(deviceAddress, ignoreCase = true) ||
            (destinationId.isNotBlank() && destinationId != "BROADCAST" && destinationId != "ANNOUNCE" && it.name.equals(destinationId, ignoreCase = true))
        }

        if (peerMatch != null) {
            // Direct 1-Hop peer with valid MAC
            if (peerMatch.hops == 1 && BluetoothAdapter.checkBluetoothAddress(peerMatch.id)) {
                Log.d("GattClient", "Target '$destinationId' resolved to active 1H MAC '${peerMatch.id}'")
                return peerMatch.id
            }
            
            // Relayed 2-Hop peer: Route through 1H Relay neighbor's MAC
            if (peerMatch.hops > 1 && !peerMatch.relayedBy.isNullOrBlank()) {
                val relayPeer = activePeers.firstOrNull { 
                    (it.id == peerMatch.relayedBy || it.name.equals(peerMatch.relayedBy, ignoreCase = true)) &&
                    it.hops == 1 && BluetoothAdapter.checkBluetoothAddress(it.id)
                }
                if (relayPeer != null) {
                    Log.d("GattClient", "Target '$destinationId' (2H) routed via 1H Relay MAC '${relayPeer.id}'")
                    return relayPeer.id
                }
                if (BluetoothAdapter.checkBluetoothAddress(peerMatch.relayedBy)) {
                    return peerMatch.relayedBy
                }
            }
        }

        // Direct Bluetooth MAC address if valid
        if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
            return deviceAddress
        }

        // Fallback to ANY active 1-Hop direct neighbor MAC
        val direct1Hop = activePeers.firstOrNull { it.hops == 1 && BluetoothAdapter.checkBluetoothAddress(it.id) }?.id
        if (direct1Hop != null) {
            Log.d("GattClient", "Fallback: Routing via 1H direct neighbor MAC '$direct1Hop' for target '$deviceAddress'")
            return direct1Hop
        }

        return deviceAddress
    }

    @SuppressLint("MissingPermission")
    fun sendChatMessage(
        deviceAddress: String,
        chatMessage: ChatMessage,
        attempt: Int = 1,
        onProgress: (Float) -> Unit = {},
        onResult: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            queueMutex.withLock {
                val deferredResult = CompletableDeferred<Boolean>()
                
                mainHandler.post {
                    executeChatMessageSend(
                        deviceAddress = deviceAddress,
                        chatMessage = chatMessage,
                        attempt = attempt,
                        onProgress = onProgress,
                        onCompleted = { success -> deferredResult.complete(success) }
                    )
                }

                val result = try {
                    withTimeout(CONNECTION_TIMEOUT_MS) { deferredResult.await() }
                } catch (e: TimeoutCancellationException) {
                    Log.e("GattClient", "Fast timeout: Message transmission timed out to '$deviceAddress'")
                    false
                }
                
                mainHandler.post { onResult(result) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun executeChatMessageSend(
        deviceAddress: String,
        chatMessage: ChatMessage,
        attempt: Int,
        onProgress: (Float) -> Unit,
        onCompleted: (Boolean) -> Unit
    ) {
        val resolvedAddress = resolveTargetAddress(deviceAddress, chatMessage.destinationId)

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
            onCompleted(false)
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
            private var negotiatedChunkSize = 239 // Preferred default (247 MTU - 3 - 5 header)
            private var currentOffset = 0
            private var isFinished = false
            private var discoverTries = 0

            private fun finishTransaction(success: Boolean, gatt: BluetoothGatt?) {
                if (!isFinished) {
                    isFinished = true
                    gatt?.disconnect()
                    gatt?.close()
                    onCompleted(success)
                    try {
                        val myName = MeshManager.activeUserName
                        MeshManager.getAdvertiser()?.startAdvertising(myName)
                    } catch (e: Exception) {
                        Log.e("GattClient", "Error restarting advertiser: ${e.message}")
                    }
                }
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    // Crisis-Connect Optimization: Request High Connection Priority (7.5ms interval)
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    refreshGattCache(gatt)
                    
                    mainHandler.postDelayed({
                        val mtuOk = gatt?.requestMtu(PREFERRED_MTU) ?: false
                        if (!mtuOk) {
                            gatt?.discoverServices()
                        }
                    }, 30)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    if (!isFinished) {
                        if (attempt < 2) {
                            Log.w("GattClient", "Connection to '$resolvedAddress' disconnected (attempt $attempt/2, status $status). Retrying once...")
                            gatt?.close()
                            mainHandler.postDelayed({
                                executeChatMessageSend(deviceAddress, chatMessage, attempt + 1, onProgress, onCompleted)
                            }, 150)
                        } else {
                            finishTransaction(false, gatt)
                        }
                    } else {
                        gatt?.close()
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) {
                    negotiatedChunkSize = (mtu - 3 - HEADER_SIZE).coerceAtMost(480)
                    Log.d("GattClient", "Negotiated MTU $mtu. Payload chunk size set to $negotiatedChunkSize")
                } else {
                    negotiatedChunkSize = 15
                }
                mainHandler.postDelayed({ gatt?.discoverServices() }, 50)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                mainHandler.post {
                    val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                    if (service == null) {
                        if (++discoverTries > 2) {
                            Log.e("GattClient", "ResQmesh service null after 2 tries. Aborting...")
                            finishTransaction(false, gatt)
                            return@post
                        }
                        Log.w("GattClient", "Service null (try $discoverTries). Retrying discoverServices...")
                        mainHandler.postDelayed({ gatt?.discoverServices() }, 100)
                    } else {
                        currentOffset = 0
                        sendNextChunk(gatt)
                    }
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
                    
                    @Suppress("DEPRECATION")
                    char.value = header.toByteArray(Charsets.UTF_8) + chunkData
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    val writeOk = gatt?.writeCharacteristic(char) ?: false
                    if (!writeOk) {
                        Log.e("GattClient", "writeCharacteristic returned false!")
                        finishTransaction(false, gatt)
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                mainHandler.post {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val bytesWritten = (currentOffset + negotiatedChunkSize).coerceAtMost(jsonPayload.size) - currentOffset
                        currentOffset += if (bytesWritten > 0) bytesWritten else negotiatedChunkSize
                        
                        val progress = (currentOffset.toFloat() / jsonPayload.size.toFloat()).coerceIn(0f, 1f)
                        onProgress(progress)

                        if (currentOffset < jsonPayload.size) {
                            sendNextChunk(gatt)
                        } else {
                            finishTransaction(true, gatt)
                        }
                    } else {
                        Log.e("GattClient", "onCharacteristicWrite failed status $status")
                        finishTransaction(false, gatt)
                    }
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
        sendChatMessage(deviceAddress, msg, 1, {}, onResult)
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

    @SuppressLint("MissingPermission")
    fun relayMeshMessage(
        deviceAddress: String,
        relayedMessage: ChatMessage,
        attempt: Int = 1,
        onResult: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            queueMutex.withLock {
                val deferredResult = CompletableDeferred<Boolean>()

                mainHandler.post {
                    executeRelayMessageSend(
                        deviceAddress = deviceAddress,
                        relayedMessage = relayedMessage,
                        attempt = attempt,
                        onCompleted = { success -> deferredResult.complete(success) }
                    )
                }

                val result = try {
                    withTimeout(2000L) { deferredResult.await() }
                } catch (e: TimeoutCancellationException) {
                    Log.e("GattClient", "Relay transmission timed out to '$deviceAddress'")
                    false
                }

                mainHandler.post { onResult(result) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun executeRelayMessageSend(
        deviceAddress: String,
        relayedMessage: ChatMessage,
        attempt: Int,
        onCompleted: (Boolean) -> Unit
    ) {
        val resolvedAddress = resolveTargetAddress(deviceAddress, relayedMessage.destinationId)

        val device = try {
            if (resolvedAddress.isNotBlank() && BluetoothAdapter.checkBluetoothAddress(resolvedAddress)) {
                bluetoothAdapter?.getRemoteDevice(resolvedAddress)
            } else null
        } catch (e: Exception) {
            Log.e("GattClient", "Invalid relay target address '$resolvedAddress': ${e.message}")
            null
        }

        if (device == null) {
            onCompleted(false)
            return
        }

        val jsonPayload = gson.toJson(relayedMessage).toByteArray(Charsets.UTF_8)

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            private var negotiatedChunkSize = 239
            private var currentOffset = 0
            private var isFinished = false
            private var discoverTries = 0

            private fun finishTransaction(success: Boolean, gatt: BluetoothGatt?) {
                if (!isFinished) {
                    isFinished = true
                    gatt?.disconnect()
                    gatt?.close()
                    onCompleted(success)
                    try {
                        val storage = ResQStorage(context)
                        val myName = runBlocking { storage.userName.first() } ?: "User"
                        MeshManager.getAdvertiser()?.startAdvertising(myName)
                    } catch (e: Exception) {
                        Log.e("GattClient", "Error restarting advertiser: ${e.message}")
                    }
                }
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    refreshGattCache(gatt)
                    mainHandler.postDelayed({
                        val mtuOk = gatt?.requestMtu(PREFERRED_MTU) ?: false
                        if (!mtuOk) {
                            gatt?.discoverServices()
                        }
                    }, 30)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    if (!isFinished) {
                        if (attempt < 2) {
                            gatt?.close()
                            mainHandler.postDelayed({
                                executeRelayMessageSend(deviceAddress, relayedMessage, attempt + 1, onCompleted)
                            }, 100)
                        } else {
                            finishTransaction(false, gatt)
                        }
                    } else {
                        gatt?.close()
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) {
                    negotiatedChunkSize = (mtu - 10).coerceAtMost(480)
                } else {
                    negotiatedChunkSize = 18
                }
                mainHandler.postDelayed({ gatt?.discoverServices() }, 50)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                mainHandler.post {
                    val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                    if (service == null) {
                        if (++discoverTries > 2) {
                            finishTransaction(false, gatt)
                            return@post
                        }
                        mainHandler.postDelayed({ gatt?.discoverServices() }, 100)
                    } else {
                        currentOffset = 0
                        sendNextChunk(gatt)
                    }
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
                    
                    @Suppress("DEPRECATION")
                    char.value = header.toByteArray(Charsets.UTF_8) + chunkData
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    val writeOk = gatt?.writeCharacteristic(char) ?: false
                    if (!writeOk) {
                        finishTransaction(false, gatt)
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                mainHandler.post {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val bytesWritten = (currentOffset + negotiatedChunkSize).coerceAtMost(jsonPayload.size) - currentOffset
                        currentOffset += if (bytesWritten > 0) bytesWritten else negotiatedChunkSize
                        if (currentOffset < jsonPayload.size) {
                            sendNextChunk(gatt)
                        } else {
                            finishTransaction(true, gatt)
                        }
                    } else {
                        finishTransaction(false, gatt)
                    }
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
            }, 100)
        }
    }
}
