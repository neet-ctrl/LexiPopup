package com.lexipopup.utils

import android.content.Context
import android.net.Uri
import com.lexipopup.domain.models.AppMode
import com.lexipopup.domain.models.BiologyData
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
        val hasBio = words.any { it.isBiology() }
        val sb = StringBuilder()
        if (hasBio) {
            sb.append("word,mode,part_of_speech,meaning,hindi_meaning,example,synonyms,antonyms,difficulty,classification,functions,structure,diseases,related_terms\n")
        } else {
            sb.append("word,part_of_speech,meaning,hindi_meaning,example,synonyms,antonyms,difficulty\n")
        }
        words.forEach { entry ->
            if (hasBio) {
                val bio = entry.biologyData()
                val classification = bio.scientificClassification.entries.joinToString(";") { "${it.key}:${it.value}" }
                val row = listOf(
                    entry.word,
                    entry.mode,
                    entry.partOfSpeech,
                    entry.shortMeaning.csvEscape(),
                    entry.hindiMeaning.csvEscape(),
                    entry.exampleSentence.csvEscape(),
                    entry.synonyms.joinToString(";"),
                    entry.antonyms.joinToString(";"),
                    entry.difficultyLabel,
                    classification.csvEscape(),
                    bio.functions.joinToString(";").csvEscape(),
                    bio.structure.joinToString(";").csvEscape(),
                    bio.diseases.joinToString(";").csvEscape(),
                    bio.relatedTerms.joinToString(";").csvEscape()
                ).joinToString(",")
                sb.append("$row\n")
            } else {
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
        }
        return sb.toString()
    }

    private fun buildJsonContent(words: List<WordEntry>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        words.forEachIndexed { i, entry ->
            val bioBlock = if (entry.isBiology()) {
                val bio = entry.biologyData()
                val classJson = bio.scientificClassification.entries
                    .joinToString(",") { "\"${it.key.jsonEscape()}\":\"${it.value.jsonEscape()}\"" }
                buildString {
                    append(",\n    \"mode\": \"${entry.mode}\"")
                    append(",\n    \"classification\": {$classJson}")
                    append(",\n    \"functions\": [${bio.functions.joinToString(",") { "\"${it.jsonEscape()}\"" }}]")
                    append(",\n    \"structure\": [${bio.structure.joinToString(",") { "\"${it.jsonEscape()}\"" }}]")
                    append(",\n    \"diseases\": [${bio.diseases.joinToString(",") { "\"${it.jsonEscape()}\"" }}]")
                    append(",\n    \"related_terms\": [${bio.relatedTerms.joinToString(",") { "\"${it.jsonEscape()}\"" }}]")
                }
            } else ""
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
    "frequency": ${entry.frequencyRating}$bioBlock
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
                if (entry.isBiology()) {
                    val bio = entry.biologyData()
                    if (bio.hasClassification) {
                        val cls = bio.scientificClassification.entries.take(3).joinToString(", ") { "${it.key}: ${it.value}" }
                        append("\n\nClassification: $cls")
                    }
                    if (bio.hasFunctions) append("\n\nFunctions: ${bio.functions.take(3).joinToString("; ")}")
                    if (bio.hasRelatedTerms) append("\n\nRelated: ${bio.relatedTerms.take(3).joinToString(", ")}")
                } else {
                    if (entry.synonyms.isNotEmpty()) append("\n\nSynonyms: ${entry.synonyms.take(3).joinToString(", ")}")
                }
            }
            sb.append("$front\t${back.replace("\t", " ")}\n")
        }
        return sb.toString()
    }

    private fun String.csvEscape() = "\"${replace("\"", "\"\"")}\""
    private fun String.jsonEscape() = replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r")
}
