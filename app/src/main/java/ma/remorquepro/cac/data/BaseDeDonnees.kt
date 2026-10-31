package ma.remorquepro.cac.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MissionEntity::class, ParametresEntity::class], version = 1, exportSchema = false)
abstract class BaseDeDonnees : RoomDatabase() {
    abstract fun missionDao(): MissionDao
    abstract fun parametresDao(): ParametresDao

    companion object {
        const val NOM_FICHIER = "remorquepro.db"

        fun creer(context: Context, nom: String = NOM_FICHIER): BaseDeDonnees =
            Room.databaseBuilder(context.applicationContext, BaseDeDonnees::class.java, nom).build()
    }
}
