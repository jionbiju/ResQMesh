package com.example.resqmesh.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.resqmesh.ui.viewmodels.CellSignalInfo
import com.example.resqmesh.ui.viewmodels.SignalFinderViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalFinderScreen(
    onBackClick: () -> Unit,
    viewModel: SignalFinderViewModel = viewModel()
) {
    val context = LocalContext.current
    val cellSignals by viewModel.cells.collectAsStateWithLifecycle()
    val wifiSignals by viewModel.wifi.collectAsStateWithLifecycle()
    val bluetoothSignals by viewModel.bluetooth.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.lastUpdated.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshSignals()
    }

    val refreshOrRequestPermissions = {
        val missingPermissions = context.missingSignalFinderPermissions()
        if (missingPermissions.isNotEmpty() && !context.hasSignalFinderLocationPermission()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            viewModel.refreshSignals()
        }
    }

    LaunchedEffect(Unit) {
        refreshOrRequestPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signal & Spectrum Finder", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = refreshOrRequestPermissions, enabled = !isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Scans cellular towers, Wi-Fi access points, and Bluetooth ResQmesh peers to locate potential network coverage in disaster zones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(onClick = refreshOrRequestPermissions, enabled = !isRefreshing) {
                            Text(text = if (isRefreshing) "Scanning Spectrum..." else "Refresh Spectrum")
                        }
                        if (lastUpdated != null) {
                            val formattedTime = DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(Date(lastUpdated!!))
                            AssistChip(
                                onClick = {},
                                label = { Text("Updated: $formattedTime") }
                            )
                        }
                    }
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start
                        )
                    }
                    if (isRefreshing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }

            // SECTION 1: CELLULAR
            item { SectionTitle("Cellular Towers") }
            if (cellSignals.isEmpty()) {
                item { EmptyState(text = "No cellular tower signals detected nearby.") }
            } else {
                items(cellSignals, contentType = { "cell_signal" }) { cell ->
                    SignalCard {
                        Text(
                            text = "${cell.technology} - Cell ID: ${cell.cellId ?: "Unknown"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        cell.strengthDbm?.let { "$it dBm" } ?: "Unknown Strength"
                                    )
                                }
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        cell.level?.let { "Level $it/4" } ?: "Unknown Level"
                                    )
                                }
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        if (cell.isRegistered) "Registered" else "Not Registered"
                                    )
                                }
                            )
                        }
                        if (!cell.operator.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Carrier: ${cell.operator}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (!cell.additionalInfo.isNullOrBlank()) {
                            Text(
                                text = cell.additionalInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // SECTION 2: WI-FI
            item { SectionTitle("Wi-Fi Access Points") }
            if (wifiSignals.isEmpty()) {
                item { EmptyState(text = "No active Wi-Fi networks found.") }
            } else {
                items(
                    wifiSignals,
                    key = { "${it.bssid}:${it.frequencyMhz}" },
                    contentType = { "wifi_signal" }
                ) { wifi ->
                    SignalCard {
                        Text(
                            text = wifi.ssid,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "BSSID: ${wifi.bssid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${wifi.level} dBm") }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${wifi.frequencyMhz} MHz") }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("Width: ${wifi.channelWidth}") }
                            )
                        }
                        if (wifi.capabilities.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Security: ${wifi.capabilities}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // SECTION 3: BLUETOOTH MESH
            item { SectionTitle("Bluetooth ResQmesh Peers") }
            if (bluetoothSignals.isEmpty()) {
                item { EmptyState(text = "No Bluetooth ResQmesh nodes found nearby.") }
            } else {
                items(
                    bluetoothSignals,
                    key = { it.address },
                    contentType = { "bluetooth_signal" }
                ) { bt ->
                    SignalCard {
                        Text(
                            text = bt.name ?: "Unknown ResQmesh Node",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "MAC/ID: ${bt.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (bt.rssi != null) {
                            Spacer(Modifier.height(6.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("RSSI: ${bt.rssi} dBm") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SignalCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

private fun Context.missingSignalFinderPermissions(): List<String> {
    return requiredSignalFinderPermissions().filter { permission ->
        ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
    }
}

private fun Context.hasSignalFinderLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun requiredSignalFinderPermissions(): List<String> {
    return buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.distinct()
}
