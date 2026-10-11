package com.elderdavid.quientienelarazon.core.game

import com.elderdavid.quientienelarazon.core.TestBank
import com.elderdavid.quientienelarazon.core.questions.AnswerFormat
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GamePlannerTest {
    private val bank = TestBank.standard()

    @Test
    fun planHasTwelveUniqueQuestionsInRoundOrder() {
        val plan = GamePlanner.plan(bank, ContentMode.General, 3, seed = 1)
        assertEquals(12, plan.questions.size)
        assertEquals(12, plan.questions.map { it.questionId }.toSet().size)
        plan.questions.forEachIndexed { i, p -> assertEquals(RoundType.forQuestionIndex(i), p.round) }
        assertTrue(plan.tiebreakQueue.none { id -> plan.questions.any { it.questionId == id } })
        assertEquals(GameRules.TIEBREAK_RESERVE, plan.tiebreakQueue.size)
    }

    @Test
    fun sameSeedGivesSamePlanForEveryPhone() {
        assertEquals(
            GamePlanner.plan(bank, ContentMode.General, 4, seed = 99),
            GamePlanner.plan(bank, ContentMode.General, 4, seed = 99),
        )
        assertNotEquals(
            GamePlanner.plan(bank, ContentMode.General, 4, seed = 99),
            GamePlanner.plan(bank, ContentMode.General, 4, seed = 100),
        )
    }

    @Test
    fun plannedCategoryMatchesQuestionCategory() {
        val plan = GamePlanner.plan(bank, ContentMode.General, 2, seed = 5)
        plan.questions.forEach { assertEquals(bank.question(it.questionId).categoryId, it.categoryId) }
    }

    @Test
    fun speedRoundPrefersTrueFalseAndFinalPrefersHard() {
        repeat(20) { seed ->
            val plan = GamePlanner.plan(bank, ContentMode.General, 2, seed.toLong())
            plan.questions.filter { it.round == RoundType.SPEED }
                .forEach { assertEquals(AnswerFormat.TRUE_FALSE, bank.question(it.questionId).format) }
        }
    }

    @Test
    fun rouletteAvoidsRepeatingConsecutiveCategoriesWhenPossible() {
        repeat(50) { seed ->
            val cats = GamePlanner.plan(bank, ContentMode.General, 2, seed.toLong()).questions.map { it.categoryId }
            cats.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun rouletteOnlyChoosesAvailableCategories() {
        val r = Random(1)
        repeat(100) {
            val c = CategoryRoulette.choose(listOf("a", "b"), recent = listOf("a"), random = r)
            assertEquals("b", c)
        }
        assertEquals("a", CategoryRoulette.choose(listOf("a"), recent = listOf("a"), random = r))
    }

    @Test
    fun rematchAvoidsPreviousQuestionsWhenEnoughRemain() {
        val big = TestBank.standard()
        val first = GamePlanner.plan(big, ContentMode.General, 2, seed = 1)
        val used = first.questions.map { it.questionId }.toSet()
        val second = GamePlanner.plan(big, ContentMode.General, 2, seed = 2, avoidQuestionIds = used)
        assertTrue(second.questions.none { it.questionId in used })
    }

    @Test(expected = PlanningError.NotEnoughQuestions::class)
    fun exhaustedBankFailsBeforeTheGameStarts() {
        val tiny = QuestionBank(bank.questions.take(5), bank.categories, bank.regions)
        GamePlanner.plan(tiny, ContentMode.General, 2, seed = 1)
    }
}
