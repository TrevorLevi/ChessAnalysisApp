# Fiche Google Play — ChessForge

Textes prêts à coller dans la Play Console. Les limites de caractères imposées par
Google sont indiquées ; les compteurs sont à jour.

---

## Titre *(30 caractères max)*

```
ChessForge : analyse d'échecs
```

*29 caractères.* Volontairement sans « chess.com » : Google interdit d'employer une
marque tierce dans le titre, et cela suggérerait une affiliation qui n'existe pas.

---

## Description courte *(80 caractères max)*

```
Analysez vos parties en ligne et entraînez-vous sur vos propres erreurs.
```

*72 caractères.*

---

## Description complète *(4000 caractères max)*

```
ChessForge transforme vos parties en programme d'entraînement personnel.

L'application récupère vos parties publiques depuis votre compte de jeu en ligne, les
analyse directement sur votre téléphone, et fabrique des exercices à partir des
positions où vous vous êtes trompé. Pas des puzzles génériques : les vôtres.

CE QUE VOUS OBTENEZ

• Analyse complète de chaque partie : précision, centipions perdus, classification de
  chaque coup (gaffe, erreur, imprécision, occasion manquée, théorie).

• Des exercices tirés de vos erreurs. Une position ne devient un exercice que s'il
  existe un coup nettement meilleur que tous les autres — vérifié en interrogeant le
  moteur sur ses deux meilleures variantes. Pas de solution ambiguë.

• Révision espacée. Un exercice raté revient en quelques minutes, un exercice résolu
  du premier coup s'espace de jour en jour.

• Un tableau de bord qui classe vos faiblesses par ce qu'elles vous coûtent
  réellement : motifs tactiques récurrents, phase de jeu la plus chère, ouvertures où
  votre score s'effondre, erreurs selon le temps de réflexion, carte des cases où vos
  gaffes atterrissent.

• Une séance du jour construite à partir de ces faiblesses.

• Revue de partie complète : plateau, courbe d'évaluation, votre coup face à celui du
  moteur, motif tactique et conseil associé — et la possibilité de rejouer la position
  contre le moteur pour vérifier que vous avez compris, pas seulement mémorisé.

DEUX MOTEURS

Stockfish, l'un des programmes d'échecs les plus forts au monde, tourne directement
sur votre téléphone. Un moteur interne écrit pour l'application prend le relais si
nécessaire. Aucune analyse n'est envoyée à un serveur.

TOUT RESTE CHEZ VOUS

Pas de compte à créer, pas de mot de passe, pas de publicité, pas de mouchard. La
seule connexion sortante sert à télécharger vos parties. Vos analyses et vos exercices
ne quittent jamais l'appareil.

ÉCRAN PLIABLE

Sur les téléphones pliables et les tablettes, l'interface passe d'elle-même en deux
colonnes : plateau d'un côté, analyse de l'autre.

LOGICIEL LIBRE

ChessForge est publié sous licence GNU GPL v3. Son code source complet est consultable
et vérifiable par quiconque.

ChessForge n'est ni affilié, ni associé, ni approuvé par Chess.com, LLC. Chess.com est
une marque déposée de son propriétaire, citée ici à titre purement descriptif.
```

---

## Notes de version *(500 caractères max)*

```
Première version.

• Import et analyse de vos parties directement sur le téléphone
• Exercices personnels tirés de vos erreurs, avec révision espacée
• Tableau de bord des faiblesses et séance du jour
• Moteur Stockfish embarqué, aucune analyse envoyée à un serveur
• Affichage sur deux colonnes sur écran déplié
```

---

## Classement et catégorie

| Champ | Valeur |
|---|---|
| Type | Application (et non Jeu) |
| Catégorie | Éducation |
| Tags | Échecs, entraînement, analyse |
| Public visé | 13 ans et plus (à confirmer au questionnaire IARC) |

