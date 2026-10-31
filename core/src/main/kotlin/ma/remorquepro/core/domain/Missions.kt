package ma.remorquepro.core.domain

import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres
import ma.remorquepro.core.model.StatutMission
import ma.remorquepro.core.model.StatutPaiement
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime

/** Création et transformation de missions. */
object Missions {

    /** Nouvelle mission pré-remplie avec le profil et les tarifs configurés. */
    fun nouvelle(p: Parametres, date: LocalDate, heureAppel: LocalTime?): Mission {
        val heure = heureAppel?.let(Heures::format)
        return Mission(
            dateReception = date,
            heureAppel = heure,
            chauffeur = p.nomChauffeur,
            immatDepanneuse = p.immatDepanneuse,
            tarifBase = p.tarifBase,
            tarifKm = p.tarifKm,
            supplementNuit = if (Supplements.estNuit(heure, p.heureDebutNuit, p.heureFinNuit)) p.supplementNuit else 0.0,
            supplementWeekend = if (Supplements.estWeekend(date, p.samediEstWeekend)) p.supplementWeekend else 0.0,
        )
    }

    /**
     * Copie d'une mission pour en saisir une nouvelle similaire : on garde bénéficiaire, véhicule,
     * trajet, motif et tarifs ; on remet à zéro numéro, horaires, compteur, statut et règlement.
     */
    fun dupliquer(m: Mission, date: LocalDate): Mission = m.copy(
        id = 0,
        numeroCac = "",
        dateReception = date,
        heureAppel = null,
        heureDepart = null,
        heureArrivee = null,
        heureChargement = null,
        heureLivraison = null,
        kmDepart = null,
        kmArrivee = null,
        distanceKm = 0,
        kmFacturables = 0,
        statut = StatutMission.RECUE,
        montantRegle = 0.0,
        dateReglement = null,
        referenceReglement = "",
        creeLe = 0,
        modifieLe = 0,
    )

    /**
     * Passe la mission au statut demandé et horodate l'étape correspondante
     * si l'heure n'a pas encore été saisie.
     */
    fun changerStatut(m: Mission, statut: StatutMission, maintenant: LocalTime): Mission {
        val h = Heures.format(maintenant)
        return when (statut) {
            StatutMission.EN_ROUTE -> m.copy(statut = statut, heureDepart = m.heureDepart ?: h)
            StatutMission.SUR_PLACE -> m.copy(statut = statut, heureArrivee = m.heureArrivee ?: h)
            StatutMission.CHARGE -> m.copy(statut = statut, heureChargement = m.heureChargement ?: h)
            StatutMission.LIVRE -> m.copy(statut = statut, heureLivraison = m.heureLivraison ?: h)
            else -> m.copy(statut = statut)
        }
    }

    /** Tri de l'historique : plus récentes d'abord. */
    val ORDRE_HISTORIQUE: Comparator<Mission> =
        compareByDescending<Mission> { it.dateReception }
            .thenByDescending { it.heureAppel ?: "" }
            .thenByDescending { it.id }
}

/** Erreurs bloquantes et avertissements d'une saisie de mission. */
data class ResultatValidation(val erreurs: List<String>, val avertissements: List<String>) {
    val estValide: Boolean get() = erreurs.isEmpty()
}

