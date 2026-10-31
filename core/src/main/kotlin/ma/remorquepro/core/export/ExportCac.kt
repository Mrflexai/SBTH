package ma.remorquepro.core.export

import ma.remorquepro.core.domain.Facturation
import ma.remorquepro.core.domain.Formats
import ma.remorquepro.core.domain.Periode
import ma.remorquepro.core.domain.Statistiques
import ma.remorquepro.core.domain.Synthese
import ma.remorquepro.core.model.Mission
import ma.remorquepro.core.model.Parametres
import java.time.LocalDate
import ma.remorquepro.core.export.XlsxClasseur.Cellule as C
import ma.remorquepro.core.export.XlsxClasseur.Style as S

/** Mention portée sur tous les documents générés. */
const val MENTION_NON_OFFICIEL =
    "Document établi par le prestataire avec Remorque Pro. Modèle non officiel : à valider par CAC avant tout usage contractuel."

/** Données d'un rapport : missions de la période (le filtrage par période est refait ici par sécurité). */
class DonneesRapport(
    val parametres: Parametres,
    val periode: Periode,
    toutesMissions: List<Mission>,
    val dateEdition: LocalDate = LocalDate.now(),
) {
    val missions: List<Mission> = Statistiques.dansPeriode(toutesMissions, periode)
    val synthese: Synthese = Statistiques.synthese(missions)
    val parMois = Statistiques.syntheseMensuelle(missions, periode)

    val prestataire: String
        get() = parametres.nomEntreprise.ifBlank { parametres.nomChauffeur }.ifBlank { "Prestataire de remorquage" }

    fun nomFichier(extension: String): String = "CAC_missions_remorquage_${periode.suffixeFichier}.$extension"
}

private fun vehicule(m: Mission) = m.vehiculeMarqueModele

private fun depart(m: Mission) = m.villeDepart.ifBlank { m.lieuPriseEnCharge }

private fun destination(m: Mission) = m.villeDestination.ifBlank { m.lieuDestination }

/** Export Excel pour CAC : onglet « Missions » + onglet « Synthèse mensuelle ». */
object ExportXlsxCac {

    val ENTETES = listOf(
        "N° mission", "Date", "Véhicule", "Immatriculation", "Départ", "Destination", "KM", "Montant DH", "Statut",
        "KM parcourus", "Réglé DH", "Reste DH", "Statut paiement",
    )

    fun generer(d: DonneesRapport): ByteArray {
        val classeur = XlsxClasseur("Missions de remorquage CAC — ${d.periode.libelle}", d.prestataire)
        feuilleMissions(classeur, d)
        feuilleSynthese(classeur, d)
        return classeur.enOctets()
    }

