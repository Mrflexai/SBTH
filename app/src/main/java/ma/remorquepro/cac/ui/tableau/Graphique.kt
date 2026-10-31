package ma.remorquepro.cac.ui.tableau

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.min

data class PointGraphique(val libelleCourt: String, val libelleLong: String, val valeur: Double)

/**
 * Histogramme mensuel à une seule série (un seul axe). La valeur du mois sélectionné
 * (dernier mois par défaut) est affichée en clair au-dessus ; toucher une barre la sélectionne.
 */
@Composable
fun GraphiqueBarres(
    titre: String,
    points: List<PointGraphique>,
    formatValeur: (Double) -> String,
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.primary,
) {
    var selection by remember(points) { mutableIntStateOf(points.lastIndex) }
    val mesureur = rememberTextMeasurer()
    val styleAxe = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val couleurGrille = MaterialTheme.colorScheme.outlineVariant
    val couleurBase = MaterialTheme.colorScheme.outline
    val description = titre + " : " + points.joinToString(", ") { "${it.libelleLong} ${formatValeur(it.valeur)}" }

    Column(modifier) {
        Text(titre, style = MaterialTheme.typography.titleSmall)
        val p = points.getOrNull(selection)
        Text(
            if (p != null) "${p.libelleLong} : ${formatValeur(p.valeur)}" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (points.isEmpty()) return@Column
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { pos ->
                        val pas = size.width.toFloat() / points.size
                        selection = (pos.x / pas).toInt().coerceIn(0, points.lastIndex)
                    }
                },
        ) {
            val hautLibelles = 20.dp.toPx()
            val zoneH = size.height - hautLibelles
            val max = points.maxOf { it.valeur }.takeIf { it > 0.0 } ?: 1.0
            for (k in 1..3) {
                val y = zoneH - (zoneH - 6.dp.toPx()) * k / 3f
                drawLine(couleurGrille, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            drawLine(couleurBase, Offset(0f, zoneH), Offset(size.width, zoneH), strokeWidth = 1.5f)
            val pas = size.width / points.size
            val largeurBarre = min(pas * 0.55f, 36.dp.toPx())
            val rayon = 4.dp.toPx()
            points.forEachIndexed { i, pt ->
                val h = (pt.valeur / max * (zoneH - 6.dp.toPx())).toFloat()
                val x = i * pas + (pas - largeurBarre) / 2
                val c = if (i == selection) couleur else couleur.copy(alpha = 0.45f)
                if (h > 0f) {
                    // Extrémité arrondie en haut, base droite posée sur l'axe.
                    drawRoundRect(c, Offset(x, zoneH - h), Size(largeurBarre, h), CornerRadius(rayon, rayon))
                    val bas = min(h, rayon)
                    drawRect(c, Offset(x, zoneH - bas), Size(largeurBarre, bas))
                }
                val texte = mesureur.measure(pt.libelleCourt, styleAxe)
                drawText(texte, topLeft = Offset(i * pas + (pas - texte.size.width) / 2, zoneH + 4.dp.toPx()))
            }
        }
    }
}
