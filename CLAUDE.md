# CLAUDE.md — GoMule-Reimagined save-parsing bug playbook

GoMule is a Java/Swing Diablo II item manager. This fork adds support for the **D2R
"Reimagined" (D2RMM) mod**, which introduces custom items and a post-v99 (version 105)
save format. Almost every bug we get is the **item parser desyncing on some new modded
item** in a real `.d2s` character or `.d2i` shared stash. This file is the playbook for
fixing those. Read it, then go straight to "Workflow".

## Build / test

- Java 11 (Temurin), Gradle 7.1, JUnit 5.
- Build + full suite: `./gradlew clean build`
- One test, seeing stdout: `./gradlew test --tests 'gomule.d2s.SomeTest' --rerun-tasks -i`
  - `--rerun-tasks` is required (results cache); `-i` is required to see `System.out`.
- Game data tables live in `./d2111/*.txt`. Tests load them with
  `D2TxtFile.constructTxtFiles("./d2111")` before parsing anything.
- Fat jar: `build/libs/GoMule-Reimagined-1.0-SNAPSHOT.jar`.
- Save files being debugged live in the user's D2RMM folder:
  `/Users/andrew/Library/CloudStorage/OneDrive-Personal/D2R saves/Diablo II Resurrected/mods/D2RMM/`.
  **Copy the file into the scratchpad first** and debug that copy — the user re-saves the
  live file constantly, so it's a moving target.

## The recurring bug class

Items are stored back-to-back, byte-aligned, with Huffman-encoded type codes. An item's
length is only known once it parses. So if item N consumes the **wrong number of bits**,
item N+1 starts at the wrong bit, its Huffman type decodes to garbage, and you get a
null-message crash. Most fixes are "this specific kind of item carries K extra/fewer
trailing bits than the parser accounts for." Everything is gated on
`usesPostV99ItemFormat()` (format version > 99) unless proven older.

## Error signature → likely cause

| Symptom | Meaning |
|---|---|
| `D2ItemException: Error: null (null, null)` (NPE at `D2Item.readExtend`) | **Desync.** Some earlier item over/under-read; the failing item's type is garbage. Most common. |
| `Error: For input string: ""` | Property read hit a stat with an empty "Save Bits" column (a character-only stat like `mana`/`level`). Almost always a **desync symptom**, not a data bug. |
| `No translation for <code>` | A new item has no localized string. Fall back to the raw `.txt` name (see the `getTranslationOrNull` fix in `readExtend`). |
| `ArrayIndexOutOfBoundsException` | Reading past the buffer end (e.g. `findBytes` scanning a marker whose first byte lands in the last few bytes). |
| Item renders wrong text (e.g. "Unknown Tree") | Not a desync — a rendering/lookup issue in `D2Prop`. |

## Workflow

1. **Reproduce.** Copy the file to scratchpad. Write a throwaway test (template below) that
   parses it and prints `parsed / incomplete / reason`. A partial load reports
   `Item N of M failed to parse: ...` — note N (1-based).
2. **Classify** using the table above.
3. **For a desync — find the true boundary.** Temporarily add an item-boundary print in
   `D2Character.readItems` (template below) to get each item's `startByte` / `len`. The
   failing item's start byte is where the previous item *thinks* it ended. Then **chain-scan**
   the raw bytes near there to find where a real item actually starts (an item that decodes
   AND whose next item also decodes = a real boundary). The delta between the two is your
   over/under-read (usually ±8 bits = 1 byte).
4. **Identify the culprit item.** It's usually the item *before* the reported failure
   (it over/under-read). Get its `item_type`, quality, sockets, `iType`, `namestr`, and look
   it up in `d2111/*.txt` (`misc.txt`, `weapons.txt`, `armor.txt`, `uniqueitems.txt`,
   `setitems.txt`). Understand *what kind* of item/property is new.
5. **Root-cause** against the taxonomy below.
6. **Fix + verify.** Add the smallest scoped skip/branch, gated on `usesPostV99ItemFormat()`.
   Verify by (a) the culprit's neighbours decoding into **real, recognizable items** — not
   just "doesn't throw" — and (b) the whole file reaching `incomplete=false`.