    private fun feuilleMissions(classeur: XlsxClasseur, d: DonneesRapport) {
        val f = classeur.feuille("Missions")
        val derniereCol = XlsxClasseur.colonne(ENTETES.size)
        f.ligne(C.Texte("Missions de remorquage réalisées pour CAC", S.TITRE))
        f.fusionner("A1:${derniereCol}1")
        f.ligne(C.Texte(infos(d), S.SOUS_TITRE))
        f.fusionner("A2:${derniereCol}2")
        f.ligne(C.Texte("KM = kilomètres facturables à CAC ; les kilomètres réellement parcourus figurent dans la colonne « KM parcourus ». $MENTION_NON_OFFICIEL", S.NOTE))
        f.fusionner("A3:${derniereCol}3")
        f.ligneVide()
        val ligneEntete = f.ligne(*ENTETES.map { C.Texte(it, S.ENTETE) }.toTypedArray())
        f.figerLignes(ligneEntete)

        d.missions.forEach { m ->
            f.ligne(
                C.Texte(m.numeroCac),
                C.Date(m.dateReception),
                C.Texte(vehicule(m)),
                C.Texte(m.vehiculeImmatriculation),
                C.Texte(depart(m)),
                C.Texte(destination(m)),
                C.Nombre(m.kmFacturables.toDouble(), S.ENTIER),
                C.Nombre(Facturation.total(m)),
                C.Texte(m.statut.libelle),
                C.Nombre(m.distanceKm.toDouble(), S.ENTIER),
                C.Nombre(m.montantRegle),
                C.Nombre(Facturation.reste(m)),
                C.Texte(Facturation.statutPaiement(m).libelle),
            )
        }
        val premiere = ligneEntete + 1
        val derniere = ligneEntete + d.missions.size
        val s = d.synthese
        if (d.missions.isNotEmpty()) {
            fun somme(col: String, valeur: Double, style: S) = C.Formule("SUM($col$premiere:$col$derniere)", valeur, style)
            f.ligne(
                C.Texte("TOTAL", S.TOTAL_LIBELLE),
                C.Texte("${d.missions.size} ligne(s)", S.TOTAL_LIBELLE),
                C.Vide(S.TOTAL_LIBELLE), C.Vide(S.TOTAL_LIBELLE), C.Vide(S.TOTAL_LIBELLE), C.Vide(S.TOTAL_LIBELLE),
                somme("G", s.kmFacturables.toDouble(), S.TOTAL_ENTIER),
                somme("H", s.totalFacture, S.TOTAL_MONTANT),
                C.Vide(S.TOTAL_LIBELLE),
                somme("J", s.kmParcourus.toDouble(), S.TOTAL_ENTIER),
                somme("K", s.totalEncaisse, S.TOTAL_MONTANT),
                somme("L", s.resteAEncaisser, S.TOTAL_MONTANT),
                C.Vide(S.TOTAL_LIBELLE),
            )
            f.filtreAuto("A$ligneEntete:$derniereCol$derniere")
        } else {
            f.ligne(C.Texte("Aucune mission sur la période.", S.NOTE))
        }
        listOf(16.0, 12.0, 22.0, 16.0, 18.0, 18.0, 9.0, 14.0, 18.0, 12.0, 14.0, 14.0, 18.0)
            .forEachIndexed { i, l -> f.largeur(i + 1, l) }
    }

    private fun feuilleSynthese(classeur: XlsxClasseur, d: DonneesRapport) {
        val f = classeur.feuille("Synthèse mensuelle")
        val entetes = listOf(
            "Mois", "Nombre de missions", "Missions annulées", "Km parcourus", "Km facturables",
            "Montant facturé DH", "Montant encaissé DH", "Reste à encaisser DH",
        )
        val derniereCol = XlsxClasseur.colonne(entetes.size)
        f.ligne(C.Texte("Synthèse mensuelle des missions CAC", S.TITRE))
        f.fusionner("A1:${derniereCol}1")
        f.ligne(C.Texte(infos(d), S.SOUS_TITRE))
        f.fusionner("A2:${derniereCol}2")
        f.ligneVide()
        val ligneEntete = f.ligne(*entetes.map { C.Texte(it, S.ENTETE) }.toTypedArray())
        f.figerLignes(ligneEntete)
        d.parMois.forEach { (mois, s) ->
            f.ligne(
                C.Texte(Formats.mois(mois)),
                C.Nombre(s.nombreMissions.toDouble(), S.ENTIER),
                C.Nombre(s.missionsAnnulees.toDouble(), S.ENTIER),
                C.Nombre(s.kmParcourus.toDouble(), S.ENTIER),
                C.Nombre(s.kmFacturables.toDouble(), S.ENTIER),
                C.Nombre(s.totalFacture),
                C.Nombre(s.totalEncaisse),
                C.Nombre(s.resteAEncaisser),
            )
        }
        val s = d.synthese
        if (d.parMois.isNotEmpty()) {
            val p = ligneEntete + 1
            val q = ligneEntete + d.parMois.size
            fun somme(col: String, v: Double, st: S) = C.Formule("SUM($col$p:$col$q)", v, st)
            f.ligne(
                C.Texte("TOTAL PÉRIODE", S.TOTAL_LIBELLE),
                somme("B", s.nombreMissions.toDouble(), S.TOTAL_ENTIER),
                somme("C", s.missionsAnnulees.toDouble(), S.TOTAL_ENTIER),
                somme("D", s.kmParcourus.toDouble(), S.TOTAL_ENTIER),
                somme("E", s.kmFacturables.toDouble(), S.TOTAL_ENTIER),
                somme("F", s.totalFacture, S.TOTAL_MONTANT),
                somme("G", s.totalEncaisse, S.TOTAL_MONTANT),
                somme("H", s.resteAEncaisser, S.TOTAL_MONTANT),
            )
        }
        f.ligneVide()
        f.ligne(C.Texte("Récapitulatif de la période", S.LIBELLE))
        f.ligne(C.Texte("Nombre de missions"), C.Nombre(s.nombreMissions.toDouble(), S.ENTIER))
        f.ligne(C.Texte("Kilomètres parcourus"), C.Nombre(s.kmParcourus.toDouble(), S.ENTIER))
        f.ligne(C.Texte("Kilomètres facturables"), C.Nombre(s.kmFacturables.toDouble(), S.ENTIER))
        f.ligne(C.Texte("Montant total facturé (DH)"), C.Nombre(s.totalFacture))
        f.ligne(C.Texte("Montant encaissé (DH)"), C.Nombre(s.totalEncaisse))
        f.ligne(C.Texte("Montant restant à encaisser (DH)", S.TOTAL_LIBELLE), C.Nombre(s.resteAEncaisser, S.TOTAL_MONTANT))
        f.ligneVide()
        f.ligne(C.Texte(MENTION_NON_OFFICIEL, S.NOTE))
        listOf(32.0, 14.0, 13.0, 14.0, 14.0, 18.0, 18.0, 20.0).forEachIndexed { i, l -> f.largeur(i + 1, l) }
    }

