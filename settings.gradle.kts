pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "QuienTieneLaRazon"

// Motor de reglas en Kotlin puro: compila y se prueba sin Android SDK.
include(":core")

// La app Android solo se incluye cuando hay un Android SDK disponible
// (local.properties con sdk.dir, o ANDROID_HOME / ANDROID_SDK_ROOT).
// Así el motor se puede probar en entornos de CI sin SDK.
val localProps = file("local.properties")
val hasSdk = (localProps.exists() && localProps.readText().contains("sdk.dir")) ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasSdk) {
    include(":app")
} else {
    logger.lifecycle("Android SDK no encontrado: se omite el módulo :app (solo :core).")
}
