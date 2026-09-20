import com.android.build.api.variant.AndroidComponentsExtension
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

/**
 * Настройки сборки, которых нет в репозитории: адрес сервера и его сертификат.
 *
 * Берутся, в порядке убывания приоритета, из -P в командной строке,
 * local.properties (файл не в репозитории) и переменной окружения. Если не
 * задано ничего, сборка получается рабочей, но ни к какому серверу не
 * подключённой — с адресом-заглушкой и учебным самоподписанным сертификатом
 * из certs/sample_server.pem. Так репозиторий можно открыть и собрать, не
 * раздавая всем желающим адрес чужого сервера.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun serverSetting(property: String, environment: String, fallback: String): String =
    (project.findProperty(property) as String?)
        ?: localProperties.getProperty(property)
        ?: System.getenv(environment)
        ?: fallback

//: Адрес сервера. 10.0.2.2 — это «мой компьютер» с точки зрения эмулятора.
val serverUrl = serverSetting("mimanga.serverUrl", "MIMANGA_SERVER_URL",
                              "https://10.0.2.2:8443")

//: Сертификат сервера (PEM). Приложение доверяет ТОЛЬКО ему, поэтому файл —
//: часть настройки, а не ресурс в репозитории.
val serverCertificate = serverSetting("mimanga.serverCert", "MIMANGA_SERVER_CERT",
                                      "certs/sample_server.pem")

//: Адреса, на которые распространяется закрепление сертификата. По умолчанию
//: — хост из адреса сервера; через запятую можно добавить второй (например,
//: адрес в локальной сети).
val serverHosts = serverSetting("mimanga.serverHosts", "MIMANGA_SERVER_HOSTS",
                                URI(serverUrl).host ?: "10.0.2.2")

/**
 * Собирает ресурсы, зависящие от настроек: сертификат в res/raw и правило
 * сети в res/xml. Оба файла раньше лежали в репозитории и оба содержали
 * адрес сервера.
 */
abstract class ServerResourcesTask : DefaultTask() {
    @get:InputFile
    abstract val certificate: RegularFileProperty

    @get:Input
    abstract val hosts: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = outputDirectory.get().asFile
        root.resolve("raw").mkdirs()
        root.resolve("xml").mkdirs()
        certificate.get().asFile.copyTo(root.resolve("raw/server_cert.pem"), overwrite = true)
        val domains = hosts.get().joinToString("\n") {
            """        <domain includeSubdomains="false">$it</domain>"""
        }
        root.resolve("xml/network_security_config.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<!--
  Файл собирается сборкой (см. app/build.gradle.kts): адреса берутся из
  настройки mimanga.serverHosts, править его руками бесполезно.

  Свой сервер: только HTTPS и только вшитый сертификат (закрепление).
  Картинки глав приходят с чужих CDN — им нужны системные центры
  сертификации, а части старых зеркал — ещё и http, поэтому базовая
  настройка их допускает. На обмен с нашим сервером это не влияет: он
  описан отдельным доменным правилом ниже.
-->
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <domain-config cleartextTrafficPermitted="false">
$domains
        <trust-anchors>
            <certificates src="@raw/server_cert" />
        </trust-anchors>
    </domain-config>
</network-security-config>
""",
        )
    }
}

extensions.configure<AndroidComponentsExtension<*, *, *>>("androidComponents") {
    onVariants { variant ->
        val task = tasks.register(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}ServerResources",
            ServerResourcesTask::class.java,
        ) {
            certificate.set(file(serverCertificate))
            hosts.set(serverHosts.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            task, ServerResourcesTask::outputDirectory)
    }
}

android {
    namespace = "com.mimanga.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mimanga.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Адрес сервера — настройка сборки, а не часть кода: у сервера нет
        // доменного имени, и в открытом репозитории ни адресу, ни его
        // сертификату не место. Откуда он берётся, см. serverSetting ниже.
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    buildTypes {
        // Ставить на телефон нужно ИМЕННО release: отладочная сборка Compose
        // работает заметно медленнее. В ней компилятор Compose добавляет в
        // каждый композабл отладочные метки (includeSourceInformation), а
        // сам Android с флагом debuggable отключает часть оптимизаций ART.
        // На списках с обложками это и даёт то самое подёргивание при
        // листании. Ключ подписи — отладочный, потому что приложение
        // ставится вручную, а не из магазина; и то и другое подписано одним
        // ключом, поэтому release встаёт поверх debug, не теряя данные.
        release {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            // R8 пока выключен: у Ktor, Hilt, ML Kit и kotlinx.serialization
            // свои правила сокращения, и проверить их можно только на
            // устройстве. Скорость отрисовки от R8 не зависит — он уменьшает
            // размер APK, — поэтому включать его наугад незачем.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // lintVital перед релизной сборкой разбирает приложение целиком
        // вместе с ML Kit и на этой машине укладывает Gradle по памяти
        // («daemon disappeared»). Приложение ставится вручную, а не
        // публикуется в магазине, поэтому блокировать этой проверке нечего;
        // сам lint по-прежнему запускается вручную: ./gradlew lintDebug.
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // OkHttp
    implementation(libs.okhttp)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Logging
    implementation(libs.timber)

    // ML Kit
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.translate)

    // Тесты на чистую логику — без Android и без сети.
    testImplementation(libs.junit)
}
