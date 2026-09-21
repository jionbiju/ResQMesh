package com.example.resqmesh

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.service.MeshManager
import com.example.resqmesh.ui.navigation.NavGraph
import com.example.resqmesh.ui.theme.ResQmeshTheme

class MainActivity : ComponentActivity() {
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Bluetooth Mesh Ready", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required for Mesh Network", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Mesh Manager singleton
        MeshManager.init(this)

        // Request permissions on startup for disaster readiness
        requestBlePermissions()

        enableEdgeToEdge()
        setContent {
            ResQmeshTheme {
                var incomingEmergency by remember { mutableStateOf<ChatMessage?>(null) }
                
                LaunchedEffect(Unit) {
                    ChatRepository.emergencyEvents.collect { message ->
                        incomingEmergency = message
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                    
                    incomingEmergency?.let { emergency ->
                        EmergencyPopup(
                            message = emergency,
                            onDismiss = { incomingEmergency = null }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun EmergencyPopup(message: ChatMessage, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFFB00020), // Emergency Red
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("URGENT SOS RECEIVED", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("From: ${message.senderId}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(message.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red)
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = mutableListOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
                )
            )
        }
    }
}
