package com.personaltool.core.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personaltool.core.storage.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY createdAt DESC")
    fun getAllMediaFlow(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaById(id: String): MediaEntity?

    @Query("SELECT * FROM media_items WHERE downloadStatus = 'COMPLETED' ORDER BY createdAt DESC")
    fun getCompletedMediaFlow(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :query || '%' OR uploader LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaEntity)

    @Update
    suspend fun updateMedia(item: MediaEntity)

    @Delete
    suspend fun deleteMedia(item: MediaEntity)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAllMedia()

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getMediaCount(): Int
}
