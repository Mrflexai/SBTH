@file:OptIn(ExperimentalMaterial3Api::class)

package ma.remorquepro.cac.ui.parametres

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.remorquepro.cac.BuildConfig
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.cac.ui.composants.ChampHeure
import ma.remorquepro.cac.ui.composants.ChampTexte
import ma.remorquepro.cac.ui.composants.DialogueConfirmation
import ma.remorquepro.cac.ui.composants.SANS_MARGES
import ma.remorquepro.cac.ui.composants.SectionCarte
import ma.remorquepro.cac.ui.composants.vmAvec
import ma.remorquepro.cac.ui.missions.FormulaireMission
import ma.remorquepro.cac.util.Fichiers
import ma.remorquepro.core.backup.CodecSauvegarde
import ma.remorquepro.core.backup.Sauvegarde
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Heures
import ma.remorquepro.core.model.Parametres
import java.time.LocalDate

/** Saisie texte des paramètres (montants gardés en texte pendant la frappe). */
data class FormulaireParametres(
    val nomChauffeur: String = "",
    val nomEntreprise: String = "",
    val immatDepanneuse: String = "",
    val telephone: String = "",
    val ville: String = "",
    val identifiantsLegaux: String = "",
    val tarifBase: String = "",
    val tarifKm: String = "",
    val kmInclus: String = "",
    val supplementNuit: String = "",
    val supplementWeekend: String = "",
    val heureDebutNuit: String = "20:00",
    val heureFinNuit: String = "06:00",
    val samediEstWeekend: Boolean = true,
) {
    fun versParametres(): Pair<Parametres?, List<String>> {
        val erreurs = mutableListOf<String>()
        fun montant(t: String, nom: String): Double {
            val v = Formats.parseMontant(t)
            if (v == null || v < 0) {
                erreurs += "$nom : montant invalide."
                return 0.0
            }
            return v
        }
        val km = if (kmInclus.isBlank()) 0 else Formats.parseEntier(kmInclus) ?: -1
        if (km < 0) erreurs += "Km inclus : nombre entier positif attendu."
        val debut = Heures.normaliser(heureDebutNuit)
        val fin = Heures.normaliser(heureFinNuit)
        if (debut == null || fin == null) erreurs += "Plage de nuit : heures au format HH:mm attendues."
        val p = Parametres(
            nomChauffeur = nomChauffeur,
            nomEntreprise = nomEntreprise,
            immatDepanneuse = immatDepanneuse,
            telephone = telephone,
            ville = ville,
            identifiantsLegaux = identifiantsLegaux,
            tarifBase = montant(tarifBase, "Tarif de base"),
            tarifKm = montant(tarifKm, "Tarif kilométrique"),
            kmInclus = km.coerceAtLeast(0),
            supplementNuit = montant(supplementNuit, "Supplément de nuit"),
            supplementWeekend = montant(supplementWeekend, "Supplément de week-end"),
            heureDebutNuit = debut ?: "20:00",
            heureFinNuit = fin ?: "06:00",
            samediEstWeekend = samediEstWeekend,
        )
        return (if (erreurs.isEmpty()) p else null) to erreurs
    }

    companion object {
        fun depuis(p: Parametres) = FormulaireParametres(
            nomChauffeur = p.nomChauffeur,
            nomEntreprise = p.nomEntreprise,
            immatDepanneuse = p.immatDepanneuse,
            telephone = p.telephone,
            ville = p.ville,
            identifiantsLegaux = p.identifiantsLegaux,
            tarifBase = FormulaireMission.montantTexte(p.tarifBase),
            tarifKm = FormulaireMission.montantTexte(p.tarifKm),
            kmInclus = if (p.kmInclus == 0) "" else p.kmInclus.toString(),
            supplementNuit = FormulaireMission.montantTexte(p.supplementNuit),
            supplementWeekend = FormulaireMission.montantTexte(p.supplementWeekend),
            heureDebutNuit = p.heureDebutNuit,
            heureFinNuit = p.heureFinNuit,
            samediEstWeekend = p.samediEstWeekend,
        )
    }
}

