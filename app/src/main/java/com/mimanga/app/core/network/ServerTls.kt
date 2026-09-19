package com.mimanga.app.core.network

import android.content.Context
import com.mimanga.app.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Проверка сертификата сервера (закрепление сертификата).
 *
 * У сервера нет доменного имени, поэтому сертификат самоподписанный и выписан
 * на его IP. Приложению вшита копия ЭТОГО сертификата (res/raw/server_cert.pem),
 * и соединение с сервером принимается, только если он предъявил именно его.
 * Обычная проверка по списку публичных центров сертификации здесь была бы
 * слабее: она приняла бы любой сертификат, выписанный кем угодно.
 *
 * Картинки грузятся с чужих CDN, поэтому для них нужен и системный список тоже
 * — [imageTrustManager] проверяет сначала по системному, затем по нашему.
 */
object ServerTls {

    /** Доверяем ТОЛЬКО сертификату нашего сервера. */
    fun serverTrustManager(context: Context): X509TrustManager {
        val certificate = context.resources.openRawResource(R.raw.server_cert).use { stream ->
            java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(stream)
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("manga-server", certificate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** Системные центры сертификации — для картинок с чужих CDN. */
    fun systemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** Системные центры плюс наш сервер. */
    fun imageTrustManager(context: Context): X509TrustManager =
        CompositeTrustManager(listOf(systemTrustManager(), serverTrustManager(context)))

    fun sslContext(trustManager: X509TrustManager): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }

    private class CompositeTrustManager(
        private val managers: List<X509TrustManager>,
    ) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            check(chain, authType) { manager -> manager.checkClientTrusted(chain, authType) }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            check(chain, authType) { manager -> manager.checkServerTrusted(chain, authType) }
        }

        /** Цепочка принимается, если её принял хотя бы один проверяющий. */
        private inline fun check(
            chain: Array<out X509Certificate>?,
            authType: String?,
            block: (X509TrustManager) -> Unit,
        ) {
            var last: CertificateException? = null
            for (manager in managers) {
                try {
                    block(manager)
                    return
                } catch (error: CertificateException) {
                    last = error
                }
            }
            throw last ?: CertificateException("Сертификат не подтверждён: chain=${chain?.size}, $authType")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            managers.flatMap { it.acceptedIssuers.toList() }.toTypedArray()
    }
}
