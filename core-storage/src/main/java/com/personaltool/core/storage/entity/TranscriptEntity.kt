package com.personaltool.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val language: String,
    val status: String,
    val segmentsJson: String,
    val confidence: Float,
    val errorMessage: String?,
    val createdAt: Long
) {
    fun toDomain(): Transcript {
        val segmentList = parseSegmentsJson(segmentsJson)

        return Transcript(
            id = id,
            targetId = targetId,
            language = language,
            status = runCatching { TranscriptStatus.valueOf(status) }.getOrDefault(TranscriptStatus.NONE),
            segments = segmentList,
            confidence = confidence,
            errorMessage = errorMessage,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(transcript: Transcript): TranscriptEntity {
            return TranscriptEntity(
                id = transcript.id,
                targetId = transcript.targetId,
                language = transcript.language,
                status = transcript.status.name,
                segmentsJson = serializeSegmentsJson(transcript.segments),
                confidence = transcript.confidence,
                errorMessage = transcript.errorMessage,
                createdAt = transcript.createdAt
            )
        }

        private fun serializeSegmentsJson(segments: List<TranscriptSegment>): String {
            if (segments.isEmpty()) return "[]"
            val sb = StringBuilder("[")
            segments.forEachIndexed { index, seg ->
                if (index > 0) sb.append(",")
                sb.append("{")
                sb.append("\"id\":").append(escapeJson(seg.id)).append(",")
                sb.append("\"startTimeMs\":").append(seg.startTimeMs).append(",")
                sb.append("\"endTimeMs\":").append(seg.endTimeMs).append(",")
                sb.append("\"text\":").append(escapeJson(seg.text)).append(",")
                val speaker = seg.speakerTag
                if (speaker != null) {
                    sb.append("\"speakerTag\":").append(escapeJson(speaker)).append(",")
                } else {
                    sb.append("\"speakerTag\":null,")
                }
                sb.append("\"confidence\":").append(seg.confidence)
                sb.append("}")
            }
            sb.append("]")
            return sb.toString()
        }

        private fun parseSegmentsJson(json: String): List<TranscriptSegment> {
            if (json.isBlank() || json == "[]") return emptyList()
            val result = mutableListOf<TranscriptSegment>()
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val content = trimmed.substring(1, trimmed.length - 1).trim()
            if (content.isEmpty()) return emptyList()

            var i = 0
            while (i < content.length) {
                val startObj = content.indexOf('{', i)
                if (startObj == -1) break
                val endObj = findClosingBrace(content, startObj)
                if (endObj == -1) break
                val objStr = content.substring(startObj + 1, endObj)
                parseSegmentObject(objStr)?.let { result.add(it) }
                i = endObj + 1
            }
            return result
        }

        private fun findClosingBrace(str: String, startIdx: Int): Int {
            var inQuotes = false
            var escape = false
            for (k in startIdx until str.length) {
                val c = str[k]
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\') {
                    escape = true
                    continue
                }
                if (c == '"') {
                    inQuotes = !inQuotes
                    continue
                }
                if (!inQuotes && c == '}') {
                    return k
                }
            }
            return -1
        }

        private fun parseSegmentObject(objStr: String): TranscriptSegment? {
            return runCatching {
                var id = ""
                var startTimeMs = 0L
                var endTimeMs = 0L
                var text = ""
                var speakerTag: String? = null
                var confidence = 1.0f

                val pairs = splitKeyValues(objStr)
                for ((key, value) in pairs) {
                    when (key.trim().removeSurrounding("\"")) {
                        "id" -> id = unescapeJson(value.trim())
                        "startTimeMs" -> startTimeMs = value.trim().toLongOrNull() ?: 0L
                        "endTimeMs" -> endTimeMs = value.trim().toLongOrNull() ?: 0L
                        "text" -> text = unescapeJson(value.trim())
                        "speakerTag" -> {
                            val v = value.trim()
                            speakerTag = if (v == "null" || v.isEmpty()) null else unescapeJson(v)
                        }
                        "confidence" -> confidence = value.trim().toFloatOrNull() ?: 1.0f
                    }
                }
                TranscriptSegment(id, startTimeMs, endTimeMs, text, speakerTag, confidence)
            }.getOrNull()
        }

        private fun splitKeyValues(str: String): List<Pair<String, String>> {
            val list = mutableListOf<Pair<String, String>>()
            var inQuotes = false
            var escape = false
            var currentKey = ""
            var currentValue = ""
            var parsingValue = false

            for (c in str) {
                if (escape) {
                    if (parsingValue) currentValue += c else currentKey += c
                    escape = false
                    continue
                }
                if (c == '\\') {
                    if (parsingValue) currentValue += c else currentKey += c
                    escape = true
                    continue
                }
                if (c == '"') {
                    inQuotes = !inQuotes
                    if (parsingValue) currentValue += c else currentKey += c
                    continue
                }
                if (!inQuotes && c == ':') {
                    parsingValue = true
                    continue
                }
                if (!inQuotes && c == ',') {
                    list.add(Pair(currentKey, currentValue))
                    currentKey = ""
                    currentValue = ""
                    parsingValue = false
                    continue
                }
                if (parsingValue) currentValue += c else currentKey += c
            }
            if (currentKey.isNotBlank()) {
                list.add(Pair(currentKey, currentValue))
            }
            return list
        }

        private fun escapeJson(str: String): String {
            val sb = StringBuilder("\"")
            for (c in str) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(c)
                }
            }
            sb.append("\"")
            return sb.toString()
        }

        private fun unescapeJson(str: String): String {
            val s = if (str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) {
                str.substring(1, str.length - 1)
            } else {
                str
            }
            val sb = StringBuilder()
            var escape = false
            for (c in s) {
                if (escape) {
                    when (c) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        else -> sb.append(c)
                    }
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}
