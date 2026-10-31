package ma.remorquepro.cac.ui.missions

import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Heures
import ma.remorquepro.core.domain.Kilometrage
import ma.remorquepro.core.domain.Supplements
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres
import ma.remorquepro.core.model.StatutMission
import java.time.LocalDate
import java.util.Locale

/**
 * État de saisie d'une mission : les nombres sont gardés en texte pour permettre la frappe
 * (« 12, » …), et convertis à l'enregistrement.
 */
data class FormulaireMission(
    val id: Long = 0,
    val numeroCac: String = "",
    val dateReception: LocalDate = LocalDate.now(),
    val heureAppel: String = "",
    val chauffeur: String = "",
    val immatDepanneuse: String = "",
    val beneficiaireNom: String = "",
    val beneficiaireTelephone: String = "",
    val vehiculeMarqueModele: String = "",
    val vehiculeImmatriculation: String = "",
    val villeDepart: String = "",
    val lieuPriseEnCharge: String = "",
    val villeDestination: String = "",
    val lieuDestination: String = "",
    val motif: String = "",
    val heureDepart: String = "",
    val heureArrivee: String = "",
    val heureChargement: String = "",
    val heureLivraison: String = "",
    val kmDepart: String = "",
    val kmArrivee: String = "",
    val distanceKm: String = "",
    val kmFacturables: String = "",
    /** Vrai tant que l'utilisateur n'a pas modifié lui-même la distance. */
    val distanceAuto: Boolean = true,
    /** Vrai tant que l'utilisateur n'a pas modifié lui-même les km facturables. */
    val kmFacturablesAuto: Boolean = true,
    val observations: String = "",
    val statut: StatutMission = StatutMission.RECUE,
    val tarifBase: String = "",
    val tarifKm: String = "",
    val nuitActive: Boolean = false,
    val supplementNuit: String = "",
    val weekendActif: Boolean = false,
    val supplementWeekend: String = "",
    /** Vrai tant que l'utilisateur n'a pas touché aux interrupteurs de suppléments. */
    val supplementsAuto: Boolean = true,
    val fraisAutoroute: String = "",
    val fraisSupplementaires: String = "",
    val fraisSupplementairesLibelle: String = "",
    val montantRegle: String = "",
    val dateReglement: LocalDate? = null,
    val referenceReglement: String = "",
    val creeLe: Long = 0,
) {
    /** Recalcule distance et km facturables automatiques après une modification du compteur. */
    fun recalculerKm(kmInclus: Int): FormulaireMission {
        var f = this
        if (f.distanceAuto) {
            val d = Kilometrage.distanceCompteur(Formats.parseEntier(f.kmDepart), Formats.parseEntier(f.kmArrivee))
            if (d != null) f = f.copy(distanceKm = d.toString())
        }
        if (f.kmFacturablesAuto) {
            val d = Formats.parseEntier(f.distanceKm)
            f = f.copy(kmFacturables = d?.let { Kilometrage.kmFacturablesParDefaut(it, kmInclus).toString() } ?: "")
        }
        return f
    }

    /** Propose les suppléments nuit / week-end selon la date et l'heure d'appel (si non modifiés à la main). */
    fun proposerSupplements(p: Parametres): FormulaireMission {
        if (!supplementsAuto) return this
        val nuit = Supplements.estNuit(Heures.normaliser(heureAppel), p.heureDebutNuit, p.heureFinNuit)
        val weekend = Supplements.estWeekend(dateReception, p.samediEstWeekend)
        return copy(
            nuitActive = nuit,
            supplementNuit = if (nuit) montantTexte(p.supplementNuit) else "",
            weekendActif = weekend,
            supplementWeekend = if (weekend) montantTexte(p.supplementWeekend) else "",
        )
    }

    /** Convertit en mission ; renvoie la liste des champs numériques illisibles le cas échéant. */
    fun versMission(): Pair<Mission?, List<String>> {
        val erreurs = mutableListOf<String>()
        fun montant(texte: String, nom: String): Double =
            Formats.parseMontant(texte) ?: 0.0.also { erreurs += "$nom : montant invalide." }

        fun entierOpt(texte: String, nom: String): Int? =
            if (texte.isBlank()) null else Formats.parseEntier(texte) ?: null.also { erreurs += "$nom : nombre entier attendu." }

        fun heure(texte: String): String? = if (texte.isBlank()) null else Heures.normaliser(texte) ?: texte.trim()

        val m = Mission(
            id = id,
            numeroCac = numeroCac.trim(),
            dateReception = dateReception,
            heureAppel = heure(heureAppel),
            chauffeur = chauffeur,
            immatDepanneuse = immatDepanneuse,
            beneficiaireNom = beneficiaireNom,
            beneficiaireTelephone = beneficiaireTelephone,
            vehiculeMarqueModele = vehiculeMarqueModele,
            vehiculeImmatriculation = vehiculeImmatriculation,
            villeDepart = villeDepart,
            lieuPriseEnCharge = lieuPriseEnCharge,
            villeDestination = villeDestination,
            lieuDestination = lieuDestination,
            motif = motif,
            heureDepart = heure(heureDepart),
            heureArrivee = heure(heureArrivee),
            heureChargement = heure(heureChargement),
            heureLivraison = heure(heureLivraison),
            kmDepart = entierOpt(kmDepart, "Kilométrage départ"),
            kmArrivee = entierOpt(kmArrivee, "Kilométrage arrivée"),
            distanceKm = entierOpt(distanceKm, "Distance totale") ?: 0,
            kmFacturables = entierOpt(kmFacturables, "Km facturables") ?: 0,
            observations = observations,
            statut = statut,
            tarifBase = montant(tarifBase, "Tarif de base"),
            tarifKm = montant(tarifKm, "Tarif kilométrique"),
            supplementNuit = if (nuitActive) montant(supplementNuit, "Supplément de nuit") else 0.0,
            supplementWeekend = if (weekendActif) montant(supplementWeekend, "Supplément de week-end") else 0.0,
            fraisAutoroute = montant(fraisAutoroute, "Frais d'autoroute"),
            fraisSupplementaires = montant(fraisSupplementaires, "Frais supplémentaires"),
            fraisSupplementairesLibelle = fraisSupplementairesLibelle,
            montantRegle = montant(montantRegle, "Montant réglé"),
            dateReglement = dateReglement,
            referenceReglement = referenceReglement,
            creeLe = creeLe,
        )
        return (if (erreurs.isEmpty()) m else null) to erreurs
    }

    companion object {
        fun montantTexte(v: Double): String =
            if (v == 0.0) "" else if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.ROOT, "%.2f", v).replace('.', ',')

        fun depuis(m: Mission, kmInclus: Int): FormulaireMission {
            val compteur = Kilometrage.distanceCompteur(m.kmDepart, m.kmArrivee)
            return FormulaireMission(
                id = m.id,
                numeroCac = m.numeroCac,
                dateReception = m.dateReception,
                heureAppel = m.heureAppel.orEmpty(),
                chauffeur = m.chauffeur,
                immatDepanneuse = m.immatDepanneuse,
                beneficiaireNom = m.beneficiaireNom,
                beneficiaireTelephone = m.beneficiaireTelephone,
                vehiculeMarqueModele = m.vehiculeMarqueModele,
                vehiculeImmatriculation = m.vehiculeImmatriculation,
                villeDepart = m.villeDepart,
                lieuPriseEnCharge = m.lieuPriseEnCharge,
                villeDestination = m.villeDestination,
                lieuDestination = m.lieuDestination,
                motif = m.motif,
                heureDepart = m.heureDepart.orEmpty(),
                heureArrivee = m.heureArrivee.orEmpty(),
                heureChargement = m.heureChargement.orEmpty(),
                heureLivraison = m.heureLivraison.orEmpty(),
                kmDepart = m.kmDepart?.toString().orEmpty(),
                kmArrivee = m.kmArrivee?.toString().orEmpty(),
                distanceKm = if (m.distanceKm == 0 && compteur == null) "" else m.distanceKm.toString(),
                kmFacturables = if (m.kmFacturables == 0 && m.distanceKm == 0) "" else m.kmFacturables.toString(),
                // Une mission existante garde ses valeurs : le recalcul auto ne s'applique que si elles
                // correspondent déjà au calcul automatique.
                distanceAuto = compteur == null || compteur == m.distanceKm,
                kmFacturablesAuto = Kilometrage.kmFacturablesParDefaut(m.distanceKm, kmInclus) == m.kmFacturables,
                observations = m.observations,
                statut = m.statut,
                tarifBase = montantTexte(m.tarifBase),
                tarifKm = montantTexte(m.tarifKm),
                nuitActive = m.supplementNuit > 0,
                supplementNuit = montantTexte(m.supplementNuit),
                weekendActif = m.supplementWeekend > 0,
                supplementWeekend = montantTexte(m.supplementWeekend),
                supplementsAuto = m.id == 0L,
                fraisAutoroute = montantTexte(m.fraisAutoroute),
                fraisSupplementaires = montantTexte(m.fraisSupplementaires),
                fraisSupplementairesLibelle = m.fraisSupplementairesLibelle,
                montantRegle = montantTexte(m.montantRegle),
                dateReglement = m.dateReglement,
                referenceReglement = m.referenceReglement,
                creeLe = m.creeLe,
            )
        }
    }
}
