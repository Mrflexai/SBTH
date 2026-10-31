package ma.remorquepro.core.model

import kotlinx.serialization.Serializable

/**
 * Paramètres du prestataire et tarifs convenus avec CAC.
 *
 * IMPORTANT : aucun tarif officiel CAC n'est fourni par l'application. Tous les montants
 * valent 0 par défaut et doivent être saisis par le chauffeur selon l'accord réellement
 * conclu avec CAC.
 */
@Serializable
data class Parametres(
    // --- Prestataire
    val nomChauffeur: String = "",
    val nomEntreprise: String = "",
    val immatDepanneuse: String = "",
    val telephone: String = "",
    val ville: String = "",
    /** Identifiants légaux libres (ICE, RC, IF…), affichés sur les rapports. */
    val identifiantsLegaux: String = "",
    // --- Tarifs convenus avec CAC (DH)
    val tarifBase: Double = 0.0,
    val tarifKm: Double = 0.0,
    /** Kilomètres inclus dans le forfait de base (non facturés au km). */
    val kmInclus: Int = 0,
    val supplementNuit: Double = 0.0,
    val supplementWeekend: Double = 0.0,
    // --- Règles d'application des suppléments (proposées automatiquement, modifiables par mission)
    val heureDebutNuit: String = "20:00",
    val heureFinNuit: String = "06:00",
    val samediEstWeekend: Boolean = true,
) {
    val tarifsConfigures: Boolean get() = tarifBase > 0.0 || tarifKm > 0.0
}
