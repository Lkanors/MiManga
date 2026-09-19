package com.mimanga.app.feature.profile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimanga.app.core.ui.components.EmptyState
import com.mimanga.app.core.ui.components.ErrorState
import com.mimanga.app.core.ui.components.LoadingState
import com.mimanga.app.core.ui.components.MangaRowSection
import com.mimanga.app.core.ui.components.formatRating
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.AppSettings
import com.mimanga.app.domain.model.Manga

/**
 * Профиль другого пользователя.
 *
 * Показывается ровно то, что человек разрешил показывать в своих настройках
 * приватности: закрытый профиль отдаёт только имя. Открывается по нажатию на
 * имя автора комментария.
 */
@Composable
fun ProfileScreen(
    username: String,
    settings: AppSettings,
    onBack: () -> Unit,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(key = "profile-$username"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accents = LocalAppAccents.current

    LaunchedEffect(username) { viewModel.load(username) }
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accents.screenGlow)
                .padding(end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(username, style = MaterialTheme.typography.headlineSmall)
        }

        val profile = state.profile
        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.load(username) })
            profile == null -> LoadingState()
            profile.isPrivate -> EmptyState(
                title = "Профиль закрыт",
                subtitle = "Пользователь не открывает свою страницу другим",
                icon = "🔒",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                profile.stats?.let { stats ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            ProfileStat("Глав прочитано", stats.readChapters.toString())
                            ProfileStat("Тайтлов", stats.readTitles.toString())
                            ProfileStat("Оценок", stats.rated.toString())
                        }
                    }
                }
                if (profile.favorites.isNotEmpty()) {
                    item {
                        MangaRowSection(
                            title = "Избранное",
                            items = profile.favorites,
                            onMangaClick = onMangaClick,
                            showRating = settings.showRatingOnCard,
                            showChapters = settings.showChaptersOnCard,
                        )
                    }
                }
                if (profile.ratings.isNotEmpty()) {
                    item {
                        MangaRowSection(
                            title = "Оценил",
                            items = profile.ratings,
                            onMangaClick = onMangaClick,
                            showRating = settings.showRatingOnCard,
                            showChapters = settings.showChaptersOnCard,
                            captionOf = { manga ->
                                manga.theirRating?.let { "Оценка ${formatRating(it)}" }
                            },
                        )
                    }
                }
                if (profile.library.isNotEmpty()) {
                    item {
                        MangaRowSection(
                            title = "Списки",
                            items = profile.library,
                            onMangaClick = onMangaClick,
                            showRating = settings.showRatingOnCard,
                            showChapters = settings.showChaptersOnCard,
                        )
                    }
                }
                if (profile.history.isNotEmpty()) {
                    item {
                        MangaRowSection(
                            title = "Читает",
                            items = profile.history,
                            onMangaClick = onMangaClick,
                            showRating = settings.showRatingOnCard,
                            showChapters = settings.showChaptersOnCard,
                        )
                    }
                }
                if (profile.comments.isNotEmpty()) {
                    item {
                        Text("Комментарии", style = MaterialTheme.typography.titleLarge,
                             modifier = Modifier.padding(start = 16.dp, end = 16.dp,
                                                         top = 18.dp, bottom = 6.dp))
                    }
                    items(profile.comments.size) { index ->
                        val comment = profile.comments[index]
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(comment.mangaTitle, style = MaterialTheme.typography.labelMedium,
                                 color = MaterialTheme.colorScheme.primary)
                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (profile.favorites.isEmpty() && profile.ratings.isEmpty() &&
                    profile.library.isEmpty() && profile.history.isEmpty() &&
                    profile.comments.isEmpty() && profile.stats == null
                ) {
                    item {
                        EmptyState(
                            title = "Здесь пусто",
                            subtitle = "Пользователь скрыл разделы профиля",
                            icon = "🙈",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
