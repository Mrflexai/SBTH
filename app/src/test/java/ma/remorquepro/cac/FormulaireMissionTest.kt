package ma.remorquepro.cac

import ma.remorquepro.cac.ui.missions.FormulaireMission
import ma.remorquepro.cac.ui.parametres.FormulaireParametres
import ma.remorquepro.core.domain.Facturation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FormulaireMissionTest {
    private val p = Donnees.parametres

    @Test
    fun `distance et km facturables calcules depuis le compteur`() {
        val f = FormulaireMission(kmDepart = "120000", kmArrivee = "120052").recalculerKm(p.kmInclus)
        assertEquals("52", f.distanceKm)
        assertEquals("42", f.kmFacturables)
    }

    @Test
    fun `saisie manuelle des km facturables respectee`() {
        val f = FormulaireMission(kmDepart = "100", kmArrivee = "160", kmFacturables = "70", kmFacturablesAuto = false)
            .recalculerKm(p.kmInclus)
        assertEquals("60", f.distanceKm)
        assertEquals("70", f.kmFacturables)
    }

    @Test
    fun `supplements proposes selon date et heure`() {
        val samediNuit = FormulaireMission(dateReception = LocalDate.of(2026, 10, 31), heureAppel = "23:10").proposerSupplements(p)
        assertTrue(samediNuit.nuitActive && samediNuit.weekendActif)
        assertEquals("100", samediNuit.supplementNuit)
        val mardi = FormulaireMission(dateReception = LocalDate.of(2026, 10, 27), heureAppel = "10:00").proposerSupplements(p)
        assertFalse(mardi.nuitActive || mardi.weekendActif)
        val manuel = samediNuit.copy(supplementsAuto = false, nuitActive = false).proposerSupplements(p)
        assertFalse(manuel.nuitActive)
    }

    @Test
    fun `conversion en mission et calcul du total`() {
        val f = FormulaireMission(
            numeroCac = "X", heureAppel = "9h05", distanceKm = "52", kmFacturables = "42",
            tarifBase = "300", tarifKm = "5,5", nuitActive = true, supplementNuit = "100",
            weekendActif = false, supplementWeekend = "50", fraisAutoroute = "23,50", montantRegle = "1 000",
        )
        val (m, erreurs) = f.versMission()
        assertTrue(erreurs.isEmpty())
        m!!
        assertEquals("09:05", m.heureAppel)
        assertEquals(0.0, m.supplementWeekend, 0.0) // interrupteur désactivé
        assertEquals(300 + 42 * 5.5 + 100 + 23.5, Facturation.total(m), 1e-9)
        assertEquals(1000.0, m.montantRegle, 0.0)
    }

    @Test
    fun `montants illisibles signales`() {
        val (m, erreurs) = FormulaireMission(numeroCac = "X", tarifBase = "abc", kmDepart = "12x").versMission()
        assertNull(m)
        assertEquals(2, erreurs.size)
    }

    @Test
    fun `aller-retour mission formulaire`() {
        val m = Donnees.mission("CAC-9").copy(id = 4)
        val (relue, e) = FormulaireMission.depuis(m, p.kmInclus).versMission()
        assertTrue(e.isEmpty())
        assertEquals(m, relue)
    }

    @Test
    fun `parametres - validation`() {
        val ok = FormulaireParametres(tarifBase = "350", tarifKm = "6,5", kmInclus = "15", heureDebutNuit = "21h00").versParametres()
        assertEquals(350.0, ok.first!!.tarifBase, 0.0)
        assertEquals(6.5, ok.first!!.tarifKm, 0.0)
        assertEquals("21:00", ok.first!!.heureDebutNuit)
        val ko = FormulaireParametres(tarifBase = "-5", heureFinNuit = "99:00").versParametres()
        assertNull(ko.first)
        assertEquals(2, ko.second.size)
    }
}
