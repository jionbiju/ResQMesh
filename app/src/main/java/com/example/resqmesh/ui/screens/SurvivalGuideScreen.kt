package com.example.resqmesh.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.domain.models.SurvivalGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurvivalGuideScreen(onBackClick: () -> Unit) {
    val guides = listOf(
        SurvivalGuide("1", "Earthquake", "What to do during tremors", "Home", 
            listOf("Drop, Cover, and Hold on", "Stay away from windows", "Do not use elevators")),
        SurvivalGuide("2", "Flood Safety", "Dealing with rising water", "Water",
            listOf("Move to higher ground", "Avoid walking through moving water", "Turn off utilities")),
        SurvivalGuide("3", "First Aid", "Basic medical response", "Health",
            listOf("Check for breathing", "Control heavy bleeding", "Keep the person warm"))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Survival Guide") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(guides) { guide ->
                GuideCard(guide)
            }
        }
    }
}

@Composable
fun GuideCard(guide: SurvivalGuide) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Future: Navigate to detail */ },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = guide.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = guide.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
