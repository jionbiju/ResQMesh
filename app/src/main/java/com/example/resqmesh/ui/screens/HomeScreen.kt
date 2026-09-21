package com.example.resqmesh.ui.screens

import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.resqmesh.ui.theme.ResQmeshTheme
import com.example.resqmesh.service.MeshManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSurvivalGuide: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToSOS: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    scanResult: String? = null
) {
    var selectedTab by remember { mutableIntStateOf(if (scanResult != null) 2 else 0) }
    val context = LocalContext.current
    
    // Initialize MeshManager with Application Context
    LaunchedEffect(Unit) {
        MeshManager.init(context)
    }

    val isMeshActive by MeshManager.isMeshActive.collectAsState()
    val bleScanner = MeshManager.getScanner()
    
    val storage = remember { com.example.resqmesh.util.ResQStorage(context) }
    val profileName by storage.userName.collectAsState(initial = "User")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when(selectedTab) {
                            0 -> "Messages"
                            1 -> "Emergency Toolkit"
                            else -> "Profile"
                        }, 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(if (selectedTab == 0) Icons.Filled.Chat else Icons.Outlined.Chat, contentDescription = null) },
                    label = { Text("Messages") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(if (selectedTab == 1) Icons.Filled.Build else Icons.Outlined.Build, contentDescription = null) },
                    label = { Text("Tools") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(if (selectedTab == 2) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("Profile") }
                )
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = onNavigateToSOS,
                containerColor = Color.Red,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "SOS",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    if (bleScanner != null) {
                        MessageListSection(
                            bleScanner = bleScanner,
                            isActive = isMeshActive,
                            onToggle = { 
                                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                                val adapter = bluetoothManager.adapter
                                
                                if (adapter == null) {
                                    Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                                } else if (!adapter.isEnabled) {
                                    Toast.makeText(context, "Please turn on Bluetooth first", Toast.LENGTH_SHORT).show()
                                } else {
                                    MeshManager.toggleMesh(profileName ?: "User")
                                    if (MeshManager.isMeshActive.value) {
                                        Toast.makeText(context, "Activating Mesh...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Mesh Deactivated", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onPeerClick = onNavigateToChat
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                1 -> ToolsScreen(onSurvivalGuideClick = onNavigateToSurvivalGuide)
                2 -> ProfileSettingsSection(
                    onScanClick = onNavigateToQrScanner,
                    scanResult = scanResult
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ResQmeshTheme {
        HomeScreen(
            onNavigateToSurvivalGuide = {},
            onNavigateToChat = { _, _ -> },
            onNavigateToSOS = {},
            onNavigateToQrScanner = {}
        )
    }
}
