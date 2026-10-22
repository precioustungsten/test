package com.openear.app.model

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun hex(file: File): String = file.inputStream().use { hex(it) }

    fun hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, expectedHex: String): Boolean =
        file.isFile && hex(file).equals(expectedHex.trim(), ignoreCase = true)
}
