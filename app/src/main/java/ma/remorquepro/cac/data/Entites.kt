package ma.remorquepro.cac.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Table SQLite des missions. Dates stockées en jours depuis l'époque (epochDay). */
@Entity(
    tableName = "missions",
    indices = [Index("dateReception"), Index("numeroCac"), Index("statut")],
)
data class MissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numeroCac: String,
    val dateReception: Long,
    val heureAppel: String?,
    val chauffeur: String,
    val immatDepanneuse: String,
    val beneficiaireNom: String,
    val beneficiaireTelephone: String,
    val vehiculeMarqueModele: String,
    val vehiculeImmatriculation: String,
    val villeDepart: String,
    val lieuPriseEnCharge: String,
    val villeDestination: String,
    val lieuDestination: String,
    val motif: String,
    val heureDepart: String?,
    val heureArrivee: String?,
    val heureChargement: String?,
    val heureLivraison: String?,
    val kmDepart: Int?,
    val kmArrivee: Int?,
    val distanceKm: Int,
    val kmFacturables: Int,
    val observations: String,
    val statut: String,
    val tarifBase: Double,
    val tarifKm: Double,
    val supplementNuit: Double,
    val supplementWeekend: Double,
    val fraisAutoroute: Double,
    val fraisSupplementaires: Double,
    val fraisSupplementairesLibelle: String,
    val montantRegle: Double,
    val dateReglement: Long?,
    val referenceReglement: String,
    val creeLe: Long,
    val modifieLe: Long,
)

/** Paramètres du prestataire et tarifs : une seule ligne (id = 1). */
@Entity(tableName = "parametres")
data class ParametresEntity(
    @PrimaryKey val id: Int = 1,
    val nomChauffeur: String,
    val nomEntreprise: String,
    val immatDepanneuse: String,
    val telephone: String,
    val ville: String,
    val identifiantsLegaux: String,
    val tarifBase: Double,
    val tarifKm: Double,
    val kmInclus: Int,
    val supplementNuit: Double,
    val supplementWeekend: Double,
    val heureDebutNuit: String,
    val heureFinNuit: String,
    val samediEstWeekend: Boolean,
)
