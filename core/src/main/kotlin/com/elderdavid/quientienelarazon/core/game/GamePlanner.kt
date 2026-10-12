package com.elderdavid.quientienelarazon.core.game

import com.elderdavid.quientienelarazon.core.questions.AnswerFormat
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.Difficulty
import com.elderdavid.quientienelarazon.core.questions.Question
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import kotlin.random.Random

/** Pregunta ya decidida para una posición de la partida. La categoría es lo que muestra la ruleta. */
data class PlannedQuestion(val index: Int, val round: RoundType, val categoryId: String, val questionId: String)

data class GamePlan(
    val questions: List<PlannedQuestion>,
    /** Preguntas objetivas reservadas para desempates, en orden de uso. */
    val tiebreakQueue: List<String>,
)

sealed class PlanningError(message: String) : Exception(message) {
    class NotEnoughQuestions(round: RoundType, index: Int) :
        PlanningError("No hay preguntas válidas para la pregunta ${index + 1} (ronda ${round.title})")
}

/**
 * Elige la categoría de la siguiente pregunta. El resultado se calcula ANTES de animar la ruleta:
 * la animación solo lo representa y no puede cambiarlo.
 */
object CategoryRoulette {
    /** Evita repetir las últimas [avoidRecent] categorías cuando hay alternativas. */
    fun choose(available: Collection<String>, recent: List<String>, random: Random, avoidRecent: Int = 2): String {
        require(available.isNotEmpty()) { "No hay categorías con preguntas válidas" }
        val sorted = available.sorted() // orden estable: mismo resultado con la misma semilla
        val recentSet = recent.takeLast(avoidRecent).toSet()
        val fresh = sorted.filterNot { it in recentSet }
        val lastOnly = sorted.filterNot { it == recent.lastOrNull() }
        val candidates = fresh.ifEmpty { lastOnly.ifEmpty { sorted } }
        return candidates[random.nextInt(candidates.size)]
    }
}

/**
 * Planifica las 12 preguntas al inicio con una semilla. En online, la semilla y el plan los genera
 * el servidor, así todos los teléfonos reciben el mismo orden.
 */
object GamePlanner {

    fun plan(
        bank: QuestionBank,
        mode: ContentMode,
        playerCount: Int,
        seed: Long,
        avoidQuestionIds: Set<String> = emptySet(),
    ): GamePlan {
        val random = Random(seed)
        val fullPool = bank.pool(mode)
        // Revancha: se evitan las preguntas recientes si quedan suficientes; si no, se permiten.
        val preferred = fullPool.filterNot { it.id in avoidQuestionIds }
        val pool = if (preferred.size >= GameRules.TOTAL_QUESTIONS + GameRules.TIEBREAK_RESERVE) preferred else fullPool
        val votesAllowed = if (playerCount >= GameRules.MIN_PLAYERS_FOR_VOTES) GameRules.MAX_VOTE_QUESTIONS else 0

        val used = mutableSetOf<String>()
        val recentCategories = mutableListOf<String>()
        var votesUsed = 0
        val planned = mutableListOf<PlannedQuestion>()

        for (index in 0 until GameRules.TOTAL_QUESTIONS) {
            val round = RoundType.forQuestionIndex(index)
            val candidates = eligibilityTiers(round).asSequence()
                .map { tier ->
                    pool.filter { q ->
                        q.id !in used && tier(q) && (!q.isVote || votesUsed < votesAllowed)
                    }
                }
                .firstOrNull { it.isNotEmpty() }
                ?: throw PlanningError.NotEnoughQuestions(round, index)

            val byCategory = candidates.groupBy { it.categoryId }
            val categoryId = CategoryRoulette.choose(byCategory.keys, recentCategories, random)
            val options = byCategory.getValue(categoryId).sortedBy { it.id }
            val question = options[random.nextInt(options.size)]

            used += question.id
            recentCategories += categoryId
            if (question.isVote) votesUsed++
            planned += PlannedQuestion(index, round, categoryId, question.id)
        }

        val tiebreak = pool
            .filter { it.id !in used && it.correctIndex != null }
            .sortedBy { it.id }
            .shuffled(random)
            .sortedByDescending { it.difficulty.ordinal } // más difíciles primero, orden estable
            .take(GameRules.TIEBREAK_RESERVE)
            .map { it.id }

        return GamePlan(planned, tiebreak)
    }

    /** Filtros por ronda, del más estricto al más permisivo. */
    private fun eligibilityTiers(round: RoundType): List<QuestionFilter> {
        val factual: QuestionFilter = { it.correctIndex != null }
        val tiers: List<QuestionFilter> = when (round) {
            RoundType.KNOWLEDGE -> listOf(
                { it.format == AnswerFormat.MULTIPLE_CHOICE && it.difficulty != Difficulty.HARD },
                { it.format == AnswerFormat.MULTIPLE_CHOICE },
            )
            RoundType.SPEED -> listOf(
                { factual(it) && it.format == AnswerFormat.TRUE_FALSE },
                { factual(it) && it.difficulty == Difficulty.EASY },
                { factual(it) && it.difficulty != Difficulty.HARD },
            )
            RoundType.STRATEGY -> listOf(
                { it.difficulty == Difficulty.MEDIUM || it.isVote },
                { it.difficulty != Difficulty.HARD },
                { true },
            )
            RoundType.FINAL -> listOf(
                { factual(it) && it.difficulty == Difficulty.HARD },
                { factual(it) && it.difficulty == Difficulty.MEDIUM },
                factual,
            )
        }
        // Las votaciones solo se usan en conocimiento y estrategia (no tienen sentido con cronómetro).
        val votesOk: QuestionFilter = { !it.isVote || round == RoundType.KNOWLEDGE || round == RoundType.STRATEGY }
        return tiers.map { tier -> { q: Question -> votesOk(q) && tier(q) } }
    }
}

private typealias QuestionFilter = (Question) -> Boolean
