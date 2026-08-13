package com.example.resqmesh.domain.models

data class ChatMessage(
    val messageId: String = java.util.UUID.randomUUID().toString(),
    val senderId: String,
    val destinationId: String, // Who is the final target?
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var ttl: Int = 3 // Maximum 3 hops
) {
    // For local UI display, we match on peerId
    val peerId: String get() = if (isFromMe) destinationId else senderId
}
