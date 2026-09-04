/*******************************************************************************
 *
 * Copyright 2007 Andy Theuninck, Randall & Silospen
 *
 * This file is part of gomule.
 *
 * gomule is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * gomule is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * gomlue; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 *
 ******************************************************************************/

package gomule.item;

import com.google.common.primitives.Ints;
import gomule.D2Files;
import gomule.util.D2BitReader;
import gomule.util.D2ItemException;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;
import randall.flavie.D2ItemInterface;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//an item class
//manages one item
//keeps the a copy of the bytes representing
//an item and a bitreader to manipulate them
//also stores most data from the item in

//this class is NOT designed to edit items
//any methods that allow the item's bytes
//to be written only exist to facillitate
//moving items. writing other item fields
//is not supported by this class
public class D2Item implements Comparable, D2ItemInterface {

    protected String iItemName;
    protected String iBaseItemName;
    private D2PropCollection iProps = new D2PropCollection();
    private ArrayList<D2Item> iSocketedItems;
    private int flags;
    private short version;
    private short location;
    private short body_position;
    private short row;
    private short col;
    private short panel;
    private String item_type;
    private short iSocketNrFilled = 0;
    private short iSocketNrTotal = 0;
    private long fingerprint;
    private short ilvl;
    private short quality;
    private short gfx_num;
    private D2TxtFileItemProperties automod_info;
    private short[] rare_prefixes;
    private short[] rare_suffixes;
    private String personalization;
    private short width;
    private short height;
    private String image_file;
    private D2TxtFileItemProperties iItemType;
    private String iType;
    private String iType2;
    private boolean iEthereal;
    private boolean iSocketed;
    private boolean iThrow;
    private boolean iMagical;
    private boolean iRare;
    private boolean iCrafted;
    private boolean iSet;
    private boolean iUnique;
    private boolean iRuneWord;
    private boolean iSmallCharm;
    private boolean iLargeCharm;

    private boolean iGrandCharm;
    private boolean iJewel;
    private boolean iGem;
    private boolean iStackable = false;
    private boolean iRune;
    private boolean iTypeMisc;
    private boolean iIdentified;
    private boolean iTypeWeapon;
    private boolean iTypeArmor;
    private short iCurDur;
    private boolean questItem;

    private short iMaxDur;

    private short iDef;

    private short cBlock;

    private short iBlock;

    private short iInitDef;

    private short[] i1Dmg;
    private short[] i2Dmg;

    // 0 FOR BOTH 1 FOR 1H 2 FOR 2H
    private int iWhichHand;

    // private int iLvl;
    private String iFP;

    private String iGUID;

    private boolean iBody = false;

    private String iBodyLoc1;

    private String iBodyLoc2;

    private boolean iBelt = false;

    private D2BitReader iItem;

    private String iFileName;

    private boolean iIsChar;

    private int iCharLvl;

    private int iReqLvl = -1;

    private int iReqStr = -1;

    private int iReqDex = -1;

    private String iSetName;

    private D2TxtFileItemProperties iSetItemRow;

    private int setSize;

    private String iItemQuality = "none";

    private short set_id;

    private short unique_id = -1;

    // The runes.txt "Name" column of the runeword this item resolved to (e.g. "Insight"),
    // captured at the point readExtend2() already has that row in hand -- see the assignment
    // near "if (iRuneWord)" below. iItemName is overwritten with the translated display name
    // right after, which loses the original runes.txt identity; this field is what lets the
    // Holy Grail scanner (gomule.grail.D2GrailScanner) identify a runeword by its real key
    // instead of re-deriving it from the socketed runes' codes a second time. Null for any
    // non-runeword item. No bit-level read is involved: this is a plain field assignment inside
    // an already-executing "if (lRuneWord != null)" branch.
    private String iRuneWordIndex;

    private final HuffmanLookupTable huffmanLookupTable = HuffmanLookupTable.withStandardDictionary();

    // The .d2s/.d2i item-format gained an extra bit before the magical property list (and
    // ethereal items, one more after it) at some D2R patch after version 99 -- confirmed present
    // at version 105, confirmed ABSENT at version 99 (an older real shared-stash file broke when
    // this was applied unconditionally). The exact version it was introduced at, between 100 and
    // 104, is unknown -- no sample files or public docs exist for those. Callers that know their
    // source file's version (D2Character, D2SharedStashReader) should call setFormatVersion(...)
    // before constructing any D2Item from it. Defaults to the old/legacy behavior so any caller
    // that doesn't set this explicitly behaves exactly as this code always has.
    private static long sFormatVersion = 99;

    public static void setFormatVersion(long pVersion) {
        sFormatVersion = pVersion;
    }

    private static boolean usesPostV99ItemFormat() {
        return sFormatVersion > 99;
    }

    // The 8 "elemental Facet" unique jewels: uniqueitems.txt IDs 392-399 (Spring/Winter/Summer/
    // Autumn/Thunder/Rime/Burnt/Toxic Facet). See the two call sites for what's empirically known
    // about why these need special handling. Not to be confused with the unrelated gem-quality
    // "Facet" family (uniqueitems.txt IDs ~1389-1398), which has not been shown to need this.
    private boolean isElementalFacet() {
        return iUnique && unique_id >= 392 && unique_id <= 399;
    }

    // The simple single-cell beltable potions that share the byte-aligned padding-byte quirk fixed
    // near the end of readExtend(): healing ("hpot"), mana ("mpot"), antidote ("apot"), thawing
    // ("wpot") and stamina ("spot"). Deliberately excludes rejuvenation ("rpot": rvl/rvs), which
    // carries a genuine extra field handled separately and unconditionally. See that call site's
    // comment for the full reasoning and what is confirmed vs. included by structural analogy.
    private static boolean isSimpleBeltablePotion(String pType) {
        return "hpot".equals(pType) || "mpot".equals(pType) || "apot".equals(pType)
                || "wpot".equals(pType) || "spot".equals(pType);
    }

    // The Reimagined/D2R quest-item family that carries one fixed extra trailing byte (see the
    // trailing-skip call site for the full evidence): every "type == ques" item whose misc.txt "quest"
    // column is blank. That blank column is what separates the mod's added quest items (Worldstone
    // Shards, Pandemonium keys, Uber organs/essences/materials, Standard of Heroes) from the classic
    // ones (Horadric Cube, Khalim's organs, etc.), which all carry a non-empty quest value and parse
    // fine untouched. iType is iItemType.get("type"), already set by the time this is called.
    private boolean isReimaginedQuestItem() {
        return "ques".equals(iType) && iItemType != null && "".equals(iItemType.get("quest"));
    }

    public D2Item(String pFileName, D2BitReader pFile, long pCharLvl)
            throws Exception {
        iFileName = pFileName;
        iIsChar = iFileName.endsWith(".d2s");
        iCharLvl = (int) pCharLvl;

        try {
            int startOfItemInBytes = pFile.get_byte_pos();
            read_item(pFile);
            int endOfItemInBytes = pFile.getNextByteBoundaryInBits() / 8;
            int lLengthToNextJM = endOfItemInBytes - startOfItemInBytes;
            pFile.set_byte_pos(startOfItemInBytes);
            iItem = new D2BitReader(pFile.get_bytes(lLengthToNextJM));
            pFile.set_byte_pos(startOfItemInBytes + lLengthToNextJM);
        } catch (D2ItemException pEx) {
            throw pEx;
        } catch (Exception pEx) {
            pEx.printStackTrace();
            throw new D2ItemException("Error: " + pEx.getMessage() + getExStr());
        }
    }

    // read basic information from the bytes
    // common to all items, then split based on
    // whether the item is an ear
    private void read_item(D2BitReader pFile) throws Exception {
        flags = (int) pFile.unflip(pFile.read(32), 32); // 4 bytes

        iSocketed = check_flag(12);
        iEthereal = check_flag(23);
        iRuneWord = check_flag(27);
        iIdentified = check_flag(5);
        version = 9999;

        pFile.skipBits(3);
        location = (short) pFile.read(3);

        body_position = (short) pFile.read(4);
        col = (short) pFile.read(4);
        row = (short) pFile.read(4);
        panel = (short) pFile.read(3);

        // flag 17 is an ear
        if (!check_flag(17)) {

            readExtend(pFile);
        } else {
            read_ear(pFile);
        }

        //Need to tidy up the properties before the item mods are calculated.
        iProps.deleteUselessProperties();


        if (isTypeArmor() || isTypeWeapon()) {
//			Blunt does 150 damage to undead
            if (iType.equals("club") || iType.equals("scep")
                    || iType.equals("mace") || iType.equals("hamm")) {
                iProps.add(new D2Prop(122, new int[]{150}, 0));
            }
            applyItemMods();
        }
    }

    // read ear related data from the bytes
    private void read_ear(D2BitReader pFile) {
        int eClass = (int) pFile.read(3);
        int eLevel = (int) (pFile.read(7));

        StringBuffer lCharName = new StringBuffer();
        for (int i = 0; i < 18; i++) {
            long lChar = pFile.read(7);
            if (lChar != 0) {
                lCharName.append((char) lChar);
            } else {
                pFile.set_pos(pFile.getNextByteBoundaryInBits() == pFile.get_pos() ? pFile.get_pos() + 8 : pFile.getNextByteBoundaryInBits());
                break;
            }
        }
        iItemType = D2TxtFile.search("ear");
        height = Short.parseShort(iItemType.get("invheight"));
        width = Short.parseShort(iItemType.get("invwidth"));
        image_file = iItemType.get("invfile");
        iBaseItemName = iItemName = lCharName.toString() + "'s Ear";

        iProps.add(new D2Prop(185, new int[]{eClass, eLevel}, 0, true, 39));
    }

