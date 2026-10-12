# Juego online (Fase 6)

## Cómo funciona

- **El servidor manda.** Las reglas online viven en funciones SQL de Supabase
  (`supabase/migrations/20261012010000_salas_online.sql`). Eligen las preguntas, miden los tiempos,
  validan apuestas y respuestas, calculan los puntos, deciden el ganador y actualizan las estadísticas.
  Son las mismas reglas que `core/game` (las pruebas comprueban los mismos casos).
- **La app solo dibuja.** Cada segundo consulta `room_state`, que también sirve de latido de conexión.
  No hay un proceso del servidor corriendo: cada llamada aplica lo que ya debió pasar
  (por ejemplo, cerrar una pregunta cuyo tiempo venció).
- **Nadie lee las tablas.** Los clientes no tienen permisos de lectura ni escritura sobre salas,
  respuestas ni preguntas (RLS activado y privilegios revocados). Solo pueden llamar a:
  `create_room`, `join_room_by_code`, `find_public_room`, `current_room`, `room_state`,
  `start_room`, `leave_room`, `submit_bet` y `submit_answer`.

## Reglas online

| Fase | Duración | Qué pasa |
|---|---|---|
| Ruleta | 3,5 s | Se muestra la categoría (ya decidida por el servidor). |
| Apuesta (solo ronda 3) | 7 s | Cada jugador apuesta sin ver la pregunta. Sin apuesta: la mínima. |
| Pregunta | 8 s | Todos responden a la vez, cada uno en su teléfono. |
| Revelación | 6 s | Solución, votos, puntos de cada jugador. |
| Marcador | 6 s | Tras cada ronda. |

- **Tiempo de respuesta.** Lo mide el servidor desde que empieza la pregunta. Para compensar la red,
  la app informa cuánto tardó desde que *vio* la pregunta, y el servidor acepta ese dato solo si está
  como mucho 1,5 s por debajo de lo que él midió. Las respuestas se aceptan hasta 1,5 s después del
  final del reloj por el mismo motivo, pero nunca cuentan como más rápidas que 8 s.
- **Cierre anticipado.** Si todos los jugadores conectados ya respondieron, la pregunta se cierra
  sin esperar al reloj.
- **Votaciones** solo con 3 o más jugadores, como en local.
- **Desempate**: solo los empatados en el primer puesto; gana el acierto más rápido según el
  servidor. No suma aciertos a las estadísticas.

## Salas

- **Privadas**: código de 6 caracteres sin letras ni números confusos (sin I, O, 0, 1).
  Se comparte con el botón de compartir de Android. Empieza el anfitrión con 2 a 4 jugadores.
- **Públicas**: se emparejan jugadores que eligieron la misma modalidad (general o un departamento).
  La partida empieza sola 15 s después de juntarse 2 jugadores, o al llegar a 4.
- **Estados**: esperando, lista, en curso, terminada, cancelada.
- **Desconexión**: quien deja de consultar durante 10 s aparece como «reconectando»; sus preguntas
  cuentan como no respondidas y la partida sigue. Si vuelve, continúa donde iba
  (`current_room` también recupera la sala al reabrir la app).
- **Abandono**: quien sale de una partida en curso pierde y no puede volver a entrar; sus puntos
  quedan en el marcador pero no puede ganar. Si nadie se conecta durante 60 s, la partida se cancela
  y no cuenta para las estadísticas.
- **Anfitrión**: si sale o se desconecta en la sala de espera, el control pasa a otro jugador.
  Durante la partida el anfitrión no tiene poderes especiales.
- **Entrar tarde**: no se puede entrar a una partida en curso.

## Límites contra abuso

- Crear salas: 10 cada 10 minutos por usuario.
- Probar códigos: 20 cada 10 minutos (los intentos fallidos también cuentan).
- Buscar partida pública: 30 cada 10 minutos.
- Cada jugador está en una sola sala activa: al entrar en otra, sale de la anterior.

## Lo que esto NO protege

- **Las respuestas están dentro de la app.** El modo local funciona sin internet, así que el banco
  de preguntas con sus soluciones va en el APK. Alguien que descompile la app podría consultarlas
  durante una partida online. El servidor impide modificar puntos, tiempos, apuestas o resultados,
  pero no puede impedir que alguien busque la respuesta. Para cerrarlo haría falta un banco
  exclusivo para online que no viaje en la app.
- El tiempo se puede «adelantar» como mucho 1,5 s declarando una latencia falsa (≈9 puntos de bono
  de rapidez como máximo por pregunta).

## Preguntas en el servidor

`supabase/migrations/20261012010100_preguntas.sql` se genera desde el banco de la app con
`python3 supabase/tools/generar_preguntas_sql.py`. Las soluciones van a la tabla `question_keys`,
que solo leen las funciones del servidor y solo se muestran en la revelación.

En el proyecto de Supabase real (12/10/2026) este archivo se cargó en cinco partes
(`preguntas_1` … `preguntas_4` y `preguntas_5_claves` en el historial de migraciones) porque el
archivo completo era demasiado grande para enviarlo de una vez. Se comprobó que el contenido del
servidor coincide con el archivo: 380 preguntas (140 generales), 380 soluciones y la misma suma
MD5 de todas las filas que en una base local cargada con el archivo original. Al cambiar el banco,
vuelve a generar el archivo y aplícalo entero (por ejemplo con `supabase db push`).

## Pruebas

`supabase/tests/salas_test.py` crea una base de datos PostgreSQL desechable con una simulación de
`auth` (`supabase/tests/auth_simulado.sql`), aplica todas las migraciones y juega partidas completas
como lo haría la app: sala privada, permisos, partida de 3 jugadores con apuestas y votación,
empate y desempate, tiempos, desconexión, reconexión, abandono, emparejamiento público y límites.

```
PGTEST_DSN="host=/ruta/socket port=5432 user=postgres" python3 supabase/tests/salas_test.py
```
