# Plan de développement — Fenêtre « Holy Grail » (Chronicle)

## 1. Constat et périmètre

La demande : une nouvelle fenêtre GoMule qui affiche la progression du **Holy Grail** —
combien des items « collectionnables » du jeu ont été trouvés — en se basant sur les items
présents dans les fichiers `.d2s`, `.d2i` et `.d2x` **ouverts**, et qui reproduit les
fonctionnalités des deux captures fournies (l'écran *Chronicle* du mod Reimagined).

Bonne nouvelle : le mod fournit lui-même la définition de son grail. `d2111/uniqueitems.txt`
et `d2111/setitems.txt` ont une colonne **`disableChronicle`** ; `d2111/itemtypes.txt` a une
colonne **`UICategory`** ; `d2111/sets.txt` a une colonne **`UIClass`**. Ce sont exactement
les données qui pilotent l'arbre de catégories et le contenu des listes dans les captures.
Il n'y a donc rien à inventer côté taxonomie : tout se lit dans les `.txt` déjà chargés par
`D2TxtFile`.

> **Langue de l'interface : anglais.** Ce document de conception est en français, mais tous
> les libellés visibles de la fenêtre (titre, onglets, cases à cocher, arbre, colonnes,
> boutons, infobulles, messages d'avertissement) sont en **anglais**, comme le reste de
> GoMule (« Open Character », « Save All », « Runeword », « Filter… »). Les croquis du §3
> sont donc écrits avec les libellés définitifs.

> **Portée du grail : réglable par l'utilisateur.** Le filtre `disableChronicle` n'exclut pas
> définitivement des entrées : il pilote une case à cocher **`Include non-Chronicle items`**.
> Décochée (défaut), la fenêtre affiche exactement le grail du jeu ; cochée, elle affiche
> l'intégralité des uniques et items de set de la table. Voir §1.2, §5.1 et §7.

### 1.1 Ce que les captures montrent et que GoMule peut reproduire

| Élément de l'écran Chronicle | Reproductible ? | Source |
|---|---|---|
| Onglets Unique / Sets / Runewords | Oui | `uniqueitems.txt`, `setitems.txt`, `runes.txt` |
| Portée du grail (items hors Chronicle) | Oui | colonne `disableChronicle`, exposée en case à cocher |
| Cases Normal / Exceptional / Elite | Oui | `normcode` / `ubercode` / `ultracode` du code de base |
| Arbre de catégories (Armor, Weapons > Axes, Bows…) | Oui | `itemtypes.txt` colonne `UICategory` (+ `UICatOverride`) |
| Arbre par classe pour les Sets (General, Amazon…) | Oui | `sets.txt` colonne `UIClass` |
| Liste avec icône + nom, entrées non trouvées grisées | Oui | `invfile` de la ligne unique/set, `D2ImageCache.getDC6Image` |
| Barre de progression + pourcentage | Oui | calcul local |
| Champ de recherche | Oui | filtre texte sur le nom |
| Liste déroulante All / Discovered | Oui | filtre Tout / Trouvés / Manquants |
| Infobulle avec les propriétés de l'item | Oui | `D2ItemRenderer` (trouvé) ou `D2TxtFile.propToStat` (manquant) |
| Regroupement par set avec en-tête (« Arcanna's Tricks ») | Oui | colonne `set` de `setitems.txt` |
| **« Dropped By: Dark One »** | **Non** | le monstre d'origine n'est écrit nulle part dans un `.d2s` |
| **« First Found: 08/21/2026, 20:56 »** | **Partiellement** | à horodater nous-mêmes, cf. §8 |
| **« View Rewards »** | **Non** | récompenses internes au mod, invisibles depuis les saves |

Les deux lignes « Non » ne sont pas des oublis : un item sauvegardé ne conserve aucune trace
de sa provenance. Le plan les remplace par des informations que GoMule *possède* :
« Trouvé dans : `Sorceress.d2s`, `mule2.d2x` » et un compteur d'exemplaires.

### 1.2 Volumétrie réelle (mesurée sur `./d2111`)

| Table | Lignes exploitables | Grail du jeu (`disableChronicle` vide) | Case `Include non-Chronicle items` cochée |
|---|---|---|---|
| `uniqueitems.txt` | 1483 lignes de données | **419** | **1470** (7 lignes `disabled` + 6 lignes d'en-tête exclues) |
| `setitems.txt` | 455 | **135** | **455** (aucune ligne `disabled`) |
| `runes.txt` (runewords) | 211 | **208** (`complete = 1`, dédupliqué) | **208** — inchangé |

> **Chiffres corrigés après implémentation.** Une premiere version de ce plan annonçait
> 1475 / 424 / 209. Trois faits mesurés sur les données réelles l'ont infirmée :
> `uniqueitems.txt` contient **6 lignes d'en-tête de section** à `*ID` vide et sans code de base
> (`Expansion`, `Armor`, `Elite Uniques`, `Rings`, `Class Specific`, `Warlock Class Pack`) qui ne
> sont pas des items ; une 7e ligne à `*ID` vide, `Talonrage`, est un vrai item mais porte
> `disabled = 1` ; et `runes.txt` contient **deux** lignes `complete = 1` partageant le même
> `Name` (`Doom1` — une variante bâtons, une variante haches/masses), fusionnées en une seule
> entrée pour garder les clés uniques. D'où 1483 − 6 − 7 = **1470** uniques et **208** runewords.

`runes.txt` n'a pas de colonne `disableChronicle` : la case à cocher ne change donc rien à
l'onglet Runewords, et elle y est désactivée (grisée) plutôt que masquée, pour que
l'utilisateur voie qu'elle existe et pourquoi elle est sans effet ici.

Soit **762 entrées** en mode Chronicle, **2133** tout compris. C'est petit dans les deux cas :
l'index complet et le rescan tiennent largement en mémoire et se recalculent en quelques
millisecondes. **L'index est construit une seule fois avec *toutes* les lignes**, chaque
entrée portant un booléen `chronicle` ; la case à cocher n'est qu'un filtre d'affichage, pas
une reconstruction. Aucune optimisation prématurée n'est nécessaire.

## 2. Objectif

Une fenêtre interne (`JInternalFrame`, comme les autres vues) intitulée « Holy Grail »,
ouverte depuis la barre d'outils et le menu, qui :

1. liste toutes les entrées du grail (uniques, items de set, runewords) ;
2. marque chacune comme trouvée ou manquante d'après les fichiers ouverts ;
3. se filtre par onglet, tier, catégorie, statut, texte libre et portée (grail du jeu seul ou
   toutes les entrées de la table) ;
4. affiche la progression globale et par catégorie ;
5. donne le détail d'une entrée (propriétés, où elle a été trouvée).

## 3. Croquis de l'interface

Tous les libellés ci-dessous sont ceux qui seront réellement affichés — donc en anglais.

### 3.1 Onglet « Uniques »

```
┌─ Holy Grail ──────────────────────────────────────────────────────────────────┐
│  ( Uniques ) (  Sets  ) ( Runewords )             Search: [_______________]   │
├───────────────────────────────────────────────────────────────────────────────┤
│  [x] Normal  [x] Exceptional  [x] Elite                  Show: [ All      |v] │
│  [ ] Include non-Chronicle items                                              │
├────────────────────────┬──────────────────────────────────────────────────────┤
│ v Armor                │ ┌──────┐                                             │
│     Helms              │ │ (img)│  Aegis                            [E]  ---  │
│     Body Armor         │ │ grey │  Ward - Elite                     Missing   │
│     Shields            │ └──────┘                                             │
│     Gloves             │ ─────────────────────────────────────────────────────│
│     Boots              │ ┌──────┐                                             │
│     Belts              │ │ (img)│  Harlequin Crest                  [E]  Found │
│ > Weapons              │ │ color│  Shako - Elite            Sorceress.d2s x2  │
│     Axes               │ └──────┘                                             │
│     Bows               │ ─────────────────────────────────────────────────────│
│     Crossbows          │ ┌──────┐                                             │
│     Daggers            │ │ (img)│  Nokozan Relic                    [N]  Found │
│     Javelins           │ │ color│  Amulet                      mule1.d2x x1   │
│     Maces              │ └──────┘                                             │
│     Polearms           │ ─────────────────────────────────────────────────────│
│ > Misc                 │ ┌──────┐                                             │
│     Amulets            │ │ (img)│  The Cat's Eye                    [E]  ---  │
│     Rings              │ │ grey │  Amulet                           Missing   │
│     Charms             │ └──────┘                                             │
├────────────────────────┤                                                      │
│  Weapons: 12 / 178     │                                                      │
│  [####------------] 7% │                                                      │
├────────────────────────┤                                                      │
│  TOTAL: 37 / 424   8%  │                                                      │
│  [###-------------]    │                                                      │
├────────────────────────┤                                                      │
│  [     Export...     ] │                                                      │
└────────────────────────┴──────────────────────────────────────────────────────┘
```

Le bouton « View Rewards » de la capture n'a pas d'équivalent exploitable : il est remplacé
par « Export… » (dump texte/CSV de la liste courante), cohérent avec les autres exports
déjà présents dans GoMule (`fullDump`, `singleTxtDump`).

Quand `Include non-Chronicle items` est cochée, les entrées ajoutées sont visuellement
distinguées (nom suffixé d'un `*` et infobulle « Not part of the in-game Chronicle »), et les
deux barres de progression basculent sur les totaux étendus :

```
│  [x] Include non-Chronicle items                                              │
...
│  TOTAL: 51 / 1470  3%  │   ┌──────┐  Crafted Cold Rupture *          [E]  --- │
│  [#---------------]    │   │ grey │  Amulet                          Missing  │
```

### 3.2 Onglet « Sets » (groupement par set, comme la capture 2)

```
┌────────────────────────┬──────────────────────────────────────────────────────┐
│   General              │  == Angelic Raiment ==                        2 / 4  │
│   Amazon               │ ─────────────────────────────────────────────────────│
│   Sorceress            │ ┌──────┐  Angelic Halo                       Found   │
│   Necromancer          │ │ color│  Ring                    mule1.d2x x1       │
│   Paladin              │ └──────┘                                             │
│   Barbarian            │ ┌──────┐  Angelic Mantle                     ---     │
│   Druid                │ │ grey │  Ring Mail                      Missing     │
│   Assassin             │ └──────┘                                             │
│   Warlock              │ ┌──────┐  Angelic Sickle                     Found   │
│                        │ │ color│  Sabre                  Barbarian.d2s x1    │
│                        │ └──────┘                                             │
│                        │  == Arcanna's Tricks ==                       0 / 4  │
│                        │ ─────────────────────────────────────────────────────│
│                        │ ┌──────┐  Arcanna's Sign                     ---     │
│                        │ │ grey │  Amulet                         Missing     │
└────────────────────────┴──────────────────────────────────────────────────────┘
```

### 3.3 Infobulle au survol (équivalent de la capture 2)

```
                      ┌───────────────────────────────┐
                      │      Angelic Sickle (3)       │  <- vert = set
                      │          Sabre [N]            │
                      │ One-Hand Damage: 3 to 8       │
                      │ Durability: 125 of 250        │
                      │ Required Dexterity: 25        │
                      │ Required Strength: 25         │
                      │ Required Level: 12            │
                      │ +75 to Attack Rating          │  <- bleu = props
                      │ +250% Damage to Undead        │
                      │ ───────────────────────────── │
                      │ Found in: Barbarian.d2s       │  <- remplace
                      │ Copies: 1                     │     "Dropped By"
                      │ First seen: 08/21/2026 20:56  │  <- horodaté par GoMule
                      └───────────────────────────────┘
```

## 4. Architecture

```
 Fichiers ouverts : D2FileManager.iItemLists  (.d2s / .d2x / .d2i)
                    │
                    │  getItemList()  +  récursion getiSocketedItems()
                    ▼
        ┌───────────────────────┐        ┌─────────────────────────────┐
        │   D2GrailScanner      │        │      D2GrailIndex           │
        │  parcourt les items,  │        │  construit une fois les     │
        │  produit une clé par  │        │  ~768 entrées depuis        │
        │  item collectionnable │        │  uniqueitems / setitems /   │
        └───────────┬───────────┘        │  runes + itemtypes          │
                    │                    └──────────────┬──────────────┘
                    │  Map<D2GrailKey, D2GrailFinding>  │ List<D2GrailEntry>
                    └──────────────┬────────────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │       D2GrailModel           │
                    │  entrées + statut + filtres  │
                    │  + statistiques par noeud    │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │   D2ViewGrail (JInternalFrame)│
                    │  onglets / arbre / liste /    │
                    │  progression / recherche      │
                    └──────────────────────────────┘
```

Séparation volontaire : `D2GrailIndex` ne dépend que de `D2TxtFile` (donc testable sans
Swing et sans save), `D2GrailScanner` ne dépend que de `D2Item`, et seule `D2ViewGrail`
touche à Swing. C'est ce qui rend l'essentiel de la fonctionnalité testable en headless.

### 4.1 Nouveaux fichiers

| Fichier | Rôle |
|---|---|
| `src/main/java/gomule/grail/D2GrailKey.java` | Identité d'une entrée : `(TYPE, id)` — `UNIQUE:412`, `SET:37`, `RUNEWORD:"Insight"`. `equals`/`hashCode`. |
| `src/main/java/gomule/grail/D2GrailEntry.java` | Une ligne du grail : clé, nom affiché, code de base, nom de base, tier, catégorie UI, nom de set, taille du set, `invfile`, **booléen `chronicle`** (`disableChronicle` vide), ligne `D2TxtFileItemProperties` d'origine. |
| `src/main/java/gomule/grail/D2GrailIndex.java` | Construit la liste **complète** des entrées depuis les `.txt` (Chronicle et hors Chronicle), une seule fois. Cache statique (les tables sont statiques et immuables une fois chargées). |
| `src/main/java/gomule/grail/D2GrailCategories.java` | Table `UICategory` → libellé anglais affiché + rattachement à une racine (Armor / Weapons / Misc), et `UIClass` → libellé de classe. |
| `src/main/java/gomule/grail/D2GrailScanner.java` | Parcourt des `D2ItemList` et retourne `Map<D2GrailKey, D2GrailFinding>`. |
| `src/main/java/gomule/grail/D2GrailFinding.java` | Ce qu'on sait d'une entrée trouvée : nombre d'exemplaires, ensemble des fichiers, premier `D2Item` rencontré (pour l'infobulle), date de première découverte. |
| `src/main/java/gomule/grail/D2GrailModel.java` | Index + findings + état des filtres → lignes affichables et statistiques. |
| `src/main/java/gomule/gui/D2ViewGrail.java` | La fenêtre. |
| `src/main/java/gomule/gui/D2GrailListRenderer.java` | Rendu d'une ligne (icône couleur/grisée, nom, tier, statut). |

### 4.2 Modifications de fichiers existants (toutes minimes)

| Fichier | Modification | Risque |
|---|---|---|
| `gomule/item/D2Item.java` | ajouter `getUniqueID()` (le champ `unique_id` existe déjà ligne 156, sans accesseur) et mémoriser le nom du runeword résolu ligne 527-532 dans un champ `iRuneWordIndex` + accesseur | **Nul** : aucune lecture de bits modifiée |
| `gomule/gui/D2FileManager.java` | accesseur `getOpenItemLists()` sur `iItemLists` ; bouton barre d'outils + entrée de menu ouvrant `D2ViewGrail` ; enregistrement/désenregistrement de la vue | Faible |

L'état de la case `Include non-Chronicle items` est mémorisé dans `projects/projects.properties`
via `FileManagerProperties.loadFileManagerProperties()` / `saveFileManagerProperties()`
(mécanisme déjà utilisé pour le look-and-feel) : aucune nouvelle infrastructure de
configuration à créer.

**Contrainte impérative** (cf. `CLAUDE.md`) : ne toucher à aucun `read()` / `skipBits()` /
ordre de lecture dans `D2Item`. Les deux ajouts ci-dessus sont un getter et une affectation
de champ dans une branche déjà exécutée. Le rappel vaut d'être écrit noir sur blanc, parce
que c'est le seul endroit du plan qui touche au parser.

## 5. Construction de l'index (`D2GrailIndex`)

### 5.1 Uniques

Pour chaque ligne de `D2TxtFile.UNIQUES` :

- **exclure définitivement** si `disabled` est non vide (7 lignes — des items que le mod a
  retirés du jeu, ils ne sont trouvables dans aucun cas) ou si la ligne est le séparateur
  `Expansion` ;
- **marquer** `chronicle = (disableChronicle != "1")` (1058 lignes sur 1482 sont hors
  Chronicle) — sans l'exclure : c'est la case `Include non-Chronicle items` qui décide si
  l'entrée est affichée et comptée (§7). Vérifié : aucune des 7 lignes `disabled` n'est dans
  le Chronicle, donc le mode « grail du jeu » donne bien 424 entrées avec ou sans ce filtre ;
- **exclure** les 6 lignes d'en-tête de section (`*ID` vide et code de base vide) ;
- clé = `UNIQUE:<id positionnel>`. ⚠️ **Ne PAS utiliser la colonne `*ID`.** C'est
  l'erreur de la première version de ce plan, corrigée après vérification sur de vraies
  sauvegardes. `D2TxtFile.searchByID()` est **purement positionnel** : il renvoie `iData[id]`,
  décalé de 1 une fois passée l'unique ligne `Expansion` (position 129), et **ignore totalement**
  les autres lignes d'en-tête. La colonne `*ID` semble coïncider au début de la table (ligne 128
  `SuperKhalimFlail` / `*ID` 128, ligne 130 `Coldkill` / `*ID` 129), mais cette concordance est un
  accident de position, pas une garantie : au-delà des en-têtes suivantes, `*ID` dérive.
  **Preuve mesurée sur `pally3/4/5.d2s`** : le joyau `Heaven Facet` serti dans
  `Hand of Blessed Light` porte `unique_id = 1400` dans les trois sauvegardes — exactement
  `searchByID(1400)` — alors que la colonne `*ID` de cette même ligne indique **1398**. La dérive
  croît avec la profondeur (à `Blinded by Faith`, id 1481, `*ID` vaut 1478). Keyer sur `*ID`
  aurait mal attribué la majorité du contenu moddé. Il faut donc **recalculer le même id
  positionnel que `searchByID`** : `id = (ligne < ligneExpansion) ? ligne : ligne - 1` ;
- **les items de set ne sont pas concernés** : `D2Item` les résout via
  `SETITEMS.searchColumns("*ID", ...)` (`D2Item.java:906`), une correspondance textuelle sur la
  colonne `*ID` elle-même et non une recherche positionnelle. Pour les sets, keyer sur `*ID` est
  donc exactement ce qu'il faut ;
- nom affiché : `getTranslation(index)`, repli sur `index` si absent (même politique que
  `D2Item` ligne 935, et que le `getTranslationOrNull` du playbook) ;
- code de base : colonne `code` ; nom de base : `*ItemName` ;
- icône : colonne `invfile` ; si vide, `invfile` de la ligne du code de base
  (`D2TxtFile.search(code)`), qui est le repli qu'applique déjà `D2Item` ligne 940.

### 5.2 Items de set

Pour chaque ligne de `D2TxtFile.SETITEMS` : mêmes règles (aucune ligne `disabled` ici, 320
lignes hors Chronicle sur 455), clé `SET:<*ID>`, code de base dans la colonne `item`, nom de
set dans `set`, taille du set = nombre de lignes ayant le même `set`
(`searchColumnsMultipleHits`, exactement comme `D2Item` ligne 913).
La classe du set vient de `D2TxtFile.FULLSET` colonne `UIClass` (vide = « General »).

Attention au compteur « n / taille » des en-têtes de set (§3.2) : la taille affichée doit
suivre le mode d'affichage. Un set dont 2 pièces sur 6 sont dans le Chronicle affiche
`x / 2` en mode Chronicle et `x / 6` case cochée — sinon un set apparaîtrait complet à
`2 / 6`, ce qui serait faux dans les deux lectures.

### 5.3 Runewords

Pour chaque ligne de `D2TxtFile.RUNES` avec `complete == "1"` (209 sur 211, **dédupliquées par
`Name` → 208 entrées** : `Doom1` apparaît deux fois, une variante bâtons et une variante
haches/masses, qui doivent former une seule entrée de grail) : clé
`RUNEWORD:<Name>`, nom affiché `getTranslation(Name)` avec repli sur `*Rune Name`, runes
requises dans `Rune1..Rune6`, types de base autorisés dans `itype1..itype6`.
Les runewords n'ont ni tier ni `invfile` : sur cet onglet, les cases Normal/Exceptional/Elite
sont désactivées et l'icône est celle de la première rune du mot (`Rune1` → `misc.txt`).
`runes.txt` n'a pas non plus de colonne `disableChronicle` : toutes les entrées sont marquées
`chronicle = true`, et la case `Include non-Chronicle items` est grisée sur cet onglet.

### 5.4 Tier (Normal / Exceptional / Elite)

`normcode`, `ubercode` et `ultracode` existent dans les trois tables (`misc`, `armor`,
`weapons`). Pour le code de base d'une entrée, on lit sa ligne via `D2TxtFile.search(code)`
et on compare :

```
code == normcode   -> Normal
code == ubercode   -> Exceptional
code == ultracode  -> Elite
sinon (colonnes vides : anneaux, amulettes, charmes, joyaux) -> Normal
```

### 5.5 Catégorie d'arbre

```
ligne du code de base (misc/armor/weapons)
      │
      ├─ UICatOverride non vide ? ──oui──> catégorie = UICatOverride
      │
      └─ non ──> colonne `type` ──> itemtypes.txt (Code) ──> colonne UICategory
                                          │
                                          └─ vide ? remonter par Equiv1 puis Equiv2
                                             (même chaîne de repli que D2Item l.428-433)
```

Les 37 valeurs de `UICategory` présentes dans `itemtypes.txt` sont :
`amazo, ammo, amule, armor, assas, axes, barbh, belts, boots, bows, charm, circl, daggs,
druid, gems, glove, helms, javel, jewel, maces, necro, palad, poles, potis, rings, runes,
scept, scrlt, shlds, sorce, spear, stave, sword, throw, wands, warlo, xbows`.

Aucune traduction n'existe pour ces codes (vérifié : absents de `ui-controller.json`), donc
`D2GrailCategories` porte une table en dur code → **libellé anglais**, plus le rattachement à
une racine :

- **Armor** : `armor` (Body Armor), `helms` (Helms), `shlds` (Shields), `boots` (Boots),
  `glove` (Gloves), `belts` (Belts), `circl` (Circlets), `barbh` (Barbarian Helms),
  `druid` (Druid Pelts), `necro` (Necromancer Shrunken Heads), `palad` (Paladin Shields),
  `sorce` (Sorceress Orbs), `assas` (Assassin Katars), `warlo` (Warlock Grimoires)
- **Weapons** : `axes` (Axes), `bows` (Bows), `xbows` (Crossbows), `daggs` (Daggers),
  `javel` (Javelins), `maces` (Maces), `poles` (Polearms), `scept` (Scepters),
  `spear` (Spears), `stave` (Staves), `sword` (Swords), `throw` (Throwing Weapons),
  `wands` (Wands), `amazo` (Amazon Weapons), `ammo` (Quivers)
- **Misc** : `amule` (Amulets), `rings` (Rings), `charm` (Charms), `jewel` (Jewels),
  `gems` (Gems), `runes` (Runes), `potis` (Potions), `scrlt` (Scrolls & Books)

À ces 37 valeurs s'ajoute **`dns` → « Colossal Jewels » (Misc)**, découverte à
l'implémentation : six Colossal Jewels uniques (code de base `cjw`) tirent leur catégorie de la
colonne `UICatOverride` de `misc.txt`, dont la valeur `dns` n'existe pas dans `itemtypes.txt`.

`UICatOverride` contient 6 autres valeurs inconnues (`uberm`, `porb`, `terrt`, `keysr`, `absol`
et la chaîne littérale `"Null"`) — **vérifié : aucune n'est atteignable depuis une entrée du
grail**, car aucun unique ni item de set n'a pour base un item les portant. La résolution doit
malgré tout dégrader proprement : un code inconnu rend l'entrée **non catégorisée** (code,
libellé et racine tous `null` ensemble), jamais un code avec un libellé nul. Invariant à tenir :
*un code de catégorie implique toujours un libellé et une racine*.

**25 entrées n'ont légitimement aucune catégorie** : 10 emplacements d'uniques réservés sans code
de base (`Gore Ripper`, `Odium`, `Darkfear`…) et 15 utilitaires de type `grab` ou `stor`
(grabbers, pinces, Gem Bag, Keychain) dont la chaîne `type → UICategory` est vide dans
`itemtypes.txt`. L'arbre de catégories doit donc rester traversable sans elles, et un nœud
`All` (ou `Uncategorized`) doit les garder atteignables — sinon elles deviennent invisibles.

Ce découpage est une hypothèse raisonnable calquée sur les captures (qui ne montrent que les
racines « Armor » et « Weapons », la liste étant tronquée par le défilement). Il est isolé
dans une seule table pour être ajustable en une ligne après comparaison avec le jeu.

## 6. Détection « trouvé » (`D2GrailScanner`)

### 6.1 Où chercher

```
pour chaque D2ItemList ouverte (D2FileManager.getOpenItemLists()) :
    pour chaque D2Item de list.getItemList() :
        traiter(item)
        pour chaque socketed de item.getiSocketedItems() :   <-- récursif
            traiter(socketed)
```

Trois pièges concrets :

1. **Les items sertis comptent.** Un joyau unique ou une rune dans une armure n'apparaît pas
   dans `getItemList()` : il est dans `getiSocketedItems()` de son porteur. Sans la récursion,
   une partie du grail serait invisible. (`location == 6` = serti.)
2. **`D2ItemListAll` n'est pas utilisable ici.** Son constructeur n'agrège que
   `getCharList()` + `getStashList()` (`D2ItemListAll.java:37-38`) : **les `.d2i` sont
   absents**. La demande impose explicitement les `.d2i` → parcourir `iItemLists` directement.
3. **Les items de cadavre sont hors périmètre.** `D2Character.getItemList()` renvoie
   `iCharItems` + `iMercItems` mais pas `iCorpseItems` (`D2Character.java:812-817`).
   Comportement acceptable et à documenter ; ne pas modifier `getItemList()`, d'autres vues
   en dépendent.

### 6.2 Comment identifier

```
item.isUnique()   -> clé UNIQUE:item.getUniqueID()        (accesseur à ajouter)
item.isSet()      -> clé SET:item.getSetID()              (existe déjà, l.1836)
item.isRuneWord() -> clé RUNEWORD:item.getRuneWordIndex() (accesseur à ajouter)
sinon             -> ignoré
```

Pour le runeword, `D2Item.readExtend` résout déjà la ligne de `runes.txt`
(`D2Item.java:527-532`) mais ne la conserve pas : le nom traduit écrase `iItemName` et
l'identité d'origine est perdue. Reconstruire l'identification côté grail à partir des codes
de runes serait dupliquer `searchRuneWord` — et donc risquer de diverger d'elle. On mémorise
donc `lRuneWord.get("Name")` dans un champ au moment où la ligne est déjà en main.

Garde-fous à respecter dans le scanner :

- `unique_id` vaut `-1` tant qu'aucune ligne n'a été lue → ignorer les clés négatives ;
- **le scan est indépendant de la portée Chronicle** : il enregistre toutes les découvertes,
  y compris celles des entrées `chronicle = false`. C'est le filtre d'affichage qui décide
  ensuite quoi montrer. Cocher la case ne doit donc jamais déclencher un rescan, et une
  découverte hors Chronicle faite avant que la case soit cochée doit apparaître
  immédiatement, avec la bonne date de première découverte ;
- une clé absente de l'index (item d'un mod plus récent que `./d2111`, ou ligne `disabled`)
  ne doit **jamais** lever d'exception : elle est simplement ignorée. C'est la même
  philosophie que le repli de traduction du playbook — un item inconnu ne peut pas casser
  toute la vue ;
- un fichier dont le chargement est partiel (`isItemsIncomplete()`, ou une tab `.d2i`
  incomplète — cf. `D2SharedStash.hasVisibleIncompletePane()`) donne un décompte
  potentiellement sous-estimé. La fenêtre doit l'indiquer, sinon un utilisateur croira avoir
  perdu un item : un bandeau d'avertissement `n file(s) loaded partially - counts may be
  incomplete` en haut de la fenêtre, avec la liste des fichiers en infobulle.

### 6.3 Rafraîchissement

`D2ViewGrail` implémente `D2ItemListListener` et s'abonne à chaque `D2ItemList` ouverte, comme
le fait `D2ViewStash`. Sur `itemListChanged()` : rescan complet (quelques ms) puis
rafraîchissement de la liste et des statistiques. À l'ouverture/fermeture d'un fichier, le
`D2FileManager` réenregistre la vue sur la nouvelle liste — même mécanisme que
`D2ItemListAll.connect()` / `disconnect()`.

## 7. Comportement de l'interface

| Contrôle (libellé affiché) | Effet |
|---|---|
| Onglets `Uniques` / `Sets` / `Runewords` | change le jeu d'entrées **et** l'arbre de gauche (catégories UI ↔ classes de set ↔ types de base) |
| Cases `Normal` / `Exceptional` / `Elite` | filtre sur le tier ; désactivées sur l'onglet Runewords ; au moins une case doit rester cochée |
| Case **`Include non-Chronicle items`** | décochée (défaut) : seules les entrées `chronicle = true`, soit le grail du jeu ; cochée : toutes les entrées de la table. Affecte l'affichage **et** les dénominateurs de toutes les barres de progression, ainsi que le `n / taille` des en-têtes de set. Désactivée sur l'onglet Runewords (§5.3). L'état est mémorisé entre deux ouvertures de la fenêtre (`FileManagerProperties`). |
| Arbre de gauche | filtre par catégorie ; un nœud racine sélectionné inclut ses enfants ; nœud `All` en tête |
| Liste déroulante `Show:` | `All` / `Found` / `Missing` |
| Champ `Search:` | filtre insensible à la casse et aux accents sur le nom affiché **et** le nom de base ; les codes couleur `ÿc4` doivent être retirés avant comparaison (`D2ItemRenderer.stripColorCodes`) |
| Double-clic sur une entrée trouvée | ouvre/active la fenêtre du fichier qui la contient |
| Survol | infobulle (§3.3) |
| Barre de progression haute | progression du nœud d'arbre sélectionné |
| Barre de progression basse | progression de l'onglet entier, filtres de tier **et** de portée Chronicle appliqués, filtres de statut/recherche **non** appliqués (sinon `Missing` afficherait toujours 0 %) |
| Bouton `Export...` | dump texte/CSV de la liste courante, filtres appliqués |

Rendu d'une entrée manquante : icône passée en niveaux de gris
(`GrayFilter.createDisabledImage`), nom en gris, statut `Missing`. Entrée trouvée : icône
couleur, nom dans la couleur de qualité (`D2Item.getItemColor()` : doré pour unique, vert pour
set, doré pour runeword), statut `Found`, plus le nombre d'exemplaires et les fichiers.
Entrée hors Chronicle (visible seulement case cochée) : nom suffixé d'un `*` et infobulle
`Not part of the in-game Chronicle`.

Chargement d'icône : passer par le même garde-fou que `D2ItemImagePanel.imageFor` — un `.dc6`
manquant ou un `invfile` vide doit donner une case vide, pas une exception. Attention,
`D2ImageCache.getDC6Image(D2Item)` prend un `D2Item` : pour une entrée **manquante** il n'y a
pas d'item, on appellera donc directement `D2ImageCache.getDC6Image(invfile + ".dc6")`
(surcharge publique existante, `D2ImageCache.java:111`).

## 8. Persistance : « First seen »

La date de première découverte n'existe dans aucun fichier de save. Deux options :

- **A (retenue, phase 4)** : GoMule l'enregistre lui-même. Au premier scan où une clé apparaît,
  on horodate et on écrit dans `projects/<projet>-grail.properties`
  (`UNIQUE:412=1756900000000`). Le fichier est lu au démarrage et complété à chaque scan ; on
  n'efface jamais une date même si l'item est vendu. Le format `Properties` est déjà celui de
  `D2Project.saveProject()`, donc rien de nouveau à introduire.
- **B** : ne rien afficher. Plus simple, mais on perd une information qui a de la valeur pour
  un joueur qui suit sa progression.

Limite à assumer et à écrire dans l'interface : la date est celle où **GoMule** a vu l'item
pour la première fois, pas celle du drop. D'où le libellé `First seen:` et non `First Found:` —
c'est honnête et utile ; reprendre tel quel le libellé du jeu ne le serait pas.

L'horodatage couvre **toutes** les entrées, y compris hors Chronicle : sinon, cocher la case
des mois plus tard laisserait des centaines d'entrées trouvées sans date.

## 9. Étapes d'implémentation

Chaque phase est autonome, compile et se teste seule.

**Phase 1 — Index (aucune UI).**
`D2GrailKey`, `D2GrailEntry`, `D2GrailCategories`, `D2GrailIndex`. Test : l'index complet
contient 1470 uniques, 455 items de set et 208 runewords, dont respectivement 419, 135 et 208
marqués `chronicle = true` ; aucune entrée sans nom ; toutes les catégories sont rattachées à
une racine ; aucun `*ID` en double.

**Phase 2 — Scanner.**
`getUniqueID()` + `getRuneWordIndex()` dans `D2Item`, `D2GrailScanner`, `D2GrailFinding`.
Test : sur les personnages de `src/test/resources/charFiles/`, le scan ne lève rien, et les
uniques/sets connus de ces fixtures sont détectés (assertions sur des items précis, pas
seulement sur un total — cf. la convention de fixtures du playbook). Test dédié à la
récursion : un item serti d'un joyau unique ou d'une rune est bien compté.

**Phase 3 — Fenêtre.**
`D2GrailModel`, `D2ViewGrail`, `D2GrailListRenderer`, accesseur `getOpenItemLists()`, bouton
de barre d'outils + entrée de menu, abonnement `D2ItemListListener`, case
`Include non-Chronicle items` et son effet sur les dénominateurs. Tous les libellés en
anglais. Vérification manuelle (§10).

**Phase 4 — Finitions.**
Infobulles, export, persistance du « First Found », bandeau de chargement partiel,
double-clic vers le fichier d'origine.

`./gradlew clean build` doit être vert à la fin de chaque phase, et toute instrumentation
temporaire supprimée avant commit.

## 10. Vérification manuelle

Lancer `build/libs/GoMule-Reimagined-1.0-SNAPSHOT.jar` et contrôler :

- ouvrir un `.d2s`, un `.d2x` et un `.d2i`, puis la fenêtre grail : le total trouvé augmente
  bien avec chaque fichier ouvert, et redescend à la fermeture ;
- un unique connu présent dans un `.d2i` apparaît comme trouvé (c'est le cas que
  `D2ItemListAll` ne couvre pas, donc celui qui régressera en premier si quelqu'un remplace
  le scanner par `D2ItemListAll`) ;
- un joyau unique serti dans une armure est compté ;
- un runeword est compté sous son nom (« Insight »), et pas comme l'arme de base ;
- un item de set est compté et son set affiche `n / taille` correcte ;
- cases Normal/Exceptional/Elite : décocher « Elite » retire bien les Elite, et le total de la
  barre de progression suit ;
- recherche : « harl » trouve Harlequin Crest ; la recherche ne casse pas sur un nom
  contenant un code couleur `ÿc4` ;
- filtre `Missing` : la barre de progression continue d'afficher la vraie progression ;
- case `Include non-Chronicle items` : cochée, le dénominateur passe de 419 à 1470 sur l'onglet
  Uniques et de 135 à 455 sur l'onglet Sets, les entrées ajoutées portent un `*`, et rien de
  déjà trouvé ne disparaît ; décochée, on revient exactement à l'état précédent ; l'état est
  bien retrouvé après fermeture/réouverture de la fenêtre ; sur l'onglet Runewords elle est
  grisée ;
- un unique hors Chronicle réellement possédé (par ex. un « Crafted … ») apparaît comme
  `Found` une fois la case cochée ;
- **tous les libellés de la fenêtre sont en anglais** — aucun texte français n'a fui dans
  l'interface ;
- ouvrir un `.d2s` que GoMule ne charge que partiellement : le bandeau d'avertissement
  apparaît et nomme le fichier ;
- déplacer/redimensionner la fenêtre, changer d'onglet avec une catégorie sélectionnée : pas
  d'exception, pas de sélection périmée.

## 11. Tests automatisés

Nouveau dossier `src/test/java/gomule/grail/` :

- `D2GrailIndexTest` — construit l'index depuis `./d2111` (`D2TxtFile.constructTxtFiles`) et
  fige les comptes dans les deux portées (Chronicle : 419 / 135 / 208 ; tout : 1470 / 455 /
  208), l'unicité des clés, le fait qu'une clé d'unique corresponde à ce que `searchByID`
  renvoie réellement et non au texte de la colonne `*ID`, la présence de quelques entrées nommées (Harlequin Crest en Elite,
  Angelic Sickle dans « Angelic Raiment », Insight), au moins une entrée connue
  `chronicle = false` (« Crafted Cold Rupture »), et le fait qu'aucune catégorie n'est
  orpheline ;
- `D2GrailModelTest` — bascule de la case `Include non-Chronicle items` : le dénominateur des
  statistiques passe bien de 419 à 1470, les découvertes déjà enregistrées ne sont pas
  perdues, aucun rescan n'est déclenché, et le `n / taille` d'un set partiellement hors
  Chronicle suit la portée ;
- `D2GrailCategoriesTest` — toutes les valeurs `UICategory` présentes dans `itemtypes.txt` ont
  un libellé et une racine. Ce test échouera à la prochaine mise à jour du mod si une nouvelle
  catégorie apparaît : c'est exactement ce qu'on veut, plutôt qu'une catégorie muette dans
  l'arbre ;
- `D2GrailScannerTest` — sur les fixtures `charFiles/`, assertions sur des clés précises,
  compte d'exemplaires, et détection d'un item serti ;
- `D2ItemTest` (ou l'existant) — `getUniqueID()` / `getRuneWordIndex()` renvoient les bonnes
  valeurs sur une fixture, et `getRuneWordIndex()` est `null` pour un item non-runeword.

Tout est headless (aucun composant Swing instancié). Comme pour `D2ItemImagePanelTest`, le
répertoire de travail des tests doit rester la racine du projet à cause des chemins relatifs
`./d2111` et `resources/gfx`.

## 12. Points de vigilance

- **Ne pas toucher au parser.** Le seul changement dans `D2Item` est un champ + deux getters,
  sans lecture de bits. Toute autre tentation (« tant qu'on y est, stockons aussi… ») doit
  passer par le workflow de `CLAUDE.md`, avec fixture et vérification en chaîne.
- **`*ID` plutôt qu'index de ligne**, pour les uniques comme pour les sets (§5.1).
- **Les `.d2i` ne passent pas par `D2ItemListAll`** (§6.1).
- **Fichiers chargés partiellement** : ne jamais présenter un décompte comme exhaustif quand
  un fichier a échoué (§6.2).
- **Ligne `Expansion`** présente dans les données de `uniqueitems`/`setitems` : à sauter
  explicitement lors de la construction de l'index.
- **Codes couleur `ÿc4`** dans les noms d'items : les retirer avant comparaison et avant
  affichage (`D2ItemRenderer.stripColorCodes`).
- **Items « Crafted … »** : `uniqueitems.txt` contient des doublons préfixés « Crafted » — ils
  portent tous `disableChronicle = 1`. Ils sont donc absents par défaut, mais **réapparaissent
  case cochée**, sous des noms très proches des originaux. C'est justement pourquoi les
  entrées hors Chronicle sont marquées d'un `*` : sans ce marqueur, la liste étendue est
  déroutante.
- **Ne pas confondre les deux exclusions** : `disabled` (7 lignes) sort définitivement de
  l'index, `disableChronicle` (1058 lignes) n'est qu'un filtre d'affichage.

## 13. Hors périmètre

- « Dropped By » et le vrai « First Found » du jeu : information absente des saves.
- « View Rewards » : données internes au mod.
- Suivi d'items non collectionnables (bases, runes seules, gemmes) : ce sont des *counters*,
  pas un grail. Extension naturelle mais distincte (un onglet « Runes » comptant r01..r33
  serait facile à ajouter ensuite sur la même infrastructure).
- Lecture de fichiers **non ouverts** (scan d'un dossier de saves complet) : la demande porte
  explicitement sur les fichiers ouverts. `D2Project.getCharList()/getStashList()/
  getSharedStashList()` permettraient de l'ajouter plus tard sans changer le modèle.
- Fusion multi-profils / grail partagé entre projets.

## 14. Récapitulatif des fichiers

| Fichier | Nature |
|---|---|
| `src/main/java/gomule/grail/D2GrailKey.java` | Nouveau |
| `src/main/java/gomule/grail/D2GrailEntry.java` | Nouveau |
| `src/main/java/gomule/grail/D2GrailIndex.java` | Nouveau |
| `src/main/java/gomule/grail/D2GrailCategories.java` | Nouveau |
| `src/main/java/gomule/grail/D2GrailScanner.java` | Nouveau |
| `src/main/java/gomule/grail/D2GrailFinding.java` | Nouveau |
| `src/main/java/gomule/grail/D2GrailModel.java` | Nouveau |
| `src/main/java/gomule/gui/D2ViewGrail.java` | Nouveau |
| `src/main/java/gomule/gui/D2GrailListRenderer.java` | Nouveau |
| `src/main/java/gomule/item/D2Item.java` | `getUniqueID()`, champ + `getRuneWordIndex()` |
| `src/main/java/gomule/gui/D2FileManager.java` | `getOpenItemLists()`, bouton + menu, enregistrement de la vue |
| `src/test/java/gomule/grail/*Test.java` | Nouveaux |