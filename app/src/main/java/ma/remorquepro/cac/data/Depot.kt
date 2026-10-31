package ma.remorquepro.cac.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ma.remorquepro.core.backup.Sauvegarde
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Point d'accès unique aux données locales (SQLite via Room). Aucun accès réseau. */
class Depot(private val db: BaseDeDonnees, private val horloge: () -> Long = System::currentTimeMillis) {
    private val missionsDao = db.missionDao()
    private val parametresDao = db.parametresDao()

    val missions: Flow<List<Mission>> = missionsDao.observerToutes().map { l -> l.map { it.versModele() } }

    val parametres: Flow<Parametres> = parametresDao.observer().map { it?.versModele() ?: Parametres() }

    fun observerMission(id: Long): Flow<Mission?> = missionsDao.observer(id).map { it?.versModele() }

    suspend fun mission(id: Long): Mission? = missionsDao.parId(id)?.versModele()

    suspend fun toutesMissions(): List<Mission> = missionsDao.toutes().map { it.versModele() }

    suspend fun parametresActuels(): Parametres = parametresDao.lire()?.versModele() ?: Parametres()

    suspend fun numerosExistants(saufId: Long): List<String> = missionsDao.numeros(saufId)

    /** Crée (id = 0) ou met à jour une mission ; renvoie son identifiant. */
    suspend fun enregistrer(m: Mission): Long {
        val maintenant = horloge()
        return if (m.id == 0L) {
            missionsDao.inserer(m.copy(creeLe = maintenant, modifieLe = maintenant).versEntite())
        } else {
            val cree = if (m.creeLe == 0L) maintenant else m.creeLe
            missionsDao.upsert(m.copy(creeLe = cree, modifieLe = maintenant).versEntite())
            m.id
        }
    }

    suspend fun supprimer(id: Long): Boolean = missionsDao.supprimer(id) > 0

    suspend fun enregistrerParametres(p: Parametres) = parametresDao.enregistrer(p.versEntite())

    suspend fun creerSauvegarde(): Sauvegarde = Sauvegarde(
        creeLe = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        parametres = parametresActuels(),
        missions = toutesMissions(),
    )

    /** Remplace TOUTES les données locales par le contenu de la sauvegarde (transaction atomique). */
    suspend fun restaurer(s: Sauvegarde) {
        db.withTransaction {
            missionsDao.toutSupprimer()
            missionsDao.insererTout(s.missions.map { it.versEntite() })
            parametresDao.enregistrer(s.parametres.versEntite())
        }
    }

    suspend fun nombreMissions(): Int = missionsDao.nombre()
}
