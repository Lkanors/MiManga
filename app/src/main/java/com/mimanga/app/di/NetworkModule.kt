package com.mimanga.app.di

import android.content.Context
import com.mimanga.app.core.network.ServerTls
import com.mimanga.app.data.remote.AuthStore
import com.mimanga.app.data.remote.MangaServerApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Клиент сервера: HTTPS с закреплённым сертификатом.
     *
     * Ошибки статусов клиент не бросает сам (expectSuccess = false) — их
     * разбирает MangaServerApi, чтобы показать пользователю текст сервера
     * («Такое имя уже занято»), а не «HTTP 409».
     */
    @Provides
    @Singleton
    fun provideHttpClient(@ApplicationContext context: Context): HttpClient {
        val trustManager = ServerTls.serverTrustManager(context)
        val sslContext = ServerTls.sslContext(trustManager)
        return HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    explicitNulls = false
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 40_000
                socketTimeoutMillis = 40_000
            }
            engine {
                config {
                    followRedirects(true)
                    sslSocketFactory(sslContext.socketFactory, trustManager)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideMangaServerApi(httpClient: HttpClient, authStore: AuthStore): MangaServerApi =
        MangaServerApi(httpClient, authStore)
}
