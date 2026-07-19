package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val fileSize: Long,
    val filterPreset: String,
    val noiseThreshold: Float,
    val isEnhanced: Boolean = true
)
