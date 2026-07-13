package com.example.resqmesh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.ui.theme.ResQmeshTheme

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSkipLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to ResQmesh",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Offline communication when it matters most.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Login with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Login with Email")
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onSkipLogin) {
            Text("Skip for now (Offline Mode)")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    ResQmeshTheme {
        LoginScreen(onLoginSuccess = {}, onSkipLogin = {})
    }
}
