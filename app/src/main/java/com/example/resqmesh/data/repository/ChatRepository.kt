package com.example.resqmesh.data.repository

import com.example.resqmesh.domain.models.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatRepository {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val allMessages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Cache of message IDs we've already seen/processed
    private val seenMessageIds = mutableSetOf<String>()

    fun isMessageNew(messageId: String): Boolean {
        if (seenMessageIds.contains(messageId)) return false
        seenMessageIds.add(messageId)
        return true
    }

    fun addMessage(message: ChatMessage) {
        val currentList = _messages.value.toMutableList()
        currentList.add(message)
        _messages.value = currentList
    }
}
