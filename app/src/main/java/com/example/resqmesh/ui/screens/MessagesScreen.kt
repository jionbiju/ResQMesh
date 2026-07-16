package com.example.resqmesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MessageListSection() {
    val dummyPeers = listOf(
        Peer("Rahul (Medical)", "Active 2m ago", true),
        Peer("Priya", "Active 5m ago", false),
        Peer("Rescue Team Alpha", "Active now", true),
        Peer("Suresh", "Offline", false)
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Nearby Devices",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(dummyPeers) { peer ->
                PeerItem(peer)
            }
        }
    }
}

@Composable
fun PeerItem(peer: Peer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    .background(if (peer.isOnline) Color.Green else Color.Gray)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = peer.name, fontWeight = FontWeight.Bold)
                Text(text = peer.status, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

data class Peer(val name: String, val status: String, val isOnline: Boolean)
