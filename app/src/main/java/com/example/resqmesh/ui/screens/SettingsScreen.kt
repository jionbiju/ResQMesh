package com.example.resqmesh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.ui.components.CenterPlaceholder
import com.example.resqmesh.ui.theme.ResQmeshTheme
import com.example.resqmesh.util.ResQStorage
import kotlinx.coroutines.launch

@Composable
fun ProfileSettingsSection() {
    val context = LocalContext.current
    val storage = remember { ResQStorage(context) }
    val userName by storage.userName.collectAsState(initial = "Loading...")
    val userRole by storage.userRole.collectAsState(initial = "...")
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Your Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Name", fontSize = 12.sp, color = Color.Gray)
                Text(text = userName ?: "Not Set", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(text = "Role", fontSize = 12.sp, color = Color.Gray)
                Text(text = userRole ?: "Survivor", fontSize = 16.sp, fontWeight = FontWeight.Normal)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(text = "Identity Status", fontSize = 12.sp, color = Color.Gray)
                Text(text = "Verified Locally", fontSize = 14.sp, color = Color(0xFF4CAF50))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        CenterPlaceholder(
            text = "QR Key and Encryption details will appear here",
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = {
                scope.launch {
                    storage.clearAll()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Reset Profile (Dev Only)")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileSettingSectionPreview(){
    ResQmeshTheme {
        ProfileSettingsSection()
    }
}
