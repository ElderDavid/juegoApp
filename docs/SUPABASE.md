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

## 3. Valores en `local.properties`

`local.properties` no se sube al repositorio (está en `.gitignore`). Añade:

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

La migración se probó en PostgreSQL 16 con un esquema `auth` simulado: creación automática de perfil, `update_profile` con nombres válidos e inválidos, y bloqueo por RLS de escrituras directas. No se ha probado aún contra un proyecto real de Supabase.
