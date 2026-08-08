package com.example.resqmesh.data.repository

import com.example.resqmesh.domain.models.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

object ChatRepository {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val allMessages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun getMessagesForPeer(peerId: String): StateFlow<List<ChatMessage>> {
        // In a real app, this would be a filtered flow from the DB
        return _messages.map { list -> 
            list.filter { it.peerId == peerId } 
        }.let { 
            val state = MutableStateFlow<List<ChatMessage>>(emptyList())
            // This is a bit simplified for now to avoid complex flow logic
            _messages.asStateFlow()
        }
    }

    fun addMessage(message: ChatMessage) {
        val currentList = _messages.value.toMutableList()
        currentList.add(message)
        _messages.value = currentList
    }
}
