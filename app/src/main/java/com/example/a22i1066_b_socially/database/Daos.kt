package com.example.a22i1066_b_socially.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM cached_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<CachedMessage>>

    @Query("SELECT * FROM cached_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatIdSync(chatId: String): List<CachedMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CachedMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessage>)

    @Update
    suspend fun updateMessage(message: CachedMessage)

    @Delete
    suspend fun deleteMessage(message: CachedMessage)

    @Query("DELETE FROM cached_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM cached_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)

    @Query("SELECT * FROM cached_messages WHERE isSent = 0 ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<CachedMessage>
}

@Dao
interface PostDao {
    @Query("SELECT * FROM cached_posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<CachedPost>>

    @Query("SELECT * FROM cached_posts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getPostsSync(limit: Int): List<CachedPost>

    @Query("SELECT * FROM cached_posts WHERE userId = :userId ORDER BY timestamp DESC")
    fun getPostsByUser(userId: String): Flow<List<CachedPost>>

    @Query("SELECT * FROM cached_posts WHERE id = :postId")
    suspend fun getPostById(postId: String): CachedPost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: CachedPost)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<CachedPost>)

    @Update
    suspend fun updatePost(post: CachedPost)

    @Delete
    suspend fun deletePost(post: CachedPost)

    @Query("DELETE FROM cached_posts WHERE timestamp < :expiryTime")
    suspend fun deleteOldPosts(expiryTime: Long)

    @Query("SELECT * FROM cached_posts WHERE isSynced = 0")
    suspend fun getUnsyncedPosts(): List<CachedPost>
}

@Dao
interface StoryDao {
    @Query("SELECT * FROM cached_stories WHERE expiresAt > :currentTime ORDER BY timestamp DESC")
    fun getActiveStories(currentTime: Long): Flow<List<CachedStory>>

    @Query("SELECT * FROM cached_stories WHERE userId = :userId AND expiresAt > :currentTime ORDER BY timestamp DESC")
    fun getStoriesByUser(userId: String, currentTime: Long): Flow<List<CachedStory>>

    @Query("SELECT * FROM cached_stories WHERE expiresAt > :currentTime ORDER BY timestamp DESC")
    suspend fun getActiveStoriesSync(currentTime: Long): List<CachedStory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: CachedStory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<CachedStory>)

    @Update
    suspend fun updateStory(story: CachedStory)

    @Delete
    suspend fun deleteStory(story: CachedStory)

    @Query("DELETE FROM cached_stories WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredStories(currentTime: Long)

    @Query("SELECT * FROM cached_stories WHERE isSynced = 0")
    suspend fun getUnsyncedStories(): List<CachedStory>
}

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions WHERE status = 'pending' ORDER BY timestamp ASC")
    suspend fun getPendingActions(): List<PendingAction>

    @Query("SELECT * FROM pending_actions ORDER BY timestamp DESC")
    fun getAllActions(): Flow<List<PendingAction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingAction): Long

    @Update
    suspend fun updateAction(action: PendingAction)

    @Delete
    suspend fun deleteAction(action: PendingAction)

    @Query("DELETE FROM pending_actions WHERE status = 'completed' AND timestamp < :expiryTime")
    suspend fun deleteOldCompletedActions(expiryTime: Long)

    @Query("SELECT COUNT(*) FROM pending_actions WHERE status = 'pending'")
    suspend fun getPendingActionsCount(): Int
}