class ParametresViewModel(private val depot: Depot) : ViewModel() {
    val formulaire = MutableStateFlow<FormulaireParametres?>(null)
    val erreurs = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch { formulaire.value = FormulaireParametres.depuis(depot.parametresActuels()) }
    }

    fun modifier(t: (FormulaireParametres) -> FormulaireParametres) {
        formulaire.value = formulaire.value?.let(t)
    }

    fun enregistrer(onOk: () -> Unit) {
        val f = formulaire.value ?: return
        val (p, e) = f.versParametres()
        erreurs.value = e
        if (p == null) return
        viewModelScope.launch {
            depot.enregistrerParametres(p)
            formulaire.value = FormulaireParametres.depuis(p)
            onOk()
        }
    }

    suspend fun contenuSauvegarde(): ByteArray = withContext(Dispatchers.Default) {
        CodecSauvegarde.encoder(depot.creerSauvegarde()).toByteArray(Charsets.UTF_8)
    }

    suspend fun lireSauvegarde(octets: ByteArray): Sauvegarde = withContext(Dispatchers.Default) {
        CodecSauvegarde.decoder(octets.toString(Charsets.UTF_8))
    }

    suspend fun restaurer(s: Sauvegarde) {
        depot.restaurer(s)
        formulaire.value = FormulaireParametres.depuis(s.parametres)
    }
}

