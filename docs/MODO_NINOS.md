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

## Google Play y AdMob

Hoy la app se declara para mayores de 13 años (`PUBLICACION.md`, punto 7). Un modo niños, sobre
todo si aparece en la ficha de la tienda, es justo el tipo de señal por la que Google revisa si la
app atrae a niños. Hay dos caminos:

1. **Seguir con público 13+.** No hace falta más código, pero la ficha no debería anunciar la app
   para niños y el modo se presenta como juego en familia con un adulto. Google puede igualmente
   decidir que la app atrae a niños y pedir que cumpla la política de Familias.
2. **Declarar público mixto (incluye menores de 13) y cumplir la política de Familias.** Es lo
   correcto si se quiere promocionar el modo niños. Requiere:
   - Una pregunta neutral de edad al abrir la app (sin sugerir la respuesta «correcta»).
   - Para quien diga tener menos de 13: anuncios marcados como dirigidos a niños
     (`setTagForChildDirectedTreatment`), clasificación máxima `G`, sin anuncios personalizados ni
     identificador de publicidad, y sin pedir datos personales.
   - Revisar compras y videos con recompensa para menores, y el juego online (nombres públicos y
     clasificación).
   - Actualizar la política de privacidad (sección «Menores»), la sección de seguridad de los
     datos y el cuestionario de clasificación de contenido. En Colombia, los datos de niños tienen
     protección reforzada (Ley 1581 de 2012).

AdMob está en la lista de SDK de anuncios autocertificados para Familias, así que no hay que
cambiar de red de anuncios en ninguno de los dos casos.
