package com.mimanga.app.core.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.mimanga.app.core.network.ServerImages
import com.mimanga.app.domain.model.Manga

/**
 * Обложка тайтла.
 *
 * Сначала берётся с нашего сервера (ServerImages.cover): он ходит к CDN
 * источников сам, подставляет нужный Referer и держит кэш, поэтому обложка
 * появляется даже там, где CDN напрямую с телефона не отвечает. Если сервер
 * картинку не отдал, остаётся прямая ссылка из карточки — тогда её пробует
 * загрузить сам клиент.
 */
@Composable
fun CoverImage(
    manga: Manga,
    modifier: Modifier = Modifier.fillMaxSize(),
    contentScale: ContentScale = ContentScale.Crop,
    /** В какой ширине показываем: сервер отдаст копию под этот размер. */
    width: Int = ServerImages.CARD_WIDTH,
) {
    val proxied = ServerImages.cover(manga, width)
    var useDirect by remember(proxied, manga.coverUrl) { mutableStateOf(proxied.isBlank()) }
    val model = if (useDirect) manga.coverUrl else proxied
    AsyncImage(
        model = model,
        contentDescription = manga.title,
        contentScale = contentScale,
        onError = { if (!useDirect && manga.coverUrl.isNotBlank()) useDirect = true },
        modifier = modifier,
    )
}

/**
 * Подложка под шапкой страницы тайтла: та же обложка, размытая до цветовых
 * пятен.
 *
 * Размытие здесь НЕ через Modifier.blur, хотя визуально это оно и есть.
 * Modifier.blur — это эффект уровня слоя: он считается заново каждый кадр, в
 * который попадает, и на шапке во всю ширину экрана это означало пересчёт
 * размытия на каждом кадре прокрутки страницы тайтла — самое дорогое место
 * во всём приложении. Вместо этого обложка запрашивается КРОШЕЧНОЙ (32×48) и
 * растягивается на всю шапку: сглаживание при растяжении и даёт размытие,
 * причём бесплатно — и при загрузке (вместо картинки в мегабайт читается
 * пара килобайт: сервер отдаёт копию шириной 64 точки), и при отрисовке.
 */
@Composable
fun CoverBackdrop(manga: Manga, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val proxied = ServerImages.cover(manga, ServerImages.BLUR_WIDTH)
    val url = proxied.ifBlank { manga.coverUrl }
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(32, 48)
            .scale(Scale.FILL)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
