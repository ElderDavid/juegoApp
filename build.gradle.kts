// Los plugins de Kotlin se declaran aquí para que :core y :app compartan la misma
// versión del compilador. AGP se declara en :app para que :core pueda compilarse
// sin acceso al repositorio de Google.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
