package com.elderdavid.quientienelarazon.core

import com.elderdavid.quientienelarazon.core.questions.AnswerFormat
import com.elderdavid.quientienelarazon.core.questions.Category
import com.elderdavid.quientienelarazon.core.questions.Difficulty
import com.elderdavid.quientienelarazon.core.questions.Evaluation
import com.elderdavid.quientienelarazon.core.questions.Question
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.QuestionKind
import com.elderdavid.quientienelarazon.core.questions.Region
import com.elderdavid.quientienelarazon.core.questions.TRUE_FALSE_OPTIONS

/** Banco sintético para probar el motor. En todas las preguntas factuales la correcta es la opción 0. */
object TestBank {
    val categories = listOf("ciencia", "historia", "deportes", "musica").map { Category(it, it) }
    val regions = listOf(Region("guajira", "La Guajira"), Region("cesar", "Cesar"))

    fun mc(id: String, cat: String, diff: Difficulty, region: String? = null) = Question(
        id = id, text = "Pregunta $id", options = listOf("A", "B", "C", "D"),
        format = AnswerFormat.MULTIPLE_CHOICE, kind = QuestionKind.OBJECTIVE,
        evaluation = Evaluation.CorrectOption(0), categoryId = cat, difficulty = diff,
        regionId = region, explanation = "Explicación $id",
    )

    fun tf(id: String, cat: String, region: String? = null) = Question(
        id = id, text = "Pregunta $id", options = TRUE_FALSE_OPTIONS,
        format = AnswerFormat.TRUE_FALSE, kind = QuestionKind.OBJECTIVE,
        evaluation = Evaluation.CorrectOption(0), categoryId = cat, difficulty = Difficulty.EASY,
        regionId = region, explanation = "Explicación $id",
    )

    fun vote(id: String, cat: String) = Question(
        id = id, text = "Opinión $id", options = listOf("X", "Y", "Z"),
        format = AnswerFormat.MULTIPLE_CHOICE, kind = QuestionKind.OPINION,
        evaluation = Evaluation.MajorityVote, categoryId = cat, difficulty = Difficulty.EASY,
        explanation = "Sin respuesta correcta: gana la mayoría.",
    )

    /** Banco suficiente para una partida general completa con desempates. */
    fun standard(
        withVotes: Boolean = false,
        hardCount: Int = 6,
    ): QuestionBank {
        val qs = mutableListOf<Question>()
        categories.forEachIndexed { i, c ->
            repeat(3) { n -> qs += mc("e-${c.id}-$n", c.id, Difficulty.EASY) }
            repeat(3) { n -> qs += mc("m-${c.id}-$n", c.id, Difficulty.MEDIUM) }
            repeat(2) { n -> qs += tf("tf-${c.id}-$n", c.id) }
            if (i < hardCount / 2 + 1) repeat(2) { n -> qs += mc("h-${c.id}-$n", c.id, Difficulty.HARD) }
        }
        if (withVotes) repeat(3) { n -> qs += vote("v-$n", "musica") }
        regions.forEach { r ->
            repeat(20) { n -> qs += mc("r-${r.id}-$n", if (n % 2 == 0) "historia" else "ciencia", Difficulty.entries[n % 3], r.id) }
        }
        return QuestionBank(qs, categories, regions)
    }
}
