package com.example.healthcare.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthcare.data.entity.RecognitionCandidate
import com.example.healthcare.data.entity.RecognitionSession

@Dao
interface RecognitionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: RecognitionSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<RecognitionCandidate>)

    @Query("UPDATE recognition_sessions SET status = :status, completedAt = :completedAt WHERE id = :sessionId")
    suspend fun completeSession(sessionId: Long, status: String, completedAt: Long)

    @Query("SELECT * FROM recognition_candidates WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getCandidates(sessionId: Long): List<RecognitionCandidate>

    @Query("DELETE FROM recognition_candidates WHERE sessionId IN (SELECT id FROM recognition_sessions WHERE createdAt < :before)")
    suspend fun deleteOldCandidates(before: Long)

    @Query("DELETE FROM recognition_sessions WHERE createdAt < :before")
    suspend fun deleteOldSessions(before: Long)
}
