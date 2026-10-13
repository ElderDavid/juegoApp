package com.elderdavid.quientienelarazon.core.questions

/** Problema estructural de una pregunta o del banco. */
data class ValidationIssue(val questionId: String?, val message: String)

/** Validaciones estructurales: no comprueban la veracidad, eso exige revisión con fuentes. */
object QuestionValidator {

    fun validate(question: Question, bank: QuestionBank? = null): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        fun issue(msg: String) = issues.add(ValidationIssue(question.id, msg))

        if (question.id.isBlank()) issue("ID vacío")
        if (question.text.isBlank()) issue("Enunciado vacío")
        if (question.explanation.isBlank()) issue("Explicación vacía")
        if (question.options.any { it.isBlank() }) issue("Hay opciones vacías")
        if (question.options.map { it.trim().lowercase() }.toSet().size != question.options.size) {
            issue("Opciones repetidas")
        }
        when (question.format) {
            AnswerFormat.TRUE_FALSE ->
                if (question.options != TRUE_FALSE_OPTIONS) issue("Verdadero/falso debe tener exactamente $TRUE_FALSE_OPTIONS")
            AnswerFormat.MULTIPLE_CHOICE ->
                if (question.options.size !in 3..4) issue("Opción múltiple requiere 3 o 4 opciones")
        }
        question.correctIndex?.let { if (it !in question.options.indices) issue("Índice correcto fuera de rango: $it") }

        when (question.kind) {
            QuestionKind.OBJECTIVE ->
                if (question.evaluation !is Evaluation.CorrectOption) issue("Una pregunta objetiva necesita una respuesta correcta")
            QuestionKind.DEBATE ->
                if (question.evaluation is Evaluation.CorrectOption) issue("Una pregunta de debate no puede presentarse como respuesta factual")
            QuestionKind.OPINION ->
                if (question.evaluation !is Evaluation.MajorityVote) issue("Una pregunta de opinión se evalúa por votación")
        }
        val rule = question.evaluation as? Evaluation.GameRule
        if (rule != null && rule.rule.isBlank()) issue("La regla de juego no puede estar vacía")
        if (question.isVote && question.format == AnswerFormat.TRUE_FALSE) issue("Las votaciones usan opción múltiple")
        if (question.audience == Audience.KIDS) {
            // Para niños solo preguntas con respuesta comprobable: nada de polémica ni de opinión.
            if (question.kind != QuestionKind.OBJECTIVE) issue("Las preguntas para niños deben ser objetivas")
            if (question.regionId != null) issue("Las preguntas para niños no pertenecen a un departamento")
        }

        if (bank != null) {
            if (bank.categories.none { it.id == question.categoryId }) issue("Categoría inexistente: ${question.categoryId}")
            if (question.regionId != null && (bank.regions + bank.countries).none { it.id == question.regionId }) {
                issue("Departamento o país inexistente: ${question.regionId}")
            }
        }
        return issues
    }

    fun validateBank(bank: QuestionBank): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        bank.questions.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            issues.add(ValidationIssue(it, "ID duplicado"))
        }
        bank.questions.forEach { issues += validate(it, bank) }
        return issues
    }
}
