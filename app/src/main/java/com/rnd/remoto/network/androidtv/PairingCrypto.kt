package com.rnd.remoto.network.androidtv

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Pairing-secret derivation for the Android TV Remote v2 pairing PIN, ported field-for-field
 * from tronikos/androidtvremote2's pairing.py (async_finish_pairing):
 *
 *   h = sha256()
 *   h.update(hex(client_modulus))            # no padding
 *   h.update("0" + hex(client_exponent))      # always one leading zero nibble
 *   h.update(hex(server_modulus))
 *   h.update("0" + hex(server_exponent))
 *   h.update(hex(pin[2:6]))                   # last 4 hex chars (2 bytes) of the 6-char PIN
 *   secret = h.digest()
 *
 * pin[0:2] (as a hex byte) must equal secret[0] - the TV performs the same check.
 */
object PairingCrypto {

    fun computeSecret(
        pin: String,
        clientModulus: BigInteger,
        clientExponent: BigInteger,
        serverModulus: BigInteger,
        serverExponent: BigInteger
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(modulusBytes(clientModulus))
        digest.update(exponentBytes(clientExponent))
        digest.update(modulusBytes(serverModulus))
        digest.update(exponentBytes(serverExponent))
        digest.update(hexToBytes(pin.substring(2, 6)))
        return digest.digest()
    }

    fun expectedFirstByte(pin: String): Int = pin.substring(0, 2).toInt(16)

    private fun modulusBytes(value: BigInteger): ByteArray = hexToBytes(value.toString(16))

    private fun exponentBytes(value: BigInteger): ByteArray = hexToBytes("0" + value.toString(16))

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Longitud hexadecimal impar: $hex" }
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Carácter hexadecimal inválido en: $hex" }
            result[i] = ((hi shl 4) + lo).toByte()
        }
        return result
    }
}