    // read non ear data from the bytes,
    // setting class variables for easier access
    private void readExtend(D2BitReader pFile) throws Exception {
        // 9,5 bytes already read (common data)
        item_type = huffmanLookupTable.readHuffmanEncodedString(pFile);
        iItemType = D2TxtFile.search(item_type);
        height = Short.parseShort(iItemType.get("invheight"));
        width = Short.parseShort(iItemType.get("invwidth"));
        image_file = iItemType.get("invfile");

        String lD2TxtFileName = iItemType.getFileName();
        if (lD2TxtFileName != null) {
            iTypeMisc = ("misc".equals(lD2TxtFileName));
            iTypeWeapon = ("weapons".equals(lD2TxtFileName));
            iTypeArmor = ("armor".equals(lD2TxtFileName));
            questItem = !iItemType.get("quest").equals("");
        }

        iType = iItemType.get("type");
        iType2 = iItemType.get("type2");

        // Shields - block chance.
        if (isShield()) {
            cBlock = Short.parseShort(iItemType.get("block"));
        }

        // Requerements
        if (iTypeMisc) {
            iReqLvl = getReq(iItemType.get("levelreq"));
        } else if (iTypeArmor) {
            iReqLvl = getReq(iItemType.get("levelreq"));
            iReqStr = getReq(iItemType.get("reqstr"));

            D2TxtFileItemProperties qualSearch = D2TxtFile.ARMOR.searchColumns(
                    "normcode", item_type);
            iItemQuality = "normal";
            if (qualSearch == null) {
                qualSearch = D2TxtFile.ARMOR.searchColumns("ubercode",
                        item_type);
                iItemQuality = "exceptional";
                if (qualSearch == null) {
                    qualSearch = D2TxtFile.ARMOR.searchColumns("ultracode",
                            item_type);
                    iItemQuality = "elite";
                }
            }

        } else if (iTypeWeapon) {
            iReqLvl = getReq(iItemType.get("levelreq"));
            iReqStr = getReq(iItemType.get("reqstr"));
            iReqDex = getReq(iItemType.get("reqdex"));

            D2TxtFileItemProperties qualSearch = D2TxtFile.WEAPONS
                    .searchColumns("normcode", item_type);
            iItemQuality = "normal";
            if (qualSearch == null) {
                qualSearch = D2TxtFile.WEAPONS.searchColumns("ubercode",
                        item_type);
                iItemQuality = "exceptional";
                if (qualSearch == null) {
                    qualSearch = D2TxtFile.WEAPONS.searchColumns("ultracode",
                            item_type);
                    iItemQuality = "elite";
                }
            }
        }

        String lItemName = D2Files.getInstance().getTranslations()
                .getTranslationOrNull(item_type, iItemType.get("name"));
        if (lItemName == null) {
            // No localized string for this item -- typically a newly-added Reimagined item that
            // isn't in the translation tables yet (e.g. the "cs2" Crafted Sunder Charm). Fall back
            // to the raw display name straight from the .txt files instead of crashing the whole
            // character load: getTranslation() would throw IllegalArgumentException here, but the
            // null check just below shows a missing translation was always meant to be tolerated.
            // Note the second argument above is a translation KEY (tried after item_type); the
            // fallback here uses the same "name" column directly as the display string.
            lItemName = iItemType.get("name");
        }
        if (lItemName != null) {
            iItemName = lItemName;
            iBaseItemName = iItemName;
        }

        // flag 22 is a simple item (extend1)
        if (!check_flag(22)) {
            readExtend1(pFile);
        }

        // gold (?)
        if ("gold".equals(item_type)) {
            if (pFile.read(1) == 0) {
                pFile.read(12);
            } else {
                pFile.read(32);
            }
        }

        long lHasGUID = pFile.read(1);

        if (lHasGUID == 1) { // GUID ???
            if (iType.startsWith("rune") || iType.startsWith("gem")
                    || iType.startsWith("amu") || iType.startsWith("rin")
                    || isCharm() || !isTypeMisc()) {

                iGUID = "0x" + Integer.toHexString((int) pFile.read(32))
                        + " 0x" + Integer.toHexString((int) pFile.read(32))
                        + " 0x" + Integer.toHexString((int) pFile.read(32))
                        + " 0x" + Integer.toHexString((int) pFile.read(32));
            } else {
                pFile.read(3);
            }
        }

        // flag 22 is a simple item (extend2)
        if (!check_flag(22)) {
            readExtend2(pFile);
        }

        if (iType != null && iType2 != null && iType.startsWith("gem")) {
            if (iType2.equals("gem0") || iType2.equals("gem1")
                    || iType2.equals("gem2") || iType2.equals("gem3")
                    || iType2.equals("gem4")) {
                readPropertiesGems();
                iGem = true;
            }
        }

        if (iType != null && iType2 != null && iType.startsWith("rune")) {
            readPropertiesGems();
            iRune = true;
        }

        D2TxtFileItemProperties lItemType = D2TxtFile.ITEM_TYPES.searchColumns(
                "Code", iType);

        if (lItemType == null) {
            lItemType = D2TxtFile.ITEM_TYPES.searchColumns("Equiv1", iType);
            if (lItemType == null) {
                lItemType = D2TxtFile.ITEM_TYPES.searchColumns("Equiv2", iType);
            }
        }

        if ("1".equals(lItemType.get("Body"))) {
            iBody = true;
            iBodyLoc1 = lItemType.get("BodyLoc1");
            iBodyLoc2 = lItemType.get("BodyLoc2");
        }
        if ("1".equals(lItemType.get("Beltable"))) {
            iBelt = true;
            readPropertiesPots(pFile);
        }

        int lLastItem = pFile.get_byte_pos();


        if (iSocketNrFilled > 0) {
            iSocketedItems = new ArrayList<>();
            // A flag-29 (skill-granting) item stores its extra skill bits -- the same bits the
            // trailing skip near the end of this method reads for non-socketed items -- BEFORE its
            // socketed sub-items, not after: sockets are always last in the item body, so anything
            // the item itself carries past its property list comes first. For a non-socketed flag-29
            // item the two placements coincide (no sockets, so "before the sockets" == "at the very
            // end"), which is why the trailing skip near the end of this method has been correct until
            // now. The first real socketed flag-29 item -- a Paladin's unique "Hand of Blessed Light"
            // (uid 146) with "Heaven Facet" jewels socketed in -- exposed the difference: its facets
            // only decoded, and the ~110 items after it only parsed, once these bits were skipped here
            // instead of after the socket loop. The amount is exactly the same as that trailing skip
            // (see hasElementalSkillProperty()); the trailing skip itself is suppressed for socketed
            // items (its iSocketNrFilled == 0 guard) so the bits are never counted twice. Confirmed
            // against three copies of that scepter in one character -- socketed with jewels, with
            // empty sockets, and un-socketed -- whose trailing blobs are byte-for-byte the same
            // structure regardless of sockets, all decoding only at this amount. Kept narrow
            // (socketed + flag-29 + not a Facet, which has its own handling).
            if (check_flag(29) && !isElementalFacet() && usesPostV99ItemFormat()) {
                pFile.skipBits(hasElementalSkillProperty() ? 56 : 52);
            }
            pFile.set_pos(pFile.getNextByteBoundaryInBits());
            for (int i = 0; i < iSocketNrFilled; i++) {
                D2Item lSocket = new D2Item(iFileName, pFile, iCharLvl);
                // Current D2R (version 105) inserts one full extra byte after each socketed
                // rune/gem sub-item before the next one -- or before whatever follows if it's the
                // last. Confirmed against a real runeword (Pul+Hel+El = "Love"): without this skip,
                // the first socketed rune decoded correctly but every rune after it read as garbage;
                // with it, all three rune names and the ~20 items that follow decode correctly.
                // Socketed JEWELS are the exception -- they don't take this byte, because a jewel
                // (unlike a simple rune/gem) carries its own property list and its read already ends
                // flush against the next sub-item. This first showed up as elemental Facets only
                // (isElementalFacet(): a "Sadira" unique bow with a Rime Facet in socket 0, where
                // adding this byte on top of the Facet's own 48-bit trailing skip broke the next
                // socket, a Shael Rune), but a later real character carried a non-Facet jewel socketed
                // in -- a Paladin's "Hand of Blessed Light" holding two "Heaven Facet" jewels (a
                // Reimagined jewel, uid 1400, outside the 392-399 elemental-Facet range) -- whose
                // second jewel only decoded once the byte was withheld here too. Keyed on the jewel
                // FAMILY, not a single item code: a regular Jewel is code "jew", but the Reimagined
                // "Colossal Jewel" is code "cjw" (e.g. a unique "Guardian's Light" socketed alongside
                // four Heaven Facets in one Hand of Blessed Light) -- both, and any future jewel code,
                // share namestr "jew", which is the real "is this a jewel" flag. Without this the cjw
                // took the byte and the item after its scepter failed to load. Runes and gems (other
                // namestrs) still take the byte.
                boolean socketIsJewel = lSocket.iItemType != null
                        && "jew".equals(lSocket.iItemType.get("namestr"));
                if (usesPostV99ItemFormat() && !lSocket.isElementalFacet() && !socketIsJewel) {
                    pFile.skipBits(8);
                }
                iSocketedItems.add(lSocket);

                if (lSocket.isJewel()) {
                    iProps.addAll(lSocket.getPropCollection(), 1);
                } else if (isTypeWeapon()) {
                    iProps.addAll(lSocket.getPropCollection(), 7);
                } else if (isTypeArmor()) {
                    if (iType.equals("tors") || iType.equals("helm")
                            || iType.equals("phlm") || iType.equals("pelt")
                            || iType.equals("cloa") || iType.equals("circ")) {
                        iProps.addAll(lSocket.getPropCollection(), 8);
                    } else {
                        iProps.addAll(lSocket.getPropCollection(), 9);
                    }
                }
                if (lSocket.iReqLvl > iReqLvl) {
                    iReqLvl = lSocket.iReqLvl;
                }

            }
        }

        if (iRuneWord) {
            ArrayList lList = new ArrayList();
            for (int i = 0; i < iSocketedItems.size(); i++) {
                lList.add(iSocketedItems.get(i).getRuneCode());
            }

            D2TxtFileItemProperties lRuneWord = D2TxtFile.RUNES
                    .searchRuneWord(lList);
            if (lRuneWord != null) {
                String lookedUpName = D2Files.getInstance().getTranslations().getTranslation(lRuneWord.get("Name"));
                iItemName = lookedUpName == null ? lRuneWord.get("*Rune Name") : lookedUpName;
                // Keep the runes.txt row's own identity before iItemName above overwrites it with
                // the (translated) display name -- see the iRuneWordIndex field comment.
                iRuneWordIndex = lRuneWord.get("Name");
            }
        }

        if (iSocketNrFilled > 0 && isNormal()) {
            iItemName = "Gemmed " + iItemName;
        }

        if (iItemName != null) {
            iItemName = iItemName.trim();

        }

        if (iBaseItemName != null) {
            iBaseItemName = iBaseItemName.trim();

        }

        if (iEthereal) {
            if (iReqStr != -1) {
                iReqStr -= 10;
            }
            if (iReqDex != -1) {
                iReqDex -= 10;
            }
        }

        if ("bkd".equals(item_type) && pFile.read(8) != 0) {
            /*
            Strange case with this quest item, it seems to have a trailing 10th byte of 00
            This needs to be skipped over, it's not clear if there's a general rule here but
            I've implemented it as a specific one just in case. This code reads and checks if there's a zero
            and then resets the skip if there is.
             */
            pFile.set_byte_pos(pFile.get_pos() - 8);
        }
        // Same shape as the "bkd" quirk just above, found the same way: a Full Rejuvenation
        // Potion ("rvl") is followed by one extra trailing byte that nothing above reads. Unlike
        // "bkd", this one is confirmed specific to the current (post-v99) format, not a
        // longstanding quirk -- and confirmed in two independent real characters: skipping
        // exactly 8 bits here, not 0 and not 16, was the only amount that let the very next item
        // decode (in both files, into a real, recognizable item -- not just "didn't throw").
        // What this byte actually holds is still unknown.
        if ("rvl".equals(item_type) && usesPostV99ItemFormat()) {
            pFile.skipBits(8);
        }
        // Same shape again, found the same way, in a real shared stash this time: a regular
        // Rejuvenation Potion ("rvs", distinct from the Full Rejuvenation Potion above) needs 16
        // extra trailing bits, not 8 -- brute-force-confirmed as the only offset, out of more
        // than 200 tried, that produced a real, recognizable next item (three runes, evenly
        // spaced at the fixed 88-bit rune item length, ruling out coincidence). What these bits
        // hold is still unknown.
        if ("rvs".equals(item_type) && usesPostV99ItemFormat()) {
            pFile.skipBits(16);
        }
        // Same shape again, but generalized to the whole item class once a fourth and fifth
        // example showed up together. Every Reimagined "elixir"-type item (itemtypes "elix") --
        // the family that includes "Orb of Infusion" ("ooi"), "Orb of Assemblage" ("ooa"), "Orb
        // of Socketing" ("oos"), "Gem Cluster" ("1gc"), and a couple dozen more (the other Orbs,
        // Token of Absolution, the dyes, etc.) -- carries 8 extra trailing bits nothing above
        // reads. Brute-force-confirmed as the only byte offset, out of 27 tried near each
        // boundary, that decoded a real, recognizable next item, across all four codes a single
        // real character actually carried (ooi here is what this started as a per-code "ooi" fix
        // before ooa/oos/1gc proved it was the whole "elix" class -- folding them into one rule
        // also avoids the per-code list silently missing the next new Orb a player picks up).
        // Keyed on iType, not item_type, deliberately: the point is the category, not the code.
        // What these bits actually hold is still unknown.
        if ("elix".equals(iType) && usesPostV99ItemFormat()) {
            pFile.skipBits(8);
        }
        // The Reimagined/D2R quest items carry 8 extra trailing bits nothing above reads -- the same
        // fixed-trailing-byte shape as "rvl"/"elix", NOT the byte-alignment one: their bodies end
        // mid-byte (observed bit offsets 3-5), so this is unconditional, not gated on landing
        // byte-aligned. The discriminator is the whole "type == ques with an EMPTY quest column"
        // family (see isReimaginedQuestItem()), not a code list: it started as an xa1-xa5 "Worldstone
        // Shard" fix (brute-force-confirmed against a real "Deep Worldstone Shard" xa4 in a character's
        // cube -- without these 8 bits its own end came out a byte short and every following item
        // failed; with them the whole 196-item file parsed, the next item a real Small Charm), then a
        // real shared stash's "Key of Destruction" (pk3, a Pandemonium event key) proved the same byte
        // is needed by a different code in the same category: chain-scanned, its body also ended
        // mid-byte (offset 5, identical to the xa4/xa5 shards) and read one byte short until this byte
        // was added, after which the rest of the stash tab -- a run of gems, Orbs and more keys --
        // decoded. Keyed on the category so the rest of that family is covered too: the other two keys
        // (pk1/pk2), the Uber organs (dhn/bey/mbr), the crafting essences (tes/ceh/bet/fed), the Uber
        // Ancient summon/upgrade materials (ua1-ua5/um1-um6) and the Standard of Heroes (std) all share
        // the identical blank-quest misc.txt shape, while the classic quest items that parse fine
        // untouched (Horadric Cube "box", Key to the Cairn Stones "bkd", Khalim's organs, etc.) all
        // carry a non-empty quest value and so are excluded. What these bits hold is still unknown.
        if (isReimaginedQuestItem() && usesPostV99ItemFormat()) {
            pFile.skipBits(8);
        }
        // The Reimagined "grabber"/tool items (misc.txt type "grab": the eight gem Grabbers
        // agr/tgr/sgr/egr/rgr/mgr/kgr/ogr, the three Pandemonium-key Grabbers tkg/hkg/dkg, the Rune
        // Pliers "rup" and Jewel Pliers "jwp") carry one extra trailing byte nothing above reads --
        // but ONLY when flag 28 is set on the item, NOT unconditionally as this rule first assumed.
        // This started life as an unconditional "grab" +8, confirmed against a real shared stash's
        // "Rune Pliers" (rup): its body ended mid-byte (bit offset 2, so it is not the byte-alignment
        // quirk) and read exactly one byte short until the byte was added, after which the rest of
        // that rune/gem tab decoded. A real character then disproved the "unconditional" part: three
        // gem Grabbers sitting loose in its stash -- an Amethyst (agr), a Ruby (rgr) and an Emerald
        // (mgr) Grabber -- each ALSO ended at bit offset 2 and were byte-for-byte the same "grab" type
        // as the pliers in every misc.txt column, yet each needed ZERO trailing bits: with the +8 the
        // agr's own end came out a byte long and every item after it (starting with a "Heaven Facet")
        // failed to decode; without it the whole 108-item file parsed. The one bit that separates them
        // is flag 28 -- set on the +8 Rune Pliers, clear on all three +0 grabbers, and otherwise
        // identical (both quality 2, no sockets, no properties, identified). Flag 28 is a Reimagined-
        // specific bit unused anywhere else in this codebase; it reads like a "this grabber is holding
        // something" marker, the extra byte being that held payload/count. So the rule is keyed on the
        // whole "grab" type (covering the sibling grabbers and pliers) AND gated on flag 28. What the
        // byte actually holds is still unknown.
        if ("grab".equals(iType) && check_flag(28) && usesPostV99ItemFormat()) {
            pFile.skipBits(8);
        }
        // Simple beltable potions sit one padding byte short in the post-v99 format whenever their
        // compact body happens to end exactly on a byte boundary. The generic end-of-item rounding
        // (getNextByteBoundaryInBits, "(pos + 7) & ~7") only advances to the next byte when there
        // are leftover bits; when the body is already a whole number of bytes it stays put, silently
        // dropping that padding byte and desyncing every following item. Which potions this hits is
        // decided purely by body length mod 8: a "Light Healing Potion" ("hp2"), a "Light Mana
        // Potion" ("mp2") and an "Antidote Potion" ("yps") are 72 bits, exactly byte-aligned, so
        // they need +8; hp1/hp3/hp4/hp5/mp1/mp3 carry 1-2 leftover bits and are already correct.
        // Brute-force-confirmed twice, in two independent real files and across two type families:
        // on a character, hp2 needed exactly +8 while three separate mp1s in the same file needed
        // none; and in a real shared stash, three consecutive antidote potions ("apot") each landed
        // byte-aligned here and each needed +8 (without it, the first one's own end came out a byte
        // short and every item after it in the tab failed to load). Keyed on iType (the potion
        // class), not item_type (the specific code), so it covers the whole family the same way.
        // "wpot" (Thawing) and "spot" (Stamina) are the two remaining simple potion classes; no
        // real sample of either has turned up yet, but they share the identical single-cell beltable
        // structure, and because this rule fires only when already byte-aligned it is a no-op for
        // them unless they hit the exact same padding-byte drop. Rejuvenation potions ("rpot":
        // rvl/rvs) are the one potion family that is NOT folded in here: they carry a genuine extra
        // field (handled above, unconditionally) regardless of alignment.
        if (usesPostV99ItemFormat() && isSimpleBeltablePotion(iType)
                && (pFile.get_pos() % 8) == 0) {
            pFile.skipBits(8);
        }
        // A rune or gem sitting loose at the top level (location != 6, i.e. not socketed into
        // another item) needs 8 extra trailing bits nothing else reads -- brute-force-confirmed
        // the same way as the other quirks here, in the same real shared stash, immediately
        // after the "rvs"/"ooi" fixes above (a free rune, then later a free "Ruby" gem). NOT
        // applied when location == 6 (socketed): that case already gets its own +8 from the
        // socket-recursion loop's skip (this file), confirmed via two real runewords (Love,
        // Edge) with actual runes socketed into them -- adding both would double count. Why a
        // loose rune/gem needs this only outside a socket is still unknown.
        // Checks iType directly rather than the iGem field: this Reimagined version's gems have
        // type2 "pgem", not one of the "gem0".."gem4" tiers iGem's own check requires, so iGem is
        // never actually set to true for a real gem here (a separate, pre-existing gap -- not
        // touched here since fixing it could affect other iGem-dependent behavior beyond this).
        boolean isLooseRuneOrGem = (iRune || (iType != null && iType.startsWith("gem"))) && location != 6;
        if (isLooseRuneOrGem && usesPostV99ItemFormat()) {
            pFile.skipBits(8);
            // A loose rune/gem can need one MORE trailing byte on top of the one just above, but only
            // when its body lands exactly on a byte boundary -- the identical dropped-padding-byte
            // quirk the simple potions (see isSimpleBeltablePotion()) and socketed runes/gems (just
            // below) have: the generic end-of-item byte-rounding advances a byte only when there are
            // leftover bits, so a byte-aligned body silently loses that padding byte and desyncs the
            // next item. This is purely an alignment quirk, NOT a location one -- an item's bytes are
            // stored identically wherever it sits (panel is just a field inside the item), which is why
            // the potion and socketed-rune rules are keyed on alignment alone. It first surfaced in the
            // Horadric Cube (panel 4) against a real character with eight runes moved into it (Sur, Zod,
            // Gul, Vex, Ohm, Lo, Jah, Cham): exactly the two that landed byte-aligned here, Sur and Ohm,
            // needed +8 and the other six needed nothing -- matching the alignment rule, not the rune
            // tier (an early guess that "high runes" needed it, or that every cube rune did, was wrong).
            // The panel==4 guard it originally carried was just "only the cube has been seen so far", and
            // a real shared stash then disproved it: a stash (panel 5) rune/gem tab with ten runes and
            // several gems had exactly one byte-aligned gem here -- an Emerald (the runes and the other
            // gems all ended mid-byte) -- and without this byte its own end came out one short and every
            // item after it in the tab (a run of gems and Orbs) failed to load. So the guard is gone;
            // the check is alignment only. What the padding byte holds is still unknown.
            if ((pFile.get_pos() % 8) == 0) {
                pFile.skipBits(8);
            }
        }
        // A rune or gem socketed INTO another item (location == 6) hits the same dropped-padding-byte
        // quirk as the cube case above, but on its own account: when the socketed sub-item's body
        // ends exactly on a byte boundary, the generic end-of-item byte-rounding (getNextByteBoundary,
        // "(pos + 7) & ~7") has nothing to round and so silently drops the trailing padding byte,
        // making the sub-item read one byte short. That desyncs the next socket, or -- for the last
        // socket -- the item that follows the parent. Brute-force-confirmed against a real character:
        // two socketed runeword flails, "Call to Arms" (Amn+Ral+Mal+Ist+Ohm) and "Heart of the Oak"
        // (Ko+Vex+Pul+Thul), each failed to parse the item right after them until their final rune
        // (Ohm, then Thul) -- the only one in each whose body happened to land byte-aligned -- got
        // this byte back; every earlier rune in both, which ended mid-byte and so kept its padding
        // through normal rounding, needed nothing, matching the alignment rule rather than the rune
        // or its position. The loose (location != 6) case is handled separately above; keeping these
        // apart avoids double-counting, since a loose rune already takes its own unconditional +8.
        boolean isSocketedRuneOrGem = (iRune || (iType != null && iType.startsWith("gem"))) && location == 6;
        if (isSocketedRuneOrGem && usesPostV99ItemFormat() && (pFile.get_pos() % 8) == 0) {
            pFile.skipBits(8);
        }
        // Elemental Facets (see isElementalFacet()) carry 48 extra trailing bits that nothing above
        // reads -- BUT only when flag 29 is set, exactly like the non-facet flag-29 blob just below.
        // The 48-bit blob is that same skill blob (facets grant a level-up/death skill), a shorter
        // variant of the 52/56-bit one; it is present iff the item actually carries the flag. This was
        // first confirmed against three real characters' Autumn, Rime and Thunder Facets (each both
        // unsocketed and socketed) -- every one of which had flag 29 set, so the skip read as
        // unconditional -- until a real shared stash turned up two copies of the same unique "Spring
        // Facet" (uid 392) side by side, one with flag 29 SET and one CLEAR: the flag-set copy needed
        // the 48 bits as before, but the flag-clear copy needed none (chain-scanned: with the skip its
        // own end came out 6 bytes long and every item after it in the tab failed to load; without it
        // the next item -- a plain magic jewel -- decoded and the whole pane parsed). So the blob
        // tracks flag 29, not the elemental-facet identity. What those 48 bits hold is still unknown.
        // See the socket-recursion loop's comment (this file) for the other half of this fix.
        if (isElementalFacet() && check_flag(29) && usesPostV99ItemFormat()) {
            pFile.skipBits(48);
        }
        // Flag 29 (never previously checked anywhere in this codebase) is set on every item seen
        // so far that grants a skill in some form -- confirmed across several real characters' full
        // inventories and a real shared stash: every elemental Facet (isElementalFacet()) has it set,
        // and so do a unique ring ("Sling"), unique gauntlets ("Steelrend") and a unique scepter
        // ("Hand of Blessed Light"). Facets get their own, different (48-bit) skip above; the rest
        // carry a trailing blob of 52 bits, or 56 when the item grants an elemental-skill bonus (see
        // hasElementalSkillProperty() for the full evidence and why the amount is keyed on
        // item_elemskill rather than the properties.txt func number). What the blob holds is still
        // unknown; this is a length heuristic, not a decode.
        // Only for items with NO socketed sub-items: when a flag-29 item is actually socketed, these
        // same bits appear BEFORE its sockets instead and are skipped up in the socket loop (see the
        // "Hand of Blessed Light" comment there), so counting them again here would double-skip.
        if (iSocketNrFilled == 0 && check_flag(29) && !isElementalFacet() && usesPostV99ItemFormat()) {
            // The base flag-29 skill blob is 52 bits, or 56 when the item grants an elemental-skill
            // bonus (see hasElementalSkillProperty()). A flag-29 item whose granted skill is a
            // "chance to cast ... on attack" (item_skillonattack, stat 195 -- the "att-skill"
            // property) carries a further 64 bits on top of that. This was found and confirmed
            // against a real Paladin character's unique ring "Opalvein" (uid 415, "15% Chance to cast
            // level 2 Flame Wave on attack"): its body read exactly 64 bits short, desyncing the very
            // next item, and adding these 64 bits was the only offset that let the rest of the file
            // decode -- the next item coming out as a real, recognizable unique ring ("Raven Frost")
            // and the whole 125-item character then reaching a clean, complete load, cross-checked
            // against an independent brute-force chain-scan of the raw bytes that put the true next
            // boundary exactly where +64 lands. Keyed on the stat (item_skillonattack), not the item
            // code, deliberately -- the point is the "cast on attack" grant, not the ring. Crucially
            // this is NOT "any chance-to-cast": the same file carried a "Wisp Projector" and a
            // "Carrion Wind", both also flag-29 rings with a chance-to-cast, but on *striking*
            // (item_skillonhit, stat 198) / *when struck* (item_skillongethit, 201) -- and both
            // decoded correctly at the standard 52 with NO extra bits, so the extra length belongs
            // specifically to the on-attack variant. What these 64 bits hold is still unknown; this
            // stays a length heuristic, not a decode of the blob. Left on the non-socketed path only,
            // matching the base blob just above (a socketed cast-on-attack item, if one can even
            // exist -- rings, the only confirmed carriers, can't be socketed -- would carry its blob
            // before its sockets, like the other flag-29 items; no such sample has turned up).
            pFile.skipBits((hasElementalSkillProperty() ? 56 : 52) + (hasSkillOnAttackProperty() ? 64 : 0));
        }
    }

