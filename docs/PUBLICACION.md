# Publicar en Google Play (Fase 10)

Este documento dice qué está listo en el código y qué tiene que hacer el dueño de la cuenta de
desarrollador. Compilar el AAB **no** es publicar: la app solo está en Google Play cuando Play
Console lo muestra como publicada.

## Lo que ya está en el código

| Pieza | Estado |
|---|---|
| `applicationId` `com.elderdavid.quientienelarazon` | Fijo. No se puede cambiar después de publicar |
| `versionCode` 1, `versionName` 1.0.0 | Subir `versionCode` en cada envío a Play |
| `targetSdk` 36, `minSdk` 26 | Cumple el requisito de Play para apps nuevas |
| R8 (reducción de código y recursos) | Activado; CI compila la release en cada PR |
| Firma de la release | Se lee de `keystore.properties` (fuera del repositorio). Sin él, la release sale sin firmar |
| Borrar la cuenta desde la app | Perfil → «Borrar mi cuenta» (función `delete_my_account` en Supabase) |
| Anuncios | Solo de prueba hasta poner los identificadores reales (ver `ANUNCIOS.md`) |
| Copia de seguridad de Android | Desactivada (`allowBackup="false"`) |

## 1. Crear la clave de subida (una sola vez, en tu PC)

```bash
keytool -genkeypair -v -keystore claves/subida.jks -alias subida \
  -keyalg RSA -keysize 2048 -validity 10000
```

- Guarda `subida.jks` y sus contraseñas fuera del repositorio y con copia de seguridad. Si se
  pierde, se puede pedir a Google que la cambie, pero lleva tiempo.
- Copia `keystore.properties.example` como `keystore.properties` y rellénalo.
- Google Play guarda la clave definitiva de la app («Firma de apps de Google Play»); la tuya
  solo sirve para subir versiones.

## 2. Compilar el AAB

```bash
./gradlew :app:bundleRelease
```

Resultado: `app/build/outputs/bundle/release/app-release.aab`. Antes de compilarlo para Play:

- En `local.properties`: `supabase.url`, `supabase.publishableKey`, `google.webClientId` y,
  si quieres anuncios reales, los tres `admob.*`.
- En Google Cloud, el cliente OAuth de Android debe tener la **huella SHA-1 de la clave de firma
  de Google Play** (Play Console → Configuración → Integridad de la app), además de la de tu
  clave de subida. Si falta, «Vincular con Google» falla en la versión descargada de Play.

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
7. **Público objetivo**: recomendado 13 años o más. Si se incluyen menores de 13, se aplica la
   política de Familias, que obliga a usar solo SDK de anuncios certificados para niños y otra
   configuración de AdMob.
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
