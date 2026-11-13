package com.jarvis.assistant.util

import java.util.Locale

/**
 * Pure Kotlin matcher for voice commands that request Siya AI to close the active popup
 * and enter standby mode.
 *
 * Supports English, Hinglish, and Hindi (Devanagari script).
 * Uses whole-word boundary matching with Unicode letters, marks, and numbers ([\p{L}\p{M}\p{N}])
 * so that Devanagari vowel signs (matras, \p{M}) are respected and do not break boundaries.
 */
object SiyaCloseCommands {

    private val CLOSE_PHRASES = listOf(
        // English / Hinglish
        "sleep",
        "so jao",
        "band ho jao",
        "sleep mode",
        "chale jao",
        "band karo",
        "bye",
        "good night",
        // Devanagari Hindi
        "सो जाओ",
        "बंद हो जाओ",
        "बंद करो",
        "चले जाओ",
        "शुभ रात्रि"
    )

    /**
     * Normalizes input text: converts to lowercase, collapses whitespace,
     * and strips punctuation while preserving letters, marks, and numbers.
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val lower = text.lowercase(Locale.ROOT).trim()
        // Replace punctuation characters with space, keeping \p{L} (letters), \p{M} (marks/matras), \p{N} (numbers), and whitespace
        val stripped = lower.replace(Regex("""[^\p{L}\p{M}\p{N}\s]"""), " ")
        // Collapse multiple spaces into one
        return stripped.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Checks if the normalized utterance is a valid close command.
     *
     * To prevent accidental closures in long conversational sentences (e.g. "tell him bye" or "sleep is important"),
     * an utterance is considered a close command only when:
     * 1. It exactly matches one of the recognized close phrases, OR
     * 2. The utterance contains at most 5 words AND contains one of the recognized close phrases with proper word boundaries.
     */
    fun isCloseCommand(rawText: String?): Boolean {
        val normalized = normalize(rawText)
        if (normalized.isEmpty()) return false

        // Check exact match first
        for (phrase in CLOSE_PHRASES) {
            if (normalized == phrase) return true
        }

        // Limit utterance length to at most 5 words to prevent false positives in continuous speech
        val words = normalized.split(' ').filter { it.isNotBlank() }
        if (words.size > 5) return false

        // Match with whole-word boundary
        for (phrase in CLOSE_PHRASES) {
            val pattern = Regex("""(?<![\p{L}\p{M}\p{N}])""" + Regex.escape(phrase) + """(?![\p{L}\p{M}\p{N}])""")
            if (pattern.containsMatchIn(normalized)) {
                return true
            }
        }

        return false
    }
}
