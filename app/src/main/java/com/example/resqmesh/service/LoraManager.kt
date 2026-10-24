package com.example.resqmesh.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.security.CryptoHelper
import com.example.resqmesh.util.NotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class LoraManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val gson = Gson()
    private val cryptoHelper = CryptoHelper()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var connectedGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false

    private val _isConnectedToLoraNode = MutableStateFlow(false)
    val isConnectedToLoraNode = _isConnectedToLoraNode.asStateFlow()

    private val _connectedNodeName = MutableStateFlow("Searching for ESP32...")
    val connectedNodeName = _connectedNodeName.asStateFlow()

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val CHARACTERISTIC_UUID_TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val loraScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val devName = scanRecord?.deviceName ?: device.name ?: ""
            val hasLoraService = scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
            val isLoraName = devName.contains("ResQMesh-Node", ignoreCase = true) ||
                    devName.contains("ResQMesh-LoRa", ignoreCase = true)

            if (hasLoraService || isLoraName) {
                val finalName = devName.ifBlank { "ResQMesh-LoRa" }
                Log.d("LoraManager", "Found LoRa Node: $finalName (${device.address})")
                stopLoraScan()
                connectToDevice(device, finalName)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _connectedNodeName.value = "Scan Failed ($errorCode)"
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopLoraScan() {
        if (isScanning) {
            isScanning = false
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(loraScanCallback)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun startConnectingToLoraNode() {
        if (_isConnectedToLoraNode.value || isScanning) return

        val adapter = bluetoothAdapter
        if (adapter?.isEnabled != true) {
            _connectedNodeName.value = "Bluetooth Disabled"
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _connectedNodeName.value = "BLE Scanner Unavailable"
            return
        }

        _connectedNodeName.value = "Scanning for ESP32 LoRa Node..."

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            isScanning = true
            scanner.startScan(null, settings, loraScanCallback)
        } catch (e: Exception) {
            isScanning = false
            _connectedNodeName.value = "Scan Error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, nodeName: String) {
        _connectedNodeName.value = "Connecting to $nodeName..."
        connectedGatt?.close()
        connectedGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    _isConnectedToLoraNode.value = true
                    _connectedNodeName.value = nodeName
                    Log.d("LoraManager", "Connected to $nodeName via BLE! Requesting MTU 512...")
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                    // CRITICAL: Request large MTU so JSON packets (>20 bytes) are not truncated!
                    mainHandler.postDelayed({
                        val mtuRequested = gatt?.requestMtu(512) ?: false
                        if (!mtuRequested) {
                            gatt?.discoverServices()
                        }
                    }, 50)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    _isConnectedToLoraNode.value = false
                    rxCharacteristic = null
                    _connectedNodeName.value = "Disconnected. Retrying..."
                    try { gatt?.close() } catch (_: Exception) {}
                    connectedGatt = null
                    scope.launch {
                        delay(3000)
                        startConnectingToLoraNode()
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                Log.d("LoraManager", "ESP32 BLE MTU negotiated: $mtu (status $status)")
                mainHandler.postDelayed({
                    gatt?.discoverServices()
                }, 50)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                val service = gatt?.getService(SERVICE_UUID)
                rxCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID_RX)

                val txCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID_TX)
                if (txCharacteristic != null && gatt != null) {
                    gatt.setCharacteristicNotification(txCharacteristic, true)
                    val descriptor = txCharacteristic.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                    Log.d("LoraManager", "LoRa TX Notification enabled successfully.")
                }
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                if (characteristic?.uuid == CHARACTERISTIC_UUID_TX) {
                    @Suppress("DEPRECATION")
                    val rawPayload = characteristic.value?.toString(Charsets.UTF_8) ?: return
                    processIncomingLoraPacket(rawPayload)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == CHARACTERISTIC_UUID_TX) {
                    val rawPayload = value.toString(Charsets.UTF_8)
                    processIncomingLoraPacket(rawPayload)
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    private fun processIncomingLoraPacket(jsonPayload: String) {
        scope.launch {
            try {
                Log.d("LoraManager", "Incoming LoRa payload: $jsonPayload")
                val message = gson.fromJson(jsonPayload, ChatMessage::class.java)
                if (!ChatRepository.isMessageNew(message.messageId)) return@launch

                val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
                val decryptedText = cryptoHelper.decrypt(message.text, dummySecret) ?: message.text

                val myProfileName = MeshManager.activeUserName
                // Ignore our own LoRa packets if echoed back
                if (myProfileName.isNotBlank() && myProfileName != "User" && message.senderId.equals(myProfileName, ignoreCase = true)) {
                    return@launch
                }

                // Register the remote LoRa sender in BleScanner so they appear on the HomeScreen!
                if (message.senderId.isNotBlank() && message.senderId != "ME" && message.senderId != "BROADCAST") {
                    MeshManager.getScanner()?.addRelayedPeer(
                        peerId = message.senderId,
                        peerName = message.senderId,
                        relayedByAddress = "LoRa-433MHz"
                    )
                }

                val isIntendedForMe = message.destinationId == "BROADCAST" ||
                        message.destinationId == "ME" ||
                        myProfileName == "User" ||
                        message.destinationId.equals(myProfileName, ignoreCase = true)

                if (isIntendedForMe) {
                    val receivedMessage = message.copy(
                        text = decryptedText,
                        isFromMe = false
                    )

                    ChatRepository.addMessage(receivedMessage)

                    if (receivedMessage.isEmergency) {
                        NotificationHelper.showEmergencyNotification(
                            context,
                            receivedMessage.senderId,
                            decryptedText
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("LoraManager", "Error parsing LoRa packet: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendLoraMessage(
        text: String,
        destinationId: String = "BROADCAST",
        isEmergency: Boolean = false,
        existingMessageId: String? = null
    ): Boolean {
        val char = rxCharacteristic
        val gatt = connectedGatt
        if (gatt == null || char == null || !_isConnectedToLoraNode.value) {
            return false
        }

        val myName = MeshManager.activeUserName
        val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
        val encryptedText = cryptoHelper.encrypt(text, dummySecret) ?: text

        // Short 8-char messageId saves 28 bytes so the JSON fits inside SX1278's 255-byte FIFO
        val msgId = (existingMessageId ?: UUID.randomUUID().toString()).take(8)

        val wireMessage = ChatMessage(
            messageId = msgId,
            senderId = myName,
            destinationId = destinationId,
            text = encryptedText,
            isFromMe = false,
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            isEmergency = isEmergency
        )

        val jsonPayload = gson.toJson(wireMessage)
        val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)

        if (payloadBytes.size > 250) {
            Log.w("LoraManager", "Payload (${payloadBytes.size} B) exceeds 250B single-frame LoRa limit.")
        }

        return try {
            @Suppress("DEPRECATION")
            char.value = payloadBytes
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        } catch (e: Exception) {
            Log.e("LoraManager", "Error writing to LoRa node: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopLoraScan()
        try {
            connectedGatt?.disconnect()
            connectedGatt?.close()
        } catch (_: Exception) {}
        connectedGatt = null
        rxCharacteristic = null
        _isConnectedToLoraNode.value = false
        _connectedNodeName.value = "Disconnected"
    }
}