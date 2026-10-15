package com.moneyper5h.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.moneyper5h.app.domain.Draft
import com.moneyper5h.app.domain.Ledger
import com.moneyper5h.app.domain.Money
import com.moneyper5h.app.domain.Plan
import com.moneyper5h.app.domain.UsageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageRepository(private val context: Context) {
    private val database by lazy {
        // The owner explicitly chose an empty ledger when upgrading the retired prototype.
        // deleteDatabase also removes its WAL/journal files; never open/import that database.
        val retired = context.getDatabasePath("wallet.db")
        val retiredFiles = listOf("", "-wal", "-shm", "-journal").map { java.io.File(retired.path + it) }
        if (retiredFiles.any { it.exists() }) {
            context.deleteDatabase("wallet.db")
            check(retiredFiles.none { it.exists() }) { "Unable to remove retired database" }
        }
        Room.databaseBuilder(context, UsageDatabase::class.java, "manual-usage.db").build()
    }
    suspend fun load(): Ledger = withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.usageDao()
            val settings = dao.settings() ?: SettingsEntity()
            val additions = settings.additions.takeIf { it.isNotEmpty() }?.split(',')?.map { it.toLong() } ?: emptyList()
            require(additions.all { it in 1..Money.MAX_CENTS } && additions.sum() <= Money.MAX_CENTS)
            Ledger(
                records = dao.records().map { UsageRecord(it.id, it.cents, it.savedAt) },
                plan = settings.multiplier?.let { Plan(it, requireNotNull(settings.configuredAt)) },
                draft = Draft(additions, settings.input, settings.held, settings.remainingMs.coerceIn(0, Draft.WAIT_MS)),
            )
        }
    }

    /** Publish UI success only after the entire record/settings change commits. */
    suspend fun commit(before: Ledger, after: Ledger) = database.withTransaction {
        val dao = database.usageDao()
        val oldIds = before.records.mapTo(hashSetOf()) { it.id }
        val newIds = after.records.mapTo(hashSetOf()) { it.id }
        (oldIds - newIds).forEach { dao.delete(it) }
        after.records.filter { it.id !in oldIds }.forEach { dao.insert(RecordEntity(it.id, it.cents, it.savedAt)) }
        if (before.plan != after.plan || before.draft != after.draft) {
            val d = after.draft
            dao.settings(SettingsEntity(multiplier = after.plan?.hundredths, configuredAt = after.plan?.configuredAt,
                additions = d.additions.joinToString(","), input = d.input, held = d.held, remainingMs = d.remainingMs))
        }
    }
}
