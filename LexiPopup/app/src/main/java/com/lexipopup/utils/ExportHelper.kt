package com.lexipopup.utils

import android.content.Context
import android.net.Uri
import com.lexipopup.domain.models.WordEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat { CSV, JSON, ANKI_TSV }

@Singleton
class ExportHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun exportWordsToUri(words: List<WordEntry>, format: ExportFormat, uri: Uri) = withContext(Dispatchers.IO) {
        val content = when (format) {
            ExportFormat.CSV -> buildCsvContent(words)
            ExportFormat.JSON -> buildJsonContent(words)
            ExportFormat.ANKI_TSV -> buildAnkiContent(words)
        }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    fun exportSettingsToUri(settingsJson: String, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(settingsJson.toByteArray(Charsets.UTF_8))
        }
    }

    private fun buildCsvContent(words: List<WordEntry>): String {
        val sb = StringBuilder()
        sb.append("word,part_of_speech,meaning,hindi_meaning,example,synonyms,antonyms,difficulty\n")
        words.forEach { entry ->
            val row = listOf(
                entry.word,
                entry.partOfSpeech,
                entry.shortMeaning.csvEscape(),
                entry.hindiMeaning.csvEscape(),
                entry.exampleSentence.csvEscape(),
                entry.synonyms.joinToString(";"),
                entry.antonyms.joinToString(";"),
                entry.difficultyLabel
            ).joinToString(",")
            sb.append("$row\n")
        }
        return sb.toString()
    }

    private fun buildJsonContent(words: List<WordEntry>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        words.forEachIndexed { i, entry ->
            sb.append(
                """  {
    "word": "${entry.word}",
    "part_of_speech": "${entry.partOfSpeech}",
    "meaning": "${entry.shortMeaning.jsonEscape()}",
    "hindi_meaning": "${entry.hindiMeaning.jsonEscape()}",
    "example": "${entry.exampleSentence.jsonEscape()}",
    "synonyms": [${entry.synonyms.joinToString(",") { "\"$it\"" }}],
    "antonyms": [${entry.antonyms.joinToString(",") { "\"$it\"" }}],
    "difficulty": "${entry.difficultyLabel}",
    "frequency": ${entry.frequencyRating}
  }${if (i < words.size - 1) "," else ""}"""
            )
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    private fun buildAnkiContent(words: List<WordEntry>): String {
        val sb = StringBuilder()
        sb.append("#separator:tab\n#html:false\n#notetype:Basic\n")
        words.forEach { entry ->
            val front = entry.word
            val back = buildString {
                append(entry.shortMeaning)
                if (entry.hindiMeaning.isNotBlank()) append("\n\nहिंदी: ${entry.hindiMeaning}")
                if (entry.exampleSentence.isNotBlank()) append("\n\n\"${entry.exampleSentence}\"")
                if (entry.synonyms.isNotEmpty()) append("\n\nSynonyms: ${entry.synonyms.take(3).joinToString(", ")}")
            }
            sb.append("$front\t${back.replace("\t", " ")}\n")
        }
        return sb.toString()
    }

    private fun String.csvEscape() = "\"${replace("\"", "\"\"")}\""
    private fun String.jsonEscape() = replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r")
}
