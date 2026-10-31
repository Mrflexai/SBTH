package ma.remorquepro.core

import ma.remorquepro.core.backup.CodecSauvegarde
import ma.remorquepro.core.backup.Sauvegarde
import ma.remorquepro.core.backup.SauvegardeInvalideException
import ma.remorquepro.core.model.Mission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SauvegardeTest {
    @Test
    fun `aller-retour complet sans perte`() {
        val missions = JeuDeDonnees.missions(25) +
            Mission(id = 99, numeroCac = "Spécial « é » \"guillemets\"", dateReglement = LocalDate.of(2026, 11, 2), observations = "Ligne 1\nLigne 2 — ملاحظة")
        val s = Sauvegarde(creeLe = "2026-10-31T21:00", parametres = JeuDeDonnees.parametres, missions = missions)
        val texte = CodecSauvegarde.encoder(s)
        assertTrue(texte.contains("\"format\": \"remorquepro-cac-sauvegarde\""))
        val relu = CodecSauvegarde.decoder(texte)
        assertEquals(s, relu)
    }

    @Test
    fun `champs inconnus ignores et champs absents par defaut`() {
        val texte = """{"format":"remorquepro-cac-sauvegarde","version":1,"futur":42,
            "missions":[{"id":3,"numeroCac":"Z","dateReception":"2026-01-02","nouveauChamp":"x"}]}"""
        val s = CodecSauvegarde.decoder(texte)
        assertEquals("Z", s.missions.single().numeroCac)
        assertEquals(LocalDate.of(2026, 1, 2), s.missions.single().dateReception)
        assertEquals(null, s.missions.single().dateReglement)
    }

    @Test(expected = SauvegardeInvalideException::class)
    fun `fichier etranger refuse`() {
        CodecSauvegarde.decoder("""{"format":"autre-appli","version":1}""")
    }

    @Test(expected = SauvegardeInvalideException::class)
    fun `fichier corrompu refuse`() {
        CodecSauvegarde.decoder("pas du json")
    }

    @Test(expected = SauvegardeInvalideException::class)
    fun `version future refusee`() {
        CodecSauvegarde.decoder("""{"format":"remorquepro-cac-sauvegarde","version":99}""")
    }
}
