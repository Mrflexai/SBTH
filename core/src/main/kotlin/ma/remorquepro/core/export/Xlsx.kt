package ma.remorquepro.core.export

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Générateur minimal de classeurs Excel (.xlsx, Office Open XML) sans dépendance externe.
 * Gère : plusieurs feuilles, texte, nombres, dates, formules avec valeur en cache, styles
 * prédéfinis, largeurs de colonnes, cellules fusionnées, volets figés, filtre automatique
 * et mise en page A4 paysage.
 */
class XlsxClasseur(private val titre: String = "", private val auteur: String = "") {

    /** Styles disponibles (index dans cellXfs de styles.xml). */
    enum class Style(val index: Int) {
        NORMAL(0), TITRE(1), SOUS_TITRE(2), ENTETE(3), TEXTE(4), ENTIER(5), MONTANT(6), DATE(7),
        TOTAL_LIBELLE(8), TOTAL_ENTIER(9), TOTAL_MONTANT(10), NOTE(11), LIBELLE(12),
    }

    sealed class Cellule {
        abstract val style: Style

        data class Texte(val valeur: String, override val style: Style = Style.TEXTE) : Cellule()
        data class Nombre(val valeur: Double, override val style: Style = Style.MONTANT) : Cellule()
        data class Date(val valeur: LocalDate, override val style: Style = Style.DATE) : Cellule()
        data class Formule(val formule: String, val valeurCache: Double, override val style: Style = Style.MONTANT) : Cellule()
        data class Vide(override val style: Style = Style.NORMAL) : Cellule()
    }

    class Feuille(nom: String) {
        val nom: String = nettoyerNomFeuille(nom)
        internal val lignes = mutableListOf<List<Cellule>>()
        internal val largeurs = mutableMapOf<Int, Double>()
        internal val fusions = mutableListOf<String>()
        internal var lignesFigees = 0
        internal var filtre: String? = null

        /** Ajoute une ligne et renvoie son numéro (1-based). */
        fun ligne(vararg cellules: Cellule): Int {
            lignes += cellules.toList()
            return lignes.size
        }

        fun ligneVide(): Int = ligne()

        fun largeur(colonne: Int, largeur: Double) {
            largeurs[colonne] = largeur
        }

        fun fusionner(plage: String) {
            fusions += plage
        }

        fun figerLignes(n: Int) {
            lignesFigees = n
        }

        fun filtreAuto(plage: String) {
            filtre = plage
        }

        val nombreLignes: Int get() = lignes.size
    }

    private val feuilles = mutableListOf<Feuille>()

    fun feuille(nom: String): Feuille = Feuille(nom).also { feuilles += it }

