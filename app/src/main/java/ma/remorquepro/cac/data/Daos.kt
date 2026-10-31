package ma.remorquepro.cac.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY dateReception DESC, heureAppel DESC, id DESC")
    fun observerToutes(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions ORDER BY dateReception DESC, heureAppel DESC, id DESC")
    suspend fun toutes(): List<MissionEntity>

    @Query("SELECT * FROM missions WHERE id = :id")
    fun observer(id: Long): Flow<MissionEntity?>

    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun parId(id: Long): MissionEntity?

    @Query("SELECT numeroCac FROM missions WHERE id != :saufId")
    suspend fun numeros(saufId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserer(m: MissionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insererTout(missions: List<MissionEntity>)

    @Upsert
    suspend fun upsert(m: MissionEntity): Long

    @Query("DELETE FROM missions WHERE id = :id")
    suspend fun supprimer(id: Long): Int

    @Query("DELETE FROM missions")
    suspend fun toutSupprimer()

    @Query("SELECT COUNT(*) FROM missions")
    suspend fun nombre(): Int
}

@Dao
interface ParametresDao {
    @Query("SELECT * FROM parametres WHERE id = 1")
    fun observer(): Flow<ParametresEntity?>

    @Query("SELECT * FROM parametres WHERE id = 1")
    suspend fun lire(): ParametresEntity?

    @Upsert
    suspend fun enregistrer(p: ParametresEntity)
}
