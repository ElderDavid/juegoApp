# Fuentes y diseño visual

## Dirección visual: «letrero de chiva»

El juego toma su estilo de los letreros pintados a mano de las chivas y las tiendas de barrio:
colores planos y saturados, contorno de tinta grueso y sombras duras (sin desenfoque), como una
calcomanía. Los botones se hunden sobre su sombra al pulsarlos.

| Nombre | Color | Uso |
|---|---|---|
| Añil | `#2A39B8` | Fondo de las pantallas |
| Tinta | `#150F3D` | Contornos, sombras y texto sobre colores claros |
| Panel | `#1B1550` | Tarjetas y botones secundarios |
| Papel | `#FFF4D6` | Texto principal y fondo de la pregunta |
| Mango | `#FFC21A` | Acción principal y acento |
| Chicle, Menta, Cielo, Naranja | `#FF7DC0`, `#29D391`, `#63C7FF`, `#FF7A2F` | Jugadores, rondas y avisos |

Reglas de contraste (WCAG AA) en `ui/theme/Color.kt`. Los temas de color cosméticos solo cambian el
fondo y el color de los títulos, y todos cumplen esas reglas.

## Fuentes incluidas

Están en `app/src/main/res/font/`, recortadas a los caracteres latinos (unos 150 KB en total), así
que funcionan sin conexión.

- **Bungee** (David Jonathan Ross): títulos, marcadores, la ruleta. SIL Open Font License 1.1.
  https://github.com/google/fonts/tree/main/ofl/bungee
- **Atkinson Hyperlegible Next** (Braille Institute): preguntas, opciones y textos; pesos 400, 600 y 800
  generados desde la fuente variable. SIL Open Font License 1.1.
  https://github.com/google/fonts/tree/main/ofl/atkinsonhyperlegiblenext

La licencia OFL permite incluirlas y modificarlas (recortarlas) dentro de la app. Textos completos en `docs/licencias/`.

## Capturas

`CapturasTest` dibuja las pantallas principales en la JVM con Robolectric. En GitHub, el flujo
«Capturas» las genera en cada PR que toca la interfaz y las deja como artefacto. En local:
`./gradlew :app:testDebugUnitTest -Pcapturas` (quedan en `app/build/capturas/`).
