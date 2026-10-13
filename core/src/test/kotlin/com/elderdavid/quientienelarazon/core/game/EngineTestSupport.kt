package com.elderdavid.quientienelarazon.core.game

import com.elderdavid.quientienelarazon.core.TestBank
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import org.junit.Assert.fail

fun players(n: Int) = (1..n).map { Player(PlayerId("p$it"), "Jugador $it") }

fun EngineResult.ok(): GameState = when (this) {
    is EngineResult.Ok -> state
    is EngineResult.Rejected -> fail("Acción rechazada: $reason").let { error("unreachable") }
}

fun EngineResult.rejection(): Rejection? = (this as? EngineResult.Rejected)?.reason

/** Respuesta simulada de un jugador. */
data class Move(val choice: Int?, val elapsed: Long = 1_000, val bet: Int? = null)

class Driver(val bank: QuestionBank = TestBank.standard(), val engine: GameEngine = GameEngine(bank)) {

    /** Juega la pregunta actual (desde ROULETTE o QUESTION) con los movimientos indicados, en orden de turno. */
    fun play(state: GameState, moves: (PlayerId) -> Move): GameState {
        var s = if (state.phase == Phase.ROULETTE) engine.startQuestion(state).ok() else state
        for (id in s.turnOrder) {
            val m = moves(id)
            if (m.bet != null) s = engine.placeBet(s, id, m.bet).ok()
            s = engine.submitAnswer(s, id, m.choice, m.elapsed).ok()
        }
        return s
    }

    /** Avanza hasta la siguiente pregunta o el final. */
    fun next(state: GameState): GameState {
        var s = engine.advance(state).ok()
        if (s.phase == Phase.ROUND_SUMMARY) s = engine.advance(s).ok()
        return s
    }

    /** Juega las preguntas principales; [moves] recibe índice de pregunta y jugador. */
    fun playMain(state: GameState, moves: (Int, PlayerId, GameState) -> Move): GameState {
        var s = state
        while (true) {
            val index = s.questionIndex
            s = play(s) { id -> moves(index, id, s) }
            if (index == s.config.length.questions - 1) return engine.advance(s).ok()
            s = next(s)
        }
    }
}

/** Respuesta correcta en el banco de prueba: siempre la opción 0. */
const val RIGHT = 0
const val WRONG = 1
