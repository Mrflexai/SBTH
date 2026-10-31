@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ma.remorquepro.cac.ui.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.remorquepro.cac.data.Depot
import ma.remorquepro.cac.ui.composants.ChampDate
import ma.remorquepro.cac.ui.composants.ChampHeure
import ma.remorquepro.cac.ui.composants.ChampTexte
import ma.remorquepro.cac.ui.composants.DialogueConfirmation
import ma.remorquepro.cac.ui.composants.LigneInfo
import ma.remorquepro.cac.ui.composants.PucePaiement
import ma.remorquepro.cac.ui.composants.SANS_MARGES
import ma.remorquepro.cac.ui.composants.SectionCarte
import ma.remorquepro.cac.ui.composants.vmAvec
import ma.remorquepro.core.domain.Facturation
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Heures
import ma.remorquepro.core.domain.Missions
import ma.remorquepro.core.domain.ValidationMission
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres
import ma.remorquepro.core.model.StatutMission
import java.time.LocalDate
import java.time.LocalTime

data class EtatEdition(
    val charge: Boolean = false,
    val formulaire: FormulaireMission = FormulaireMission(),
    val parametres: Parametres = Parametres(),
    val erreurs: List<String> = emptyList(),
    val avertissements: List<String> = emptyList(),
    /** Avertissements affichés : l'utilisateur doit confirmer pour enregistrer quand même. */
    val confirmationRequise: Boolean = false,
    val enregistrement: Boolean = false,
)

class EditionViewModel(private val depot: Depot, private val id: Long, private val dupliquer: Boolean) : ViewModel() {
    val etat = MutableStateFlow(EtatEdition())

    init {
        viewModelScope.launch {
            val p = depot.parametresActuels()
            val existante = if (id != 0L) depot.mission(id) else null
            val form = when {
                existante != null && dupliquer ->
                    FormulaireMission.depuis(Missions.dupliquer(existante, LocalDate.now()), p.kmInclus)
                        .copy(heureAppel = Heures.format(LocalTime.now()))
                        .proposerSupplements(p)
                existante != null -> FormulaireMission.depuis(existante, p.kmInclus)
                else -> FormulaireMission.depuis(Missions.nouvelle(p, LocalDate.now(), LocalTime.now()), p.kmInclus)
            }
            etat.value = EtatEdition(charge = true, formulaire = form, parametres = p)
        }
    }

    val estNouvelle: Boolean get() = id == 0L || dupliquer

    fun modifier(t: (FormulaireMission) -> FormulaireMission) =
        etat.update { it.copy(formulaire = t(it.formulaire), confirmationRequise = false, erreurs = emptyList()) }

    fun modifierKm(t: (FormulaireMission) -> FormulaireMission) =
        modifier { t(it).recalculerKm(etat.value.parametres.kmInclus) }

    fun modifierDateOuHeure(t: (FormulaireMission) -> FormulaireMission) =
        modifier { t(it).proposerSupplements(etat.value.parametres) }

    /** Mission calculée pour l'aperçu (null si un champ numérique est illisible). */
    fun apercu(f: FormulaireMission): Mission? = f.versMission().first

    fun enregistrer(forcer: Boolean, onSucces: (Long) -> Unit) {
        val e = etat.value
        if (e.enregistrement) return
        val (mission, erreursSaisie) = e.formulaire.versMission()
        if (mission == null) {
            etat.update { it.copy(erreurs = erreursSaisie) }
            return
        }
        viewModelScope.launch {
            val numeros = depot.numerosExistants(if (estNouvelle) 0L else id)
            val v = ValidationMission.valider(mission, numeros)
            if (!v.estValide) {
                etat.update { it.copy(erreurs = v.erreurs, avertissements = v.avertissements) }
                return@launch
            }
            if (v.avertissements.isNotEmpty() && !forcer) {
                etat.update { it.copy(avertissements = v.avertissements, confirmationRequise = true) }
                return@launch
            }
            etat.update { it.copy(enregistrement = true) }
            val nouvelId = depot.enregistrer(mission)
            onSucces(nouvelId)
        }
    }
}

