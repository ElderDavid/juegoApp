package com.elderdavid.quientienelarazon.core.profile

/** Motivos por los que un nombre de perfil no se acepta. */
enum class ProfileNameIssue { TOO_SHORT, TOO_LONG, INVALID_CHARACTERS, OFFENSIVE }

/**
 * Reglas del nombre público de perfil (visible en partidas online). El servidor repite esta misma
 * validación: el cliente solo la usa para avisar antes de enviar.
 */
object ProfileName {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 16

    private val allowed = Regex("^[\\p{L}\\p{N} ._-]+$")

    // Términos ofensivos frecuentes en español. Las palabras cortas solo cuentan como palabra
    // completa, para no bloquear nombres como «Vergara» o «Computador».
    private val blockedWords = setOf(
        "puta", "puto", "putas", "putos", "culo", "verga", "polla", "perra", "zorra", "marica", "idiota",
        "imbecil", "pendejo", "pendeja", "huevon", "guevon", "cabron", "estupido", "estupida", "nazi", "coño", "cono",
    ).map(::normalizeForCheck).toSet()

    // Raíces largas y sin uso inocente: se buscan dentro del nombre, aunque vayan pegadas.
    private val blockedRoots = listOf(
        "hijueputa", "hijoeputa", "hijodeputa", "malparid", "gonorrea", "maricon", "carechimba", "careverga",
        "hpta", "pirob", "mierda", "hitler", "sicari", "violador",
    ).map(::normalizeForCheck)

    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    fun validate(name: String): ProfileNameIssue? {
        val cleaned = clean(name)
        return when {
            cleaned.length < MIN_LENGTH -> ProfileNameIssue.TOO_SHORT
            cleaned.length > MAX_LENGTH -> ProfileNameIssue.TOO_LONG
            !allowed.matches(cleaned) -> ProfileNameIssue.INVALID_CHARACTERS
            isOffensive(cleaned) -> ProfileNameIssue.OFFENSIVE
            else -> null
        }
    }

    fun isOffensive(name: String): Boolean {
        val words = name.split(Regex("[^\\p{L}\\p{N}@$!]+")).map(::normalizeForCheck).filter { it.isNotEmpty() }
        if (words.any { it in blockedWords }) return true
        val joined = normalizeForCheck(name)
        return blockedRoots.any { it in joined }
    }

    /** Minúsculas, sin tildes ni eñe, sustituciones típicas (0→o, 1→i…) y sin separadores. */
    internal fun normalizeForCheck(text: String): String {
        return text.lowercase().map {
            when (it) {
                'á', 'à', 'ä', 'â' -> 'a'; 'é', 'è', 'ë', 'ê' -> 'e'; 'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'; 'ú', 'ù', 'ü', 'û' -> 'u'; 'ñ' -> 'n'
                '0' -> 'o'; '1', '!' -> 'i'; '3' -> 'e'; '4', '@' -> 'a'; '5', '$' -> 's'; '7' -> 't'
                else -> it
            }
        }.filter { it.isLetter() }.joinToString("")
    }
}

/** Avatares predefinidos: no se suben imágenes, así no hay contenido que moderar. */
object Avatars {
    const val COUNT = 12
    fun isValid(id: Int) = id in 0 until COUNT
}
