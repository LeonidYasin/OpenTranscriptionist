package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.TranscriptEntity
import com.example.data.database.TranscriptRepository
import com.example.data.gemini.GeminiTranscriber
import com.example.data.youtube.CaptionTrack
import com.example.data.youtube.TranscriptFormatter
import com.example.data.youtube.YouTubeTranscriptExtractor
import com.example.data.youtube.YouTubeVideoInfo
import com.example.utils.VoiceRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

sealed class ProcessState {
    object Idle : ProcessState()
    data class Loading(val message: String) : ProcessState()
    data class ChooseYouTubeLanguage(val videoInfo: YouTubeVideoInfo) : ProcessState()
    data class Success(val transcript: TranscriptEntity) : ProcessState()
    data class Error(val message: String) : ProcessState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = TranscriptRepository(database.transcriptDao())

    val history: StateFlow<List<TranscriptEntity>> = repository.allTranscripts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _processState = MutableStateFlow<ProcessState>(ProcessState.Idle)
    val processState: StateFlow<ProcessState> = _processState.asStateFlow()

    private val _currentViewingTranscript = MutableStateFlow<TranscriptEntity?>(null)
    val currentViewingTranscript: StateFlow<TranscriptEntity?> = _currentViewingTranscript.asStateFlow()

    // Recording States
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private var activeRecordingFile: File? = null

    fun setIdle() {
        _processState.value = ProcessState.Idle
    }

    fun viewTranscript(transcript: TranscriptEntity) {
        _currentViewingTranscript.value = transcript
    }

    fun clearViewingTranscript() {
        _currentViewingTranscript.value = null
    }

    // YouTube Process
    fun processYouTubeUrl(url: String) {
        viewModelScope.launch {
            _processState.value = ProcessState.Loading("Анализ ссылки YouTube...")
            val videoId = YouTubeTranscriptExtractor.extractVideoId(url)
            if (videoId == null) {
                _processState.value = ProcessState.Error("Не удалось извлечь ID видео из ссылки. Проверьте формат ссылки.")
                return@launch
            }

            _processState.value = ProcessState.Loading("Получение информации о видео...")
            val info = YouTubeTranscriptExtractor.fetchVideoInfo(videoId)
            if (info == null) {
                _processState.value = ProcessState.Error("Не удалось получить информацию о видео. Возможно, включено ограничение или нет сети.")
                return@launch
            }

            if (info.captionTracks.isEmpty()) {
                _processState.value = ProcessState.Error("У этого видео нет доступных субтитров (даже автоматических).")
                return@launch
            }

            // If there's only one subtitle track, download it immediately
            if (info.captionTracks.size == 1) {
                fetchAndProcessYouTubeTranscript(info, info.captionTracks.first())
            } else {
                // Otherwise let user select language
                _processState.value = ProcessState.ChooseYouTubeLanguage(info)
            }
        }
    }

    fun fetchAndProcessYouTubeTranscript(info: YouTubeVideoInfo, track: CaptionTrack) {
        viewModelScope.launch {
            _processState.value = ProcessState.Loading("Загрузка и обработка субтитров (${track.name})...")
            val rawTranscript = YouTubeTranscriptExtractor.fetchTranscript(track.baseUrl)
            if (rawTranscript == null) {
                _processState.value = ProcessState.Error("Не удалось получить субтитры от YouTube.")
                return@launch
            }

            val segments = TranscriptFormatter.parseJsonTranscript(rawTranscript)
            if (segments.isEmpty()) {
                _processState.value = ProcessState.Error("Полученные субтитры пусты.")
                return@launch
            }

            val srt = TranscriptFormatter.formatToSrt(segments)
            val chapters = TranscriptFormatter.formatToChapters(segments)
            val plain = TranscriptFormatter.formatToPlainText(segments)

            val entity = TranscriptEntity(
                title = info.title,
                sourceUrl = "https://youtu.be/${info.videoId}",
                sourceType = "YOUTUBE",
                durationSeconds = info.captionTracks.size.toLong(),
                srtText = srt,
                chaptersText = chapters,
                plainText = plain
            )

            val id = repository.insert(entity)
            val savedEntity = entity.copy(id = id.toInt())
            
            _processState.value = ProcessState.Success(savedEntity)
            _currentViewingTranscript.value = savedEntity
        }
    }

