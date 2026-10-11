package com.example.resqmesh.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.domain.models.SurvivalGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurvivalGuideScreen(onBackClick: () -> Unit) {
    val guides = listOf(
        SurvivalGuide(
            id = "1",
            title = "Earthquake Response",
            description = "Crucial actions during tremors, post-quake checks & evacuation.",
            category = "Seismic",
            steps = listOf(
                "DROP, COVER, AND HOLD ON: Drop to hands and knees, take cover under a sturdy table, and hold on until shaking stops.",
                "STAY INDOORS: Do not run outside during shaking. Stay clear of windows, heavy furniture, and hanging fixtures.",
                "AFTER SHAKING: Check for gas leaks, electrical damage, and water line ruptures before turning on switches.",
                "NO ELEVATORS: Use stairwells only for post-quake building evacuation.",
                "OUTDOOR SAFETY: Move to an open area away from buildings, streetlights, and power lines."
            )
        ),
        SurvivalGuide(
            id = "2",
            title = "Flood & Tsunami Safety",
            description = "High ground migration, swift water hazards & utility isolation.",
            category = "Water",
            steps = listOf(
                "MOVE TO HIGH GROUND: Immediately migrate to elevated terrain or the highest floor of a sturdy structure.",
                "AVOID MOVING WATER: Just 6 inches of swift-moving water can knock an adult down. Never drive through flooded roads.",
                "UTILITY ISOLATION: Turn off main gas, water, and power switches before evacuating to prevent electrical shock or fire.",
                "WATER SANITATION: Assume floodwaters are contaminated. Do not consume unboiled or untreated water.",
                "SIGNAL FOR HELP: Use bright flags, whistles (3 sharp blasts), or phone flashlights to signal rescue boats."
            )
        ),
        SurvivalGuide(
            id = "3",
            title = "Emergency First Aid & CPR",
            description = "Life-saving protocols for bleeding, CPR, fractures & burns.",
            category = "Medical",
            steps = listOf(
                "CHECK RESPONSIVENESS: Tap shoulders and shout. If no response and no breathing, call/signal for CPR.",
                "CPR PROTOCOL: Perform 30 rapid chest compressions (2 inches deep, 100-120 bpm) followed by 2 rescue breaths.",
                "CONTROL HEAVY BLEEDING: Apply direct firm pressure over wounds with clean cloth. Elevate injured limbs above heart level.",
                "BURNS & SCALDS: Cool burns under clean running water for at least 10 minutes. Do not pop blisters or apply butter.",
                "SHOCK MANAGEMENT: Lay victim flat, elevate legs 12 inches, and cover with blankets to maintain body heat."
            )
        ),
        SurvivalGuide(
            id = "4",
            title = "Fire & Smoke Escape",
            description = "Low-crawl smoke navigation, heat checks & stop-drop-roll.",
            category = "Fire",
            steps = listOf(
                "CRAWL LOW UNDER SMOKE: Toxic smoke rises. Stay low to the floor where air is cleaner and cooler.",
                "DOOR HEAT CHECK: Touch door handles with the back of your hand before opening. If hot, do not open.",
                "STOP, DROP, AND ROLL: If clothing catches fire, immediately stop, drop to the ground, cover face, and roll.",
                "NEVER RE-ENTER: Once outside, assemble at a safe rendezvous point. Never go back inside a burning building."
            )
        ),
        SurvivalGuide(
            id = "5",
            title = "Water Purification & Food Rations",
            description = "Making raw water drinkable & managing emergency rations.",
            category = "Survival",
            steps = listOf(
                "BOILING WATER: Bring water to a rolling boil for at least 1 full minute to kill bacteria, viruses, and parasites.",
                "BLEACH DISINFECTION: Add 2 drops of unscented liquid household bleach per 1 liter of clear water. Stir and let sit 30 minutes.",
                "SOLAR DISINFECTION (SODIS): Fill clean transparent plastic bottles with clear water and leave in direct sunlight for 6 hours.",
                "RATIONING FOOD: Consume perishable foods first. Ration non-perishable canned foods for long-term shelter stays."
            )
        ),
        SurvivalGuide(
            id = "6",
            title = "Offline Mesh Signaling",
            description = "Whistle signals, flash patterns & phone battery preservation.",
            category = "Signals",
            steps = listOf(
                "WHISTLE DISTRESS CODE: Blow whistle 3 short sharp blasts (Universal Distress Signal). Pause and repeat.",
                "SOS LIGHT PATTERN: Flash flashlight 3 short, 3 long, 3 short flashes (S-O-S in Morse Code).",
                "BATTERY PRESERVATION: Enable Flight Mode, lower screen brightness, disable Bluetooth audio/Wi-Fi, and keep ResQmesh BLE active.",
                "ELEVATION FOR MESH RANGE: Move to elevated ground or upper building levels to expand offline BLE mesh coverage."
            )
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("DISASTER SURVIVAL GUIDE", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("Offline First Aid & Emergency Manual", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            items(guides) { guide ->
                InteractiveGuideCard(guide)
            }
        }
    }
}

@Composable
fun InteractiveGuideCard(guide: SurvivalGuide) {
    var isExpanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when (guide.category) {
        "Seismic" -> Icons.Default.Warning
        "Water" -> Icons.Default.Water
        "Medical" -> Icons.Default.HealthAndSafety
        "Fire" -> Icons.Default.LocalFireDepartment
        "Survival" -> Icons.Default.Sanitizer
        else -> Icons.Default.Campaign
    }

    val iconColor: Color = when (guide.category) {
        "Seismic" -> Color(0xFFFF9800)
        "Water" -> Color(0xFF2196F3)
        "Medical" -> Color(0xFFE53935)
        "Fire" -> Color(0xFFFF5722)
        "Survival" -> Color(0xFF4CAF50)
        else -> Color(0xFF9C27B0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = guide.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = guide.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "ACTIONABLE STEPS & SAFETY PROTOCOL:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    guide.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = iconColor.copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = iconColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = step,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
