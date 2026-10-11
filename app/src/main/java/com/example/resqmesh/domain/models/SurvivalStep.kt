package com.example.resqmesh.domain.models

data class SurvivalGuide(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val steps: List<String>
)
