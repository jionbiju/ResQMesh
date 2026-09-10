package com.example.resqmesh.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE senderId = :peerId OR destinationId = :peerId ORDER BY timestamp ASC")
    fun getMessagesWithPeer(peerId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE messageId = :id)")
    suspend fun hasMessage(id: String): Boolean
}