    // True when this item's own property list carries an item_skillonattack stat (id 195) -- the
    // "chance to cast ... on attack" grant. Read straight off the parsed stats (iProps) rather than
    // the unique/set recipe so it also catches the same grant arriving as a magic/rare affix, and so
    // it reflects what is actually stored in this item. See the flag-29 blob's caller for why the
    // presence of this stat lengthens that blob by 64 bits.
    private boolean hasSkillOnAttackProperty() {
        for (Object o : iProps) {
            if (((D2Prop) o).getPNum() == 195) return true;
        }
        return false;
    }

    private void readExtend1(D2BitReader pFile) throws Exception {
        // extended item
        iSocketNrFilled = (short) pFile.read(3);
        fingerprint = pFile.read(32);
        iFP = "0x" + Integer.toHexString((int) fingerprint);
        ilvl = (short) pFile.read(7);
        quality = (short) pFile.read(4);
        iProps = new D2PropCollection();
        // check variable graphic flag
        gfx_num = -1;
        if (pFile.read(1) == 1) {
            gfx_num = (short) pFile.read(3);
            if (iItemType.get("namestr").compareTo("cm1") == 0) {
                iSmallCharm = true;
                image_file = "invch" + ((gfx_num) * 3 + 1);
            } else if (iItemType.get("namestr").compareTo("cm2") == 0) {
                iLargeCharm = true;
                image_file = "invch" + ((gfx_num) * 3 + 2);
            } else if (iItemType.get("namestr").compareTo("cm3") == 0) {
                iGrandCharm = true;
                image_file = "invch" + ((gfx_num) * 3 + 3);
            } else if (iItemType.get("namestr").compareTo("jew") == 0) {
                iJewel = true;
                image_file = "invjw" + (gfx_num + 1);
            } else {
                image_file += (gfx_num + 1);
            }
        }
        // check class info flag
        if (pFile.read(1) == 1) {
            automod_info = D2TxtFile.AUTOMAGIC.getRow((int) pFile.read(11) - 1);
        }

        // path determined by item quality
        switch (quality) {
            case 1: // low quality item
            {
                short low_quality = (short) pFile.read(3);

                switch (low_quality) {

                    case 0: {
                        iItemName = "Crude " + iItemName;
                        break;
                    }

                    case 1: {
                        iItemName = "Cracked " + iItemName;
                        break;
                    }

                    case 2: {
                        iItemName = "Damaged " + iItemName;
                        break;
                    }

                    case 3: {
                        iItemName = "Low Quality " + iItemName;
                        break;
                    }

                }

                break;
            }
            case 3: // high quality item
            {
                iItemName = "Superior " + iItemName;
                iBaseItemName = iItemName;
                // 3bytes, don't know what they are.
                pFile.read(3);
                break;
            }
            case 4: // magic item
            {
                iMagical = true;
                short magic_prefix = (short) pFile.read(11);
                short magic_suffix = (short) pFile.read(11);

                if (magic_suffix == 0) {
                    magic_suffix = 10000;
                }

                D2TxtFileItemProperties lPrefix = D2TxtFile.PREFIX
                        .getRow(magic_prefix);
                String lPreName = lPrefix.get("Name");
                if (lPreName != null && !lPreName.equals("")) {
                    iItemName = D2Files.getInstance().getTranslations().getTranslation(lPreName) + " " + iItemName;
                    int lPreReq = getReq(lPrefix.get("levelreq"));
                    if (lPreReq > iReqLvl) {
                        iReqLvl = lPreReq;
                    }
                }

                D2TxtFileItemProperties lSuffix = D2TxtFile.SUFFIX
                        .getRow(magic_suffix);
                String lSufName = lSuffix.get("Name");
                if (lSufName != null && !lSufName.equals("")) {
                    iItemName = iItemName + " "
                            + D2Files.getInstance().getTranslations().getTranslation(lSufName);
                    int lSufReq = getReq(lSuffix.get("levelreq"));
                    if (lSufReq > iReqLvl) {
                        iReqLvl = lSufReq;
                    }
                }
                applyAutomodLvl();
                break;
            }
            case 5: // set item
            {
                iSet = true;
                set_id = (short) pFile.read(12);
                if (gfx_num == -1) {
                    String s = (String) iItemType.get("setinvfile");
                    if (s.compareTo("") != 0)
                        image_file = s;
                }

                D2TxtFileItemProperties lSet = D2TxtFile.SETITEMS.searchColumns("*ID", String.valueOf(set_id));
                iSetItemRow = lSet;
                String nameFromSetFile = lSet.get("index");
                String translatedName = D2Files.getInstance().getTranslations().getTranslation(nameFromSetFile);
                iItemName = translatedName == null ? nameFromSetFile : translatedName;
                iSetName = lSet.get("set");

                setSize = (D2TxtFile.SETITEMS.searchColumnsMultipleHits("set",
                        iSetName)).size();

                int lSetReq = getReq(lSet.get("lvl req"));
                if (lSetReq != -1 && lSetReq > iReqLvl) {
                    iReqLvl = lSetReq;
                }

                applyAutomodLvl();
                addSetProperties(D2TxtFile.FULLSET.searchColumns("index", lSet.get("set")));
                break;
            }
            case 7: {
                iUnique = true;
                unique_id = (short) pFile.read(12);
                String s = iItemType.get("uniqueinvfile");
                if (s.compareTo("") != 0) {
                    image_file = s;
                }

                D2TxtFileItemProperties lUnique = D2TxtFile.UNIQUES.searchByID(unique_id);
                if (lUnique == null) break;
                String lNewName = D2Files.getInstance().getTranslations().getTranslation(lUnique.get("index"));
                if (lNewName != null) {
                    iItemName = lNewName;
                }

                if (s.equals("") && !lUnique.get("invfile").equals("")) image_file = lUnique.get("invfile");

                if (lUnique.get("code").equals(item_type)) {
                    int lUniqueReq = getReq(lUnique.get("lvl req"));
                    if (lUniqueReq != -1) {
                        iReqLvl = lUniqueReq;
                    }
                }
                applyAutomodLvl();
                break;
            }
            case 6: // rare item
            {
                iRare = true;
                iItemName = "Rare " + iItemName;
            }
            case 8: // also a rare item, do the same (one's probably crafted)
            {
                if (!iRare) {
                    iCrafted = true;
                    iItemName = "Crafted " + iItemName;
                }
            }

            applyAutomodLvl();
            short rare_name_1 = (short) pFile.read(8);
            short rare_name_2 = (short) pFile.read(8);
            D2TxtFileItemProperties lRareName1 = D2TxtFile.RAREPREFIX
                    .getRow(rare_name_1 - 156);
            D2TxtFileItemProperties lRareName2 = D2TxtFile.RARESUFFIX
                    .getRow(rare_name_2 - 1);
                iItemName = D2Files.getInstance().getTranslations().getTranslation(lRareName1.get("name")) + " "
                        + D2Files.getInstance().getTranslations().getTranslation(lRareName2.get("name"));

            rare_prefixes = new short[3];
            rare_suffixes = new short[3];
            short pre_count = 0;
            short suf_count = 0;
            for (int i = 0; i < 3; i++) {
                if (pFile.read(1) == 1) {
                    rare_prefixes[pre_count] = (short) pFile.read(11);
                    D2TxtFileItemProperties lPrefix = D2TxtFile.PREFIX
                            .getRow(rare_prefixes[pre_count]);
                    pre_count++;
                    String lPreName = lPrefix.get("Name");
                    if (lPreName != null && !lPreName.equals("")) {
                        int lPreReq = getReq(lPrefix.get("levelreq"));
                        if (lPreReq > iReqLvl) {
                            iReqLvl = lPreReq;
                        }
                    }

                }
                if (pFile.read(1) == 1) {
                    rare_suffixes[suf_count] = (short) pFile.read(11);
                    D2TxtFileItemProperties lSuffix = D2TxtFile.SUFFIX
                            .getRow(rare_suffixes[suf_count]);
                    suf_count++;
                    String lSufName = lSuffix.get("Name");
                    if (lSufName != null && !lSufName.equals("")) {
                        int lSufReq = getReq(lSuffix.get("levelreq"));
                        if (lSufReq > iReqLvl) {
                            iReqLvl = lSufReq;
                        }
                    }
                }
            }

            if (isCrafted()) {
                iReqLvl = iReqLvl + 10 + (3 * (suf_count + pre_count));
            }
            break;

            case 2: {
                if (iItemName.contains("Token of Absolution")) iItemName = "Token of Absolution";
                readTypes(pFile);
                break;
            }
        }

        // rune word
        if (check_flag(27)) {
            pFile.skipBits(12);
            pFile.skipBits(4);
        }
        // personalized
        if (check_flag(25)) {
            personalization = "";
            boolean lNotEnded = true;
            for (int i = 0; i < 15 && lNotEnded; i++) {
                char c = (char) pFile.read(8);
                if (c == 0) {
                    lNotEnded = false;
                } else {
                    personalization += c;
                }
            }
            if (lNotEnded == true) {
                pFile.read(8);
            }
        }
    }

