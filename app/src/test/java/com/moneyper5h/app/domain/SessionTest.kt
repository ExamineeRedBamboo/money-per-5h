package com.moneyper5h.app.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SessionTest {
    private val start = Instant.parse("2026-10-15T02:20:00Z").toEpochMilli()
    private var now = start
    private var elapsed = 100_000L
    private var sequence = 0
    private var s = Session(ledger = Ledger(plan = Plan(1000, start)), foreground = true)
    private fun act(action: Action) { s = SessionReducer.reduce(s, action, now, elapsed) { "id-${++sequence}" } }
    private fun advance(ms: Long) { now += ms; elapsed += ms; act(Action.Tick) }
    private fun add(cents: Long = 100) = act(Action.Add(cents))
    private fun direct(value: String, id: String = "manual") {
        act(Action.Open(Dialog.AMOUNT)); act(Action.Input(value)); act(Action.ConfirmAmount(id))
    }
    private fun expire() { now = s.ledger.plan!!.expiresAt; act(Action.Tick) }
    private fun renew(value: String = "10") { act(Action.Open(Dialog.PLAN)); act(Action.ConfirmPlan(value)) }

    @Test fun allDenominationsSumExactlyAndCommitOnceAtLastTapPlusThreeSeconds() {
        SessionReducer.denominations.forEach { add(it); advance(100) }
        val sum = SessionReducer.denominations.sum()
        assertEquals(sum, s.ledger.draft.cents)
        assertTrue(s.ledger.records.isEmpty())
        advance(2899)
        assertTrue(s.ledger.records.isEmpty())
        advance(1)
        assertEquals(sum, s.ledger.records.single().cents)
        assertEquals(now, s.ledger.records.single().savedAt)
        advance(10000)
        assertEquals(1, s.ledger.records.size)
    }
    @Test fun repeatedSameAmountIsTwoRecords() {
        add(); advance(3000); add(); advance(3000)
        assertEquals(2, s.ledger.records.size)
        assertEquals(2, s.ledger.records.map { it.id }.distinct().size)
    }
    @Test fun undoRemovesExactLastAddition() {
        add(1000); add(5); add(1); act(Action.UndoTap)
        assertEquals(1005L, s.ledger.draft.cents)
        advance(3000)
        assertEquals(1005L, s.ledger.records.single().cents)
    }
    @Test fun clearCancelsPendingSave() {
        add(); advance(2999); act(Action.Clear); advance(10000)
        assertTrue(s.ledger.records.isEmpty())
        assertNull(s.deadline)
    }
    @Test fun inputWaitsForConfirmationAndReplacesDraftOnce() {
        add(1000); advance(1000)
        act(Action.Open(Dialog.AMOUNT)); act(Action.Input("12.34")); advance(10000)
        assertTrue(s.ledger.records.isEmpty())
        act(Action.ConfirmAmount("a")); act(Action.ConfirmAmount("b"))
        assertEquals(1234L, s.ledger.records.single().cents)
        assertEquals(0L, s.ledger.draft.cents)
    }
    @Test fun invalidInputCannotSave() {
        direct("1.001")
        assertTrue(s.ledger.records.isEmpty())
        assertEquals(Dialog.AMOUNT, s.dialog)
    }
    @Test fun inputCancelRestoresOriginalDraftAndRemainingWait() {
        add(500); advance(1000)
        act(Action.Open(Dialog.AMOUNT)); act(Action.Input("99")); advance(10000); act(Action.Close)
        advance(1999); assertTrue(s.ledger.records.isEmpty())
        advance(1); assertEquals(500L, s.ledger.records.single().cents)
    }
    @Test fun recordsPagePausesDraftAndReturnResumesRemaining() {
        add(); advance(1000); act(Action.Navigate(Page.RECORDS)); advance(100000)
        assertTrue(s.ledger.records.isEmpty())
        act(Action.Navigate(Page.USAGE)); advance(1999)
        assertTrue(s.ledger.records.isEmpty())
        advance(1); assertEquals(100L, s.ledger.records.single().cents)
    }
    @Test fun backgroundFreezesDraftEvenWhenElapsedPassesDeadline() {
        add(); advance(1000); act(Action.Foreground(false)); advance(100000)
        assertTrue(s.ledger.records.isEmpty())
        act(Action.Foreground(true)); advance(2000)
        assertEquals(1, s.ledger.records.size)
    }
    @Test fun monotonicWaitDoesNotFollowWallClockJump() {
        add(); now += 3600000; elapsed += 1000; act(Action.Tick)
        assertTrue(s.ledger.records.isEmpty())
        elapsed += 2000; act(Action.Tick)
        assertEquals(now, s.ledger.records.single().savedAt)
    }
    @Test fun unsetAndExpiredPlansBlockAllNewRecords() {
        s = Session(foreground = true)
        add(); direct("10"); assertTrue(s.ledger.records.isEmpty())
        renew(); expire(); add(); direct("10"); assertTrue(s.ledger.records.isEmpty())
    }
    @Test fun expiryWinsAtExactCountdownCommitTime() {
        now = s.ledger.plan!!.expiresAt - 3000
        add(); advance(3000)
        assertTrue(s.ledger.records.isEmpty())
        assertTrue(s.ledger.draft.held)
        assertEquals(100L, s.ledger.draft.cents)
    }
    @Test fun expiryDuringInputRetainsTypedValueAndBlocksConfirm() {
        add(500); act(Action.Open(Dialog.AMOUNT)); act(Action.Input("12.34")); expire()
        act(Action.ConfirmAmount("blocked"))
        assertTrue(s.ledger.records.isEmpty())
        assertEquals(1234L, s.ledger.draft.cents)
        assertEquals("12.34", s.ledger.draft.input)
        assertTrue(s.ledger.draft.held)
    }
    @Test fun renewalDoesNotSaveHeldDraftUntilNextEdit() {
        add(); expire(); renew(); advance(100000)
        assertTrue(s.ledger.records.isEmpty())
        assertTrue(s.ledger.draft.held)
        add(5); advance(3000)
        assertEquals(105L, s.ledger.records.single().cents)
    }
    @Test fun renewalDoesNotDeleteHistoryOrResetWindows() {
        direct("10")
        val first = s.ledger.records.single()
        advance(1000); renew("25")
        val q = Usage.quota(s.ledger.records, s.ledger.plan!!.fiveHourLimit, Usage.FIVE_HOURS, now)
        assertEquals(listOf(first), s.ledger.records)
        assertEquals(first.savedAt + Usage.FIVE_HOURS, q.end)
        assertEquals(1000L, q.used)
        assertEquals(now + Plan.DURATION, s.ledger.plan!!.expiresAt)
    }
    @Test fun increasingMultiplierRecalculatesBothRemainingQuotasWithoutResettingUsageOrTime() {
        direct("20")
        fun quotas() = listOf(
            Usage.quota(s.ledger.records, s.ledger.plan!!.fiveHourLimit, Usage.FIVE_HOURS, now),
            Usage.quota(s.ledger.records, s.ledger.plan!!.weeklyLimit, Usage.WEEK, now),
        )
        val before = quotas()
        assertEquals(listOf(60, 94), before.map { it.percent })
        advance(60_000); renew("20")
        val after = quotas()
        assertEquals(listOf(10_000L, 70_000L), after.map { it.limit })
        assertEquals(listOf(2_000L, 2_000L), after.map { it.used })
        assertEquals(before.map { it.end }, after.map { it.end })
        assertEquals(listOf(80, 97), after.map { it.percent })
    }
    @Test fun changingMultiplierPreservesIndependentFiveHourAndWeeklyAnchors() {
        direct("10", "first")
        advance(Usage.FIVE_HOURS + 1000)
        direct("20", "second")
        val fiveEnd = now + Usage.FIVE_HOURS
        val weekEnd = start + Usage.WEEK
        renew("25")
        val five = Usage.quota(s.ledger.records, s.ledger.plan!!.fiveHourLimit, Usage.FIVE_HOURS, now)
        val week = Usage.quota(s.ledger.records, s.ledger.plan!!.weeklyLimit, Usage.WEEK, now)
        assertEquals(2000L, five.used)
        assertEquals(3000L, week.used)
        assertEquals(fiveEnd, five.end)
        assertEquals(weekEnd, week.end)
    }
    @Test fun decreasingMultiplierCanExhaustQuotaButNeverEraseUsage() {
        direct("20")
        val fiveEnd = start + Usage.FIVE_HOURS
        val weekEnd = start + Usage.WEEK
        renew("1")
        val five = Usage.quota(s.ledger.records, s.ledger.plan!!.fiveHourLimit, Usage.FIVE_HOURS, now)
        val week = Usage.quota(s.ledger.records, s.ledger.plan!!.weeklyLimit, Usage.WEEK, now)
        assertEquals(2000L, five.used)
        assertEquals(2000L, week.used)
        assertEquals(0, five.percent)
        assertEquals(42, week.percent)
        assertEquals(fiveEnd, five.end)
        assertEquals(weekEnd, week.end)
    }
    @Test fun septemberNineteenthUpgradeStartsThirtyDayPlanButKeepsCurrentQuotaWindows() {
        val septemberSecond = Instant.parse("2026-09-02T02:20:00Z").toEpochMilli()
        now = Instant.parse("2026-09-19T02:20:00Z").toEpochMilli()
        s = Session(ledger = Ledger(plan = Plan(1000, septemberSecond)), foreground = true)
        direct("20", "current")
        val fiveEnd = now + Usage.FIVE_HOURS
        val weekEnd = now + Usage.WEEK
        renew("20")
        assertEquals(Instant.parse("2026-10-19T02:20:00Z").toEpochMilli(), s.ledger.plan!!.expiresAt)
        assertEquals(fiveEnd, Usage.quota(s.ledger.records, s.ledger.plan!!.fiveHourLimit, Usage.FIVE_HOURS, now).end)
        assertEquals(weekEnd, Usage.quota(s.ledger.records, s.ledger.plan!!.weeklyLimit, Usage.WEEK, now).end)
    }
    @Test fun cancelPlanDoesNotRenewAndDuplicateConfirmDoesNotRenewTwice() {
        val original = s.ledger.plan
        advance(1000); act(Action.Open(Dialog.PLAN)); act(Action.Close)
        assertEquals(original, s.ledger.plan)
        renew(); val confirmed = s.ledger.plan
        advance(1000); act(Action.ConfirmPlan("25"))
        assertEquals(confirmed, s.ledger.plan)
    }
    @Test fun deletionRemovesImmediatelyAndUndoRestoresOriginalIdentityAndTime() {
        direct("10"); val original = s.ledger.records.single()
        act(Action.Navigate(Page.RECORDS)); act(Action.Delete(original.id))
        assertTrue(s.ledger.records.isEmpty())
        advance(100000); act(Action.UndoDelete)
        assertEquals(original, s.ledger.records.single())
        act(Action.UndoDelete); assertEquals(1, s.ledger.records.size)
    }
    @Test fun onlyLatestDeletionCanBeUndone() {
        direct("10", "a"); direct("20", "b"); act(Action.Navigate(Page.RECORDS))
        act(Action.Delete("a")); act(Action.Delete("b")); act(Action.UndoDelete)
        assertEquals(listOf("b"), s.ledger.records.map { it.id })
    }
    @Test fun leavingPageOrBackgroundClearsUndo() {
        direct("10", "a"); act(Action.Navigate(Page.RECORDS)); act(Action.Delete("a"))
        act(Action.Navigate(Page.USAGE)); act(Action.Navigate(Page.RECORDS)); act(Action.UndoDelete)
        assertTrue(s.ledger.records.isEmpty())
        act(Action.Navigate(Page.USAGE)); direct("20", "b"); act(Action.Navigate(Page.RECORDS)); act(Action.Delete("b"))
        act(Action.Foreground(false)); act(Action.Foreground(true)); act(Action.UndoDelete)
        assertTrue(s.ledger.records.isEmpty())
    }
    @Test fun ordinaryRefreshAndDialogCloseDoNotClearUndo() {
        direct("10", "a"); act(Action.Navigate(Page.RECORDS)); act(Action.Delete("a"))
        advance(100000); act(Action.Open(Dialog.PLAN)); act(Action.Close); act(Action.UndoDelete)
        assertEquals("a", s.ledger.records.single().id)
    }
    @Test fun expiredPlanAllowsDeleteAndUndoWithoutRenewing() {
        direct("10", "a"); expire(); val plan = s.ledger.plan
        act(Action.Navigate(Page.RECORDS)); act(Action.Delete("a")); act(Action.UndoDelete)
        assertEquals("a", s.ledger.records.single().id)
        assertEquals(plan, s.ledger.plan)
        assertFalse(s.canRecord(now))
    }
    @Test fun crossMidnightDraftUsesActualSaveDay() {
        now = Instant.parse("2026-10-15T15:59:59Z").toEpochMilli()
        add(); advance(3000)
        assertEquals("2026-10-16", Usage.day(s.ledger.records.single().savedAt).toString())
    }
    @Test fun capAndSingleCentAccuracy() {
        direct("999999.99"); add(1)
        assertEquals(Money.MAX_CENTS, s.ledger.records.single().cents)
        act(Action.Open(Dialog.AMOUNT)); act(Action.Input("999999.99")); expire(); renew(); add(1)
        assertEquals(Money.MAX_CENTS, s.ledger.draft.cents)
        assertTrue(s.ledger.draft.held)
    }
    @Test fun processRestoreKeepsUnconfirmedInputHeldAndDoesNotRestoreUndo() {
        add(500); act(Action.Open(Dialog.AMOUNT)); act(Action.Input("12.34"))
        s = Session.restore(s.ledger)
        act(Action.Foreground(true)); advance(10000)
        assertTrue(s.ledger.records.isEmpty())
        assertTrue(s.ledger.draft.held)
        assertEquals("12.34", s.ledger.draft.input)
        act(Action.Open(Dialog.AMOUNT)); act(Action.ConfirmAmount("restored"))
        assertEquals(1234L, s.ledger.records.single().cents)
        act(Action.Navigate(Page.RECORDS)); act(Action.Delete("restored"))
        s = Session.restore(s.ledger)
        act(Action.Foreground(true)); act(Action.Navigate(Page.RECORDS)); act(Action.UndoDelete)
        assertTrue(s.ledger.records.isEmpty())
    }
    @Test fun recreationRestoresSavedCountdownWithoutWallTimeElapsingIt() {
        add(); advance(1000); act(Action.Foreground(false))
        s = Session.restore(s.ledger)
        now += 100000; elapsed = 1
        act(Action.Foreground(true)); advance(1999)
        assertTrue(s.ledger.records.isEmpty())
        advance(1); assertEquals(100L, s.ledger.records.single().cents)
    }
}
