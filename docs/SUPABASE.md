# Configurar Supabase y Google (Fase 5)

La app funciona sin esto: el modo local no usa la red. Sin configuración, la pantalla de perfil dice «Cuentas aún no disponibles».

## 1. Proyecto de Supabase

1. Crea un proyecto en https://supabase.com (plan gratuito; región São Paulo es la más cercana).
2. En **SQL Editor**, ejecuta el contenido de `supabase/migrations/20261012000000_perfiles.sql`.
3. En **Authentication → Sign In / Providers**:
   - Activa **Allow anonymous sign-ins** (invitados).
   - Activa **Allow manual linking** (necesario para vincular Google a un invitado sin cambiar su ID).
4. En **Project Settings → API** copia la **Project URL** y la **publishable key** (o *anon key*). Son valores públicos de cliente.
   **Nunca** pongas la *service_role key* ni la contraseña de la base de datos en la app ni en el repositorio.

## 2. Google (para vincular cuentas)

En https://console.cloud.google.com, en un proyecto propio:

1. Configura la **pantalla de consentimiento OAuth** (tipo externo, nombre de la app, correo de soporte).
2. Crea un **ID de cliente OAuth de tipo «Aplicación web»**. Copia el *Client ID* y el *Client secret*.
3. Crea un **ID de cliente OAuth de tipo «Android»** con:
   - Nombre del paquete: `com.elderdavid.quientienelarazon`
   - SHA-1 de tu certificado de depuración. En Windows:
     `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`
   - Al publicar habrá que añadir también el SHA-1 de la firma de Google Play.
4. En Supabase, **Authentication → Sign In / Providers → Google**: actívalo, pega el *Client ID* y el *Client secret* del cliente **web**. Deja desactivado «Skip nonce checks».

## 3. Valores del cliente

El proyecto del juego (`sijsdrzgskieiusfukcf`) ya tiene la migración aplicada, y su URL y clave publishable están en `gradle.properties`, así que no hace falta configurar nada para compilar.
Para apuntar a otro proyecto, o para añadir el cliente de Google, usa `local.properties` (no se sube al repositorio y tiene prioridad):

```properties
supabase.url=https://TU-PROYECTO.supabase.co
supabase.publishableKey=TU_CLAVE_PUBLICA
google.webClientId=TU_CLIENT_ID_WEB.apps.googleusercontent.com
```

Después recompila. Si falta `google.webClientId`, las cuentas de invitado funcionan y el botón de Google indica que no está configurado.

## Qué hace la app

- Al abrir el perfil entra como invitado anónimo si no hay sesión.
- «Vincular con Google» añade Google al mismo usuario: el ID no cambia y se conserva el perfil.
- Si esa cuenta de Google ya pertenece a otro jugador, la app pregunta: seguir como invitado o cambiar a esa cuenta (el progreso del invitado no se fusiona).
- El nombre se valida en la app y otra vez en el servidor (`update_profile`): longitud 3–16, caracteres permitidos y filtro de nombres ofensivos.
- Los clientes solo pueden leer perfiles y sus propias estadísticas; no pueden escribir tablas directamente (RLS sin políticas de escritura).

## Verificado

La migración se probó en PostgreSQL 16 con un esquema `auth` simulado: creación automática de perfil, `update_profile` con nombres válidos e inválidos, y bloqueo por RLS de escrituras directas. El 12/10/2026 se aplicó en el proyecto real `sijsdrzgskieiusfukcf`; el asesor de seguridad de Supabase solo señala que `update_profile` es ejecutable por usuarios con sesión, que es intencional.

Ese mismo día se aplicaron también `salas_online` y las preguntas (ver `docs/ONLINE.md`). El asesor
de seguridad marca como «ejecutables por usuarios con sesión» las funciones de sala (`create_room`,
`room_state`, `submit_answer`…), que es lo previsto: son la única puerta de entrada de los clientes.
Las tablas de salas y preguntas tienen RLS sin políticas a propósito (nadie las lee directamente).
También aparece `rls_auto_enable`, que no es de este proyecto sino una función de disparador de
eventos que Supabase crea en proyectos nuevos; al devolver `event_trigger` no se puede llamar como RPC.
