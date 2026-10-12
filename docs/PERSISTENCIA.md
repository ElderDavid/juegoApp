# Persistencia y estadísticas (Fase 7)

Cada dato tiene una sola fuente de verdad. La app nunca suma datos de dos fuentes distintas.

| Dato | Dónde vive | Quién lo escribe | Se recupera en otro teléfono |
|---|---|---|---|
| Sonido, vibración, menos animaciones | DataStore `settings` (teléfono) | La pantalla de ajustes | No |
| Historial de partidas locales | DataStore `local_history` (teléfono) | `LocalGameViewModel` al terminar cada partida | No |
| Nombre y avatar | Supabase `profiles` | Solo la función `update_profile` | Sí, con la misma cuenta |
| Nivel, XP, victorias, aciertos (online) | Supabase `player_stats` | Solo el servidor, al terminar una sala online | Sí, con la misma cuenta |
| Clasificación | Calculada en Supabase (`leaderboard`) | Nadie: se calcula a partir de `player_stats` | Sí |

## Por qué las partidas locales no suman a la cuenta

En una partida local varias personas comparten un teléfono y ninguna tiene por qué ser el dueño de
la cuenta. Si esos resultados sumaran XP, cualquiera podría subir de nivel jugando contra sí mismo
en un solo teléfono, y la clasificación online dejaría de significar algo. Por eso:

- La pantalla **Estadísticas** muestra dos secciones separadas: «Online · tu cuenta» y
  «En este teléfono · partidas locales».
- El historial local guarda, por partida, la fecha, la modalidad, los nombres con sus puntos y el
  ganador. Se conservan las 10 últimas partidas, el número total y el récord de puntos.
- Se puede borrar desde la pantalla (con confirmación). Borrarlo no toca la cuenta.

## Sin conexión

- El historial local y los ajustes funcionan siempre.
- Si no hay conexión, la sección online dice que no se pudo cargar y ofrece reintentar. No se
  muestran números guardados de antes como si fueran actuales.
- Si solo falla la clasificación, se muestran igualmente las estadísticas propias.

## Recuperación de datos

- **Cuenta vinculada con Google:** al entrar con la misma cuenta en otro teléfono, perfil,
  estadísticas online y posición en la clasificación vuelven tal cual, porque viven en el servidor.
- **Invitado sin vincular:** si se borran los datos de la app o se cambia de teléfono, se pierde el
  acceso a ese invitado y a su progreso online.
- **Historial local y ajustes:** se pierden al desinstalar la app o borrar sus datos. La copia de
  seguridad automática de Android está desactivada (`allowBackup="false"`), así que no se restauran.

## Datos dañados

Si el archivo del historial local está dañado o es de un formato desconocido, se empieza de cero en
lugar de cerrar la app. Lo mismo con los ajustes (se usan los valores por defecto).

## Room

No se usa. El banco de preguntas va como recurso dentro de la app y el historial local es una lista
corta (10 partidas) que cabe en DataStore. Se reconsiderará si hace falta consultar un historial
largo.

## Pruebas

- `core`: `LocalHistoryTest` (registro, límite de 10, modalidad, partidas sin terminar, datos dañados).
- `app`: `LocalGameViewModelTest` (una sola anotación por partida, también tras la revancha; un fallo
  al guardar no rompe la partida) y `StatsViewModelTest` (separación online/local, sin servidor,
  sin conexión, fallo de la clasificación, borrado con confirmación).
- SQL: `supabase/tests/salas_test.py` incluye la clasificación (orden, posición propia, límite,
  solo datos públicos, sin sesión no se puede consultar).
