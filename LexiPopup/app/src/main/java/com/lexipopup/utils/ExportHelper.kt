package com.lexipopup.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.lexipopup.domain.models.WordEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat { CSV, JSON, ANKI_TSV }

@Singleton
class ExportHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val exportDir get() = File(context.cacheDir, "exports").also { it.mkdirs() }
    private val timestamp get() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    suspend fun exportWords(words: List<WordEntry>, format: ExportFormat): Uri = withContext(Dispatchers.IO) {
        val file = when (format) {
            ExportFormat.CSV -> exportCsv(words)
            ExportFormat.JSON -> exportJson(words)
            ExportFormat.ANKI_TSV -> exportAnkiTsv(words)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun exportCsv(words: List<WordEntry>): File {
        val file = File(exportDir, "lexipopup_vocab_$timestamp.csv")
        OutputStreamWriter(FileOutputStream(file)).use { w ->
            w.write("word,part_of_speech,meaning,hindi_meaning,example,synonyms,antonyms,difficulty\n")
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
                w.write("$row\n")
            }
        }
        return file
    }

    private fun exportJson(words: List<WordEntry>): File {
        val file = File(exportDir, "lexipopup_vocab_$timestamp.json")
        OutputStreamWriter(FileOutputStream(file)).use { w ->
            w.write("[\n")
            words.forEachIndexed { i, entry ->
                w.write(
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
                w.write("\n")
            }
            w.write("]\n")
        }
        return file
    }

    /**
     * Anki-compatible TSV: Front\tBack
     * Import via File → Import in Anki desktop.
     * Anki separates fields by tab; front=word, back=full definition.
     */
    private fun exportAnkiTsv(words: List<WordEntry>): File {
        val file = File(exportDir, "lexipopup_anki_$timestamp.txt")
        OutputStreamWriter(FileOutputStream(file)).use { w ->
            w.write("#separator:tab\n#html:false\n#notetype:Basic\n")
            words.forEach { entry ->
                val front = entry.word
                val back = buildString {
                    append(entry.shortMeaning)
                    if (entry.hindiMeaning.isNotBlank()) append("\n\nहिंदी: ${entry.hindiMeaning}")
                    if (entry.exampleSentence.isNotBlank()) append("\n\n\"${entry.exampleSentence}\"")
                    if (entry.synonyms.isNotEmpty()) append("\n\nSynonyms: ${entry.synonyms.take(3).joinToString(", ")}")
                }
                w.write("$front\t${back.replace("\t", " ")}\n")
            }
        }
        return file
    }

    fun shareExport(uri: Uri, format: ExportFormat): Intent {
        val mime = when (format) {
            ExportFormat.CSV -> "text/csv"
            ExportFormat.JSON -> "application/json"
            ExportFormat.ANKI_TSV -> "text/plain"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LexiPopup Vocabulary Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun exportSettingsJson(settingsJson: String): Uri {
        val file = File(exportDir, "lexipopup_settings_$timestamp.json")
        file.writeText(settingsJson)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun String.csvEscape() = "\"${replace("\"", "\"\"")}\""
    private fun String.jsonEscape() = replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r")
}
