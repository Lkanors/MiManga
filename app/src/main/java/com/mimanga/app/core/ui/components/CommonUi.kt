package com.mimanga.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.Manga

/** Заголовок раздела с необязательной кнопкой справа. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Горизонтальная строка тайтлов — из таких собрана главная каталога. */
@Composable
fun MangaRowSection(
    title: String,
    items: List<Manga>,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    showRating: Boolean = true,
    showChapters: Boolean = true,
    showStatusBadge: Boolean = true,
    onMangaLongClick: ((Manga) -> Unit)? = null,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
    /** Доля прочитанного по тайтлу — для строки «продолжить чтение». */
    progressOf: ((Manga) -> Float?)? = null,
    /** Подпись под названием — например, «Гл. 12 · 40%». */
    captionOf: ((Manga) -> String?)? = null,
    /**
     * Своё действие для названия под обложкой. В строке «продолжить чтение»
     * обложка открывает страницу тайтла, а название — саму главу.
     */
    onTitleClick: ((Manga) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.padding(bottom = 10.dp)) {
        SectionHeader(title, action = actionTitle, onAction = onAction)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { manga ->
                Box {
                    MangaRowCard(
                        manga = manga,
                        onClick = { onMangaClick(manga) },
                        showRating = showRating,
                        showChapters = showChapters,
                        showStatusBadge = showStatusBadge,
                        progress = progressOf?.invoke(manga),
                        caption = captionOf?.invoke(manga),
                        onTitleClick = onTitleClick?.let { click -> { click(manga) } },
                    )
                    if (onMangaLongClick != null) {
                        // Крестик вместо долгого нажатия: удаление из истории
                        // должно быть очевидным действием, а не секретным жестом.
                        Text(
                            "✕",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x99000000))
                                .clickable { onMangaLongClick(manga) }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Полоса загрузки под шапкой: показывает, СКОЛЬКО загружено, если это известно,
 * и просто «идёт загрузка», если нет.
 */
@Composable
fun TopLoadingBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        val accents = LocalAppAccents.current
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        } else {
            val animated by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(180), label = "progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated)
                        .height(3.dp)
                        .background(accents.brand),
                )
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String = "📚",
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(icon, style = MaterialTheme.typography.headlineLarge)
            Text(title, style = MaterialTheme.typography.titleMedium,
                 textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     textAlign = TextAlign.Center)
            }
            if (action != null && onAction != null) {
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text("Не получилось", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 textAlign = TextAlign.Center)
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Text("  Повторить")
                }
            }
        }
    }
}

/** Тег/жанр. Выбранный подсвечивается фирменным цветом. */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    excluded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        excluded -> colors.error.copy(alpha = 0.16f)
        selected -> colors.primary.copy(alpha = 0.18f)
        else -> colors.surfaceContainerHigh
    }
    val border = when {
        excluded -> colors.error.copy(alpha = 0.5f)
        selected -> colors.primary.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    Text(
        text = if (excluded) "− $text" else text,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            excluded -> colors.error
            selected -> colors.primary
            else -> colors.onSurfaceVariant
        },
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * «Перетекающая» раскладка: элементы идут в строку, а что не влезло —
 * переносится на следующую.
 *
 * Нужна там, где ширина элементов заранее неизвестна: чипы жанров, варианты
 * настройки. Раньше они разбивались по N штук в строке, и длинные подписи
 * («По количеству просмотров») уезжали за край экрана.
 */
@Composable
fun FlowChips(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val gapX = horizontalGap.roundToPx()
        val gapY = verticalGap.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        val positions = ArrayList<Pair<Int, Int>>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > maxWidth) {
                x = 0
                y += rowHeight + gapY
                rowHeight = 0
            }
            positions += x to y
            x += placeable.width + gapX
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        val height = y + rowHeight
        layout(maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val (px, py) = positions[index]
                placeable.placeRelative(px, py)
            }
        }
    }
}

/**
 * Переключатель из нескольких равных по ширине кнопок — как сегменты в
 * системных настройках. Для коротких наборов («2 / 3 / 4», «Светлая /
 * Тёмная») это читается лучше, чем чипы разной ширины.
 */
@Composable
fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
