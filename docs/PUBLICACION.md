# Publicar en Google Play (Fase 10)

Este documento dice qué está listo en el código y qué tiene que hacer el dueño de la cuenta de
desarrollador. Compilar el AAB **no** es publicar: la app solo está en Google Play cuando Play
Console lo muestra como publicada.

## Lo que ya está en el código

| Pieza | Estado |
|---|---|
| `applicationId` `com.elderdavid.quientienelarazon` | Fijo. No se puede cambiar después de publicar |
| `versionCode` | El flujo «Versión para Google Play» lo sube solo (100 + número de ejecución) |
| `targetSdk` 36, `minSdk` 26 | Cumple el requisito de Play para apps nuevas |
| R8 (reducción de código y recursos) | Activado; CI compila la release en cada PR |
| Firma de la release | En GitHub, con secretos (ver abajo). En un PC, se lee de `keystore.properties` |
| Borrar la cuenta desde la app | Perfil → «Borrar mi cuenta» (función `delete_my_account` en Supabase) |
| Anuncios | Solo de prueba hasta poner los identificadores reales (ver `ANUNCIOS.md`) |
| Copia de seguridad de Android | Desactivada (`allowBackup="false"`) |

## 1 y 2. Clave de subida y .aab firmado desde GitHub (recomendado)

No hace falta Android Studio. Todo se hace en GitHub → repositorio → **Settings → Secrets and
variables → Actions → New repository secret**, y en la pestaña **Actions**.

**Una sola vez: crear la clave**

1. Crea el secreto `RELEASE_KEYSTORE_PASSWORD` con una contraseña de al menos 8 caracteres.
   Apúntala también en un lugar seguro.
2. En **Actions → Crear clave de subida → Run workflow**. Al terminar, descarga el artefacto
   `clave-subida` (se borra solo en 1 día).
3. Guarda `subida.jks` en un lugar seguro (por ejemplo, Google Drive) junto con la contraseña.
4. Abre `subida.jks.base64.txt`, copia todo el texto y crea el secreto `RELEASE_KEYSTORE_BASE64`.
5. Borra la descarga del computador cuando la tengas guardada.

**Resto de secretos**

| Secreto | Obligatorio | Valor |
|---|---|---|
| `SUPABASE_URL` | Sí | `https://sijsdrzgskieiusfukcf.supabase.co` |
| `SUPABASE_PUBLISHABLE_KEY` | Sí | Supabase → Project Settings → API Keys → *publishable* (nunca la *secret*) |
| `GOOGLE_WEB_CLIENT_ID` | No | ID de cliente web de Google Cloud, para «Vincular con Google» |
| `ADMOB_APP_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_BANNER_ID` | No | Identificadores reales de AdMob. Sin ellos la versión no muestra anuncios |
| `ADMOB_REWARDED_ID` | No | Bloque de video con recompensa |

**Cada vez que quieras subir una versión**

**Actions → Versión para Google Play → Run workflow**. Al terminar, descarga el artefacto
`version-play-N`: trae `app-release.aab` (lo que se sube a Play) y `mapping.txt` (Play lo pide
para leer los informes de fallos). El `versionCode` sube solo en cada ejecución.

En Google Cloud, el cliente OAuth de Android debe tener la **huella SHA-1 de la clave de firma de
Google Play** (Play Console → Configuración → Integridad de la app), además de la de la clave de
subida (sale en `huellas.txt`). Si falta, «Vincular con Google» falla en la versión de Play.

### Alternativa: compilar en un PC

```bash
keytool -genkeypair -v -keystore claves/subida.jks -alias subida \
  -keyalg RSA -keysize 2048 -validity 10000
./gradlew :app:bundleRelease
```

Copia `keystore.properties.example` como `keystore.properties` y rellénalo. En `local.properties`
van `supabase.url`, `supabase.publishableKey`, `google.webClientId` y los `admob.*`. Resultado:
`app/build/outputs/bundle/release/app-release.aab`.

