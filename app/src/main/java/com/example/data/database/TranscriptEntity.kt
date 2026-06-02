package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val sourceUrl: String = "",
    val sourceType: String, // "YOUTUBE", "LOCAL_AUDIO", "LIVE_RECORDING"
    val durationSeconds: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val srtText: String,
    val chaptersText: String,
    val plainText: String
)
