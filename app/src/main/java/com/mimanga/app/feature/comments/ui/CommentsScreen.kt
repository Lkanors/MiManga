package com.mimanga.app.feature.comments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimanga.app.core.ui.components.EmptyState
import com.mimanga.app.core.ui.components.TopLoadingBar
import com.mimanga.app.core.ui.theme.LocalAppAccents
import com.mimanga.app.domain.model.Manga

/**
 * Комментарии тайтла — отдельным экраном.
 *
 * Раньше они были хвостом страницы тайтла, и поле ввода уезжало под
 * клавиатуру: человек не видел, что набирает. Здесь поле закреплено внизу и
 * поднимается вместе с клавиатурой (imePadding), а список остаётся на месте.
 */
@Composable
fun CommentsScreen(
    manga: Manga,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    viewModel: CommentsViewModel = hiltViewModel(key = "comments-${manga.actionKey}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val accents = LocalAppAccents.current

    LaunchedEffect(manga.actionKey) { viewModel.init(manga.actionKey) }

    // Подгрузка следующих страниц обсуждения по мере прокрутки.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadMore() }

    Column(modifier = Modifier
        .fillMaxSize()
        .imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(accents.screenGlow)
                .padding(end = 16.dp, top = 4.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.total > 0) "Комментарии · ${state.total}" else "Комментарии",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    manga.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TopLoadingBar(visible = state.isLoading)

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.error != null && state.comments.isEmpty() -> EmptyState(
                    title = "Не получилось",
                    subtitle = state.error,
                    action = "Обновить",
                    onAction = viewModel::refresh,
                    icon = "⚠️",
                )

                state.comments.isEmpty() && !state.isLoading -> EmptyState(
                    title = "Пока тихо",
                    subtitle = "Никто ещё ничего не написал — будьте первым",
                    icon = "💬",
                )

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentRow(
                            username = comment.username,
                            text = comment.text,
                            createdAt = comment.createdAt,
                            canDelete = state.isLoggedIn && comment.userId == state.userId,
                            onDelete = { viewModel.delete(comment.id) },
                            onOpenProfile = { onOpenProfile(comment.username) },
                        )
                    }
                }
            }
        }

        CommentInput(
            text = text,
            enabled = state.isLoggedIn,
            isSending = state.isSending,
            onTextChange = { text = it },
            onSend = { viewModel.send(text); text = "" },
        )
    }
}

/**
 * Поле ввода внизу экрана.
 *
 * Отступ под клавиатуру даёт весь экран (imePadding), а здесь остаётся отступ
 * под системные кнопки — иначе на телефонах с жестовой навигацией поле
 * прижимается к самому краю.
 */
@Composable
private fun CommentInput(
    text: String,
    enabled: Boolean,
    isSending: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (!enabled) {
                Text(
                    "Войдите в аккаунт, чтобы оставить комментарий",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                return@Column
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Что думаете о тайтле?") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 5,
                )
                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !isSending,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(strokeWidth = 2.dp,
                                                  modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    username: String,
    text: String,
    createdAt: String,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(LocalAppAccents.current.brand)
                    .clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center,
            ) {
                Text(username.take(1).uppercase(),
                     style = MaterialTheme.typography.labelLarge,
                     color = androidx.compose.ui.graphics.Color.White)
            }
            Text(
                "  $username",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )
            Text("  ${createdAt.take(10)}", style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (canDelete) {
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium,
             modifier = Modifier.padding(start = 30.dp, top = 4.dp))
        Spacer(Modifier.height(2.dp))
    }
}
