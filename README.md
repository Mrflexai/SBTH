# Remorque Pro — missions de remorquage CAC

Application Android **100 % locale et hors ligne**, en français, destinée à un chauffeur de
dépanneuse travaillant avec la maison CAC au Maroc. Aucun compte, aucun serveur, aucune
connexion Internet : l'APK ne demande **pas** la permission `INTERNET` (vérifié
automatiquement par la CI) et la sauvegarde cloud Android est désactivée.

> ⚠️ **Avertissement.** L'application ne contient **aucun tarif officiel CAC**. Tous les tarifs
> valent 0 par défaut et doivent être saisis par le chauffeur selon l'accord réellement conclu
> avec CAC. Les états PDF et fichiers Excel générés sont des **modèles proposés par
> l'application** : ils ne constituent pas des documents officiels CAC sans validation de leur part
> (une mention l'indique sur chaque document).

## Fonctionnalités

| Écran | Contenu |
|---|---|
| **Accueil** (tableau de bord) | Missions du jour / du mois, terminées, en attente, km parcourus et facturables du mois, facturé à CAC, réglé, reste à encaisser (mois et total), graphiques mensuels (missions et chiffre d'affaires, 6 derniers mois), missions en cours |
| **Missions** (historique) | Recherche libre (sans accents ni casse) + filtres : n° CAC, période (aujourd'hui, semaine, mois, mois précédent, personnalisée), immatriculation (véhicule ou dépanneuse, tirets/espaces ignorés), ville de départ, ville de destination, chauffeur, statut de mission, statut de paiement. Actions : consulter, modifier, dupliquer, supprimer |
| **Fiche mission** | Toutes les informations, progression du statut en un geste (l'heure de l'étape est horodatée automatiquement), annulation, détail du calcul de facturation |
| **Formulaire** | Tous les champs CAC (voir ci-dessous), calcul automatique distance / km facturables, suppléments nuit et week-end proposés selon l'heure d'appel et la date, aperçu du total et du reste à encaisser, contrôles de saisie (n° obligatoire, heures, compteur, doublon de n°) |
| **Exports** | Période jour / semaine / mois / personnalisée, aperçu des totaux, fichier **Excel XLSX** et **état PDF A4**, à enregistrer (sélecteur de fichiers Android) ou à partager |
| **Paramètres** | Chauffeur, entreprise, dépanneuse, téléphone, identifiants légaux ; tarifs convenus avec CAC ; plage horaire de nuit ; samedi = week-end ; **sauvegarde et restauration complètes** (fichier JSON) |

### Données d'une mission

N° de mission CAC, date de réception, heure d'appel, chauffeur, immatriculation de la
dépanneuse, bénéficiaire (nom, téléphone), marque et modèle, immatriculation du véhicule, ville
et lieu de prise en charge, ville et lieu de destination, motif, heures de départ / d'arrivée sur
place / de chargement / de livraison, compteur départ et arrivée, distance parcourue, km
facturables, observations, statut, et volet financier : tarif de base, tarif kilométrique,
supplément de nuit, supplément de week-end, frais d'autoroute, frais supplémentaires (et leur
nature), montant réglé par CAC, date et référence du règlement.

Statuts : Mission reçue → En route → Sur place → Véhicule chargé → Véhicule livré → Mission
terminée, ou Mission annulée.

### Règles de calcul

* **Total facturé** = tarif de base + km facturables × tarif kilométrique + supplément de nuit
  + supplément de week-end + frais d'autoroute + frais supplémentaires (arrondi au centime).
* **Reste à encaisser** = total facturé − montant réglé par CAC (négatif = trop-perçu).
* **Statut de paiement** (déduit) : Sans montant, Non réglé, Partiellement réglé, Réglé.
* **Km réellement parcourus ≠ km facturables** : la distance est calculée depuis le compteur
  (modifiable) ; les km facturables proposés valent « distance − km inclus dans le forfait »
  (paramètre, 0 par défaut) et restent modifiables mission par mission.
* Les tarifs sont **copiés dans la mission** à sa création : modifier les paramètres plus tard ne
  change pas les missions déjà saisies.
* Synthèses : les missions annulées ne sont pas comptées dans le nombre de missions (affichées à
  part) mais leurs km et montants éventuels (déplacement facturé) sont inclus dans les totaux.

### Exports

* **Excel** — onglet « Missions » : `N° mission | Date | Véhicule | Immatriculation | Départ |
  Destination | KM | Montant DH | Statut` (KM = km facturables), suivis de colonnes utiles au
  suivi (KM parcourus, Réglé, Reste, Statut de paiement), ligne de total avec formules `SUM`,
  filtre automatique, volets figés, mise en page A4 paysage. Onglet « Synthèse mensuelle » :
  nombre de missions, km parcourus, km facturables, montant facturé, encaissé et restant, par
  mois et pour la période.
* **PDF** — A4 paysage multipage : prestataire, période, indicateurs, liste détaillée (en-tête
  de tableau répété sur chaque page), synthèse par mois si la période en couvre plusieurs,
  récapitulatif (total missions, km, facturé, encaissé, solde), cadres « Cachet et signature du
  prestataire » et « Visa CAC », numérotation « Page x / n ».

Des exemples générés par les tests (données fictives) sont publiés avec l'APK.

## Installation de l'APK

1. Copier `RemorquePro-CAC-v1.0.0.apk` sur le téléphone (Android 8.0 ou plus récent).
2. Ouvrir le fichier et autoriser l'installation depuis cette source si Android le demande.
3. Au premier lancement : **Paramètres** → saisir le profil et les tarifs convenus avec CAC.

Penser à créer régulièrement une **sauvegarde** (Paramètres → Créer une sauvegarde…) et à la
copier hors du téléphone : désinstaller l'application efface ses données.

## Architecture technique

```
core/   Kotlin/JVM pur, sans dépendance Android (testable partout)
  model/    Mission, Parametres, statuts
  domain/   Facturation, Kilometrage, Supplements, Periode, FiltreMissions, Statistiques, Validation
  export/   Générateur XLSX (Office Open XML) et générateur PDF (PDF 1.4) maison, sans bibliothèque
  backup/   Sauvegarde JSON (kotlinx.serialization), contrôle de format et de version
app/    Android : Kotlin, Jetpack Compose (Material 3), Room (SQLite), Navigation Compose
  data/     Entités Room, DAO, base, Depot (accès unique aux données)
  ui/       Écrans : tableau de bord, historique, fiche, formulaire, exports, paramètres
```

* minSdk 26 (Android 8.0), targetSdk / compileSdk 35.
* Pas d'injection de dépendances : un `Depot` unique créé par `RemorqueApp`.
* Partage de fichiers via `FileProvider` (cache interne), enregistrement via le Storage Access
  Framework : aucune permission de stockage requise.

## Compilation

Prérequis : JDK 17+, Android SDK (plateforme 35).

```bash
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:assembleRelease                # APK : app/build/outputs/apk/release/app-release.apk
```

La CI GitHub Actions (`.github/workflows/android.yml`) exécute les tests, compile les APK,
vérifie la signature et l'absence de permission Internet, puis publie l'APK et les exemples
d'exports en artefact. Lancée manuellement avec l'option « publier_apk », elle copie aussi
l'APK dans `livrables/`.

### Signature

Par défaut, les APK sont signés avec la clé de partage du projet
(`keystore/remorquepro-partage.jks`, mot de passe `remorquepro`), afin que chaque nouvelle version
s'installe par-dessus la précédente **sans perte de données**. Cette clé étant versionnée, elle
n'offre pas de protection contre une mise à jour falsifiée : pour un usage durable, créer une clé
privée et un fichier `keystore.properties` (non versionné) à la racine :

```properties
storeFile=/chemin/vers/ma-cle.jks
storePassword=…
keyAlias=…
keyPassword=…
```

(Changer de clé impose une désinstallation : faire une sauvegarde avant, puis restaurer.)

### Tests

* `core` : calculs de facturation et d'arrondi, kilométrage, plages de nuit / week-end,
  périodes, filtres, statistiques, validation, sauvegarde (aller-retour, fichiers invalides),
  XLSX (structure OOXML, XML bien formé, en-têtes, totaux) et PDF (table xref, longueurs de flux,
  multipage, encodage des accents).
* `app` (Robolectric) : persistance Room sur fichier SQLite (fermeture / réouverture),
  création / modification / duplication / suppression, sauvegarde puis restauration complète,
  logique du formulaire, partage des exports via FileProvider, et parcours d'interface Compose
  (création d'une mission depuis le formulaire, progression de statut, n° obligatoire,
  recherche dans l'historique, navigation entre onglets).
