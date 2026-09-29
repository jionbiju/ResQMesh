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

class GattServerManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val cryptoHelper = CryptoHelper()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val messageBuffer = StringBuilder()

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
                
                when {
                    dataStr.startsWith("STRT:") -> {
                        messageBuffer.setLength(0)
                        messageBuffer.append(dataStr.substring(5))
                    }
                    dataStr.startsWith("DATA:") -> {
                        messageBuffer.append(dataStr.substring(5))
                    }
                    dataStr.startsWith("DONE:") -> {
                        messageBuffer.append(dataStr.substring(5))
                        processFullMessage(messageBuffer.toString(), device?.address ?: "Unknown")
                    }
                    dataStr.startsWith("SOLO:") -> {
                        processFullMessage(dataStr.substring(5), device?.address ?: "Unknown")
                    }
                    else -> {
                        processFullMessage(dataStr, device?.address ?: "Unknown")
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
                
                val receivedMessage = meshMessage.copy(
                    senderId = finalSenderId,
                    text = decryptedText,
                    isFromMe = false
                )
                
                ChatRepository.addMessage(receivedMessage)
                
                // Update active peer scanner name if sender sent their real profile name
                MeshManager.getScanner()?.updatePeerName(deviceAddress, finalSenderId)
                
                // Only register a 2-hop relayed peer if the packet was ACTUALLY relayed (TTL < 3)
                if (meshMessage.ttl < 3 && finalSenderId != deviceAddress && finalSenderId != "02:00:00:00:00:00" && finalSenderId != "ME" && finalSenderId != "BROADCAST") {
                    MeshManager.getScanner()?.addRelayedPeer(
                        peerId = finalSenderId,
                        peerName = finalSenderId,
                        relayedByAddress = deviceAddress
                    )
                }

                if (receivedMessage.isEmergency) {
                    NotificationHelper.showEmergencyNotification(
                        context,
                        finalSenderId,
                        decryptedText
                    )
                }

                // MULTI-HOP FORWARDING (FORWARD)
                if (meshMessage.ttl > 1) {
                    val nextHopTtl = meshMessage.ttl - 1
                    val relayMessage = meshMessage.copy(ttl = nextHopTtl)
                    
                    val activePeers = MeshManager.getScanner()?.foundPeers?.value ?: emptyList()
                    val targetPeerAddresses = activePeers
                        .map { it.id }
                        .filter { it != deviceAddress && it != finalSenderId }

                    if (targetPeerAddresses.isNotEmpty()) {
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
