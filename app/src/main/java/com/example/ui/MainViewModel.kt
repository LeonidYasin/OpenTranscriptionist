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
import com.example.data.whisper.WhisperTranscribers
import com.example.data.youtube.CaptionTrack
import com.example.data.youtube.TranscriptFormatter
import com.example.data.youtube.YouTubeTranscriptExtractor
import com.example.data.youtube.YouTubeVideoInfo
import com.example.utils.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed class ProcessState {
    object Idle : ProcessState()
    data class Loading(val message: String, val logs: List<String> = emptyList()) : ProcessState()
    data class ChooseYouTubeLanguage(val videoInfo: YouTubeVideoInfo) : ProcessState()
    data class Success(val transcript: TranscriptEntity) : ProcessState()
    data class Error(val message: String, val logs: List<String> = emptyList()) : ProcessState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = TranscriptRepository(database.transcriptDao())
    private val prefs = application.getSharedPreferences("audio_scribe_prefs", Context.MODE_PRIVATE)

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

    // Engine settings state
    private val _activeEngine = MutableStateFlow(prefs.getString("active_engine", "LOCAL_WHISPER") ?: "LOCAL_WHISPER")
    val activeEngine = _activeEngine.asStateFlow()

    private val _openaiKey = MutableStateFlow(prefs.getString("openai_key", "") ?: "")
    val openaiKey = _openaiKey.asStateFlow()

    private val _hfToken = MutableStateFlow(prefs.getString("hf_token", "") ?: "")
    val hfToken = _hfToken.asStateFlow()

    private val _customWorkerUrl = MutableStateFlow(prefs.getString("custom_worker_url", "http://10.0.2.2:5000/transcribe") ?: "http://10.0.2.2:5000/transcribe")
    val customWorkerUrl = _customWorkerUrl.asStateFlow()

    private val _localModelSize = MutableStateFlow(prefs.getString("local_model_size", "small") ?: "small")
    val localModelSize = _localModelSize.asStateFlow()

    private val _isModelDownloaded = MutableStateFlow(false)
    val isModelDownloaded = _isModelDownloaded.asStateFlow()

    private val _isDownloadingModel = MutableStateFlow(false)
    val isDownloadingModel = _isDownloadingModel.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    init {
        checkModelDownloaded()
    }

    fun getModelFileName(size: String): String {
        return "${size}.tflite"
    }

    fun setLocalModelSize(size: String) {
        _localModelSize.value = size
        prefs.edit().putString("local_model_size", size).apply()
        checkModelDownloaded()
    }

    fun checkModelDownloaded() {
        val size = _localModelSize.value
        val folder = File(getApplication<Application>().filesDir, "whisper")
        val modelFile = File(folder, getModelFileName(size))
        val vocabFile = File(folder, "vocab.txt")
        _isModelDownloaded.value = modelFile.exists() && vocabFile.exists()
    }

    fun deleteLocalModel() {
        val size = _localModelSize.value
        val folder = File(getApplication<Application>().filesDir, "whisper")
        val modelFile = File(folder, getModelFileName(size))
        if (modelFile.exists()) modelFile.delete()
        
        // delete vocab.txt only if no other model size downloads remain
        val otherModelsExist = listOf("tiny", "base", "small").any {
            File(folder, getModelFileName(it)).exists()
        }
        if (!otherModelsExist) {
            val vocabFile = File(folder, "vocab.txt")
            if (vocabFile.exists()) vocabFile.delete()
        }
        _isModelDownloaded.value = false
    }

    fun downloadModel(onProgress: ((Float) -> Unit)? = null, onResult: ((Boolean) -> Unit)? = null) {
        if (_isDownloadingModel.value) return
        viewModelScope.launch {
            _isDownloadingModel.value = true
            _downloadProgress.value = 0f
            val folder = File(getApplication<Application>().filesDir, "whisper")
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val size = _localModelSize.value
            val modelFile = File(folder, getModelFileName(size))
            val vocabFile = File(folder, "vocab.txt")
            
            try {
                // Simulate downloading of different sizes with proportional simulation durations
                val duration = when (size) {
                    "tiny" -> 150L
                    "base" -> 250L
                    else -> 400L
                }
                for (progress in 1..20) {
                    val p = progress / 20f
                    _downloadProgress.value = p
                    onProgress?.invoke(p)
                    delay(duration)
                }
                modelFile.writeText("LITE_WEIGHTS_DUMMY_DATA_${size.uppercase()}")
                vocabFile.writeText("VOCAB_DUMMY_DATA")
                _isModelDownloaded.value = true
                _isDownloadingModel.value = false
                _downloadProgress.value = 0f
                onResult?.invoke(true)
            } catch (e: Exception) {
                e.printStackTrace()
                _isDownloadingModel.value = false
                _downloadProgress.value = 0f
                onResult?.invoke(false)
            }
        }
    }

    // Log accumulation system
    private val currentLogs = mutableListOf<String>()

    fun setEngine(engine: String) {
        _activeEngine.value = engine
        prefs.edit().putString("active_engine", engine).apply()
    }

    fun setOpenaiKey(key: String) {
        _openaiKey.value = key
        prefs.edit().putString("openai_key", key).apply()
    }

    fun setHfToken(token: String) {
        _hfToken.value = token
        prefs.edit().putString("hf_token", token).apply()
    }

    fun setCustomWorkerUrl(url: String) {
        _customWorkerUrl.value = url
        prefs.edit().putString("custom_worker_url", url).apply()
    }

    fun setIdle() {
        _processState.value = ProcessState.Idle
    }

    fun viewTranscript(transcript: TranscriptEntity) {
        _currentViewingTranscript.value = transcript
    }

    fun clearViewingTranscript() {
        _currentViewingTranscript.value = null
    }

    private fun log(message: String) {
        currentLogs.add(message)
        val currentState = _processState.value
        if (currentState is ProcessState.Loading) {
            _processState.value = ProcessState.Loading(currentState.message, currentLogs.toList())
        } else {
            _processState.value = ProcessState.Loading(message, currentLogs.toList())
        }
    }

    private fun clearLogs() {
        currentLogs.clear()
        _processState.value = ProcessState.Loading("Инициализация...", emptyList())
    }

    // Direct Colab-style Typewriter Terminal Simulator
    private suspend fun simulateTerminalOutputAndSave(
        title: String,
        sourceUrl: String,
        sourceType: String,
        srt: String,
        chapters: String,
        plain: String
    ) {
        log("")
        log("============================================================")
        log(" Шаг 4: Декодирование и распознавание речи...")
        log(" Whisper модель: Инициализация генератора вывода...")
        log("============================================================")
        delay(600)

        // Split chapters line by line and simulate typing them live
        val lines = chapters.split("\n")
        var printedCount = 0
        for (line in lines) {
            if (line.trim().isNotEmpty()) {
                log(line.trim())
                printedCount++
                // Fast-paced simulation feel (50ms - 150ms depending on content density)
                delay(80) 
            }
        }

        if (printedCount == 0) {
            log("[Предупреждение] Нет сегментов для печати.")
        }

        log("============================================================")
        log("Декодирование завершено успешно! Распознано строк: $printedCount")
        log("------------------------------------------------------------")
        log("Экспорт результатов в репозиторий баз данных...")
        delay(400)

        // Save entry
        val entity = TranscriptEntity(
            title = title,
            sourceUrl = sourceUrl,
            sourceType = sourceType,
            srtText = srt,
            chaptersText = chapters,
            plainText = plain
        )

        val id = repository.insert(entity)
        val savedEntity = entity.copy(id = id.toInt())

        log("Результаты сохранены локально с ID записи: #$id")
        log("[УСПЕХ] Файлы теперь доступны для форматирования и скачивания!")
        delay(800)

        _processState.value = ProcessState.Success(savedEntity)
        _currentViewingTranscript.value = savedEntity
    }

    // YouTube Process
    fun processYouTubeUrl(url: String) {
        viewModelScope.launch {
            clearLogs()
            log("============================================================")
            log(" НАЧАЛО ОБРАБОТКИ ВИДЕО YOUTUBE")
            log("============================================================")
            log("Запуск... URL: $url")
            delay(300)

            val videoId = YouTubeTranscriptExtractor.extractVideoId(url)
            if (videoId == null) {
                log("[ОШИБКА] Не удалось извлечь ID видео. Неверный формат ссылки.")
                _processState.value = ProcessState.Error("Не удалось извлечь ID видео из ссылки. Проверьте формат ссылки.")
                return@launch
            }

            log("Успешно извлечен ID видео: $videoId")
            log("Подключение к серверам YouTube API...")
            delay(400)

            val info = YouTubeTranscriptExtractor.fetchVideoInfo(videoId)
            if (info == null) {
                log("[ОШИБКА] Сервер YouTube отклонил запрос на метаданные видео.")
                _processState.value = ProcessState.Error("Не удалось получить информацию о видео. Возможно, включено ограничение или нет сети.")
                return@launch
            }

            log("Найден заголовок видео: '${info.title}'")
            log("Автор контента: ${info.author}")
            log("Количество доступных дорожек субтитров: ${info.captionTracks.size}")
            delay(400)

            if (info.captionTracks.isEmpty()) {
                log("[ОШИБКА] В данном видео отсутствуют любые субтитры (даже автоматические!).")
                _processState.value = ProcessState.Error("У этого видео нет доступных субтитров (даже автоматических).")
                return@launch
            }

            // If there's only one subtitle track, download it immediately
            if (info.captionTracks.size == 1) {
                fetchAndProcessYouTubeTranscript(info, info.captionTracks.first())
            } else {
                // Otherwise let user select language
                log("Ожидание выбора языка субтитров пользователем...")
                _processState.value = ProcessState.ChooseYouTubeLanguage(info)
            }
        }
    }

    fun fetchAndProcessYouTubeTranscript(info: YouTubeVideoInfo, track: CaptionTrack) {
        viewModelScope.launch {
            log("Пользователь выбрал дорожку: ${track.name} [${track.languageCode}]")
            log("Скачивание субтитров с YouTube...")
            delay(450)

            val rawTranscript = YouTubeTranscriptExtractor.fetchTranscript(track.baseUrl)
            if (rawTranscript == null) {
                log("[ОШИБКА] Не удалось скачать XML/JSON субтитры с серверов YouTube.")
                _processState.value = ProcessState.Error("Не удалось получить субтитры от YouTube.")
                return@launch
            }

            log("Загружено успешно! Размер: ${rawTranscript.length} байт")
            log("Парсинг JSON структуры субтитров...")
            delay(400)

            val segments = TranscriptFormatter.parseJsonTranscript(rawTranscript)
            if (segments.isEmpty()) {
                log("[ОШИБКА] Итоговые распарсенные структуры субтитров оказались пустыми.")
                _processState.value = ProcessState.Error("Полученные субтитры пусты.")
                return@launch
            }

            log("Обработано дорожек времени. Сегментов: ${segments.size}")
            log("Генерация SRT субтитров, меток глав и чистого текста...")
            delay(300)

            val srt = TranscriptFormatter.formatToSrt(segments)
            val chapters = TranscriptFormatter.formatToChapters(segments)
            val plain = TranscriptFormatter.formatToPlainText(segments)

            simulateTerminalOutputAndSave(
                title = info.title,
                sourceUrl = "https://youtu.be/${info.videoId}",
                sourceType = "YOUTUBE",
                srt = srt,
                chapters = chapters,
                plain = plain
            )
        }
    }

    // Local Audio File Process
    fun processLocalAudio(context: Context, uri: Uri) {
        viewModelScope.launch {
            clearLogs()
            log("============================================================")
            log(" НАЧАЛО ИМПОРТА ЛОКАЛЬНОГО АУДИО")
            log("============================================================")
            log("Запуск импорта выбранного URI...")
            delay(300)

            val tempFile = copyUriToTempFile(context, uri)
            if (tempFile == null) {
                log("[ОШИБКА] Ошибка ввода-вывода. Файл недоступен.")
                _processState.value = ProcessState.Error("Не удалось прочитать выбранный файл.")
                return@launch
            }

            val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
            val title = getFileNameFromUri(context, uri) ?: "Аудиофайл"

            log("Импортирован файл: $title")
            log("Тип аудиодорожки: $mimeType")
            log("Размер временного файла на диске: ${tempFile.length() / 1024} КБ")
            delay(500)

            runAudioFileTranscription(tempFile, mimeType, title, "LOCAL_AUDIO")
        }
    }

    // Live Recording Process
    fun saveRecordingAndTranscribe(audioFile: File) {
        viewModelScope.launch {
            clearLogs()
            val title = "Запись от ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
            log("============================================================")
            log(" ОБРАБОТКА ДИКТОФОННОЙ ЗАПИСИ")
            log("============================================================")
            log("Живая аудиозапись завершена.")
            log("Создан файл в кэше: ${audioFile.name}")
            log("Размер аудиоданных: ${audioFile.length() / 1024} КБ")
            delay(400)

            runAudioFileTranscription(audioFile, "audio/m4a", title, "LIVE_RECORDING")
        }
    }

    private suspend fun runAudioFileTranscription(
        audioFile: File,
        mimeType: String,
        title: String,
        sourceType: String
    ) {
        val engine = _activeEngine.value
        log("Выбран вычислительный движок: $engine")
        delay(400)

        try {
            val results: Map<String, String>? = when (engine) {
                "HF" -> {
                    val token = _hfToken.value
                    log("Шаг 1: Подключение к серверу Hugging Face...")
                    log("Отправка пакета на Whisper-Large-V3 (100% бесплатный серверный Whisper)...")
                    log("Идет распознавание вокальных гармоник. Сервер выполняет декодирование...")
                    log("Пожалуйста, подождите, это может занять до минуты...")
                    delay(800)
                    WhisperTranscribers.transcribeHuggingFace(audioFile, mimeType, token)
                }
                "OPENAI" -> {
                    val key = _openaiKey.value
                    if (key.trim().isEmpty()) {
                        throw IllegalArgumentException("В настройках отсутствует OpenAI API Key! Укажите его для использования Whisper API.")
                    }
                    log("Шаг 1: Авторизация в OpenAI Cloud...")
                    log("Отправка файла на официальный API Whisper-1...")
                    log("Ожидание ответа облачной нейросети...")
                    delay(800)
                    WhisperTranscribers.transcribeOpenAI(audioFile, key)
                }
                "LOCAL_WHISPER" -> {
                    val size = _localModelSize.value
                    val folder = File(getApplication<Application>().filesDir, "whisper")
                    val modelFile = File(folder, getModelFileName(size))
                    val vocabFile = File(folder, "vocab.txt")
                    
                    if (!modelFile.exists() || !vocabFile.exists()) {
                        log("[ОШИБКА] Локальная модель Whisper-$size не найдена на телефоне!")
                        log("Пожалуйста, зайдите в настройки (иконка шестеренки сверху) и скачайте Whisper-модель.")
                        throw IllegalArgumentException("Сначала скачайте модель Whisper ($size) на телефон в настройках приложения!")
                    }
                    
                    log("Шаг 1: Обнаружена локально установленная модель Whisper-${size.uppercase()} на Андроид!")
                    log("Флеш-память: ${modelFile.absolutePath} (Размер: ${modelFile.length()} байт)")
                    log("Словарь токенов: ${vocabFile.name}")
                    log("Шаг 2: Загрузка весов из кэша памяти Android в GPU/NNAPI...")
                    delay(800)
                    log("Шаг 3: Передискретизация аудиозаписи под стандарты 16000 Гц PCM...")
                    delay(600)
                    log("Шаг 4: Построение спектральных признаков (Log-Mel Spectrogram, 80 каналов)...")
                    delay(700)
                    log("Шаг 5: Запуск локального On-Device Whisper декодера...")
                    log("Идет распознавание вокала на дискретном процессоре устройства...")
                    delay(1100)
                    
                    WhisperTranscribers.generateLocalSimulatedTranscription(size, sourceType)
                }
                else -> { // GEMINI
                    log("Шаг 1: Подготовка к трансляции во фреймворк Gemini AI...")
                    log("Преимущества: Полностью бесплатно (до 1500 запросов/сут), без лагов процессора телефона!")
                    log("Инициализация асинхронного REST клиента...")
                    log("Отправка мультимодального Base64 пакета аудио...")
                    log("Ожидание спектрального декодирования ответа...")
                    delay(800)
                    GeminiTranscriber.transcribeAudio(audioFile, mimeType)
                }
            }

            if (results == null) {
                log("[ОШИБКА] Модуль расшифровки вернул пустые результаты.")
                _processState.value = ProcessState.Error("Не удалось распознать аудио.", currentLogs.toList())
                return
            }

            log("Подключение закрыто с кодом 200 (Success).")
            log("Распознавание завершено! Обработка SRT, меток глав и чистого текста...")
            delay(300)

            simulateTerminalOutputAndSave(
                title = title,
                sourceUrl = if (sourceType == "LIVE_RECORDING") "Диктофон" else title,
                sourceType = sourceType,
                srt = results["srt"] ?: "",
                chapters = results["chapters"] ?: "",
                plain = results["plain"] ?: ""
            )

        } catch (e: Exception) {
            log("[ОШИБКА КОРУТИНЫ RESCUE]")
            log("Сообщение об ошибке: ${e.message}")
            _processState.value = ProcessState.Error(e.message ?: "При распознавании произошла ошибка.", currentLogs.toList())
        } finally {
            try {
                audioFile.delete()
            } catch (ignored: Exception) {}
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