## 3. Play Console

1. **Crear la app**: nombre «¿Quién tiene la razón?», idioma español, juego, gratis.
2. **Ficha de la tienda**: descripción corta y larga, icono 512×512, gráfico de 1024×500 y al
   menos 2 capturas de teléfono. Las capturas deben salir de la app real.
3. **Política de privacidad**: hay que publicarla en una URL pública. Hay un borrador en
   `POLITICA_PRIVACIDAD.md`; complétalo con tu nombre y correo de contacto.
4. **Acceso a la app**: no hace falta usuario ni contraseña (se entra como invitado).
5. **Anuncios**: «Sí, mi app contiene anuncios».
6. **Clasificación de contenido**: rellenar el cuestionario de IARC. Responder con la verdad
   sobre la interacción entre usuarios: hay nombres públicos de jugadores en la clasificación y
   en las salas online, pero no hay chat.
7. **Público objetivo**: público mixto, incluidos menores de 13, por el modo niños. Se aplica la
   política de Familias; la app ya pregunta la edad y adapta los anuncios. Ver `MODO_NINOS.md`.
8. **Seguridad de los datos**: ver la tabla siguiente.
9. **Borrado de cuenta**: Play pide el método dentro de la app (ya existe) **y** una URL web
   donde pedir el borrado sin instalar la app. Puede ser una página sencilla con tu correo y
   los pasos.
10. **Pruebas**: las cuentas personales de desarrollador nuevas deben hacer primero una prueba
    cerrada con al menos 12 personas durante 14 días antes de poder pedir producción. Revisa en
    Play Console lo que aplique a tu cuenta.

## Seguridad de los datos (para el formulario de Play)

| Dato | Lo recoge | Para qué | Compartido |
|---|---|---|---|
| ID de usuario (invitado o Google) | Supabase | Cuenta y juego online | No |
| Correo electrónico (solo si vincula Google) | Supabase Auth | Recuperar la cuenta | No |
| Nombre público y avatar | Supabase | Perfil, salas y clasificación (visibles para otros jugadores) | No |
| Estadísticas y respuestas online | Supabase | Puntuación, nivel y clasificación | No |
| ID de publicidad, datos del dispositivo, ubicación aproximada por IP | SDK de Google Mobile Ads | Anuncios, analítica de anuncios y prevención de fraude | Sí, con Google |
| Historial de compras (producto, id de pedido) | Google Play Billing y Supabase | Entregar lo comprado | No (el cobro lo hace Google) |
| Monedas, cosméticos, logros y partidas locales terminadas | Supabase | Premios del juego | No |

- Todo viaja cifrado (HTTPS).
- El usuario puede borrar su cuenta y sus datos online desde la app.
- Las partidas locales y los ajustes no salen del teléfono.
- Revisa la guía oficial de Google para el SDK de anuncios antes de enviar el formulario: lo que
  recoge depende de la versión del SDK y de la configuración de AdMob.

## Lista de comprobación antes de enviar

- [ ] La rama con todas las fases está fusionada y CI está en verde.
- [ ] Probada en un teléfono real: partida local, online entre dos teléfonos, vincular Google,
      borrar cuenta, anuncios de prueba.
- [ ] `versionCode` mayor que el último enviado.
- [ ] Identificadores de AdMob reales solo en `local.properties` de la máquina que compila la
      versión de Play.
- [ ] Supabase: inicios de sesión anónimos y vinculación manual activados; migraciones aplicadas.
- [ ] Política de privacidad y página de borrado publicadas.
- [ ] Monedas y compras: migración de economía aplicada, funciones `verify-purchase` y `admob-ssv`
      desplegadas, productos creados en Play Console y verificación de servidor activada en el
      bloque de video de AdMob (`ECONOMIA.md`).
- [ ] En Play Console, la ficha declara «Compras en la aplicación».
