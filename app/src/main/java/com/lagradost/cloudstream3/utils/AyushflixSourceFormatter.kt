package com.lagradost.cloudstream3.utils

object AyushflixSourceFormatter {
    /**
     * Formats any source name during playback according to Ayushflix branding rules:
     * - Castle TV -> "Ayushflix"
     * - Netflix / Netflix Mirror / NetflixM -> "Ayushflix - Netflix"
     * - Prime Video / Amazon -> "Ayushflix - Prime Video"
     * - Hotstar / JioHotstar -> "Ayushflix - Hotstar"
     * - Disney -> "Ayushflix - Disney"
     * - Any other source -> "Ayushflix - [Source]"
     */
    fun formatSourceName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "Ayushflix"
        val trimmed = rawName.trim()
        val lower = trimmed.lowercase()

        // Castle: show only Ayushflix
        if (lower.contains("castle")) {
            return "Ayushflix"
        }

        // Netflix
        if (lower.contains("netflix")) {
            return "Ayushflix - Netflix"
        }

        // Prime Video
        if (lower.contains("prime") || lower.contains("amazon")) {
            return "Ayushflix - Prime Video"
        }

        // Hotstar
        if (lower.contains("hotstar") || lower.contains("jiohotstar") || lower.contains("jio hotstar")) {
            return "Ayushflix - Hotstar"
        }

        // Disney
        if (lower.contains("disney")) {
            return "Ayushflix - Disney"
        }

        // Already formatted
        if (lower.startsWith("ayushflix - ") || lower == "ayushflix") {
            return trimmed
        }

        // Clean up common provider suffixes
        val cleaned = trimmed
            .replace(Regex("(?i)mirror"), "")
            .replace(Regex("(?i)provider"), "")
            .replace(Regex("(?i)mobile"), "")
            .trim()
            .trim('-', '_', ' ')

        return if (cleaned.isBlank() || cleaned.equals("ayushflix", ignoreCase = true)) {
            "Ayushflix"
        } else {
            "Ayushflix - $cleaned"
        }
    }
}
