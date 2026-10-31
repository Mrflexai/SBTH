package ma.remorquepro.cac

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.remorquepro.cac.data.BaseDeDonnees
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.core.backup.CodecSauvegarde
import ma.remorquepro.core.domain.Facturation
import ma.remorquepro.core.domain.Missions
import ma.remorquepro.core.model.Parametres
import ma.remorquepro.core.model.StatutMission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Persistance SQLite réelle (fichier) via Room. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DepotTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun ouvrir(nom: String = "essai.db") = BaseDeDonnees.creer(ctx, nom)

    @Test
    fun `les donnees persistent apres fermeture et reouverture de la base`() = runBlocking {
        var db = ouvrir()
        var depot = Depot(db)
        val m = Donnees.mission("CAC-0001")
        val id = depot.enregistrer(m)
        depot.enregistrerParametres(Donnees.parametres)
        db.close()

        db = ouvrir()
        depot = Depot(db)
        val relue = depot.mission(id)!!
        assertEquals(m.copy(id = id), relue.copy(creeLe = 0, modifieLe = 0))
        assertTrue(relue.creeLe > 0)
        assertEquals(Donnees.parametres, depot.parametresActuels())
        assertEquals(748.5 - 200.0, Facturation.reste(relue), 0.0)
        db.close()
    }

    @Test
    fun `creer modifier dupliquer supprimer`() = runBlocking {
        val db = ouvrir("crud.db")
        val depot = Depot(db)
        val id = depot.enregistrer(Donnees.mission("CAC-0002"))
        val m = depot.mission(id)!!

        depot.enregistrer(m.copy(statut = StatutMission.ANNULEE, observations = "Client absent"))
        val modifiee = depot.mission(id)!!
        assertEquals(StatutMission.ANNULEE, modifiee.statut)
        assertEquals("Client absent", modifiee.observations)
        assertEquals(m.creeLe, modifiee.creeLe)

        val copie = Missions.dupliquer(modifiee, LocalDate.of(2026, 10, 20)).copy(numeroCac = "CAC-0003")
        val idCopie = depot.enregistrer(copie)
        assertNotEquals(id, idCopie)
        assertEquals(2, depot.nombreMissions())
        assertEquals(listOf("CAC-0003"), depot.numerosExistants(id))

        assertTrue(depot.supprimer(id))
        assertNull(depot.mission(id))
        assertFalse(depot.supprimer(id))
        assertEquals(1, depot.missions.first().size)
        db.close()
    }

    @Test
    fun `sauvegarde puis restauration complete`() = runBlocking {
        val db = ouvrir("sauvegarde.db")
        val depot = Depot(db)
        depot.enregistrerParametres(Donnees.parametres)
        repeat(12) { depot.enregistrer(Donnees.mission("CAC-%03d".format(it), i = it)) }
        val texte = CodecSauvegarde.encoder(depot.creerSauvegarde())
        val avant = depot.toutesMissions()

        // On modifie tout, puis on restaure
        depot.enregistrerParametres(Parametres(nomChauffeur = "Autre"))
        avant.take(5).forEach { depot.supprimer(it.id) }
        depot.enregistrer(Donnees.mission("INTRUS"))

        depot.restaurer(CodecSauvegarde.decoder(texte))
        assertEquals(avant, depot.toutesMissions())
        assertEquals(Donnees.parametres, depot.parametresActuels())
        db.close()
    }
}
