# Plan de développement — Afficher l'apparence de l'item sous ses propriétés

## 1. Constat et clarification du périmètre

GoMule ouvre trois types de fenêtres, chacune avec sa propre vue :

| Fichier | Classe de vue | Rendu des items |
|---|---|---|
| `.d2s` (personnage) | `gomule/gui/D2ViewChar.java` | Grille peinte, sprite DC6 par item (`D2ViewChar.java:1333`), propriétés en infobulle (`D2ViewChar.java:1284`) |
| `.d2i` (shared stash) | `gomule/gui/sharedStash/D2ViewSharedStash.java` + `SharedStashPanel.java` | Grille peinte, sprite DC6 par item (`SharedStashPanel.java:94`), propriétés en infobulle (`SharedStashMouseMotionListener.java:42`) |
| `.d2x` (stash GoMule) | `gomule/gui/D2ViewStash.java` | **Tableau texte uniquement.** La sélection remplit un `JEditorPane` HTML avec les propriétés (`D2ViewStash.java:312-331`). Aucune image. |

La demande parle de « .d2i » deux fois, mais le `.d2i` dessine déjà chaque item avec son sprite réel. La seule vue où « seules les propriétés sont affichées à la sélection » est celle du **`.d2x`**, c'est-à-dire `D2ViewStash`. C'est donc cette vue que ce plan modifie. Si l'intention était réellement le `.d2i`, voir la section 8.

Bonne nouvelle : le comportement demandé existe déjà ailleurs dans le code. Le presse-papiers (`D2ViewClipboard.java:101-121`) affiche un `JLabel` avec l'icône DC6 sous son tableau, mis à jour par un `ListSelectionListener`. C'est le patron à reprendre, en le rendant plus robuste.

## 2. Objectif

Dans la fenêtre d'un stash `.d2x`, lorsqu'un item est sélectionné dans le tableau, afficher son sprite d'inventaire (l'apparence réelle du jeu) sous le texte de ses propriétés, dans le même panneau de droite.

## 3. Portée exacte du changement

`D2ViewStash` sert deux fenêtres différentes :

- une fenêtre par fichier `.d2x` ouvert (`D2FileManager.java:1617`) ;
- la vue agrégée « All » qui liste les items de tous les fichiers du projet (`isStash()` renvoie `false`, `D2FileManager.java:1501`).

Les deux héritent automatiquement de la nouvelle fonctionnalité. C'est souhaitable et cohérent : dans la vue « All », l'en-tête « Item From: <fichier> » reste au-dessus, les propriétés au milieu, l'image en bas.

## 4. Conception

### 4.1 Nouveau composant `D2ItemImagePanel`

Créer `src/main/java/gomule/gui/D2ItemImagePanel.java`, un `JPanel` réutilisable :

- champ `private D2Item item;` et méthode `setItem(D2Item item)` qui déclenche `repaint()`.
- `setItem(null)` vide l'aperçu.
- `paintComponent` : fond noir, sprite dessiné centré horizontalement et verticalement.
- l'image vient de `D2ImageCache.getDC6Image(item)`, qui gère déjà le cache et les cas particuliers (porte-clés, Nightshade unique).
- taille préférée fixe d'environ 120 x 140 px. Le sprite le plus grand du jeu fait 2 x 4 cellules, soit environ 56 x 112 px avec `GRID_SIZE = 28` (`D2ViewChar.java:77`), donc le rendu tient à l'échelle 1:1 sans mise à l'échelle.
- si l'image dépasse la zone disponible (fenêtre redimensionnée petit), la réduire proportionnellement avec `Graphics2D.drawImage` et un hint d'interpolation. Ne jamais agrandir au delà de 1:1 par défaut.

Ne pas utiliser `JLabel` + `ImageIcon` comme le presse-papiers : un `JLabel` rogne l'image quand le composant rétrécit et ne permet pas de contrôler le fond ni le centrage aussi proprement.

