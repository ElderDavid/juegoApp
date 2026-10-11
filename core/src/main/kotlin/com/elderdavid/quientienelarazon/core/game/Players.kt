package com.elderdavid.quientienelarazon.core.game

@JvmInline
value class PlayerId(val value: String)

data class Player(val id: PlayerId, val name: String, val avatarId: Int = 0)
