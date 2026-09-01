package com.personaltool.core.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY startTimeEpochMs DESC")
    fun getAllCallsFlow(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :id")
    suspend fun getCallById(id: String): CallEntity?

    @Query("SELECT * FROM calls WHERE isFavorite = 1 ORDER BY startTimeEpochMs DESC")
    fun getFavoriteCallsFlow(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE phoneNumber LIKE '%' || :query || '%' OR contactName LIKE '%' || :query || '%' ORDER BY startTimeEpochMs DESC")
    fun searchCalls(query: String): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<CallEntity>)

    @Update
    suspend fun updateCall(call: CallEntity)

    @Delete
    suspend fun deleteCall(call: CallEntity)

    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun deleteCallById(id: String): Int

    @Query("DELETE FROM calls")
    suspend fun deleteAllCalls(): Int

    @Query("SELECT COUNT(*) FROM calls")
    suspend fun getCallCount(): Int
}
