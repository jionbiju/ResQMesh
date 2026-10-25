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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
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
import com.example.resqmesh.service.MeshManager
import com.example.resqmesh.util.BleScanner
import com.example.resqmesh.util.DiscoveredPeer
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun MessageListSection(
    bleScanner: BleScanner,
    isActive: Boolean,
    onToggle: () -> Unit,
    onPeerClick: (String, String) -> Unit
) {
    val allPeers by bleScanner.foundPeers.collectAsState()
    val isLoraConnected by (MeshManager.getLoraManager()?.isConnectedToLoraNode ?: MutableStateFlow(false)).collectAsState()
    val loraNodeName by (MeshManager.getLoraManager()?.connectedNodeName ?: MutableStateFlow("Disconnected")).collectAsState()

    // 1. BLE Section: All peers discovered via direct BLE or BLE multi-hop
    val bleSectionPeers = remember(allPeers) {
        allPeers.filter { it.relayedBy != "LoRa-433MHz" }
    }

    // 2. LoRa Section: Peers discovered ONLY via LoRa-433MHz AND not currently present in the BLE section
    val loraSectionPeers = remember(allPeers, bleSectionPeers) {
        val bleNames = bleSectionPeers.map { it.name.lowercase() }.toSet()
        val bleIds = bleSectionPeers.map { it.id.lowercase() }.toSet()

        allPeers.filter { peer ->
            peer.relayedBy == "LoRa-433MHz" &&
                    !bleNames.contains(peer.name.lowercase()) &&
                    !bleIds.contains(peer.id.lowercase())
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        MeshStatusCard(
            isActive = isActive,
            onToggle = onToggle
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isActive) "Mesh Network Channels & Nodes" else "Recent Conversations",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (isActive) {
                IconButton(onClick = { bleScanner.refreshPeers() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Nodes",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
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
                                text = "Broadcasts over LoRa 433MHz & Direct BLE",
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

            // --- SECTION 1: NEARBY BLE MESH ---
            item {
                Text(
                    text = "📡 Nearby BLE Mesh (High-Speed • Files & Voice)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                )
            }

            if (bleSectionPeers.isEmpty()) {
                item {
                    Text(
                        text = "No phones within direct BLE range (10–30m)",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                items(bleSectionPeers, key = { "ble_${it.id}" }) { peer ->
                    PeerRowItem(
                        peer = peer,
                        badgeText = if (peer.hops > 1) "🟣 BLE Relayed" else "⚡ BLE Direct",
                        badgeColor = if (peer.hops > 1) Color(0xFF9C27B0) else Color(0xFF2E7D32),
                        onClick = { onPeerClick(peer.id, peer.name) }
                    )
                }
            }

            // --- SECTION 2: LONG-RANGE LORA 433MHz ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📡 Long-Range LoRa 433MHz (Text-Only)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isLoraConnected) "Connected ($loraNodeName)" else "ESP32 Offline",
                        fontSize = 11.sp,
                        color = if (isLoraConnected) Color(0xFF4CAF50) else Color.Gray
                    )
                }
            }

            if (!isLoraConnected) {
                item {
                    Text(
                        text = "Connect to an ESP32 LoRa node to reach beyond BLE range.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (loraSectionPeers.isEmpty()) {
                item {
                    Text(
                        text = "Listening on 433 MHz... Remote LoRa nodes will appear here.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                items(loraSectionPeers, key = { "lora_${it.id}" }) { peer ->
                    PeerRowItem(
                        peer = peer,
                        badgeText = "📡 LoRa 433MHz",
                        badgeColor = Color(0xFFFF9800),
                        onClick = { onPeerClick(peer.id, peer.name) }
                    )
                }
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
fun PeerRowItem(
    peer: DiscoveredPeer,
    badgeText: String,
    badgeColor: Color,
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
                    .background(
                        if (!peer.isOnline) Color.Gray else badgeColor
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = peer.name, fontWeight = FontWeight.Bold)
                Text(
                    text = if (!peer.isOnline) "Offline Node" else "ID: ${peer.id.take(12)}...",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Surface(
                color = if (!peer.isOnline) Color.Gray.copy(alpha = 0.15f) else badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (!peer.isOnline) "🔴 Offline" else badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!peer.isOnline) Color.Red else badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
