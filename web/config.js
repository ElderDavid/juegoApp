// Valores PÚBLICOS del cliente web. Son los mismos que usa la app Android (gradle.properties):
// la URL del proyecto y la clave publishable están pensadas para ir en el navegador.
// Nunca pongas aquí la service_role key ni la contraseña de la base de datos.
export const config = {
  supabaseUrl: "https://sijsdrzgskieiusfukcf.supabase.co",
  supabaseKey: "sb_publishable_uJnTvyFeoz0ks4B14ch8zQ_vUzss1DK",

  // Google AdSense «H5 Games Ads». Vacío = sin anuncios. Cuando AdSense apruebe el dominio, pon aquí
  // tu ID de editor (ca-pub-…) y publica también web/ads.txt (ver docs/WEB.md).
  adsenseClient: "",
  // true mientras pruebas: Google muestra anuncios de prueba y no cuenta impresiones.
  adsTestMode: true,
};
