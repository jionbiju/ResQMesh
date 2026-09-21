package com.example.resqmesh.ui.screens

import android.content.Context
import android.location.LocationManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import com.example.resqmesh.ui.theme.ResQmeshTheme
import com.example.resqmesh.util.HardwareManager
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView as MapLibreView
import org.maplibre.android.maps.Style

@Composable
fun ToolsScreen(onSurvivalGuideClick: () -> Unit) {
    val context = LocalContext.current
    val hardwareManager = remember { HardwareManager(context) }
    
    var showCompass by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    val tools = listOf(
        ToolItem("Flashlight", Icons.Default.FlashlightOn, Color(0xFFFFD700), "Emergency light"),
        ToolItem("Compass", Icons.Default.Explore, Color(0xFF4CAF50), "Navigation"),
        ToolItem("Whistle", Icons.Default.Campaign, Color(0xFFF44336), "Rescue signal"),
        ToolItem("Survival Guide", Icons.Default.MenuBook, Color(0xFF2196F3), "First aid & tips"),
        ToolItem("Maps", Icons.Default.Map, Color(0xFF9C27B0), "MapLibre Vector Area"),
        ToolItem("Signal Finder", Icons.Default.CellTower, Color(0xFF795548), "Locate networks")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        if (showCompass) {
            CompassView(hardwareManager, onDismiss = { showCompass = false })
        } else if (showMap) {
            MapLibreVectorMapView(onDismiss = { showMap = false })
        } else {
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
                                "Compass" -> showCompass = true
                                "Maps" -> showMap = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MapLibreVectorMapView(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isEmergencyGridMode by remember { mutableStateOf(false) }

    // Synchronously initialize MapLibre instance before view creation
    remember { MapLibre.getInstance(context) }

    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "MAPLIBRE VECTOR MAP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        if (isEmergencyGridMode) "Tactical Radar Grid (Fully Offline)" else "OpenFreeMap Street Vector Style",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isEmergencyGridMode = !isEmergencyGridMode },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            if (isEmergencyGridMode) Icons.Default.Map else Icons.Default.GridOn,
                            contentDescription = "Toggle Grid Mode",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E1E)
            ) {
                if (isEmergencyGridMode) {
                    TacticalGridMap()
                } else {
                    AndroidView(
                        factory = { ctx ->
                            MapLibre.getInstance(ctx)
                            MapLibreView(ctx).apply {
                                onCreate(null)
                                onStart()
                                getMapAsync { map ->
                                    // High-detail OpenFreeMap vector style with all streets, roads, and places worldwide
                                    map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/bright")) {
                                        // Hardware GPS location
                                        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                        var userLatLng = LatLng(12.9716, 77.5946)

                                        try {
                                            val lastLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                                            if (lastLoc != null) {
                                                userLatLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                            }
                                        } catch (e: SecurityException) {
                                            e.printStackTrace()
                                        }

                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15.0))

                                        // Marker 1: Current GPS Position
                                        map.addMarker(
                                            MarkerOptions()
                                                .position(userLatLng)
                                                .title("📍 You Are Here")
                                                .snippet("Hardware GPS Fixed")
                                        )

                                        // Marker 2: Emergency Safe Zone
                                        val shelterLatLng = LatLng(userLatLng.latitude + 0.003, userLatLng.longitude + 0.003)
                                        map.addMarker(
                                            MarkerOptions()
                                                .position(shelterLatLng)
                                                .title("🏥 Emergency Shelter")
                                                .snippet("ResQmesh Node 01")
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "📍 You Are Here",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Surface(
                    color = if (isEmergencyGridMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (isEmergencyGridMode) "🛡️ Radar Grid" else "🚀 MapLibre Vector",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (isEmergencyGridMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun TacticalGridMap() {
    val context = LocalContext.current
    var latText by remember { mutableStateOf("12.9716 N") }
    var lonText by remember { mutableStateOf("77.5946 E") }

    LaunchedEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        try {
            val loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                latText = "${String.format("%.4f", loc.latitude)} N"
                lonText = "${String.format("%.4f", loc.longitude)} E"
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 60f
            // Draw grid lines
            for (x in 0..size.width.toInt() step step.toInt()) {
                drawLine(
                    color = Color.Green.copy(alpha = 0.15f),
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step step.toInt()) {
                drawLine(
                    color = Color.Green.copy(alpha = 0.15f),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }

            // Draw center radar circles
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(color = Color.Green.copy(alpha = 0.2f), radius = 100f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            drawCircle(color = Color.Green.copy(alpha = 0.1f), radius = 200f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            drawCircle(color = Color.Green.copy(alpha = 0.05f), radius = 300f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

            // Center Pin
            drawCircle(color = Color.Red, radius = 10f, center = center)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Text(
                "TACTICAL OFFLINE RADAR GRID",
                color = Color.Green,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
            Text(
                "Lat: $latText | Lon: $lonText",
                color = Color.Green.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun CompassView(hardwareManager: HardwareManager, onDismiss: () -> Unit) {
    val azimuth by hardwareManager.azimuth.collectAsState()
    
    DisposableEffect(Unit) {
        hardwareManager.startCompass()
        onDispose { hardwareManager.stopCompass() }
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = -azimuth, 
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "compass_rotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)), // Dark tactical theme
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "DIGITAL COMPASS", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 12.sp, 
                        color = Color.Gray,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "Level your phone for accuracy", 
                        fontSize = 10.sp, 
                        color = Color.DarkGray
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) { 
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) 
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                // Compass Background & Dial
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2
                    
                    // Draw outer subtle ring
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )

                    rotate(animatedAzimuth) {
                        // Draw degree ticks
                        for (i in 0 until 360 step 10) {
                            val angleInRad = Math.toRadians(i.toDouble())
                            val tickLength = if (i % 30 == 0) 40f else 20f
                            val strokeWidth = if (i % 30 == 0) 3.dp.toPx() else 1.dp.toPx()
                            val color = if (i % 30 == 0) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
                            
                            val startX = center.x + (radius - 10f) * Math.sin(angleInRad).toFloat()
                            val startY = center.y - (radius - 10f) * Math.cos(angleInRad).toFloat()
                            val endX = center.x + (radius - 10f - tickLength) * Math.sin(angleInRad).toFloat()
                            val endY = center.y - (radius - 10f - tickLength) * Math.cos(angleInRad).toFloat()
                            
                            drawLine(color = color, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = strokeWidth)
                        }

                        // Draw Cardinal Points with better styling
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 48f
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        
                        val northPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.RED
                            textSize = 52f
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }

                        // N (Red for emphasis)
                        drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - radius + 85f, northPaint)
                        // Others
                        drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + radius - 55f, textPaint)
                        drawContext.canvas.nativeCanvas.drawText("E", center.x + radius - 70f, center.y + 18f, textPaint)
                        drawContext.canvas.nativeCanvas.drawText("W", center.x - radius + 70f, center.y + 18f, textPaint)
                    }
                }
                
                // Central Indicator (Static Needle)
                Canvas(modifier = Modifier.size(220.dp)) {
                    // Top Red Needle
                    val topNeedlePath = Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width / 2 - 15f, size.height / 2)
                        lineTo(size.width / 2 + 15f, size.height / 2)
                        close()
                    }
                    drawPath(topNeedlePath, color = Color(0xFFE53935))
                    
                    // Bottom Grey Needle
                    val bottomNeedlePath = Path().apply {
                        moveTo(size.width / 2, size.height)
                        lineTo(size.width / 2 - 15f, size.height / 2)
                        lineTo(size.width / 2 + 15f, size.height / 2)
                        close()
                    }
                    drawPath(bottomNeedlePath, color = Color(0xFF757575))
                    
                    // Center Hub
                    drawCircle(color = Color.Black, radius = 8f, center = Offset(size.width / 2, size.height / 2))
                    drawCircle(color = Color.White, radius = 4f, center = Offset(size.width / 2, size.height / 2))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Fixed Degree Text (No longer cut off)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${azimuth.toInt()}°",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 56.sp
                )
                
                val direction = when (azimuth) {
                    in 337.5..360.0, in 0.0..22.5 -> "NORTH"
                    in 22.5..67.5 -> "NORTH-EAST"
                    in 67.5..112.5 -> "EAST"
                    in 112.5..157.5 -> "SOUTH-EAST"
                    in 157.5..202.5 -> "SOUTH"
                    in 202.5..247.5 -> "SOUTH-WEST"
                    in 247.5..292.5 -> "WEST"
                    in 292.5..337.5 -> "NORTH-WEST"
                    else -> "---"
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = direction,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        letterSpacing = 2.sp
                    )
                }
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
