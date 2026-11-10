import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProp(key: String, fallback: String = ""): String =
    localProperties.getProperty(key)?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback

android {
    namespace = "br.com.meufinanceiro"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.meufinanceiro"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Credenciais lidas de local.properties (nao versionado).
        // Em producao, gere o Connect Token em um backend e remova estes campos.
        buildConfigField("String", "PLUGGY_CLIENT_ID", "\"${localProp("pluggy.clientId")}\"")
        buildConfigField("String", "PLUGGY_CLIENT_SECRET", "\"${localProp("pluggy.clientSecret")}\"")
        buildConfigField("String", "PLUGGY_API_BASE_URL", "\"${localProp("pluggy.apiBaseUrl", "https://api.pluggy.ai")}\"")
        // Chave opcional da brapi.dev para cotacao de acoes. Sem ela o app usa
        // a brapi sem chave (limite apertado) e cai no Yahoo Finance.
        buildConfigField("String", "BRAPI_TOKEN", "\"${localProp("brapi.token")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    // org.json do Android é um stub nos testes: a implementação real permite
    // validar o salvamento das lançamentos e investimentos.
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}



