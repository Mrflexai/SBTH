package ma.remorquepro.core.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Statuts d'une mission de remorquage CAC, dans l'ordre normal de progression. */
@Serializable
enum class StatutMission(val libelle: String, val libelleCourt: String) {
    RECUE("Mission reçue", "Reçue"),
    EN_ROUTE("En route", "En route"),
    SUR_PLACE("Sur place", "Sur place"),
    CHARGE("Véhicule chargé", "Chargé"),
    LIVRE("Véhicule livré", "Livré"),
    TERMINEE("Mission terminée", "Terminée"),
    ANNULEE("Mission annulée", "Annulée");

    /** Une mission « en attente » n'est ni terminée ni annulée. */
    val estEnCours: Boolean get() = this != TERMINEE && this != ANNULEE

    /** Statut suivant dans le déroulé normal (null si terminée ou annulée). */
    val suivant: StatutMission?
        get() = when (this) {
            RECUE -> EN_ROUTE
            EN_ROUTE -> SUR_PLACE
            SUR_PLACE -> CHARGE
            CHARGE -> LIVRE
            LIVRE -> TERMINEE
            TERMINEE, ANNULEE -> null
        }
}

/** Statut de paiement, toujours déduit des montants (jamais saisi). */
enum class StatutPaiement(val libelle: String) {
    SANS_MONTANT("Sans montant"),
    NON_REGLE("Non réglé"),
    PARTIEL("Partiellement réglé"),
    REGLE("Réglé"),
}

/**
 * Mission de remorquage réalisée pour CAC.
 *
 * Les tarifs sont copiés depuis les paramètres au moment de la création de la mission
 * (instantané) : modifier les tarifs plus tard ne change pas les missions déjà saisies.
 * Les heures sont stockées au format « HH:mm ».
 */
@Serializable
data class Mission(
    val id: Long = 0,
    // --- Identification CAC
    val numeroCac: String = "",
    @Serializable(with = LocalDateIsoSerializer::class)
    val dateReception: LocalDate = LocalDate.now(),
    val heureAppel: String? = null,
    val chauffeur: String = "",
    val immatDepanneuse: String = "",
    // --- Bénéficiaire et véhicule
    val beneficiaireNom: String = "",
    val beneficiaireTelephone: String = "",
    val vehiculeMarqueModele: String = "",
    val vehiculeImmatriculation: String = "",
    // --- Trajet
    val villeDepart: String = "",
    val lieuPriseEnCharge: String = "",
    val villeDestination: String = "",
    val lieuDestination: String = "",
    val motif: String = "",
    // --- Horaires
    val heureDepart: String? = null,
    val heureArrivee: String? = null,
    val heureChargement: String? = null,
    val heureLivraison: String? = null,
    // --- Kilométrage
    val kmDepart: Int? = null,
    val kmArrivee: Int? = null,
    /** Distance réellement parcourue (km). Calculée depuis le compteur quand c'est possible. */
    val distanceKm: Int = 0,
    /** Kilomètres facturés à CAC (peut différer de la distance réellement parcourue). */
    val kmFacturables: Int = 0,
    val observations: String = "",
    val statut: StatutMission = StatutMission.RECUE,
    // --- Facturation (montants en dirhams)
    val tarifBase: Double = 0.0,
    val tarifKm: Double = 0.0,
    val supplementNuit: Double = 0.0,
    val supplementWeekend: Double = 0.0,
    val fraisAutoroute: Double = 0.0,
    val fraisSupplementaires: Double = 0.0,
    val fraisSupplementairesLibelle: String = "",
    val montantRegle: Double = 0.0,
    @Serializable(with = LocalDateIsoNullableSerializer::class)
    val dateReglement: LocalDate? = null,
    val referenceReglement: String = "",
    // --- Traçabilité
    val creeLe: Long = 0,
    val modifieLe: Long = 0,
)
