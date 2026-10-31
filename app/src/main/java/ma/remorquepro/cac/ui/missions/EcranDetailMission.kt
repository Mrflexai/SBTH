@file:OptIn(ExperimentalMaterial3Api::class)

package ma.remorquepro.cac.ui.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.cac.ui.composants.DialogueConfirmation
import ma.remorquepro.cac.ui.composants.LigneInfo
import ma.remorquepro.cac.ui.composants.PucePaiement
import ma.remorquepro.cac.ui.composants.PuceStatut
import ma.remorquepro.cac.ui.composants.SANS_MARGES
import ma.remorquepro.cac.ui.composants.SectionCarte
import ma.remorquepro.cac.ui.composants.vmAvec
import ma.remorquepro.core.domain.Facturation
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Missions
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.StatutMission
import java.time.LocalTime

/** null = chargement ; Absente = mission supprimée ou introuvable. */
sealed interface EtatDetail {
    data object Chargement : EtatDetail
    data object Absente : EtatDetail
    data class Presente(val mission: Mission) : EtatDetail
}

class DetailViewModel(private val depot: Depot, id: Long) : ViewModel() {
    val etat: StateFlow<EtatDetail> = depot.observerMission(id)
        .map { if (it == null) EtatDetail.Absente else EtatDetail.Presente(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatDetail.Chargement)

    fun changerStatut(m: Mission, s: StatutMission) = viewModelScope.launch {
        depot.enregistrer(Missions.changerStatut(m, s, LocalTime.now()))
    }

    fun supprimer(id: Long, apres: () -> Unit) = viewModelScope.launch {
        depot.supprimer(id)
        apres()
    }
}

@Composable
fun EcranDetailMission(
    depot: Depot,
    id: Long,
    onRetour: () -> Unit,
    onModifier: () -> Unit,
    onDupliquer: () -> Unit,
) {
    val vm = vmAvec { DetailViewModel(depot, id) }
    val etat by vm.etat.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmerSuppression by remember { mutableStateOf(false) }
    var confirmerAnnulation by remember { mutableStateOf(false) }
    val m = (etat as? EtatDetail.Presente)?.mission

    Scaffold(
        contentWindowInsets = SANS_MARGES,
        topBar = {
            TopAppBar(
                title = { Text(m?.numeroCac ?: "Mission", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    if (m != null) {
                        IconButton(onClick = onModifier, modifier = Modifier.testTag("action_modifier")) {
                            Icon(Icons.Default.Edit, "Modifier")
                        }
                        IconButton(onClick = { confirmerSuppression = true }) { Icon(Icons.Default.Delete, "Supprimer") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Plus d'actions") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Dupliquer") }, onClick = { menu = false; onDupliquer() })
                                StatutMission.entries.filter { it != m.statut }.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("Statut : ${s.libelle}") },
                                        onClick = { menu = false; vm.changerStatut(m, s) },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { marges ->
        when (val e = etat) {
            EtatDetail.Chargement -> Unit
            EtatDetail.Absente -> Box(Modifier.fillMaxSize().padding(marges).padding(24.dp)) {
                Text("Cette mission n'existe plus.")
            }
            is EtatDetail.Presente -> ContenuDetail(
                e.mission,
                Modifier
                    .padding(marges)
                    .fillMaxSize(),
                onStatut = { vm.changerStatut(e.mission, it) },
                onAnnuler = { confirmerAnnulation = true },
            )
        }
    }

    if (confirmerSuppression && m != null) {
        DialogueConfirmation(
            titre = "Supprimer la mission ?",
            texte = "La mission ${m.numeroCac} sera définitivement supprimée de cet appareil.",
            libelleConfirmer = "Supprimer",
            onConfirmer = {
                confirmerSuppression = false
                vm.supprimer(m.id, onRetour)
            },
            onAnnuler = { confirmerSuppression = false },
        )
    }
    if (confirmerAnnulation && m != null) {
        DialogueConfirmation(
            titre = "Annuler la mission ?",
            texte = "Le statut passera à « Mission annulée ». Les montants déjà saisis sont conservés.",
            libelleConfirmer = "Annuler la mission",
            onConfirmer = {
                confirmerAnnulation = false
                vm.changerStatut(m, StatutMission.ANNULEE)
            },
            onAnnuler = { confirmerAnnulation = false },
        )
    }
}

@Composable
private fun ContenuDetail(
    m: Mission,
    modifier: Modifier,
    onStatut: (StatutMission) -> Unit,
    onAnnuler: () -> Unit,
) {
    val f = Facturation.detail(m)
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCarte("Statut") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PuceStatut(m.statut)
                PucePaiement(f.statutPaiement)
            }
            val suivant = m.statut.suivant
            if (suivant != null) {
                Button(
                    onClick = { onStatut(suivant) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bouton_statut_suivant"),
                ) { Text("Passer à : ${suivant.libelle}") }
            }
            if (m.statut.estEnCours) {
                OutlinedButton(onClick = onAnnuler, modifier = Modifier.fillMaxWidth()) { Text("Annuler la mission") }
            }
            Text(
                "L'heure de l'étape est enregistrée automatiquement si elle n'a pas été saisie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCarte("Mission CAC") {
            LigneInfo("N° mission CAC", m.numeroCac, gras = true)
            LigneInfo("Date de réception", Formats.date(m.dateReception))
            LigneInfo("Heure d'appel", m.heureAppel.orEmpty())
            LigneInfo("Chauffeur", m.chauffeur)
            LigneInfo("Dépanneuse", m.immatDepanneuse)
            LigneInfo("Motif", m.motif)
        }
        SectionCarte("Bénéficiaire et véhicule") {
            LigneInfo("Bénéficiaire", m.beneficiaireNom)
            LigneInfo("Téléphone", m.beneficiaireTelephone)
            LigneInfo("Véhicule", m.vehiculeMarqueModele)
            LigneInfo("Immatriculation", m.vehiculeImmatriculation)
        }
        SectionCarte("Trajet") {
            LigneInfo("Ville de départ", m.villeDepart)
            LigneInfo("Lieu de prise en charge", m.lieuPriseEnCharge)
            LigneInfo("Ville de destination", m.villeDestination)
            LigneInfo("Lieu de destination", m.lieuDestination)
        }
        SectionCarte("Horaires") {
            LigneInfo("Départ", m.heureDepart.orEmpty())
            LigneInfo("Arrivée sur place", m.heureArrivee.orEmpty())
            LigneInfo("Chargement", m.heureChargement.orEmpty())
            LigneInfo("Livraison", m.heureLivraison.orEmpty())
            if (listOf(m.heureDepart, m.heureArrivee, m.heureChargement, m.heureLivraison).all { it.isNullOrBlank() }) {
                Text("Aucune heure saisie.", style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCarte("Kilométrage") {
            LigneInfo("Compteur départ", m.kmDepart?.let { Formats.km(it) }.orEmpty())
            LigneInfo("Compteur arrivée", m.kmArrivee?.let { Formats.km(it) }.orEmpty())
            LigneInfo("Distance parcourue", Formats.km(m.distanceKm), gras = true)
            LigneInfo("Km facturables", Formats.km(m.kmFacturables), gras = true)
        }
        SectionCarte("Facturation") {
            LigneInfo("Tarif de base", Formats.montantDh(f.tarifBase))
            LigneInfo("Kilomètres", "${f.kmFacturables} km × ${Formats.montant(f.tarifKm)} = ${Formats.montantDh(f.montantKm)}")
            if (f.supplementNuit > 0) LigneInfo("Supplément de nuit", Formats.montantDh(f.supplementNuit))
            if (f.supplementWeekend > 0) LigneInfo("Supplément de week-end", Formats.montantDh(f.supplementWeekend))
            if (f.fraisAutoroute > 0) LigneInfo("Frais d'autoroute", Formats.montantDh(f.fraisAutoroute))
            if (f.fraisSupplementaires > 0) {
                LigneInfo(
                    "Frais supplémentaires" + m.fraisSupplementairesLibelle.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                    Formats.montantDh(f.fraisSupplementaires),
                )
            }
            HorizontalDivider()
            LigneInfo("Montant total facturé", Formats.montantDh(f.total), gras = true)
            LigneInfo("Montant réglé par CAC", Formats.montantDh(f.regle))
            LigneInfo("Reste à encaisser", Formats.montantDh(f.reste), gras = true)
            LigneInfo("Date de règlement", Formats.date(m.dateReglement))
            LigneInfo("Référence du règlement", m.referenceReglement)
        }
        if (m.observations.isNotBlank()) {
            SectionCarte("Observations") { Text(m.observations) }
        }
    }
}
