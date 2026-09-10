package com.example.resqmesh.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.security.CryptoHelper
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
                
                val finalPeerId = if (meshMessage.senderId == "02:00:00:00:00:00") deviceAddress else meshMessage.senderId
                
                val receivedMessage = meshMessage.copy(
                    senderId = finalPeerId,
                    text = decryptedText,
                    isFromMe = false
                )
                
                ChatRepository.addMessage(receivedMessage)
                Log.d("GattServer", "Delivered: $decryptedText")
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