    // A threshold-bonus property with no rolled range (only a fixed "apar" param, e.g. a set
    // item's "cold-len" always being exactly 300) still needs a stored base value if its
    // underlying stat computes its real effect from that value at runtime (itemstatcost.txt's
    // "op" column, e.g. "att/lvl" -> item_tohit_perlevel, "+X to Attack Rating per level" --
    // confirmed real via Angelic Halo, where skipping it desynced the next property list).
    // A fixed-param stat with no such computation (no "op") has nothing further to derive at
    // runtime and isn't stored at all -- confirmed real via Death Knight's Demon Blade, where
    // including an (absent) list for "cold-len" desynced everything after it.
    private boolean needsStoredBaseValue(String pPropertyCode) {
        if (pPropertyCode.equals("")) return false;
        D2TxtFileItemProperties propRow = D2TxtFile.PROPS.searchColumns("code", pPropertyCode);
        if (propRow == null) return false;
        String statName = propRow.get("stat1");
        if (statName.equals("")) return false;
        D2TxtFileItemProperties statRow = D2TxtFile.ITEM_STAT_COST.searchColumns("Stat", statName);
        return statRow != null && !statRow.get("op").equals("");
    }

    // Whether a set item's threshold-bonus property list setitems.txt says *could* be here
    // (needsStoredBaseValue's rule, or the caller's own amin/amax check) actually *is* here for
    // this specific saved instance -- see the "quality == 5" block's comment for two real
    // instances of the same item ("The River Stix") needing opposite answers that no static rule
    // predicted. A real property list always opens with a raw 9-bit stat ID, so peeking those 9
    // bits (position is always restored, whichever way this returns) and checking whether they
    // resolve to something real distinguishes "list present" from "list absent" using the actual
    // file instead of a guess: the terminator (511, an empty-but-present list) or a real
    // itemstatcost.txt row with a non-empty "Save Bits" both mean a real list starts here; the id
    // that this fix exists because of -- The River Stix's absent threshold 4 reading as
    // "stamdrainmindam", an in-range id whose "Save Bits" column is empty -- means these bits
    // belong to something else (this item's own trailing bits) and the list itself isn't here.
    private boolean isSetBonusListPresent(D2BitReader pFile) {
        int lSavedPos = pFile.get_pos();
        int lCandidateId = (int) pFile.read(9);
        pFile.set_pos(lSavedPos);
        if (lCandidateId == 511) return true;
        // getRow() never returns null (out-of-range rows come back as an empty-valued wrapper --
        // see D2TxtFile.getValue()'s bounds check), so an invalid id surfaces as an empty
        // "Save Bits" exactly like a real but non-storable stat row would.
        return !D2TxtFile.ITEM_STAT_COST.getRow(lCandidateId).get("Save Bits").equals("");
    }

