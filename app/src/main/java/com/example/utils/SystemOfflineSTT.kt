package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SystemOfflineSTT(
    private val context: Context,
    private val onPartialResults: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String, Int) -> Unit,
    private val onStatusChange: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val recognizedStringBuilder = java.lang.StringBuilder()
    private var lastEstablishedText = ""

    fun startListening() {
        if (isListening) return
        
        // Run on main thread
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(listener)
                    }

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // Force offline if possible
                    }

                    recognizedStringBuilder.clear()
                    lastEstablishedText = ""
                    speechRecognizer?.startListening(intent)
                    isListening = true
                    onStatusChange("Говорите... Системный STT слушает")
                } else {
                    onError("Голосовое распознавание недоступно на вашем устройстве.", -1)
                }
            } catch (e: Exception) {
                onError("Ошибка инициализации STT: ${e.message}", -2)
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                isListening = false
                onStatusChange("Остановка распознавания...")
                
                // Trigger final combine
                val finalResult = (lastEstablishedText + " " + recognizedStringBuilder.toString()).trim()
                onFinalResult(finalResult.ifEmpty { "Голос не распознан" })
            } catch (e: Exception) {
                Log.e("SystemOfflineSTT", "Error stopping: ${e.message}")
            }
        }
    }

    fun destroy() {
        val mainHandler = android.os.Handler(context.mainLooper)
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                isListening = false
            } catch (e: Exception) {
                Log.e("SystemOfflineSTT", "Error destroying: ${e.message}")
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStatusChange("Система готова. Начните говорить...")
        }

        override fun onBeginningOfSpeech() {
            onStatusChange("Распознавание голоса в реальном времени...")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Can be used for custom microphone level indicators
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            onStatusChange("Обработка речи...")
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудиозаписи"
                SpeechRecognizer.ERROR_CLIENT -> "Внутренняя ошибка приложения. Скачайте языковые пакеты в Google Сервисах!"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Отсутствуют разрешения на запись звука."
                SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети. Включите оффлайн пакеты для автономной работы."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут соединения."
                SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана. Пожалуйста, говорите четче."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Движок занят. Попробуйте еще раз."
                SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера распознавания речи."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не слышно голоса. Начните говорить заново."
                else -> "Неизвестная ошибка распознавания ($error)."
            }
            onError(errorMessage, error)
            isListening = false
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                if (text.isNotEmpty()) {
                    lastEstablishedText = text
                    onFinalResult(text)
                }
            }
            isListening = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partialText = matches[0]
                if (partialText.isNotEmpty()) {
                    onPartialResults(partialText)
                    recognizedStringBuilder.clear()
                    recognizedStringBuilder.append(partialText)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
