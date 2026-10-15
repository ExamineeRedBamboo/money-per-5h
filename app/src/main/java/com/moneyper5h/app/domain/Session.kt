package com.moneyper5h.app.domain

data class Draft(
    val additions: List<Long> = emptyList(),
    val input: String = "",
    val held: Boolean = false,
    val remainingMs: Long = WAIT_MS,
) {
    val cents: Long get() = additions.sum()
    val hasContent: Boolean get() = additions.isNotEmpty() || input.isNotEmpty()
    companion object { const val WAIT_MS = 3_000L }
}

data class Ledger(
    val records: List<UsageRecord> = emptyList(),
    val plan: Plan? = null,
    val draft: Draft = Draft(),
)
enum class Page { USAGE, RECORDS }
enum class Dialog { NONE, AMOUNT, PLAN }
data class Session(
    val ledger: Ledger = Ledger(),
    val page: Page = Page.USAGE,
    val foreground: Boolean = false,
    val dialog: Dialog = Dialog.NONE,
    val deadline: Long? = null,
    val deleted: UsageRecord? = null,
    val message: String? = null,
) {
    fun canRecord(now: Long): Boolean = ledger.plan?.active(now) == true
    fun remaining(elapsed: Long): Long = deadline?.let { (it - elapsed).coerceAtLeast(0) }
        ?: ledger.draft.remainingMs
    companion object {
        // A process may die while the amount dialog is open: unconfirmed typing must stay held.
        fun restore(ledger: Ledger): Session = Session(ledger = ledger.copy(draft = ledger.draft.copy(
            held = ledger.draft.held || ledger.draft.input.isNotEmpty(),
        )))
    }
}

sealed interface Action {
    data object Tick : Action
    data class Foreground(val visible: Boolean) : Action
    data class Navigate(val page: Page) : Action
    data class Open(val dialog: Dialog) : Action
    data object Close : Action
    data class Add(val cents: Long) : Action
    data object UndoTap : Action
    data object Clear : Action
    data class Input(val text: String) : Action
    data class ConfirmAmount(val id: String) : Action
    data class ConfirmPlan(val value: String) : Action
    data class Delete(val id: String) : Action
    data object UndoDelete : Action
    data object DismissMessage : Action
}

/** All saves, expiration checks and lifecycle changes pass through one reducer. */
object SessionReducer {
    val denominations = listOf(10_000L, 5_000L, 2_000L, 1_000L, 500L, 100L, 50L, 10L, 5L, 1L)

