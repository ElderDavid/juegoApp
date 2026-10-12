package com.elderdavid.quientienelarazon.core.questions

/** Modalidad de contenido de la partida. */
sealed interface ContentMode {
    data object General : ContentMode
    data class Regional(val regionId: String) : ContentMode
}

class QuestionBank(
    val questions: List<Question>,
    val categories: List<Category>,
    val regions: List<Region>,
) {
    private val byId = questions.associateBy { it.id }
    private val categoriesById = categories.associateBy { it.id }

    fun question(id: String): Question = byId[id] ?: error("Pregunta desconocida: $id")
    fun category(id: String): Category = categoriesById[id] ?: error("Categoría desconocida: $id")

    fun pool(mode: ContentMode): List<Question> = when (mode) {
        ContentMode.General -> questions.filter { it.regionId == null }
        is ContentMode.Regional -> questions.filter { it.regionId == mode.regionId }
    }
}
