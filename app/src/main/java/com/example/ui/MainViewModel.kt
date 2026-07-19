package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.AppDatabase
import com.example.data.Recording
import com.example.data.RecordingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = RecordingRepository(database.recordingDao())
    val audioEngine = AudioEngine(application)

    // Reactive streams from Database
    val recordings: StateFlow<List<Recording>> = repository.allRecordings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactive streams from Audio Engine
    val isRecording = audioEngine.isRecording
    val recordingDuration = audioEngine.recordingDuration
    val rawAmplitude = audioEngine.rawAmplitude
    val enhancedAmplitude = audioEngine.enhancedAmplitude
    val isPlaying = audioEngine.isPlaying
    val playbackProgress = audioEngine.playbackProgress
    val playingRecordingId = audioEngine.playingRecordingId

    // Real-time loopback monitoring setting
    var isMonitorEnabled: Boolean
        get() = audioEngine.isMonitorEnabled
        set(value) {
            audioEngine.isMonitorEnabled = value
        }

    // DSP Engine Variables
    var isDeRumbleEnabled: Boolean
        get() = audioEngine.processor.isDeRumbleEnabled
        set(value) {
            audioEngine.processor.isDeRumbleEnabled = value
        }

    var isNoiseGateEnabled: Boolean
        get() = audioEngine.processor.isNoiseGateEnabled
        set(value) {
            audioEngine.processor.isNoiseGateEnabled = value
        }

    var isCompressorEnabled: Boolean
        get() = audioEngine.processor.isCompressorEnabled
        set(value) {
            audioEngine.processor.isCompressorEnabled = value
        }

    var selectedPreset: String
        get() = audioEngine.processor.selectedPreset
        set(value) {
            audioEngine.processor.selectedPreset = value
            audioEngine.processor.updatePresetCoefficients()
        }

    var noiseThreshold: Float
        get() = audioEngine.processor.noiseThreshold
        set(value) {
            audioEngine.processor.noiseThreshold = value
        }

    // ==========================================
    // Controller Actions
    // ==========================================

    fun startRecording(title: String) {
        viewModelScope.launch {
            audioEngine.startRecording(title)
        }
    }

    fun stopRecording() {
        audioEngine.stopRecording()
    }

    fun importAudioFile(uri: android.net.Uri, name: String) {
        viewModelScope.launch {
            audioEngine.importAudioFile(uri, name)
        }
    }

    fun applyFiltersAndExport(recording: Recording, newTitle: String) {
        viewModelScope.launch {
            audioEngine.applyFiltersAndExport(recording, newTitle)
        }
    }

    fun startPlayback(recording: Recording) {
        viewModelScope.launch {
            audioEngine.startPlayback(recording)
        }
    }

    fun stopPlayback() {
        audioEngine.stopPlayback()
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            // If deleting the active playing track, stop it first!
            if (playingRecordingId.value == recording.id) {
                stopPlayback()
            }
            repository.delete(recording)
        }
    }

    fun updateRecordingTitle(recording: Recording, newTitle: String) {
        viewModelScope.launch {
            repository.update(recording.copy(title = newTitle))
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Release audio system immediately if ViewModel is destroyed
        audioEngine.stopRecording()
        audioEngine.stopPlayback()
    }

    // ViewModel Factory class
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
