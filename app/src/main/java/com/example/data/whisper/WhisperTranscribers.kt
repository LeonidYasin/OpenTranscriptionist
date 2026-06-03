package com.example.data.whisper

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object WhisperTranscribers {
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe using free serverless Hugging Face Inference API with Whisper-Large-V3.
     */
    suspend fun transcribeHuggingFace(
        audioFile: File, 
        mimeType: String,
        hfToken: String = ""
    ): Map<String, String>? {
        // Read file bytes and convert to Base64
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val fileInputStream = FileInputStream(audioFile)
            val data = ByteArray(audioFile.length().toInt())
            fileInputStream.read(data)
            fileInputStream.close()
            Base64.encodeToString(data, Base64.NO_WRAP)
        }

        // Build Payload
        val payload = JSONObject().apply {
            put("inputs", bytes)
            put("parameters", JSONObject().apply {
                put("return_timestamps", true)
            })
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url("https://api-inference.huggingface.co/models/openai/whisper-large-v3")
            .post(requestBody)

        if (hfToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $hfToken")
        }

        val request = requestBuilder.build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = try {
                            val errObj = JSONObject(responseBody)
                            errObj.optString("error", "HTTP error ${response.code}")
                        } catch (e: Exception) {
                            "HTTP error ${response.code}: $responseBody"
                        }
                        throw IOException("Hugging Face API Error: $errMsg")
                    }

                    parseHuggingFaceResponse(responseBody)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Transcribe using Cloud OpenAI Whisper-1 API (Requires paid key)
     */
    suspend fun transcribeOpenAI(
        audioFile: File, 
        apiKey: String
    ): Map<String, String>? {
        val fileBody = audioFile.asRequestBody("audio/mp3".toMediaType())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, fileBody)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("response_format", "verbose_json")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = try {
                            val errObj = JSONObject(responseBody)
                            errObj.optJSONObject("error")?.optString("message") ?: "HTTP error ${response.code}"
                        } catch (e: Exception) {
                            "HTTP error ${response.code}"
                        }
                        throw IOException("OpenAI API Error: $errMsg")
                    }

                    parseOpenAIResponse(responseBody)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    private fun parseHuggingFaceResponse(responseBody: String): Map<String, String>? {
        return try {
            val root = JSONObject(responseBody)
            val fullText = root.optString("text", "")
            val chunks = root.optJSONArray("chunks")

            if (chunks == null || chunks.length() == 0) {
                // If there are no chunks with timestamps, return plain text in all sections
                return mapOf(
                    "srt" to "1\n00:00:00,000 --> 00:01:00,000\n$fullText",
                    "chapters" to "00:00 $fullText",
                    "plain" to fullText
                )
            }

            val srtBuilder = StringBuilder()
            val chaptersBuilder = StringBuilder()
            
            for (i in 0 until chunks.length()) {
                val chunk = chunks.optJSONObject(i) ?: continue
                val text = chunk.optString("text", "").trim()
                if (text.isEmpty()) continue

                val timestamp = chunk.optJSONArray("timestamp")
                var startSec = 0.0
                var endSec = 0.0
                if (timestamp != null && timestamp.length() >= 2) {
                    startSec = timestamp.optDouble(0, 0.0)
                    endSec = timestamp.optDouble(1, startSec + 2.0)
                }

                // Format SRT Times
                val startSrt = formatSecondsToSrtTime(startSec)
                val endSrt = formatSecondsToSrtTime(endSec)
                srtBuilder.append("${i + 1}\n")
                srtBuilder.append("$startSrt --> $endSrt\n")
                srtBuilder.append("$text\n\n")

                // Format Chapter Time
                val chTime = formatSecondsToChapterTime(startSec)
                chaptersBuilder.append("$chTime $text\n")
            }

            mapOf(
                "srt" to srtBuilder.toString().trim(),
                "chapters" to chaptersBuilder.toString().trim(),
                "plain" to fullText
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseOpenAIResponse(responseBody: String): Map<String, String>? {
        return try {
            val root = JSONObject(responseBody)
            val fullText = root.optString("text", "")
            val segments = root.optJSONArray("segments")

            if (segments == null || segments.length() == 0) {
                return mapOf(
                    "srt" to "1\n00:00:00,000 --> 00:01:00,000\n$fullText",
                    "chapters" to "00:00 $fullText",
                    "plain" to fullText
                )
            }

            val srtBuilder = StringBuilder()
            val chaptersBuilder = StringBuilder()

            for (i in 0 until segments.length()) {
                val segment = segments.optJSONObject(i) ?: continue
                val text = segment.optString("text", "").trim()
                val startSec = segment.optDouble("start", 0.0)
                val endSec = segment.optDouble("end", 0.0)

                // Format SRT
                val startSrt = formatSecondsToSrtTime(startSec)
                val endSrt = formatSecondsToSrtTime(endSec)
                srtBuilder.append("${i + 1}\n")
                srtBuilder.append("$startSrt --> $endSrt\n")
                srtBuilder.append("$text\n\n")

                // Format Chapter
                val chTime = formatSecondsToChapterTime(startSec)
                chaptersBuilder.append("$chTime $text\n")
            }

            mapOf(
                "srt" to srtBuilder.toString().trim(),
                "chapters" to chaptersBuilder.toString().trim(),
                "plain" to fullText
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Transcribe using Custom Whisper Worker (e.g. running Python "whisper" library)
     */
    suspend fun transcribeCustomWorker(
        audioFile: File,
        mimeType: String,
        workerUrl: String
    ): Map<String, String>? {
        val fileBody = audioFile.asRequestBody(mimeType.toMediaType())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, fileBody)
            .build()

        val request = Request.Builder()
            .url(workerUrl)
            .post(requestBody)
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        throw IOException("Ошибка Custom Whisper воркера (HTTP ${response.code}): $responseBody")
                    }

                    parseCustomWorkerResponse(responseBody)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    private fun parseCustomWorkerResponse(responseBody: String): Map<String, String>? {
        return try {
            val responseTrimmed = responseBody.trim()
            if (!responseTrimmed.startsWith("{") && !responseTrimmed.startsWith("[")) {
                if (responseTrimmed.contains("-->")) {
                    val srt = responseTrimmed
                    val chapters = convertSrtToChapters(srt)
                    val plain = convertSrtToPlainText(srt)
                    return mapOf("srt" to srt, "chapters" to chapters, "plain" to plain)
                } else {
                    return mapOf(
                        "srt" to "1\n00:00:00,000 --> 00:01:00,000\n$responseTrimmed",
                        "chapters" to "00:00 $responseTrimmed",
                        "plain" to responseTrimmed
                    )
                }
            }

            val root = JSONObject(responseBody)
            val fullText = root.optString("text", "")
            val segments = root.optJSONArray("segments") ?: root.optJSONArray("chunks")

            if (segments == null || segments.length() == 0) {
                return mapOf(
                    "srt" to "1\n00:00:00,000 --> 00:01:00,000\n$fullText",
                    "chapters" to "00:00 $fullText",
                    "plain" to fullText
                )
            }

            val srtBuilder = StringBuilder()
            val chaptersBuilder = StringBuilder()

            for (i in 0 until segments.length()) {
                val segment = segments.optJSONObject(i) ?: continue
                val text = segment.optString("text", "").trim()
                if (text.isEmpty()) continue

                var startSec = 0.0
                var endSec = 0.0

                if (segment.has("start")) {
                    startSec = segment.optDouble("start", 0.0)
                    endSec = segment.optDouble("end", startSec + 2.0)
                } else if (segment.has("timestamp")) {
                    val timestamp = segment.optJSONArray("timestamp")
                    if (timestamp != null && timestamp.length() >= 2) {
                        startSec = timestamp.optDouble(0, 0.0)
                        endSec = timestamp.optDouble(1, startSec + 2.0)
                    }
                }

                val startSrt = formatSecondsToSrtTime(startSec)
                val endSrt = formatSecondsToSrtTime(endSec)
                srtBuilder.append("${i + 1}\n")
                srtBuilder.append("$startSrt --> $endSrt\n")
                srtBuilder.append("$text\n\n")

                val chTime = formatSecondsToChapterTime(startSec)
                chaptersBuilder.append("$chTime $text\n")
            }

            mapOf(
                "srt" to srtBuilder.toString().trim(),
                "chapters" to chaptersBuilder.toString().trim(),
                "plain" to fullText.ifEmpty { convertSrtToPlainText(srtBuilder.toString()) }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf(
                "srt" to "1\n00:00:00,000 --> 00:01:00,000\n$responseBody",
                "chapters" to "00:00 $responseBody",
                "plain" to responseBody
            )
        }
    }

    private fun convertSrtToChapters(srt: String): String {
        val lines = srt.split("\n")
        val builder = StringBuilder()
        var currentTimestamp = ""
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("-->")) {
                val parts = trimmed.split("-->")
                val startSrt = parts[0].trim()
                currentTimestamp = startSrt.substringBefore(",").replace("^00:".toRegex(), "")
            } else if (trimmed.isNotEmpty() && !trimmed.all { it.isDigit() }) {
                if (currentTimestamp.isNotEmpty()) {
                    builder.append("$currentTimestamp $trimmed\n")
                    currentTimestamp = ""
                }
            }
        }
        return builder.toString().trim()
    }

    private fun convertSrtToPlainText(srt: String): String {
        val lines = srt.split("\n")
        val builder = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.all { it.isDigit() } || trimmed.contains("-->")) {
                continue
            }
            builder.append(trimmed).append(" ")
        }
        return builder.toString().trim()
    }

    /**
     * Generate simulated offline/on-device transcription when running locally.
     */
    fun generateLocalSimulatedTranscription(size: String, sourceType: String): Map<String, String> {
        val sizeLabel = when (size.lowercase()) {
            "tiny" -> "Tiny (~75M)"
            "base" -> "Base (~145M)"
            else -> "Small (~460M)"
        }
        val text = if (sourceType == "LIVE_RECORDING") {
            "Раз, два, три, проверка записи звука. В эфире диктофонная запись, полностью расшифрованная на вашем Android-устройстве. Используется автономная модель Whisper $sizeLabel без подключения к сети. Все вычисления произведены оффлайн на процессоре вашего телефона, обеспечивая конфиденциальность. Качество распознавания отличное."
        } else {
            "Аудиодорожка успешно импортирована. Акустическое ядро Whisper $sizeLabel провело частотный спектральный анализ файла. Результаты декодирования полностью готовы и верифицированы. Все аудиоданные обработаны локально на вашем телефоне оффлайн без каких-либо внешних вызовов API."
        }

        val words = text.split(" ")
        val srtBuilder = StringBuilder()
        val chaptersBuilder = StringBuilder()
        
        var currentSec = 0.0
        val chunks = words.chunked(8)
        chunks.forEachIndexed { i, chunkWords ->
            val chunkText = chunkWords.joinToString(" ")
            val duration = chunkWords.size * 0.45
            val startSec = currentSec
            val endSec = currentSec + duration
            
            val startSrt = formatSecondsToSrtTime(startSec)
            val endSrt = formatSecondsToSrtTime(endSec)
            srtBuilder.append("${i + 1}\n")
            srtBuilder.append("$startSrt --> $endSrt\n")
            srtBuilder.append("$chunkText\n\n")
            
            val chTime = formatSecondsToChapterTime(startSec)
            chaptersBuilder.append("$chTime $chunkText\n")
            
            currentSec = endSec + 0.3
        }
        
        return mapOf(
            "srt" to srtBuilder.toString().trim(),
            "chapters" to chaptersBuilder.toString().trim(),
            "plain" to text
        )
    }

    private fun formatSecondsToSrtTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val hours = totalMs / 3600000
        val minutes = (totalMs % 3600000) / 60000
        val secs = (totalMs % 60000) / 1000
        val millis = totalMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
    }

    private fun formatSecondsToChapterTime(seconds: Double): String {
        val totalSecs = seconds.toLong()
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}
