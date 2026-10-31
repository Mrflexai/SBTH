package ma.remorquepro.core

import ma.remorquepro.core.domain.FiltreMissions
import ma.remorquepro.core.domain.Missions
import ma.remorquepro.core.domain.Periode
import ma.remorquepro.core.domain.Statistiques
import ma.remorquepro.core.domain.ValidationMission
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.StatutMission
import ma.remorquepro.core.model.StatutPaiement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class MissionsTest {
    private val p = JeuDeDonnees.parametres

    @Test
    fun `nouvelle mission pre-remplie et supplements proposes`() {
        val samediNuit = Missions.nouvelle(p, LocalDate.of(2026, 10, 31), LocalTime.of(22, 10))
        assertEquals("22:10", samediNuit.heureAppel)
        assertEquals(p.nomChauffeur, samediNuit.chauffeur)
        assertEquals(p.immatDepanneuse, samediNuit.immatDepanneuse)
        assertEquals(300.0, samediNuit.tarifBase, 0.0)
        assertEquals(100.0, samediNuit.supplementNuit, 0.0)
        assertEquals(50.0, samediNuit.supplementWeekend, 0.0)

        val mardiJour = Missions.nouvelle(p, LocalDate.of(2026, 10, 27), LocalTime.of(10, 0))
        assertEquals(0.0, mardiJour.supplementNuit, 0.0)
        assertEquals(0.0, mardiJour.supplementWeekend, 0.0)
        assertEquals(StatutMission.RECUE, mardiJour.statut)
    }

    @Test
    fun `duplication garde le contexte et remet a zero le suivi`() {
        val m = JeuDeDonnees.mission(4)
        val d = Missions.dupliquer(m, LocalDate.of(2026, 11, 3))
        assertEquals(0L, d.id)
        assertEquals("", d.numeroCac)
        assertEquals(LocalDate.of(2026, 11, 3), d.dateReception)
        assertEquals(m.vehiculeImmatriculation, d.vehiculeImmatriculation)
        assertEquals(m.villeDepart, d.villeDepart)
        assertEquals(m.tarifKm, d.tarifKm, 0.0)
        assertNull(d.kmDepart)
        assertEquals(0.0, d.montantRegle, 0.0)
        assertEquals(StatutMission.RECUE, d.statut)
    }

    @Test
    fun `changement de statut horodate l'etape une seule fois`() {
        val m = Mission(numeroCac = "A")
        val enRoute = Missions.changerStatut(m, StatutMission.EN_ROUTE, LocalTime.of(9, 5))
        assertEquals("09:05", enRoute.heureDepart)
        val encore = Missions.changerStatut(enRoute, StatutMission.EN_ROUTE, LocalTime.of(10, 0))
        assertEquals("09:05", encore.heureDepart)
        val livre = Missions.changerStatut(encore, StatutMission.LIVRE, LocalTime.of(11, 45))
        assertEquals("11:45", livre.heureLivraison)
        assertEquals(StatutMission.LIVRE, livre.statut)
        assertEquals(StatutMission.TERMINEE, StatutMission.LIVRE.suivant)
        assertNull(StatutMission.ANNULEE.suivant)
    }

    @Test
    fun validation() {
        val ok = JeuDeDonnees.mission(1)
        assertTrue(ValidationMission.valider(ok).estValide)
        assertTrue(ValidationMission.valider(ok.copy(numeroCac = " ")).erreurs.any { "obligatoire" in it })
        assertTrue(ValidationMission.valider(ok.copy(heureAppel = "25:00")).erreurs.isNotEmpty())
        assertTrue(ValidationMission.valider(ok.copy(kmDepart = 100, kmArrivee = 50)).erreurs.isNotEmpty())
        assertTrue(ValidationMission.valider(ok.copy(fraisAutoroute = -1.0)).erreurs.isNotEmpty())
        val doublon = ValidationMission.valider(ok, listOf(ok.numeroCac.lowercase()))
        assertTrue(doublon.estValide)
        assertTrue(doublon.avertissements.any { "déjà" in it })
    }

    @Test
    fun filtres() {
        val toutes = JeuDeDonnees.missions(60)
        assertEquals(60, FiltreMissions().appliquer(toutes).size)

        val parNumero = FiltreMissions(numeroCac = "00007").appliquer(toutes)
        assertEquals(listOf("CAC-2026-00007"), parNumero.map { it.numeroCac })

        val m = toutes[12]
        val immatSansTirets = m.vehiculeImmatriculation.replace("-", " ")
        assertTrue(FiltreMissions(immatriculation = immatSansTirets).appliquer(toutes).contains(m))
        assertEquals(toutes.size, FiltreMissions(immatriculation = "45821A6").appliquer(toutes).size) // dépanneuse

        // Recherche insensible aux accents et à la casse
        val mohammedia = FiltreMissions(villeDepart = "MOHAMMEDIA").appliquer(toutes)
        assertTrue(mohammedia.isNotEmpty())
        assertTrue(mohammedia.all { it.villeDepart == "Mohammédia" })
        assertTrue(FiltreMissions(villeDestination = "kenitra").appliquer(toutes).all { it.villeDestination == "Kénitra" })

        val periode = Periode.personnalisee(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6))
        val res = FiltreMissions(periode = periode).appliquer(toutes)
        assertTrue(res.isNotEmpty())
        assertTrue(res.all { periode.contient(it.dateReception) })
        // Tri : plus récentes d'abord
        assertEquals(res.map { it.dateReception }.sortedDescending(), res.map { it.dateReception })

        val terminees = FiltreMissions(statuts = setOf(StatutMission.TERMINEE)).appliquer(toutes)
        assertTrue(terminees.isNotEmpty() && terminees.all { it.statut == StatutMission.TERMINEE })

        val nonReglees = FiltreMissions(statutsPaiement = setOf(StatutPaiement.NON_REGLE)).appliquer(toutes)
        assertTrue(nonReglees.isNotEmpty() && nonReglees.all { it.montantRegle == 0.0 })

        assertTrue(FiltreMissions(chauffeur = "youssef").appliquer(toutes).size == 60)
        assertTrue(FiltreMissions(chauffeur = "karim").appliquer(toutes).isEmpty())
        assertTrue(FiltreMissions(texte = "dacia casablanca").appliquer(toutes).all { "Dacia" in it.vehiculeMarqueModele })
        assertFalse(FiltreMissions().estActif)
        assertTrue(FiltreMissions(texte = "x").estActif)
    }

    @Test
    fun statistiques() {
        val a = Mission(numeroCac = "1", distanceKm = 50, kmFacturables = 40, tarifBase = 300.0, tarifKm = 5.0, montantRegle = 500.0, statut = StatutMission.TERMINEE)
        val b = Mission(numeroCac = "2", distanceKm = 20, kmFacturables = 20, tarifBase = 300.0, tarifKm = 5.0, statut = StatutMission.EN_ROUTE)
        val c = Mission(numeroCac = "3", distanceKm = 8, kmFacturables = 0, statut = StatutMission.ANNULEE)
        val s = Statistiques.synthese(listOf(a, b, c))
        assertEquals(2, s.nombreMissions)
        assertEquals(1, s.missionsAnnulees)
        assertEquals(78, s.kmParcourus)
        assertEquals(60, s.kmFacturables)
        assertEquals(900.0, s.totalFacture, 0.0)
        assertEquals(500.0, s.totalEncaisse, 0.0)
        assertEquals(400.0, s.resteAEncaisser, 0.0)
    }

    @Test
    fun `tableau de bord`() {
        val auj = LocalDate.of(2026, 10, 31)
        val missions = listOf(
            Mission(numeroCac = "a", dateReception = auj, statut = StatutMission.EN_ROUTE, tarifBase = 100.0, distanceKm = 10),
            Mission(numeroCac = "b", dateReception = auj, statut = StatutMission.TERMINEE, tarifBase = 200.0, montantRegle = 200.0, distanceKm = 30),
            Mission(numeroCac = "c", dateReception = auj.minusDays(10), statut = StatutMission.TERMINEE, tarifBase = 50.0, distanceKm = 5),
            Mission(numeroCac = "d", dateReception = auj.minusMonths(2), statut = StatutMission.SUR_PLACE, tarifBase = 70.0, distanceKm = 7),
            Mission(numeroCac = "e", dateReception = auj, statut = StatutMission.ANNULEE),
        )
        val t = Statistiques.tableauDeBord(missions, auj)
        assertEquals(2, t.missionsDuJour)
        assertEquals(3, t.missionsDuMois)
        assertEquals(2, t.missionsTermineesMois)
        assertEquals(2, t.missionsEnAttente)
        assertEquals(45, t.kmMois)
        assertEquals(350.0, t.factureMois, 0.0)
        assertEquals(200.0, t.regleMois, 0.0)
        assertEquals(150.0, t.resteMois, 0.0)
        assertEquals(220.0, t.resteTotal, 0.0)
        assertEquals(6, t.historique.size)
        assertEquals(YearMonth.of(2026, 10), t.historique.last().mois)
        assertEquals(1, t.historique[3].synthese.nombreMissions) // août
    }
}