@Composable
fun EcranEditionMission(
    depot: Depot,
    id: Long,
    dupliquer: Boolean,
    onRetour: () -> Unit,
    onEnregistree: (Long) -> Unit,
) {
    val vm = vmAvec { EditionViewModel(depot, id, dupliquer) }
    val etat by vm.etat.collectAsStateWithLifecycle()
    val titre = when {
        dupliquer -> "Dupliquer la mission"
        id == 0L -> "Nouvelle mission"
        else -> "Modifier la mission"
    }
    Scaffold(
        contentWindowInsets = SANS_MARGES,
        topBar = {
            TopAppBar(
                title = { Text(titre, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(
                        onClick = { vm.enregistrer(false, onEnregistree) },
                        modifier = Modifier.testTag("action_enregistrer"),
                    ) { Icon(Icons.Default.Check, "Enregistrer") }
                },
            )
        },
    ) { marges ->
        if (!etat.charge) return@Scaffold
        Formulaire(
            etat = etat,
            vm = vm,
            onEnregistrer = { vm.enregistrer(false, onEnregistree) },
            modifier = Modifier
                .padding(marges)
                .fillMaxSize()
                .imePadding(),
        )
    }
    if (etat.confirmationRequise) {
        DialogueConfirmation(
            titre = "Vérifier avant d'enregistrer",
            texte = etat.avertissements.joinToString("\n") { "• $it" },
            libelleConfirmer = "Enregistrer quand même",
            onConfirmer = { vm.enregistrer(true, onEnregistree) },
            onAnnuler = { vm.modifier { it } },
        )
    }
}

@Composable
private fun Formulaire(
    etat: EtatEdition,
    vm: EditionViewModel,
    onEnregistrer: () -> Unit,
    modifier: Modifier,
) {
    val f = etat.formulaire
    val p = etat.parametres
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (etat.erreurs.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    etat.erreurs.forEach { Text("• $it", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
        }

        SectionCarte("Mission CAC") {
            ChampTexte(
                f.numeroCac, { v -> vm.modifier { it.copy(numeroCac = v) } }, "N° de mission CAC *",
                majuscules = true, tag = "champ_numero_cac",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampDate(
                    "Date de réception", f.dateReception,
                    { d -> if (d != null) vm.modifierDateOuHeure { it.copy(dateReception = d) } },
                    Modifier.weight(1f),
                )
                ChampHeure(
                    "Heure d'appel", f.heureAppel,
                    { v -> vm.modifierDateOuHeure { it.copy(heureAppel = v) } },
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampTexte(f.chauffeur, { v -> vm.modifier { it.copy(chauffeur = v) } }, "Chauffeur", Modifier.weight(1f))
                ChampTexte(
                    f.immatDepanneuse, { v -> vm.modifier { it.copy(immatDepanneuse = v) } }, "Immat. dépanneuse",
                    Modifier.weight(1f), majuscules = true,
                )
            }
            ChampTexte(f.motif, { v -> vm.modifier { it.copy(motif = v) } }, "Motif de l'intervention", aide = "Ex. : panne, accident, crevaison…")
        }

        SectionCarte("Bénéficiaire et véhicule") {
            ChampTexte(f.beneficiaireNom, { v -> vm.modifier { it.copy(beneficiaireNom = v) } }, "Nom du bénéficiaire", tag = "champ_beneficiaire")
            ChampTexte(
                f.beneficiaireTelephone, { v -> vm.modifier { it.copy(beneficiaireTelephone = v) } }, "Téléphone du bénéficiaire",
                clavier = KeyboardType.Phone,
            )
            ChampTexte(
                f.vehiculeMarqueModele, { v -> vm.modifier { it.copy(vehiculeMarqueModele = v) } }, "Marque et modèle",
                tag = "champ_vehicule",
            )
            ChampTexte(
                f.vehiculeImmatriculation, { v -> vm.modifier { it.copy(vehiculeImmatriculation = v) } }, "Immatriculation du véhicule",
                majuscules = true, tag = "champ_immat",
            )
        }

        SectionCarte("Trajet") {
            ChampTexte(f.villeDepart, { v -> vm.modifier { it.copy(villeDepart = v) } }, "Ville de départ", tag = "champ_ville_depart")
            ChampTexte(
                f.lieuPriseEnCharge, { v -> vm.modifier { it.copy(lieuPriseEnCharge = v) } }, "Lieu de prise en charge",
                aide = "Adresse, autoroute et PK, repère…",
            )
            ChampTexte(
                f.villeDestination, { v -> vm.modifier { it.copy(villeDestination = v) } }, "Ville de destination",
                tag = "champ_ville_destination",
            )
            ChampTexte(
                f.lieuDestination, { v -> vm.modifier { it.copy(lieuDestination = v) } }, "Destination du remorquage",
                aide = "Garage, domicile, fourrière…",
            )
        }

        SectionCarte("Horaires") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampHeure("Départ", f.heureDepart, { v -> vm.modifier { it.copy(heureDepart = v) } }, Modifier.weight(1f))
                ChampHeure("Arrivée sur place", f.heureArrivee, { v -> vm.modifier { it.copy(heureArrivee = v) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampHeure("Chargement", f.heureChargement, { v -> vm.modifier { it.copy(heureChargement = v) } }, Modifier.weight(1f))
                ChampHeure("Livraison", f.heureLivraison, { v -> vm.modifier { it.copy(heureLivraison = v) } }, Modifier.weight(1f))
            }
        }

        SectionCarte("Kilométrage") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampTexte(
                    f.kmDepart, { v -> vm.modifierKm { it.copy(kmDepart = v.filter(Char::isDigit)) } }, "Compteur départ",
                    Modifier.weight(1f), clavier = KeyboardType.Number, suffixe = "km",
                )
                ChampTexte(
                    f.kmArrivee, { v -> vm.modifierKm { it.copy(kmArrivee = v.filter(Char::isDigit)) } }, "Compteur arrivée",
                    Modifier.weight(1f), clavier = KeyboardType.Number, suffixe = "km",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampTexte(
                    f.distanceKm,
                    { v -> vm.modifierKm { it.copy(distanceKm = v.filter(Char::isDigit), distanceAuto = false) } },
                    "Distance parcourue", Modifier.weight(1f), clavier = KeyboardType.Number, suffixe = "km",
                    aide = if (f.distanceAuto) "Calculée (compteur)" else "Saisie manuelle",
                    tag = "champ_distance",
                )
                ChampTexte(
                    f.kmFacturables,
                    { v -> vm.modifier { it.copy(kmFacturables = v.filter(Char::isDigit), kmFacturablesAuto = false) } },
                    "Km facturables", Modifier.weight(1f), clavier = KeyboardType.Number, suffixe = "km",
                    aide = if (f.kmFacturablesAuto) "Auto (−${p.kmInclus} km inclus)" else "Saisie manuelle",
                    tag = "champ_km_facturables",
                )
            }
            if (!f.distanceAuto || !f.kmFacturablesAuto) {
                TextButton(onClick = { vm.modifierKm { it.copy(distanceAuto = true, kmFacturablesAuto = true) } }) {
                    Text("Revenir au calcul automatique des km")
                }
            }
        }

        SectionCarte("Statut") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatutMission.entries.forEach { s ->
                    FilterChip(selected = f.statut == s, onClick = { vm.modifier { it.copy(statut = s) } }, label = { Text(s.libelleCourt) })
                }
            }
        }

        SectionFacturation(f, p, vm)

        SectionCarte("Observations") {
            ChampTexte(f.observations, { v -> vm.modifier { it.copy(observations = v) } }, "Observations", multiligne = true)
        }

        Button(
            onClick = onEnregistrer,
            enabled = !etat.enregistrement,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bouton_enregistrer"),
        ) { Text("Enregistrer la mission") }
        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun SectionFacturation(f: FormulaireMission, p: Parametres, vm: EditionViewModel) {
    SectionCarte("Facturation CAC") {
        Text(
            "Tarifs repris des paramètres (accord réel avec CAC) — modifiables pour cette mission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampTexte(
                f.tarifBase, { v -> vm.modifier { it.copy(tarifBase = v) } }, "Tarif de base", Modifier.weight(1f),
                clavier = KeyboardType.Decimal, suffixe = "DH",
            )
            ChampTexte(
                f.tarifKm, { v -> vm.modifier { it.copy(tarifKm = v) } }, "Tarif par km", Modifier.weight(1f),
                clavier = KeyboardType.Decimal, suffixe = "DH",
            )
        }
        LigneSupplement(
            "Supplément de nuit", f.nuitActive, f.supplementNuit,
            onActive = { a ->
                vm.modifier {
                    it.copy(
                        nuitActive = a, supplementsAuto = false,
                        supplementNuit = if (a && it.supplementNuit.isBlank()) FormulaireMission.montantTexte(p.supplementNuit) else it.supplementNuit,
                    )
                }
            },
            onMontant = { v -> vm.modifier { it.copy(supplementNuit = v, supplementsAuto = false) } },
        )
        LigneSupplement(
            "Supplément de week-end", f.weekendActif, f.supplementWeekend,
            onActive = { a ->
                vm.modifier {
                    it.copy(
                        weekendActif = a, supplementsAuto = false,
                        supplementWeekend = if (a && it.supplementWeekend.isBlank()) FormulaireMission.montantTexte(p.supplementWeekend) else it.supplementWeekend,
                    )
                }
            },
            onMontant = { v -> vm.modifier { it.copy(supplementWeekend = v, supplementsAuto = false) } },
        )
        ChampTexte(
            f.fraisAutoroute, { v -> vm.modifier { it.copy(fraisAutoroute = v) } }, "Frais d'autoroute",
            clavier = KeyboardType.Decimal, suffixe = "DH",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampTexte(
                f.fraisSupplementaires, { v -> vm.modifier { it.copy(fraisSupplementaires = v) } }, "Frais supplémentaires",
                Modifier.weight(1f), clavier = KeyboardType.Decimal, suffixe = "DH",
            )
            ChampTexte(
                f.fraisSupplementairesLibelle, { v -> vm.modifier { it.copy(fraisSupplementairesLibelle = v) } }, "Nature des frais",
                Modifier.weight(1f),
            )
        }
        HorizontalDivider()
        val apercu = vm.apercu(f)
        if (apercu != null) {
            val d = Facturation.detail(apercu)
            LigneInfo("Montant km", "${d.kmFacturables} km × ${Formats.montant(d.tarifKm)} = ${Formats.montantDh(d.montantKm)}")
            LigneInfo("Montant total facturé", Formats.montantDh(d.total), gras = true)
        } else {
            Text("Vérifiez les montants saisis.", color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Text("Règlement CAC", style = MaterialTheme.typography.titleSmall)
        ChampTexte(
            f.montantRegle, { v -> vm.modifier { it.copy(montantRegle = v) } }, "Montant réglé par CAC",
            clavier = KeyboardType.Decimal, suffixe = "DH", tag = "champ_montant_regle",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampDate("Date de règlement", f.dateReglement, { d -> vm.modifier { it.copy(dateReglement = d) } }, Modifier.weight(1f), effacable = true)
            ChampTexte(
                f.referenceReglement, { v -> vm.modifier { it.copy(referenceReglement = v) } }, "Référence",
                Modifier.weight(1f), majuscules = true,
            )
        }
        if (apercu != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Reste à encaisser : ${Formats.montantDh(Facturation.reste(apercu))}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                PucePaiement(Facturation.statutPaiement(apercu))
            }
        }
    }
}

@Composable
private fun LigneSupplement(
    libelle: String,
    actif: Boolean,
    montant: String,
    onActive: (Boolean) -> Unit,
    onMontant: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(libelle)
            Text(
                if (actif) "Appliqué" else "Non appliqué",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actif) {
            ChampTexte(montant, onMontant, "Montant", Modifier.weight(1f), clavier = KeyboardType.Decimal, suffixe = "DH")
        }
        Switch(checked = actif, onCheckedChange = onActive)
    }
}
