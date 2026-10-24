package com.example.resqmesh.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.resqmesh.MainActivity
import com.example.resqmesh.util.BleAdvertiser
import com.example.resqmesh.util.BleScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*

/**
 * Singleton manager to persist Mesh & LoRa Network state across screens.
 */
object MeshManager {
    private var bleScanner: BleScanner? = null
    private var bleAdvertiser: BleAdvertiser? = null
    private var gattServer: GattServerManager? = null
    private var loraManager: LoraManager? = null

    var activeUserName: String = "User"

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive = _isMeshActive.asStateFlow()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        if (bleScanner == null) {
            bleScanner = BleScanner(appCtx)
            bleAdvertiser = BleAdvertiser(appCtx)
            gattServer = GattServerManager(appCtx)
        }
        if (loraManager == null) {
            loraManager = LoraManager(appCtx)
        }
    }

    fun isInitialized(): Boolean = loraManager != null || bleScanner != null

    fun getScanner() = bleScanner
    fun getAdvertiser() = bleAdvertiser
    fun getGattServer() = gattServer
    fun getLoraManager(): LoraManager? = loraManager

    fun setMeshActiveState(active: Boolean) {
        _isMeshActive.value = active
    }

    fun toggleMesh(context: Context, userName: String) {
        activeUserName = userName
        val newState = !_isMeshActive.value
        _isMeshActive.value = newState

        if (newState) {
            MeshService.startMeshService(context, userName)
        } else {
            MeshService.stopMeshService(context)
        }
    }
}

/**
 * Persistent Foreground Relay & LoRa Service ensuring ResQmesh stays alive in the background.
 */
class MeshService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "mesh_relay_channel"
        private const val NOTIFICATION_ID = 777

        const val ACTION_START_MESH = "ACTION_START_MESH"
        const val ACTION_STOP_MESH = "ACTION_STOP_MESH"
        const val EXTRA_USER_NAME = "EXTRA_USER_NAME"

        fun startMeshService(context: Context, userName: String = "User") {
            val intent = Intent(context, MeshService::class.java).apply {
                action = ACTION_START_MESH
                putExtra(EXTRA_USER_NAME, userName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopMeshService(context: Context) {
            val intent = Intent(context, MeshService::class.java).apply {
                action = ACTION_STOP_MESH
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MESH -> {
                stopForegroundMesh()
                stopSelf()
            }
            else -> {
                val userName = intent?.getStringExtra(EXTRA_USER_NAME) ?: "User"
                startForegroundMesh(userName)
            }
        }
        return START_STICKY
    }

    private fun startForegroundMesh(userName: String) {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ResQmesh Active")
            .setContentText("Connected to ESP32 LoRa radio & mesh in background")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Initialize and start BLE & LoRa hardware engines
        MeshManager.activeUserName = userName
        MeshManager.init(applicationContext)
        MeshManager.getLoraManager()?.startConnectingToLoraNode()
        MeshManager.getScanner()?.startScan()
        MeshManager.getAdvertiser()?.startAdvertising(userName)
        MeshManager.getGattServer()?.startServer()
        MeshManager.setMeshActiveState(true)
    }

    private fun stopForegroundMesh() {
        MeshManager.getLoraManager()?.disconnect()
        MeshManager.getScanner()?.stopScan()
        MeshManager.getAdvertiser()?.stopAdvertising()
        MeshManager.getGattServer()?.stopServer()
        MeshManager.setMeshActiveState(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopForegroundMesh()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ResQmesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains background LoRa & BLE connectivity for disaster messaging"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