    private fun infos(d: DonneesRapport): String = buildList {
        add("Prestataire : ${d.prestataire}")
        if (d.parametres.nomChauffeur.isNotBlank()) add("Chauffeur : ${d.parametres.nomChauffeur}")
        if (d.parametres.immatDepanneuse.isNotBlank()) add("Dépanneuse : ${d.parametres.immatDepanneuse}")
        add("Période : ${d.periode.libelle}")
        add("Édité le ${Formats.date(d.dateEdition)}")
    }.joinToString("  •  ")
}

/** État PDF A4 (paysage) des missions de remorquage réalisées pour CAC. */
object ExportPdfCac {
    private val BLEU = Couleur.hex(0x1F3864)
    private val BLEU_CLAIR = Couleur.hex(0xE7ECF5)
    private val GRIS_LIGNE = Couleur.hex(0xF4F6FA)
    private val GRIS_TRAIT = Couleur.hex(0xB0B7C3)
    private val GRIS_TEXTE = Couleur.hex(0x55595F)

    private const val MARGE = 36.0
    private const val H_LIGNE = 17.0
    private const val H_ENTETE_TABLE = 22.0
    private const val BAS_UTILE = 48.0 // espace réservé au pied de page

    private data class Colonne(val titre: String, val largeur: Double, val align: Alignement)

    // Largeur utile en paysage : 841.89 - 2 × 36 ≈ 769.9 pt
    private val COLONNES = listOf(
        Colonne("N° mission", 78.0, Alignement.GAUCHE),
        Colonne("Date", 54.0, Alignement.CENTRE),
        Colonne("Véhicule", 98.0, Alignement.GAUCHE),
        Colonne("Immatriculation", 76.0, Alignement.GAUCHE),
        Colonne("Départ", 90.0, Alignement.GAUCHE),
        Colonne("Destination", 90.0, Alignement.GAUCHE),
        Colonne("KM parc.", 44.0, Alignement.DROITE),
        Colonne("KM fact.", 44.0, Alignement.DROITE),
        Colonne("Montant DH", 66.0, Alignement.DROITE),
        Colonne("Réglé DH", 66.0, Alignement.DROITE),
        Colonne("Statut", 59.0, Alignement.GAUCHE),
    )

