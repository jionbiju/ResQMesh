package com.example.resqmesh.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*

class BleAdvertiser(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser

    companion object {
        // Unique ID for ResQmesh - This ensures we only find our own app nodes
        // Changed to a truly unique 128-bit UUID to avoid conflicts with standard services
        val SERVICE_UUID: UUID = UUID.fromString("8f83db5d-0043-41c8-89c0-67c9c0b621e2")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BleAdvertiser", "ResQmesh Node is now visible to others")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BleAdvertiser", "Failed to start advertising: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(userName: String) {
        android.util.Log.d("BleAdvertiser", "Starting advertising with name: $userName")
        val advertiserLocal = advertiser
        if (advertiserLocal == null) {
            android.util.Log.e("BleAdvertiser", "BluetoothLeAdvertiser is null. Check BT state.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Move Name to Scan Response to ensure the Service UUID always fits in the main packet
        val data = AdvertiseData.Builder()
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            advertiserLocal.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: Exception) {
            android.util.Log.e("BleAdvertiser", "Error starting advertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }
}
