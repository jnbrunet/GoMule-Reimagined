# Changelog

<!--
  Add your release notes here as you go, newest first, and commit them to main like any other
  change. When a commit lands on a YYYY.MM release branch, .github/workflows/release.yml diffs this
  file against the last published release and uses the lines you added as the GitHub release notes
  -- nothing to reset, rename or clean up afterwards.

  The per-version history lives on the releases page; this file is only the staging area for the
  next one. Only *added* lines are picked up, so editing an existing entry to fix a typo will
  republish it in the next release; fix it before the release goes out if that is not what you want.

  Anything inside an HTML comment like this one is stripped, so notes-to-self are safe here.

  Images must be absolute URLs -- GitHub does not resolve repository-relative paths in release
  notes. They are served from docs/screenshots/ on main.
-->

### Projects

- **Projects now live under `%APPDATA%\GoMule-Reimagined`**, not next to the GoMule executable —
  extracting a new release, or moving it to a new folder, no longer loses your characters,
  stashes, Clipboard or Holy Grail progress. Existing projects are copied there automatically the
  first time you run this version; the original `projects\` folder is left untouched as a backup.
- **`File / New Project…` and `File / Open Project…`** let you keep a project anywhere on disk —
  a OneDrive folder, another drive, right next to your saves — instead of only under GoMule's own
  `projects\` folder. `File / Close Project` leaves GoMule with no project open at all: the file
  tree and the Clipboard empty out, and everything that only makes sense with a project — the
  project controls, the character and stash actions, the Holy Grail — disappears or greys out
  until you open or create one.
- **Closing, switching or creating a project now asks first** if you have unsaved changes, listing
  which files they're in, with the option to save them, discard them, or cancel and keep working.
  Nothing is written to disk without your say any more. Only your own characters and stashes are
  listed there — the Clipboard is part of the project and is always saved for you.
- **The `Del Proj` and `Clear Proj` buttons are gone.** A project is now just a folder you chose
  the location of, so deleting one is a job for Windows Explorer, where you can see exactly what
  you are removing — `Del Proj` deleted the whole project folder and everything in it, which was
  no longer safe once a project could be opened from anywhere.
- **`File / Switch Appearance` asks about unsaved changes too**, instead of quietly saving
  everything, and cancelling that prompt now keeps your current appearance: the new one is only
  recorded once GoMule is really exiting, so a cancelled switch no longer applied itself anyway at
  the next restart.

### Holy Grail

- **New Holy Grail window**, tracking your collection across every character, stash and shared
  stash you have open at once. Uniques, sets and runewords each get their own tab, with a category
  tree, tier filters, a search box, per-category and overall progress, and an export button. Items
  are listed in required-level order, and every entry says which file it was found in and how many
  copies you have.

  ![Holy Grail window](https://raw.githubusercontent.com/jnbrunet/GoMule-Reimagined/main/docs/screenshots/holy-grail.png)

- **Runewords tab**, filtered by base item type and by the runes you actually own, so you can see
  what you could make right now. Each runeword is drawn as its own full description rather than a
  bare name.

  ![Runewords tab](https://raw.githubusercontent.com/jnbrunet/GoMule-Reimagined/main/docs/screenshots/holy-grail-runewords.png)

- **Hovering an item you are missing shows its whole definition** -- real value ranges instead of
  fixed numbers, the base item's own statistics, chance-to-cast and charged skills, and the "One of:"
  affix groups a unique picks from when it rolls. Missing runewords get a proper tooltip too.

  ![Missing-item tooltip](https://raw.githubusercontent.com/jnbrunet/GoMule-Reimagined/main/docs/screenshots/holy-grail-tooltip.png)

- The search box matches affixes, not just item names.
- Sets you have started list every piece, found or not, so you can see what is left to complete
  them -- and a set is never split across the Normal/Exceptional/Elite checkboxes.

### Item display

- Set items now show their own partial and full-set bonuses.
- Properties that come from the game tables and scale per character level no longer display as
  "+0".
- Properties whose stat is implied by their function, rather than named outright, are shown again
  instead of silently disappearing from an item.
- The `.d2x` stash view shows the selected item's sprite.
- Required levels are correct again on affixed items and on uniques.

### Characters and stashes that would not load

- A mercenary's set ring no longer aborts the whole character load. Set items whose bonus can roll
  a value but which never actually stored one were read as if they had, desynchronising every item
  after them.
- Quest items that end exactly on a byte boundary no longer swallow the item that follows them --
  one affected character was losing 34 of its 59 items, including everything it had equipped.
- Items carrying a granted-skill block now use the length the item itself records instead of a
  guess, which had been cutting some unique helms short and stopping the load at that point.

### Packaging

- Publish a turn-key Windows executable from a `YYYY.MM` release branch, no Java install required.
