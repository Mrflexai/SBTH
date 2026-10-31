package ma.remorquepro.core.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres

/** Contenu complet d'une sauvegarde locale (fichier JSON lisible). */
@Serializable
data class Sauvegarde(
    @SerialName("format") val format: String = FORMAT,
    @SerialName("version") val version: Int = VERSION,
    @SerialName("creeLe") val creeLe: String = "",
    @SerialName("parametres") val parametres: Parametres = Parametres(),
    @SerialName("missions") val missions: List<Mission> = emptyList(),
) {
    companion object {
        const val FORMAT = "remorquepro-cac-sauvegarde"
        const val VERSION = 1
    }
}

class SauvegardeInvalideException(message: String, cause: Throwable? = null) : Exception(message, cause)

object CodecSauvegarde {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun encoder(s: Sauvegarde): String = json.encodeToString(Sauvegarde.serializer(), s)

    /** Lit et vérifie une sauvegarde. Lève [SauvegardeInvalideException] si le fichier n'est pas reconnu. */
    fun decoder(texte: String): Sauvegarde {
        val s = try {
            json.decodeFromString(Sauvegarde.serializer(), texte.removePrefix("﻿"))
        } catch (e: Exception) {
            throw SauvegardeInvalideException("Fichier de sauvegarde illisible ou corrompu.", e)
        }
        if (s.format != Sauvegarde.FORMAT) {
            throw SauvegardeInvalideException("Ce fichier n'est pas une sauvegarde Remorque Pro.")
        }
        if (s.version > Sauvegarde.VERSION) {
            throw SauvegardeInvalideException("Sauvegarde créée par une version plus récente de l'application (v${s.version}).")
        }
        val ids = s.missions.map { it.id }.filter { it != 0L }
        if (ids.size != ids.toSet().size) {
            throw SauvegardeInvalideException("Sauvegarde incohérente : identifiants de mission en double.")
        }
        return s
    }
}
