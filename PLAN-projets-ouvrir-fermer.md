# Plan de développement — Ouvrir / fermer / créer un projet, et projet par défaut dans le dossier user data

## 1. Constat

Aujourd'hui, un « projet » GoMule n'est pas un fichier qu'on ouvre : c'est un **sous-dossier de
`./projects`**, relatif au répertoire de travail du processus.

| Élément | Emplacement actuel | Code |
|---|---|---|
| Racine des projets | `./projects` (constante `PROJECTS_DIR = "projects"`) | `D2Project.java:45` |
| Dossier d'un projet | `projects/<nom>` (créé au besoin par le constructeur) | `D2Project.java:84-90` |
| Réglages du projet | `projects/<nom>/project.properties` | `D2Project.java:92`, `saveProject()` |
| Clipboard (un vrai `.d2x`) | `projects/<nom>/Clipboard.d2x` | `D2ViewClipboard.java:202` |
| Holy Grail « first seen » | `projects/<nom>-grail.properties` (à plat, **hors** du dossier projet) | `D2GrailFirstSeenStore.fileFor()` |
| Réglages globaux (géométrie fenêtre, L&F, projet courant) | `projects/projects.properties` | `FileManagerProperties.java:14-27` |
| Backups des saves | `GoMule.backup/<période>/` **à côté du `.d2s`** édité | `D2Backup.java:68` |
| Dumps txt de projet | `./<nom>Dumps` (répertoire de travail) | `D2FileManager.java:402` |

Le choix du projet se fait par la **combo box** du panneau gauche, alimentée en listant les
sous-dossiers de `projects/` (`checkProjectsModel()`, `D2FileManager.java:230-243`), plus trois
boutons « New Proj » / « Del Proj » / « Clear Proj » (`createLeftPane()`, lignes 274-358). Le menu
`File` ne contient **rien** sur les projets : `Open Character`, `New Stash`, `Open Stash`,
`Save All`, `Holy Grail`, `Switch Appearance`, `Exit` (`createMenubar()`, lignes 786-903).

Trois conséquences gênantes :

1. **Un projet n'est pas ouvrable là où il vit.** Il doit être un sous-dossier de `projects/`, sous
   le dossier d'installation. Impossible de mettre un projet sur OneDrive, sur un disque partagé,
   ou à côté de ses saves.
2. **Les données utilisateur vivent dans le dossier d'installation.** D'où l'avertissement du
   `README.md:31` (« Avoid `C:\Program Files` ») et le `cd /d "%~dp0"` de `GoMule.bat`. Extraire une
   nouvelle release par-dessus l'ancienne, ou dans un dossier neuf, **perd** les projets et le
   clipboard.
3. **Changer de projet sauvegarde en silence.** `closeWindows()` (`D2FileManager.java:1251`) appelle
   `saveAll()` sans rien demander : l'utilisateur n'a jamais le choix d'abandonner ses
   modifications, et n'est même pas prévenu qu'on écrit dans ses `.d2s`.

## 2. Objectif

