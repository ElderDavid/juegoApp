# Banco de preguntas

Ubicación: `core/src/main/resources/questions/`. Lo carga `QuestionBankLoader.fromResources()` y lo valida `QuestionValidator.validateBank()`. `QuestionBankContentTest` comprueba el banco real en cada `./gradlew :core:test`.

## Distribución (380)

| Archivo | Preguntas | Notas |
|---|---|---|
| `general.json` | 140 | ciencia 16, historia 14, geografía 15, deportes 14, tecnología 13, cine 13, música 13, cultura 15, curiosidades 13, debate 14 (5 debates con regla de juego, 2 debates por votación, 7 opiniones por votación) |
| `la-guajira.json` | 40 | Modo «De aquí somos» |
| `magdalena.json` | 40 | Modo «De aquí somos» |
| `cesar.json` | 40 | Modo «De aquí somos» |
| `atlantico.json` | 40 | Modo «De aquí somos» |
| `antioquia.json` | 40 | Modo «De aquí somos» |
| `bogota.json` | 40 | Modo «De aquí somos». Bogotá es Distrito Capital, no departamento |

Además, `ninos.json` tiene 111 preguntas con `"audience": "KIDS"` que solo salen en el modo niños
(ver `MODO_NINOS.md`). No cuentan en las 380 ni se mezclan con el banco general.

## Formato

Cada pregunta tiene `id`, `text`, `options`, `evaluation` (`correct` con índice, `rule` con índice y regla explícita, o `vote`), `category`, `kind` (OBJECTIVE, DEBATE, OPINION), `format` (MULTIPLE_CHOICE, TRUE_FALSE), `difficulty`, `region` (solo regionales), `explanation` y `source` (obligatoria en regionales).

La especificación pedía 300 preguntas. Antioquia y Bogotá se añadieron después a petición del usuario.

## Fuentes

Toda pregunta regional cita la página de Wikipedia consultada al redactarla (campo `source`). Donde las fuentes daban cifras inconsistentes (altitudes, número de municipios) se evitó preguntar el dato exacto. Antes de publicar conviene una revisión humana del contenido regional.

## Añadir un departamento

1. Crear `<id>.json` con preguntas que usen `region: "<id>"` y categorías `reg_*`.
2. Registrar la región en `regions` y el archivo en `files` de `index.json`.
3. Ejecutar `./gradlew :core:test`. El motor no necesita cambios.
