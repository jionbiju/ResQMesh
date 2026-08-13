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
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private val cryptoHelper = CryptoHelper()
    private val gson = Gson()

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
            
            if (characteristic?.uuid == MESSAGE_CHARACTERISTIC_UUID) {
                val jsonPayload = value?.toString(Charsets.UTF_8) ?: ""
                try {
                    val meshMessage = gson.fromJson(jsonPayload, ChatMessage::class.java)
                    
                    // 1. DEDUPLICATION
                    if (!ChatRepository.isMessageNew(meshMessage.messageId)) return

                    // 2. IS IT FOR ME?
                    val myAddress = bluetoothAdapter?.address ?: "SELF"
                    if (meshMessage.destinationId == myAddress || meshMessage.destinationId == "BROADCAST") {
                        val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
                        val decryptedText = cryptoHelper.decrypt(meshMessage.text, dummySecret) ?: "[Encrypted]"
                        ChatRepository.addMessage(meshMessage.copy(text = decryptedText, isFromMe = false))
                    } 
                    
                    // 3. RELAY LOGIC
                    if (meshMessage.ttl > 0 && meshMessage.destinationId != myAddress) {
                        relayMessage(meshMessage.copy(ttl = meshMessage.ttl - 1))
                    }

                } catch (e: Exception) {
                    Log.e("GattServer", "Mesh Parse Error: ${e.message}")
                }
                
                if (responseNeeded && device != null) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    private fun relayMessage(message: ChatMessage) {
        Log.d("GattServer", "Relaying message ${message.messageId} to ${message.destinationId}")
        val client = GattClientManager(context)
        // Find nearby peers and re-send. For now, we log the relay intent.
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
        Log.d("GattServer", "Mesh Node Online")
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        gattServer?.close()
    }
}
