package com.example.resqmesh.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavBackStackEntry
import com.example.resqmesh.ui.components.CenterPlaceholder
import com.example.resqmesh.ui.theme.ResQmeshTheme
import com.example.resqmesh.util.ResQStorage
import com.example.resqmesh.util.QrGenerator
import kotlinx.coroutines.launch

@Composable
fun ProfileSettingsSection(
    onScanClick: () -> Unit,
    scanResult: String? = null
) {
    val context = LocalContext.current
    val storage = remember { ResQStorage(context) }
    val userName by storage.userName.collectAsState(initial = "Loading...")
    val userRole by storage.userRole.collectAsState(initial = "...")
    val scope = rememberCoroutineScope()
    
    var showQrDialog by remember { mutableStateOf(false) }

    // Show feedback if a scan just happened
    LaunchedEffect(scanResult) {
        if (scanResult != null) {
            Toast.makeText(context, "Trust Established with: ${scanResult.replace("ResQmesh:", "")}", Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Your Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Name", fontSize = 12.sp, color = Color.Gray)
                Text(text = userName ?: "Not Set", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(text = "Role", fontSize = 12.sp, color = Color.Gray)
                Text(text = userRole ?: "Survivor", fontSize = 16.sp, fontWeight = FontWeight.Normal)
                
                if (scanResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Recent Handshake", fontSize = 12.sp, color = Color.Gray)
                    Text(text = "Trusted Peer Verified", fontSize = 14.sp, color = Color(0xFF4CAF50))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { showQrDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show My Trust ID (QR)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Peer QR Code")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { scope.launch { storage.clearAll() } },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Reset Profile (Dev Only)")
        }
    }

    if (showQrDialog) {
        QrCodeDialog(
            name = userName ?: "User",
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
fun QrCodeDialog(name: String, onDismiss: () -> Unit) {
    val qrBitmap = remember { QrGenerator.generateQrCode("ResQmesh:$name") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Scan to Trust", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(text = "Other phones scan this to establish a secure link.", fontSize = 12.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "My QR Code",
                    modifier = Modifier.size(250.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
