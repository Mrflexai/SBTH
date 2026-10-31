package ma.remorquepro.core

import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres
import ma.remorquepro.core.model.StatutMission
import java.io.File
import java.time.LocalDate

/** Données d'essai fictives (tarifs d'exemple, NON officiels). */
object JeuDeDonnees {
    val parametres = Parametres(
        nomChauffeur = "Youssef El Amrani",
        nomEntreprise = "Dépannage El Amrani SARL",
        immatDepanneuse = "45821-A-6",
        telephone = "06 61 23 45 67",
        ville = "Casablanca",
        identifiantsLegaux = "ICE 000000000000000 (exemple)",
        tarifBase = 300.0,
        tarifKm = 5.0,
        kmInclus = 10,
        supplementNuit = 100.0,
        supplementWeekend = 50.0,
    )

    private val villes = listOf("Casablanca", "Rabat", "Mohammédia", "El Jadida", "Settat", "Berrechid", "Kénitra", "Marrakech", "Fès", "Tanger")
    private val vehicules = listOf("Dacia Logan", "Renault Clio 4", "Peugeot 208", "Hyundai Accent", "Toyota Corolla", "Volkswagen Golf 7", "Kia Picanto", "Fiat Tipo")
    private val motifs = listOf("Panne moteur", "Accident", "Crevaison", "Batterie", "Embrayage", "Surchauffe")

    fun mission(i: Int, date: LocalDate = LocalDate.of(2026, 10, 1).plusDays((i % 31).toLong())): Mission {
        val distance = 15 + (i * 37) % 180
        val kmDep = 120_000 + i * 250
        val statut = StatutMission.entries[i % StatutMission.entries.size]
        val total = 300.0 + (distance - 10).coerceAtLeast(0) * 5.0
        return Mission(
            id = (i + 1).toLong(),
            numeroCac = "CAC-2026-%05d".format(i + 1),
            dateReception = date,
            heureAppel = "%02d:%02d".format((i * 5) % 24, (i * 7) % 60),
            chauffeur = parametres.nomChauffeur,
            immatDepanneuse = parametres.immatDepanneuse,
            beneficiaireNom = "Bénéficiaire $i",
            beneficiaireTelephone = "06000000%02d".format(i % 100),
            vehiculeMarqueModele = vehicules[i % vehicules.size],
            vehiculeImmatriculation = "${10000 + i * 13}-${"ABDHW"[i % 5]}-${1 + i % 80}",
            villeDepart = villes[i % villes.size],
            lieuPriseEnCharge = "Autoroute A${1 + i % 7}, PK ${10 + i}",
            villeDestination = villes[(i + 3) % villes.size],
            lieuDestination = "Garage agréé n°${i % 20}",
            motif = motifs[i % motifs.size],
            kmDepart = kmDep,
            kmArrivee = kmDep + distance,
            distanceKm = distance,
            kmFacturables = (distance - 10).coerceAtLeast(0),
            statut = statut,
            tarifBase = 300.0,
            tarifKm = 5.0,
            fraisAutoroute = if (i % 3 == 0) 23.5 else 0.0,
            montantRegle = when (i % 4) { 0 -> total; 1 -> total / 2; else -> 0.0 },
        )
    }

    fun missions(n: Int, debut: LocalDate = LocalDate.of(2026, 10, 1)): List<Mission> =
        (0 until n).map { mission(it, debut.plusDays((it % 31).toLong())) }

    fun dossierExports(): File {
        val d = File(System.getProperty("exports.dir") ?: "build/exemples-exports")
        d.mkdirs()
        return d
    }
}
