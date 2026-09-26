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

/**
 * Singleton manager to persist Mesh Network state across screens.
 */
object MeshManager {
    private var bleScanner: BleScanner? = null
    private var bleAdvertiser: BleAdvertiser? = null
    private var gattServer: GattServerManager? = null

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive = _isMeshActive.asStateFlow()

    fun init(context: Context) {
        if (bleScanner == null) {
            val appTile = context.applicationContext
            bleScanner = BleScanner(appTile)
            bleAdvertiser = BleAdvertiser(appTile)
            gattServer = GattServerManager(appTile)
        }
    }

    fun isInitialized(): Boolean = bleScanner != null

    fun getScanner() = bleScanner
    fun getAdvertiser() = bleAdvertiser
    fun getGattServer() = gattServer

    fun setMeshActiveState(active: Boolean) {
        _isMeshActive.value = active
    }

    fun toggleMesh(context: Context, userName: String) {
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
 * Persistent Foreground Relay Service ensuring BLE Mesh stays alive in the background.
 */
class MeshService : Service() {

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
            .setContentText("Relaying offline emergency signals in background")
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

        // Initialize and start BLE Mesh hardware engines
        MeshManager.init(applicationContext)
        MeshManager.getScanner()?.startScan()
        MeshManager.getAdvertiser()?.startAdvertising(userName)
        MeshManager.getGattServer()?.startServer()
        MeshManager.setMeshActiveState(true)
    }

    private fun stopForegroundMesh() {
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
        stopForegroundMesh()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ResQmesh Mesh Relay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains background BLE Mesh relay service for disaster messaging"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
