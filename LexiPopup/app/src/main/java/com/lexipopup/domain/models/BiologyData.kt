package com.lexipopup.domain.models

import com.google.gson.Gson

/**
 * Extended biology-specific data stored as JSON in the bio_ext_data column.
 * Fields map to the biology popup UI sections.
 * Diagram URL is reserved for future database integrations (see BIOLOGY_MODE.md).
 */
data class BiologyData(
    val scientificClassification: Map<String, String> = emptyMap(),
    val functions: List<String> = emptyList(),
    val structure: List<String> = emptyList(),
    val diseases: List<String> = emptyList(),
    val relatedTerms: List<String> = emptyList(),
    val difficultyLabel: String = "",
    val difficultyPercent: Int = 0,
    val frequencyPercent: Int = 0,
    val diagramUrl: String = ""
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): BiologyData = try {
            if (json.isBlank() || json == "{}") BiologyData()
            else gson.fromJson(json, BiologyData::class.java) ?: BiologyData()
        } catch (_: Exception) { BiologyData() }

        fun toJson(data: BiologyData): String = gson.toJson(data)

        val EMPTY = BiologyData()
    }

    val hasClassification get() = scientificClassification.isNotEmpty()
    val hasFunctions get() = functions.isNotEmpty()
    val hasStructure get() = structure.isNotEmpty()
    val hasDiseases get() = diseases.isNotEmpty()
    val hasRelatedTerms get() = relatedTerms.isNotEmpty()
    val hasDiagram get() = diagramUrl.isNotBlank()
}
