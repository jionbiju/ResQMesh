package com.example.resqmesh.ui.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.resqmesh.service.MeshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CellSignalInfo(
    val technology: String,
    val cellId: String?,
    val strengthDbm: Int?,
    val level: Int?,
    val operator: String?,
    val isRegistered: Boolean,
    val additionalInfo: String? = null
)

data class WifiSignalInfo(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val frequencyMhz: Int,
    val channelWidth: String,
    val capabilities: String
)

data class BluetoothSignalInfo(
    val name: String?,
    val address: String,
    val rssi: Int?
)

class SignalFinderViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _cells = MutableStateFlow<List<CellSignalInfo>>(emptyList())
    val cells: StateFlow<List<CellSignalInfo>> = _cells.asStateFlow()

    private val _wifi = MutableStateFlow<List<WifiSignalInfo>>(emptyList())
    val wifi: StateFlow<List<WifiSignalInfo>> = _wifi.asStateFlow()

    private val _bluetooth = MutableStateFlow<List<BluetoothSignalInfo>>(emptyList())
    val bluetooth: StateFlow<List<BluetoothSignalInfo>> = _bluetooth.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    fun refreshSignals() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null

            try {
                withContext(Dispatchers.IO) {
                    scanCellSignals()
                    scanWifiSignals()
                    scanBluetoothSignals()
                }
                _lastUpdated.value = System.currentTimeMillis()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to refresh signal spectrum"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanCellSignals() {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val cellList = mutableListOf<CellSignalInfo>()

        try {
            val operatorName = telephonyManager.networkOperatorName.ifBlank { null }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val allCellInfo = telephonyManager.allCellInfo
                if (!allCellInfo.isNullOrEmpty()) {
                    for (cell in allCellInfo) {
                        when (cell) {
                            is CellInfoLte -> {
                                val signal = cell.cellSignalStrength
                                val identity = cell.cellIdentity
                                cellList.add(
                                    CellSignalInfo(
                                        technology = "4G LTE",
                                        cellId = if (identity.ci != Int.MAX_VALUE) identity.ci.toString() else null,
                                        strengthDbm = signal.dbm,
                                        level = signal.level,
                                        operator = operatorName ?: identity.operatorAlphaLong?.toString(),
                                        isRegistered = cell.isRegistered,
                                        additionalInfo = "Bandwidth: ${identity.bandwidth} kHz"
                                    )
                                )
                            }
                            is CellInfoGsm -> {
                                val signal = cell.cellSignalStrength
                                val identity = cell.cellIdentity
                                cellList.add(
                                    CellSignalInfo(
                                        technology = "2G GSM",
                                        cellId = if (identity.cid != Int.MAX_VALUE) identity.cid.toString() else null,
                                        strengthDbm = signal.dbm,
                                        level = signal.level,
                                        operator = operatorName ?: identity.operatorAlphaLong?.toString(),
                                        isRegistered = cell.isRegistered
                                    )
                                )
                            }
                            is CellInfoWcdma -> {
                                val signal = cell.cellSignalStrength
                                val identity = cell.cellIdentity
                                cellList.add(
                                    CellSignalInfo(
                                        technology = "3G WCDMA",
                                        cellId = if (identity.cid != Int.MAX_VALUE) identity.cid.toString() else null,
                                        strengthDbm = signal.dbm,
                                        level = signal.level,
                                        operator = operatorName ?: identity.operatorAlphaLong?.toString(),
                                        isRegistered = cell.isRegistered
                                    )
                                )
                            }
                            is CellInfoNr -> {
                                val signal = cell.cellSignalStrength
                                cellList.add(
                                    CellSignalInfo(
                                        technology = "5G NR",
                                        cellId = "5G Node",
                                        strengthDbm = signal.dbm,
                                        level = signal.level,
                                        operator = operatorName,
                                        isRegistered = cell.isRegistered
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (cellList.isEmpty() && !operatorName.isNullOrBlank()) {
                cellList.add(
                    CellSignalInfo(
                        technology = "Cellular Voice/Data",
                        cellId = null,
                        strengthDbm = null,
                        level = null,
                        operator = operatorName,
                        isRegistered = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _cells.value = cellList
    }

    @SuppressLint("MissingPermission")
    private fun scanWifiSignals() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val wifiList = mutableListOf<WifiSignalInfo>()

        try {
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val results = wifiManager.scanResults
                if (!results.isNullOrEmpty()) {
                    for (scan in results) {
                        val ssidName = if (scan.SSID.isNullOrBlank()) "<Hidden SSID>" else scan.SSID
                        val widthStr = when (scan.channelWidth) {
                            ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
                            ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
                            ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
                            ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
                            else -> "20/40 MHz"
                        }

                        wifiList.add(
                            WifiSignalInfo(
                                ssid = ssidName,
                                bssid = scan.BSSID ?: "Unknown",
                                level = scan.level,
                                frequencyMhz = scan.frequency,
                                channelWidth = widthStr,
                                capabilities = scan.capabilities ?: "Open"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _wifi.value = wifiList.sortedByDescending { it.level }
    }

    private fun scanBluetoothSignals() {
        val scanner = MeshManager.getScanner()
        val peers = scanner?.foundPeers?.value ?: emptyList()

        val btList = peers.map { peer ->
            BluetoothSignalInfo(
                name = peer.name,
                address = peer.id,
                rssi = peer.rssi
            )
        }.sortedByDescending { it.rssi ?: -100 }

        _bluetooth.value = btList
    }
}
