package com.example.resqmesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.ui.theme.ResQmeshTheme
import com.example.resqmesh.util.HardwareManager

@Composable
fun ToolsScreen(onSurvivalGuideClick: () -> Unit) {
    val context = LocalContext.current
    val hardwareManager = remember { HardwareManager(context) }

    val tools = listOf(
        ToolItem("Flashlight", Icons.Default.FlashlightOn, Color(0xFFFFD700), "Emergency light"),
        ToolItem("Compass", Icons.Default.Explore, Color(0xFF4CAF50), "Navigation"),
        ToolItem("Whistle", Icons.Default.Campaign, Color(0xFFF44336), "Rescue signal"),
        ToolItem("Survival Guide", Icons.Default.MenuBook, Color(0xFF2196F3), "First aid & tips"),
        ToolItem("Maps", Icons.Default.Map, Color(0xFF9C27B0), "Offline area"),
        ToolItem("Signal Finder", Icons.Default.CellTower, Color(0xFF795548), "Locate networks")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "ESSENTIAL UTILITIES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tools.size) { index ->
                val tool = tools[index]
                ToolCard(
                    tool = tool,
                    onClick = {
                        hardwareManager.vibrate()
                        when (tool.name) {
                            "Flashlight" -> hardwareManager.toggleFlashlight()
                            "Whistle" -> hardwareManager.playWhistle()
                            "Survival Guide" -> onSurvivalGuideClick()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = tool.color.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.name,
                    tint = tool.color,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = tool.name, 
                fontWeight = FontWeight.ExtraBold, 
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = tool.description, 
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

data class ToolItem(val name: String, val icon: ImageVector, val color: Color, val description: String)

@Preview(showBackground = true)
@Composable
fun ToolsScreenPreview() {
    ResQmeshTheme {
        ToolsScreen(onSurvivalGuideClick = {})
    }
}