object ValidationMission {
    fun valider(m: Mission, numerosExistants: Collection<String> = emptyList()): ResultatValidation {
        val erreurs = mutableListOf<String>()
        val avert = mutableListOf<String>()
        if (m.numeroCac.isBlank()) erreurs += "Le numéro de mission CAC est obligatoire."
        val heures = listOf(
            "d'appel" to m.heureAppel, "de départ" to m.heureDepart, "d'arrivée sur place" to m.heureArrivee,
            "de chargement" to m.heureChargement, "de livraison" to m.heureLivraison,
        )
        heures.forEach { (nom, h) ->
            if (!h.isNullOrBlank() && !Heures.estValide(h)) erreurs += "Heure $nom invalide (format HH:mm attendu)."
        }
        if (m.kmDepart != null && m.kmArrivee != null && m.kmArrivee < m.kmDepart) {
            erreurs += "Le kilométrage d'arrivée doit être supérieur ou égal au kilométrage de départ."
        }
        if (m.kmDepart != null && m.kmDepart < 0 || m.kmArrivee != null && m.kmArrivee < 0) {
            erreurs += "Le kilométrage ne peut pas être négatif."
        }
        if (m.distanceKm < 0 || m.kmFacturables < 0) erreurs += "Les kilomètres ne peuvent pas être négatifs."
        val montants = listOf(
            m.tarifBase, m.tarifKm, m.supplementNuit, m.supplementWeekend,
            m.fraisAutoroute, m.fraisSupplementaires, m.montantRegle,
        )
        if (montants.any { it < 0 }) erreurs += "Les montants ne peuvent pas être négatifs."

        val numero = m.numeroCac.trim()
        if (numero.isNotEmpty() && numerosExistants.any { it.trim().equals(numero, ignoreCase = true) }) {
            avert += "Une autre mission porte déjà le numéro « $numero »."
        }
        val compteur = Kilometrage.distanceCompteur(m.kmDepart, m.kmArrivee)
        if (compteur != null && compteur != m.distanceKm) {
            avert += "La distance saisie (${m.distanceKm} km) diffère du compteur ($compteur km)."
        }
        if (m.kmFacturables > m.distanceKm && m.distanceKm > 0) {
            avert += "Les km facturables dépassent la distance réellement parcourue."
        }
        if (m.montantRegle > Facturation.total(m) + 0.005) {
            avert += "Le montant réglé dépasse le montant facturé (trop-perçu)."
        }
        return ResultatValidation(erreurs, avert)
    }
}

/** Critères de recherche de l'historique. Champs vides = pas de filtre. */
data class FiltreMissions(
    val texte: String = "",
    val numeroCac: String = "",
    val periode: Periode? = null,
    val immatriculation: String = "",
    val villeDepart: String = "",
    val villeDestination: String = "",
    val chauffeur: String = "",
    val statuts: Set<StatutMission> = emptySet(),
    val statutsPaiement: Set<StatutPaiement> = emptySet(),
) {
    val estActif: Boolean
        get() = texte.isNotBlank() || numeroCac.isNotBlank() || periode != null || immatriculation.isNotBlank() ||
            villeDepart.isNotBlank() || villeDestination.isNotBlank() || chauffeur.isNotBlank() ||
            statuts.isNotEmpty() || statutsPaiement.isNotEmpty()

    fun accepte(m: Mission): Boolean {
        if (periode != null && !periode.contient(m.dateReception)) return false
        if (!contient(m.numeroCac, numeroCac)) return false
        if (immatriculation.isNotBlank() &&
            !contientImmat(m.vehiculeImmatriculation, immatriculation) &&
            !contientImmat(m.immatDepanneuse, immatriculation)
        ) return false
        if (!contient(m.villeDepart + " " + m.lieuPriseEnCharge, villeDepart)) return false
        if (!contient(m.villeDestination + " " + m.lieuDestination, villeDestination)) return false
        if (!contient(m.chauffeur, chauffeur)) return false
        if (statuts.isNotEmpty() && m.statut !in statuts) return false
        if (statutsPaiement.isNotEmpty() && Facturation.statutPaiement(m) !in statutsPaiement) return false
        if (texte.isNotBlank()) {
            val tout = listOf(
                m.numeroCac, m.beneficiaireNom, m.beneficiaireTelephone, m.vehiculeMarqueModele,
                m.vehiculeImmatriculation, m.villeDepart, m.lieuPriseEnCharge, m.villeDestination,
                m.lieuDestination, m.motif, m.chauffeur, m.observations, m.referenceReglement,
            ).joinToString(" ")
            val mots = normaliser(texte).split(' ').filter { it.isNotBlank() }
            val cible = normaliser(tout)
            val cibleCompacte = compacter(tout)
            if (!mots.all { cible.contains(it) || cibleCompacte.contains(compacter(it)) }) return false
        }
        return true
    }

    fun appliquer(missions: List<Mission>): List<Mission> = missions.filter(::accepte).sortedWith(Missions.ORDRE_HISTORIQUE)

    companion object {
        /** Minuscules sans accents, espaces normalisés. */
        fun normaliser(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()

        /** Pour les immatriculations : on ignore espaces, tirets et barres (« 12345-A-6 » = « 12345 a 6 »). */
        fun compacter(s: String): String = normaliser(s).replace(Regex("[\\s\\-/|_.]"), "")

        private fun contient(valeur: String, recherche: String): Boolean =
            recherche.isBlank() || normaliser(valeur).contains(normaliser(recherche))

        private fun contientImmat(valeur: String, recherche: String): Boolean =
            compacter(valeur).contains(compacter(recherche))
    }
}
