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
        
        // Fix 3: EXACT 32-byte key (Removing the 33rd character)
        val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
        val encryptedText = cryptoHelper.encrypt(messageText, dummySecret)

        // Fix 2: Use the sentinel address the server expects
        val myAddress = "02:00:00:00:00:00"
        
        val meshMessage = ChatMessage(
            messageId = UUID.randomUUID().toString(),
            senderId = myAddress,
            destinationId = if (isBroadcast) "BROADCAST" else deviceAddress,
            text = encryptedText,
            isFromMe = false, // Clean local-only field on wire
            timestamp = System.currentTimeMillis(),
            ttl = 3
        )
        
        val jsonPayload = gson.toJson(meshMessage).toByteArray(Charsets.UTF_8)
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
                        mainHandler.post { onResult(false) }
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
                    // Fix 1: Protocol Header Alignment
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

    fun broadcastToAll(peers: List<String>, messageText: String) {
        // Fix 6: Basic throttling - process sequentially to avoid connection churn
        if (peers.isEmpty()) return
        sendToPeer(peers, 0, messageText)
    }

    private fun sendToPeer(peers: List<String>, index: Int, text: String) {
        if (index >= peers.size) return
        sendMessage(peers[index], text, true) {
            sendToPeer(peers, index + 1, text)
        }
    }
}
