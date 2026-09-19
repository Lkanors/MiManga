package com.mimanga.app.feature.catalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mimanga.app.core.ui.components.FlowChips
import com.mimanga.app.core.ui.components.TagChip
import com.mimanga.app.domain.model.CatalogFilters
import com.mimanga.app.domain.model.FilterOptions
import com.mimanga.app.domain.model.mangaStatusTitle

/**
 * Боковая панель отбора.
 *
 * Жанры и теги показаны РАЗДЕЛЬНО: жанр отвечает на вопрос «что это»
 * (романтика, сёнэн, фэнтези), тег — на вопрос «что внутри» («Система»,
 * «ГГ имба», «В цвете»). Сайты отдают их одним списком, разделяет их сервер.
 *
 * Выбор применяется СРАЗУ: список за панелью перестраивается, пока панель
 * открыта, и видно, сколько тайтлов осталось. Кнопка внизу только закрывает
 * панель. Сортировки здесь нет — она отдельным списком над сеткой.
 *
 * Нажатие по тегу переключает его по кругу: не выбран → включить → исключить →
 * не выбран. Так исключающий фильтр не требует отдельного экрана.
 */
@Composable
fun FilterPanel(
    options: FilterOptions?,
    filters: CatalogFilters,
    total: Int,
    onChange: (CatalogFilters) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }
    var genresQuery by remember { mutableStateOf("") }
    var tagsQuery by remember { mutableStateOf("") }

    /** Меняет набор фильтров и сразу применяет его к списку. */
    fun update(next: CatalogFilters) {
        draft = next
        onChange(next)
    }

    val genreNames = remember(options) { options?.genres.orEmpty().map { it.name } }
    val tagNames = remember(options) { options?.tags.orEmpty().map { it.name } }

    fun toggle(tag: String) {
        update(
            when {
                tag in draft.tags -> draft.copy(tags = draft.tags - tag,
                                                excludeTags = draft.excludeTags + tag)
                tag in draft.excludeTags -> draft.copy(excludeTags = draft.excludeTags - tag)
                else -> draft.copy(tags = draft.tags + tag)
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Фильтры", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { draft = CatalogFilters(); onClear() }) { Text("Сбросить") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val genres = options?.genres.orEmpty()
            val tags = options?.tags.orEmpty()

            if (genres.isNotEmpty()) {
                filterSection(
                    title = "Жанры",
                    expanded = genresExpanded,
                    onExpand = { genresExpanded = !genresExpanded },
                    names = genreNames,
                    limit = 14,
                    selected = draft.tags,
                    excluded = draft.excludeTags,
                    query = genresQuery,
                    onQuery = { genresQuery = it },
                    onToggle = ::toggle,
                )
            }
            if (tags.isNotEmpty()) {
                filterSection(
                    title = "Теги",
                    expanded = tagsExpanded,
                    onExpand = { tagsExpanded = !tagsExpanded },
                    names = tagNames,
                    limit = 20,
                    selected = draft.tags,
                    excluded = draft.excludeTags,
                    query = tagsQuery,
                    onQuery = { tagsQuery = it },
                    onToggle = ::toggle,
                )
            }
            // Тип издания — первым: он делит каталог крупнее всех остальных
            // отборов, и выбирают его чаще, чем год или число глав.
            val kinds = options?.kinds.orEmpty()
            if (kinds.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Тип", style = MaterialTheme.typography.titleMedium)
                        ChipFlow(
                            names = kinds.map { it.key },
                            label = { key -> kinds.first { it.key == key }.name },
                            selected = draft.kinds,
                            onToggle = { key ->
                                update(
                                    draft.copy(
                                        kinds = if (key in draft.kinds) draft.kinds - key
                                                else draft.kinds + key
                                    )
                                )
                            },
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Статус", style = MaterialTheme.typography.titleMedium)
                    ChipFlow(
                        names = options?.statuses.orEmpty(),
                        label = { mangaStatusTitle(it) },
                        selected = draft.statuses,
                        onToggle = { status ->
                            update(
                                draft.copy(
                                    statuses = if (status in draft.statuses) draft.statuses - status
                                               else draft.statuses + status
                                )
                            )
                        },
                    )
                }
            }
            item {
                RangeRow(
                    title = "Количество глав",
                    from = draft.minChapters,
                    to = draft.maxChapters,
                    onChange = { from, to -> update(draft.copy(minChapters = from, maxChapters = to)) },
                )
            }
            item {
                RangeRow(
                    title = "Год выхода",
                    from = draft.yearFrom,
                    to = draft.yearTo,
                    onChange = { from, to -> update(draft.copy(yearFrom = from, yearTo = to)) },
                )
            }
            item {
                RatingSlider(
                    value = draft.minRating,
                    // Пока тянут ползунок, запросы не шлём: применяем на
                    // отпускании, иначе каталог перезапрашивался бы на
                    // каждом делении.
                    onDraft = { draft = draft.copy(minRating = it) },
                    onApply = { onChange(draft) },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Совпадение тегов", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagChip("Все выбранные", selected = !draft.anyTag,
                                onClick = { update(draft.copy(anyTag = false)) })
                        TagChip("Любой из них", selected = draft.anyTag,
                                onClick = { update(draft.copy(anyTag = true)) })
                    }
                }
            }
        }

        // Кнопка не только закрывает панель, но и досылает отбор: по одному
        // только перестроению списка за панелью не всегда видно, применилось
        // ли что-то, а у ползунка оценки применение и вовсе отложенное.
        Button(
            onClick = { onChange(draft); onClose() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(if (total > 0) "Сохранить · показать $total" else "Сохранить")
        }
    }
}

