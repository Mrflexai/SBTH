package ma.remorquepro.core

import ma.remorquepro.core.domain.Periode
import ma.remorquepro.core.export.DonneesRapport
import ma.remorquepro.core.export.ExportPdfCac
import ma.remorquepro.core.export.ExportXlsxCac
import ma.remorquepro.core.export.PdfDocumentSimple
import ma.remorquepro.core.export.Polices
import ma.remorquepro.core.export.XlsxClasseur
import ma.remorquepro.core.model.Parametres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExportsTest {
    private val octobre = Periode.mois(YearMonth.of(2026, 10))
    private val edition = LocalDate.of(2026, 10, 31)

    private fun dezipper(octets: ByteArray): Map<String, String> {
        val res = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(octets)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                res[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return res
    }

    @Test
    fun `xlsx - structure valide, deux onglets, en-tetes CAC et totaux`() {
        val missions = JeuDeDonnees.missions(40) + JeuDeDonnees.mission(200, LocalDate.of(2026, 11, 2))
        val d = DonneesRapport(JeuDeDonnees.parametres, octobre, missions, edition)
        val octets = ExportXlsxCac.generer(d)
        File(JeuDeDonnees.dossierExports(), d.nomFichier("xlsx")).writeBytes(octets)

        val fichiers = dezipper(octets)
        listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
            "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml",
        ).forEach { assertTrue("manque $it", it in fichiers) }

        // Chaque partie XML doit être bien formée
        val fabrique = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        fichiers.forEach { (nom, xml) ->
            fabrique.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                ?: error("XML invalide : $nom")
        }
        val wb = fichiers.getValue("xl/workbook.xml")
        assertTrue(wb.contains("name=\"Missions\""))
        assertTrue(wb.contains("name=\"Synthèse mensuelle\""))

        val f1 = fichiers.getValue("xl/worksheets/sheet1.xml")
        listOf("N° mission", "Date", "Véhicule", "Immatriculation", "Départ", "Destination", "KM", "Montant DH", "Statut")
            .forEach { assertTrue("en-tête $it", f1.contains(">$it<")) }
        // 40 missions d'octobre seulement (celle de novembre exclue)
        assertEquals(40, d.missions.size)
        assertTrue(f1.contains("CAC-2026-00040"))
        assertTrue(!f1.contains("CAC-2026-00201"))
        assertTrue(f1.contains("<f>SUM(H6:H45)</f>"))

        val f2 = fichiers.getValue("xl/worksheets/sheet2.xml")
        listOf("Nombre de missions", "Kilomètres parcourus", "Kilomètres facturables", "Montant total facturé (DH)", "Montant encaissé (DH)", "Montant restant à encaisser (DH)")
            .forEach { assertTrue("synthèse $it", f2.contains(it)) }
        assertTrue(f2.contains("Octobre 2026"))
    }

    @Test
    fun `xlsx - periode vide et caracteres speciaux`() {
        val p = Parametres(nomEntreprise = "A & B <Dépannage> \"Test\"")
        val m = JeuDeDonnees.mission(1).copy(vehiculeMarqueModele = "Citroën C3 <&>\u0001", dateReception = LocalDate.of(2026, 10, 2))
        val octets = ExportXlsxCac.generer(DonneesRapport(p, octobre, listOf(m), edition))
        val fabrique = DocumentBuilderFactory.newInstance()
        dezipper(octets).values.forEach { fabrique.newDocumentBuilder().parse(ByteArrayInputStream(it.toByteArray())) }
        val vide = ExportXlsxCac.generer(DonneesRapport(p, Periode.jour(LocalDate.of(2020, 1, 1)), listOf(m), edition))
        assertTrue(dezipper(vide).getValue("xl/worksheets/sheet1.xml").contains("Aucune mission"))
    }

    @Test
    fun `colonnes et dates excel`() {
        assertEquals("A", XlsxClasseur.colonne(1))
        assertEquals("Z", XlsxClasseur.colonne(26))
        assertEquals("AA", XlsxClasseur.colonne(27))
        assertEquals("AZ", XlsxClasseur.colonne(52))
        assertEquals(46326L, XlsxClasseur.serieExcel(LocalDate.of(2026, 10, 31)))
    }

    private fun verifierPdf(octets: ByteArray): Int {
        val texte = String(octets, Charsets.ISO_8859_1)
        assertTrue(texte.startsWith("%PDF-1.4"))
        assertTrue(texte.trimEnd().endsWith("%%EOF"))
        // Table xref : chaque décalage pointe bien sur « n 0 obj »
        val startxref = Regex("startxref\\n(\\d+)").find(texte)!!.groupValues[1].toInt()
        assertTrue(texte.startsWith("xref", startxref))
        val lignes = texte.substring(startxref).lines()
        val nb = lignes[1].split(" ")[1].toInt()
        for (i in 1 until nb) {
            val off = lignes[2 + i].substring(0, 10).toInt()
            assertTrue("objet $i", texte.startsWith("$i 0 obj", off))
        }
        // Longueur des flux exacte
        Regex("<< /Length (\\d+) >>\\nstream\\n").findAll(texte).forEach { m ->
            val debut = m.range.last + 1
            val len = m.groupValues[1].toInt()
            assertTrue(texte.startsWith("\nendstream", debut + len))
        }
        return Regex("/Type /Page /Parent").findAll(texte).count()
    }

    @Test
    fun `pdf - multipage valide avec totaux`() {
        val missions = JeuDeDonnees.missions(95)
        val d = DonneesRapport(JeuDeDonnees.parametres, octobre, missions, edition)
        val octets = ExportPdfCac.generer(d)
        File(JeuDeDonnees.dossierExports(), d.nomFichier("pdf")).writeBytes(octets)
        val pages = verifierPdf(octets)
        assertTrue("au moins 3 pages : $pages", pages >= 3)
        val texte = String(octets, Charsets.ISO_8859_1)
        assertTrue(texte.contains("Page 1 / $pages"))
        assertTrue(texte.contains("Page $pages / $pages"))
        assertTrue(texte.contains("CAC-2026-00095"))
    }

    @Test
    fun `pdf - periode multi-mois et periode vide`() {
        val missions = JeuDeDonnees.missions(30, LocalDate.of(2026, 8, 20)) + JeuDeDonnees.missions(10, LocalDate.of(2026, 10, 1))
        val p = Periode.personnalisee(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31))
        val d = DonneesRapport(JeuDeDonnees.parametres, p, missions, edition)
        val octets = ExportPdfCac.generer(d)
        File(JeuDeDonnees.dossierExports(), d.nomFichier("pdf")).writeBytes(octets)
        verifierPdf(octets)
        assertTrue(String(octets, Charsets.ISO_8859_1).contains("Synth\\350se mensuelle"))

        val vide = ExportPdfCac.generer(DonneesRapport(Parametres(), Periode.jour(LocalDate.of(2026, 1, 1)), emptyList(), edition))
        assertEquals(1, verifierPdf(vide))
    }

    @Test
    fun `pdf - encodage WinAnsi et metriques`() {
        assertEquals("(R\\351gl\\351 \\(DH\\) 50\\260)", PdfDocumentSimple.chainePdf("Réglé (DH) 50°"))
        assertEquals("(\\200 \\234)", PdfDocumentSimple.chainePdf("€ œ"))
        assertEquals("(?)", PdfDocumentSimple.chainePdf("م"))
        assertEquals(Polices.largeur("e", 10.0, false), Polices.largeur("é", 10.0, false), 0.0)
        assertEquals(5.56, Polices.largeur("0", 10.0, false), 1e-9)
        val t = Polices.tronquer("Volkswagen Golf 7 GTI Performance", 8.0, false, 60.0)
        assertTrue(t.endsWith("…"))
        assertTrue(Polices.largeur(t, 8.0, false) <= 60.0)
    }
}
