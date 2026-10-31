package ma.remorquepro.core

import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Heures
import ma.remorquepro.core.domain.Periode
import ma.remorquepro.core.domain.Supplements
import ma.remorquepro.core.domain.TypePeriode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TempsTest {
    @Test
    fun heures() {
        assertEquals("08:05", Heures.normaliser("8:05"))
        assertEquals("23:59", Heures.normaliser("23h59"))
        assertEquals("07:30", Heures.normaliser(" 07H30 "))
        assertNull(Heures.normaliser("24:00"))
        assertNull(Heures.normaliser("8:5"))
        assertNull(Heures.normaliser("abc"))
    }

    @Test
    fun `plage de nuit chevauchant minuit`() {
        assertTrue(Supplements.estNuit("22:15", "20:00", "06:00"))
        assertTrue(Supplements.estNuit("20:00", "20:00", "06:00"))
        assertTrue(Supplements.estNuit("03:00", "20:00", "06:00"))
        assertFalse(Supplements.estNuit("06:00", "20:00", "06:00"))
        assertFalse(Supplements.estNuit("12:00", "20:00", "06:00"))
        assertFalse(Supplements.estNuit(null, "20:00", "06:00"))
        // Plage dans la même journée
        assertTrue(Supplements.estNuit("01:00", "00:00", "05:00"))
        assertFalse(Supplements.estNuit("05:00", "00:00", "05:00"))
    }

    @Test
    fun weekend() {
        val samedi = LocalDate.of(2026, 10, 31)
        val dimanche = LocalDate.of(2026, 11, 1)
        val lundi = LocalDate.of(2026, 11, 2)
        assertTrue(Supplements.estWeekend(samedi, true))
        assertFalse(Supplements.estWeekend(samedi, false))
        assertTrue(Supplements.estWeekend(dimanche, false))
        assertFalse(Supplements.estWeekend(lundi, true))
    }

    @Test
    fun formats() {
        assertEquals("1 234,50", Formats.montant(1234.5))
        assertEquals("0,00", Formats.montant(0.0))
        assertEquals("-12,30", Formats.montant(-12.3))
        assertEquals("1 000 000,00 DH", Formats.montantDh(1_000_000.0))
        assertEquals("12 345 km", Formats.km(12345))
        assertEquals(1234.5, Formats.parseMontant("1 234,5")!!, 0.0)
        assertEquals(1234.5, Formats.parseMontant("1234.50")!!, 0.0)
        assertEquals(0.0, Formats.parseMontant("")!!, 0.0)
        assertNull(Formats.parseMontant("12a"))
        assertEquals("31/10/2026", Formats.date(LocalDate.of(2026, 10, 31)))
        assertEquals("Février 2026", Formats.mois(YearMonth.of(2026, 2)))
    }

    @Test
    fun periodes() {
        val s = Periode.semaine(LocalDate.of(2026, 10, 29)) // jeudi
        assertEquals(LocalDate.of(2026, 10, 26), s.debut)
        assertEquals(LocalDate.of(2026, 11, 1), s.fin)
        val m = Periode.mois(YearMonth.of(2024, 2))
        assertEquals(LocalDate.of(2024, 2, 29), m.fin)
        assertEquals("Février 2024", m.libelle)
        assertTrue(m.contient(LocalDate.of(2024, 2, 1)))
        assertFalse(m.contient(LocalDate.of(2024, 3, 1)))
        val p = Periode.personnalisee(LocalDate.of(2026, 11, 15), LocalDate.of(2027, 1, 5))
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)), p.mois())
        assertEquals(TypePeriode.JOUR, Periode.jour(LocalDate.of(2026, 1, 1)).type)
        assertEquals("2026-10", Periode.mois(YearMonth.of(2026, 10)).suffixeFichier)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `periode inversee refusee`() {
        Periode.personnalisee(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))
    }
}
