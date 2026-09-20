package com.mimanga.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Адрес обложки должен зависеть от того, кто смотрит.
 *
 * Зачем такой тест. Размывает обложку сервер, но картинки в приложении идут
 * через кэш, а он знает только адрес. Пока адрес один на всех, после входа в
 * аккаунт на экране остаются размытые картинки из кэша, а после выхода —
 * неразмытые. Проверяется, что метка в адресе есть, что она меняется вместе
 * с аккаунтом и что это именно метка, а не сам токен.
 */
class ServerImagesTest {

    @Test
    fun `адрес обложки меняется вместе с аккаунтом`() {
        ServerImages.viewer(null)
        val guest = ServerImages.cover("key-1", ServerImages.CARD_WIDTH)

        ServerImages.viewer("secret-token-1")
        val signedIn = ServerImages.cover("key-1", ServerImages.CARD_WIDTH)
        assertNotEquals("после входа адрес должен стать другим", guest, signedIn)
        assertFalse("токен в адрес попадать не должен", signedIn.contains("secret-token-1"))

        ServerImages.viewer("secret-token-2")
        assertNotEquals("у другого аккаунта и адрес другой",
                        signedIn, ServerImages.cover("key-1", ServerImages.CARD_WIDTH))

        ServerImages.viewer(null)
        assertEquals("после выхода адрес — снова гостевой",
                     guest, ServerImages.cover("key-1", ServerImages.CARD_WIDTH))
    }

    @Test
    fun `ширина остаётся в адресе`() {
        ServerImages.viewer(null)
        assertTrue(ServerImages.cover("key-1", 320).endsWith("w=320"))
        ServerImages.viewer("token")
        assertTrue(ServerImages.cover("key-1", 320).contains("w=320"))
    }
}
