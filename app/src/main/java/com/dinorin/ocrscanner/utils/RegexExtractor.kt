package com.dinorin.ocrscanner.utils

object RegexExtractor {

    enum class MatchType(val label: String, val emoji: String) {
        PHONE("Phone", "📞"),
        EMAIL("Email", "✉️"),
        URL("URL", "🔗"),
        DATE("Date", "📅"),
        ID_NUMBER("ID/CCCD", "🪪"),
        NUMBER("Number", "#")
    }

    data class ExtractedMatch(
        val type: MatchType,
        val value: String
    )

    // Vietnamese phone numbers: 0xxx or +84xxx
    private val phoneRegex = Regex(
        """(\+84|0)(3[2-9]|5[25689]|7[06-9]|8[0-9]|9[0-9])\d{7}\b"""
    )

    // Email
    private val emailRegex = Regex(
        """[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}"""
    )

    // URLs starting with http/https
    private val urlRegex = Regex(
        """https?://[^\s\],)>"']+"""
    )

    // Dates: dd/mm/yyyy, dd-mm-yyyy, dd.mm.yyyy and variations
    private val dateRegex = Regex(
        """\b(0?[1-9]|[12][0-9]|3[01])[/\-.](0?[1-9]|1[0-2])[/\-.](19|20)\d{2}\b"""
    )

    // Vietnamese ID / CCCD: 9 or 12 digits
    private val idNumberRegex = Regex(
        """\b(\d{9}|\d{12})\b"""
    )

    // Generic numbers (4+ digits, fallback)
    private val numberRegex = Regex(
        """\b\d{4,}\b"""
    )

    fun extract(text: String): List<ExtractedMatch> {
        val results = mutableListOf<ExtractedMatch>()
        val usedRanges = mutableListOf<IntRange>()

        fun addMatches(regex: Regex, type: MatchType) {
            regex.findAll(text).forEach { match ->
                val range = match.range
                val overlaps = usedRanges.any { it.first <= range.last && range.first <= it.last }
                if (!overlaps) {
                    results.add(ExtractedMatch(type, match.value.trim()))
                    usedRanges.add(range)
                }
            }
        }

        addMatches(phoneRegex, MatchType.PHONE)
        addMatches(emailRegex, MatchType.EMAIL)
        addMatches(urlRegex, MatchType.URL)
        addMatches(dateRegex, MatchType.DATE)
        addMatches(idNumberRegex, MatchType.ID_NUMBER)

        // Only add generic numbers if no specific matches found
        if (results.isEmpty()) {
            addMatches(numberRegex, MatchType.NUMBER)
        }

        return results.distinctBy { it.value }
    }
}
