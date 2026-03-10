package com.yourname.communique
import android.util.Base64

object SecretDecoder {
    fun decode(obfuscated: String): String {
        if (obfuscated.isEmpty()) return ""
        return try {
            val decodedBytes = Base64.decode(obfuscated, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8).reversed()
        } catch (e: Exception) {
            ""
        }
    }
}