### 4.2 Robustesse du chargement d'image

`D2ImageCache.getDC6Image(D2Item)` (`D2ImageCache.java:92-109`) présente deux risques réels pour un stash `.d2x` contenant des items moddés Reimagined :

1. `pItem.getItemName()` est déréférencé sans contrôle de nullité à la ligne 104. Un item dont le nom n'a pas été traduit peut faire lever une `NullPointerException`.
2. `pItem.get_image()` peut être `null` ou pointer un `.dc6` absent de `resources/gfx`. `D2dc6` retombe sur `invsple.dc6` seulement si le fichier existe mais est vide (`D2dc6.java:40`) ; un nom `null` produit `"null.dc6"` et un chemin inexistant.

Décision : ne pas modifier `D2ImageCache` (utilisé par les vues `.d2s` et `.d2i` déjà en production). Encapsuler l'appel dans `D2ItemImagePanel` :

```java
private static Image imageFor(D2Item item) {
    if (item == null) return null;
    try {
        return D2ImageCache.getDC6Image(item);
    } catch (RuntimeException | Error e) {
        return null;   // un sprite manquant ne doit jamais casser la vue stash
    }
}
```

Quand l'image est `null`, peindre le fond noir et un court texte gris centré, par exemple « (aucune image) ». Cette dégradation silencieuse est cohérente avec le reste du fork, où un item non traduit ne doit pas faire échouer tout le chargement.

### 4.3 Intégration dans `D2ViewStash`

Modifications dans `D2ViewStash.java` :

1. Nouveau champ `private D2ItemImagePanel iItemImage;` à côté de `iItemText` (ligne 68).
2. Dans le constructeur, au bloc de construction de `lItemPanel` (lignes 258-272), qui est déjà en `BorderLayout` avec le `JScrollPane` en `CENTER` : instancier `iItemImage` et l'ajouter en `BorderLayout.SOUTH`. Le texte garde tout l'espace extensible, l'image occupe une bande de hauteur fixe en bas.
3. Dans le `ListSelectionListener` (lignes 312-331) :
   - branche « exactement une ligne sélectionnée » : après `iItemText.setCaretPosition(0)`, appeler `iItemImage.setItem(iItemModel.getItem(iTable.getSelectedRow()))`.
   - branche `else` (zéro ou plusieurs lignes) : appeler `iItemImage.setItem(null)` en même temps que `iItemText.setText("")`.
4. `iTable.getSelectedRow()` est déjà appelé trois fois dans cette branche ; l'extraire dans une variable locale au passage évite un quatrième appel et clarifie le code.

### 4.4 Point de vigilance : sélection périmée après un filtre

`refreshData()` reconstruit la liste `iItems` à chaque changement de filtre, et `pickupSelected()` / `iDelete` retirent des items. Après un `fireTableChanged`, la ligne sélectionnée peut disparaître ou désigner un autre item. Le listener de sélection se déclenche dans la plupart de ces cas, mais pas systématiquement quand le modèle change sans que l'index de sélection bouge.

Mitigation : appeler une méthode privée `refreshItemDetails()` (qui met à jour texte **et** image en une seule fois) à la fois depuis le `ListSelectionListener` et à la fin de `itemListChanged()`. Cela garantit qu'un aperçu ne survit jamais à l'item qu'il représente. Cette méthode centralise la logique aujourd'hui dupliquée dans le listener anonyme.

## 5. Étapes d'implémentation

1. Créer `D2ItemImagePanel` avec `setItem`, `paintComponent`, le repli image absente, et le helper statique `imageFor`.
2. Ajouter le champ, l'instanciation et le placement `SOUTH` dans `D2ViewStash`.
3. Extraire la logique du `ListSelectionListener` dans `refreshItemDetails()`, y ajouter la mise à jour de l'image.
4. Appeler `refreshItemDetails()` en fin de `itemListChanged()`.
5. Vérifier le rendu à la main (section 6).
6. Ajouter les tests (section 7).
7. `./gradlew clean build` doit être vert avant commit.