    fun generer(d: DonneesRapport): ByteArray {
        val doc = PdfDocumentSimple(
            largeurPage = PdfDocumentSimple.A4_HAUTEUR,
            hauteurPage = PdfDocumentSimple.A4_LARGEUR,
            titre = "État des missions de remorquage CAC — ${d.periode.libelle}",
            auteur = d.prestataire,
        )
        val largeurUtile = doc.largeurPage - 2 * MARGE
        var page = doc.nouvellePage()
        var y = entetePremierePage(page, d, largeurUtile)

        // --- Tableau détaillé
        page.texte(MARGE, y, "Détail des missions", 11.0, gras = true, couleur = BLEU)
        y += 8
        y = enteteTableau(page, y)
        if (d.missions.isEmpty()) {
            page.texte(MARGE + 4, y + 12, "Aucune mission enregistrée sur la période.", 9.0, couleur = GRIS_TEXTE)
            y += H_LIGNE
        }
        d.missions.forEachIndexed { i, m ->
            if (y + H_LIGNE > doc.hauteurPage - BAS_UTILE) {
                page = doc.nouvellePage()
                y = enteteSuite(page, d)
                y = enteteTableau(page, y)
            }
            if (i % 2 == 1) page.rectangle(MARGE, y, largeurUtile, H_LIGNE, remplissage = GRIS_LIGNE)
            val valeurs = listOf(
                m.numeroCac, Formats.date(m.dateReception), vehicule(m), m.vehiculeImmatriculation,
                depart(m), destination(m), Formats.entier(m.distanceKm), Formats.entier(m.kmFacturables),
                Formats.montant(Facturation.total(m)), Formats.montant(m.montantRegle), m.statut.libelleCourt,
            )
            ligneTableau(page, y, valeurs, gras = false)
            page.trait(MARGE, y + H_LIGNE, MARGE + largeurUtile, y + H_LIGNE, 0.3, GRIS_TRAIT)
            y += H_LIGNE
        }
        // Ligne de total
        if (y + H_LIGNE > doc.hauteurPage - BAS_UTILE) {
            page = doc.nouvellePage()
            y = enteteSuite(page, d)
            y = enteteTableau(page, y)
        }
        val s = d.synthese
        page.rectangle(MARGE, y, largeurUtile, H_LIGNE, remplissage = BLEU_CLAIR)
        ligneTableau(
            page, y,
            listOf(
                "TOTAL", "${s.nombreMissions} mission(s)", "", "", "", "",
                Formats.entier(s.kmParcourus), Formats.entier(s.kmFacturables),
                Formats.montant(s.totalFacture), Formats.montant(s.totalEncaisse), "",
            ),
            gras = true,
        )
        y += H_LIGNE + 18

        // --- Synthèse mensuelle (si la période couvre plusieurs mois)
        if (d.parMois.size > 1) {
            val hauteurBloc = 30 + H_LIGNE * (d.parMois.size + 1)
            if (y + hauteurBloc > doc.hauteurPage - BAS_UTILE) {
                page = doc.nouvellePage()
                y = enteteSuite(page, d)
            }
            y = syntheseMensuelle(page, d, y)
        }

        // --- Récapitulatif et signatures
        val hauteurFin = 150.0
        if (y + hauteurFin > doc.hauteurPage - BAS_UTILE) {
            page = doc.nouvellePage()
            y = enteteSuite(page, d)
        }
        recapitulatif(page, d, y, largeurUtile)

        // --- Pieds de page (numérotation connue seulement à la fin)
        val total = doc.nombrePages
        for (i in 0 until total) piedDePage(doc.page(i), doc, i + 1, total)
        return doc.enOctets()
    }

