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

    private val countryIds = bank.countries.map { it.id }.toSet()
    private val countryQuestions = bank.questions.filter { it.regionId in countryIds }
    private val adultQuestions = bank.questions.filter { it.audience == Audience.ALL && it.regionId !in countryIds }
    private val kidsQuestions = bank.questions.filter { it.audience == Audience.KIDS }

    @Test fun hasExactly380QuestionsPlus111ForKidsAnd180OfCountries() {
        assertEquals(380, adultQuestions.size)
        assertEquals(111, kidsQuestions.size)
        assertEquals(180, countryQuestions.size)
    }

    @Test
    fun distributionIsExact() {
        val byRegion = adultQuestions.groupingBy { it.regionId }.eachCount()
        assertEquals(
            mapOf(null to 140, "la-guajira" to 40, "magdalena" to 40, "cesar" to 40, "atlantico" to 40,
                "antioquia" to 40, "bogota" to 40),
            byRegion,
        )
        assertEquals(bank.countries.associate { it.id to 30 }, countryQuestions.groupingBy { it.regionId }.eachCount())
    }

    @Test fun idsAreUnique() = assertEquals(bank.questions.size, bank.questions.map { it.id }.toSet().size)

    @Test
    fun everyQuestionPassesStructuralValidation() {
        assertEquals(emptyList<ValidationIssue>(), QuestionValidator.validateBank(bank))
    }

    @Test
    fun regionalQuestionsCiteASourceAndUseRegionalCategories() {
        bank.questions.filter { it.regionId != null && it.regionId !in countryIds }.forEach {
            assertTrue("${it.id} sin fuente", !it.source.isNullOrBlank())
            assertTrue("${it.id} categoría general", it.categoryId.startsWith("reg_"))
        }
    }

    @Test
    fun countryQuestionsAreObjectiveWithSources() {
        // Las de países usan categorías generales y, de las regionales, solo las que valen para un país.
        val allowedRegional = setOf("reg_gastronomia", "reg_simbolos", "reg_lugares")
        countryQuestions.forEach {
            assertTrue("${it.id} sin fuente", !it.source.isNullOrBlank())
            assertEquals("${it.id}", QuestionKind.OBJECTIVE, it.kind)
            assertTrue("${it.id} categoría", it.categoryId != "debate" && (!it.categoryId.startsWith("reg_") || it.categoryId in allowedRegional))
        }
        bank.countries.forEach { country ->
            val pool = bank.pool(ContentMode.Country(country.id))
            assertTrue("${country.name}: pocas categorías", pool.map { it.categoryId }.toSet().size >= 5)
            assertTrue("${country.name}: pocas de verdadero o falso", pool.count { it.format == AnswerFormat.TRUE_FALSE } >= 3)
            assertTrue("${country.name}: pocas difíciles", pool.count { it.difficulty == Difficulty.HARD } >= 5)
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
        val modes = listOf(ContentMode.General, ContentMode.Kids) + bank.regions.map { ContentMode.Regional(it.id) } +
            bank.countries.map { ContentMode.Country(it.id) }
        for (mode in modes) {
            for (n in GameRules.MIN_LOCAL_PLAYERS..GameRules.MAX_PLAYERS) {
                repeat(25) { seed ->
                    val state = engine.newGame(players(n), GameConfig(mode = mode), seed.toLong())
                    val ids = state.plan.questions.map { it.questionId } + state.plan.tiebreakQueue
                    assertEquals(ids.size, ids.toSet().size)
                    when (mode) {
                        is ContentMode.Regional -> assertTrue(ids.all { bank.question(it).regionId == mode.regionId })
                        is ContentMode.Country -> assertTrue(ids.all { bank.question(it).regionId == mode.countryId })
                        ContentMode.General -> assertTrue(ids.all { bank.question(it).let { q -> q.regionId == null && q.audience == Audience.ALL } })
                        ContentMode.Kids -> assertTrue(ids.all { bank.question(it).audience == Audience.KIDS })
                    }
                }
            }
        }
    }

    @Test
    fun soloGamesOnlyUseMediumAndHardQuestions() {
        val engine = GameEngine(bank)
        val modes = listOf(ContentMode.General) + bank.regions.map { ContentMode.Regional(it.id) } +
            bank.countries.map { ContentMode.Country(it.id) }
        for (mode in modes) {
            repeat(10) { seed ->
                val state = engine.newGame(players(1), GameConfig(mode = mode), seed.toLong())
                val ids = state.plan.questions.map { it.questionId } + state.plan.tiebreakQueue
                assertTrue("$mode", ids.none { bank.question(it).difficulty == Difficulty.EASY })
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

    @Test
    fun kidsQuestionsAreObjectiveAndNotTooHard() {
        kidsQuestions.forEach {
            assertEquals("${it.id}", QuestionKind.OBJECTIVE, it.kind)
            assertTrue("${it.id} es difícil", it.difficulty != Difficulty.HARD)
            assertTrue("${it.id} tiene departamento", it.regionId == null)
            assertTrue("${it.id} categoría", !it.categoryId.startsWith("reg_") && it.categoryId != "debate")
        }
    }

    @Test
    fun kidsModeOffersVarietyAndRoomForRematches() {
        val pool = bank.pool(ContentMode.Kids)
        assertTrue(pool.map { it.categoryId }.toSet().size >= 8)
        // Una revancha de 12 preguntas puede evitar todas las de la partida anterior.
        assertTrue(pool.size >= 2 * (GameRules.ROUNDS * 3 + GameRules.TIEBREAK_RESERVE))
        assertTrue(pool.count { it.format == AnswerFormat.TRUE_FALSE } >= 2 * 3)
    }
}
