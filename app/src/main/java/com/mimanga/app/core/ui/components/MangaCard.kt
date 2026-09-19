package com.mimanga.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.LibraryStatus
import com.mimanga.app.domain.model.Manga

/**
 * Карточка тайтла в сетке и в строках главной.
 *
 * Снизу на обложке — оценка с числом оценивших и число глав (требование к
 * карточке), сверху слева — значок состояния, если пользователь его поставил.
 * Всё это лежит ПОВЕРХ обложки на градиентной подложке: так карточка остаётся
 * обложкой, а не таблицей с цифрами.
 */
@Composable
fun MangaCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRating: Boolean = true,
    showChapters: Boolean = true,
    showStatusBadge: Boolean = true,
    compact: Boolean = false,
    /** Доля прочитанного 0..1: полоска внизу обложки в строке «продолжить». */
    progress: Float? = null,
    /** Подпись под названием вместо обычной — например, «Гл. 12 · 40%». */
    caption: String? = null,
    /**
     * Отдельное действие для названия под обложкой.
     *
     * Задано — карточка делится на две кнопки: обложка открывает страницу
     * тайтла, название с подписью делает что-то своё (в строке «продолжить
     * чтение» — открывает главу с места остановки). Не задано — вся карточка
     * одна кнопка, как в каталоге.
     */
    onTitleClick: (() -> Unit)? = null,
) {
    val accents = LocalAppAccents.current
    val split = onTitleClick != null
    val hasFooter = (showRating && manga.rating != null) ||
                    (showChapters && manga.chaptersCount > 0)
    Column(modifier = if (split) modifier else modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(20.dp))
                .then(if (split) Modifier.clickable(onClick = onClick) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            CoverImage(manga = manga, modifier = Modifier.fillMaxSize())
            // Подложка под оценкой и числом глав — только под ними, нижней
            // полосой. Раньше градиент лежал во всю обложку: в сетке это
            // десяток полноразмерных полупрозрачных слоёв поверх десятка
            // картинок на каждый кадр прокрутки, причём верхние две трети
            // слоя всё равно прозрачные. Если показывать под обложкой нечего,
            // подложки нет вовсе.
            if (hasFooter) {
                Box(modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .background(accents.cardOverlay))
            }

            if (showStatusBadge) {
                manga.libraryStatus?.let { status ->
                    StatusBadge(
                        status = status,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }
            }
            if (manga.isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "В избранном",
                    tint = accents.favorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp),
                )
            }

            if (hasFooter) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showRating && manga.rating != null) {
                        Icon(Icons.Default.Star, null, tint = accents.rating,
                             modifier = Modifier.size(13.dp))
                        Text(
                            text = buildString {
                                append(formatRating(manga.rating))
                                if (manga.ratingCount > 0) append(" · ${formatCount(manga.ratingCount)}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    if (showChapters && manga.chaptersCount > 0) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color.White.copy(alpha = 0.8f),
                             modifier = Modifier.size(13.dp))
                        Text(
                            text = manga.chaptersCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (progress != null && progress > 0f) {
                // Тонкая полоска по низу обложки: сразу видно, сколько
                // прочитано, и не надо открывать карточку.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(accents.brand),
                    )
                }
            }
        }

        Column(
            modifier = if (onTitleClick != null) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onTitleClick)
            } else Modifier,
        ) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            )
            if (!compact) {
                val subtitle = caption
                    ?: manga.libraryStatus?.title
                    ?: manga.tags.firstOrNull()
                    ?: manga.authors.firstOrNull()
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            caption != null -> MaterialTheme.colorScheme.primary
                            manga.libraryStatus != null -> statusColor(manga.libraryStatus!!)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp,
                                                    bottom = 2.dp),
                    )
                }
            }
        }
    }
}

/** Карточка в горизонтальной строке: та же, но с фиксированной шириной. */
@Composable
fun MangaRowCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 128,
    showRating: Boolean = true,
    showChapters: Boolean = true,
    showStatusBadge: Boolean = true,
    progress: Float? = null,
    caption: String? = null,
    onTitleClick: (() -> Unit)? = null,
) = MangaCard(
    manga = manga,
    onClick = onClick,
    modifier = modifier.width(width.dp),
    showRating = showRating,
    showChapters = showChapters,
    showStatusBadge = showStatusBadge,
    progress = progress,
    caption = caption,
    onTitleClick = onTitleClick,
)

@Composable
fun statusColor(status: LibraryStatus): Color {
    val accents = LocalAppAccents.current
    return when (status) {
        LibraryStatus.READING -> accents.statusReading
        LibraryStatus.PLANNED -> accents.statusPlanned
        LibraryStatus.DROPPED -> accents.statusDropped
        LibraryStatus.COMPLETED -> accents.statusCompleted
    }
}

/**
 * Значок состояния на обложке: цвет и короткая подпись.
 *
 * Если пользователь ничего не отмечал, значка нет вовсе — поэтому он и
 * читается как «вот этот тайтл я читаю».
 */
@Composable
fun StatusBadge(status: LibraryStatus, modifier: Modifier = Modifier) {
    Text(
        text = status.title,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(statusColor(status).copy(alpha = 0.92f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Цветная точка состояния — там, где на подпись нет места. */
@Composable
fun StatusDot(status: LibraryStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(statusColor(status)),
    )
}

/** «9.5», без лишнего нуля у целых. */
fun formatRating(value: Float?): String {
    if (value == null) return "—"
    val rounded = Math.round(value * 10f) / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

/** Большие числа коротко: 198000 -> «198 тыс.». */
fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 100_000 / 10.0} млн"
    value >= 10_000 -> "${value / 1000} тыс."
    value >= 1_000 -> "${value / 100 / 10.0} тыс."
    else -> value.toString()
}
