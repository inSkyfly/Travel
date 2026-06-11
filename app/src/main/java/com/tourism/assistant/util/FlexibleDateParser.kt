package com.tourism.assistant.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 智能解析用户输入的出行日期范围，支持多种口语化/简写格式。
 */
object FlexibleDateParser {

    private val chineseFullRange = Regex(
        """(\d{4})年(\d{1,2})月(\d{1,2})[号日]?[到至~—–－\-/\\]+(\d{4})年(\d{1,2})月(\d{1,2})[号日]?"""
    )
    private val chineseStartOnlyRange = Regex(
        """(\d{4})年(\d{1,2})月(\d{1,2})[号日]?[到至~—–－\-/\\]+(\d{1,2})月(\d{1,2})[号日]?"""
    )
    private val chineseNoYearRange = Regex(
        """(\d{1,2})月(\d{1,2})[号日]?[到至~—–－\-/\\]+(\d{1,2})月(\d{1,2})[号日]?"""
    )
    private val compact8Range = Regex("""(\d{8})[-~到至—–－/\\]+(\d{8})""")
    private val compact4Range = Regex("""(?<!\d)(\d{4})[-~到至—–－/\\]+(\d{4})(?!\d)""")
    private val isoDoubleRange = Regex(
        """(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})[-~到至—–－/\\]+(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})"""
    )
    private val isoSingleRange = Regex(
        """(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})[到至~—–－/\\]+(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})"""
    )

    fun parseRange(input: String, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate>? {
        val text = normalize(input)
        if (text.isBlank()) return null

        parseByRegex(text)?.let { return it }
        parseBySplit(text, today)?.let { return it }

        return null
    }

    fun formatRange(start: LocalDate, end: LocalDate): String {
        return "${start.year}年${start.monthValue}月${start.dayOfMonth}日 至 " +
            "${end.year}年${end.monthValue}月${end.dayOfMonth}日"
    }

    private fun normalize(input: String): String {
        return input.trim()
            .replace(" ", "")
            .replace("　", "")
            .replace("—", "-")
            .replace("–", "-")
            .replace("－", "-")
            .replace("~", "-")
            .replace("号", "日")
    }

    private fun parseByRegex(text: String): Pair<LocalDate, LocalDate>? {
        chineseFullRange.matchEntire(text)?.let { m ->
            return safeRange(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt()
            )
        }

        chineseStartOnlyRange.matchEntire(text)?.let { m ->
            val year = m.groupValues[1].toInt()
            return safeRange(
                year, m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                year, m.groupValues[4].toInt(), m.groupValues[5].toInt()
            )
        }

        chineseNoYearRange.matchEntire(text)?.let { m ->
            val startYear = inferYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), LocalDate.now())
            val endYear = if (
                m.groupValues[3].toInt() < m.groupValues[1].toInt() ||
                (m.groupValues[3].toInt() == m.groupValues[1].toInt() &&
                    m.groupValues[4].toInt() < m.groupValues[2].toInt())
            ) {
                startYear + 1
            } else {
                startYear
            }
            return safeRange(
                startYear, m.groupValues[1].toInt(), m.groupValues[2].toInt(),
                endYear, m.groupValues[3].toInt(), m.groupValues[4].toInt()
            )
        }

        compact8Range.matchEntire(text)?.let { m ->
            return parseYyyyMmDd(m.groupValues[1])?.let { start ->
                parseYyyyMmDd(m.groupValues[2])?.let { end -> validateRange(start, end) }
            }
        }

        isoDoubleRange.matchEntire(text)?.let { m ->
            return safeRange(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt()
            )
        }

        isoSingleRange.matchEntire(text)?.let { m ->
            return safeRange(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt()
            )
        }

        compact4Range.matchEntire(text)?.let { m ->
            val today = LocalDate.now()
            val startMd = m.groupValues[1]
            val endMd = m.groupValues[2]
            val startYear = inferYear(startMd.substring(0, 2).toInt(), startMd.substring(2, 4).toInt(), today)
            val endYear = inferYear(endMd.substring(0, 2).toInt(), endMd.substring(2, 4).toInt(), today, startYear)
            return safeRange(
                startYear, startMd.substring(0, 2).toInt(), startMd.substring(2, 4).toInt(),
                endYear, endMd.substring(0, 2).toInt(), endMd.substring(2, 4).toInt()
            )
        }

        return null
    }

    private fun parseBySplit(text: String, today: LocalDate): Pair<LocalDate, LocalDate>? {
        val parts = text.split(Regex("[到至]+")).filter { it.isNotBlank() }
        if (parts.size != 2) return null

        val start = parseToken(parts[0], today) ?: return null
        val end = parseToken(parts[1], today, fallbackYear = start.year) ?: return null
        return validateRange(start, end)
    }

    private fun parseToken(raw: String, today: LocalDate, fallbackYear: Int? = null): LocalDate? {
        val token = raw.trim().trim('-', '/', '.', '\\')
        if (token.isEmpty()) return null

        parseYyyyMmDd(token)?.let { return it }

        if (token.matches(Regex("""\d{4}[-/.]\d{1,2}[-/.]\d{1,2}"""))) {
            return parseIsoLike(token)
        }

        val chineseSingle = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日?""").matchEntire(token)
        if (chineseSingle != null) {
            return safeDate(
                chineseSingle.groupValues[1].toInt(),
                chineseSingle.groupValues[2].toInt(),
                chineseSingle.groupValues[3].toInt()
            )
        }

        val chineseNoYear = Regex("""(\d{1,2})月(\d{1,2})日?""").matchEntire(token)
        if (chineseNoYear != null) {
            val year = fallbackYear ?: inferYear(
                chineseNoYear.groupValues[1].toInt(),
                chineseNoYear.groupValues[2].toInt(),
                today
            )
            return safeDate(
                year,
                chineseNoYear.groupValues[1].toInt(),
                chineseNoYear.groupValues[2].toInt()
            )
        }

        if (token.length == 4 && token.all { it.isDigit() }) {
            val month = token.substring(0, 2).toInt()
            val day = token.substring(2, 4).toInt()
            val year = fallbackYear ?: inferYear(month, day, today)
            return safeDate(year, month, day)
        }

        return parseIsoLike(token)
    }

    private fun parseYyyyMmDd(token: String): LocalDate? {
        if (!token.matches(Regex("""\d{8}"""))) return null
        return safeDate(
            token.substring(0, 4).toInt(),
            token.substring(4, 6).toInt(),
            token.substring(6, 8).toInt()
        )
    }

    private fun parseIsoLike(token: String): LocalDate? {
        val normalized = token.replace('/', '-').replace('.', '-')
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )
        for (formatter in formatters) {
            try {
                return LocalDate.parse(normalized, formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun inferYear(month: Int, day: Int, today: LocalDate, minYear: Int? = null): Int {
        var year = maxOf(today.year, minYear ?: today.year)
        repeat(3) {
            val candidate = safeDate(year, month, day) ?: return today.year
            if (candidate.isBefore(today.minusDays(14))) {
                year += 1
            } else {
                return year
            }
        }
        return year
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? {
        return try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun safeRange(
        startYear: Int, startMonth: Int, startDay: Int,
        endYear: Int, endMonth: Int, endDay: Int
    ): Pair<LocalDate, LocalDate>? {
        val start = safeDate(startYear, startMonth, startDay) ?: return null
        val end = safeDate(endYear, endMonth, endDay) ?: return null
        return validateRange(start, end)
    }

    private fun validateRange(start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate>? {
        if (end.isBefore(start)) return null
        return start to end
    }
}
