# Fase 0 · Diagnóstico y plan

Fecha: 2026-10-11

## Estado inicial
- Repositorio `ElderDavid/juegoApp` vacío: no había archivos que conservar.
- Entorno de trabajo en la nube: JDK 21, Gradle 9.7.1 (wrapper), acceso a Maven Central.
- **Bloqueo:** la red de ese entorno rechaza `dl.google.com` (repositorio Maven de Google y Android SDK).
  Por eso allí solo se compila y prueba el módulo `:core`. La app Android se compila en el portátil
  del usuario (Android Studio) o en un entorno con `dl.google.com` permitido.

## Versiones elegidas
Verificadas contra los catálogos de `firebase/quickstart-android` y `android/nowinandroid` (oct. 2026).

| Componente | Versión |
|---|---|
| Android Gradle Plugin | 9.4.0 (Kotlin integrado, sin plugin `kotlin-android`) |
| Gradle | 9.7.1 |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Coroutines | 1.11.0 |
| Lifecycle / Navigation / DataStore | 2.11.0 / 2.10.1 / 1.2.0 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |

Google Play exige `targetSdk` 36 para apps nuevas y actualizaciones desde el 31/08/2026.

## Decisiones de arquitectura
1. **Backend: Supabase** (decidido por el usuario el 2026-10-11) en lugar de Firebase.
   - Auth anónima + Google con vinculación de identidad, Postgres, Realtime para salas.
   - La autoridad de la partida online vive en funciones SQL (`security definer`) del servidor:
     validan turnos, tiempos (reloj del servidor), apuestas y puntuación. Las respuestas correctas
     están en tablas sin permiso de lectura para los clientes hasta la revelación.
   - Plan gratuito: el proyecto se pausa tras ~7 días sin actividad. Para producción, plan de pago.
2. **Dos módulos Gradle, no más:**
   - `:core` — Kotlin puro: reglas, rondas, puntuación, ruleta, banco de preguntas y validaciones.
     Es el único motor de reglas para modo local y online, y se prueba sin Android SDK.
   - `:app` — Android/Compose, organizado por paquetes (`ui`, `settings`, `data`, `auth`, `online`,
     `profile`, `ads`).
3. **Sin Hilt:** contenedor de dependencias manual (`AppContainer`).
4. **Room descartado por ahora:** el banco de preguntas es un recurso estático de `:core` y las
   preferencias van en DataStore. Se reevaluará si el historial local lo justifica.
5. **AdMob** solo con IDs de prueba; **Play Billing** solo como interfaz, sin dependencia ni compras.

## Plan por fases
| Fase | Contenido | Dónde se verifica |
|---|---|---|
| 1 | Estructura, tema arcade, navegación, reglas, ajustes | `:core` aquí; app en el portátil |
| 2 | Motor: rondas, puntuación, rapidez, apuestas, final, desempate, votación, ruleta | JUnit en `:core` |
| 3 | Banco de 300 preguntas + validador automático; modo «De aquí somos» | JUnit en `:core` |
| 4 | Partida local completa 2–4 jugadores, revancha, estadísticas locales | portátil / emulador |
| 5 | Supabase Auth: invitado, vinculación Google, conflictos, perfiles | requiere proyecto Supabase |
| 6 | Salas privadas/públicas, Realtime, validación en servidor | requiere proyecto Supabase |
| 7 | Persistencia y estadísticas (fuente de verdad: servidor) | — |
| 8 | AdMob de prueba, interfaz de compras futuras | — |
| 9 | Calidad: pruebas, lint, accesibilidad, rendimiento | — |
| 10 | AAB firmado y guía de publicación | — |

## Lo que falta del usuario
- Compilar la app en su portátil (o habilitar `dl.google.com` en el entorno en la nube).
- Antes de la Fase 5: proyecto de Supabase y cliente OAuth de Google (Web + Android con SHA-1).
- Antes de la Fase 8: cuenta de AdMob (mientras tanto se usan IDs de prueba oficiales).
