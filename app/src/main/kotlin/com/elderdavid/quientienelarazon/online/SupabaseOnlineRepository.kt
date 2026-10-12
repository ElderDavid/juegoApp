package com.elderdavid.quientienelarazon.online

import com.elderdavid.quientienelarazon.account.AccountRepository
import com.elderdavid.quientienelarazon.core.game.GameLength
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

/** Llama a las funciones de sala del servidor. No guarda estado: la fuente de verdad es el servidor. */
class SupabaseOnlineRepository(
    private val client: SupabaseClient,
    private val accounts: AccountRepository,
) : OnlineRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun ensureSignedIn(): OnlineResult<Unit> =
        if (accounts.ensureSignedIn().isSuccess) OnlineResult.Ok(Unit) else OnlineResult.Failed(OnlineError.NETWORK)

    override suspend fun currentRoom(): OnlineResult<RoomView?> {
        val raw = call { client.postgrest.rpc("current_room").data }
        if (raw !is OnlineResult.Ok) return raw as OnlineResult.Failed
        if (raw.value.isBlank() || raw.value.trim() == "null") return OnlineResult.Ok(null)
        return decode(raw.value)
    }

    override suspend fun createRoom(publicRoom: Boolean, mode: String, length: GameLength) = view("create_room") {
        put("p_visibility", if (publicRoom) "public" else "private")
        put("p_mode", mode)
        put("p_questions", length.questions)
    }

    override suspend fun joinByCode(code: String) = view("join_room_by_code") { put("p_code", code) }

    override suspend fun findPublic(mode: String, length: GameLength) = view("find_public_room") {
        put("p_mode", mode)
        put("p_questions", length.questions)
    }

    override suspend fun state(roomId: String) = view("room_state") { put("p_room", roomId) }

    override suspend fun start(roomId: String) = view("start_room") { put("p_room", roomId) }

    override suspend fun leave(roomId: String): OnlineResult<Unit> = call<Unit> {
        client.postgrest.rpc("leave_room", buildJsonObject { put("p_room", roomId) })
    }

    override suspend fun bet(roomId: String, index: Int, amount: Int) = view("submit_bet") {
        put("p_room", roomId)
        put("p_index", index)
        put("p_amount", amount)
    }

    override suspend fun answer(roomId: String, index: Int, choice: Int, clientElapsedMs: Long) = view("submit_answer") {
        put("p_room", roomId)
        put("p_index", index)
        put("p_choice", choice)
        put("p_client_elapsed_ms", clientElapsedMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }

    private suspend fun view(function: String, params: JsonObjectBuilder.() -> Unit): OnlineResult<RoomView> {
        val raw = call { client.postgrest.rpc(function, buildJsonObject(params)).data }
        if (raw !is OnlineResult.Ok) return raw as OnlineResult.Failed
        return decode(raw.value)
    }

    private fun decode(raw: String): OnlineResult<RoomView> {
        return try {
            val element = json.parseToJsonElement(raw).jsonObject
            // Algunas funciones responden {"error": "..."} sin lanzar excepción (p. ej. código inexistente).
            element["error"]?.jsonPrimitive?.content?.let { return OnlineResult.Failed(OnlineError.from(it)) }
            OnlineResult.Ok(json.decodeFromJsonElement(RoomView.serializer(), element))
        } catch (e: IllegalArgumentException) {
            OnlineResult.Failed(OnlineError.UNKNOWN)
        }
    }

    private suspend fun <T> call(block: suspend () -> T): OnlineResult<T> = try {
        OnlineResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: RestException) {
        // e.error es el mensaje de PostgreSQL: el código que lanzó la función (room_full, time_expired…).
        OnlineResult.Failed(OnlineError.from(e.error))
    } catch (e: HttpRequestException) {
        OnlineResult.Failed(OnlineError.NETWORK)
    } catch (e: HttpRequestTimeoutException) {
        OnlineResult.Failed(OnlineError.NETWORK)
    } catch (e: IOException) {
        OnlineResult.Failed(OnlineError.NETWORK)
    }
}
