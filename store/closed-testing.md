# Test fermé — 12 testeurs, 14 jours

Votre compte Play étant un compte personnel créé après le 13 novembre 2023, Google
impose un test fermé avant de vous donner accès à la production.

## La règle, exactement

**12 testeurs inscrits en continu pendant 14 jours consécutifs.**

Trois précisions qui font échouer la plupart des tentatives :

1. **« Inscrit » ne veut pas dire « invité ».** Un testeur ne compte qu'une fois qu'il a
   ouvert le lien d'inscription et validé son inscription depuis son compte Google.
2. **Le compteur ne démarre pas à la création de la piste.** Il démarre quand la release
   est approuvée *et* que les 12 inscriptions sont effectives. Tant que vous êtes à 11,
   rien ne court.
3. **La continuité est stricte.** Un testeur qui se désinscrit remet son compteur à zéro,
   et les 14 jours doivent être consécutifs. Prévoyez 14 ou 15 testeurs plutôt que
   pile 12 : il y aura des désistements.

Les 12 testeurs doivent être **12 comptes Google distincts**. Vos propres comptes
secondaires sont à éviter : Google examine aussi la qualité du test au moment de la
demande d'accès à la production.

## Mise en place

### 1. Créer un groupe Google

Sur <https://groups.google.com>, créez un groupe, par exemple
`chessforge-testeurs@googlegroups.com`.

C'est la méthode à privilégier : vous inscrivez **une seule adresse** dans la Play
Console, et vous gérez ensuite les arrivées et départs dans le groupe, sans jamais
retoucher à la console. Avec une liste d'adresses individuelles, chaque remplacement
vous oblige à modifier la piste.

Réglez le groupe pour que vous puissiez ajouter des membres directement.

### 2. Créer la piste de test fermé

Play Console → **Test** → **Test fermé** → créer une release.

- Téléversez `app-release.aab` (produit par `gradlew bundleRelease -PplayRelease`).
- Renseignez les notes de version (voir `play-listing.md`).
- Onglet **Testeurs** → **Groupes Google** → saisissez l'adresse du groupe.
- Publiez la release et **attendez son approbation**. Le lien d'inscription ne
  fonctionne pas avant.

### 3. Récupérer le lien d'inscription

Toujours dans l'onglet **Testeurs**, copiez le **lien d'inscription**. C'est lui que
vous transmettez — pas l'adresse de la fiche Play, qui n'existe pas encore.

### 4. Inviter

Ajoutez vos testeurs au groupe Google, puis envoyez-leur le message ci-dessous.

## Message à transmettre à vos testeurs

> Salut,
>
> J'ai développé une application Android d'échecs et j'ai besoin d'un coup de main pour
> la publier : Google exige 12 testeurs pendant 14 jours avant d'autoriser la mise en
> ligne. Ça te prend deux minutes, et il suffit ensuite de ne pas la désinstaller.
>
> **Ce que ça fait :** l'appli récupère tes parties d'échecs en ligne, les analyse
> directement sur ton téléphone et fabrique des exercices à partir de tes propres
> erreurs. Tout reste sur l'appareil, il n'y a ni compte à créer, ni publicité.
>
> **Ce que je te demande :**
>
> 1. Ouvre ce lien depuis ton téléphone Android, connecté au compte Google que tu
>    utilises sur le Play Store : `<COLLER ICI LE LIEN D'INSCRIPTION>`
> 2. Accepte de devenir testeur, puis installe l'application.
> 3. Ouvre-la une fois et laisse-la installée **14 jours**. C'est tout.
>
> Si tu joues aux échecs en ligne, entre ton pseudo dans les réglages et lance un
> import : tu auras une analyse de tes parties et tes propres exercices. Si tu n'y joues
> pas, garde simplement l'appli installée, ça suffit pour le décompte.
>
> Surtout : **ne te désinscris pas et ne désinstalle pas avant 14 jours**, sinon le
> compteur repart de zéro pour tout le monde. Merci !

## Où trouver 12 personnes

- Vos proches, camarades et collègues : c'est le plus simple et le plus fiable, et un
  téléphone Android suffit.
- Un club d'échecs, une association, un serveur Discord où vous êtes déjà connu.
- Les communautés d'entraide entre développeurs, où chacun teste l'application des
  autres.

**Évitez les services payants de « testeurs ».** Google a durci sa lutte contre les
tests fictifs, et l'usage de ces prestations expose à la suspension du compte
développeur. Le risque est sans commune mesure avec les deux semaines gagnées.

## Pendant les 14 jours

Vous pouvez continuer à publier des mises à jour sur la piste fermée : cela ne remet pas
le compteur à zéro tant que les testeurs restent inscrits. C'est le bon moment pour
corriger ce que les retours font apparaître.

Suivez l'avancement dans **Tableau de bord → Accès à la production**, où Google affiche
le nombre de testeurs comptabilisés et les jours restants.

## Au bout des 14 jours

Demandez l'accès à la production depuis le tableau de bord. Google pose alors des
questions sur la manière dont vous avez testé, ce que vous en avez tiré et ce que vous
avez corrigé. Répondez concrètement : des réponses vagues font rejeter la demande.

Notez au fil de l'eau les retours reçus et les corrections apportées — vous les aurez
sous la main au moment de répondre.
