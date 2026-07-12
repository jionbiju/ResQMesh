package com.example.resqmesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.resqmesh.ui.navigation.NavGraph
import com.example.resqmesh.ui.theme.ResQmeshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResQmeshTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