    // The flag-29 trailing skill blob is 4 bits longer for an item that grants an "elemental skill"
    // bonus -- properties.txt's fireskill/coldskill/lightningskill/poisonskill/magicskill, all of
    // which resolve to the item_elemskill stat -- than for one that only grants named/fixed skills
    // (oskill, aura, single skill, +class skills, skill tab). Both an elemental-skill property and a
    // +class-skills property (item_addclassskills, e.g. "pal"/"dru") happen to share properties.txt
    // func1 == 21 in this mod's data, so keying on func 21 (as this originally did) wrongly gave the
    // extra 4 bits to +class-skills items too. Real examples pin the rule to item_elemskill, not
    // func 21: a unique ring "Sling" (a magicskill/item_elemskill item) needs 56, while a unique
    // scepter "Hand of Blessed Light" (+2 Paladin skills via item_addclassskills, plus single-skill,
    // oskill and chance-to-cast grants, but no item_elemskill) needs 52 -- confirmed three ways from
    // three copies of that scepter in one character (socketed with jewels, with empty sockets, and
    // un-socketed), all of which decoded and let the rest of the file parse only at 52, not 56, and
    // whose trailing blobs are byte-for-byte the same structure regardless of sockets. A unique pair
    // of gauntlets "Steelrend" (item_aura, no item_elemskill) is the other confirmed 52. Checks every
    // property slot this item's recipe (unique or set) could use; uniqueitems.txt goes up to prop12,
    // setitems.txt up to prop9 plus the five threshold slots' "a"/"b" pairs. What the 4 bits hold is
    // still unknown; this stays a length heuristic, not a decode of the blob.
    private boolean hasElementalSkillProperty() {
        D2TxtFileItemProperties recipeRow = iUnique
                ? D2TxtFile.UNIQUES.searchByID(unique_id)
                : (iSet ? iSetItemRow : null);
        if (recipeRow == null) return false;
        for (int x = 1; x <= 12; x++) {
            if (propertyGrantsElementalSkill(recipeRow.get("prop" + x))) return true;
        }
        if (iSet) {
            for (int x = 1; x <= 5; x++) {
                if (propertyGrantsElementalSkill(recipeRow.get("aprop" + x + "a"))) return true;
                if (propertyGrantsElementalSkill(recipeRow.get("aprop" + x + "b"))) return true;
            }
        }
        return false;
    }

    private boolean propertyGrantsElementalSkill(String pPropertyCode) {
        if (pPropertyCode.equals("")) return false;
        D2TxtFileItemProperties propRow = D2TxtFile.PROPS.searchColumns("code", pPropertyCode);
        return propRow != null && "item_elemskill".equals(propRow.get("stat1"));
    }

    private void addSetProperties(D2TxtFileItemProperties fullsetRow) {
        // The caller (readExtend, "case 5") passes FULLSET.searchColumns("index", lSet.get("set"))
        // straight through with no null check -- a set item whose "set" name has no matching
        // sets.txt row (bad/newer-mod data) would NPE the whole item load right here. Not a
        // bit-level read, so safe to guard: this item simply gets no set-wide bonuses (its own
        // per-piece properties, read earlier, are unaffected), rather than the load aborting.
        if (fullsetRow == null) {
            return;
        }

        for (int x = 2; x < 6; x++) {
            if (fullsetRow.get("PCode" + x + "a").equals("")) continue;
            iProps.addAll(D2TxtFile.propToStat(fullsetRow.get("PCode" + x + "a"), fullsetRow.get("PMin" + x + "a"), fullsetRow.get("PMax" + x + "a"), fullsetRow.get("PParam" + x + "a"), (20 + x)));
        }
        for (int x = 1; x < 9; x++) {
            if (fullsetRow.get("FCode" + x).equals("")) continue;
            iProps.addAll(D2TxtFile.propToStat(fullsetRow.get("FCode" + x), fullsetRow.get("FMin" + x), fullsetRow.get("FMax" + x), fullsetRow.get("FParam" + x), 26));
        }
    }

