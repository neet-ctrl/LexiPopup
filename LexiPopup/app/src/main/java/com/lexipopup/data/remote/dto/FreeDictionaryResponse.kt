package com.lexipopup.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FreeDictionaryResponse(
    @SerializedName("word") val word: String = "",
    @SerializedName("phonetic") val phonetic: String? = null,
    @SerializedName("phonetics") val phonetics: List<Phonetic> = emptyList(),
    @SerializedName("meanings") val meanings: List<Meaning> = emptyList(),
    @SerializedName("etymology") val etymology: String? = null
)

data class Phonetic(
    @SerializedName("text") val text: String? = null,
    @SerializedName("audio") val audio: String? = null
)

data class Meaning(
    @SerializedName("partOfSpeech") val partOfSpeech: String = "",
    @SerializedName("definitions") val definitions: List<Definition> = emptyList(),
    @SerializedName("synonyms") val synonyms: List<String> = emptyList(),
    @SerializedName("antonyms") val antonyms: List<String> = emptyList()
)

data class Definition(
    @SerializedName("definition") val definition: String = "",
    @SerializedName("example") val example: String? = null,
    @SerializedName("synonyms") val synonyms: List<String> = emptyList(),
    @SerializedName("antonyms") val antonyms: List<String> = emptyList()
)