7. **Lock it in.** Copy the real save into `src/test/resources/charFiles/` (next `pallyN.d2s`)
   and add a `D2CharacterTest` asserting the item count + the specific decoded item(s).
   Remove all throwaway tests and instrumentation. `./gradlew clean build` must be green.

## Root-cause taxonomy (what past fixes were)

- **Byte-alignment padding-byte drop.** `getNextByteBoundaryInBits()` = `(pos+7)&~7` does
  **not** advance when already byte-aligned, so an item whose body ends exactly on a byte
  boundary silently loses its trailing padding byte. Fix: `if (pos % 8 == 0) skipBits(8)`.
  NOT universal — only certain item classes have that padding (byte-aligned simple potions,
  loose cube runes/gems, socketed runes). Others (e.g. runewords) end byte-aligned with no
  padding, so never blanket-apply it.
- **Fixed extra trailing bits per item class.** Some codes/categories carry a constant blob
  nothing above reads: `rvl` +8, `rvs` +16, `elix` +8, Worldstone Shards (`xa1`–`xa5`) +8,
  elemental Facets +48, the flag-29 skill blob +52 (+64 more when its bit 47 is set). There is
  no +56 `item_elemskill` variant — that old rule fit only because byte-rounding hides a 4-bit
  difference unless the blob starts at bit 1–4 of a byte (Crescent Moon disproved it). When
  confirming an amount, check the start offset actually **discriminates** between candidates.
- **Placement: socketed items put trailing data BEFORE the sockets.** Sockets are always last
  in the item body, so a socketed flag-29 item's skill blob is skipped *before* the socket
  loop, not after (the after-socket skip is guarded by `iSocketNrFilled == 0`).
- **Wrong discriminator.** Recurring theme: a rule keyed on the exact `item_type` misses a
  sibling code. Prefer the **category**: `namestr` (jewels: `jew` and `cjw` both = `"jew"`),
  `iType` (the "type" column, e.g. `elix`, `ques`), or a stat (`item_elemskill`, not
  properties.txt `func1`). Verify the category cleanly separates the confirmed cases.
- **Inter-socket byte.** Each socketed **rune/gem** is followed by one padding byte; socketed
  **jewels** (namestr `jew`, incl. Facets and Colossal Jewels `cjw`) are not.
- **Missing translation.** Not a desync — `getTranslationOrNull` + fall back to the `.txt`
  display name so one untranslated item can't abort the whole load.

## Key files & methods

- `src/main/java/gomule/item/D2Item.java` — item parsing. `readExtend` (type, base name,
  gem/rune props, socket loop, then the block of per-class trailing skips), `readExtend1`
  (socket-fill count, quality), `readExtend2` (property list + set/runeword bonus lists),
  `readProperties` / `D2PropCollection.readProp` (the stat-id/value loop, terminated by 511).
  The socket loop is where the inter-socket byte and the before-sockets skill blob live.
- `src/main/java/gomule/d2s/D2Character.java` — `readItems` (the flat item loop; each failure
  is caught and marks `iItemsIncomplete`).
- `src/main/java/gomule/d2i/D2SharedStashReader.java` — `.d2i` shared stash; panes found by
  scanning for the `55AA55AA` marker; each pane parsed independently, failures isolated.
- `src/main/java/gomule/util/D2BitReader.java` — `read`, `skipBits`, `get_pos`/`set_pos`,
  `getNextByteBoundaryInBits`, `findBytes`/`findNextBytes`.
- `src/main/java/gomule/item/D2Prop.java` — property rendering (tooltip text, skill tabs).
- Useful accessors: `D2Item.getItem_type() get_location() get_panel() getItemName()
  getSocketNrFilled() getSocketNrTotal() getItemLength() getiSocketedItems() getItemQuality()`;
  `D2Character.getItemList()` (raw ArrayList), `isItemsIncomplete()`,
  `getItemsIncompleteReason()`, `getMercItemNr()`, `getCharName()`, `getCharClass()`.
- `panel`: 1 = inventory, 4 = cube, 5 = stash. `location`: 6 = socketed.

## Diagnostic recipes

Reproduce / list items (drop in `src/test/java/gomule/d2s/`, delete when done):

