@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ma.remorquepro.cac.ui.missions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.cac.ui.composants.ChampDate
import ma.remorquepro.cac.ui.composants.ChampTexte
import ma.remorquepro.cac.ui.composants.DialogueConfirmation
import ma.remorquepro.cac.ui.composants.PucePaiement
import ma.remorquepro.cac.ui.composants.PuceStatut
import ma.remorquepro.cac.ui.composants.SANS_MARGES
import ma.remorquepro.cac.ui.composants.vmAvec
import ma.remorquepro.core.domain.Facturation
import ma.remorquepro.core.domain.FiltreMissions
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Periode
import ma.remorquepro.core.domain.Statistiques
import ma.remorquepro.core.domain.Synthese
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.StatutMission
import ma.remorquepro.core.model.StatutPaiement
import java.time.LocalDate
import java.time.YearMonth

/** Raccourcis de période proposés dans les filtres. */
enum class RaccourciPeriode(val libelle: String) {
    TOUT("Toutes"), JOUR("Aujourd'hui"), SEMAINE("Cette semaine"), MOIS("Ce mois"), MOIS_PRECEDENT("Mois précédent"), PERSO("Personnalisée")
}

data class EtatHistorique(val missions: List<Mission>, val synthese: Synthese, val total: Int)

class HistoriqueViewModel(private val depot: Depot) : ViewModel() {
    val filtre = MutableStateFlow(FiltreMissions())
    val raccourci = MutableStateFlow(RaccourciPeriode.TOUT)
    val debutPerso = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val finPerso = MutableStateFlow(LocalDate.now())