    // Local Audio File Process
    fun processLocalAudio(context: Context, uri: Uri) {
        viewModelScope.launch {
            _processState.value = ProcessState.Loading("Импорт файла...")
            val tempFile = copyUriToTempFile(context, uri)
            if (tempFile == null) {
                _processState.value = ProcessState.Error("Не удалось прочитать выбранный файл.")
                return@launch
            }

            val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
            val title = getFileNameFromUri(context, uri) ?: "Аудиофайл"

            _processState.value = ProcessState.Loading("Распознавание речи через Gemini AI... Это может занять до минуты.")
            try {
                val results = GeminiTranscriber.transcribeAudio(tempFile, mimeType)
                if (results == null) {
                    _processState.value = ProcessState.Error("Не удалось распознать аудио.")
                    return@launch
                }

                val entity = TranscriptEntity(
                    title = title,
                    sourceUrl = title,
                    sourceType = "LOCAL_AUDIO",
                    srtText = results["srt"] ?: "",
                    chaptersText = results["chapters"] ?: "",
                    plainText = results["plain"] ?: ""
                )

                val id = repository.insert(entity)
                val savedEntity = entity.copy(id = id.toInt())

                _processState.value = ProcessState.Success(savedEntity)
                _currentViewingTranscript.value = savedEntity
            } catch (e: Exception) {
                _processState.value = ProcessState.Error(e.message ?: "При распознавании произошла неизвестная ошибка.")
            } finally {
                tempFile.delete()
            }
        }
    }

    // Live Recording Process
    fun saveRecordingAndTranscribe(audioFile: File) {
        viewModelScope.launch {
            _processState.value = ProcessState.Loading("Распознавание живой записи через Gemini AI... Ждем.")
            val title = "Запись от ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
            try {
                val results = GeminiTranscriber.transcribeAudio(audioFile, "audio/m4a")
                if (results == null) {
                    _processState.value = ProcessState.Error("Не удалось распознать запись.")
                    return@launch
                }

                val entity = TranscriptEntity(
                    title = title,
                    sourceUrl = "Внутренняя запись",
                    sourceType = "LIVE_RECORDING",
                    srtText = results["srt"] ?: "",
                    chaptersText = results["chapters"] ?: "",
                    plainText = results["plain"] ?: ""
                )

                val id = repository.insert(entity)
                val savedEntity = entity.copy(id = id.toInt())

                _processState.value = ProcessState.Success(savedEntity)
                _currentViewingTranscript.value = savedEntity
            } catch (e: Exception) {
                _processState.value = ProcessState.Error(e.message ?: "Ошибка распознавания записи.")
            } finally {
                audioFile.delete()
            }
        }
    }

    fun startRecording(recorder: VoiceRecorder) {
        try {
            activeRecordingFile = recorder.startRecording("live_recording_${System.currentTimeMillis()}.m4a")
            _isRecording.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _processState.value = ProcessState.Error("Не удалось запустить микрофон: ${e.message}")
        }
    }

    fun stopAndProcessRecording(recorder: VoiceRecorder) {
        if (!_isRecording.value) return
        _isRecording.value = false
        val file = recorder.stopRecording()
        if (file != null && file.exists() && file.length() > 0) {
            saveRecordingAndTranscribe(file)
        } else {
            _processState.value = ProcessState.Error("Файл записи пуст или не был создан.")
        }
    }

    fun deleteTranscript(id: Int) {
        viewModelScope.launch {
            if (_currentViewingTranscript.value?.id == id) {
                _currentViewingTranscript.value = null
            }
            repository.deleteById(id)
        }
    }

    // Helpers
    private fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
}
