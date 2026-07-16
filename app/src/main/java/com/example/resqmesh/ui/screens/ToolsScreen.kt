package com.example.resqmesh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.ui.theme.ResQmeshTheme

@Composable
fun ToolsScreen() {
    val tools = listOf(
        ToolItem("Flashlight", Icons.Default.FlashlightOn, Color(0xFFFFD700)),
        ToolItem("Compass", Icons.Default.Explore, Color(0xFF4CAF50)),
        ToolItem("Whistle", Icons.Default.Campaign, Color(0xFFF44336)),
        ToolItem("Survival Guide", Icons.Default.MenuBook, Color(0xFF2196F3)),
        ToolItem("Maps", Icons.Default.Map, Color(0xFF9C27B0)),
        ToolItem("Signal Finder", Icons.Default.CellTower, Color(0xFF795548))
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Essential Tools",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tools.size) { index ->
                ToolCard(tools[index])
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        onClick = { /* Tool Action */ },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.name,
                tint = tool.color,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = tool.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

data class ToolItem(val name: String, val icon: ImageVector, val color: Color)

@Preview(showBackground = true)
@Composable
fun ToolsScreenPreview() {
    ResQmeshTheme() {
        ToolsScreen()
    }
}
