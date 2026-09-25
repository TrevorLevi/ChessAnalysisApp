# ChessForge

Application Android **locale** qui telecharge vos parties chess.com, les analyse sur le
telephone, et transforme vos erreurs en puzzles personnels avec revision espacee.

Aucun compte a creer, aucun serveur : la seule connexion sortante est l'API publique de
chess.com, en lecture seule, pour recuperer vos parties.

---

## Ce que fait l'application

| Ecran | Contenu |
|---|---|
| **Tableau de bord** | Precision moyenne, perte moyenne par coup, taux de gaffes, serie de jours, seance du jour, faiblesses classees par cout reel, courbes de precision et de classement, repartition des coups, pertes par phase, motifs d'erreur, erreurs selon le temps de reflexion, carte de chaleur des gaffes, score par cadence. |
| **Parties** | Liste filtrable (cadence, couleur, resultat, analysees) avec precision et nombre de gaffes. |
| **Revue de partie** | Plateau + barre d'evaluation, courbe d'evaluation cliquable, liste des coups annotee, coup joue contre coup du moteur, motif tactique et conseil, temps passe sur le coup, **mode « rejouer la position contre le moteur »**, bouton « en faire un puzzle ». |
| **Puzzles** | File de revision (repetition espacee), series par motif, entraineur avec indices, validation des solutions alternatives par le moteur, renvoi vers la partie d'origine. |
| **Ouvertures** | Score par couleur, score par famille d'ouverture, reperage des fuites. |
| **Reglages** | Pseudo, historique a importer, profondeur d'analyse, choix du moteur, theme du plateau, objectif quotidien, effacement des donnees. |

### D'ou viennent les puzzles

Un puzzle n'est cree que si les trois conditions sont reunies :

1. la position vient d'une de vos parties, a un moment ou vous avez devie ;
2. il existe **un** coup nettement meilleur que tous les autres (verifie en demandant au
   moteur ses deux meilleures variantes) ;
3. l'ecart d'evaluation est assez grand pour que la lecon soit reelle.

Quatre origines : gaffe a corriger, gain manque, erreur adverse non punie, exercice de
finale. La revision suit un algorithme type SM-2 : un puzzle rate revient en 10 minutes,
un puzzle trouve du premier coup s'espace de jour en jour.

---

## Compiler et installer

Prerequis : **JDK 17**, **SDK Android** (plateforme 36, build-tools 36), un cable USB.

```powershell
# 1. Indiquer le SDK si besoin (local.properties est deja pre-rempli)
#    sdk.dir=C\:\\Android\\Sdk

# 2. Telephone : Options developpeur > Debogage USB active, puis branche en USB
.\gradlew.bat :app:installDebug

# ou produire un APK a copier sur le telephone
.\gradlew.bat :app:assembleRelease
# -> app\build\outputs\apk\release\app-release.apk
```

L'APK *release* est signe avec la cle de debug Android : suffisant pour une installation
personnelle par sideload, a ne jamais publier tel quel.

### Premier lancement

1. **Reglages** → saisir votre pseudo chess.com → *Verifier et enregistrer*.
2. Choisir le nombre de mois d'historique (3 par defaut) et la profondeur d'analyse.
3. **Tableau de bord** → *Importer*. L'import est rapide, l'analyse dure quelques
   dizaines de secondes par partie et tourne en tache de fond.

Comptez une dizaine de parties analysees avant que les faiblesses et la seance du jour
deviennent pertinentes.

---

## Le moteur

Deux moteurs, selectionnables dans les reglages :

- **ForgeEngine** (par defaut, integre) : moteur Kotlin ecrit pour ce projet —
  alpha-beta avec approfondissement iteratif, table de transposition, quiescence,
  elagage du coup nul, reductions de coups tardifs. Environ 2000 Elo, aucune
  dependance, fonctionne hors ligne immediatement.
- **Stockfish natif** (optionnel) : nettement plus fort et plus rapide.

```powershell
.\tools\fetch_stockfish.ps1      # clone les sources + telecharge le reseau NNUE
.\gradlew.bat :app:assembleRelease # compile la bibliotheque native au passage
```

