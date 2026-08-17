package com.example.resqmesh.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.security.CryptoHelper
import com.google.gson.Gson
import java.util.*

class GattClientManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val gson = Gson()
    private val cryptoHelper = CryptoHelper()
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun sendMessage(deviceAddress: String, messageText: String, isBroadcast: Boolean = false, onResult: (Boolean) -> Unit) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        
        // 1. Prepare the full payload
        val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
        val encryptedText = cryptoHelper.encrypt(messageText, dummySecret)
        val meshMessage = ChatMessage(
            messageId = UUID.randomUUID().toString(),
            senderId = "ME",
            destinationId = if (isBroadcast) "BROADCAST" else deviceAddress,
            text = encryptedText,
            isFromMe = true,
            timestamp = System.currentTimeMillis(),
            ttl = 3
        )
        val fullPayload = gson.toJson(meshMessage).toByteArray(Charsets.UTF_8)

        // 2. Split into chunks (Safe size for all Android devices)
        val chunkSize = 150 
        val chunks = fullPayload.indices.step(chunkSize).map { 
            fullPayload.sliceArray(it until (it + chunkSize).coerceAtMost(fullPayload.size))
        }

        var currentChunkIndex = 0
        var connectionFinished = false

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!connectionFinished) {
                        connectionFinished = true
                        mainHandler.post { onResult(false) }
                    }
                    gatt?.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                gatt?.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                sendNextChunk(gatt)
            }

            private fun sendNextChunk(gatt: BluetoothGatt?) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                val char = service?.getCharacteristic(GattServerManager.MESSAGE_CHARACTERISTIC_UUID)
                
                if (char != null && currentChunkIndex < chunks.size) {
                    // Add a prefix to tell the receiver if this is the START, MIDDLE, or END
                    val prefix = if (currentChunkIndex == 0) "START:" else if (currentChunkIndex == chunks.size - 1) "END:" else "MID:"
                    val chunkData = prefix.toByteArray(Charsets.UTF_8) + chunks[currentChunkIndex]
                    
                    char.value = chunkData
                    gatt.writeCharacteristic(char)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    currentChunkIndex++
                    if (currentChunkIndex < chunks.size) {
                        sendNextChunk(gatt)
                    } else {
                        connectionFinished = true
                        mainHandler.post { onResult(true) }
                        gatt?.disconnect()
                    }
                } else {
                    connectionFinished = true
                    mainHandler.post { onResult(false) }
                    gatt?.disconnect()
                }
            }
        })
    }

    fun broadcastToAll(peers: List<String>, messageText: String) {
        peers.forEach { address ->
            sendMessage(address, messageText, true) { _ -> }
        }
    }
}
