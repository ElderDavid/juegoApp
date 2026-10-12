// AGP debe cargarse en el mismo classloader que el plugin de Kotlin; si solo se declara en :app,
// Kotlin no ve sus clases (NoClassDefFoundError: BaseVariant). Por eso va en el classpath raíz,
// pero solo cuando :app está incluido: así :core sigue compilando sin el repositorio de Google.
buildscript {
    if (findProject(":app") != null) {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        dependencies {
            classpath(libs.android.gradle.plugin)
        }
    }
}

// Los plugins de Kotlin se declaran aquí para que :core y :app compartan la misma
// versión del compilador.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
