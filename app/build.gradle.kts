plugins {
    // AGP 9 incluye soporte de Kotlin integrado: no hace falta org.jetbrains.kotlin.android.
    // Sin versión: AGP ya está en el classpath raíz (ver build.gradle.kts).
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Configuración de Supabase y Google. Son valores públicos de cliente (no secretos), pero se leen
// de local.properties para no fijar un proyecto concreto en el repositorio. Si faltan, la app
// funciona igual en modo local y las cuentas aparecen como «no configuradas».
val localConfig = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun clientConfig(key: String): String =
    (localConfig.getProperty(key) ?: providers.gradleProperty(key).orNull ?: "").trim().replace("\"", "")

// AdMob. Los identificadores de prueba son los públicos de Google y nunca generan ingresos ni
// cuentan como clics reales. La versión debug SIEMPRE usa estos. La release solo muestra anuncios
// si se le pasan identificadores reales (admob.* en local.properties, nunca en el repositorio);
// sin ellos, la release no muestra ningún anuncio. Ver docs/ANUNCIOS.md.
object AdMobTestIds {
    const val APP = "ca-app-pub-3940256099942544~3347511713"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
}
val admobRelease = listOf("admob.appId", "admob.interstitialId", "admob.bannerId").map(::clientConfig)
val admobReleaseConfigured = admobRelease.all { it.isNotEmpty() }

android {
    namespace = "com.elderdavid.quientienelarazon"
    // Las librerías AndroidX actuales (Compose 1.12, core 1.19, lifecycle 2.11) exigen compilar con API 37.
    compileSdk = 37

    defaultConfig {
        // OJO: el applicationId no se puede cambiar después de publicar en Google Play.
        applicationId = "com.elderdavid.quientienelarazon"
        minSdk = 26
        // Google Play exige targetSdk 36 para apps nuevas desde el 31/08/2026.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${clientConfig("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${clientConfig("supabase.publishableKey")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${clientConfig("google.webClientId")}\"")
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = AdMobTestIds.APP
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${AdMobTestIds.INTERSTITIAL}\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${AdMobTestIds.BANNER}\"")
        }
        release {
            // El SDK exige un APPLICATION_ID en el manifiesto aunque no se muestren anuncios.
            manifestPlaceholders["admobAppId"] = if (admobReleaseConfigured) admobRelease[0] else AdMobTestIds.APP
            buildConfigField("boolean", "ADS_ENABLED", admobReleaseConfigured.toString())
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${admobRelease[1]}\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${admobRelease[2]}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
