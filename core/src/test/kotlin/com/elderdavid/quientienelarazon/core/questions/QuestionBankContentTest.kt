package com.elderdavid.quientienelarazon.core.questions

import com.elderdavid.quientienelarazon.core.game.GameConfig
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.players
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validación automática del banco real (sección 8 de la especificación). */
class QuestionBankContentTest {
    private val bank = QuestionBankLoader.fromResources()

    @Test fun hasExactly300Questions() = assertEquals(300, bank.questions.size)

    @Test
    fun distributionIsExact() {
        val byRegion = bank.questions.groupingBy { it.regionId }.eachCount()
        assertEquals(
            mapOf(null to 140, "la-guajira" to 40, "magdalena" to 40, "cesar" to 40, "atlantico" to 40),
            byRegion,
        )
    }

    @Test fun idsAreUnique() = assertEquals(bank.questions.size, bank.questions.map { it.id }.toSet().size)

    @Test
    fun everyQuestionPassesStructuralValidation() {
        assertEquals(emptyList<ValidationIssue>(), QuestionValidator.validateBank(bank))
    }

    @Test
    fun regionalQuestionsCiteASourceAndUseRegionalCategories() {
        bank.questions.filter { it.regionId != null }.forEach {
            assertTrue("${it.id} sin fuente", !it.source.isNullOrBlank())
            assertTrue("${it.id} categoría general", it.categoryId.startsWith("reg_"))
        }
    }

    @Test
    fun generalQuestionsUseGeneralCategories() {
        bank.questions.filter { it.regionId == null }.forEach {
            assertTrue("${it.id} categoría regional", !it.categoryId.startsWith("reg_"))
        }
    }

    @Test
    fun factualAnswersAreSpreadAcrossPositions() {
        // Evita que la respuesta correcta esté casi siempre en la misma posición.
        val positions = bank.questions
            .filter { it.format == AnswerFormat.MULTIPLE_CHOICE && it.correctIndex != null }
            .groupingBy { it.correctIndex!! }.eachCount()
        val total = positions.values.sum()
        positions.values.forEach { assertTrue("Distribución sesgada: $positions", it < total / 2) }
    }

    @Test
    fun opinionQuestionsNeverHaveACorrectAnswer() {
        bank.questions.filter { it.kind == QuestionKind.OPINION }.forEach { assertTrue(it.isVote) }
    }

    @Test
    fun everyModeCanPlanFullGamesForTwoToFourPlayers() {
        val engine = GameEngine(bank)
        val modes = listOf(ContentMode.General) + bank.regions.map { ContentMode.Regional(it.id) }
        for (mode in modes) {
            for (n in GameRules.MIN_PLAYERS..GameRules.MAX_PLAYERS) {
                repeat(25) { seed ->
                    val state = engine.newGame(players(n), GameConfig(mode = mode), seed.toLong())
                    val ids = state.plan.questions.map { it.questionId } + state.plan.tiebreakQueue
                    assertEquals(ids.size, ids.toSet().size)
                    if (mode is ContentMode.Regional) {
                        assertTrue(ids.all { bank.question(it).regionId == mode.regionId })
                    } else {
                        assertTrue(ids.all { bank.question(it).regionId == null })
                    }
                }
            }
        }
    }

    @Test
    fun eachRegionOffersSeveralCategoriesForTheRoulette() {
        bank.regions.forEach { region ->
            val categories = bank.pool(ContentMode.Regional(region.id)).map { it.categoryId }.toSet()
            assertTrue("${region.name}: ${categories.size} categorías", categories.size >= 5)
        }
    }
}
