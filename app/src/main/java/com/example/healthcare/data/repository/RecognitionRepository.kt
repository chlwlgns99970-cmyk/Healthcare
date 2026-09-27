package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.RecognitionDao
import com.example.healthcare.data.entity.RecognitionCandidate
import com.example.healthcare.data.entity.RecognitionSession

class RecognitionRepository(private val dao: RecognitionDao) {
    suspend fun createSession(session: RecognitionSession): Long = dao.insertSession(session)

    suspend fun saveCandidates(candidates: List<RecognitionCandidate>) = dao.insertCandidates(candidates)

    suspend fun complete(sessionId: Long, status: String, completedAt: Long = System.currentTimeMillis()) =
        dao.completeSession(sessionId, status, completedAt)

    suspend fun cleanExpired(before: Long) {
        dao.deleteOldCandidates(before)
        dao.deleteOldSessions(before)
    }
}