    private fun entetePremierePage(page: PdfDocumentSimple.Page, d: DonneesRapport, largeurUtile: Double): Double {
        val p = d.parametres
        page.rectangle(0.0, 0.0, PdfDocumentSimple.A4_HAUTEUR, 6.0, remplissage = BLEU)
        page.texte(MARGE, 40.0, "ÉTAT DES MISSIONS DE REMORQUAGE", 17.0, gras = true, couleur = BLEU)
        page.texte(MARGE, 58.0, "Missions réalisées pour le compte de CAC", 11.0, couleur = GRIS_TEXTE)

        // Bloc prestataire (gauche)
        var y = 84.0
        page.texte(MARGE, y, d.prestataire, 11.0, gras = true)
        val lignes = buildList {
            if (p.nomChauffeur.isNotBlank() && p.nomChauffeur != d.prestataire) add("Chauffeur : ${p.nomChauffeur}")
            if (p.immatDepanneuse.isNotBlank()) add("Dépanneuse : ${p.immatDepanneuse}")
            if (p.telephone.isNotBlank()) add("Téléphone : ${p.telephone}")
            if (p.ville.isNotBlank()) add("Ville : ${p.ville}")
            if (p.identifiantsLegaux.isNotBlank()) add(p.identifiantsLegaux)
        }
        lignes.forEach {
            y += 13
            page.texte(MARGE, y, it, 9.0, couleur = GRIS_TEXTE)
        }

        // Bloc période (droite)
        val xd = MARGE + largeurUtile
        page.rectangle(xd - 230, 72.0, 230.0, 52.0, remplissage = BLEU_CLAIR)
        page.texte(xd - 220, 88.0, "Période du rapport", 8.0, couleur = GRIS_TEXTE)
        page.texte(xd - 220, 103.0, d.periode.libelle, 11.0, gras = true, couleur = BLEU)
        page.texte(xd - 220, 117.0, "Édité le ${Formats.date(d.dateEdition)}", 8.0, couleur = GRIS_TEXTE)

        // Indicateurs
        y = maxOf(y, 124.0) + 18
        val s = d.synthese
        val cartes = listOf(
            "Missions" to s.nombreMissions.toString() + if (s.missionsAnnulees > 0) " (+${s.missionsAnnulees} annulée(s))" else "",
            "Km parcourus" to Formats.km(s.kmParcourus),
            "Km facturables" to Formats.km(s.kmFacturables),
            "Total facturé" to Formats.montantDh(s.totalFacture),
            "Total encaissé" to Formats.montantDh(s.totalEncaisse),
            "Solde restant" to Formats.montantDh(s.resteAEncaisser),
        )
        val ecart = 8.0
        val lc = (largeurUtile - ecart * (cartes.size - 1)) / cartes.size
        cartes.forEachIndexed { i, (titre, valeur) ->
            val x = MARGE + i * (lc + ecart)
            page.rectangle(x, y, lc, 44.0, contour = GRIS_TRAIT, epaisseur = 0.6)
            page.rectangle(x, y, 3.0, 44.0, remplissage = BLEU)
            page.texteCellule(x + 4, y + 16, lc - 4, titre, 8.0, couleur = GRIS_TEXTE)
            page.texteCellule(x + 4, y + 33, lc - 4, valeur, 11.0, gras = true)
        }
        return y + 44 + 24
    }

    private fun enteteSuite(page: PdfDocumentSimple.Page, d: DonneesRapport): Double {
        page.rectangle(0.0, 0.0, PdfDocumentSimple.A4_HAUTEUR, 6.0, remplissage = BLEU)
        page.texte(MARGE, 30.0, "État des missions de remorquage CAC — ${d.prestataire}", 10.0, gras = true, couleur = BLEU)
        page.texte(
            PdfDocumentSimple.A4_HAUTEUR - MARGE, 30.0, d.periode.libelle, 9.0,
            couleur = GRIS_TEXTE, alignement = Alignement.DROITE,
        )
        return 56.0
    }

    private fun enteteTableau(page: PdfDocumentSimple.Page, y: Double): Double {
        val largeur = COLONNES.sumOf { it.largeur }
        page.rectangle(MARGE, y, largeur, H_ENTETE_TABLE, remplissage = BLEU)
        var x = MARGE
        COLONNES.forEach { c ->
            page.texteCellule(x, y + 14.5, c.largeur, c.titre, 8.0, gras = true, couleur = Couleur.BLANC, alignement = c.align)
            x += c.largeur
        }
        return y + H_ENTETE_TABLE
    }

    private fun ligneTableau(page: PdfDocumentSimple.Page, y: Double, valeurs: List<String>, gras: Boolean) {
        var x = MARGE
        COLONNES.forEachIndexed { i, c ->
            page.texteCellule(x, y + 12, c.largeur, valeurs[i], 8.0, gras = gras, alignement = c.align)
            x += c.largeur
        }
    }

