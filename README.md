# ¿Quién tiene la razón?

Juego Android de cultura general competitivo para 2 a 4 jugadores, con modo local sin conexión,
partidas online, el modo regional «De aquí somos» y un modo niños con preguntas más fáciles y más
tiempo (ver `docs/MODO_NINOS.md`).

## Estructura
- `core/` — motor de reglas en Kotlin puro (compartido por los modos local y online). Se prueba sin Android SDK.
- `app/` — aplicación Android con Jetpack Compose y Material 3.
- `web/` — versión para navegador del juego online (iPhone y otros), en las mismas salas que la app. Ver `docs/WEB.md`.
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

## Anuncios
La versión debug usa solo anuncios de prueba de Google. Ver `docs/ANUNCIOS.md`.

## Estado
Ver `docs/FASE0_DIAGNOSTICO.md` para el plan por fases y lo que falta.
