package com.example.resqmesh.domain.models

data class ChatMessage(
    val messageId: String = java.util.UUID.randomUUID().toString(),
    val senderId: String,
    val destinationId: String, // Who is the final target?
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var ttl: Int = 7, // Default 7 hops for wide physical range
    val isEmergency: Boolean = false,
    var via: String? = null // Profile name of relay node who forwarded this packet
) {
    // For local UI display, we match on peerId
    val peerId: String get() = if (isFromMe) destinationId else senderId
}
