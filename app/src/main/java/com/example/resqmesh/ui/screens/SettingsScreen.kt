package com.example.resqmesh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.ui.components.CenterPlaceholder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.resqmesh.ui.theme.ResQmeshTheme

@Composable
fun ProfileSettingsSection() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Your Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        CenterPlaceholder(
            text = "Profile settings and QR Key will be here",
            modifier = Modifier.weight(1f)
        )


    }
}

@Preview(showBackground = true)
@Composable
fun ProfileSettingSectionPreview(){
    ResQmeshTheme() {
        ProfileSettingsSection()
    }
}