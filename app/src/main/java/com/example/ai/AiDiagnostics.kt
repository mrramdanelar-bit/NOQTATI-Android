package com.example.ai

import android.util.Log
import com.google.firebase.FirebaseException

/**
 * Formats actionable Firebase/AI failures for debug builds.
 * Never logs or returns credentials, bearer tokens, or App Check secrets.
 */
object AiDiagnostics {
    fun format(error: Throwable): String {
        val parts = mutableListOf<String>()
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        var depth = 0

        while (current != null && depth < 8 && seen.add(current)) {
            val type = current.javaClass.name
            val rawMessage = current.message.orEmpty()
            val message = redact(rawMessage).ifBlank { "(no message)" }
            parts += "Cause ${depth + 1}: $type\nMessage: $message"

            if (current is FirebaseException) {
                parts += "Firebase errorCode: ${redact(current.errorCode)}"
            }

            current = current.cause
            depth++
        }

        val stack = redact(Log.getStackTraceString(error))
        if (stack.isNotBlank()) {
            parts += "Stack trace:\n$stack"
        }

        return parts.joinToString("\n\n")
    }

    private fun redact(value: String): String {
        return value
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [REDACTED]")
            .replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "[REDACTED_API_KEY]")
            .replace(
                Regex("(?i)(debug[_ -]?token|api[_ -]?key|access[_ -]?token)\\s*[:=]\\s*[^\\s,;]+"),
                "$1=[REDACTED]"
            )
    }
}
