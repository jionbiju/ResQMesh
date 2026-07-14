package com.example.resqmesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.ui.theme.ResQmeshTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
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
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    label = { Text("Messages") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) }, // Will change to Tools later
                    label = { Text("Tools") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) }, // Will change to Profile later
                    label = { Text("Profile") }
                )
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { /* SOS Action */ },
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
                0 -> MessageListSection()
                1 -> CenterPlaceholder("Emergency Tools coming soon...")
                2 -> CenterPlaceholder("Profile Settings coming soon...")
            }
        }
    }
}

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

@Composable
fun CenterPlaceholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.Gray)
    }
}

data class Peer(val name: String, val status: String, val isOnline: Boolean)

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ResQmeshTheme {
        HomeScreen()
    }
}
