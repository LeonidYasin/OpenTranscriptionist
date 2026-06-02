package com.example.data.youtube

import org.json.JSONObject

data class TranscriptSegment(
    val startMs: Long,
    val durationMs: Long,
    val text: String
)

object TranscriptFormatter {

    fun parseJsonTranscript(jsonString: String): List<TranscriptSegment> {
        val list = mutableListOf<TranscriptSegment>()
        try {
            val root = JSONObject(jsonString)
            val events = root.optJSONArray("events") ?: return list
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val tStartMs = event.optLong("tStartMs", -1L)
                val dDurationMs = event.optLong("dDurationMs", 0L)
                if (tStartMs == -1L) continue
                
                val segs = event.optJSONArray("segs") ?: continue
                val textBuilder = StringBuilder()
                for (j in 0 until segs.length()) {
                    val seg = segs.optJSONObject(j) ?: continue
                    textBuilder.append(seg.optString("utf8", ""))
                }
                
                val text = textBuilder.toString().replace("\n", " ").trim()
                if (text.isEmpty()) continue
                
                list.add(TranscriptSegment(tStartMs, dDurationMs, text))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatChapterTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatToSrt(segments: List<TranscriptSegment>): String {
        val srtBuilder = StringBuilder()
        var index = 1
        for (segment in segments) {
            val startStr = formatSrtTime(segment.startMs)
            val endStr = formatSrtTime(segment.startMs + segment.durationMs)
            srtBuilder.append("$index\n")
            srtBuilder.append("$startStr --> $endStr\n")
            srtBuilder.append("${segment.text}\n\n")
            index++
        }
        return srtBuilder.toString().trim()
    }

    fun formatToChapters(segments: List<TranscriptSegment>): String {
        val chaptersBuilder = StringBuilder()
        var first = true
        for (segment in segments) {
            val timeStr = if (first && segment.startMs < 2000L) {
                formatChapterTime(0L)
            } else {
                formatChapterTime(segment.startMs)
            }
            first = false
            chaptersBuilder.append("$timeStr ${segment.text}\n")
        }
        return chaptersBuilder.toString().trim()
    }

    fun formatToPlainText(segments: List<TranscriptSegment>): String {
        return segments.joinToString(" ") { it.text.trim() }.replace("\\s+".toRegex(), " ").trim()
    }
}
