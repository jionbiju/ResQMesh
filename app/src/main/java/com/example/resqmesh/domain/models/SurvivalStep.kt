package com.example.resqmesh.domain.models

data class SurvivalGuide(
    val id: String,
    val title: String,
    val description: String,
    val icon: String, // We'll use this to match icons
    val steps: List<String>
)