@Composable
fun EcranParametres(depot: Depot) {
    val vm = vmAvec { ParametresViewModel(depot) }
    val form by vm.formulaire.collectAsStateWithLifecycle()
    val erreurs by vm.erreurs.collectAsStateWithLifecycle()
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sauvegardeEnAttente by remember { mutableStateOf<ByteArray?>(null) }
    var aRestaurer by remember { mutableStateOf<Sauvegarde?>(null) }

    fun message(t: String) {
        portee.launch { snackbar.showSnackbar(t) }
    }

    val creerFichier = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Fichiers.MIME_JSON)) { uri ->
        val octets = sauvegardeEnAttente
        sauvegardeEnAttente = null
        if (uri != null && octets != null) {
            runCatching { Fichiers.ecrire(contexte, uri, octets) }
                .onSuccess { message("Sauvegarde enregistrée") }
                .onFailure { message("Échec de la sauvegarde : ${it.message}") }
        }
    }
    val ouvrirFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            portee.launch {
                runCatching { vm.lireSauvegarde(withContext(Dispatchers.IO) { Fichiers.lire(contexte, uri) }) }
                    .onSuccess { aRestaurer = it }
                    .onFailure { message(it.message ?: "Fichier invalide") }
            }
        }
    }

    Scaffold(
        contentWindowInsets = SANS_MARGES,
        topBar = { TopAppBar(title = { Text("Paramètres", fontWeight = FontWeight.SemiBold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { marges ->
        val f = form ?: return@Scaffold
        Column(
            Modifier
                .padding(marges)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCarte("Chauffeur et entreprise") {
                ChampTexte(f.nomChauffeur, { v -> vm.modifier { it.copy(nomChauffeur = v) } }, "Nom du chauffeur", tag = "param_chauffeur")
                ChampTexte(f.nomEntreprise, { v -> vm.modifier { it.copy(nomEntreprise = v) } }, "Entreprise de remorquage")
                ChampTexte(
                    f.immatDepanneuse, { v -> vm.modifier { it.copy(immatDepanneuse = v) } }, "Immatriculation de la dépanneuse",
                    majuscules = true,
                )
                ChampTexte(f.telephone, { v -> vm.modifier { it.copy(telephone = v) } }, "Téléphone", clavier = KeyboardType.Phone)
                ChampTexte(f.ville, { v -> vm.modifier { it.copy(ville = v) } }, "Ville")
                ChampTexte(
                    f.identifiantsLegaux, { v -> vm.modifier { it.copy(identifiantsLegaux = v) } }, "Identifiants légaux",
                    aide = "Facultatif : ICE, RC, IF… (affichés sur le PDF)",
                )
            }

            SectionCarte("Tarifs convenus avec CAC") {
                Text(
                    "Aucun tarif officiel CAC n'est fourni par l'application. Saisissez uniquement les tarifs " +
                        "réellement convenus avec CAC. Ils sont copiés dans chaque nouvelle mission (modifiables mission par mission).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampTexte(
                        f.tarifBase, { v -> vm.modifier { it.copy(tarifBase = v) } }, "Tarif de base", Modifier.weight(1f),
                        clavier = KeyboardType.Decimal, suffixe = "DH", tag = "param_tarif_base",
                    )
                    ChampTexte(
                        f.tarifKm, { v -> vm.modifier { it.copy(tarifKm = v) } }, "Tarif par km", Modifier.weight(1f),
                        clavier = KeyboardType.Decimal, suffixe = "DH", tag = "param_tarif_km",
                    )
                }
                ChampTexte(
                    f.kmInclus, { v -> vm.modifier { it.copy(kmInclus = v.filter(Char::isDigit)) } }, "Km inclus dans le tarif de base",
                    clavier = KeyboardType.Number, suffixe = "km",
                    aide = "Km facturables proposés = distance parcourue − km inclus (0 si aucun)",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampTexte(
                        f.supplementNuit, { v -> vm.modifier { it.copy(supplementNuit = v) } }, "Supplément nuit", Modifier.weight(1f),
                        clavier = KeyboardType.Decimal, suffixe = "DH",
                    )
                    ChampTexte(
                        f.supplementWeekend, { v -> vm.modifier { it.copy(supplementWeekend = v) } }, "Supplément week-end",
                        Modifier.weight(1f), clavier = KeyboardType.Decimal, suffixe = "DH",
                    )
                }
                Text("Plage horaire de nuit (proposition automatique)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampHeure("Début", f.heureDebutNuit, { v -> vm.modifier { it.copy(heureDebutNuit = v) } }, Modifier.weight(1f))
                    ChampHeure("Fin", f.heureFinNuit, { v -> vm.modifier { it.copy(heureFinNuit = v) } }, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Le samedi compte comme week-end", Modifier.weight(1f))
                    Switch(checked = f.samediEstWeekend, onCheckedChange = { c -> vm.modifier { it.copy(samediEstWeekend = c) } })
                }
            }

            erreurs.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.enregistrer { message("Paramètres enregistrés") } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bouton_enregistrer_parametres"),
            ) { Text("Enregistrer les paramètres") }

            SectionCarte("Sauvegarde locale") {
                Text(
                    "Les données restent sur ce téléphone. Créez régulièrement une sauvegarde (fichier JSON) " +
                        "et copiez-la sur une carte SD, une clé USB ou un ordinateur.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        portee.launch {
                            runCatching { vm.contenuSauvegarde() }
                                .onSuccess {
                                    sauvegardeEnAttente = it
                                    creerFichier.launch("RemorquePro_sauvegarde_${LocalDate.now()}.json")
                                }
                                .onFailure { message("Erreur : ${it.message}") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Créer une sauvegarde…") }
                OutlinedButton(
                    onClick = { ouvrirFichier.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restaurer une sauvegarde…") }
            }

            SectionCarte("À propos") {
                Text("Remorque Pro ${BuildConfig.VERSION_NAME} — missions de remorquage CAC")
                Text(
                    "Application 100 % hors ligne : aucun compte, aucun serveur, aucune connexion Internet. " +
                        "Les rapports PDF et Excel sont des modèles proposés par l'application et ne constituent pas " +
                        "des documents officiels CAC sans validation de leur part.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    aRestaurer?.let { s ->
        DialogueConfirmation(
            titre = "Restaurer cette sauvegarde ?",
            texte = "Sauvegarde du ${s.creeLe.take(16).replace('T', ' ')} : ${s.missions.size} mission(s).\n\n" +
                "TOUTES les données actuelles (missions et paramètres) seront remplacées.",
            libelleConfirmer = "Restaurer",
            onConfirmer = {
                aRestaurer = null
                portee.launch {
                    runCatching { vm.restaurer(s) }
                        .onSuccess { message("Restauration terminée : ${s.missions.size} mission(s)") }
                        .onFailure { message("Échec de la restauration : ${it.message}") }
                }
            },
            onAnnuler = { aRestaurer = null },
        )
    }
}