    val etat: StateFlow<EtatHistorique?> = combine(depot.missions, filtre) { toutes, f ->
        val res = f.appliquer(toutes)
        EtatHistorique(res, Statistiques.synthese(res), toutes.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun modifier(transformation: (FiltreMissions) -> FiltreMissions) = filtre.update(transformation)

    fun choisirPeriode(r: RaccourciPeriode) {
        raccourci.value = r
        appliquerPeriode()
    }

    fun definirPerso(debut: LocalDate?, fin: LocalDate?) {
        debut?.let { debutPerso.value = it }
        fin?.let { finPerso.value = it }
        appliquerPeriode()
    }

    private fun appliquerPeriode() {
        val auj = LocalDate.now()
        val p = when (raccourci.value) {
            RaccourciPeriode.TOUT -> null
            RaccourciPeriode.JOUR -> Periode.jour(auj)
            RaccourciPeriode.SEMAINE -> Periode.semaine(auj)
            RaccourciPeriode.MOIS -> Periode.mois(YearMonth.from(auj))
            RaccourciPeriode.MOIS_PRECEDENT -> Periode.mois(YearMonth.from(auj).minusMonths(1))
            RaccourciPeriode.PERSO -> {
                val d = debutPerso.value
                val f = finPerso.value
                if (f.isBefore(d)) Periode.personnalisee(f, d) else Periode.personnalisee(d, f)
            }
        }
        filtre.update { it.copy(periode = p) }
    }

    fun reinitialiser() {
        raccourci.value = RaccourciPeriode.TOUT
        filtre.value = FiltreMissions()
    }

    fun supprimer(id: Long, apres: () -> Unit) = viewModelScope.launch {
        depot.supprimer(id)
        apres()
    }
}

@Composable
fun EcranHistorique(
    depot: Depot,
    onNouvelleMission: () -> Unit,
    onOuvrir: (Long) -> Unit,
    onModifier: (Long) -> Unit,
    onDupliquer: (Long) -> Unit,
) {
    val vm = vmAvec { HistoriqueViewModel(depot) }
    val etat by vm.etat.collectAsStateWithLifecycle()
    val filtre by vm.filtre.collectAsStateWithLifecycle()
    val raccourci by vm.raccourci.collectAsStateWithLifecycle()
    val debutPerso by vm.debutPerso.collectAsStateWithLifecycle()
    val finPerso by vm.finPerso.collectAsStateWithLifecycle()
    var filtresOuverts by remember { mutableStateOf(false) }
    var aSupprimer by remember { mutableStateOf<Mission?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val portee = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = SANS_MARGES,
        topBar = { TopAppBar(title = { Text("Historique des missions", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNouvelleMission) { Icon(Icons.Default.Add, "Nouvelle mission") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { marges ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(marges),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filtre.texte,
                    onValueChange = { t -> vm.modifier { it.copy(texte = t) } },
                    placeholder = { Text("Rechercher (n°, nom, immat., ville…)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (filtre.texte.isNotEmpty()) {
                            IconButton(onClick = { vm.modifier { it.copy(texte = "") } }) { Icon(Icons.Default.Clear, "Effacer") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recherche_missions"),
                )
                val nbFiltres = listOf(
                    filtre.numeroCac.isNotBlank(), filtre.periode != null, filtre.immatriculation.isNotBlank(),
                    filtre.villeDepart.isNotBlank(), filtre.villeDestination.isNotBlank(), filtre.chauffeur.isNotBlank(),
                    filtre.statuts.isNotEmpty(), filtre.statutsPaiement.isNotEmpty(),
                ).count { it }
                TextButton(onClick = { filtresOuverts = !filtresOuverts }) {
                    BadgedBox(badge = { if (nbFiltres > 0) Badge { Text("$nbFiltres") } }) {
                        Text(if (filtresOuverts) "Masquer" else "Filtres")
                    }
                }
            }
            AnimatedVisibility(filtresOuverts) {
                PanneauFiltres(
                    filtre = filtre,
                    raccourci = raccourci,
                    debutPerso = debutPerso,
                    finPerso = finPerso,
                    vm = vm,
                )
            }
            val e = etat
            if (e != null) {
                Text(
                    "${e.missions.size} mission(s) sur ${e.total} • Facturé ${Formats.montantDh(e.synthese.totalFacture)} • Reste ${Formats.montantDh(e.synthese.resteAEncaisser)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                if (e.missions.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            if (e.total == 0) "Aucune mission enregistrée.\nAppuyez sur « + » pour créer la première."
                            else "Aucune mission ne correspond aux filtres.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("liste_missions"),
                ) {
                    items(e.missions, key = { it.id }) { m ->
                        CarteMission(
                            m = m,
                            onOuvrir = { onOuvrir(m.id) },
                            onModifier = { onModifier(m.id) },
                            onDupliquer = { onDupliquer(m.id) },
                            onSupprimer = { aSupprimer = m },
                        )
                    }
                }
            }
        }
    }

    aSupprimer?.let { m ->
        DialogueConfirmation(
            titre = "Supprimer la mission ?",
            texte = "La mission ${m.numeroCac} sera définitivement supprimée de cet appareil.",
            libelleConfirmer = "Supprimer",
            onConfirmer = {
                aSupprimer = null
                vm.supprimer(m.id) { portee.launch { snackbar.showSnackbar("Mission ${m.numeroCac} supprimée") } }
            },
            onAnnuler = { aSupprimer = null },
        )
    }
}

@Composable
private fun PanneauFiltres(
    filtre: FiltreMissions,
    raccourci: RaccourciPeriode,
    debutPerso: LocalDate,
    finPerso: LocalDate,
    vm: HistoriqueViewModel,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Période", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RaccourciPeriode.entries.forEach { r ->
                FilterChip(selected = raccourci == r, onClick = { vm.choisirPeriode(r) }, label = { Text(r.libelle) })
            }
        }
        if (raccourci == RaccourciPeriode.PERSO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampDate("Du", debutPerso, { vm.definirPerso(it, null) }, Modifier.weight(1f))
                ChampDate("Au", finPerso, { vm.definirPerso(null, it) }, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampTexte(filtre.numeroCac, { t -> vm.modifier { it.copy(numeroCac = t) } }, "N° mission CAC", Modifier.weight(1f))
            ChampTexte(filtre.immatriculation, { t -> vm.modifier { it.copy(immatriculation = t) } }, "Immatriculation", Modifier.weight(1f), majuscules = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampTexte(filtre.villeDepart, { t -> vm.modifier { it.copy(villeDepart = t) } }, "Ville de départ", Modifier.weight(1f))
            ChampTexte(filtre.villeDestination, { t -> vm.modifier { it.copy(villeDestination = t) } }, "Ville de destination", Modifier.weight(1f))
        }
        ChampTexte(filtre.chauffeur, { t -> vm.modifier { it.copy(chauffeur = t) } }, "Chauffeur")
        Text("Statut de mission", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatutMission.entries.forEach { s ->
                FilterChip(
                    selected = s in filtre.statuts,
                    onClick = { vm.modifier { f -> f.copy(statuts = if (s in f.statuts) f.statuts - s else f.statuts + s) } },
                    label = { Text(s.libelleCourt) },
                )
            }
        }
        Text("Statut de paiement", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatutPaiement.entries.forEach { s ->
                FilterChip(
                    selected = s in filtre.statutsPaiement,
                    onClick = {
                        vm.modifier { f -> f.copy(statutsPaiement = if (s in f.statutsPaiement) f.statutsPaiement - s else f.statutsPaiement + s) }
                    },
                    label = { Text(s.libelle) },
                )
            }
        }
        TextButton(onClick = { vm.reinitialiser() }) { Text("Réinitialiser les filtres") }
        HorizontalDivider()
    }
}

@Composable
private fun CarteMission(
    m: Mission,
    onOuvrir: () -> Unit,
    onModifier: () -> Unit,
    onDupliquer: () -> Unit,
    onSupprimer: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOuvrir),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.numeroCac, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Formats.date(m.dateReception) + (m.heureAppel?.let { " à $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Consulter") }, onClick = { menu = false; onOuvrir() })
                        DropdownMenuItem(text = { Text("Modifier") }, onClick = { menu = false; onModifier() })
                        DropdownMenuItem(text = { Text("Dupliquer") }, onClick = { menu = false; onDupliquer() })
                        DropdownMenuItem(text = { Text("Supprimer") }, onClick = { menu = false; onSupprimer() })
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val vehicule = listOf(m.vehiculeMarqueModele, m.vehiculeImmatriculation).filter { it.isNotBlank() }.joinToString(" • ")
                if (vehicule.isNotBlank()) Text(vehicule, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${m.villeDepart.ifBlank { m.lieuPriseEnCharge.ifBlank { "?" } }} → ${m.villeDestination.ifBlank { m.lieuDestination.ifBlank { "?" } }}" +
                        " • ${Formats.km(m.distanceKm)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PuceStatut(m.statut)
                    PucePaiement(Facturation.statutPaiement(m))
                    Text(
                        Formats.montantDh(Facturation.total(m)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
