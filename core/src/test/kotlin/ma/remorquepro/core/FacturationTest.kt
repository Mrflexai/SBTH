package ma.remorquepro.core

import ma.remorquepro.core.domain.Facturation
import ma.remorquepro.core.domain.Kilometrage
import ma.remorquepro.core.domain.arrondi2
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.StatutPaiement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacturationTest {
    private val m = Mission(
        numeroCac = "X1",
        tarifBase = 300.0,
        tarifKm = 5.0,
        distanceKm = 52,
        kmFacturables = 40,
        supplementNuit = 100.0,
        supplementWeekend = 50.0,
        fraisAutoroute = 23.5,
        fraisSupplementaires = 10.0,
    )

    @Test
    fun `total = base + km facturables x tarif + supplements + frais`() {
        assertEquals(200.0, Facturation.montantKm(m), 0.0)
        assertEquals(683.5, Facturation.total(m), 0.0)
    }

    @Test
    fun `le total utilise les km facturables et non la distance parcourue`() {
        val autre = m.copy(distanceKm = 500)
        assertEquals(Facturation.total(m), Facturation.total(autre), 0.0)
    }

    @Test
    fun `reste et statut de paiement`() {
        assertEquals(StatutPaiement.NON_REGLE, Facturation.statutPaiement(m))
        assertEquals(683.5, Facturation.reste(m), 0.0)

        val partiel = m.copy(montantRegle = 500.0)
        assertEquals(StatutPaiement.PARTIEL, Facturation.statutPaiement(partiel))
        assertEquals(183.5, Facturation.reste(partiel), 0.0)

        val regle = m.copy(montantRegle = 683.5)
        assertEquals(StatutPaiement.REGLE, Facturation.statutPaiement(regle))
        assertEquals(0.0, Facturation.reste(regle), 0.0)

        assertEquals(StatutPaiement.SANS_MONTANT, Facturation.statutPaiement(Mission()))
    }

    @Test
    fun `arrondi au centime sans erreur flottante`() {
        val x = Mission(tarifBase = 0.1, fraisAutoroute = 0.2, tarifKm = 3.333, kmFacturables = 3)
        assertEquals(10.3, Facturation.total(x), 0.0)
        assertEquals(1.01, arrondi2(1.005), 0.0)
    }

    @Test
    fun kilometrage() {
        assertEquals(152, Kilometrage.distanceCompteur(120_000, 120_152))
        assertNull(Kilometrage.distanceCompteur(120_000, null))
        assertNull(Kilometrage.distanceCompteur(120_200, 120_100))
        assertEquals(42, Kilometrage.kmFacturablesParDefaut(52, 10))
        assertEquals(0, Kilometrage.kmFacturablesParDefaut(5, 10))
        assertEquals(52, Kilometrage.kmFacturablesParDefaut(52, 0))
    }
}
