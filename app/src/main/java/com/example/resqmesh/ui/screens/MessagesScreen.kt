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

import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups

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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isActive) "Mesh Network Channels & Nodes" else "Recent Conversations",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Permanent Public Emergency Mesh Channel Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPeerClick("BROADCAST", "Public Emergency Channel") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📢 Public Emergency Channel",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Broadcasts to all nodes (1, 2 & 3 Hops away)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Public",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (isActive && realPeers.isEmpty()) {
                item {
                    Text("Searching for nearby ResQmesh nodes...", color = Color.Gray, fontSize = 14.sp)
                }
            }
            
            items(realPeers) { peer ->
                PeerItem(
                    name = peer.name,
                    status = "ID: ${peer.id.take(16)}...",
                    hops = peer.hops,
                    relayedBy = peer.relayedBy,
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
fun PeerItem(
    name: String, 
    status: String, 
    isOnline: Boolean, 
    hops: Int = 1,
    relayedBy: String? = null,
    onClick: () -> Unit
) {
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
                    .background(if (hops > 1) Color(0xFF9C27B0) else if (isOnline) Color(0xFF4CAF50) else Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (hops > 1) "2H" else "1H",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontWeight = FontWeight.Bold)
                Text(
                    text = if (hops > 1) "Relayed Node ($status)" else status, 
                    fontSize = 12.sp, 
                    color = Color.Gray
                )
            }
            Surface(
                color = if (hops > 1) Color(0xFF9C27B0).copy(alpha = 0.15f) else Color(0xFF4CAF50).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (hops > 1) "🟣 2 Hops" else "🟢 Direct",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hops > 1) Color(0xFF9C27B0) else Color(0xFF2E7D32),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
