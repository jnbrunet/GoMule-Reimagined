# GoMule-Reimagined

This is a fork of the popular Muling tool "GoMule" originally created by Andy Theuninck (Gohanman) and then continued by Randall and Silospen.
It was modified to be compatible with the Diablo 2 Resurrected mod "Reimagined" (3.0.10+)

![image](https://github.com/user-attachments/assets/3c53b5dd-4b99-45ba-9187-572866ed9963)

Get D2R Reimagined here: https://www.nexusmods.com/diablo2resurrected/mods/503

## Download

Go to the release page: https://github.com/jnbrunet/GoMule-Reimagined/releases

Releases are named `YYYY.MM.BB` — year, month, and the build number within that month — and each
one ships two archives:

| Archive | Size | Java required? |
|---|---|---|
| `GoMule_Reimagined_<version>_windows.zip` | ~44 MB | **No** — a Java runtime is bundled |
| `GoMule_Reimagined_<version>.zip` | ~11 MB | Yes — needs a JRE already installed |

### Older releases from the upstream fork
⮕ [2.1.2](https://github.com/Cjreek/GoMule-Reimagined/releases/download/gomule_reimagined_212/GoMule_Reimagined_2.1.2.zip)  
⮕ [2.1.1](https://github.com/Cjreek/GoMule-Reimagined/releases/download/gomule_reimagined_211/GoMule_Reimagined_2.1.1.zip)  

## Installation

Using `GoMule_Reimagined_<version>_windows.zip` (recommended, nothing else to install):

1) Extract the .zip anywhere you have write access — your Desktop or Documents is fine.
2) Double-click `GoMule.exe` inside the extracted `GoMule` folder.
3) Done.

Windows will show a "Windows protected your PC" SmartScreen warning the first time, because the
executable is not signed with a paid code-signing certificate. Click *More info* → *Run anyway*.

Note that `GoMule.exe` finds its data files (`d2111\`, `resources\`, …) relative to the folder it is
started from, so launch it from its own folder rather than from a shortcut with a different
"Start in" directory.

Using `GoMule_Reimagined_<version>.zip` (smaller, but you need a Java runtime installed):

1) Extract the contents of the .zip file.
2) Run `GoMule.bat`.

## Your projects

Your projects (character/stash lists, the Clipboard, Holy Grail progress, and the app's own
settings) live in `%APPDATA%\GoMule-Reimagined`, not next to the GoMule executable. This means
extracting a new release over an old one, or into a brand new folder, never loses your data. The
first time you run a release after upgrading from an older GoMule that still used a `projects\`
folder next to the executable, GoMule copies that folder's contents into `%APPDATA%\GoMule-Reimagined`
automatically and tells you where they went; the original `projects\` folder is left in place as a
backup, not deleted.

If you'd rather GoMule kept everything in one portable folder (e.g. running it from a USB stick),
launch it with `-Dgomule.userdata.dir=<path>` — see `GoMule.bat` for where to add a Java option —
to use that exact folder instead of `%APPDATA%`.

Use `File / New Project…` to create a project anywhere on disk (a OneDrive folder, another drive,
next to your saves — anywhere you have write access), `File / Open Project…` to switch to an
existing one, and `File / Close Project` to leave GoMule with no project open at all. Any of these,
plus the project drop-down in the left pane, will first ask whether to save any files you have open
with unsaved changes.

## Restrictions & Known Bugs

The main feature of opening characters and shared stashes and moving items inbetween those and custom GoMule/ATMA stashes should be fully functional for D2RR.
You might encounter very few custom properties from D2RR that are not correctly rendered. This is just a visual glitch and there is no danger of file corruption or anything bad happening with those items or characters.
It's just a minor visual issue that I might try to fix if you find something like this and report it to me.

The Flavie Report feature is currently not compatible for D2RR and I currently have no plans of fixing this anytime soon or probably at all.
I have focused on the main feature which is muling.

At the time of the first release I have tested this version of GoMule on my own characters/stashes for about a week with no issues.
It's still advised to make regular backups of your characters and GoMule stashes - just in case.

## Help & Support

If you have any questions or issues with this tool you can either post your issue here on github or contact @Cjreek on the Reimagined discord

## Compiling for different/new versions of D2RR

This is addressed at developers or technically versed people who want to compile GoMule Reimagined for a different or a new release of the Reimagined Mod.
Generally no actual code changes are necessary. Some files from the mod need to be copied over and then GoMule needs to be recompiled:

1) Copy all `.dc6` files from `/data/global/items/` into `resources/gfx/`
2) Copy following files from `/data/local/lng/strings/` into `src/main/resources/d2Files/D2R_1.0/translations/`
    - item-modifiers.json
    - item-nameaffixes.json
    - item-names.json
    - item-runes.json
    - monsters.json
    - skills.json
4) Copy following files from `/data/global/excel/` into `d2111/`
    - armor.txt
    - automagic.txt
    - charstats.txt
    - gems.txt
    - hireling.txt
    - itemstatcost.txt
    - itemtypes.txt
    - levels.txt
    - magicprefix.txt
    - magicsuffix.txt
    - misc.txt
    - monstats.txt
    - properties.txt
    - runes.txt
    - setitems.txt
    - sets.txt
    - skilldesc.txt
    - skills.txt
    - superuniques.txt
    - treasureclassex.txt
    - uniqueitems.txt
    - weapons.txt
4) Execute the `distribution` gradle task. Pass the version you are building for with
   `-PappVersion=...` to name the archive (it defaults to the `appVersion` in `build.gradle`):
```
gradlew distribution -PappVersion=2.1.2
```
5) An updated version of GoMule should be generated and placed in `build/distributions/`

## Releasing

Pushing a commit to a branch named `YYYY.MM` (e.g. `2026.09`) triggers
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds, runs the test
suite, packages the self-contained Windows application with `jpackage`, and publishes a
`YYYY.MM.BB` release. `BB` is derived from the tags already published for that month, so it
restarts at `01` every month. Pushing several commits at once produces a single release for the
tip of the branch.

### Release notes

Add an entry to [`CHANGELOG.md`](CHANGELOG.md) as you go and commit it to `main` like any other
change. When you merge `main` into the release branch, the workflow diffs `CHANGELOG.md` against
the last published release and uses the lines you added as the release notes. There is nothing to
rename or reset afterwards, and the workflow never commits to the repository.

- The per-version history lives on the releases page; `CHANGELOG.md` is only the staging area for
  the next release.
- Text inside `<!-- HTML comments -->` is stripped, so notes-to-self are safe in that file.
- Only *added* lines are picked up, so editing an existing entry republishes it in the next
  release.
- If you added nothing, the release falls back to GitHub's auto-generated commit list rather than
  shipping empty notes.

The last release is found by version-sorting the `YYYY.MM.BB` tags rather than with
`git describe`, because merge commits mean a tag on `2026.09` is not an ancestor of a `2026.10`
branch cut from `main`.
