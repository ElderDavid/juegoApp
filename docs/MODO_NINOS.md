# Modo niños

Modo de la partida local pensado para jugar en familia con niños. Se elige en **Partida local →
Preguntas → Niños**.

## Qué cambia

| | Modo normal | Modo niños |
|---|---|---|
| Preguntas | Banco general o regional | Banco propio (`ninos.json`), solo para este modo |
| Tiempo por pregunta | 8 s | 12 s (`GameRules.KIDS_QUESTION_TIME_MILLIS`, 1,5 veces) |
| Ronda de estrategia | Fallar resta lo apostado | Fallar no resta nada; acertar suma lo apostado |
| Anuncio al terminar la partida | Puede haber intersticial | Nunca |
| Paquete de partida (compra con monedas) | Se ofrece | No se ofrece |

Las cuatro rondas, la ruleta, los comodines gratis, el desempate y la duración (8, 10 o 12
preguntas) funcionan igual. El historial del teléfono marca estas partidas como «Niños».

El modo solo existe en la partida local: las preguntas para niños no se suben a Supabase
(`generar_preguntas_sql.py` las omite) y el juego online y la web no cambian.

## Banco de preguntas

111 preguntas (81 de opción múltiple y 30 de verdadero o falso) en 11 categorías, todas
objetivas y de dificultad fácil o media. Se añadieron las categorías «Animales» y «Números y
formas». `QuestionValidator` impide que una pregunta para niños sea de debate u opinión o tenga
departamento, y `QuestionBankContentTest` comprueba que el banco alcanza para una revancha sin
repetir preguntas. Conviene una revisión humana antes de publicar, como con el resto del banco.

## Google Play y AdMob: público mixto

Se decidió declarar en Google Play un **público mixto** (incluye menores de 13), así que se aplica la
política de Familias. Lo que ya hace la app:

- **Pregunta neutral de edad** la primera vez que se abre: «¿En qué año naciste?», sin sugerir
  ninguna respuesta (`AgeGateScreen`, `core/.../ads/AgeGate.kt`). Solo se guarda en el teléfono si
  es menor de 13 o no, nunca el año. Ante la duda (nacido hace justo 13 años) cuenta como menor.
  No se puede cambiar desde la app; para volver a preguntar hay que borrar los datos de la app.
- **Menores de 13**: anuncios marcados como dirigidos a niños
  (`TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` y `TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE`), clasificación
  máxima `G`, sin personalizar ni identificador de publicidad, y sin videos con recompensa. El
  formulario de consentimiento del UMP también se pide marcado como menor de edad.
- **13 o más**: igual que antes (clasificación máxima `PG`, consentimiento del UMP donde la ley lo
  pide).
- La edad no separa a los jugadores: todos pueden jugar juntos en local y en online.
- En el modo niños no hay intersticial al terminar ni paquete de partida, tenga la edad que tenga
  el dueño del teléfono.

Lo que tiene que hacer el dueño de la cuenta antes de publicar:

1. Play Console → **Público objetivo y contenido**: marcar los grupos de edad que incluyan menores
   de 13 y aceptar la política de Familias.
2. AdMob → configuración de la app: revisar que no haya redes de mediación que no estén
   certificadas para Familias (AdMob sí lo está).
3. Publicar la política de privacidad actualizada (sección «Menores» de `POLITICA_PRIVACIDAD.md`).
4. Responder el cuestionario de clasificación de contenido y la sección de seguridad de los datos
   teniendo en cuenta a los menores.
5. En online, los nombres de jugador son públicos en salas y clasificación. No hay chat, pero
   conviene que la ficha y el perfil recomienden no usar el nombre real. Si Google lo pide,
   habría que ocultar la clasificación a menores de 13.
