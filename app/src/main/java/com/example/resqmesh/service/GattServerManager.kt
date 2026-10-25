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
import java.util.concurrent.ConcurrentHashMap

class GattServerManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val cryptoHelper = CryptoHelper()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Thread-safe map for per-device reassembly buffers
    private val deviceBuffers = ConcurrentHashMap<String, StringBuilder>()

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8f83db5d-0043-41c8-89c0-67c9c0b621e2")
        val MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("3f99f928-8742-45e0-9e6b-a25e24c52084")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("GattServer", "ResQmesh GATT Service added successfully.")
            } else {
                Log.e("GattServer", "Failed to add ResQmesh GATT Service, status: $status")
            }
        }

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

                val finalSenderId = if (meshMessage.senderId.isNotBlank() &&
                    meshMessage.senderId != "02:00:00:00:00:00" &&
                    meshMessage.senderId != "ME"
                ) {
                    meshMessage.senderId
                } else {
                    deviceAddress
                }

                // Update direct neighbor's name
                MeshManager.getScanner()?.updatePeerName(deviceAddress, finalSenderId)

                // Handle Node Announcement Frames (Discovery Pings)
                if (meshMessage.destinationId == "ANNOUNCE" || decryptedText == "ANNOUNCE" || meshMessage.text == "ANNOUNCE") {
                    return@launch
                }

                // Check if this DM is intended for ME or BROADCAST
                val myProfileName = MeshManager.activeUserName

                val isIntendedForMe = meshMessage.destinationId == "BROADCAST" ||
                        meshMessage.destinationId == "ME" ||
                        myProfileName == "User" ||
                        meshMessage.destinationId.equals(myProfileName, ignoreCase = true)

                val receivedMessage = meshMessage.copy(
                    senderId = finalSenderId,
                    text = decryptedText,
                    isFromMe = false
                )

                // Save to local inbox if message is intended for ME or BROADCAST
                if (isIntendedForMe) {
                    ChatRepository.addMessage(receivedMessage)
                    if (receivedMessage.isEmergency) {
                        NotificationHelper.showEmergencyNotification(
                            context,
                            finalSenderId,
                            decryptedText
                        )
                    }
                }

                Log.d("GattServer", "Direct BLE Message Delivered: $decryptedText")
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
        if (gattServer == null) {
            Log.e("GattServer", "openGattServer returned null. Bluetooth may be off or unsupported.")
            return
        }
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
        try {
            gattServer?.close()
            gattServer = null
        } catch (e: Exception) {
            Log.e("GattServer", "Error stopping GattServer: ${e.message}")
        }
    }
}
