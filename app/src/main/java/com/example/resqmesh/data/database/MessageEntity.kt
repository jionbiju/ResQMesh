package com.example.resqmesh.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.resqmesh.domain.models.ChatMessage

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val destinationId: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long,
    val ttl: Int
) {
    fun toDomainModel() = ChatMessage(
        messageId = messageId,
        senderId = senderId,
        destinationId = destinationId,
        text = text,
        isFromMe = isFromMe,
        timestamp = timestamp,
        ttl = ttl
    )

    companion object {
        fun fromDomainModel(model: ChatMessage) = MessageEntity(
            messageId = model.messageId,
            senderId = model.senderId,
            destinationId = model.destinationId,
            text = model.text,
            isFromMe = model.isFromMe,
            timestamp = model.timestamp,
            ttl = model.ttl
        )
    }
}
