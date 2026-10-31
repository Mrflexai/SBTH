package ma.remorquepro.cac.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Fichiers {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_PDF = "application/pdf"
    const val MIME_JSON = "application/json"

    /** Écrit les octets dans un document choisi par l'utilisateur (Storage Access Framework). */
    fun ecrire(context: Context, uri: Uri, octets: ByteArray) {
        val flux = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Impossible d'ouvrir le fichier de destination")
        flux.use { it.write(octets) }
    }

    fun lire(context: Context, uri: Uri): ByteArray {
        val flux = context.contentResolver.openInputStream(uri) ?: error("Impossible d'ouvrir le fichier")
        return flux.use { it.readBytes() }
    }

    /** Copie le fichier dans le cache de l'application (dossier exports/) et renvoie son URI partageable. */
    fun preparerPartage(context: Context, nom: String, octets: ByteArray): Uri {
        val dossier = File(context.cacheDir, "exports").apply { mkdirs() }
        dossier.listFiles()?.forEach { it.delete() }
        val f = File(dossier, nom)
        f.writeBytes(octets)
        return FileProvider.getUriForFile(context, context.packageName + ".fichiers", f)
    }

    /** Ouvre le menu de partage Android (e-mail, WhatsApp, Drive… selon les applications installées). */
    fun partager(context: Context, nom: String, mime: String, octets: ByteArray) {
        val uri = preparerPartage(context, nom, octets)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nom)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager $nom").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
