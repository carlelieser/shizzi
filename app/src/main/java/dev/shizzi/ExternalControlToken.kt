package dev.shizzi

import java.security.SecureRandom

object ExternalControlToken {

    private const val LENGTH = 24
    private const val ALPHABET = "abcdefghijkmnopqrstuvwxyz23456789"

    fun generate(): String {
        val random = SecureRandom()
        return (1..LENGTH)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString("")
    }
}
