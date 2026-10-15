package com.moneyper5h.app.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object Money {
    const val MAX_CENTS = 99_999_999L
    private val pattern = Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?")
    fun parse(input: String): Long? {
        val value = input.trim()
        if (value.length > 16 || !pattern.matches(value)) return null
        return runCatching { BigDecimal(value).movePointRight(2).longValueExact() }
            .getOrNull()?.takeIf { it in 1..MAX_CENTS }
    }
    fun decimal(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
    fun compact(cents: Long): String = BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()
    fun display(cents: Long): String = "¥${decimal(cents)}"
}

data class UsageRecord(val id: String, val cents: Long, val savedAt: Long) {
    init { require(id.isNotBlank() && cents in 1..Money.MAX_CENTS && savedAt >= 0) }
}

data class Plan(val hundredths: Long, val configuredAt: Long) {
    init { require(hundredths in 1..MAX_HUNDREDTHS && configuredAt >= 0) }
    val expiresAt: Long get() = configuredAt + DURATION
    val fiveHourLimit: Long get() = 5 * hundredths
    val weeklyLimit: Long get() = 35 * hundredths
    val monthlyReference: Long get() = 150 * hundredths
    fun active(now: Long): Boolean = now >= configuredAt && now < expiresAt
    companion object {
        const val MAX_HUNDREDTHS = 666_666L
        const val DURATION = 30 * 86_400_000L
        fun parse(raw: String): Long? = Money.parse(raw)?.takeIf { it <= MAX_HUNDREDTHS }
    }
}

data class Quota(val limit: Long, val used: Long, val end: Long?) {
    val remaining: Long get() = (limit - used).coerceAtLeast(0)
    val fraction: Float get() = if (limit > 0) (remaining.toDouble() / limit).toFloat() else 0f
    val percent: Int get() = if (limit > 0) (remaining * 100 / limit).toInt() else 0
}

object Usage {
    const val FIVE_HOURS = 18_000_000L
    const val WEEK = 604_800_000L
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    fun day(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun quota(records: List<UsageRecord>, limit: Long, duration: Long, now: Long): Quota {
        var end: Long? = null
        var used = 0L
        for (record in records.distinctBy { it.id }.filter { it.savedAt <= now }
            .sortedWith(compareBy<UsageRecord> { it.savedAt }.thenBy { it.id })) {
            if (end == null || record.savedAt >= end) {
                end = record.savedAt + duration
                used = 0
            }
            used = Math.addExact(used, record.cents)
        }
        return if (end == null || now >= end) Quota(limit, 0, null) else Quota(limit, used, end)
    }

    data class Day(val date: LocalDate, val cents: Long, val future: Boolean) {
        val level: Int get() = when { cents == 0L -> 0; cents <= 2_000 -> 1; cents <= 5_000 -> 2; else -> 3 }
    }
    fun heatmap(records: List<UsageRecord>, now: Long): List<Day> {
        val today = day(now)
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
        val totals = records.distinctBy { it.id }.groupBy { day(it.savedAt) }
            .mapValues { (_, rows) -> rows.sumOf { it.cents } }
        return List(84) { index ->
            val date = start.plusDays(index.toLong())
            Day(date, totals[date] ?: 0, date > today)
        }
    }
}
