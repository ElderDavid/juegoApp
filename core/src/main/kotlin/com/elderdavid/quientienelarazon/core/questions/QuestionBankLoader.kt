package com.elderdavid.quientienelarazon.core.questions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Carga el banco desde los JSON de `resources/questions`. Para añadir un departamento basta con
 * añadir su archivo y registrarlo en `index.json`: el motor no cambia.
 */
object QuestionBankLoader {
    private val json = Json { ignoreUnknownKeys = false }

    /** [read] devuelve el contenido de un archivo dentro de `questions/`. */
    fun load(read: (fileName: String) -> String): QuestionBank {
        val index = json.decodeFromString<IndexDto>(read("index.json"))
        val questions = index.files.flatMap { file ->
            json.decodeFromString<List<QuestionDto>>(read(file)).map { it.toDomain() }
        }
        return QuestionBank(
            questions = questions,
            categories = index.categories.map { Category(it.id, it.name) },
            regions = index.regions.map { Region(it.id, it.name) },
        )
    }

    /** Carga desde los recursos empaquetados con el módulo (funciona en JVM y Android). */
    fun fromResources(): QuestionBank = load { name ->
        val stream = QuestionBankLoader::class.java.getResourceAsStream("/questions/$name")
            ?: error("No se encontró el recurso questions/$name")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Serializable
    private data class IndexDto(
        val version: Int,
        val categories: List<NamedDto>,
        val regions: List<NamedDto>,
        val files: List<String>,
    )

    @Serializable
    private data class NamedDto(val id: String, val name: String)

    @Serializable
    private data class EvaluationDto(val type: String, val index: Int? = null, val rule: String? = null)

    @Serializable
    private data class QuestionDto(
        val id: String,
        val text: String,
        val options: List<String>,
        val format: AnswerFormat,
        val kind: QuestionKind,
        val evaluation: EvaluationDto,
        @SerialName("category") val categoryId: String,
        val difficulty: Difficulty,
        @SerialName("region") val regionId: String? = null,
        val explanation: String,
        val source: String? = null,
        val referenceDate: String? = null,
        val audience: Audience = Audience.ALL,
    ) {
        fun toDomain() = Question(
            id = id,
            text = text,
            options = options,
            format = format,
            kind = kind,
            evaluation = when (evaluation.type) {
                "correct" -> Evaluation.CorrectOption(requireNotNull(evaluation.index) { "$id: falta index" })
                "rule" -> Evaluation.GameRule(
                    requireNotNull(evaluation.index) { "$id: falta index" },
                    requireNotNull(evaluation.rule) { "$id: falta la regla" },
                )
                "vote" -> Evaluation.MajorityVote
                else -> error("$id: tipo de evaluación desconocido ${evaluation.type}")
            },
            categoryId = categoryId,
            difficulty = difficulty,
            regionId = regionId,
            explanation = explanation,
            source = source,
            referenceDate = referenceDate,
            audience = audience,
        )
    }
}
