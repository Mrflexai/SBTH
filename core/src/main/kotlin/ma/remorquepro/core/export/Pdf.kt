package ma.remorquepro.core.export

import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Couleur RVB (composantes 0..1). */
data class Couleur(val r: Double, val v: Double, val b: Double) {
    companion object {
        fun hex(rgb: Int) = Couleur(((rgb shr 16) and 0xFF) / 255.0, ((rgb shr 8) and 0xFF) / 255.0, (rgb and 0xFF) / 255.0)
        val NOIR = Couleur(0.0, 0.0, 0.0)
        val BLANC = Couleur(1.0, 1.0, 1.0)
    }
}

enum class Alignement { GAUCHE, CENTRE, DROITE }

/**
 * Générateur PDF minimal (PDF 1.4) sans dépendance : texte Helvetica / Helvetica-Bold
 * (encodage WinAnsi, accents français pris en charge), traits et rectangles, pages multiples.
 *
 * Les coordonnées de l'API sont en points (1/72 pouce) avec l'origine en HAUT à gauche
 * de la page (y vers le bas), plus naturel pour composer un rapport.
 */
class PdfDocumentSimple(
    val largeurPage: Double = A4_LARGEUR,
    val hauteurPage: Double = A4_HAUTEUR,
    private val titre: String = "",
    private val auteur: String = "",
) {
    inner class Page internal constructor() {
        internal val contenu = StringBuilder()

        fun texte(
            x: Double,
            y: Double,
            texte: String,
            taille: Double = 10.0,
            gras: Boolean = false,
            couleur: Couleur = Couleur.NOIR,
            alignement: Alignement = Alignement.GAUCHE,
        ) {
            if (texte.isEmpty()) return
            val w = Polices.largeur(texte, taille, gras)
            val xr = when (alignement) {
                Alignement.GAUCHE -> x
                Alignement.CENTRE -> x - w / 2
                Alignement.DROITE -> x - w
            }
            contenu.append("BT /").append(if (gras) "F2" else "F1").append(' ').append(n(taille)).append(" Tf ")
            contenu.append(couleurRemplissage(couleur))
            contenu.append(n(xr)).append(' ').append(n(hauteurPage - y)).append(" Td ")
            contenu.append(chainePdf(texte)).append(" Tj ET\n")
        }

        /** Texte tronqué avec « … » pour tenir dans la largeur donnée. */
        fun texteCellule(
            x: Double,
            y: Double,
            largeur: Double,
            texte: String,
            taille: Double = 8.0,
            gras: Boolean = false,
            couleur: Couleur = Couleur.NOIR,
            alignement: Alignement = Alignement.GAUCHE,
            marge: Double = 3.0,
        ) {
            val dispo = largeur - 2 * marge
            val t = Polices.tronquer(texte, taille, gras, dispo)
            val xt = when (alignement) {
                Alignement.GAUCHE -> x + marge
                Alignement.CENTRE -> x + largeur / 2
                Alignement.DROITE -> x + largeur - marge
            }
            texte(xt, y, t, taille, gras, couleur, alignement)
        }

        fun trait(x1: Double, y1: Double, x2: Double, y2: Double, epaisseur: Double = 0.5, couleur: Couleur = Couleur.NOIR) {
            contenu.append(n(epaisseur)).append(" w ").append(couleurTrait(couleur))
            contenu.append(n(x1)).append(' ').append(n(hauteurPage - y1)).append(" m ")
            contenu.append(n(x2)).append(' ').append(n(hauteurPage - y2)).append(" l S\n")
        }

        fun rectangle(
            x: Double,
            y: Double,
            largeur: Double,
            hauteur: Double,
            remplissage: Couleur? = null,
            contour: Couleur? = null,
            epaisseur: Double = 0.5,
        ) {
            if (remplissage == null && contour == null) return
            val yPdf = hauteurPage - y - hauteur
            contenu.append("q ")
            remplissage?.let { contenu.append(couleurRemplissage(it)) }
            contour?.let { contenu.append(couleurTrait(it)).append(n(epaisseur)).append(" w ") }
            contenu.append(n(x)).append(' ').append(n(yPdf)).append(' ').append(n(largeur)).append(' ').append(n(hauteur)).append(" re ")
            contenu.append(
                when {
                    remplissage != null && contour != null -> "B"
                    remplissage != null -> "f"
                    else -> "S"
                },
            )
            contenu.append(" Q\n")
        }
    }

    private val pages = mutableListOf<Page>()

    val nombrePages: Int get() = pages.size

    fun nouvellePage(): Page = Page().also { pages += it }

    fun page(index: Int): Page = pages[index]

    fun enOctets(): ByteArray {
        require(pages.isNotEmpty()) { "Le document doit contenir au moins une page" }
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun ecrire(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun objet(num: Int, corps: String) {
            while (offsets.size < num) offsets += 0
            offsets[num - 1] = out.size()
            ecrire("$num 0 obj\n$corps\nendobj\n")
        }

        ecrire("%PDF-1.4\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        // 1 catalogue, 2 arbre des pages, 3-4 polices, 5 infos, puis (page, contenu) par page
        val premierePage = 6
        val kids = pages.indices.joinToString(" ") { "${premierePage + 2 * it} 0 R" }
        objet(1, "<< /Type /Catalog /Pages 2 0 R >>")
        objet(2, "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")
        objet(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        objet(4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT))
        objet(5, "<< /Title ${chaineInfo(titre)} /Author ${chaineInfo(auteur)} /Creator (Remorque Pro) /Producer (Remorque Pro) /CreationDate (D:$date) >>")
        pages.forEachIndexed { i, p ->
            val numPage = premierePage + 2 * i
            val flux = p.contenu.toString()
            objet(
                numPage,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(largeurPage)} ${n(hauteurPage)}] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${numPage + 1} 0 R >>",
            )
            objet(numPage + 1, "<< /Length ${flux.length} >>\nstream\n$flux\nendstream")
        }
        val xref = out.size()
        val sb = StringBuilder()
        sb.append("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { sb.append(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        sb.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Info 5 0 R >>\nstartxref\n$xref\n%%EOF\n")
        ecrire(sb.toString())
        return out.toByteArray()
    }

    private fun couleurRemplissage(c: Couleur) = "${n(c.r)} ${n(c.v)} ${n(c.b)} rg "
    private fun couleurTrait(c: Couleur) = "${n(c.r)} ${n(c.v)} ${n(c.b)} RG "

    companion object {
        const val A4_LARGEUR = 595.28
        const val A4_HAUTEUR = 841.89

        /** Nombre décimal compact, toujours avec un point. */
        internal fun n(v: Double): String {
            val s = String.format(Locale.ROOT, "%.2f", v)
            return s.trimEnd('0').trimEnd('.').ifEmpty { "0" }.let { if (it == "-0") "0" else it }
        }

        /** Chaîne de métadonnées (dictionnaire Info) : UTF-16BE hexadécimal avec BOM. */
        internal fun chaineInfo(s: String): String {
            val sb = StringBuilder("<FEFF")
            s.toByteArray(Charsets.UTF_16BE).forEach { sb.append(String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF)) }
            return sb.append(">").toString()
        }

        /**
         * Chaîne littérale PDF encodée en WinAnsi ; les octets non ASCII sont écrits en
         * séquences octales pour garder un flux de contenu 100 % ASCII.
         */
        internal fun chainePdf(s: String): String {
            val sb = StringBuilder("(")
            for (code in Polices.encoder(s)) {
                val b = code.toInt() and 0xFF
                when {
                    b == '('.code || b == ')'.code || b == '\\'.code -> sb.append('\\').append(b.toChar())
                    b in 32..126 -> sb.append(b.toChar())
                    else -> sb.append('\\').append(String.format(Locale.ROOT, "%03o", b))
                }
            }
            return sb.append(")").toString()
        }
    }
}