- `File / New Project…` : créer un projet **n'importe où**, fermer le projet courant, ouvrir le neuf.
- `File / Open Project…` : ouvrir un projet existant depuis son emplacement sur le disque.
- `File / Close Project` : fermer le projet courant et **laisser l'application sans projet ouvert**.
- Toute fermeture de projet (Close, New, Open, changement via la combo, sortie de l'application)
  **demande** à l'utilisateur s'il veut sauvegarder ses fichiers modifiés — s'il en a.
- Le projet par défaut vit dans le dossier **user data de Windows**, plus dans le dossier
  d'installation. Les projets existants y sont migrés automatiquement.

### Décisions arrêtées avec l'utilisateur (2026-09-23)

| Question | Décision |
|---|---|
| Sémantique de `Close` | **Vrai état « aucun projet ouvert »** : arbre vide, clipboard détaché, actions liées au projet désactivées jusqu'à un `Open`/`New`. |
| Forme d'un projet sur le disque | **Un dossier contenant `project.properties`.** Pas de nouveau format de fichier. |
| Projets existants sous `./projects` | **Migrés par copie** vers le dossier user data au premier lancement ; l'original est laissé intact en secours. |

## 3. Où mettre les données utilisateur

Windows propose trois emplacements ; le bon ici est **`%APPDATA%` (Roaming)** :

| Emplacement | Variable | Destiné à | Verdict |
|---|---|---|---|
| `C:\Users\<u>\AppData\Roaming\<App>` | `%APPDATA%` | Réglages et **documents applicatifs** de l'utilisateur, petits, qui suivent le profil et sont inclus dans les sauvegardes | **Retenu** |
| `C:\Users\<u>\AppData\Local\<App>` | `%LOCALAPPDATA%` | Caches, index, données re-générables, spécifiques à la machine | Non : le `Clipboard.d2x` contient de **vrais objets** du jeu, irremplaçables |
| `C:\Users\<u>\Documents\<App>` | — | Fichiers que l'utilisateur ouvre lui-même | Non pour le projet *par défaut* (on ne pollue pas Documents sans le demander) ; reste le répertoire proposé par défaut dans le sélecteur de `New Project…` |

Arborescence retenue :

```
%APPDATA%\GoMule-Reimagined\
├── projects.properties          ← réglages globaux (ex-projects/projects.properties)
└── projects\
    └── GoMule\                  ← projet par défaut
        ├── project.properties
        ├── Clipboard.d2x
        └── grail.properties     ← ex projects/GoMule-grail.properties (voir §5.3)
```

Nouvelle classe `gomule/util/D2UserData.java`, sans dépendance Swing (donc testable en headless) :

```java
public final class D2UserData {
    public static final String OVERRIDE_PROPERTY = "gomule.userdata.dir";
    public static File getUserDataDir();      // racine, créée au besoin
    public static File getProjectsDir();      // <racine>/projects
    public static File getDefaultProjectDir();// <racine>/projects/GoMule
}
```

Résolution, dans l'ordre :

1. La propriété système `gomule.userdata.dir`, si elle est définie — **c'est le point d'injection
   des tests** (et la porte de sortie pour un utilisateur qui veut une installation portable, à
   documenter dans le README).
2. Windows (`os.name` commence par `Windows`) : `%APPDATA%\GoMule-Reimagined`, avec repli sur
   `System.getProperty("user.home") + "\\AppData\\Roaming\\GoMule-Reimagined"` si la variable
   d'environnement est absente (elle l'est dans certains environnements de service/CI).
3. macOS : `~/Library/Application Support/GoMule-Reimagined` — le projet garde des utilisateurs
   macOS (cf. les chemins de saves dans `CLAUDE.md`), donc pas de code Windows-only.
4. Autre (Linux) : `$XDG_DATA_HOME/GoMule-Reimagined`, sinon `~/.local/share/GoMule-Reimagined`.

Si la racine n'est pas créable ou pas inscriptible, on **retombe sur `./projects`** avec un message
d'avertissement (une seule fois, au démarrage) : mieux vaut un GoMule qui démarre comme avant qu'un
GoMule qui refuse de s'ouvrir.

## 4. Nouveau modèle de projet

Un projet = **un dossier contenant `project.properties`**. Son *nom* est le nom du dossier ; il n'y
a plus de contrainte sur l'emplacement du dossier.

`D2Project` est actuellement construit depuis un **nom** (`new D2Project(this, "GoMule")`) et en
dérive le chemin (`PROJECTS_DIR + File.separator + nom`). Refonte :

```java
public D2Project(D2FileManager pFileManager, File pProjectDir)   // nouveau constructeur canonique
public static boolean isProjectDir(File pDir)                    // pDir/project.properties existe
public File getProjectDirFile()
public String getProjectName()                                   // = pProjectDir.getName(), inchangé côté appelants
public String getProjectDir()                                    // conservé (String), = chemin absolu
```

Le constructeur `(D2FileManager, String)` est **supprimé** plutôt que conservé : il ne reste que 4
sites d'appel (`D2FileManager.java:912`, `:1141`, et les boutons du panneau gauche), et le garder
laisserait deux façons contradictoires de localiser un projet.

Effets de bord à traiter :

- `getProjectDir()` renvoyait un chemin **relatif** (`projects/GoMule`) ; il renverra un chemin
  absolu. Seul consommateur : `D2ViewClipboard.java:202`, qui concatène `/Clipboard.d2x` — OK.
- `PROJECTS_DIR` reste comme constante *legacy* (utilisée par la migration et par le repli du §3),
  avec un commentaire disant explicitement qu'elle ne désigne plus l'emplacement courant.

### Registre des projets connus

La combo box ne peut plus se contenter de lister un dossier. `projects.properties` gagne :

