package de.sephie.bluete.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Datumswerte werden als ISO-Text gespeichert (JJJJ-MM-TT), das sortiert korrekt.

@Entity(tableName = "perioden")
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: String,
    val ende: String?
)

@Entity(tableName = "symptome", indices = [Index(value = ["datum", "name"], unique = true)])
data class SymptomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datum: String,
    val kategorie: String,
    val name: String,
    val staerke: Int
)

@Entity(tableName = "eisprung")
data class OvulationEntity(
    @PrimaryKey val datum: String,
    /** Anzeichen als Komma-Liste der Schlüssel, z. B. "waerme,lh_test". */
    val anzeichen: String
)

@Entity(tableName = "schleim")
data class MucusEntity(
    @PrimaryKey val datum: String,
    val art: String
)

@Entity(tableName = "eigene_symptome")
data class CustomSymptomEntity(
    @PrimaryKey val name: String,
    val kategorie: String
)

@Dao
abstract class CycleDao {
    @Query("SELECT * FROM perioden ORDER BY start")
    abstract fun periods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM perioden ORDER BY start")
    abstract suspend fun periodsOnce(): List<PeriodEntity>

    @Insert
    abstract suspend fun insertPeriod(p: PeriodEntity): Long

    @Upsert
    abstract suspend fun upsertPeriod(p: PeriodEntity)

    @Query("DELETE FROM perioden WHERE id = :id")
    abstract suspend fun deletePeriod(id: Long)

    @Query("SELECT * FROM symptome ORDER BY datum")
    abstract fun symptoms(): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptome ORDER BY datum")
    abstract suspend fun symptomsOnce(): List<SymptomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSymptoms(list: List<SymptomEntity>)

    @Query("DELETE FROM symptome WHERE datum = :datum")
    abstract suspend fun deleteSymptomsOn(datum: String)

    @Transaction
    open suspend fun replaceSymptomsOn(datum: String, list: List<SymptomEntity>) {
        deleteSymptomsOn(datum)
        insertSymptoms(list)
    }

    @Query("SELECT * FROM eisprung ORDER BY datum")
    abstract fun ovulations(): Flow<List<OvulationEntity>>

    @Query("SELECT * FROM eisprung ORDER BY datum")
    abstract suspend fun ovulationsOnce(): List<OvulationEntity>

    @Upsert
    abstract suspend fun upsertOvulation(o: OvulationEntity)

    @Query("DELETE FROM eisprung WHERE datum = :datum")
    abstract suspend fun deleteOvulation(datum: String)

    @Query("SELECT * FROM schleim ORDER BY datum")
    abstract fun mucus(): Flow<List<MucusEntity>>

    @Query("SELECT * FROM schleim ORDER BY datum")
    abstract suspend fun mucusOnce(): List<MucusEntity>

    @Upsert
    abstract suspend fun upsertMucus(m: MucusEntity)

    @Query("DELETE FROM schleim WHERE datum = :datum")
    abstract suspend fun deleteMucus(datum: String)

    @Query("SELECT * FROM eigene_symptome ORDER BY name")
    abstract fun customSymptoms(): Flow<List<CustomSymptomEntity>>

    @Query("SELECT * FROM eigene_symptome ORDER BY name")
    abstract suspend fun customSymptomsOnce(): List<CustomSymptomEntity>

    @Upsert
    abstract suspend fun upsertCustomSymptom(c: CustomSymptomEntity)

    @Query("DELETE FROM eigene_symptome WHERE name = :name")
    abstract suspend fun deleteCustomSymptom(name: String)

    @Query("DELETE FROM perioden")
    abstract suspend fun clearPeriods()

    @Query("DELETE FROM symptome")
    abstract suspend fun clearSymptoms()

    @Query("DELETE FROM eisprung")
    abstract suspend fun clearOvulations()

    @Query("DELETE FROM schleim")
    abstract suspend fun clearMucus()

    @Query("DELETE FROM eigene_symptome")
    abstract suspend fun clearCustomSymptoms()
}

@Database(
    entities = [PeriodEntity::class, SymptomEntity::class, OvulationEntity::class, MucusEntity::class, CustomSymptomEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): CycleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "bluete.db").build()
    }
}