    private void readExtend2(D2BitReader pFile) throws Exception {
        if (isTypeArmor()) {
            iDef = (short) (pFile.read(11) - 10); // -10 ???
            iInitDef = iDef;
            iMaxDur = (short) pFile.read(8);

            if (iMaxDur != 0) {
                iCurDur = (short) pFile.read(9);
            }

        } else if (isTypeWeapon()) {
            if (iType.equals("tkni") || iType.equals("taxe")
                    || iType.equals("jave") || iType.equals("ajav")) {
                iThrow = true;
            }
            iMaxDur = (short) pFile.read(8);

            if (iMaxDur != 0) {
                iCurDur = (short) pFile.read(9);
            }

            if ((D2TxtFile.WEAPONS.searchColumns("code", item_type)).get(
                    "1or2handed").equals("")
                    && !iThrow) {

                if ((D2TxtFile.WEAPONS.searchColumns("code", item_type)).get(
                        "2handed").equals("1")) {
                    iWhichHand = 2;
                    i1Dmg = new short[4];
                    i1Dmg[0] = i1Dmg[1] = Short.parseShort((D2TxtFile.WEAPONS
                            .searchColumns("code", item_type))
                            .get("2handmindam"));
                    i1Dmg[2] = i1Dmg[3] = Short.parseShort((D2TxtFile.WEAPONS
                            .searchColumns("code", item_type))
                            .get("2handmaxdam"));
                } else {
                    iWhichHand = 1;
                    i1Dmg = new short[4];
                    i1Dmg[0] = i1Dmg[1] = Short.parseShort((D2TxtFile.WEAPONS
                            .searchColumns("code", item_type)).get("mindam"));
                    i1Dmg[2] = i1Dmg[3] = Short.parseShort((D2TxtFile.WEAPONS
                            .searchColumns("code", item_type)).get("maxdam"));
                }

            } else {
                iWhichHand = 0;
                if (iThrow) {
                    i2Dmg = new short[4];
                    i2Dmg[0] = i2Dmg[1] = Short
                            .parseShort((D2TxtFile.WEAPONS.searchColumns(
                                    "code", item_type)).get("minmisdam"));
                    i2Dmg[2] = i2Dmg[3] = Short
                            .parseShort((D2TxtFile.WEAPONS.searchColumns(
                                    "code", item_type)).get("maxmisdam"));
                } else {
                    i2Dmg = new short[4];
                    i2Dmg[0] = i2Dmg[1] = Short
                            .parseShort((D2TxtFile.WEAPONS.searchColumns(
                                    "code", item_type)).get("2handmindam"));
                    i2Dmg[2] = i2Dmg[3] = Short
                            .parseShort((D2TxtFile.WEAPONS.searchColumns(
                                    "code", item_type)).get("2handmaxdam"));
                }
                i1Dmg = new short[4];
                i1Dmg[0] = i1Dmg[1] = Short.parseShort((D2TxtFile.WEAPONS
                        .searchColumns("code", item_type)).get("mindam"));
                i1Dmg[2] = i1Dmg[3] = Short.parseShort((D2TxtFile.WEAPONS
                        .searchColumns("code", item_type)).get("maxdam"));
            }

            if ("1".equals(iItemType.get("stackable"))) {
                iStackable = true;
                iCurDur = (short) pFile.read(9);
            }
        } else if (isTypeMisc()) {
            if ("1".equals(iItemType.get("stackable"))) {
                iStackable = true;
                iCurDur = (short) pFile.read(9);
            }

        }

        // Current D2R (item format version drift alongside the .d2s header changes elsewhere in
        // this fork -- version 105 confirmed -- not yet documented anywhere public, including
        // D2CE's, whose newest documented item version is "v140"/Patch 2.5) inserts extra bits
        // around the socket-count field and the magical property list that don't exist in any
        // prior format. Confirmed: applying any of these unconditionally to every item --
        // including the pre-v100 fixtures elsewhere in this codebase -- broke a known-good
        // version-99 shared-stash file, so all of them are gated on file version. The exact
        // layout, derived empirically from three real version-105 characters (a Barbarian, a
        // Druid, and an Amazon mule, ~70 items total spanning socketed/ethereal/unique/runeword/
        // charm/stackable/simple types) and cross-checked against real item data (defense/
        // durability against base item stats in armor.txt/weapons.txt, decoded property values
        // against fixed ranges in uniqueitems.txt, the player's own in-game socket count, and --
        // for the two runeword items in the Amazon mule -- every line of their actual in-game
        // tooltips) is:
        //   - socketed items get one extra bit immediately before the socket-count field, and
        //     one more immediately after the property list.
        //   - non-socketed items get one extra bit immediately before the property list instead.
        //   - every item -- socketed or not -- gets one further extra bit after the property
        //     list, on top of whichever of the above it already got. Ethereal items get one
        //     additional bit beyond that, also after the property list.
        // "Doesn't throw" was repeatedly not enough evidence that a guess here was right -- wrong
        // guesses often produced a plausible-but-wrong value (e.g. a socket count that
        // coincidentally equaled the item's max, or a durability that happened to coincide with
        // an unrelated real mechanic) and only surfaced once the *next* item's data came out as
        // garbage. Every bit below was confirmed by checking decoded values against real data,
        // not just by checking for an exception.
        if (iSocketed && usesPostV99ItemFormat()) {
            pFile.skipBits(1);
        }
        if (iSocketed) {
            iSocketNrTotal = (short) pFile.read(4);
        }

        int[] lSet = new int[5];

        if (quality == 5) {
            for (int x = 0; x < 5; x++) {
                lSet[x] = (int) pFile.read(1);
            }
        }

        if (usesPostV99ItemFormat() && !iSocketed) {
            pFile.skipBits(1);
        }
        if (iJewel) {
            readProperties(pFile, 1);
        } else {
            readProperties(pFile, 0);
        }
        // A runeword's own bonus properties (e.g. Edge's Thorns aura, +skills, etc.) are a
        // second property list, stored back-to-back with the item's own list -- immediately
        // after its terminator, with none of the trailing bits below in between. Confirmed
        // against a real runeword bow (Edge: Tir+Tal+Amn) by reading every line of its in-game
        // tooltip and matching each one to its underlying stat (strength/energy/dexterity/
        // vitality for "+9 to all Attributes", item_reducedprices, item_fasterattackrate,
        // item_preventheal, item_demondamage_percent, item_undeaddamage_percent, item_aura for
        // the Thorns aura) -- putting the trailing bits before this list, as an earlier version
        // of this fix did, decoded plausible-looking but real-data-mismatched values (e.g. a
        // dexterity bonus of +50 from a bow, an unrelated "Attacker Takes Damage" stat) that
        // didn't throw and were only caught by checking against the player's actual tooltip.
        if (iRuneWord) {
            readProperties(pFile, 0);
        }
        // Same shape as the runeword fix just above: a set item's per-threshold bonus
        // properties are additional property lists, read back-to-back with everything above --
        // before the trailing bits, not after. In the current (post-v99) format, how many of
        // these lists are present has nothing to do with the lSet flags just read above: those
        // track which thresholds are *currently active* (i.e. how many pieces of the set the
        // player has on right now), which can go up and down as gear changes, but the bonus
        // values themselves -- once rolled -- are stored permanently regardless of whether
        // they're presently contributing. The number of stored lists instead matches the number
        // of threshold slots (1 through 5, "a" and "b" each) that actually roll a random value
        // for this specific set item in setitems.txt -- confirmed against three real set items:
        // Immortal King's Stone Crusher (lSet all five thresholds: 0,1,1,1,1 -- i.e. missing the
        // *lowest* one -- but all five thresholds roll a value, and reading five lists, not
        // four, was required), Ebony Plate of Evil (lSet 0,0,1,1,0 -- two thresholds active --
        // but only two thresholds (2 and 3) roll a value, and reading exactly those two, not
        // four, was required), and Death Knight's Demon Blade (three thresholds have a property
        // at all, but the middle one, "cold-len", only ever sets a fixed value -- apar2a, no
        // amin2a/amax2a -- nothing to roll, so nothing was stored for it; treating it the same
        // as the other two and reading three lists decoded plausible-looking but wrong values
        // for the third, eventually hitting a stat with no "Save Bits" at all that can only
        // appear in the file from a misread position like this). All three contradict "read one
        // list per active lSet flag", "read up to the highest active flag", and "one list per
        // threshold with any property at all" -- only "one list per threshold that rolls a
        // value" fits all three.
        // Even that rule has an exception no static txt-driven guess can predict: setitems.txt
        // marking a threshold as roll-capable (an amin/amax pair, or an op-based apar) does not
        // guarantee THIS item instance ever actually rolled it -- two real copies of a D2RMM set
        // ring, "The River Stix" (Hades' Underworld, its only roll-capable threshold being 4,
        // "nofreeze"), needed opposite answers: one had nothing stored for threshold 4 at all
        // (reading it read into the next item's bits and desynced the mercenary's next item), the
        // other did have it stored -- and neither copy's lSet flags (both examples above, plus a
        // real set item with EVERY threshold's own lSet flag clear yet its highest-rolling
        // threshold still stored -- Janis' Gloves, threshold 5, "str") predict which. So rather
        // than guess further from static data, each roll-capable threshold's list is confirmed
        // against the bitstream itself before being read: a real property list always opens with
        // a raw 9-bit stat ID, so isSetBonusListPresent() peeks those 9 bits (restoring position
        // either way) and checks whether they resolve to the list terminator (511) or a real,
        // Save-Bits-bearing itemstatcost.txt row; if instead they're a stray value that resolves
        // to nothing real (this fix's own reason for existing: reading The River Stix's absent
        // threshold 4 first manifested as exactly this -- an in-range but Save-Bits-less stat ID,
        // "stamdrainmindam"), the list is treated as absent and left untouched for whatever
        // actually follows (this item's own trailing bits).
        // A real v99 shared-stash fixture (predating this discovery, from issue #1) breaks under
        // even the setitems.txt-driven rule -- it stores bonus lists only for thresholds the lSet
        // flags actually mark active, same as this code always assumed before now -- so the old
        // behavior is kept for anything not confirmed to be on the current format.
        if (quality == 5) {
            if (usesPostV99ItemFormat() && iSetItemRow != null) {
                for (int x = 1; x <= 5; x++) {
                    boolean rollsAValue = !iSetItemRow.get("amin" + x + "a").equals("")
                            || !iSetItemRow.get("amin" + x + "b").equals("")
                            || needsStoredBaseValue(iSetItemRow.get("aprop" + x + "a"))
                            || needsStoredBaseValue(iSetItemRow.get("aprop" + x + "b"));
                    if (rollsAValue && isSetBonusListPresent(pFile)) {
                        readProperties(pFile, x + 1);
                    }
                }
            } else {
                for (int x = 0; x < 5; x++) {
                    if (lSet[x] == 1) {
                        readProperties(pFile, x + 2);
                    }
                }
            }
        }
        // This single trailing bit was previously modeled as three separate ones -- one if
        // iSocketed, one unconditional, one if iEthereal -- on the theory that a real ethereal
        // socketed runeword (a mercenary's "Wyrmhide") needing only 1 bit instead of the naively-
        // summed 3 meant the two flags' bits "collapse" into each other specifically when BOTH
        // are true (an XOR of the two flags, in other words), while every previously-validated
        // single-flag case (Edge/Blasthammer for socketed-only, Arreat's Face/Spectral Slayer for
        // ethereal-only) still needed 2. That theory was wrong: every one of those single-flag
        // validations had only ever been checked by confirming the *next* item still decoded,
        // which D2Item's automatic round-up to the next byte boundary can mask a 1-bit error
        // under -- and in every one of those cases, it was. A real unique armor in a shared stash
        // ("Adamantine Mail", socketed but not ethereal) exposed the same 1-bit error landing on
        // a byte boundary that didn't absorb it: brute-force scanning its real next item (a real
        // unique armor, "Red Dragon Scales") found exactly 1 bit fewer than the old
        // "socketed-only = 2 bits" rule gives. The real
        // rule is just this one always-present bit, regardless of either flag; re-confirmed
        // against the full existing test suite (including the ethereal+socketed Wyrmhide case
        // above) with no regressions.
        if (usesPostV99ItemFormat()) {
            pFile.skipBits(1);
        }
    }

    private void applyAutomodLvl() {
        // modifies the level if the automod is higher
        if (automod_info == null) {
            return;
        }
        if (Integer.parseInt(automod_info.get("levelreq")) > iReqLvl) {
            iReqLvl = Integer.parseInt(automod_info.get("levelreq"));
        }

    }

    // MBR: unknown, but should be according to file format
    private void readTypes(D2BitReader pFile) {
        // charms ??
        if (isCharm()) {
//			long lCharm1 = pFile.read(1);
            pFile.read(1);
//			long lCharm2 = pFile.read(11);
            pFile.read(11);
            // System.err.println("Charm (?): " + lCharm1 );
            // System.err.println("Charm (?): " + lCharm2 );
        }

        // books / scrolls ??
        if ("tbk".equals(item_type) || "ibk".equals(item_type)) {
//			long lTomb = pFile.read(5);
            pFile.read(5);
            // System.err.println("Tome ID: " + lTomb );
        }

        if ("tsc".equals(item_type) || "isc".equals(item_type)) {
//			long lTomb = pFile.read(5);
            pFile.read(5);
            // System.err.println("Tome ID: " + lTomb );
        }

        // body ??
        if ("body".equals(item_type)) {
//			long lMonster = pFile.read(10);
            pFile.read(10);
            // System.err.println("Monster ID: " + lMonster );
        }
    }

