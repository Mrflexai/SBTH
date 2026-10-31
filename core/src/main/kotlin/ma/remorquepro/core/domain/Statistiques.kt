package ma.remorquepro.core.domain

import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.StatutMission
import java.time.LocalDate
import java.time.YearMonth

/**
 * Agrégats d'un ensemble de missions.
 *
 * Règle : les missions annulées ne sont pas comptées dans « nombre de missions » (elles sont
 * comptées à part), mais leurs kilomètres et montants éventuels (déplacement facturé) sont
 * inclus dans les totaux — une mission annulée sans montant n'a donc aucun effet sur les sommes.
 */
data class Synthese(
    val nombreMissions: Int,
    val missionsAnnulees: Int,
    val kmParcourus: Int,
    val kmFacturables: Int,
    val totalFacture: Double,
    val totalEncaisse: Double,
    val resteAEncaisser: Double,
) {
    companion object {
        val VIDE = Synthese(0, 0, 0, 0, 0.0, 0.0, 0.0)
    }
}

data class SyntheseMois(val mois: YearMonth, val synthese: Synthese)

data class TableauDeBord(
    val missionsDuJour: Int,
    val missionsDuMois: Int,
    val missionsTermineesMois: Int,
    val missionsEnAttente: Int,
    val kmMois: Int,
    val kmFacturablesMois: Int,
    val factureMois: Double,
    val regleMois: Double,
    val resteMois: Double,
    val resteTotal: Double,
    val historique: List<SyntheseMois>,
)

object Statistiques {

    fun synthese(missions: List<Mission>): Synthese {
        if (missions.isEmpty()) return Synthese.VIDE
        var facture = 0.0
        var encaisse = 0.0
        missions.forEach {
            facture += Facturation.total(it)
            encaisse += it.montantRegle
        }
        return Synthese(
            nombreMissions = missions.count { it.statut != StatutMission.ANNULEE },
            missionsAnnulees = missions.count { it.statut == StatutMission.ANNULEE },
            kmParcourus = missions.sumOf { it.distanceKm },
            kmFacturables = missions.sumOf { it.kmFacturables },
            totalFacture = arrondi2(facture),
            totalEncaisse = arrondi2(encaisse),
            resteAEncaisser = arrondi2(facture - encaisse),
        )
    }

    fun dansPeriode(missions: List<Mission>, periode: Periode): List<Mission> =
        missions.filter { periode.contient(it.dateReception) }.sortedWith(
            compareBy<Mission> { it.dateReception }.thenBy { it.heureAppel ?: "" }.thenBy { it.numeroCac },
        )

    /** Synthèse pour chaque mois de la période (mois sans mission inclus). */
    fun syntheseMensuelle(missions: List<Mission>, periode: Periode): List<SyntheseMois> {
        val parMois = dansPeriode(missions, periode).groupBy { YearMonth.from(it.dateReception) }
        val moisCouverts = if (periode.type == TypePeriode.TOUT) {
            parMois.keys.sorted()
        } else {
            periode.mois()
        }
        return moisCouverts.map { SyntheseMois(it, synthese(parMois[it].orEmpty())) }
    }

    fun tableauDeBord(missions: List<Mission>, aujourdhui: LocalDate, nbMoisHistorique: Int = 6): TableauDeBord {
        val moisCourant = YearMonth.from(aujourdhui)
        val duMois = missions.filter { YearMonth.from(it.dateReception) == moisCourant }
        val sMois = synthese(duMois)
        val premierMois = moisCourant.minusMonths((nbMoisHistorique - 1).toLong())
        val historique = syntheseMensuelle(
            missions,
            Periode(premierMois.atDay(1), moisCourant.atEndOfMonth(), TypePeriode.PERSONNALISEE),
        )
        return TableauDeBord(
            missionsDuJour = missions.count { it.dateReception == aujourdhui && it.statut != StatutMission.ANNULEE },
            missionsDuMois = sMois.nombreMissions,
            missionsTermineesMois = duMois.count { it.statut == StatutMission.TERMINEE },
            missionsEnAttente = missions.count { it.statut.estEnCours },
            kmMois = sMois.kmParcourus,
            kmFacturablesMois = sMois.kmFacturables,
            factureMois = sMois.totalFacture,
            regleMois = sMois.totalEncaisse,
            resteMois = sMois.resteAEncaisser,
            resteTotal = synthese(missions).resteAEncaisser,
            historique = historique,
        )
    }
}
