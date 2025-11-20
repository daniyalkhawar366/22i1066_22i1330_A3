package com.example.a22i1066_b_socially.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatDao {
    @Query("SELECT * FROM cached_chats WHERE userId = :currentUserId ORDER BY lastTimestamp DESC")
    suspend fun getChatsForUser(currentUserId: String): List<CachedChat>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: CachedChat)

    @Query("DELETE FROM cached_chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)

    @Query("DELETE FROM cached_chats WHERE userId = :userId")
    suspend fun deleteChatsForUser(userId: String)
}

