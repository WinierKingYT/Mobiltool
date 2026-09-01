package com.personaltool.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus
import org.json.JSONArray
import org.json.JSONObject

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
        val segmentList = mutableListOf<TranscriptSegment>()
        if (segmentsJson.isNotBlank()) {
            runCatching {
                val jsonArray = JSONArray(segmentsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    segmentList.add(
                        TranscriptSegment(
                            id = obj.getString("id"),
                            startTimeMs = obj.getLong("startTimeMs"),
                            endTimeMs = obj.getLong("endTimeMs"),
                            text = obj.getString("text"),
                            speakerTag = if (obj.has("speakerTag") && !obj.isNull("speakerTag")) obj.getString("speakerTag") else null,
                            confidence = obj.optDouble("confidence", 1.0).toFloat()
                        )
                    )
                }
            }
        }

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
            val jsonArray = JSONArray()
            transcript.segments.forEach { segment ->
                val obj = JSONObject().apply {
                    put("id", segment.id)
                    put("startTimeMs", segment.startTimeMs)
                    put("endTimeMs", segment.endTimeMs)
                    put("text", segment.text)
                    put("speakerTag", segment.speakerTag)
                    put("confidence", segment.confidence.toDouble())
                }
                jsonArray.put(obj)
            }

            return TranscriptEntity(
                id = transcript.id,
                targetId = transcript.targetId,
                language = transcript.language,
                status = transcript.status.name,
                segmentsJson = jsonArray.toString(),
                confidence = transcript.confidence,
                errorMessage = transcript.errorMessage,
                createdAt = transcript.createdAt
            )
        }
    }
}