```java
@Test public void repro() throws Exception {
    D2TxtFile.constructTxtFiles("./d2111");
    String path = "<scratchpad>/copy.d2s";
    D2Character c = new D2Character(path);
    var items = c.getItemList();
    System.out.println("parsed=" + items.size() + " incomplete=" + c.isItemsIncomplete()
            + " reason=" + c.getItemsIncompleteReason());
}
```

Per-item boundaries — temporarily add to `D2Character.readItems`, in the `try` right after
`new D2Item(...)` and in the `catch`, guarded so it's easy to remove:

```java
if (Boolean.getBoolean("d2.dbg"))
    System.out.println("[SEQ] i=" + i + " start=" + lItemStart + " '" + lItem.getItemName()
        + "' type=" + lItem.getItem_type() + " filled=" + lItem.getSocketNrFilled()
        + " len=" + lItem.getItemLength() + " end=" + (lItemStart + lItem.getItemLength()));
```
Set it from the test with `System.setProperty("d2.dbg","true")` (same JVM — no build.gradle
plumbing needed).

Chain-scan for the real next item (finds true boundaries independent of the sequential parse):

```java
D2Character warmup = new D2Character(path);   // CRUCIAL: sets the static format version,
                                              // else isolated items parse in the wrong format
byte[] data = Files.readAllBytes(Paths.get(path));
for (int b = <startByte>; b < data.length - 4; b++) {
    var r = new D2BitReader(data); r.set_byte_pos(b);
    try {
        D2Item it = new D2Item(path, r, 1);
        if (it.getItemName() != null && !it.getItemName().contains("Ear")
                && it.getItemLength() >= 10 && it.getItemLength() < 150) {
            int end = r.get_byte_pos();
            try { D2Item nxt = new D2Item(path, r, 1);   // require a 2-item chain
                if (nxt.getItemName() != null && !nxt.getItemName().contains("Ear")) {
                    System.out.println("[ITEM@] byte=" + b + " '" + it.getItemName()
                        + "' type=" + it.getItem_type() + " end=" + end + " -> '" + nxt.getItemName() + "'");
                    b = end - 1; } } catch (Throwable ignore) {}
        }
    } catch (Throwable ignore) {}
}
```

Peek raw bits (compare the trailing "blob" across copies of the same item, etc.): save
`pFile.get_pos()`, `read(1)` N times into a string, `set_pos(saved)`.

## Gotchas

- **Format version is static.** Isolated `new D2Item(...)` uses `sFormatVersion`, set only when
  a `D2Character` reads a header. Construct a `D2Character` first or everything desyncs with
  `For input string: ""` at empty-Save-Bits stats. (This is why the chain-scan warms up.)
- **Byte-scanning can hang** at bad offsets (recursion into a bogus socket count). Bound the
  scan and the item length (`< 150`), catch `Throwable`.
- **"Ear"-shaped garbage** (names like `X's Ear`) is the classic desync tell; a valid decode
  is a real rune/gem/jewel/item name.
- **Verify with a chain**, never "didn't throw" — a wrong offset often decodes into a
  plausible-but-wrong item. Prior fixes were confirmed as the *only* offset producing a run of
  real, recognizable following items.
- macOS has no `timeout` (use `gtimeout` if needed). If a Gradle daemon hangs holding
  `~/.gradle/caches/journal-1/journal-1.lock`, `./gradlew --stop` / kill the daemon PIDs.

## Conventions

- **Comment density matches the codebase**: every trailing-skip has a paragraph explaining the
  real item it was confirmed against, why that amount, and "what these bits hold is still
  unknown" where true. Keep that style — future-you relies on it.
- **Fixtures are real saves** in `src/test/resources/charFiles/` (`pally3/4/5.d2s`, etc.), with
  tests asserting concrete decoded values, not just counts. `hblSocketVariants.d2s` is the
  minimal isolated case for the flag-29 skill blob.
- **Commit hygiene:** never stage `.DS_Store` or the pre-existing `GoMule.bat` change. Stage
  only the specific source/test/fixture files. Commits go on `main`; push when asked. End
  commit messages with the `Co-Authored-By: Claude ...` trailer.
- Remove ALL throwaway tests and instrumentation before building/committing.
