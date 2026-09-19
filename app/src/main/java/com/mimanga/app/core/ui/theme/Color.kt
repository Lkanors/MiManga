package com.mimanga.app.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра приложения.
 *
 * Тёмная тема — основная и сделана действительно тёмной: фон почти чёрный
 * (#06060A), поверхности отличаются от него на пару тонов. Так обложки манги
 * выглядят ярче всего, а ночью экран не слепит.
 *
 * Акцентов два — фиолетовый и бирюзовый. Градиент между ними используется
 * точечно: активная вкладка, главная кнопка, подложка под обложкой. Заливать
 * им крупные плоскости нельзя — интерфейс сразу становится «нарисованным».
 */

// Тёмная тема
val DarkBackground = Color(0xFF06060A)
val DarkSurface = Color(0xFF0E0E15)
val DarkSurfaceElevated = Color(0xFF15151F)
val DarkOutline = Color(0xFF23232F)
val DarkOnSurface = Color(0xFFECEAF5)
val DarkOnSurfaceVariant = Color(0xFF8E8CA6)

// Светлая тема
val LightBackground = Color(0xFFF6F6FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightOutline = Color(0xFFE3E3EC)
val LightOnSurface = Color(0xFF14131B)
val LightOnSurfaceVariant = Color(0xFF6A6880)

// Акценты
val AccentViolet = Color(0xFF7C5CFF)
val AccentVioletDeep = Color(0xFF5B3DF5)
val AccentTeal = Color(0xFF2AD4C1)
val AccentTealDeep = Color(0xFF0E9E8E)
//: Промежуточные тона — только для градиентов. Между фиолетовым и бирюзовым
//: лежит «мёртвая» серо-синяя зона, и переход из двух цветов выглядел мутным:
//: сиреневый оживляет свечение шапки, синий — градиент кнопок и активной
//: вкладки.
val AccentOrchid = Color(0xFFB06BE8)
val AccentIndigo = Color(0xFF4C6FFF)

// Состояния тайтла в библиотеке: у каждого свой цвет, чтобы значок читался
// без подписи прямо на карточке в поиске.
val StatusReading = Color(0xFF3B82F6)     // читаю — синий
val StatusPlanned = Color(0xFFA855F7)     // в планах — фиолетовый
val StatusDropped = Color(0xFFF97316)     // заброшено — оранжевый
val StatusCompleted = Color(0xFF22C55E)   // прочитано — зелёный
val FavoriteRed = Color(0xFFFF4D6D)

val RatingGold = Color(0xFFFFC53D)
val ErrorRed = Color(0xFFFF5A6E)
