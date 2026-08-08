package com.example.resqmesh.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*

class GattClientManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun sendMessage(deviceAddress: String, message: String, onResult: (Boolean) -> Unit) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("GattClient", "Connected to peer, discovering services...")
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("GattClient", "Disconnected from peer")
                    gatt?.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(GattServerManager.MESSAGE_CHARACTERISTIC_UUID)
                    
                    if (characteristic != null) {
                        characteristic.value = message.toByteArray(Charsets.UTF_8)
                        gatt.writeCharacteristic(characteristic)
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("GattClient", "Message delivered successfully!")
                    onResult(true)
                } else {
                    Log.e("GattClient", "Message delivery failed: $status")
                    onResult(false)
                }
                gatt?.disconnect()
            }
        })
    }
}
