# ¿Quién tiene la razón?

Juego Android de cultura general competitivo para 2 a 4 jugadores, con modo local sin conexión,
partidas online y el modo regional «De aquí somos» (La Guajira, Magdalena, Cesar y Atlántico).

## Estructura
- `core/` — motor de reglas en Kotlin puro (compartido por los modos local y online). Se prueba sin Android SDK.
- `app/` — aplicación Android con Jetpack Compose y Material 3.
- `docs/` — diagnóstico, decisiones y guías.

## Compilar
Requisitos: Android Studio reciente (AGP 9.4, Gradle 9.7.1) con Android SDK 36 y JDK 17 o superior.

```bash
./gradlew :core:test            # pruebas del motor (no requiere Android SDK)
./gradlew assembleDebug         # APK de depuración (requiere Android SDK)
./gradlew testDebugUnitTest     # pruebas unitarias de la app
```

Si no hay Android SDK (sin `local.properties` ni `ANDROID_HOME`), Gradle omite el módulo `:app`
y solo compila `:core`.

## Estado
Ver `docs/FASE0_DIAGNOSTICO.md` para el plan por fases y lo que falta.