    fun reduce(previous: Session, action: Action, now: Long, elapsed: Long, newId: () -> String): Session {
        var s = previous
        fun pause() {
            if (s.deadline != null) {
                s = s.copy(ledger = s.ledger.copy(draft = s.ledger.draft.copy(remainingMs = s.remaining(elapsed))), deadline = null)
            }
        }
        fun draft(value: Draft) { s = s.copy(ledger = s.ledger.copy(draft = value)) }
        fun save(cents: Long, id: String) {
            if (!s.canRecord(now) || cents !in 1..Money.MAX_CENTS) return
            s = s.copy(ledger = s.ledger.copy(
                records = s.ledger.records + UsageRecord(id, cents, now), draft = Draft(),
            ), deadline = null, dialog = Dialog.NONE, message = "已记下 ${Money.display(cents)}")
        }
        // Check expiration BEFORE a timer or explicit save can write a record.
        if (!s.canRecord(now)) {
            pause()
            val d = s.ledger.draft
            val typed = if (s.dialog == Dialog.AMOUNT) Money.parse(d.input) else null
            if (d.hasContent) draft(d.copy(additions = typed?.let { listOf(it) } ?: d.additions, held = true))
        }
        when (action) {
            is Action.Foreground -> {
                pause()
                s = s.copy(foreground = action.visible, deleted = if (action.visible) s.deleted else null)
            }
            is Action.Navigate -> {
                pause()
                s = s.copy(page = action.page, deleted = if (action.page == Page.USAGE) null else s.deleted)
            }
            is Action.Open -> {
                if (action.dialog != Dialog.AMOUNT || s.canRecord(now)) {
                    pause()
                    if (action.dialog == Dialog.AMOUNT && s.ledger.draft.input.isEmpty()) {
                        draft(s.ledger.draft.copy(input = s.ledger.draft.cents.takeIf { it > 0 }?.let(Money::decimal) ?: ""))
                    }
                    s = s.copy(dialog = action.dialog, message = null)
                }
            }
            Action.Close -> {
                if (s.dialog == Dialog.AMOUNT && s.canRecord(now)) draft(s.ledger.draft.copy(input = ""))
                s = s.copy(dialog = Dialog.NONE, message = null)
            }
            is Action.Add -> if (s.canRecord(now) && s.foreground && s.page == Page.USAGE && s.dialog == Dialog.NONE) {
                val d = s.ledger.draft
                if (action.cents in denominations && d.cents + action.cents <= Money.MAX_CENTS) {
                    draft(Draft(d.additions + action.cents))
                    s = s.copy(deadline = null, message = null)
                } else s = s.copy(message = "单笔最多 ¥999999.99")
            }
            Action.UndoTap -> {
                val d = s.ledger.draft
                if (d.additions.isNotEmpty()) {
                    draft(Draft(d.additions.dropLast(1), held = !s.canRecord(now)))
                    s = s.copy(deadline = null)
                }
            }
            Action.Clear -> { draft(Draft()); s = s.copy(deadline = null, message = null) }
            is Action.Input -> if (s.dialog == Dialog.AMOUNT && s.canRecord(now)) {
                draft(s.ledger.draft.copy(input = action.text.take(32)))
                s = s.copy(message = null)
            }
            is Action.ConfirmAmount -> if (s.dialog == Dialog.AMOUNT && s.foreground && s.canRecord(now)) {
                val cents = Money.parse(s.ledger.draft.input)
                if (cents != null) save(cents, action.id)
                else s = s.copy(message = "请输入大于0、最多两位小数的金额，最大 ¥999999.99")
            }
            is Action.ConfirmPlan -> if (s.dialog == Dialog.PLAN && s.foreground) {
                val multiplier = Plan.parse(action.value)
                if (multiplier == null) s = s.copy(message = "请输入大于0、最多两位小数的倍数，最大6666.66x")
                else {
                    val d = s.ledger.draft
                    s = s.copy(ledger = s.ledger.copy(plan = Plan(multiplier, now), draft = d.copy(held = d.hasContent)),
                        deadline = null, dialog = Dialog.NONE, message = "方案已设置")
                }
            }
            is Action.Delete -> if (s.page == Page.RECORDS && s.foreground) {
                s.ledger.records.find { it.id == action.id }?.let { record ->
                    s = s.copy(ledger = s.ledger.copy(records = s.ledger.records.filterNot { it.id == record.id }),
                        deleted = record, message = null)
                }
            }
            Action.UndoDelete -> if (s.page == Page.RECORDS && s.foreground) {
                s.deleted?.let { record ->
                    s = s.copy(ledger = s.ledger.copy(records = (s.ledger.records + record).distinctBy { it.id }),
                        deleted = null, message = "已恢复记录")
                }
            }
            Action.DismissMessage -> s = s.copy(message = null)
            Action.Tick -> if (s.foreground && s.page == Page.USAGE && s.dialog == Dialog.NONE &&
                s.canRecord(now) && !s.ledger.draft.held && s.deadline != null && elapsed >= s.deadline!!) {
                save(s.ledger.draft.cents, newId())
            }
        }
        if (s.foreground && s.page == Page.USAGE && s.dialog == Dialog.NONE && s.canRecord(now) &&
            !s.ledger.draft.held && s.ledger.draft.cents > 0 && s.deadline == null) {
            s = s.copy(deadline = elapsed + s.ledger.draft.remainingMs)
        }
        return s
    }
}
