package com.example.resqmesh.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.resqmesh.ui.theme.ResQmeshTheme

@Composable
fun HomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Home Screen - Nearby Peers will appear here")
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ResQmeshTheme {
        HomeScreen()
    }
}
