package ma.remorquepro.cac

import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres
import ma.remorquepro.core.model.StatutMission
import java.time.LocalDate

/** Données d'essai fictives — tarifs d'exemple, NON officiels. */
object Donnees {
    val parametres = Parametres(
        nomChauffeur = "Youssef El Amrani",
        nomEntreprise = "Dépannage El Amrani SARL",
        immatDepanneuse = "45821-A-6",
        tarifBase = 300.0,
        tarifKm = 5.0,
        kmInclus = 10,
        supplementNuit = 100.0,
        supplementWeekend = 50.0,
    )

    fun mission(numero: String, date: LocalDate = LocalDate.of(2026, 10, 15), i: Int = 1) = Mission(
        numeroCac = numero,
        dateReception = date,
        heureAppel = "14:30",
        chauffeur = "Youssef El Amrani",
        immatDepanneuse = "45821-A-6",
        beneficiaireNom = "Client $i",
        beneficiaireTelephone = "0661000000",
        vehiculeMarqueModele = "Dacia Logan",
        vehiculeImmatriculation = "12345-A-$i",
        villeDepart = "Rabat",
        lieuPriseEnCharge = "A1 PK 12",
        villeDestination = "Casablanca",
        lieuDestination = "Garage Atlas",
        motif = "Panne moteur",
        kmDepart = 120_000,
        kmArrivee = 120_095,
        distanceKm = 95,
        kmFacturables = 85,
        statut = StatutMission.TERMINEE,
        tarifBase = 300.0,
        tarifKm = 5.0,
        fraisAutoroute = 23.5,
        montantRegle = 200.0,
        dateReglement = LocalDate.of(2026, 10, 30),
        referenceReglement = "VIR-001",
    )
}