```properties
current-project-dir=C:\\Users\\u\\AppData\\Roaming\\GoMule-Reimagined\\projects\\GoMule
project.recent.0=C:\\Users\\u\\AppData\\Roaming\\GoMule-Reimagined\\projects\\GoMule
project.recent.1=D:\\D2R\\MonProjetLadder
```

- `current-project-dir` remplace `current-project` (un simple nom). **Compatibilité** : si seul
  `current-project` existe (ancienne installation), on le résout sous `D2UserData.getProjectsDir()`
  après migration, puis on écrit la nouvelle clé. La clé `current-project` peut être vide (aucun
  projet ouvert à la fermeture), et dans ce cas GoMule démarre… sur le projet par défaut : au
  **premier** démarrage on ouvre le projet par défaut, mais un `Close Project` explicite suivi d'une
  sortie doit rouvrir vide au démarrage suivant (`current-project-dir=` vide) — c'est le
  comportement attendu d'un « Close ».
- `project.recent.N` : liste ordonnée (plus récent d'abord), bornée à 10, purgée des dossiers
  disparus au chargement. Alimente la combo box, qui affiche le **nom** du projet et met le chemin
  complet en tooltip ; deux projets homonymes sont donc distinguables.

## 5. Découpage en étapes

### Étape 1 — Chemins user data + migration (aucun changement d'IHM)

**Fichiers :** nouveau `src/main/java/gomule/util/D2UserData.java`, nouveau
`src/main/java/gomule/util/D2UserDataMigration.java`, `FileManagerProperties.java`.

1. `D2UserData` comme au §3.
2. `D2UserDataMigration.migrateIfNeeded()`, appelée **une seule fois, au tout début de
   `GoMule.main()`**, avant `FileManagerProperties.loadFileManagerProperties()` (qui crée déjà le
   fichier s'il manque — sinon la migration verrait une cible « déjà existante » et ne ferait rien) :
   - ne rien faire si `<userdata>/projects` existe déjà (migration déjà faite, ou installation
     neuve déjà utilisée) ;
   - ne rien faire si `./projects` n'existe pas (installation neuve) ;
   - sinon **copier** récursivement `./projects` → `<userdata>/projects` et
     `./projects/projects.properties` → `<userdata>/projects.properties` (noter le changement de
     niveau : les réglages globaux remontent d'un cran, hors du dossier `projects/`, pour qu'ils ne
     soient plus confondus avec un projet). L'original est **laissé en place**.
   - Journaliser sur `System.out` ce qui a été copié, et afficher un dialogue d'information unique
     (« Vos projets ont été copiés vers … ») — l'utilisateur doit savoir où sont passées ses
     données, sans quoi un clipboard « vide » ressemblera à une perte de données.
3. `FileManagerProperties.getFileManagerPropertiesFile()` lit/écrit
   `<userdata>/projects.properties` au lieu de `projects/projects.properties`. Le nom du fichier est
   **conservé** : renommer en même temps qu'on déplace rendrait la migration et le diagnostic
   inutilement obscurs.

À la fin de l'étape 1, GoMule fonctionne comme avant mais depuis `%APPDATA%`.

### Étape 2 — `D2Project` adressé par dossier

**Fichiers :** `D2Project.java`, `D2FileManager.java` (`setProject`, `checkProjects`,
`checkProjectsModel`), `D2GrailFirstSeenStore.java`, `D2ViewGrail.java:150`.

1. Nouveau constructeur `(D2FileManager, File)` + `isProjectDir()` + `getProjectDirFile()` (§4).
2. `checkProjects()` : lit `current-project-dir`, vérifie que le dossier est un projet valide, et
   retombe sur `D2UserData.getDefaultProjectDir()` (créé au besoin) le cas échéant.
3. `checkProjectsModel()` : alimenté par le registre des projets récents (§4) au lieu du listage de
   `projects/`.
4. **`D2GrailFirstSeenStore` : le fichier « first seen » rentre dans le dossier du projet.**
   `fileFor(String projectName)` → `fileFor(D2Project)` renvoyant
   `<projectDir>/grail.properties`. Sans ça, un projet posé sur OneDrive laisserait son historique
   grail derrière lui dans `%APPDATA%`, et deux projets homonymes dans deux dossiers différents
   partageraient le même fichier. La migration de l'étape 1 copie `projects/<nom>-grail.properties`
   tel quel ; il faut donc aussi, dans `load()`, **récupérer l'ancien fichier** s'il existe et que
   le nouveau n'existe pas (renommage ponctuel, idempotent) pour ne pas perdre les dates déjà
   enregistrées. `D2GrailFirstSeenStoreTest` est à mettre à jour en conséquence.

### Étape 3 — Le dialogue « sauvegarder les fichiers modifiés ? »

**Fichiers :** `D2FileManager.java`.

Point de départ important : **rien ne sauvegarde à la fermeture d'une fenêtre.** `closeView()`
(`D2ViewChar.java:647`, `D2ViewStash.java:934`, `D2ViewSharedStash.java:60`) se contente de
`disconnect()` + `removeFromOpenWindows()`. Seuls `saveAll()` / `saveAllItemLists()` écrivent. Le
« Non, ne pas sauvegarder » est donc réellement implémentable : il suffit de ne pas appeler
`saveAllItemLists()`.

1. Nouvelle méthode `List<String> getModifiedFileNames()` : parcourt `iItemLists` (`isModified()`)
   + le clipboard (`iClipboard.isModified()`, libellé « Clipboard »). C'est la liste affichée.
2. Nouvelle méthode `boolean confirmCloseProject()` :
   - aucun fichier modifié → `true` sans rien demander ;
   - sinon `JOptionPane.showConfirmDialog(..., YES_NO_CANCEL_OPTION)` listant les fichiers modifiés
     (tronquée à ~15 lignes + « … et N autres ») ;
   - `Yes` → `saveAllItemLists()` puis `true` ; `No` → `true` sans sauvegarder ; `Cancel`/fermeture
     du dialogue → `false`, et **l'action appelante est annulée**.
   - `iProject.saveProject()` (les réglages du projet : liste de fichiers, banque, préférences
     Flavie) est en revanche **toujours** écrit, y compris sur « No » : ce n'est pas un fichier de
     jeu modifié par l'utilisateur, et le perdre dégraderait silencieusement l'état de l'appli.
3. `closeWindows()` perd son `saveAll()` initial et devient un pur « ferme les fenêtres » ; tous les
   appelants passent désormais par `confirmCloseProject()` :
   - `File / Close Project`, `File / New Project…`, `File / Open Project…` ;
   - le listener de la combo box (`createLeftPane()`, ligne 258) — et si l'utilisateur annule, il
     faut **remettre la sélection sur le projet courant** sans redéclencher l'événement (poser un
     drapeau `iIgnoreProjectSelection`, sinon on part en récursion) ;
   - `closeListener()` (croix de la fenêtre / `File / Exit`) : un `Cancel` doit **empêcher la
     sortie**, donc `closeListener()` sort tôt sans `System.exit(0)` ;
   - les boutons « Del Proj » / « Clear Proj » du panneau gauche.
   - `closeFileName()` (`:1173`) garde son `saveAll()` : il est appelé depuis la suppression d'un
     fichier de l'arbre, hors du cycle de vie du projet. À laisser tel quel pour ne pas élargir le
     périmètre.

### Étape 4 — Les trois entrées de menu

**Fichiers :** `D2FileManager.java` (`createMenubar()`).

Le menu `File` devient :

```
New Project…        Ctrl+Shift+N
Open Project…       Ctrl+O
Close Project       Ctrl+W
────────────────
Open Character
────────────────
New Stash / Open Stash
… (inchangé)
```

Libellé retenu : **`Close Project`** plutôt que `Close` tout court, parce que le menu contient déjà
des actions d'ouverture de personnage/stash et qu'un `Close` isolé se lirait comme « fermer la
fenêtre active ».

- Ces trois entrées utilisent `addActionListener` (et non le `addMouseListener` du code existant,
  qui casse l'activation au clavier et les accélérateurs) — divergence de style assumée et
  commentée sur place.
- **`New Project…`** : `JFileChooser` en `DIRECTORIES_ONLY` (répertoire proposé :
  `D2UserData.getProjectsDir()`), avec un champ « nom du fichier » servant de nom de projet ; ou, à
  l'implémentation, un petit dialogue composite « Nom : [____] Emplacement : [____] [Parcourir] ».
  Retenu : le dialogue composite, parce qu'un `JFileChooser` détourné pour « choisir un dossier
  parent + taper un nom » est notoirement confus. Validations : nom non vide, motif déjà utilisé
  par `checkNewFilename()` (`D2FileManager.java:304`, caractères `/?*:;{}\` interdits), dossier
  cible inexistant ou vide, dossier parent inscriptible. Puis fermeture du projet courant
  (étape 3), création du dossier, `new D2Project(this, dir)`, ajout au registre.
- **`Open Project…`** : `JFileChooser` en `FILES_AND_DIRECTORIES` avec un filtre acceptant
  `project.properties` — si l'utilisateur sélectionne le fichier, on prend son dossier parent ; s'il
  sélectionne un dossier, on vérifie `isProjectDir()`. Message d'erreur explicite sinon (« Ce
  dossier ne contient pas de project.properties »), avec proposition d'en faire un nouveau projet ?
  → **non**, on garde `New Project…` comme unique chemin de création.
- **`Close Project`** : `confirmCloseProject()` → `closeWindows()` → `setProject((D2Project) null)`.

### Étape 5 — L'état « aucun projet ouvert »

C'est l'étape la plus invasive : `iProject` est aujourd'hui supposé non nul partout (146
occurrences de `iProject`/`getProject()` sur 11 fichiers).

**Invariant qui rend la chose tenable, à écrire en commentaire sur `setProject(null)` :** fermer un
projet ferme **toutes** les fenêtres d'objets. Donc « aucun projet » implique « aucune fenêtre
d'objets ouverte », et tout le code atteignable seulement depuis une fenêtre ouverte
(`D2ViewChar`, `D2ViewStash`, `D2ViewSharedStash`, `SharedStashGoldTransferPanel`, `ItemRightClickMenu`,
`D2ItemListAll`, les boutons « Item Control » / « Output Control ») ne peut pas observer un projet
nul. Ces fichiers ne sont **pas** à modifier.

Travail effectif :

1. `setProject(D2Project)` accepte `null` : propage à `iViewProject.setProject(null)` (déjà
   null-safe, `D2ViewProject.java:314-333`) et à un nouveau `iClipboard.clearProject()`
   (`D2ViewClipboard`) qui détache le `D2Stash` courant, vide la table, remet la banque à vide et
   désactive les boutons du panneau.
2. Nouveau `updateProjectDependentUI()` : active/désactive selon `iProject != null`
   - menu : `Close Project`, `Open Character`, `New Stash`, `Open Stash`, `Save All`, `Holy Grail`,
     et le menu `Project` entier (`Preferences`, qui ouvrirait un `D2ProjectSettingsDialog` sur un
     projet nul — 38 usages de `iProject` dedans) ;
   - barre d'outils : tous les boutons sauf `Switch Appearance`/about ;
   - panneau gauche : combo + « Del Proj » / « Clear Proj » / « Proj Flavie Report » /
     « Proj Txt Dump ».
3. Garde-fous pour les points d'entrée programmatiques qui ne passent pas par un bouton :
   `openChar(String, boolean)`, `openStash(String, boolean)`, `openSharedStash(String, boolean)`,
   `addItemList()` (`getProject().getType()`, ligne 1735), `openGrailWindow()`,
   `saveAllItemLists()` (`lList.save(iProject)`), `closeListener()`
   (`iProject.getProjectName()`, ligne 1157 → écrire une valeur vide).
4. Titre de la fenêtre : `setTitle()` (ligne 172) affiche désormais le nom du projet, et
   « (aucun projet) » quand il n'y en a pas — c'est le retour visuel principal de l'état vide.
5. Le panneau central (`iDesktopPane`) vide affiche déjà « rien » ; ajouter un simple libellé
   d'invite (« Aucun projet ouvert — File / Open Project… ») est optionnel et sera fait si le rendu
   est trop nu.

### Étape 6 — Tests, documentation, vérification manuelle

**Tests** (`src/test/java/`, JUnit 5, headless — cf. `build.gradle:39-49`) :

| Test | Objet |
|---|---|
| `D2UserDataTest` | résolution du chemin : override par propriété système, branche Windows avec/sans `%APPDATA%`, macOS, Linux avec/sans `XDG_DATA_HOME` (injecter `os.name`/l'environnement via des paramètres internes plutôt que de lire `System.getenv` en dur — la méthode de résolution prendra donc une surcharge package-private testable) |
| `D2UserDataMigrationTest` | copie complète d'un faux `projects/` (`@TempDir`) ; remontée de `projects.properties` d'un niveau ; no-op si la cible existe ; no-op si la source n'existe pas ; idempotence sur deux appels |
| `D2ProjectTest` | round-trip `saveProject()`/relecture depuis un dossier arbitraire ; `isProjectDir()` vrai/faux ; `getProjectName()` = nom du dossier |
| `D2ProjectRegistryTest` | ajout/ordre/borne à 10/purge des dossiers disparus de `project.recent.N` |
| `D2GrailFirstSeenStoreTest` (existant) | mise à jour vers le nouvel emplacement + test de récupération de l'ancien `<nom>-grail.properties` |

Les dialogues Swing (`confirmCloseProject`, les sélecteurs) ne sont pas testables en headless et ne
seront **pas** couverts par des tests ; la logique testable (`getModifiedFileNames()` reste dans
`D2FileManager`, non instanciable en test) est délibérément réduite à un parcours de liste.

**Documentation :**
- `README.md:29-31` : supprimer l'avertissement « Avoid `C:\Program Files` » relatif aux projets
  (il reste vrai pour… rien : `d2111/` et `resources/` sont lus seulement), et documenter le nouvel
  emplacement `%APPDATA%\GoMule-Reimagined` + la propriété `gomule.userdata.dir` pour une
  installation portable. Ajouter un mot sur `File / New|Open|Close Project`.
- `GoMule.bat` : le commentaire mentionne « create its projects\ folder » — à corriger (le `cd /d`
  reste nécessaire pour `d2111\` et `resources\`).
- `CHANGELOG.md` : entrée utilisateur (nouvelles entrées de menu, projets où l'on veut, projets
  migrés automatiquement vers AppData, invite de sauvegarde à la fermeture).

**Vérification manuelle** (`gradlew run`, cf. `build.gradle:64-70` — attention, `workingDir = rootDir`
fait que le `./projects` du dépôt sert de source de migration ; c'est précisément le scénario à
tester) :

1. Premier lancement : les projets existants sont copiés dans `%APPDATA%\GoMule-Reimagined`, le
   projet `GoMule` s'ouvre avec son clipboard et son arbre intacts.
2. `New Project…` vers `%USERPROFILE%\Documents\TestProj` → projet vide, arbre vide, clipboard vide.
3. Ouvrir un personnage, déplacer un objet (titre marqué `*`), `Close Project` → le dialogue liste
   le fichier ; `Cancel` laisse tout en place ; `No` ferme sans écrire (vérifier l'horodatage du
   `.d2s`) ; `Yes` écrit.
4. État fermé : arbre vide, clipboard vide, menu/toolbar désactivés, titre « (aucun projet) ».
5. `Open Project…` sur le dossier du projet migré → tout revient.
6. Quitter en état fermé, relancer : GoMule rouvre sans projet.
7. `gradlew clean build` vert.

## 6. Risques et points de vigilance

- **Perte de données perçue.** Le déplacement vers `%APPDATA%` déroute si rien ne le dit : d'où le
  dialogue d'information unique de l'étape 1 et la copie (non le déplacement) de l'original.
- **`Cancel` qui ne annule pas.** Chaque appelant de `confirmCloseProject()` doit propager le
  `false` (fenêtre, combo, menu, boutons). Le cas de la combo box est le plus piégeux : il faut
  restaurer la sélection sans relancer l'`ItemListener`.
- **`D2ViewClipboard` est un singleton statique** (`getInstance`, champs `static iMouseItem`) dont
  `saveView()` fait `iStash.save(iFileManager.getProject())` (ligne 244) : à garder null-safe, c'est
  le seul chemin d'écriture du clipboard.
- **Deux projets ouverts simultanément : hors périmètre.** L'application reste mono-projet ; `Open`
  ferme le courant.
- **`<nom>Dumps` et les rapports Flavie** continuent d'écrire dans le répertoire de travail. C'est
  un défaut préexistant (et un vrai problème sous `Program Files`) mais **hors périmètre** de ce
  plan ; à traiter séparément si souhaité — l'emplacement naturel serait le dossier du projet.
- **Sous-arborescence `.gitignore`.** `projects/` est versionné dans le dépôt (il contient le projet
  de travail de l'utilisateur). Après migration, c'est la copie sous `%APPDATA%` qui est la source
  de vérité ; ne pas supprimer `projects/` du dépôt dans le cadre de ce plan (le laisser comme
  secours), mais en prendre note.
