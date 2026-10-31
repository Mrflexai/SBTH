@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ma.remorquepro.cac.ui.exports

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.cac.ui.composants.ChampDate
import ma.remorquepro.cac.ui.composants.LigneInfo
import ma.remorquepro.cac.ui.composants.SANS_MARGES
import ma.remorquepro.cac.ui.composants.SectionCarte
import ma.remorquepro.cac.ui.composants.vmAvec
import ma.remorquepro.cac.util.Fichiers
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Periode
import ma.remorquepro.core.domain.TypePeriode
import ma.remorquepro.core.export.DonneesRapport
import ma.remorquepro.core.export.ExportPdfCac
import ma.remorquepro.core.export.ExportXlsxCac
import ma.remorquepro.core.export.MENTION_NON_OFFICIEL
import java.time.LocalDate
import java.time.YearMonth

enum class FormatExport(val extension: String, val mime: String, val libelle: String) {
    XLSX("xlsx", Fichiers.MIME_XLSX, "Excel"),
    PDF("pdf", Fichiers.MIME_PDF, "PDF"),
}

data class ChoixPeriode(
    val type: TypePeriode = TypePeriode.MOIS,
    val date: LocalDate = LocalDate.now(),
    val mois: YearMonth = YearMonth.now(),
    val debut: LocalDate = LocalDate.now().withDayOfMonth(1),
    val fin: LocalDate = LocalDate.now(),
) {
    fun periode(): Periode = when (type) {
        TypePeriode.JOUR -> Periode.jour(date)
        TypePeriode.SEMAINE -> Periode.semaine(date)
        TypePeriode.MOIS -> Periode.mois(mois)
        TypePeriode.PERSONNALISEE -> if (fin.isBefore(debut)) Periode.personnalisee(fin, debut) else Periode.personnalisee(debut, fin)
        TypePeriode.TOUT -> Periode.tout()
    }
}

class ExportsViewModel(private val depot: Depot) : ViewModel() {
    val choix = MutableStateFlow(ChoixPeriode())