    private void readPropertiesPots(D2BitReader pfile) {

        String[] statsToRead = {"stat1", "stat2"};

        for (int x = 0; x < statsToRead.length; x = x + 1) {

            if ((D2TxtFile.MISC.searchColumns("code", item_type)).get(
                    statsToRead[x]).equals(""))
                continue;

            Integer statValue = Ints.tryParse(D2TxtFile.MISC.searchColumns("code", item_type).get(statsToRead[x].replaceFirst("stat", "calc")));
            if (statValue == null)
            {
                statValue = 0;
            }

            iProps.add(new D2Prop(Integer.parseInt((D2TxtFile.ITEM_STAT_COST.searchColumns("Stat", (D2TxtFile.MISC
                    .searchColumns("code", item_type))
                    .get(statsToRead[x]))).get("*ID")),
                    new int[]{ statValue }, 0));
        }
    }

    private void readPropertiesGems() {
//		RUNES ARE GEMS TOO!!!!
        String[][] gemHeaders = {{"weaponMod1", "weaponMod2", "weaponMod3"},
                {"helmMod1", "helmMod2", "helmMod3"},
                {"shieldMod1", "shieldMod2", "shieldMod3"}};

        for (int x = 0; x < gemHeaders.length; x++) {

            for (int y = 0; y < gemHeaders[x].length; y++) {

                if (D2TxtFile.GEMS.searchColumns("code", item_type).get(
                        gemHeaders[x][y] + "Code").equals(""))
                    continue;
                iProps.addAll(D2TxtFile.propToStat(D2TxtFile.GEMS
                        .searchColumns("code", item_type).get(
                                gemHeaders[x][y] + "Code"), D2TxtFile.GEMS
                        .searchColumns("code", item_type).get(
                                gemHeaders[x][y] + "Min"), D2TxtFile.GEMS
                        .searchColumns("code", item_type).get(
                                gemHeaders[x][y] + "Max"), D2TxtFile.GEMS
                        .searchColumns("code", item_type).get(
                                gemHeaders[x][y] + "Param"), (x + 7)));
            }
        }
    }

    private void readProperties(D2BitReader pFile, int qFlag) {

        int rootProp = (int) pFile.read(9);

        while (rootProp != 511) {

            iProps.readProp(pFile, rootProp, qFlag);

            if (rootProp == 17) {
                iProps.readProp(pFile, 18, qFlag);
            } else if (rootProp == 48) {
                iProps.readProp(pFile, 49, qFlag);
            } else if (rootProp == 50) {
                iProps.readProp(pFile, 51, qFlag);
            } else if (rootProp == 52) {
                iProps.readProp(pFile, 53, qFlag);
            } else if (rootProp == 54) {
                iProps.readProp(pFile, 55, qFlag);
                iProps.readProp(pFile, 56, qFlag);
            } else if (rootProp == 57) {
                iProps.readProp(pFile, 58, qFlag);
                iProps.readProp(pFile, 59, qFlag);
            }
            rootProp = (int) pFile.read(9);
        }

    }