## 6. Vérification manuelle

Lancer le jar (`build/libs/GoMule-Reimagined-1.0-SNAPSHOT.jar`) et contrôler :

- ouverture d'un `.d2x` contenant des items variés ; sélection d'un item : le sprite apparaît sous les propriétés et correspond à ce que le jeu affiche ;
- items de tailles différentes (bague 1x1, arme 2x4) : tous centrés, aucun rognage ;
- charmes, joyaux, items uniques et de set : le sprite doit refléter les variantes `gfx_num` et les `invfile` spécifiques (`D2Item.java:800-813`, `928-940`) ;
- sélection multiple puis désélection : l'aperçu se vide ;
- application d'un filtre qui exclut l'item sélectionné : l'aperçu se vide ou suit la nouvelle sélection, jamais un item disparu ;
- « Pickup » et « Delete » : pas d'image fantôme ;
- redimensionnement de la fenêtre et déplacement du séparateur du `JSplitPane` : pas d'exception, image toujours visible ou proprement réduite ;
- vue « All » : en-tête « Item From », propriétés, puis image.

## 7. Tests automatisés

L'interface Swing n'est pas testée dans ce dépôt, mais la partie fragile (la résolution du sprite) l'est facilement et sans écran :

- nouveau test dans `src/test/java/gomule/gui/` qui charge un personnage existant de `src/test/resources/charFiles/` via `D2TxtFile.constructTxtFiles("./d2111")` puis `new D2Character(...)`, et vérifie que `D2ItemImagePanel.imageFor(item)` ne lève jamais d'exception pour aucun item du fichier.
- un cas ciblé sur un item moddé Reimagined connu pour être le plus à risque côté image, afin de figer le comportement de repli.
- les tests créent des `BufferedImage`, donc ils fonctionnent en mode headless. Vérifier que le répertoire de travail des tests est bien la racine du projet, car `D2ImageCache` construit des chemins relatifs `resources/gfx/...`.

## 8. Hors périmètre, extensions possibles

- **Si la demande visait bien le `.d2i`** : cette vue n'a pas de panneau latéral de propriétés, seulement des infobulles sur une grille où le sprite est déjà visible. Ajouter un panneau « propriétés + apparence » à droite de la grille serait un chantier distinct et bien plus gros, incluant le suivi de l'item sélectionné dans `SharedStashPanel` et l'élargissement de la fenêtre au delà de `BG_WIDTH = 552`.
- Réutiliser `D2ItemImagePanel` dans la fenêtre de rapport du clic droit (`ItemRightClickMenu.java:29-35`), qui souffre du même manque.
- Remplacer le `JLabel` + `ImageIcon` du presse-papiers (`D2ViewClipboard.java:84-121`) par le nouveau composant, pour supprimer le rognage des grands sprites. À faire dans un commit séparé afin de garder le premier changement minimal.
- Dessiner les gemmes et runes serties par dessus le sprite de base, comme le fait le jeu. Aucune des vues actuelles ne le fait ; ce serait une amélioration transverse à traiter à part.

## 9. Fichiers touchés

| Fichier | Nature |
|---|---|
| `src/main/java/gomule/gui/D2ItemImagePanel.java` | Nouveau |
| `src/main/java/gomule/gui/D2ViewStash.java` | Champ, construction du panneau, refactor du listener de sélection, appel dans `itemListChanged()` |
| `src/test/java/gomule/gui/D2ItemImagePanelTest.java` | Nouveau |

Aucune modification du code de parsing (`D2Item`, `D2Character`, `D2SharedStashReader`) n'est nécessaire : tout ce dont l'aperçu a besoin est déjà exposé par `D2Item.get_image()` et `D2ImageCache`.
