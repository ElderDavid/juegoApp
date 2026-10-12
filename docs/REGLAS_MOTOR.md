# Reglas implementadas en el motor (`:core`)

El motor (`GameEngine`) es puro y determinista: recibe el estado y una acción y devuelve el nuevo
estado o un rechazo. Lo usan el modo local y, más adelante, la validación del modo online.

## Partida
- 2 a 4 jugadores, 12 preguntas en 4 rondas de 3, 8 segundos por pregunta.
- Las 12 preguntas y 5 de desempate se **planifican al inicio con una semilla** (`GamePlanner`).
  Con la misma semilla todos los teléfonos obtienen el mismo plan.
- La **ruleta** (`CategoryRoulette`) elige la categoría antes de animar; la animación solo la muestra.
  Evita repetir las dos últimas categorías cuando hay alternativas y nunca elige una categoría sin preguntas válidas.
- No se repiten preguntas en una partida. En la revancha se evitan las de la partida anterior si quedan suficientes.
- Si el banco no alcanza para la partida, falla **antes de empezar** (`PlanningError.NotEnoughQuestions`).
- En modo local los jugadores responden **por turnos** y el primer turno rota en cada pregunta.
  En online las respuestas son simultáneas.

## Puntuación
| Ronda | Acierto | Error / sin respuesta |
|---|---|---|
| 1 Conocimiento | +100 | 0 |
| 2 Velocidad | +100 + bonificación | 0 |
| 3 Estrategia | + apuesta | − apuesta |
| 4 Final | +150 + bonificación | 0 |

**Bonificación por rapidez:** `50 × (tiempo_límite − tiempo_usado) / tiempo_límite`, con división entera
y recortada a [0, 50]. Responder al instante da 50 y al vencer el tiempo da 0.

**Apuestas (ronda 3):**
- Se apuesta 50 o 100, solo si el saldo lo permite.
- **Regla para quien no puede apostar** (saldo < 50): juega una *apuesta libre*: si acierta gana 50 y si
  falla no pierde nada. Así nadie queda en negativo y siempre tiene algo por qué jugar.
- Si se agota el tiempo sin apostar, se aplica la apuesta mínima posible y cuenta como error.
- Responder sin haber apostado se rechaza.

## Votaciones (preguntas de opinión y de debate por votación)
- Cada voto es privado hasta la revelación.
- Ganan quienes eligen una de las opciones más votadas; si hay empate entre opciones, todas ganan.
- Con menos de 2 votos válidos **no se puntúa** (ni premio ni pérdida de apuesta).
- Solo se usan con **3 o más jugadores** (con 2 siempre ganarían ambos), como máximo 1 por partida y
  solo en las rondas 1 y 3. En la ronda 3, la mayoría gana su apuesta y la minoría la pierde.
- Las preguntas de debate también pueden tener una **regla de juego** anunciada con la pregunta
  (p. ej. qué explicación cuenta como correcta para el juego); nunca se presentan como hecho científico.

## Desempate
- Solo decide el **primer puesto**. Juegan los empatados, sin apuestas.
- Gana la respuesta correcta más rápida. Si nadie acierta o el tiempo coincide exactamente, se repite con otra pregunta.
- Si se agotan las 5 preguntas reservadas, la victoria es compartida (no se inventa un ganador).
- No cambia los puntos ni el historial de las 12 preguntas, que son la fuente de las estadísticas.

## Protecciones
Rechazos con el estado intacto: fase incorrecta, jugador desconocido, fuera de turno, respuesta o
apuesta repetida, apuesta inválida o fuera de la ronda 3, opción inexistente, tiempo negativo o vencido,
y jugadores ajenos al desempate. Cada pregunta se puntúa una sola vez, al recibir la última respuesta.

## Modo local (un solo teléfono)

`core/local/LocalMatch` envuelve el motor para la partida por turnos en un solo teléfono:

- Antes de cada turno aparece «Pasa el teléfono a…». La pregunta no se muestra hasta que ese jugador pulsa «estoy listo», y las respuestas de los demás no se ven hasta la revelación.
- El tiempo de 8 s de cada jugador empieza cuando ve la pregunta. En la ronda de estrategia apuesta antes de verla (sin límite de tiempo, porque aún no conoce la pregunta) y el tiempo empieza al cerrar la apuesta.
- Una respuesta que llega después del límite se registra como tiempo agotado. El fin del tiempo solo se acepta si el reloj monótono confirma que pasó.
- Las acciones fuera de su momento (doble toque, responder durante el traspaso) no cambian nada.
- La revancha mantiene jugadores y modo y evita las preguntas de la partida anterior mientras queden suficientes.
