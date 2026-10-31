package ma.remorquepro.cac

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.remorquepro.core.model.StatutMission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Parcours utilisateur réels dans l'interface Compose (exécutés sous Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InterfaceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val depot get() = (compose.activity.application as RemorqueApp).depot

    private fun attendreTexte(t: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun attendreTag(t: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `creation d'une mission depuis le formulaire puis progression du statut`() {
        compose.onNodeWithText("Tableau de bord").assertIsDisplayed()
        compose.onNodeWithTag("fab_nouvelle_mission").performClick()

        attendreTag("champ_numero_cac")
        compose.onNodeWithTag("champ_numero_cac").performTextInput("CAC-UI-001")
        compose.onNodeWithTag("champ_beneficiaire").performScrollTo().performTextInput("Karim Bennani")
        compose.onNodeWithTag("champ_vehicule").performScrollTo().performTextInput("Peugeot 208")
        compose.onNodeWithTag("champ_immat").performScrollTo().performTextInput("77777-B-1")
        compose.onNodeWithTag("champ_ville_depart").performScrollTo().performTextInput("Rabat")
        compose.onNodeWithTag("champ_ville_destination").performScrollTo().performTextInput("Casablanca")
        compose.onNodeWithTag("champ_distance").performScrollTo().performTextInput("90")
        compose.onNodeWithTag("bouton_enregistrer").performScrollTo().performClick()

        // Après enregistrement : fiche de la mission
        attendreTag("bouton_statut_suivant")
        compose.onNodeWithText("Passer à : En route").assertIsDisplayed()
        compose.onNodeWithTag("bouton_statut_suivant").performClick()
        attendreTexte("Passer à : Sur place")

        val missions = runBlocking { depot.toutesMissions() }
        assertEquals(1, missions.size)
        val m = missions.single()
        assertEquals("CAC-UI-001", m.numeroCac)
        assertEquals("77777-B-1", m.vehiculeImmatriculation)
        assertEquals(90, m.distanceKm)
        assertEquals(90, m.kmFacturables)
        assertEquals(StatutMission.EN_ROUTE, m.statut)
        assertNotNull("heure de départ horodatée", m.heureDepart)
    }

    @Test
    fun `numero obligatoire`() {
        compose.onNodeWithTag("fab_nouvelle_mission").performClick()
        attendreTag("bouton_enregistrer")
        compose.onNodeWithTag("bouton_enregistrer").performScrollTo().performClick()
        attendreTexte("numéro de mission CAC est obligatoire")
        assertEquals(0, runBlocking { depot.nombreMissions() })
    }

    @Test
    fun `historique, recherche et onglets`() {
        runBlocking {
            depot.enregistrerParametres(Donnees.parametres)
            depot.enregistrer(Donnees.mission("CAC-A-100", LocalDate.now(), 1))
            depot.enregistrer(Donnees.mission("CAC-B-200", LocalDate.now(), 2).copy(villeDepart = "Fès"))
        }
        compose.onNodeWithTag("onglet_missions").performClick()
        attendreTexte("CAC-A-100")
        attendreTexte("CAC-B-200")
        compose.onNodeWithTag("recherche_missions").performTextInput("fes")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("CAC-A-100").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("CAC-B-200").assertIsDisplayed()

        compose.onNodeWithTag("onglet_exports").performClick()
        attendreTexte("Exports pour CAC")
        compose.onNodeWithTag("enregistrer_pdf").performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("onglet_parametres").performClick()
        attendreTag("param_tarif_base")
        compose.onNodeWithTag("onglet_tableau").performClick()
        attendreTexte("Missions du mois")
    }
}
