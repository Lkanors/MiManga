package com.mimanga.app.feature.catalog.state

import com.mimanga.app.domain.model.CatalogFilters
import com.mimanga.app.domain.model.CatalogSort
import com.mimanga.app.domain.model.FilterOptions
import com.mimanga.app.domain.model.Manga

/**
 * Состояние каталога.
 *
 * Каталог — это всегда список: поиск, жанры и теги, сортировка. Строки
 * «популярное», «продолжить чтение» и рекомендации переехали на вкладку
 * «Главное», поэтому здесь их больше нет.
 */
data class CatalogState(
    val query: String = "",
    val results: List<Manga> = emptyList(),
    val sort: CatalogSort = CatalogSort.POPULAR,
    val filters: CatalogFilters = CatalogFilters(),
    val options: FilterOptions? = null,
    val includeForeign: Boolean = false,
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /**
     * Счётчик новых выдач: растёт на каждый запрос с первой страницы (новый
     * поисковый запрос, другой фильтр, другая сортировка). Экран по нему
     * перематывает сетку наверх — иначе после смены запроса человек остаётся
     * на середине списка, которого он не видел.
     */
    val resultsRevision: Int = 0,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}
