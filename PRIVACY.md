# Politique de confidentialité — ChessForge

*Dernière mise à jour : 25 septembre 2026*

ChessForge est une application d'analyse de parties d'échecs qui fonctionne
**entièrement sur votre appareil**. Cette politique décrit précisément les seules
données manipulées et ce qu'il en advient.

## En résumé

- ChessForge **ne crée aucun compte** et ne demande **aucun mot de passe**.
- ChessForge **n'a aucun serveur**. Le développeur ne reçoit, ne stocke et ne
  consulte **aucune** de vos données.
- ChessForge ne contient **ni publicité, ni outil de mesure d'audience, ni
  rapport de plantage**.
- Vos parties, analyses et exercices restent dans la mémoire privée de votre
  téléphone et disparaissent avec l'application.

## La seule donnée qui quitte votre appareil

Pour télécharger vos parties, l'application doit les demander à chess.com. Elle
envoie pour cela **votre pseudonyme chess.com** aux serveurs de chess.com, via
l'interface de programmation publique `api.chess.com`, en connexion chiffrée
(HTTPS).

- Ce pseudonyme est celui que **vous saisissez vous-même** dans les réglages.
- Il n'est transmis qu'à chess.com, **jamais au développeur ni à un tiers**.
- Seules des données **déjà publiques** sont récupérées : vos parties terminées
  et votre classement, tels que n'importe qui peut les consulter sur votre
  profil chess.com.
- Aucune donnée n'est envoyée à chess.com : l'application lit, elle n'écrit pas.

L'usage que chess.com fait de ces requêtes relève de sa propre politique de
confidentialité.

## Ce qui est stocké sur votre appareil

Dans la zone de stockage privée de l'application, inaccessible aux autres
applications :

| Donnée | Usage |
|---|---|
| Vos parties (notation PGN) | Rejouer et analyser |
| Les analyses produites par le moteur | Statistiques et progression |
| Les exercices tirés de vos erreurs | Entraînement et révision |
| Vos réglages, dont votre pseudonyme | Fonctionnement de l'application |

## Permission demandée

L'application ne demande qu'**une seule permission** : l'accès à Internet,
uniquement pour contacter `api.chess.com`. Aucun accès à vos contacts, à votre
position, à vos fichiers, à l'appareil photo ni au microphone.

## Supprimer vos données

- **Réglages → Effacer les données locales** supprime immédiatement parties,
  analyses et exercices.
- Désinstaller l'application supprime la totalité des données.

Comme rien n'est transmis au développeur, aucune demande de suppression ne peut
lui être adressée : il n'a rien à supprimer.

## Enfants

L'application ne s'adresse pas spécifiquement aux enfants et ne collecte
sciemment aucune donnée les concernant.

## Modifications

Toute modification de cette politique sera publiée sur cette page, avec une date
de mise à jour.

## Contact

Questions ou remarques : ouvrez un ticket sur
<https://github.com/TrevorLevi/ChessAnalysisApp/issues>.

---

*ChessForge est un logiciel libre sous licence GNU GPL v3. Son code source
complet est consultable, ce qui permet à quiconque de vérifier les affirmations
ci-dessus.*

*ChessForge n'est ni affilié, ni associé, ni approuvé par Chess.com, LLC.*
