package com.example.resqmesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.util.BleScanner

@Composable
fun MessageListSection(
    bleScanner: BleScanner,
    isActive: Boolean,
    onToggle: () -> Unit,
    onPeerClick: (String, String) -> Unit
) {
    val realPeers by bleScanner.foundPeers.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        MeshStatusCard(
            isActive = isActive,
            onToggle = onToggle
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isActive) "Discovered Devices" else "Recent Conversations",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isActive && realPeers.isEmpty()) {
                item {
                    Text("Searching for nearby ResQmesh nodes...", color = Color.Gray, fontSize = 14.sp)
                }
            }
            
            items(realPeers) { peer ->
                PeerItem(
                    name = peer.name,
                    status = "ID: ${peer.id}",
                    isOnline = true,
                    onClick = { onPeerClick(peer.id, peer.name) }
                )
            }
        }
    }
}

@Composable
fun MeshStatusCard(isActive: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "Mesh Network Active" else "Mesh Network Offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isActive) "Listening for peers..." else "Enable Bluetooth to scan",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Switch(checked = isActive, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
fun PeerItem(name: String, status: String, isOnline: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color.Green else Color.Gray)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = name, fontWeight = FontWeight.Bold)
                Text(text = status, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}