    fun enOctets(): ByteArray {
        require(feuilles.isNotEmpty()) { "Le classeur doit contenir au moins une feuille" }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun entree(nom: String, contenu: String) {
                zip.putNextEntry(ZipEntry(nom))
                zip.write(contenu.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entree("[Content_Types].xml", contentTypes())
            entree("_rels/.rels", RELS)
            entree("docProps/core.xml", coreProps())
            entree("docProps/app.xml", APP_PROPS)
            entree("xl/workbook.xml", workbook())
            entree("xl/_rels/workbook.xml.rels", workbookRels())
            entree("xl/styles.xml", STYLES)
            feuilles.forEachIndexed { i, f -> entree("xl/worksheets/sheet${i + 1}.xml", feuilleXml(f)) }
        }
        return bos.toByteArray()
    }

    private fun contentTypes() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        feuilles.indices.forEach {
            append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        append("""<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""")
        append("</Types>")
    }

    private fun coreProps(): String {
        val maintenant = ZonedDateTime.now(ZoneOffset.UTC).withNano(0).format(DateTimeFormatter.ISO_INSTANT)
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""" +
            "<dc:title>${esc(titre)}</dc:title><dc:creator>${esc(auteur)}</dc:creator>" +
            """<dcterms:created xsi:type="dcterms:W3CDTF">$maintenant</dcterms:created>""" +
            """<dcterms:modified xsi:type="dcterms:W3CDTF">$maintenant</dcterms:modified>""" +
            "</cp:coreProperties>"
    }

    private fun workbook() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<bookViews><workbookView/></bookViews><sheets>")
        feuilles.forEachIndexed { i, f ->
            append("""<sheet name="${esc(f.nom)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets>")
        val filtres = feuilles.withIndex().filter { it.value.filtre != null }
        if (filtres.isNotEmpty()) {
            append("<definedNames>")
            filtres.forEach { (i, f) ->
                val (a, b) = f.filtre!!.split(":")
                append("""<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">""")
                append(esc("'${f.nom.replace("'", "''")}'!${absolu(a)}:${absolu(b)}"))
                append("</definedName>")
            }
            append("</definedNames>")
        }
        append("</workbook>")
    }

    private fun workbookRels() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        feuilles.indices.forEach {
            append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
        }
        append("""<Relationship Id="rId${feuilles.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun feuilleXml(f: Feuille) = buildString {
        val maxCol = (f.lignes.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        val maxLigne = f.lignes.size.coerceAtLeast(1)
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        append("""<dimension ref="A1:${colonne(maxCol)}$maxLigne"/>""")
        append("""<sheetViews><sheetView workbookViewId="0"""")
        if (feuilles.firstOrNull() === f) append(""" tabSelected="1"""")
        append(">")
        if (f.lignesFigees > 0) {
            append("""<pane ySplit="${f.lignesFigees}" topLeftCell="A${f.lignesFigees + 1}" activePane="bottomLeft" state="frozen"/>""")
        }
        append("</sheetView></sheetViews>")
        append("""<sheetFormatPr defaultRowHeight="15"/>""")
        if (f.largeurs.isNotEmpty()) {
            append("<cols>")
            f.largeurs.toSortedMap().forEach { (c, l) ->
                append("""<col min="$c" max="$c" width="$l" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        f.lignes.forEachIndexed { li, cellules ->
            val r = li + 1
            append("""<row r="$r"""")
            if (cellules.any { it.style == Style.TITRE }) append(""" ht="22" customHeight="1"""")
            if (cellules.any { it.style == Style.ENTETE }) append(""" ht="30" customHeight="1"""")
            append(">")
            cellules.forEachIndexed { ci, c ->
                val ref = colonne(ci + 1) + r
                val s = c.style.index
                when (c) {
                    is Cellule.Texte -> append("""<c r="$ref" s="$s" t="inlineStr"><is><t xml:space="preserve">${esc(c.valeur)}</t></is></c>""")
                    is Cellule.Nombre -> append("""<c r="$ref" s="$s"><v>${nombre(c.valeur)}</v></c>""")
                    is Cellule.Date -> append("""<c r="$ref" s="$s"><v>${serieExcel(c.valeur)}</v></c>""")
                    is Cellule.Formule -> append("""<c r="$ref" s="$s"><f>${esc(c.formule)}</f><v>${nombre(c.valeurCache)}</v></c>""")
                    is Cellule.Vide -> if (c.style != Style.NORMAL) append("""<c r="$ref" s="$s"/>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        f.filtre?.let { append("""<autoFilter ref="$it"/>""") }
        if (f.fusions.isNotEmpty()) {
            append("""<mergeCells count="${f.fusions.size}">""")
            f.fusions.forEach { append("""<mergeCell ref="$it"/>""") }
            append("</mergeCells>")
        }
        append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>""")
        append("""<pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/>""")
        append("</worksheet>")
    }

    companion object {
        /** Numéro de colonne (1-based) → lettres Excel (1 → A, 27 → AA). */
        fun colonne(n: Int): String {
            require(n >= 1)
            var x = n
            val sb = StringBuilder()
            while (x > 0) {
                val r = (x - 1) % 26
                sb.insert(0, ('A' + r))
                x = (x - 1) / 26
            }
            return sb.toString()
        }

        /** Numéro de série Excel d'une date (calendrier 1900). */
        fun serieExcel(d: LocalDate): Long = d.toEpochDay() + 25569

        private fun absolu(ref: String): String {
            val m = Regex("^([A-Z]+)(\\d+)$").matchEntire(ref) ?: return ref
            return "$" + m.groupValues[1] + "$" + m.groupValues[2]
        }

        private fun nombre(v: Double): String =
            if (v.isNaN() || v.isInfinite()) "0"
            else if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
            else java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

        internal fun nettoyerNomFeuille(nom: String): String {
            val n = nom.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().ifEmpty { "Feuille" }
            return n.take(31)
        }

        /** Échappement XML + suppression des caractères interdits en XML 1.0. */
        fun esc(s: String): String {
            val sb = StringBuilder(s.length + 16)
            for (ch in s) {
                when {
                    ch == '&' -> sb.append("&amp;")
                    ch == '<' -> sb.append("&lt;")
                    ch == '>' -> sb.append("&gt;")
                    ch == '"' -> sb.append("&quot;")
                    ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                    ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private const val RELS =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>""" +
                """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>""" +
                "</Relationships>"

        private const val APP_PROPS =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">""" +
                "<Application>Remorque Pro</Application></Properties>"

        // Polices : 0 normal, 1 gras, 2 titre, 3 note italique grise, 4 gras blanc (en-tête), 5 italique gris 10
        // Remplissages : 0 aucun, 1 gray125 (obligatoire), 2 bleu foncé (en-tête), 3 gris clair (totaux)
        // Bordures : 0 aucune, 1 fine
        // Formats : 3 = #,##0 ; 4 = #,##0.00 ; 164 = jj/mm/aaaa
        private const val STYLES =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
                """<numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/></numFmts>""" +
                """<fonts count="6">""" +
                """<font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""" +
                """<font><b/><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>""" +
                """<font><b/><sz val="15"/><color rgb="FF1F3864"/><name val="Calibri"/><family val="2"/></font>""" +
                """<font><i/><sz val="9"/><color rgb="FF595959"/><name val="Calibri"/><family val="2"/></font>""" +
                """<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>""" +
                """<font><sz val="10"/><color rgb="FF404040"/><name val="Calibri"/><family val="2"/></font>""" +
                "</fonts>" +
                """<fills count="4">""" +
                """<fill><patternFill patternType="none"/></fill>""" +
                """<fill><patternFill patternType="gray125"/></fill>""" +
                """<fill><patternFill patternType="solid"><fgColor rgb="FF1F3864"/><bgColor indexed="64"/></patternFill></fill>""" +
                """<fill><patternFill patternType="solid"><fgColor rgb="FFE7ECF5"/><bgColor indexed="64"/></patternFill></fill>""" +
                "</fills>" +
                """<borders count="2">""" +
                "<border><left/><right/><top/><bottom/><diagonal/></border>" +
                """<border><left style="thin"><color rgb="FFA6A6A6"/></left><right style="thin"><color rgb="FFA6A6A6"/></right>""" +
                """<top style="thin"><color rgb="FFA6A6A6"/></top><bottom style="thin"><color rgb="FFA6A6A6"/></bottom><diagonal/></border>""" +
                "</borders>" +
                """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
                """<cellXfs count="13">""" +
                """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
                """<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
                """<xf numFmtId="0" fontId="5" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
                """<xf numFmtId="0" fontId="4" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>""" +
                """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
                """<xf numFmtId="3" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>""" +
                """<xf numFmtId="4" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>""" +
                """<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf>""" +
                """<xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>""" +
                """<xf numFmtId="3" fontId="1" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>""" +
                """<xf numFmtId="4" fontId="1" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>""" +
                """<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
                """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
                "</cellXfs>" +
                """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
                "</styleSheet>"
    }
}