Le script installe au besoin CMake du SDK, clone Stockfish, lit le nom du reseau NNUE
attendu par cette version et le place dans les assets. Stockfish tourne *dans* le
processus de l'application (Android interdit d'executer un binaire depuis le repertoire
de donnees) : sa boucle UCI lit et ecrit sur deux tubes rediriges vers stdin/stdout.

Sans cette etape, tout fonctionne : l'application detecte l'absence de la bibliotheque
et utilise son moteur integre.

**Ce que cela coute.** Stockfish 19 n'utilise plus qu'un seul reseau NNUE, mais il pese
**94 Mo** : l'APK passe d'environ 1,5 Mo a une centaine de Mo, et le reseau est recopie
une fois dans le stockage prive de l'appli au premier demarrage (comptez ~190 Mo
installes). C'est le meme ordre de grandeur que l'application officielle chess.com.

**Details de compilation** (deja configures, pour memoire) : seule l'architecture
`arm64-v8a` est produite ; les drapeaux reproduisent `make ARCH=armv8-dotprod COMP=ndk`
du Makefile officiel (`-O3 -fno-exceptions -march=armv8.2-a+dotprod`, `USE_NEON=8`,
`USE_NEON_DOTPROD`, `IS_64BIT`, `USE_POPCNT`) ; la bibliotheque cible `android-29`,
plateforme minimale exigee par Stockfish, tandis que l'application reste installable
depuis l'API 26 — sur un appareil trop ancien elle se rabat simplement sur le moteur
integre. Stockfish recommande le **NDK r27c ou plus recent**. L'edition de liens force un
alignement sur des **pages de 16 Ko** (`-Wl,-z,max-page-size=16384`) : c'est exige par
Google Play pour tout code natif, et necessaire sur les appareils 64 bits recents qui
n'utilisent plus des pages de 4 Ko. Le NDK r27 ne l'applique pas par defaut.

> **Licence** : Stockfish est sous GPL v3. Un APK qui l'embarque doit respecter cette
> licence. Pour un usage personnel sur votre telephone, aucun probleme ; pour une
> redistribution, il faut publier les sources.

---

## Ecran deplie (Galaxy Z Fold)

Au-dela de 720 dp de large, l'interface passe d'elle-meme en deux colonnes : barre de
navigation laterale, tableau de bord sur deux colonnes, plateau a gauche et analyse a
droite en revue de partie comme dans l'entraineur. Plier ou deplier ne perd pas l'etat
en cours.

---

## Organisation du code

```
app/src/main/java/com/chessforge/
  chess/          coeur echiquéen : plateau, generation de coups, FEN, SAN, PGN
  engine/         interface moteur, ForgeEngine (Kotlin), pont JNI Stockfish
  analysis/       classification des coups, motifs tactiques, generation de puzzles,
                  moteur de faiblesses, plan d'entrainement
  data/           base SQLite, DAO, client chess.com, preferences, depot
  srs/            repetition espacee
  ui/             theme, graphiques, plateau, ecrans
app/src/main/cpp/ pont JNI + CMake pour Stockfish (optionnel)
tools/            script de recuperation de Stockfish
```

Choix assumes : pas de Room (SQLite direct, migrations explicites, compilation plus
rapide), pas d'injection de dependances (un conteneur manuel suffit a cette taille),
pas de bibliotheque de graphiques (tout est dessine sur Canvas), pieces d'echecs
dessinees geometriquement (aucune police ni image, rendu identique partout).

### Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Le coeur echiquéen est verifie par **perft** sur les cinq positions de reference du
Chess Programming Wiki — le seul test qui prouve qu'un generateur de coups est juste —
plus des tests d'aller-retour FEN/SAN, de parsing PGN chess.com, et des tests tactiques
sur le moteur (mat en un, mat en deux, gain de materiel, piece en prise).

---

## Limites connues

- La detection des motifs tactiques est heuristique : elle rate des motifs plutot que
  d'en inventer, mais elle n'est pas exhaustive.
- Les parties autres que les echecs classiques (960, bughouse) sont ignorees a l'import.
- Le moteur integre suffit largement a reperer les gaffes ; sur les positions calmes et
  les finales precises, Stockfish est sensiblement meilleur.
- L'analyse consomme de la batterie : elle est concue pour tourner branche, ecran allume.