    private void applyItemMods() {

        int[] armourTriple = new int[]{0, 0, 0};
        int[] dmgTriple = new int[]{0, 0, 0, 0, 0};
        int[] durTriple = new int[]{0, 0};
        RequirementModifierAccumulator requirementModifierAccumulator = new RequirementModifierAccumulator();

        iProps.applyOp(iCharLvl);

        for (int x = 0; x < iProps.size(); x++) {
            if (((D2Prop) iProps.get(x)).getQFlag() != 0 && ((D2Prop) iProps.get(x)).getQFlag() != 12 && ((D2Prop) iProps.get(x)).getQFlag() != 13 && ((D2Prop) iProps.get(x)).getQFlag() != 14 && ((D2Prop) iProps.get(x)).getQFlag() != 15 && ((D2Prop) iProps.get(x)).getQFlag() != 16)
                continue;

            // +Dur
            if (((D2Prop) iProps.get(x)).getPNum() == 73) {
                durTriple[0] = durTriple[0]
                        + ((D2Prop) iProps.get(x)).getPVals()[0];
            }

            // Dur%
            if (((D2Prop) iProps.get(x)).getPNum() == 75) {
                durTriple[1] = durTriple[1]
                        + ((D2Prop) iProps.get(x)).getPVals()[0];
            }

            // +LvlReq
            if (((D2Prop) iProps.get(x)).getPNum() == 92) {
                requirementModifierAccumulator.accumulateLevelRequirement(((D2Prop) iProps.get(x)).getPVals()[0]);
            }

            // -Req
            if (((D2Prop) iProps.get(x)).getPNum() == 91) {
                requirementModifierAccumulator.accumulatePercentRequirements(((D2Prop) iProps.get(x)).getPVals()[0]);
            }

            // +Skills modify level
            if (((D2Prop) iProps.get(x)).getPNum() == 97
                    || ((D2Prop) iProps.get(x)).getPNum() == 107) {

                D2TxtFileItemProperties skillsRow = D2TxtFile.SKILLS.searchColumns(
                        "skilldesc",
                        D2TxtFile.SKILL_DESC.getRow(
                                ((D2Prop) iProps.get(x)).getPVals()[0]).get(
                                "skilldesc"));
                String reqlevel = skillsRow.get("reqlevel");
                try {
                    if (iReqLvl < Integer.parseInt(reqlevel)) {
                        iReqLvl = (Integer.parseInt(reqlevel));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse level req number for " + skillsRow.get("skill"));
                }
            }

            if (isTypeArmor()) {

                // EDef
                if (((D2Prop) iProps.get(x)).getPNum() == 16) {
                    armourTriple[0] = armourTriple[0]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                }

                // +Def
                if (((D2Prop) iProps.get(x)).getPNum() == 31) {
                    armourTriple[1] = armourTriple[1]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                }

                // +Def/lvl
                if (((D2Prop) iProps.get(x)).getPNum() == 214) {
                    armourTriple[2] = armourTriple[2]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                }

                if (isShield()) {
                    if (((D2Prop) iProps.get(x)).getPNum() == 20) {
                        iBlock = (short) (cBlock + ((D2Prop) iProps.get(x))
                                .getPVals()[0]);
                    }
                }

            } else if (isTypeWeapon()) {

                // EDmg
                if (((D2Prop) iProps.get(x)).getPNum() == 17) {
                    dmgTriple[0] = dmgTriple[0]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                }

                // MinDMg
                if (((D2Prop) iProps.get(x)).getPNum() == 21) {
                    dmgTriple[1] = dmgTriple[1]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                    if (((D2Prop) iProps.get(x)).getFuncN() == 31) {
                        dmgTriple[2] = dmgTriple[2]
                                + ((D2Prop) iProps.get(x)).getPVals()[1];
                    }
                }

                // MaxDmg
                if (((D2Prop) iProps.get(x)).getPNum() == 22) {
                    dmgTriple[2] = dmgTriple[2]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                }

                // MaxDmg/Lvl
                if (((D2Prop) iProps.get(x)).getPNum() == 218) {
                    dmgTriple[3] = dmgTriple[3]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                }

                // MaxDmg%/lvl
                if (((D2Prop) iProps.get(x)).getPNum() == 219) {
                    dmgTriple[4] = dmgTriple[4]
                            + ((D2Prop) iProps.get(x)).getPVals()[0];
                }
            }
        }

        iReqLvl = iReqLvl + requirementModifierAccumulator.getLevelRequirement();
        double percentReqirementsModifier = requirementModifierAccumulator.getPercentRequirements() / (double) 100;
        iReqDex = iReqDex + ((int) (iReqDex * percentReqirementsModifier));
        iReqStr = iReqStr + ((int) (iReqStr * percentReqirementsModifier));

        if (isTypeWeapon()) {

            if (iEthereal) {
                i1Dmg[0] = i1Dmg[1] = (short) Math
                        .floor((((double) i1Dmg[1] / (double) 100) * (double) 50)
                                + i1Dmg[1]);
                i1Dmg[2] = i1Dmg[3] = (short) Math
                        .floor((((double) i1Dmg[3] / (double) 100) * (double) 50)
                                + i1Dmg[3]);

                if (iWhichHand == 0) {
                    i2Dmg[0] = i2Dmg[1] = (short) Math
                            .floor((((double) i2Dmg[1] / (double) 100) * (double) 50)
                                    + i2Dmg[1]);
                    i2Dmg[2] = i2Dmg[3] = (short) Math
                            .floor((((double) i2Dmg[3] / (double) 100) * (double) 50)
                                    + i2Dmg[3]);
                }
            }

            i1Dmg[1] = (short) Math
                    .floor((((double) i1Dmg[1] / (double) 100) * dmgTriple[0])
                            + (i1Dmg[1] + dmgTriple[1]));
            i1Dmg[3] = (short) Math
                    .floor((((double) i1Dmg[3] / (double) 100) * (dmgTriple[0] + dmgTriple[4]))
                            + (i1Dmg[3] + (dmgTriple[2] + dmgTriple[3])));

            if (iWhichHand == 0) {
                i2Dmg[1] = (short) Math.floor((((double) i2Dmg[1] / (double) 100) * dmgTriple[0])
                        + (i2Dmg[1] + dmgTriple[1]));
                i2Dmg[3] = (short) Math.floor((((double) i2Dmg[3] / (double) 100) * (dmgTriple[0] + dmgTriple[4]))
                        + (i2Dmg[3] + (dmgTriple[2] + dmgTriple[3])));
            }
            if (i1Dmg[1] > i1Dmg[3]) {
                i1Dmg[3] = (short) (i1Dmg[1] + 1);
            }


        } else if (isTypeArmor()) {
            iDef = (short) Math
                    .floor((((double) iInitDef / (double) 100) * armourTriple[0])
                            + (iInitDef + (armourTriple[1] + armourTriple[2])));
        }

        iMaxDur = (short) Math
                .floor((((double) iMaxDur / (double) 100) * durTriple[1])
                        + (iMaxDur + durTriple[0]));


    }

    private boolean check_flag(int bit) {
        if (((flags >>> (32 - bit)) & 1) == 1)
            return true;
        else
            return false;
    }

    private int getReq(String pReq) {
        if (pReq != null) {
            String lReq = pReq.trim();
            if (!lReq.equals("") && !lReq.equals("0")) {
                try {
                    return Integer.parseInt(lReq);
                } catch (Exception pEx) {
                    // do nothing, no req
                }
            }
        }
        return -1;
    }

    private String getExStr() {
        return " (" + iItemName + ", " + iFP + ")";
    }

    private boolean isBodyLocation(String pLocation) {
        if (iBody) {
            if (pLocation.equals(iBodyLoc1)) {
                return true;
            }
            if (pLocation.equals(iBodyLoc2)) {
                return true;
            }
        }
        return false;
    }

    public void toWriter(PrintWriter pw) {
        pw.println();
        pw.print(D2ItemRenderer.itemDump(this, true));
    }

    public boolean isBodyLArm() {
        return isBodyLocation("larm");
    }

    public boolean isBodyRRin() {
        return isBodyLocation("rrin");
    }

    public boolean isBodyLRin() {
        return isBodyLocation("lrin");
    }

    public boolean isWeaponType(D2WeaponTypes pType) {
        if (iTypeWeapon) {
            if (pType.isType(iType)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBodyLocation(D2BodyLocations pLocation) {
        if (iBody) {
            if (pLocation.getLocation().equals(iBodyLoc1)) {
                return true;
            }
            if (pLocation.getLocation().equals(iBodyLoc2)) {
                return true;
            }
        }
        return false;
    }


    public boolean isBelt() {
        return iBelt;
    }

    public boolean isCharm() {
        return (iSmallCharm || iLargeCharm || iGrandCharm);
    }

    public boolean isCharmSmall() {
        return iSmallCharm;
    }

    public boolean isCharmLarge() {
        return iLargeCharm;
    }

    public boolean isCharmGrand() {
        return iGrandCharm;
    }

    public boolean isJewel() {
        return iJewel;
    }

    // accessor for the row
    public short get_row() {
        return row;
    }

    // setter for the row
    // necessary for moving items
    public void set_row(short r) {
        iItem.set_byte_pos(4);
        iItem.skipBits(14);
        iItem.write((long) r, 4);
        row = r;
    }

    // accessor for the column
    public short get_col() {
        return col;
    }

    // setter for the column
    // necessary for moving items
    public void set_col(short c) {
        iItem.set_byte_pos(4);
        iItem.skipBits(10);
        iItem.write((long) c, 4);
        col = c;
    }

    public short get_location() {
        return location;
    }

    public void set_location(short l) {
        iItem.set_byte_pos(4);
        iItem.skipBits(3);
        iItem.write((long) l, 3);
        location = l;
    }

    public short get_body_position() {
        return body_position;
    }

    public void set_body_position(short bp) {
        iItem.set_byte_pos(4);
        iItem.skipBits(6);
        iItem.write((long) bp, 4);
        body_position = bp;
    }

    public short get_panel() {
        return panel;
    }

    public void set_panel(short p) {
        iItem.set_byte_pos(4);
        iItem.skipBits(18);
        iItem.write((long) p, 3);
        panel = p;
    }

    public short get_width() {
        return width;
    }

    public short get_height() {
        return height;
    }

    public String get_image() {
        return image_file;
    }

    public short getSetID() {
        return set_id;
    }

    public short getUniqueID() {
        return unique_id;
    }

    /**
     * The runes.txt "Name" column of this item's resolved runeword (e.g. "Insight"), or null if
     * this item is not a runeword (isRuneWord() false) or the runeword lookup found no match.
     */
    public String getRuneWordIndex() {
        return iRuneWordIndex;
    }

    public String get_version() {

        if (version == 0) {
            return "Legacy (pre 1.08)";
        }

        if (version == 1) {
            return "Classic";
        }
//2 is another version perhaps?
        if (version == 100) {
            return "Expansion";
        }

        if (version == 101) {
            return "Expansion 1.10+";
        }
//		System.out.println(version);

        if (version == 9999) {
            return "Resurrected";
        }

        return "UNKNOWN";
    }

    public long getSocketNrFilled() {
        return iSocketNrFilled;
    }

    public long getSocketNrTotal() {
        return iSocketNrTotal;
    }

    public byte[] get_bytes() {
        return iItem.getFileContent();
    }

    public int getItemLength() {
        return iItem.get_length();
    }

    public String getItemName() {
        return iItemName;
    }

    public String getName() {
        return iItemName;
    }

    public String getFingerprint() {
        return iFP;
    }

    public short getIlvl() {
        return ilvl;
    }

    public int getReqLvl() {
        return iReqLvl;
    }

    public int getReqStr() {
        return iReqStr;
    }

    public int getReqDex() {
        return iReqDex;
    }

    public Color getItemColor() {
        if (isUnique()) {
            // return Color.yellow.darker().darker();
            return new Color(255, 222, 173);
        }
        if (isSet()) {
            return Color.green.darker();
        }
        if (isRare()) {
            return Color.yellow.brighter();
        }
        if (isMagical()) {
            return new Color(72, 118, 255);
        }
        if (isRune()) {
            return Color.orange;
        }
        if (isCrafted()) {
            return Color.orange;
        }
        if (isRuneWord()) {
            return new Color(255, 222, 173);
        }
        if (isEthereal() || isSocketed()) {
            return Color.gray;
        }
        return Color.white;
    }

    public boolean isUnique() {
        return iUnique;
    }

    public boolean isSet() {
        return iSet;
    }

    public boolean isRuneWord() {
        return iRuneWord;
    }

    public boolean isCrafted() {
        return iCrafted;
    }

    public boolean isRare() {
        return iRare;
    }

    public boolean isMagical() {
        return iMagical;
    }

    public boolean isShield() {
        if (iType != null) {
            if (iType.equals("ashd") || iType.equals("shie")
                    || iType.equals("head")) {
                return true;
            }
        }
        return false;
    }

    public boolean isNormal() {
        return !(iMagical || iRare || iCrafted || iRuneWord || isRune() || iSet || iUnique);
    }

    public boolean isSocketFiller() {
        return isRune() || isJewel() || isGem();
    }

    public boolean isGem() {
        return iGem;
    }

    public boolean isRune() {
        return getRuneCode() != null;
    }

    public String getRuneCode() {
        if (iItemType != null) {
            if ("rune".equals(iItemType.get("type"))) {
                return iItemType.get("code");
            }
        }
        return null;
    }

    public boolean isEthereal() {
        return iEthereal;
    }

    public boolean isSocketed() {
        return iSocketed;
    }

    public boolean isStackable() {
        return iStackable;
    }

    public boolean isTypeMisc() {
        return iTypeMisc;
    }

    public boolean isTypeArmor() {
        return iTypeArmor;
    }

    public boolean isTypeWeapon() {
        return iTypeWeapon;
    }

    public boolean isCursorItem() {
        if (location != 0 && location != 2) {
            if (body_position == 0) {
                // System.err.println("location: " + location );
                return true;
            }
        }
        return false;
    }

    public int compareTo(Object pObject) {
        if (pObject instanceof D2Item) {
            String lItemName = ((D2Item) pObject).iItemName;
            if (iItemName == lItemName) {
                // also both "null"
                return 0;
            }
            if (iItemName == null) {
                return -1;
            }
            if (lItemName == null) {
                return 1;
            }
            return iItemName.compareTo(lItemName);
        }
        return -1;
    }

    public int getiDef() {
        return (int) iDef;
    }

    public boolean isCharacterItem() {

        //Belt or equipped
        if (get_location() == 1 || get_location() == 2) {
            return true;
        } else if (get_location() == 0) {
            switch (get_panel()) {
                case 1:
                case 4:
                case 5:
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }

    }

    public boolean isEquipped() {

        if (get_location() == 1) {
            return true;
        } else if (get_panel() == 1 && isCharm()) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isEquipped(int wepSlot) {

        if (get_location() == 1) {

            if (!isTypeWeapon() && !isShield()) return true;
            if (wepSlot == 0) {
                if (get_body_position() == 4 || get_body_position() == 5) return true;
            } else if (wepSlot == 1) {
                if (get_body_position() == 11 || get_body_position() == 12) return true;
            }
            return false;
        } else if (get_panel() == 1 && isCharm()) {
            return true;
        } else {
            return false;
        }
    }

    public int getSetSize() {
        return setSize;
    }

    public String getSetName() {
        return iSetName;
    }

    public boolean statModding() {

        if (iJewel || iGem || iRune) {
            return false;
        } else {
            return true;
        }
    }

    public void setCharLvl(int pCharLvl) {
        iCharLvl = pCharLvl;
    }

    public String getPreSuf() {

        String retStr = "";
        for (int x = 0; x < rare_prefixes.length; x++) {

            if (rare_prefixes[x] > 1) {

                retStr = retStr
                        + D2Files.getInstance()
                                .getTranslations()
                                .getTranslation(D2TxtFile.PREFIX
                                        .getRow(rare_prefixes[x])
                                        .get("Name"))
                        + " ";
            }
        }

        retStr = retStr + iBaseItemName + " ";

        for (int x = 0; x < rare_suffixes.length; x++) {

            if (rare_suffixes[x] > 1) {

                retStr = retStr
                        + D2Files.getInstance()
                                .getTranslations()
                                .getTranslation(D2TxtFile.SUFFIX
                                        .getRow(rare_suffixes[x])
                                        .get("Name"))
                        + " ";
            }
        }

        return retStr;
    }

    public boolean conforms(String prop, int pVal, boolean min) {
        String dumpStr = D2ItemRenderer.itemDump(this, true);
        if (dumpStr.toLowerCase().contains(prop.toLowerCase())) {
            if (pVal == -1337) {
                return true;
            }
            Pattern propertyLinePattern = Pattern.compile("(\\n.*" + prop.toLowerCase() + ".*\\n)", Pattern.UNIX_LINES);
            Matcher propertyPatternMatcher = propertyLinePattern.matcher("\n" + dumpStr.toLowerCase() + "\n");
            while (propertyPatternMatcher.find()) {
                Pattern pat = Pattern.compile("[^\\(?](\\d+)");
                Matcher mat = pat.matcher(propertyPatternMatcher.group());
                while (mat.find()) {
                    if (mat.groupCount() > 0) {
                        if (min == true) {
                            if (Integer.parseInt(mat.group(1)) >= pVal) {

                                return true;
                            }
                        } else {
                            if (Integer.parseInt(mat.group(1)) <= pVal) {

                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public int getBlock() {
        return (int) this.cBlock;
    }

    public boolean isABelt() {
        if (iType.equals("belt")) {
            return true;
        } else {
            return false;
        }
    }

    public D2PropCollection getPropCollection() {
        return iProps;
    }

    public String getItemQuality() {
        return iItemQuality;
    }

    public short getQuality() {
        return quality;
    }

    public String getFileName() {
        return iFileName;
    }

    public boolean isCharacter() {
        return iIsChar;
    }

    public void refreshItemMods() {
        if (isTypeArmor() || isTypeWeapon()) {
            applyItemMods();
        }
    }

    public String getBaseItemName() {
        return iBaseItemName;
    }

    public boolean isMoveable() {

        if (get_location() == 0 && get_panel() == 1 && (getName().toLowerCase().equals("horadric cube") || isCharm() || getName().toLowerCase().equals("key") || getName().toLowerCase().indexOf("tome of") != -1)) {
            //Inv
        } else if (get_location() == 2) {
            //Belt
        } else if (get_location() == 0 && get_panel() == 5 && getName().toLowerCase().equals("horadric cube")) {
            //Stash
        } else if (get_location() == 1) {
            //equipped
        } else {
            return true;
        }
        return false;
    }

    public boolean isQuestItem() {
        return questItem;
    }

    public String getPersonalization() {
        return personalization;
    }

    public ArrayList<D2Item> getiSocketedItems() {
        return iSocketedItems;
    }

    public int getiWhichHand() {
        return iWhichHand;
    }

    public boolean isiThrow() {
        return iThrow;
    }

    public short[] getI1Dmg() {
        return i1Dmg;
    }

    public short[] getI2Dmg() {
        return i2Dmg;
    }

    public short getiBlock() {
        return iBlock;
    }

    public short getiCurDur() {
        return iCurDur;
    }

    public short getiMaxDur() {
        return iMaxDur;
    }

    public String getiGUID() {
        return iGUID;
    }

    public boolean isiIdentified() {
        return iIdentified;
    }

    public int getiCharLvl() {
        return iCharLvl;
    }

    public String getItem_type() { return item_type; }
}
