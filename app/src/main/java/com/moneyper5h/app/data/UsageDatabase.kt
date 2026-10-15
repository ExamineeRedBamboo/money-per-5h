package com.moneyper5h.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "records")
data class RecordEntity(@PrimaryKey val id: String, val cents: Long, val savedAt: Long)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val multiplier: Long? = null,
    val configuredAt: Long? = null,
    val additions: String = "",
    val input: String = "",
    val held: Boolean = false,
    val remainingMs: Long = 3_000,
)

@Dao
interface UsageDao {
    @Query("SELECT * FROM records ORDER BY savedAt DESC, id DESC")
    suspend fun records(): List<RecordEntity>
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): SettingsEntity?
    @Insert suspend fun insert(record: RecordEntity)
    @Query("DELETE FROM records WHERE id = :id") suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun settings(value: SettingsEntity)
}

@Database(entities = [RecordEntity::class, SettingsEntity::class], version = 1, exportSchema = true)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
}
