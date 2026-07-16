package com.example.resqmesh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun CenterPlaceholder(text: String, modifier: Modifier = Modifier) {
    // We now accept a modifier so we can control the size from outside
    Box(
        modifier = modifier.fillMaxSize(), 
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.Gray)
    }
}
