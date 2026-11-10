# Banco de preguntas

Ubicación: `core/src/main/resources/questions/`. Lo carga `QuestionBankLoader.fromResources()` y lo valida `QuestionValidator.validateBank()`. `QuestionBankContentTest` comprueba el banco real en cada `./gradlew :core:test`.

## Distribución (560)

| Archivo | Preguntas | Notas |
|---|---|---|
| `general.json` | 320 | Modo normal. ciencia 34, geografía 33, cultura 33, historia 32, deportes 32, debate 32, tecnología 31, cine 31, música 31, curiosidades 31. Debate: 13 con regla de juego, 4 debates por votación y 15 opiniones por votación. Dificultad: 116 fáciles, 107 medias y 97 difíciles |
| `la-guajira.json` | 40 | Modo «De aquí somos» |
| `magdalena.json` | 40 | Modo «De aquí somos» |
| `cesar.json` | 40 | Modo «De aquí somos» |
| `atlantico.json` | 40 | Modo «De aquí somos» |
| `antioquia.json` | 40 | Modo «De aquí somos» |
| `bogota.json` | 40 | Modo «De aquí somos». Bogotá es Distrito Capital, no departamento |

Además, `ninos.json` tiene 111 preguntas con `"audience": "KIDS"` que solo salen en el modo niños
(ver `MODO_NINOS.md`). No cuentan en las 560 ni se mezclan con el banco general.

### Modo países (180)

`pais-colombia.json`, `pais-mexico.json`, `pais-argentina.json`, `pais-brasil.json`,
`pais-estados-unidos.json` y `pais-espana.json`: 30 preguntas objetivas cada uno, todas con fuente
de Wikipedia. Se registran en `index.json` bajo `countries` y `countryFiles` (no en `files`), así
el generador de preguntas del servidor no las carga: el modo países solo existe en la partida en el
teléfono y se desbloquea con monedas (ver `ECONOMIA.md`). Usan las categorías generales más
`reg_gastronomia`, `reg_simbolos` y `reg_lugares`.

En el modo solitario (un jugador) el planificador usa solo preguntas de dificultad media y difícil
si hay suficientes para la partida y el desempate.

## Formato

Cada pregunta tiene `id`, `text`, `options`, `evaluation` (`correct` con índice, `rule` con índice y regla explícita, o `vote`), `category`, `kind` (OBJECTIVE, DEBATE, OPINION), `format` (MULTIPLE_CHOICE, TRUE_FALSE), `difficulty`, `region` (solo regionales), `explanation` y `source` (obligatoria en regionales).

La especificación pedía 300 preguntas. Antioquia y Bogotá se añadieron después a petición del usuario, y el 10/11/2026 el modo normal pasó de 140 a 320 preguntas (`gen-*` nuevas a partir del número siguiente de cada categoría).

## Sin repeticiones

- **Dentro de una partida** el planificador nunca repite una pregunta (ni entre las de juego ni en las de desempate).
- **Entre partidas** el teléfono recuerda las preguntas que ya salieron (`RecentQuestions`, guardado con DataStore en
  `recent_questions`, solo en el teléfono). Cada partida nueva o revancha evita las más recientes y deja siempre libres
  al menos el doble de las que necesita, para que la ruleta tenga de dónde elegir. Con las 320 del modo normal salen
  unas 15 partidas seguidas sin repetir ninguna pregunta; luego vuelven primero las vistas hace más tiempo. Si las que
  quedan no alcanzan para alguna ronda, el planificador usa todo el banco del modo.
- Las salas online eligen sus preguntas en el servidor y tampoco repiten dentro de una partida.

## Fuentes

Toda pregunta regional cita la página de Wikipedia consultada al redactarla (campo `source`). Donde las fuentes daban cifras inconsistentes (altitudes, número de municipios) se evitó preguntar el dato exacto. Antes de publicar conviene una revisión humana del contenido regional.

## Añadir un departamento

1. Crear `<id>.json` con preguntas que usen `region: "<id>"` y categorías `reg_*`.
2. Registrar la región en `regions` y el archivo en `files` de `index.json`.
3. Ejecutar `./gradlew :core:test`. El motor no necesita cambios.
