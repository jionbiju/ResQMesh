package com.example.resqmesh.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.security.CryptoHelper
import com.google.gson.Gson
import java.util.*

class GattServerManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val cryptoHelper = CryptoHelper()
    private val gson = Gson()

    private val messageBuffer = StringBuilder()

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
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
            
            if (characteristic?.uuid == MESSAGE_CHARACTERISTIC_UUID && value != null) {
                val dataStr = String(value, Charsets.UTF_8)
                Log.d("GattServer", "Chunk Received: $dataStr")
                
                when {
                    dataStr.startsWith("START:") -> {
                        messageBuffer.setLength(0)
                        messageBuffer.append(dataStr.substring(6))
                    }
                    dataStr.startsWith("MID:") -> {
                        messageBuffer.append(dataStr.substring(4))
                    }
                    dataStr.startsWith("END:") -> {
                        messageBuffer.append(dataStr.substring(4))
                        processFullMessage(messageBuffer.toString())
                    }
                    else -> {
                        processFullMessage(dataStr)
                    }
                }
                
                if (responseNeeded && device != null) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    private fun processFullMessage(jsonPayload: String) {
        try {
            val meshMessage = gson.fromJson(jsonPayload, ChatMessage::class.java)
            if (!ChatRepository.isMessageNew(meshMessage.messageId)) return

            val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
            val decryptedText = cryptoHelper.decrypt(meshMessage.text, dummySecret) ?: "[Encrypted]"
            
            ChatRepository.addMessage(meshMessage.copy(text = decryptedText, isFromMe = false))
            Log.d("GattServer", "Full Message Reassembled and Decrypted: $decryptedText")
        } catch (e: Exception) {
            Log.e("GattServer", "Reassembly Error: ${e.message}")
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
        Log.d("GattServer", "GATT Server Online (Chunking Mode)")
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        gattServer?.close()
    }
}