    val apercu: StateFlow<DonneesRapport?> = combine(depot.missions, depot.parametres, choix) { m, p, c ->
        DonneesRapport(p, c.periode(), m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Génère le fichier pour la période choisie (hors du fil principal). */
    suspend fun generer(format: FormatExport): Pair<String, ByteArray> = withContext(Dispatchers.Default) {
        val d = DonneesRapport(depot.parametres.first(), choix.value.periode(), depot.missions.first())
        val octets = when (format) {
            FormatExport.XLSX -> ExportXlsxCac.generer(d)
            FormatExport.PDF -> ExportPdfCac.generer(d)
        }
        d.nomFichier(format.extension) to octets
    }
}

@Composable
fun EcranExports(depot: Depot) {
    val vm = vmAvec { ExportsViewModel(depot) }
    val choix by vm.choix.collectAsStateWithLifecycle()
    val apercu by vm.apercu.collectAsStateWithLifecycle()
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var enAttente by remember { mutableStateOf<ByteArray?>(null) }

    fun message(t: String) {
        portee.launch { snackbar.showSnackbar(t) }
    }

    fun ecrireResultat(uri: android.net.Uri?) {
        val octets = enAttente
        enAttente = null
        if (uri == null || octets == null) return
        runCatching { Fichiers.ecrire(contexte, uri, octets) }
            .onSuccess { message("Fichier enregistré") }
            .onFailure { message("Échec de l'enregistrement : ${it.message}") }
    }

    val creerXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Fichiers.MIME_XLSX)) { ecrireResultat(it) }
    val creerPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Fichiers.MIME_PDF)) { ecrireResultat(it) }

    fun enregistrer(format: FormatExport) {
        portee.launch {
            runCatching { vm.generer(format) }
                .onSuccess { (nom, octets) ->
                    enAttente = octets
                    when (format) {
                        FormatExport.XLSX -> creerXlsx.launch(nom)
                        FormatExport.PDF -> creerPdf.launch(nom)
                    }
                }
                .onFailure { message("Erreur de génération : ${it.message}") }
        }
    }

    fun partager(format: FormatExport, ctx: Context) {
        portee.launch {
            runCatching {
                val (nom, octets) = vm.generer(format)
                Fichiers.partager(ctx, nom, format.mime, octets)
            }.onFailure { message("Partage impossible : ${it.message}") }
        }
    }

    Scaffold(
        contentWindowInsets = SANS_MARGES,
        topBar = { TopAppBar(title = { Text("Exports pour CAC", fontWeight = FontWeight.SemiBold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { marges ->
        Column(
            Modifier
                .padding(marges)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCarte("Période") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TypePeriode.JOUR, TypePeriode.SEMAINE, TypePeriode.MOIS, TypePeriode.PERSONNALISEE).forEach { t ->
                        FilterChip(
                            selected = choix.type == t,
                            onClick = { vm.choix.value = choix.copy(type = t) },
                            label = { Text(t.libelle) },
                        )
                    }
                }
                when (choix.type) {
                    TypePeriode.JOUR, TypePeriode.SEMAINE -> ChampDate(
                        if (choix.type == TypePeriode.JOUR) "Jour" else "Une date de la semaine",
                        choix.date,
                        { d -> if (d != null) vm.choix.value = choix.copy(date = d) },
                    )
                    TypePeriode.MOIS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.choix.value = choix.copy(mois = choix.mois.minusMonths(1)) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mois précédent")
                        }
                        Text(
                            Formats.mois(choix.mois),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.choix.value = choix.copy(mois = choix.mois.plusMonths(1)) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mois suivant")
                        }
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChampDate("Du", choix.debut, { d -> if (d != null) vm.choix.value = choix.copy(debut = d) }, Modifier.weight(1f))
                        ChampDate("Au", choix.fin, { d -> if (d != null) vm.choix.value = choix.copy(fin = d) }, Modifier.weight(1f))
                    }
                }
                apercu?.let { Text(it.periode.libelle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            apercu?.let { d ->
                val s = d.synthese
                SectionCarte("Aperçu de la période") {
                    LigneInfo("Nombre de missions", s.nombreMissions.toString() + if (s.missionsAnnulees > 0) " (+${s.missionsAnnulees} annulée(s))" else "")
                    LigneInfo("Kilomètres parcourus", Formats.km(s.kmParcourus))
                    LigneInfo("Kilomètres facturables", Formats.km(s.kmFacturables))
                    LigneInfo("Montant total facturé", Formats.montantDh(s.totalFacture), gras = true)
                    LigneInfo("Montant encaissé", Formats.montantDh(s.totalEncaisse))
                    LigneInfo("Reste à encaisser", Formats.montantDh(s.resteAEncaisser), gras = true)
                }
            }

            CarteExport(
                titre = "Fichier Excel (XLSX)",
                description = "Onglet « Missions » (N° mission, date, véhicule, immatriculation, départ, destination, km, montant, statut) " +
                    "et onglet « Synthèse mensuelle ».",
                onEnregistrer = { enregistrer(FormatExport.XLSX) },
                onPartager = { partager(FormatExport.XLSX, contexte) },
                tag = "xlsx",
            )
            CarteExport(
                titre = "État mensuel PDF (A4)",
                description = "Document imprimable multipage : prestataire, période, liste détaillée des missions, totaux et solde restant.",
                onEnregistrer = { enregistrer(FormatExport.PDF) },
                onPartager = { partager(FormatExport.PDF, contexte) },
                tag = "pdf",
            )
            Text(MENTION_NON_OFFICIEL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CarteExport(titre: String, description: String, onEnregistrer: () -> Unit, onPartager: () -> Unit, tag: String) {
    SectionCarte(titre) {
        Text(description, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEnregistrer, modifier = Modifier.weight(1f).testTag("enregistrer_$tag")) { Text("Enregistrer…") }
            OutlinedButton(onClick = onPartager, modifier = Modifier.weight(1f).testTag("partager_$tag")) { Text("Partager") }
        }
    }
}
