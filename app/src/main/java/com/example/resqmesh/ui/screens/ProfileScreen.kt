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
fun ProfileScreen(onProfileComplete: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Survivor") }
    val roles = listOf("Survivor", "Volunteer", "Medical Support", "Rescue Team")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(49.dp))
        
        Text(
            text = "Complete Your Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = "This information will be visible to nearby devices offline.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 32.dp).align(Alignment.Start)
                .padding(top =10.dp )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(

            text = "Select Your Role",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        roles.forEach { role ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (role == selectedRole),
                    onClick = { selectedRole = role }
                )
                Text(text = role, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onProfileComplete,
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Finish Setup")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ResQmeshTheme {
        ProfileScreen(onProfileComplete = {})
    }
}