Questionnaire de classification du contenu : aucune violence, aucun contenu sexuel,
aucun jeu d'argent, aucun achat intégré, aucun contenu généré par les utilisateurs.
Classement attendu : **PEGI 3 / Tout public**.

---

## Sécurité des données

**Ce que fait l'application, techniquement** — c'est la base des réponses à donner :

1. elle transmet **le pseudonyme saisi par l'utilisateur** à `api.chess.com`, en
   HTTPS, pour télécharger ses parties publiques ;
2. elle ne transmet **rien d'autre**, à personne ;
3. le développeur **ne reçoit aucune donnée** : il n'existe aucun serveur ;
4. tout le reste est stocké dans la mémoire privée de l'application ;
5. aucune publicité, aucune mesure d'audience, aucun rapport de plantage tiers.

**Réponses proposées :**

| Question | Réponse | Justification |
|---|---|---|
| L'application collecte-t-elle des données ? | Oui | Le pseudonyme quitte l'appareil |
| Type de donnée | Informations personnelles → Identifiants utilisateur | Le pseudonyme est un identifiant |
| Collectée ou partagée ? | Partagée | Elle est transmise à chess.com, un tiers |
| Finalité | Fonctionnalité de l'application | Sans lui, rien à télécharger |
| Obligatoire ou facultative ? | Obligatoire | C'est la fonction même de l'application |
| Chiffrée en transit ? | Oui | HTTPS |
| L'utilisateur peut-il demander la suppression ? | Oui | Réglages → Effacer les données locales |

> **À vérifier par vous :** selon l'interprétation de Google, une donnée transmise à
> un service tiers **à l'initiative explicite de l'utilisateur** peut relever d'une
> exemption. Déclarer la transmission, comme proposé ci-dessus, est le choix prudent :
> une sous-déclaration est un motif de suspension, une sur-déclaration n'en est pas un.

**URL de politique de confidentialité** *(champ obligatoire)* : publiez `PRIVACY.md`
sur GitHub Pages et indiquez l'adresse obtenue. Dans les réglages du dépôt, activez
Pages sur la branche `main`, puis utilisez :

```
https://trevorlevi.github.io/ChessAnalysisApp/PRIVACY
```

---

## Éléments graphiques à fournir

| Élément | Format | État |
|---|---|---|
| Icône | PNG 512 × 512, 32 bits | **à produire** — l'icône de l'application est un vecteur, à exporter |
| Image de présentation | PNG/JPEG 1024 × 500 | **à produire** |
| Captures téléphone | 2 minimum, 8 maximum | **disponibles** dans `store/screenshots/` |
| Captures tablette 7" et 10" | facultatives | à faire depuis l'écran déplié |

Les captures présentes dans `store/screenshots/` ont été prises sur un appareil réel
et **contiennent des statistiques de jeu personnelles**. Relisez-les avant publication ;
elles ne sont volontairement pas versionnées dans le dépôt.

---

## Avant de téléverser — points bloquants

1. **Type de compte.** Un compte personnel créé après le 13 novembre 2023 impose un
   test fermé avec **12 testeurs inscrits en continu pendant 14 jours** avant de
   pouvoir demander l'accès à la production. Les comptes « organisation » en sont
   exemptés. À vérifier dans la console.
2. **Politique de confidentialité en ligne.** Le champ est obligatoire et l'URL doit
   être accessible publiquement au moment de l'examen.
3. **Marque tierce.** Le titre et l'icône ne doivent évoquer aucune marque de jeu en
   ligne. La mention de non-affiliation figure déjà en fin de description.
4. **Licence GPL v3.** L'application embarque Stockfish : le code source complet doit
   rester accessible, ce qu'assure le dépôt public.

---

## Fichier à téléverser

```
app/build/outputs/bundle/release/app-release.aab
```

Signé avec la clé de téléversement (`CN=Trevor Levi`, RSA 4096, valable jusqu'en 2054).
Activez **Play App Signing** : Google gère alors la clé de distribution, et votre clé
de téléversement peut être réinitialisée en cas de perte.
