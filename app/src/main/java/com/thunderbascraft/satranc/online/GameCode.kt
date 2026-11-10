package com.thunderbascraft.satranc.online

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** Six-character codes for online games, without easily confused characters (0/O, 1/I). */
object GameCode {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    private val secureRandom: Random by lazy { SecureRandom().asKotlinRandom() }

    fun generate(random: Random = secureRandom): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Cleans up typed or pasted input: upper case, without spaces or dashes. */
    fun normalize(input: String): String =
        input.uppercase().filter { !it.isWhitespace() && it != '-' }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
