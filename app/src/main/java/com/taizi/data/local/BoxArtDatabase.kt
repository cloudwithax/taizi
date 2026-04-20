package com.taizi.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "box_art")
data class BoxArtEntry(
    @PrimaryKey val romPath: String,
    val systemId: String,
    val gameName: String,
    val artPath: String,
    val description: String? = null,
    val genre: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val players: Int? = null,
    val rating: Float? = null,
    val scrapedAt: Long = System.currentTimeMillis()
)

@Dao
interface BoxArtDao {
    @Query("SELECT * FROM box_art WHERE romPath = :romPath LIMIT 1")
    suspend fun get(romPath: String): BoxArtEntry?

    @Query("SELECT * FROM box_art WHERE systemId = :systemId")
    suspend fun getForSystem(systemId: String): List<BoxArtEntry>

    @Query("SELECT * FROM box_art")
    suspend fun getAll(): List<BoxArtEntry>

    @Upsert
    suspend fun upsert(entry: BoxArtEntry)

    @Upsert
    suspend fun upsertAll(entries: List<BoxArtEntry>)

    @Query("DELETE FROM box_art WHERE romPath = :romPath")
    suspend fun delete(romPath: String)

    @Query("SELECT COUNT(*) FROM box_art")
    suspend fun count(): Int
}

@Database(entities = [BoxArtEntry::class], version = 1, exportSchema = false)
abstract class BoxArtDatabase : RoomDatabase() {
    abstract fun boxArtDao(): BoxArtDao

    companion object {
        @Volatile
        private var INSTANCE: BoxArtDatabase? = null

        fun getInstance(context: Context): BoxArtDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BoxArtDatabase::class.java,
                    "taizi_boxart.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
