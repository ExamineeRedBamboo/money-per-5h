package com.moneyper5h.app.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class UsageTest {
    private val start = Instant.parse("2026-10-15T02:20:00Z").toEpochMilli()
    private fun record(id: String, cents: Long = 100, time: Long = start) = UsageRecord(id, cents, time)
    private fun quota(rows: List<UsageRecord>, now: Long = start, duration: Long = Usage.FIVE_HOURS, limit: Long = 5_000) = Usage.quota(rows, limit, duration, now)

    @Test fun exactCentsAndStrictInput() {
        assertEquals(1L, Money.parse("0.01"))
        assertEquals(1234L, Money.parse("12.34"))
        assertEquals(Money.MAX_CENTS, Money.parse("999999.99"))
        listOf("", "0", "-1", "01", ".1", "1.", "1.001", "1e2", "NaN", "1000000", "999999999999999999999999999").forEach { assertNull(it, Money.parse(it)) }
        assertEquals("1.01", Money.decimal(101))
        assertEquals("1.5", Money.compact(150))
    }
    @Test fun planUsesIntegerHundredthsAndExactThirtyDays() {
        val p = Plan(150, start)
        assertEquals(750L, p.fiveHourLimit)
        assertEquals(5250L, p.weeklyLimit)
        assertEquals(22500L, p.monthlyReference)
        assertEquals(start + 720 * 3_600_000L, p.expiresAt)
        assertTrue(p.active(p.expiresAt - 1))
        assertFalse(p.active(p.expiresAt))
        assertFalse(p.active(start - 1))
    }
    @Test fun multiplierBoundaries() {
        assertEquals(1L, Plan.parse("0.01"))
        assertEquals(666666L, Plan.parse("6666.66"))
        listOf("6666.67", "0", "-1", "1.234").forEach { assertNull(Plan.parse(it)) }
        val p = Plan(1000, start)
        assertEquals(5000L, p.fiveHourLimit)
        assertEquals(35000L, p.weeklyLimit)
        assertEquals(150000L, p.monthlyReference)
    }
    @Test fun emptyHasNoClockAndFullRemaining() {
        val q = quota(emptyList())
        assertNull(q.end)
        assertEquals(0L, q.used)
        assertEquals(100, q.percent)
    }
    @Test fun firstRecordStartsFixedWindowLaterOnesDoNotExtendIt() {
        val q = quota(listOf(record("a"), record("b", 200, start + 1_000)), start + 1_000)
        assertEquals(start + Usage.FIVE_HOURS, q.end)
        assertEquals(300L, q.used)
        assertEquals(94, q.percent)
    }
    @Test fun exactBoundaryOpensNewWindow() {
        val boundary = start + Usage.FIVE_HOURS
        val q = quota(listOf(record("a"), record("b", 200, boundary)), boundary)
        assertEquals(boundary + Usage.FIVE_HOURS, q.end)
        assertEquals(200L, q.used)
        assertNull(quota(listOf(record("a")), boundary).end)
    }
    @Test fun idleDoesNotCreateEmptyWindows() {
        val late = start + Usage.WEEK + 12345
        val q = quota(listOf(record("a"), record("b", 200, late)), late)
        assertEquals(late + Usage.FIVE_HOURS, q.end)
        assertEquals(200L, q.used)
    }
    @Test fun twoDurationsResetIndependently() {
        val now = start + Usage.FIVE_HOURS
        val rows = listOf(record("a", 1000), record("b", 2000, now))
        assertEquals(2000L, quota(rows, now).used)
        val week = quota(rows, now, Usage.WEEK)
        assertEquals(3000L, week.used)
        assertEquals(start + Usage.WEEK, week.end)
    }
    @Test fun excessNeverStartsNewWindow() {
        val q = quota(listOf(record("a", 9000)))
        assertEquals(0L, q.remaining)
        assertEquals(0, q.percent)
        assertEquals(start + Usage.FIVE_HOURS, q.end)
        assertEquals(9000L, q.used)
    }
    @Test fun sameAmountSeparateIdsAndReplayDedup() {
        val a = record("a")
        val b = record("b")
        assertEquals(200L, quota(listOf(a, b, a)).used)
    }
    @Test fun deletionReplaysWindowFromRemainingRecords() {
        val second = record("b", 200, start + 1_000)
        val third = record("c", 300, start + Usage.FIVE_HOURS)
        assertEquals(300L, quota(listOf(record("a"), second, third), third.savedAt).used)
        val q = quota(listOf(second, third), third.savedAt)
        assertEquals(500L, q.used)
        assertEquals(second.savedAt + Usage.FIVE_HOURS, q.end)
    }
    @Test fun changedLimitKeepsUsedAndEnd() {
        val rows = listOf(record("a", 1000))
        val a = quota(rows)
        val b = quota(rows, limit = 2000)
        assertEquals(a.used, b.used)
        assertEquals(a.end, b.end)
        assertEquals(50, b.percent)
    }
    @Test fun futureTimeDoesNotConsumeCurrentWindow() {
        assertEquals(0L, quota(listOf(record("a", time = start + 1))).used)
    }
    @Test fun midnightUsesBeijingCalendarDay() {
        val before = Instant.parse("2026-10-15T15:59:59.999Z").toEpochMilli()
        val after = before + 1
        assertEquals("2026-10-15", Usage.day(before).toString())
        assertEquals("2026-10-16", Usage.day(after).toString())
        val grid = Usage.heatmap(listOf(record("a", 2000, before), record("b", 2001, after)), after)
        assertEquals(2000L, grid.first { it.date.toString() == "2026-10-15" }.cents)
        assertEquals(2001L, grid.first { it.date.toString() == "2026-10-16" }.cents)
    }
    @Test fun heatmapIsTwelveMondayWeeksWithFutureCells() {
        val grid = Usage.heatmap(emptyList(), start)
        assertEquals(84, grid.size)
        assertEquals(1, grid.first().date.dayOfWeek.value)
        assertEquals(7, grid.last().date.dayOfWeek.value)
        assertEquals(3, grid.count { it.future })
        assertTrue(grid.all { it.level == 0 })
    }
    @Test fun heatmapColorsAreAmountBasedAndOlderRecordsRemain() {
        val old = record("old", 999, start - Usage.WEEK * 20)
        val rows = listOf(old, record("a", 2000), record("b", 1))
        val day = Usage.heatmap(rows, start).first { it.date == Usage.day(start) }
        assertEquals(2001L, day.cents)
        assertEquals(2, day.level)
        assertEquals(1, Usage.Day(day.date, 2000, false).level)
        assertEquals(2, Usage.Day(day.date, 5000, false).level)
        assertEquals(3, Usage.Day(day.date, 5001, false).level)
        assertEquals(3, rows.size)
    }
}
