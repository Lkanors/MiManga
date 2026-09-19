package com.mimanga.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Page(
    val index: Int,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    /** Резервный путь через серверный прокси /api/image (если CDN блокирует прямой доступ). */
    @SerialName("proxy_url") val proxyUrl: String? = null,
)
