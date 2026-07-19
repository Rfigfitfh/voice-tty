package com.example.data

import java.io.File
import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val recordingDao: RecordingDao) {
    val allRecordings: Flow<List<Recording>> = recordingDao.getAllRecordings()

    suspend fun insert(recording: Recording): Long {
        return recordingDao.insert(recording)
    }

    suspend fun update(recording: Recording) {
        recordingDao.update(recording)
    }

    suspend fun delete(recording: Recording) {
        try {
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recordingDao.delete(recording)
    }
}