/** Métriques des polices standard Helvetica (valeurs AFM, unités de 1/1000 em). */
object Polices {
    // Largeurs ASCII 32..126
    private val HELV = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
    )
    private val HELV_GRAS = intArrayOf(
        278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
        975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
        333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
        611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584,
    )

    /** Correspondance Unicode → WinAnsi (cp1252) pour la plage 0x80..0x9F. */
    private val CP1252 = mapOf(
        '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86, '‡' to 0x87,
        'ˆ' to 0x88, '‰' to 0x89, 'Š' to 0x8A, '‹' to 0x8B, 'Œ' to 0x8C, 'Ž' to 0x8E, '‘' to 0x91,
        '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95, '–' to 0x96, '—' to 0x97, '˜' to 0x98,
        '™' to 0x99, 'š' to 0x9A, '›' to 0x9B, 'œ' to 0x9C, 'ž' to 0x9E, 'Ÿ' to 0x9F,
    )

    /** Encode en WinAnsi ; caractères non représentables → « ? » (ou équivalent sans accent). */
    fun encoder(s: String): ByteArray {
        val out = ByteArray(s.length)
        var i = 0
        for (ch in s) {
            out[i++] = codeWinAnsi(ch).toByte()
        }
        return out
    }

    private fun codeWinAnsi(ch: Char): Int {
        val c = ch.code
        return when {
            c in 32..126 -> c
            c in 0xA0..0xFF -> c
            ch == ' ' || ch == ' ' || ch == ' ' -> 32
            ch == '\t' || ch == '\n' || ch == '\r' -> 32
            CP1252.containsKey(ch) -> CP1252.getValue(ch)
            else -> {
                // Lettre accentuée hors Latin-1 : on garde la lettre de base si possible.
                val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull()
                if (base != null && base.code in 32..126) base.code else '?'.code
            }
        }
    }

    private fun largeurCode(code: Int, gras: Boolean): Int {
        val table = if (gras) HELV_GRAS else HELV
        if (code in 32..126) return table[code - 32]
        return when (code) {
            0xB0 -> 400 // °
            0xAB, 0xBB -> 556 // « »
            0xA0 -> 278
            0xE6 -> 889 // æ
            0xC6 -> 1000 // Æ
            0x9C -> 944 // œ
            0x8C -> 1000 // Œ
            0xDF -> 611 // ß
            0x80 -> 556 // €
            0x85, 0x97 -> 1000 // … —
            0x96 -> 556 // –
            0x92, 0x91 -> if (gras) 278 else 222
            0x93, 0x94 -> if (gras) 500 else 333
            0x95 -> 350
            else -> {
                // Lettres accentuées : même largeur que la lettre de base.
                val ch = String(byteArrayOf(code.toByte()), Charsets.ISO_8859_1)
                val base = Normalizer.normalize(ch, Normalizer.Form.NFD).firstOrNull()?.code ?: 0
                if (base in 32..126) table[base - 32] else 556
            }
        }
    }

    fun largeur(texte: String, taille: Double, gras: Boolean): Double {
        var total = 0
        for (b in encoder(texte)) total += largeurCode(b.toInt() and 0xFF, gras)
        return total * taille / 1000.0
    }

    fun tronquer(texte: String, taille: Double, gras: Boolean, largeurMax: Double): String {
        val propre = texte.replace(Regex("\\s+"), " ").trim()
        if (largeur(propre, taille, gras) <= largeurMax) return propre
        val ellipse = "…"
        var fin = propre.length
        while (fin > 0 && largeur(propre.substring(0, fin).trimEnd() + ellipse, taille, gras) > largeurMax) fin--
        return if (fin == 0) "" else propre.substring(0, fin).trimEnd() + ellipse
    }
}