/**
 * «Оценка не ниже» ползунком: шаг в полбалла по всей десятибалльной шкале.
 *
 * Раньше здесь были четыре кнопки (6–9) — ни «не ниже 4», ни «не ниже 9.5»
 * выбрать было нельзя, а крайнее левое положение честно означает
 * «любая оценка».
 */
@Composable
private fun RatingSlider(
    value: Float?,
    onDraft: (Float?) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Оценка не ниже", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (value == null || value <= 0f) "любая" else formatRating(value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value ?: 0f,
            onValueChange = { onDraft(if (it <= 0f) null else it) },
            onValueChangeFinished = onApply,
            valueRange = 0f..10f,
            steps = 19,          // деления через 0.5 балла
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("10", style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 8.0 -> «8», 8.5 -> «8.5»: лишний ноль в подписи только мешает. */
private fun formatRating(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format("%.1f", value)

/**
 * Раздел с чипами (жанры или теги).
 *
 * Тегов у сайтов сотни, поэтому свёрнутым показывается только начало списка —
 * он отсортирован по числу тайтлов, так что сверху самое ходовое. В
 * развёрнутом виде появляется строка поиска: пролистывать шестьсот чипов,
 * чтобы найти «Реинкарнация», никто не станет.
 *
 * Выбранное показывается всегда, даже если раздел свёрнут, — иначе, свернув
 * его, человек терял из виду то, по чему уже отбирает. Но показывается на
 * своём месте: порядок чипов от выбора не меняется (см. ниже).
 *
 * Раздел не композабл, а построитель элементов списка (LazyListScope): в
 * развёрнутом виде тегов под пятьсот, и одним элементом панель собирала и
 * РИСОВАЛА все чипы сразу — на каждый кадр прокрутки. Порциями по [CHUNK]
 * штук список держит в композиции только те, что видно.
 */
/**
 * Какие чипы показать в разделе — и, что важнее, В КАКОМ ПОРЯДКЕ.
 *
 * Порядок не зависит от выбора. Раньше выбранное поднималось в начало списка,
 * и чипы разъезжались прямо под пальцем: нажал жанр — он прыгнул в начало,
 * следующее нажатие в то же место попадало уже по соседнему. Со стороны это
 * выглядело так, будто снятый жанр сам включается обратно, и по кругу.
 * Заметнее всего было в поиске по жанрам, где список и без того короткий.
 *
 * Свёрнутым разделом показывается начало списка (он отсортирован по числу
 * тайтлов, сверху самое ходовое) плюс всё выбранное, что в это начало не
 * попало, — но на своих местах, а не первым: иначе, свернув раздел, человек
 * потерял бы из виду то, по чему уже отбирает.
 */
internal fun visibleFilterNames(
    names: List<String>,
    expanded: Boolean,
    limit: Int,
    selected: Set<String>,
    excluded: Set<String>,
    query: String,
): List<String> {
    fun chosen(name: String) = name in selected || name in excluded
    return when {
        !expanded -> names.filterIndexed { index, name -> index < limit || chosen(name) }
        query.isBlank() -> names
        else -> {
            val needle = query.trim()
            names.filter { it.contains(needle, ignoreCase = true) }
        }
    }
}

private fun LazyListScope.filterSection(
    title: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    names: List<String>,
    limit: Int,
    selected: Set<String>,
    excluded: Set<String>,
    query: String,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    val visible = visibleFilterNames(names, expanded, limit, selected, excluded, query)

    item(key = "$title:head") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$title · ${names.size}", style = MaterialTheme.typography.titleMedium)
                if (names.size > limit) {
                    TextButton(onClick = onExpand) { Text(if (expanded) "Свернуть" else "Ещё") }
                }
            }
            if (expanded && names.size > limit) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = { Text("Поиск по названию") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (visible.isEmpty()) {
                Text("Ничего не нашлось", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val chunks = visible.chunked(CHUNK)
    items(chunks.size, key = { index -> "$title:$index" }) { index ->
        ChipFlow(
            names = chunks[index],
            label = { it },
            selected = selected,
            excluded = excluded,
            onToggle = onToggle,
        )
    }
}

/** Сколько чипов уходит в один элемент списка. */
private const val CHUNK = 21

/**
 * Чипы жанров и тегов: что не влезло в строку, переносится на следующую.
 *
 * Раньше они делились по два в ряд, и длинные названия («Сверхъестественное»)
 * уезжали за край панели.
 */
@Composable
private fun ChipFlow(
    names: List<String>,
    label: (String) -> String,
    selected: Set<String>,
    excluded: Set<String> = emptySet(),
    onToggle: (String) -> Unit,
) {
    FlowChips {
        names.forEach { name ->
            TagChip(
                text = label(name),
                selected = name in selected,
                excluded = name in excluded,
                onClick = { onToggle(name) },
            )
        }
    }
}

@Composable
private fun RangeRow(
    title: String,
    from: Int?,
    to: Int?,
    onChange: (Int?, Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = from?.toString() ?: "",
                onValueChange = { onChange(it.filter(Char::isDigit).toIntOrNull(), to) },
                label = { Text("от") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
            OutlinedTextField(
                value = to?.toString() ?: "",
                onValueChange = { onChange(from, it.filter(Char::isDigit).toIntOrNull()) },
                label = { Text("до") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
        }
    }
}
