package ma.remorquepro.core.domain

import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.StatutPaiement
import java.math.BigDecimal
import java.math.RoundingMode

/** Arrondi monétaire au centime (demi-supérieur). */
fun arrondi2(v: Double): Double =
    if (v.isNaN() || v.isInfinite()) 0.0 else BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toDouble()

/** Détail du calcul de facturation d'une mission. */
data class DetailFacture(
    val tarifBase: Double,
    val kmFacturables: Int,
    val tarifKm: Double,
    val montantKm: Double,
    val supplementNuit: Double,
    val supplementWeekend: Double,
    val fraisAutoroute: Double,
    val fraisSupplementaires: Double,
    val total: Double,
    val regle: Double,
    val reste: Double,
    val statutPaiement: StatutPaiement,
)

object Facturation {
    private const val EPSILON = 0.005

    fun montantKm(m: Mission): Double = arrondi2(m.kmFacturables.coerceAtLeast(0) * m.tarifKm)

    /**
     * Total facturé = tarif de base + (km facturables × tarif km) + suppléments nuit/week-end
     * + frais d'autoroute + frais supplémentaires.
     */
    fun total(m: Mission): Double = arrondi2(
        m.tarifBase + montantKm(m) + m.supplementNuit + m.supplementWeekend +
            m.fraisAutoroute + m.fraisSupplementaires,
    )

    /** Reste à encaisser (négatif = trop-perçu). */
    fun reste(m: Mission): Double = arrondi2(total(m) - m.montantRegle)

    fun statutPaiement(m: Mission): StatutPaiement {
        val total = total(m)
        return when {
            total <= EPSILON && m.montantRegle <= EPSILON -> StatutPaiement.SANS_MONTANT
            m.montantRegle <= EPSILON -> StatutPaiement.NON_REGLE
            m.montantRegle + EPSILON < total -> StatutPaiement.PARTIEL
            else -> StatutPaiement.REGLE
        }
    }

    fun detail(m: Mission): DetailFacture = DetailFacture(
        tarifBase = arrondi2(m.tarifBase),
        kmFacturables = m.kmFacturables,
        tarifKm = m.tarifKm,
        montantKm = montantKm(m),
        supplementNuit = arrondi2(m.supplementNuit),
        supplementWeekend = arrondi2(m.supplementWeekend),
        fraisAutoroute = arrondi2(m.fraisAutoroute),
        fraisSupplementaires = arrondi2(m.fraisSupplementaires),
        total = total(m),
        regle = arrondi2(m.montantRegle),
        reste = reste(m),
        statutPaiement = statutPaiement(m),
    )
}

object Kilometrage {
    /** Distance parcourue d'après le compteur, ou null si incomplet / incohérent. */
    fun distanceCompteur(kmDepart: Int?, kmArrivee: Int?): Int? {
        if (kmDepart == null || kmArrivee == null) return null
        if (kmArrivee < kmDepart) return null
        return kmArrivee - kmDepart
    }

    /** Kilomètres facturables proposés par défaut : distance moins les km inclus au forfait. */
    fun kmFacturablesParDefaut(distanceKm: Int, kmInclus: Int): Int =
        (distanceKm - kmInclus.coerceAtLeast(0)).coerceAtLeast(0)
}