    private fun syntheseMensuelle(page: PdfDocumentSimple.Page, d: DonneesRapport, y0: Double): Double {
        var y = y0
        page.texte(MARGE, y, "Synthèse mensuelle", 11.0, gras = true, couleur = BLEU)
        y += 8
        val cols = listOf(
            Colonne("Mois", 130.0, Alignement.GAUCHE),
            Colonne("Missions", 70.0, Alignement.DROITE),
            Colonne("Km parcourus", 90.0, Alignement.DROITE),
            Colonne("Km facturables", 90.0, Alignement.DROITE),
            Colonne("Facturé DH", 100.0, Alignement.DROITE),
            Colonne("Encaissé DH", 100.0, Alignement.DROITE),
            Colonne("Reste DH", 100.0, Alignement.DROITE),
        )
        val largeur = cols.sumOf { it.largeur }
        fun ligne(vals: List<String>, gras: Boolean, blanc: Boolean = false) {
            var x = MARGE
            cols.forEachIndexed { i, c ->
                page.texteCellule(
                    x, y + 12, c.largeur, vals[i], 8.0, gras = gras,
                    couleur = if (blanc) Couleur.BLANC else Couleur.NOIR, alignement = c.align,
                )
                x += c.largeur
            }
        }
        page.rectangle(MARGE, y, largeur, H_LIGNE, remplissage = BLEU)
        ligne(cols.map { it.titre }, gras = true, blanc = true)
        y += H_LIGNE
        d.parMois.forEach { (mois, s) ->
            ligne(
                listOf(
                    Formats.mois(mois), s.nombreMissions.toString(), Formats.entier(s.kmParcourus),
                    Formats.entier(s.kmFacturables), Formats.montant(s.totalFacture),
                    Formats.montant(s.totalEncaisse), Formats.montant(s.resteAEncaisser),
                ),
                gras = false,
            )
            page.trait(MARGE, y + H_LIGNE, MARGE + largeur, y + H_LIGNE, 0.3, GRIS_TRAIT)
            y += H_LIGNE
        }
        return y + 18
    }

    private fun recapitulatif(page: PdfDocumentSimple.Page, d: DonneesRapport, y0: Double, largeurUtile: Double) {
        val s = d.synthese
        var y = y0
        page.texte(MARGE, y, "Récapitulatif de la période", 11.0, gras = true, couleur = BLEU)
        y += 6
        val lignes = listOf(
            "Total des missions" to "${s.nombreMissions}" + if (s.missionsAnnulees > 0) "  (dont ${s.missionsAnnulees} annulée(s) non comptée(s))" else "",
            "Total des kilomètres parcourus" to Formats.km(s.kmParcourus),
            "Total des kilomètres facturables" to Formats.km(s.kmFacturables),
            "Total facturé" to Formats.montantDh(s.totalFacture),
            "Total encaissé" to Formats.montantDh(s.totalEncaisse),
            "Solde restant à encaisser" to Formats.montantDh(s.resteAEncaisser),
        )
        val lb = 330.0
        lignes.forEachIndexed { i, (lib, v) ->
            val dernier = i == lignes.lastIndex
            if (dernier) page.rectangle(MARGE, y, lb, 16.0, remplissage = BLEU_CLAIR)
            page.texte(MARGE + 4, y + 11.5, lib, 9.0, gras = dernier)
            page.texte(MARGE + lb - 4, y + 11.5, v, 9.0, gras = true, alignement = Alignement.DROITE)
            page.trait(MARGE, y + 16, MARGE + lb, y + 16, 0.3, GRIS_TRAIT)
            y += 16
        }
        // Signatures
        val xs = MARGE + largeurUtile - 380
        val ys = y0 + 6
        page.rectangle(xs, ys, 180.0, 80.0, contour = GRIS_TRAIT)
        page.texte(xs + 6, ys + 13, "Cachet et signature du prestataire", 8.0, couleur = GRIS_TEXTE)
        page.rectangle(xs + 200, ys, 180.0, 80.0, contour = GRIS_TRAIT)
        page.texte(xs + 206, ys + 13, "Visa CAC", 8.0, couleur = GRIS_TEXTE)
    }

    private fun piedDePage(page: PdfDocumentSimple.Page, doc: PdfDocumentSimple, numero: Int, total: Int) {
        val y = doc.hauteurPage - 26
        page.trait(MARGE, y - 10, doc.largeurPage - MARGE, y - 10, 0.4, GRIS_TRAIT)
        page.texte(MARGE, y, MENTION_NON_OFFICIEL, 7.0, couleur = GRIS_TEXTE)
        page.texte(doc.largeurPage - MARGE, y, "Page $numero / $total", 8.0, couleur = GRIS_TEXTE, alignement = Alignement.DROITE)
    }
}
