package gomule.item;

// The Reimagined update of 2026-11-03 changed how five item stats are encoded, without bumping the
// save format version (still 105), so nothing in a file says which encoding its items use:
//
//   *ID  stat                         before            after
//   76   item_maxhp_percent           6 bits, add 10    8 bits, add 100
//   77   item_maxmana_percent         6 bits, add 10    8 bits, add 100
//   78   item_attackertakesdamage     7 bits, add 0     16 bits, add 0
//   366  hpregen_perlevel  -> moved to *ID 432 (same 6 bits, add 30)
//   432  passive_dmg_pierce -> moved to *ID 366 (same 8 bits, add 0)
//
// itemstatcost.txt is kept in sync with the installed mod, so it describes the new encoding --
// which is what every save written since the update uses: the live
// ModernSharedStashSoftCoreV2.d2i lost three of its tabs (first failure "Harrogath's Last Stand")
// with the old widths and loads fully with the new ones. But every save written before it still
// holds the old encoding: 29 tests on the real fixture characters and stashes loaded partially
// with the new table, and putting back just these five rows (and nothing else) made all 29 load
// again. The GoMule stash (.d2x) is never re-saved by the game, so it can even mix both encodings
// item by item.
//
// D2Item.readItem() therefore reads an item with the current table first and, only when that item
// actually reached one of these stat ids (every bit before the first such id decodes the same
// either way), reads it a second time with the pre-update encoding below and keeps whichever one
// holds together. Items are written back as their original bytes, never re-encoded, so a
// pre-update item is saved exactly as it was found.
final class D2PreUpdateStatWidths {

    // Set while D2Item.readItem() is reading an item with the pre-update encoding.
    static boolean sActive = false;

    // Set by D2PropCollection.readProp() whenever it reads one of the changed stat ids, in either
    // encoding: an item that never sets it decodes identically with both, so it needs no retry.
    static boolean sChangedStatRead = false;

    private D2PreUpdateStatWidths() {
    }

    static boolean isChangedStat(int pStatId) {
        return pStatId == 76 || pStatId == 77 || pStatId == 78 || pStatId == 366 || pStatId == 432;
    }

    // The current *ID of the stat a pre-update item stored under pStatId. The current table's row
    // for the moved stat already has the right width, so it can be read through that row as is.
    static int currentStatId(int pStatId) {
        if (pStatId == 366) return 432;
        if (pStatId == 432) return 366;
        return pStatId;
    }

    // {Save Bits, Save Add} for the stats whose width changed, or null to use the current table.
    static int[] saveBitsAndAdd(int pStatId) {
        if (pStatId == 76 || pStatId == 77) return new int[]{6, 10};
        if (pStatId == 78) return new int[]{7, 0};
        return null;
    }
}
