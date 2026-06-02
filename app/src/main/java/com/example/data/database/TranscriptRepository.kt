package com.example.data.database

import kotlinx.coroutines.flow.Flow

class TranscriptRepository(private val transcriptDao: TranscriptDao) {
    val allTranscripts: Flow<List<TranscriptEntity>> = transcriptDao.getAllTranscripts()

    suspend fun getTranscriptById(id: Int): TranscriptEntity? {
        return transcriptDao.getTranscriptById(id)
    }

    suspend fun insert(transcript: TranscriptEntity): Long {
        return transcriptDao.insertTranscript(transcript)
    }

    suspend fun deleteById(id: Int) {
        transcriptDao.deleteTranscriptById(id)
    }
}
