package com.example.resqmesh.data.repository

import android.content.Context
import android.util.Log
import com.example.resqmesh.data.database.AppDatabase
import com.example.resqmesh.data.database.MessageEntity
import com.example.resqmesh.domain.models.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

object ChatRepository {
    private var database: AppDatabase? = null
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _allMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val allMessages: StateFlow<List<ChatMessage>> = _allMessages.asStateFlow()

    private val _emergencyEvents = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 1)
    val emergencyEvents = _emergencyEvents.asSharedFlow()

    fun init(context: Context) {
        if (database == null) {
            Log.d("ChatRepository", "Initializing Database...")
            database = AppDatabase.getInstance(context)
            
            repositoryScope.launch {
                database?.messageDao()?.getAllMessages()?.collect { entities ->
                    Log.d("ChatRepository", "Loaded ${entities.size} messages from DB")
                    _allMessages.value = entities.map { it.toDomainModel() }
                }
            }
        }
    }

    suspend fun isMessageNew(messageId: String): Boolean {
        val db = database ?: return true
        return !db.messageDao().hasMessage(messageId)
    }

    fun addMessage(message: ChatMessage) {
        val db = database
        if (db == null) {
            Log.e("ChatRepository", "Cannot add message: Database not initialized!")
            // Fallback for safety if DB fails to init
            val currentList = _allMessages.value.toMutableList()
            currentList.add(message)
            _allMessages.value = currentList
            return
        }
        
        repositoryScope.launch {
            try {
                db.messageDao().insertMessage(MessageEntity.fromDomainModel(message))
                Log.d("ChatRepository", "Message saved to DB: ${message.messageId}")
                
                if (message.isEmergency && !message.isFromMe) {
                    _emergencyEvents.emit(message)
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "Error saving message: ${e.message}")
            }
        }
    }

    fun getMessagesForPeer(peerId: String): Flow<List<ChatMessage>> {
        return database?.messageDao()?.getMessagesWithPeer(peerId)?.map { list ->
            list.map { it.toDomainModel() }
        } ?: flowOf(emptyList())
    }
}
