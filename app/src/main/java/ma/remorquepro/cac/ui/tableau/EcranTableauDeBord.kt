@file:OptIn(ExperimentalMaterial3Api::class)

package ma.remorquepro.cac.ui.tableau

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.cac.ui.composants.PuceStatut
import ma.remorquepro.cac.ui.composants.SANS_MARGES
import ma.remorquepro.cac.ui.composants.vmAvec
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Statistiques
import ma.remorquepro.core.domain.TableauDeBord
import ma.remorquepro.core.model.Mission
import java.time.LocalDate

data class EtatTableau(
    val tableau: TableauDeBord,
    val tarifsConfigures: Boolean,
    val enCours: List<Mission>,
    val nomPrestataire: String,
)

class TableauViewModel(depot: Depot) : ViewModel() {
    val etat: StateFlow<EtatTableau?> = combine(depot.missions, depot.parametres) { missions, p ->
        EtatTableau(
            tableau = Statistiques.tableauDeBord(missions, LocalDate.now()),
            tarifsConfigures = p.tarifsConfigures,
            enCours = missions.filter { it.statut.estEnCours }.take(8),
            nomPrestataire = p.nomEntreprise.ifBlank { p.nomChauffeur },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun EcranTableauDeBord(
    depot: Depot,
    onNouvelleMission: () -> Unit,
    onOuvrirMission: (Long) -> Unit,
    onVoirMissions: () -> Unit,
    onParametres: () -> Unit,
) {
    val vm = vmAvec { TableauViewModel(depot) }
    val etat by vm.etat.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = SANS_MARGES,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tableau de bord", fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(etat?.nomPrestataire?.takeIf { it.isNotBlank() }, "Missions CAC").joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNouvelleMission,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nouvelle mission") },
                modifier = Modifier.testTag("fab_nouvelle_mission"),
            )
        },
    ) { marges ->
        val e = etat ?: return@Scaffold
        val t = e.tableau
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(marges),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!e.tarifsConfigures) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("Tarifs CAC non configurés", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Saisissez les tarifs réellement convenus avec CAC pour calculer les montants.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = onParametres) { Text("Configurer") }
                        }
                    }
                }
            }
            item {
                Text("Activité", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                LigneIndicateurs(
                    "Missions du jour" to t.missionsDuJour.toString(),
                    "Missions du mois" to t.missionsDuMois.toString(),
                )
            }
            item {
                LigneIndicateurs(
                    "Terminées (mois)" to t.missionsTermineesMois.toString(),
                    "En attente" to t.missionsEnAttente.toString(),
                )
            }
            item {
                LigneIndicateurs(
                    "Km parcourus (mois)" to Formats.km(t.kmMois),
                    "Km facturables (mois)" to Formats.km(t.kmFacturablesMois),
                )
            }
            item {
                Text("Facturation CAC — mois en cours", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            item {
                LigneIndicateurs(
                    "Facturé à CAC" to Formats.montantDh(t.factureMois),
                    "Montant réglé" to Formats.montantDh(t.regleMois),
                )
            }
            item {
                LigneIndicateurs(
                    "Reste à encaisser (mois)" to Formats.montantDh(t.resteMois),
                    "Reste à encaisser (total)" to Formats.montantDh(t.resteTotal),
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GraphiqueBarres(
                            titre = "Missions par mois",
                            points = t.historique.map {
                                PointGraphique(Formats.moisCourt(it.mois), Formats.mois(it.mois), it.synthese.nombreMissions.toDouble())
                            },
                            formatValeur = { v -> "${v.toInt()} mission(s)" },
                        )
                        GraphiqueBarres(
                            titre = "Chiffre d'affaires facturé par mois",
                            points = t.historique.map {
                                PointGraphique(Formats.moisCourt(it.mois), Formats.mois(it.mois), it.synthese.totalFacture)
                            },
                            formatValeur = { v -> Formats.montantDh(v) },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Missions en cours",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onVoirMissions) { Text("Tout l'historique") }
                }
            }
            if (e.enCours.isEmpty()) {
                item {
                    Text(
                        "Aucune mission en cours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(e.enCours, key = { it.id }) { m ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOuvrirMission(m.id) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.numeroCac, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(m.vehiculeMarqueModele, m.vehiculeImmatriculation).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${m.villeDepart.ifBlank { "?" }} → ${m.villeDestination.ifBlank { "?" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PuceStatut(m.statut)
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneIndicateurs(a: Pair<String, String>, b: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CarteIndicateur(a.first, a.second, Modifier.weight(1f))
        CarteIndicateur(b.first, b.second, Modifier.weight(1f))
    }
}

@Composable
private fun CarteIndicateur(libelle: String, valeur: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                libelle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                valeur,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
