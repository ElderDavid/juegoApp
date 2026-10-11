package com.elderdavid.quientienelarazon.core.questions

import com.elderdavid.quientienelarazon.core.TestBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionValidatorTest {
    private val base = TestBank.mc("q1", "ciencia", Difficulty.EASY)
    private val bank = TestBank.standard(withVotes = true)

    private fun messages(q: Question) = QuestionValidator.validate(q, bank).map { it.message }

    @Test fun validQuestionHasNoIssues() = assertTrue(messages(base).isEmpty())
    @Test fun testBankIsValid() = assertEquals(emptyList<ValidationIssue>(), QuestionValidator.validateBank(bank))

    @Test fun correctIndexOutOfRange() = assertTrue(messages(base.copy(evaluation = Evaluation.CorrectOption(4))).any { "fuera de rango" in it })
    @Test fun emptyText() = assertTrue(messages(base.copy(text = " ")).any { "Enunciado" in it })
    @Test fun emptyExplanation() = assertTrue(messages(base.copy(explanation = "")).any { "Explicación" in it })
    @Test fun duplicatedOptions() = assertTrue(messages(base.copy(options = listOf("A", "a", "B"))).any { "repetidas" in it })
    @Test fun tooFewOptions() = assertTrue(messages(base.copy(options = listOf("A", "B"))).any { "3 o 4" in it })
    @Test fun badTrueFalse() = assertTrue(messages(base.copy(format = AnswerFormat.TRUE_FALSE, options = listOf("Sí", "No"))).isNotEmpty())
    @Test fun unknownCategory() = assertTrue(messages(base.copy(categoryId = "nada")).any { "Categoría" in it })
    @Test fun unknownRegion() = assertTrue(messages(base.copy(regionId = "narnia")).any { "Departamento" in it })

    @Test
    fun objectiveNeedsCorrectAnswerAndOpinionNeedsVote() {
        assertTrue(messages(base.copy(evaluation = Evaluation.MajorityVote)).isNotEmpty())
        assertTrue(messages(base.copy(kind = QuestionKind.OPINION)).isNotEmpty())
    }

    @Test
    fun debateCannotBePresentedAsFact() {
        assertTrue(messages(base.copy(kind = QuestionKind.DEBATE)).any { "factual" in it })
        assertTrue(messages(base.copy(kind = QuestionKind.DEBATE, evaluation = Evaluation.GameRule(0, "Regla"))).isEmpty())
        assertTrue(messages(base.copy(kind = QuestionKind.DEBATE, evaluation = Evaluation.GameRule(0, " "))).isNotEmpty())
    }

    @Test
    fun duplicatedIdsInBank() {
        val dup = QuestionBank(listOf(base, base), bank.categories, bank.regions)
        assertTrue(QuestionValidator.validateBank(dup).any { it.message == "ID duplicado" })
    }
}
