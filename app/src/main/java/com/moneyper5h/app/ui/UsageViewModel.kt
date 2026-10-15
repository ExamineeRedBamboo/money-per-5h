package com.moneyper5h.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneyper5h.app.WalletApplication
import com.moneyper5h.app.domain.Action
import com.moneyper5h.app.domain.Session
import com.moneyper5h.app.domain.SessionReducer
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UsageUiState(
    val session: Session = Session(),
    val now: Long = System.currentTimeMillis(),
    val elapsed: Long = SystemClock.elapsedRealtime(),
    val loading: Boolean = true,
    val unavailable: Boolean = false,
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WalletApplication).repository
    private val mutex = Mutex()
    private var visible = false
    private val visibility = MutableStateFlow(false)
    private val mutableState = MutableStateFlow(UsageUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val ledger = repository.load()
                val now = System.currentTimeMillis()
                val elapsed = SystemClock.elapsedRealtime()
                val initial = Session.restore(ledger)
                val resumed = SessionReducer.reduce(initial, Action.Foreground(visible), now, elapsed, ::id)
                if (ledger != resumed.ledger) repository.commit(ledger, resumed.ledger)
                mutableState.value = UsageUiState(resumed, now, elapsed, loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = UsageUiState(loading = false, unavailable = true)
            }
        }
        viewModelScope.launch {
            while (true) {
                visibility.first { it }
                delay(100)
                if (visible) update(Action.Tick)
            }
        }
    }

    fun foreground(value: Boolean) {
        visible = value
        visibility.value = value
        dispatch(Action.Foreground(value))
    }
    fun dispatch(action: Action) { viewModelScope.launch { update(action) } }
    fun confirmAmount() = dispatch(Action.ConfirmAmount(id()))
    private fun id(): String = UUID.randomUUID().toString()

    private suspend fun update(action: Action) = mutex.withLock {
        val before = mutableState.value
        if (before.loading || before.unavailable) return@withLock
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val effectiveAction = if (action == Action.Tick && before.session.foreground != visible) Action.Foreground(visible) else action
        val after = SessionReducer.reduce(before.session, effectiveAction, now, elapsed, ::id)
        try {
            if (after.ledger != before.session.ledger) repository.commit(before.session.ledger, after.ledger)
            mutableState.value = before.copy(session = after, now = now, elapsed = elapsed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val old = before.session
            mutableState.value = before.copy(session = old.copy(deadline = null,
                ledger = old.ledger.copy(draft = old.ledger.draft.copy(held = old.ledger.draft.hasContent)),
                foreground = visible, deleted = if (visible) old.deleted else null,
                message = "本地保存失败，草稿已保留，请重试。"), now = now, elapsed = elapsed)
        }
    }
}
