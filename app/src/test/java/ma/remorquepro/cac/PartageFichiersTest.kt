package ma.remorquepro.cac

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ma.remorquepro.cac.util.Fichiers
import ma.remorquepro.core.domain.Periode
import ma.remorquepro.core.export.DonneesRapport
import ma.remorquepro.core.export.ExportPdfCac
import ma.remorquepro.core.export.ExportXlsxCac
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Génération des exports sur Android et mise à disposition via FileProvider. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PartageFichiersTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `exports xlsx et pdf partageables`() {
        val missions = (1..45).map { Donnees.mission("CAC-%04d".format(it), LocalDate.of(2026, 10, 1 + it % 28), it) }
        val d = DonneesRapport(Donnees.parametres, Periode.mois(YearMonth.of(2026, 10)), missions, LocalDate.of(2026, 10, 31))
        val xlsx = ExportXlsxCac.generer(d)
        val pdf = ExportPdfCac.generer(d)

        for ((nom, octets) in listOf(d.nomFichier("xlsx") to xlsx, d.nomFichier("pdf") to pdf)) {
            val uri = Fichiers.preparerPartage(ctx, nom, octets)
            assertEquals("content", uri.scheme)
            assertEquals(ctx.packageName + ".fichiers", uri.authority)
            val relu = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(octets, relu)
        }
        assertTrue(String(pdf, Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
        assertEquals('P'.code.toByte(), xlsx[0])
        assertEquals('K'.code.toByte(), xlsx[1])

        // Exemples conservés comme artefacts de build
        System.getProperty("exports.dir")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "android_" + d.nomFichier("xlsx")).writeBytes(xlsx)
            File(dir, "android_" + d.nomFichier("pdf")).writeBytes(pdf)
        }
    }
}
