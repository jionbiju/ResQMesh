package com.example.resqmesh.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.security.CryptoHelper
import com.example.resqmesh.util.NotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.*

import com.example.resqmesh.util.ResQStorage
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

class GattServerManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val cryptoHelper = CryptoHelper()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Thread-safe map for per-device reassembly buffers (Prevents multi-device chunk corruption)
    private val deviceBuffers = ConcurrentHashMap<String, StringBuilder>()

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8f83db5d-0043-41c8-89c0-67c9c0b621e2")
        val MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("3f99f928-8742-45e0-9e6b-a25e24c52084")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            
            if (preparedWrite) {
                if (responseNeeded && device != null) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }

            if (characteristic?.uuid == MESSAGE_CHARACTERISTIC_UUID && value != null) {
                val dataStr = String(value, Charsets.UTF_8)
                val devAddr = device?.address ?: "Unknown"
                val buffer = deviceBuffers.getOrPut(devAddr) { StringBuilder() }
                
                when {
                    dataStr.startsWith("STRT:") -> {
                        buffer.setLength(0)
                        buffer.append(dataStr.substring(5))
                    }
                    dataStr.startsWith("DATA:") -> {
                        buffer.append(dataStr.substring(5))
                    }
                    dataStr.startsWith("DONE:") -> {
                        buffer.append(dataStr.substring(5))
                        val fullMsg = buffer.toString()
                        buffer.setLength(0)
                        processFullMessage(fullMsg, devAddr)
                    }
                    dataStr.startsWith("SOLO:") -> {
                        processFullMessage(dataStr.substring(5), devAddr)
                    }
                    else -> {
                        processFullMessage(dataStr, devAddr)
                    }
                }
                
                if (responseNeeded && device != null) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    private fun processFullMessage(jsonPayload: String, deviceAddress: String) {
        serviceScope.launch {
            try {
                val meshMessage = gson.fromJson(jsonPayload, ChatMessage::class.java)
                if (!ChatRepository.isMessageNew(meshMessage.messageId)) return@launch

                val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
                val decryptedText = cryptoHelper.decrypt(meshMessage.text, dummySecret) ?: "[Encrypted]"
                
                // Determine real sender identity (Profile Name / Static ID)
                val finalSenderId = if (meshMessage.senderId.isNotBlank() && meshMessage.senderId != "02:00:00:00:00:00" && meshMessage.senderId != "ME") {
                    meshMessage.senderId
                } else {
                    deviceAddress
                }

                // Handle Node Announcement Frames (Discovery Pings)
                if (meshMessage.destinationId == "ANNOUNCE" || meshMessage.text == "ANNOUNCE") {
                    if (meshMessage.ttl >= 2) {
                        MeshManager.getScanner()?.updatePeerName(deviceAddress, finalSenderId)
                    }
                    if (meshMessage.ttl < 2 && finalSenderId != deviceAddress) {
                        MeshManager.getScanner()?.addRelayedPeer(
                            peerId = finalSenderId,
                            peerName = finalSenderId,
                            relayedByAddress = deviceAddress
                        )
                    }
                    // Relay ANNOUNCE to other peers if TTL > 1
                    if (meshMessage.ttl > 1) {
                        val activePeers = MeshManager.getScanner()?.foundPeers?.value ?: emptyList()
                        val targetPeerAddresses = activePeers.map { it.id }.filter { it != deviceAddress }
                        if (targetPeerAddresses.isNotEmpty()) {
                            delay(800)
                            GattClientManager(context).relayToAllPeers(targetPeerAddresses, meshMessage.copy(ttl = meshMessage.ttl - 1))
                        }
                    }
                    return@launch
                }

                // Check if this DM is intended for ME or BROADCAST
                val storage = ResQStorage(context)
                val myProfileName = runBlocking { storage.userName.first() } ?: "User"

                val isIntendedForMe = meshMessage.destinationId == "BROADCAST" ||
                                      meshMessage.destinationId == "ME" ||
                                      meshMessage.destinationId.equals(myProfileName, ignoreCase = true)
                
                val receivedMessage = meshMessage.copy(
                    senderId = finalSenderId,
                    text = decryptedText,
                    isFromMe = false
                )
                
                // ONLY save to local inbox if message is intended for ME or BROADCAST
                if (isIntendedForMe) {
                    ChatRepository.addMessage(receivedMessage)
                    if (receivedMessage.isEmergency) {
                        NotificationHelper.showEmergencyNotification(
                            context,
                            finalSenderId,
                            decryptedText
                        )
                    }
                } else {
                    Log.d("GattServer", "Relay Node: DM for '${meshMessage.destinationId}' received. Relaying without storing in local inbox.")
                }
                
                // ONLY update direct neighbor's name if this was a DIRECT 1-hop transmission (TTL >= 3)
                if (meshMessage.ttl >= 3) {
                    MeshManager.getScanner()?.updatePeerName(deviceAddress, finalSenderId)
                }
                
                // Only register a 2-hop relayed peer if the packet was ACTUALLY relayed (TTL < 3)
                if (meshMessage.ttl < 3 && finalSenderId != deviceAddress && finalSenderId != "02:00:00:00:00:00" && finalSenderId != "ME" && finalSenderId != "BROADCAST") {
                    MeshManager.getScanner()?.addRelayedPeer(
                        peerId = finalSenderId,
                        peerName = finalSenderId,
                        relayedByAddress = deviceAddress
                    )
                }

                // MULTI-HOP FORWARDING (FORWARD original encrypted wire payload)
                if (meshMessage.ttl > 1) {
                    val nextHopTtl = meshMessage.ttl - 1
                    val relayMessage = meshMessage.copy(ttl = nextHopTtl)
                    
                    val activePeers = MeshManager.getScanner()?.foundPeers?.value ?: emptyList()
                    val targetPeerAddresses = activePeers
                        .map { it.id }
                        .filter { it != deviceAddress && it != finalSenderId }

                    if (targetPeerAddresses.isNotEmpty()) {
                        delay(800)
                        Log.d("GattServer", "Multi-Hop Relaying (TTL: $nextHopTtl) to ${targetPeerAddresses.size} peers...")
                        GattClientManager(context).relayToAllPeers(targetPeerAddresses, relayMessage)
                    }
                }

                Log.d("GattServer", "Delivered & Multi-Hop Processed: $decryptedText")
            } catch (e: Exception) {
                Log.e("GattServer", "JSON/Crypto Error: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        try {
            gattServer?.close()
        } catch (e: Exception) {
            // Ignore
        }
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val messageChar = BluetoothGattCharacteristic(
            MESSAGE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(messageChar)
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        gattServer?.close()
    }
}
