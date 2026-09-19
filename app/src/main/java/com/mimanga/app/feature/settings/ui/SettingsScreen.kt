package com.mimanga.app.feature.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mimanga.app.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimanga.app.core.ui.components.FlowChips
import com.mimanga.app.core.ui.components.SegmentedChoice
import com.mimanga.app.core.ui.components.TagChip
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.core.ui.theme.ThemeMode
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.model.CatalogSort
import com.mimanga.app.domain.model.ReaderBackground
import com.mimanga.app.domain.model.ReaderDirection

/**
 * Настройки: как приложение выглядит и ведёт себя.
 *
 * Состояния сервера здесь нет — оно переехало на страницу аккаунта: настройки
 * про внешний вид и поведение, а доступность сервера относится к данным.
 * Распознавания текста (OCR) здесь тоже нет: перевод страниц включается прямо
 * во время чтения, в меню читалки, — там же, где он и нужен.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accents = LocalAppAccents.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Text(
                "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accents.screenGlow)
                    .padding(20.dp),
            )
        }

        // ---------------------------------------------------------- внешний вид
        item { SectionTitle("Внешний вид") }
        item {
            ChoiceRow(
                title = "Тема",
                options = ThemeMode.entries.map { it.name },
                labels = mapOf("SYSTEM" to "Как в системе", "LIGHT" to "Светлая", "DARK" to "Тёмная"),
                selected = settings.themeMode,
                onSelect = { value -> viewModel.update { it.copy(themeMode = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Колонок в сетке",
                options = listOf("2", "3", "4"),
                selected = settings.gridColumns.toString(),
                onSelect = { value -> viewModel.update { it.copy(gridColumns = value.toInt()) } },
            )
        }
        item {
            SwitchRow("Компактные карточки", "Без подписи под названием",
                      settings.compactCards) { value ->
                viewModel.update { it.copy(compactCards = value) }
            }
        }
        item {
            SwitchRow("Оценка на карточке", "Оценка и сколько человек её поставило",
                      settings.showRatingOnCard) { value ->
                viewModel.update { it.copy(showRatingOnCard = value) }
            }
        }
        item {
            SwitchRow("Число глав на карточке", "Максимум по всем источникам",
                      settings.showChaptersOnCard) { value ->
                viewModel.update { it.copy(showChaptersOnCard = value) }
            }
        }
        item {
            SwitchRow("Значок состояния", "Цветная точка «читаю», «в планах» и т. д.",
                      settings.showStatusBadge) { value ->
                viewModel.update { it.copy(showStatusBadge = value) }
            }
        }

        // -------------------------------------------------------------- каталог
        item { SectionTitle("Каталог") }
        item {
            SwitchRow("Зарубежные источники", "Показывать тайтлы без русских сайтов",
                      settings.includeForeign) { value ->
                viewModel.update { it.copy(includeForeign = value) }
            }
        }
        item {
            ChoiceRow(
                title = "Сортировка по умолчанию",
                options = CatalogSort.entries.map { it.key },
                labels = CatalogSort.entries.associate { it.key to it.title },
                selected = settings.defaultSort,
                onSelect = { value -> viewModel.update { it.copy(defaultSort = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Тайтлов в строке главной",
                options = listOf("10", "20", "30"),
                selected = settings.homeRowSize.toString(),
                onSelect = { value -> viewModel.update { it.copy(homeRowSize = value.toInt()) } },
            )
        }

        // -------------------------------------------------------------- читалка
        item { SectionTitle("Читалка") }
        item {
            ChoiceRow(
                title = "Направление чтения",
                options = ReaderDirection.entries.map { it.key },
                labels = ReaderDirection.entries.associate { it.key to it.title },
                selected = settings.readerDirection,
                onSelect = { value -> viewModel.update { it.copy(readerDirection = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Фон",
                options = ReaderBackground.entries.map { it.key },
                labels = ReaderBackground.entries.associate { it.key to it.title },
                selected = settings.readerBackground,
                onSelect = { value -> viewModel.update { it.copy(readerBackground = value) } },
            )
        }
        item {
            SwitchRow("Не гасить экран", "Пока открыта глава", settings.keepScreenOn) { value ->
                viewModel.update { it.copy(keepScreenOn = value) }
            }
        }
        item {
            SwitchRow("Во весь экран", "Прятать системные панели",
                      settings.fullscreenReader) { value ->
                viewModel.update { it.copy(fullscreenReader = value) }
            }
        }
        item {
            SwitchRow("Полоса загрузки страниц", "Видно, сколько страниц уже скачалось",
                      settings.showPageProgress) { value ->
                viewModel.update { it.copy(showPageProgress = value) }
            }
        }
        item {
            ChoiceRow(
                title = "Предзагрузка страниц",
                options = listOf("0", "1", "3", "5", "10"),
                selected = settings.prefetchPages.toString(),
                onSelect = { value -> viewModel.update { it.copy(prefetchPages = value.toInt()) } },
            )
        }
        item {
            SwitchRow("Отмечать главы прочитанными", "Автоматически, при открытии главы",
                      settings.markChapterReadAutomatically) { value ->
                viewModel.update { it.copy(markChapterReadAutomatically = value) }
            }
        }

        // --------------------------------------------------------- о приложении
        item { SectionTitle("О приложении") }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape),
                )
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Версия ${com.mimanga.app.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        // Правообладателям нужен способ связаться, и он должен быть виден без
        // поисков — поэтому прямо в настройках, рядом с версией.
        item {
            Column(
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp),
            ) {
                Text("Правообладателям", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Если у вас есть претензии по поводу публикуемого материала — " +
                        "напишите на почту:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.contact_email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Строка с выбором одного значения.
 *
 * Короткий набор («2 / 3 / 4», «Как в системе / Светлая / Тёмная») показывается
 * переключателем с одинаковыми по ширине сегментами, длинный — чипами, которые
 * переносятся на следующую строку. Раньше варианты жёстко делились по три в
 * ряд: короткие выглядели обрубками, а длинные уезжали за край экрана.
 */
@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labels: Map<String, String> = emptyMap(),
) {
    val pairs = options.map { option -> option to (labels[option] ?: option) }
    val segmented = pairs.size <= 4 && pairs.all { (_, label) -> label.length <= 14 }
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Box(modifier = Modifier.padding(top = 10.dp)) {
            if (segmented) {
                SegmentedChoice(options = pairs, selected = selected, onSelect = onSelect)
            } else {
                FlowChips {
                    pairs.forEach { (option, label) ->
                        TagChip(
                            text = label,
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                    }
                }
            }
        }
    }
}
