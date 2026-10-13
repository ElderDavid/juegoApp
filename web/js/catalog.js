// Nombres que el servidor solo envía como identificadores. Copia de
// core/src/main/resources/questions/index.json y de AvatarEmojis (ProfileScreen.kt);
// web/test/catalog.test.mjs falla si dejan de coincidir.

export const CATEGORIES = {
  ciencia: "Ciencia y naturaleza",
  historia: "Historia",
  geografia: "Geografía",
  deportes: "Deportes",
  tecnologia: "Tecnología",
  cine: "Cine y entretenimiento",
  musica: "Música",
  cultura: "Cultura general",
  curiosidades: "Curiosidades",
  animales: "Animales",
  numeros: "Números y formas",
  debate: "Debate y opinión",
  reg_geografia: "Municipios y geografía",
  reg_historia: "Historia regional",
  reg_simbolos: "Símbolos",
  reg_gastronomia: "Gastronomía",
  reg_culturas: "Culturas indígenas y locales",
  reg_tradiciones: "Tradiciones",
  reg_lugares: "Lugares representativos",
  reg_personajes: "Personajes y acontecimientos",
  reg_curiosidades: "Curiosidades regionales",
};

export const REGIONS = [
  { id: "la-guajira", name: "La Guajira" },
  { id: "magdalena", name: "Magdalena" },
  { id: "cesar", name: "Cesar" },
  { id: "atlantico", name: "Atlántico" },
  { id: "antioquia", name: "Antioquia" },
  { id: "bogota", name: "Bogotá D.C." },
];

/** Modalidades de partida: general o «De aquí somos» de un departamento. */
export const MODES = [{ id: "general", name: "General" }, ...REGIONS];

/** Avatares predefinidos (fauna colombiana). El índice es el avatar_id del servidor. */
export const AVATARS = ["🦜", "🐆", "🦊", "🐢", "🐬", "🦉", "🐸", "🦋", "🐝", "🦁", "🐙", "🦩"];

export function categoryName(id) {
  return CATEGORIES[id] ?? id;
}

export function modeName(id) {
  if (id === "general") return "General";
  const region = REGIONS.find((r) => r.id === id);
  return region ? `De aquí somos: ${region.name}` : id;
}
