package com.mimanga.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_name") val sourceName: String,
    @SerialName("chapter_id") val chapterId: String,
    val title: String,
    val number: Float = 0f,
    val url: String = "",
    @SerialName("is_foreign") val isForeign: Boolean = false,
    val language: String = "ru",
)
