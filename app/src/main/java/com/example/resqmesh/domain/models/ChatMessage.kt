package com.example.resqmesh.domain.models

data class ChatMessage(
    val peerId: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
