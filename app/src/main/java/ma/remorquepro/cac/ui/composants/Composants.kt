@file:OptIn(ExperimentalMaterial3Api::class)

package ma.remorquepro.cac.ui.composants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.remorquepro.cac.R
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Heures
import ma.remorquepro.core.model.StatutMission
import ma.remorquepro.core.model.StatutPaiement
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun ChampTexte(
    valeur: String,
    onChange: (String) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier,
    clavier: KeyboardType = KeyboardType.Text,
    majuscules: Boolean = false,
    erreur: String? = null,
    aide: String? = null,
    multiligne: Boolean = false,
    suffixe: String? = null,
    tag: String? = null,
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(libelle) },
        modifier = modifier
            .fillMaxWidth()
            .let { if (tag != null) it.testTag(tag) else it },
        singleLine = !multiligne,
        minLines = if (multiligne) 3 else 1,
        isError = erreur != null,
        suffix = if (suffixe != null) {
            { Text(suffixe) }
        } else {
            null
        },
        supportingText = if (erreur != null || aide != null) {
            { Text(erreur ?: aide.orEmpty()) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = clavier,
            capitalization = when {
                majuscules -> KeyboardCapitalization.Characters
                clavier == KeyboardType.Text -> KeyboardCapitalization.Sentences
                else -> KeyboardCapitalization.None
            },
        ),
    )
}

/** Champ de date en lecture seule ouvrant un calendrier. */
@Composable
fun ChampDate(
    libelle: String,
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    effacable: Boolean = false,
    tag: String? = null,
) {
    var ouvert by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = Formats.date(date),
            onValueChange = {},
            readOnly = true,
            label = { Text(libelle) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (effacable && date != null) {
                    IconButton(onClick = { onChange(null) }) { Icon(Icons.Default.Clear, "Effacer la date") }
                } else {
                    Icon(Icons.Default.DateRange, null)
                }
            },
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (effacable && date != null) 48.dp else 0.dp)
                .clickable { ouvert = true }
                .let { if (tag != null) it.testTag(tag) else it },
        )
    }
    if (ouvert) {
        SelecteurDate(
            initiale = date ?: LocalDate.now(),
            onValider = {
                onChange(it)
                ouvert = false
            },
            onAnnuler = { ouvert = false },
        )
    }
}

@Composable
fun SelecteurDate(initiale: LocalDate, onValider: (LocalDate) -> Unit, onAnnuler: () -> Unit) {
    val etat = rememberDatePickerState(initialSelectedDateMillis = initiale.toEpochDay() * JOUR_MS)
    DatePickerDialog(
        onDismissRequest = onAnnuler,
        confirmButton = {
            TextButton(onClick = {
                etat.selectedDateMillis?.let { onValider(LocalDate.ofEpochDay(Math.floorDiv(it, JOUR_MS))) } ?: onAnnuler()
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    ) {
        DatePicker(state = etat)
    }
}

private const val JOUR_MS = 86_400_000L

/** Champ d'heure « HH:mm » : saisie libre + sélecteur d'horloge + bouton « Maintenant ». */
@Composable
fun ChampHeure(
    libelle: String,
    valeur: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    var ouvert by remember { mutableStateOf(false) }
    val invalide = valeur.isNotBlank() && !Heures.estValide(valeur)
    OutlinedTextField(
        value = valeur,
        onValueChange = { onChange(it.take(5)) },
        label = { Text(libelle) },
        placeholder = { Text("HH:mm") },
        singleLine = true,
        isError = invalide,
        modifier = modifier
            .fillMaxWidth()
            .let { if (tag != null) it.testTag(tag) else it },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            IconButton(onClick = { ouvert = true }) {
                Icon(painterResource(R.drawable.ic_horloge), "Choisir l'heure")
            }
        },
    )
    if (ouvert) {
        val initiale = Heures.parse(valeur) ?: LocalTime.now()
        val etat = rememberTimePickerState(initialHour = initiale.hour, initialMinute = initiale.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { ouvert = false },
            title = { Text(libelle) },
            text = { TimePicker(state = etat) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(Heures.format(LocalTime.of(etat.hour, etat.minute)))
                    ouvert = false
                }) { Text("Valider") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onChange(Heures.format(LocalTime.now()))
                        ouvert = false
                    }) { Text("Maintenant") }
                    TextButton(onClick = { ouvert = false }) { Text("Annuler") }
                }
            },
        )
    }
}

@Composable
fun SectionCarte(titre: String, modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            contenu()
        }
    }
}

@Composable
fun LigneInfo(libelle: String, valeur: String, gras: Boolean = false) {
    if (valeur.isBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            libelle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            valeur,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(0.55f),
            textAlign = TextAlign.End,
        )
    }
}

private data class CouleursPuce(val fond: Color, val texte: Color)

@Composable
private fun Puce(texte: String, couleurs: CouleursPuce, modifier: Modifier = Modifier) {
    Surface(color = couleurs.fond, contentColor = couleurs.texte, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            texte,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun PuceStatut(statut: StatutMission, modifier: Modifier = Modifier) {
    val c = when (statut) {
        StatutMission.RECUE -> CouleursPuce(Color(0xFFE3E7EF), Color(0xFF2B3445))
        StatutMission.EN_ROUTE -> CouleursPuce(Color(0xFFFFE7B3), Color(0xFF5A3B00))
        StatutMission.SUR_PLACE -> CouleursPuce(Color(0xFFFFD8C2), Color(0xFF5C2700))
        StatutMission.CHARGE -> CouleursPuce(Color(0xFFDCE4F5), Color(0xFF14284F))
        StatutMission.LIVRE -> CouleursPuce(Color(0xFFCDEDF0), Color(0xFF0B3C42))
        StatutMission.TERMINEE -> CouleursPuce(Color(0xFFCBEBD9), Color(0xFF0B2E1F))
        StatutMission.ANNULEE -> CouleursPuce(Color(0xFFF9DEDC), Color(0xFF601410))
    }
    Puce(statut.libelle, c, modifier)
}

@Composable
fun PucePaiement(statut: StatutPaiement, modifier: Modifier = Modifier) {
    val c = when (statut) {
        StatutPaiement.SANS_MONTANT -> CouleursPuce(Color(0xFFE3E7EF), Color(0xFF2B3445))
        StatutPaiement.NON_REGLE -> CouleursPuce(Color(0xFFF9DEDC), Color(0xFF601410))
        StatutPaiement.PARTIEL -> CouleursPuce(Color(0xFFFFE7B3), Color(0xFF5A3B00))
        StatutPaiement.REGLE -> CouleursPuce(Color(0xFFCBEBD9), Color(0xFF0B2E1F))
    }
    Puce(statut.libelle, c, modifier)
}

@Composable
fun DialogueConfirmation(
    titre: String,
    texte: String,
    libelleConfirmer: String,
    onConfirmer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = { Text(texte) },
        confirmButton = { TextButton(onClick = onConfirmer) { Text(libelleConfirmer) } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
}
