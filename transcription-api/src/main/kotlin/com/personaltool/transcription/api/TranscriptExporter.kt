package com.personaltool.transcription.api

import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment

object TranscriptExporter {

    fun exportAsPlainText(transcript: Transcript, includeTimestamps: Boolean = true): String {
        val builder = StringBuilder()
        for (segment in transcript.segments) {
            if (includeTimestamps) {
                builder.append("[${formatTimestamp(segment.startTimeMs)}] ")
            }
            if (!segment.speakerTag.isNullOrBlank()) {
                builder.append("${segment.speakerTag}: ")
            }
            builder.appendLine(segment.text)
        }
        return builder.toString().trim()
    }

    fun exportAsSrt(transcript: Transcript): String {
        val builder = StringBuilder()
        transcript.segments.forEachIndexed { index, segment ->
            builder.appendLine("${index + 1}")
            builder.appendLine("${formatSrtTime(segment.startTimeMs)} --> ${formatSrtTime(segment.endTimeMs)}")
            if (!segment.speakerTag.isNullOrBlank()) {
                builder.append("${segment.speakerTag}: ")
            }
            builder.appendLine(segment.text)
            builder.appendLine()
        }
        return builder.toString().trim()
    }

    fun exportAsVtt(transcript: Transcript): String {
        val builder = StringBuilder()
        builder.appendLine("WEBVTT")
        builder.appendLine()
        transcript.segments.forEachIndexed { index, segment ->
            builder.appendLine("${index + 1}")
            builder.appendLine("${formatVttTime(segment.startTimeMs)} --> ${formatVttTime(segment.endTimeMs)}")
            if (!segment.speakerTag.isNullOrBlank()) {
                builder.append("<v ${segment.speakerTag}>")
            }
            builder.append(segment.text)
            if (!segment.speakerTag.isNullOrBlank()) {
                builder.append("</v>")
            }
            builder.appendLine()
            builder.appendLine()
        }
        return builder.toString().trim()
    }

    fun exportAsMarkdown(transcript: Transcript, title: String = "Transcript"): String {
        val builder = StringBuilder()
        builder.appendLine("# $title")
        builder.appendLine()
        builder.appendLine("- **Language:** ${transcript.language}")
        builder.appendLine("- **Confidence:** ${(transcript.confidence * 100).toInt()}%")
        builder.appendLine("- **Segments:** ${transcript.segments.size}")
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()

        for (segment in transcript.segments) {
            val speaker = if (!segment.speakerTag.isNullOrBlank()) "**${segment.speakerTag}** " else ""
            builder.appendLine("`[${formatTimestamp(segment.startTimeMs)}]` $speaker${segment.text}")
            builder.appendLine()
        }
        return builder.toString().trim()
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun formatVttTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
