package com.personaltool.core.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personaltool.core.storage.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE targetId = :targetId LIMIT 1")
    fun getTranscriptByTargetIdFlow(targetId: String): Flow<TranscriptEntity?>

    @Query("SELECT * FROM transcripts WHERE targetId = :targetId LIMIT 1")
    suspend fun getTranscriptByTargetId(targetId: String): TranscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity)

    @Update
    suspend fun updateTranscript(transcript: TranscriptEntity)

    @Delete
    suspend fun deleteTranscript(transcript: TranscriptEntity)

    @Query("DELETE FROM transcripts WHERE targetId = :targetId")
    suspend fun deleteTranscriptByTargetId(targetId: String): Int

    @Query("DELETE FROM transcripts")
    suspend fun deleteAllTranscripts(): Int
}
