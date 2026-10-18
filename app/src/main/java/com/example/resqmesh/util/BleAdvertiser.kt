package com.example.resqmesh.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.*

class BleAdvertiser(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8f83db5d-0043-41c8-89c0-67c9c0b621e2")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BleAdvertiser", "ResQmesh Node is now visible to others")
        }

        override fun onStartFailure(errorCode: Int) {
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                Log.d("BleAdvertiser", "BLE Advertiser is already active.")
            } else {
                Log.e("BleAdvertiser", "Failed to start advertising: $errorCode")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(userName: String) {
        Log.d("BleAdvertiser", "Starting advertising with name: $userName")

        try {
            stopAdvertising()
        } catch (e: Exception) {
            // Ignore
        }

        var nameChanged = false
        if (userName.isNotBlank() && userName != "User" && userName != "ResQmesh Node") {
            try {
                if (bluetoothAdapter?.name != userName) {
                    bluetoothAdapter?.name = userName
                    nameChanged = true
                }
            } catch (e: Exception) {
                Log.e("BleAdvertiser", "Could not set adapter name: ${e.message}")
            }
        }

        // Wait 200ms if adapter name changed so BLE Scan Response picks up the new name
        val startDelay = if (nameChanged) 200L else 0L
        mainHandler.postDelayed({
            executeStartAdvertising()
        }, startDelay)
    }

    @SuppressLint("MissingPermission")
    private fun executeStartAdvertising() {
        val advertiserLocal = advertiser
        if (advertiserLocal == null) {
            Log.e("BleAdvertiser", "BluetoothLeAdvertiser is null. Check BT state.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

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
            Log.e("BleAdvertiser", "Error starting advertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e("BleAdvertiser", "Error stopping advertising: ${e.message}")
        }
    }
}