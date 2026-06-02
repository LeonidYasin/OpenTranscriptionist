package com.example.data.gemini

import android.util.Base64
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiTranscriber {
    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeAudio(audioFile: File, mimeType: String): Map<String, String>? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add GEMINI_API_KEY in the Secrets panel in AI Studio.")
        }

        // 1. Read file and encode to Base64
        val bytes = withRootContextIO {
            val fileInputStream = FileInputStream(audioFile)
            val data = ByteArray(audioFile.length().toInt())
            fileInputStream.read(data)
            fileInputStream.close()
            Base64.encodeToString(data, Base64.NO_WRAP)
        }

        // 2. Build direct REST payload
        val rootJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        // Part 1: System-like prompt instructions
                        val textPart = JSONObject().apply {
                            put("text", """
                                You are an expert audio transcriber. Your task is to transcribe the attached audio into three specific formats.

                                Provide three separate output segments, clearly separated by the specified markers:
                                [SRT_START]
                                <the transcribed subtitles in SRT format, starting index from 1, use timestamps format: HH:MM:SS,MS --> HH:MM:SS,MS, e.g. 00:00:15,250 --> 00:00:18,500>
                                [SRT_END]

                                [CHAPTERS_START]
                                <the timestamps and text line by line, e.g. 00:00 Title or first phrase, 00:15 Next phrase, matching the timeline>
                                [CHAPTERS_END]

                                [PLAIN_START]
                                <the full plain text paragraph, cohesive text, no timestamps, just clean readable transcript text>
                                [PLAIN_END]
                                
                                Go!
                            """.trimIndent())
                        }
                        // Part 2: Multimodal audio inline data
                        val audioPart = JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mimeType", mimeType)
                                put("data", bytes)
                            }
                            put("inlineData", inlineData)
                        }
                        put(textPart)
                        put(audioPart)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errObj = try { JSONObject(responseBody) } catch(e: Exception) { null }
                        val errMsg = errObj?.optJSONObject("error")?.optString("message") ?: "HTTP error ${response.code}"
                        throw IOException("Gemini API Error: $errMsg")
                    }

                    parseResponse(responseBody)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    private fun parseResponse(responseBody: String): Map<String, String>? {
        return try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val firstPart = parts.optJSONObject(0) ?: return null
            val rawText = firstPart.optString("text", "") ?: ""

            // Extract the parts
            val srt = extractSection(rawText, "[SRT_START]", "[SRT_END]")
            val chapters = extractSection(rawText, "[CHAPTERS_START]", "[CHAPTERS_END]")
            val plain = extractSection(rawText, "[PLAIN_START]", "[PLAIN_END]")

            mapOf(
                "srt" to srt.ifEmpty { rawText },
                "chapters" to chapters.ifEmpty { rawText },
                "plain" to plain.ifEmpty { rawText }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractSection(text: String, startTag: String, endTag: String): String {
        val start = text.indexOf(startTag)
        if (start == -1) return ""
        val end = text.indexOf(endTag, start + startTag.length)
        if (end == -1) {
            return text.substring(start + startTag.length).trim()
        }
        return text.substring(start + startTag.length, end).trim()
    }

    private suspend fun <T> withRootContextIO(block: () -> T): T {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            block()
        }
    }
}
