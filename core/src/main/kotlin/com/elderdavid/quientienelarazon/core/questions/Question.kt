package com.elderdavid.quientienelarazon.core.questions

/** Naturaleza de la pregunta (sección 7 de la especificación). */
enum class QuestionKind {
    /** Respuesta verificable. */
    OBJECTIVE,

    /** Polémica o de debate: se evalúa por una regla de juego anunciada o por votación. */
    DEBATE,

    /** Opinión: siempre por votación secreta. */
    OPINION,
}

enum class AnswerFormat { MULTIPLE_CHOICE, TRUE_FALSE }

enum class Difficulty { EASY, MEDIUM, HARD }

/** Público de la pregunta: las de [KIDS] solo salen en el modo niños y no se mezclan con las demás. */
enum class Audience { ALL, KIDS }

/** Cómo se decide quién acierta. Se conoce antes de que nadie responda. */
sealed interface Evaluation {
    /** Respuesta factual. */
    data class CorrectOption(val index: Int) : Evaluation

    /**
     * Pregunta polémica con una regla de juego explícita que se muestra junto a la pregunta,
     * p. ej. «para este juego cuenta la explicación de la biología evolutiva». No se presenta como hecho.
     */
    data class GameRule(val index: Int, val rule: String) : Evaluation

    /** Ganan quienes eligen una de las opciones más votadas. */
    data object MajorityVote : Evaluation
}

/** Opciones fijas de las preguntas de verdadero o falso. */
val TRUE_FALSE_OPTIONS = listOf("Verdadero", "Falso")

data class Question(
    val id: String,
    val text: String,
    val options: List<String>,
    val format: AnswerFormat,
    val kind: QuestionKind,
    val evaluation: Evaluation,
    val categoryId: String,
    val difficulty: Difficulty,
    /** Departamento para el modo «De aquí somos»; null en preguntas generales. */
    val regionId: String? = null,
    val explanation: String,
    /** Fuente verificable cuando el dato lo requiere. Nunca se inventa: null si no aplica. */
    val source: String? = null,
    /** Fecha o periodo de referencia para datos que cambian con el tiempo. */
    val referenceDate: String? = null,
    val audience: Audience = Audience.ALL,
) {
    val isVote: Boolean get() = evaluation is Evaluation.MajorityVote

    /** Índice correcto si la pregunta tiene uno (objetiva o con regla de juego). */
    val correctIndex: Int?
        get() = when (val e = evaluation) {
            is Evaluation.CorrectOption -> e.index
            is Evaluation.GameRule -> e.index
            Evaluation.MajorityVote -> null
        }
}

data class Category(val id: String, val name: String)

/** Departamento del modo regional. Es un dato, no un enum: añadir uno no toca el motor. */
data class Region(val id: String, val name: String)
