package de.ilazlow.velosonic.data.network

import java.security.MessageDigest
import java.util.UUID

/** Classic Subsonic token auth: token = MD5(password + salt). Mirrors NavidromeAuth.swift. */
object NavidromeAuth {

    data class Auth(val token: String, val salt: String)

    fun generateAuth(password: String): Auth {
        val salt = UUID.randomUUID().toString().take(6).lowercase()
        val token = md5Hex(password + salt)
        return Auth(token = token, salt = salt)
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
