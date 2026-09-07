/*******************************************************************************
 *
 * Copyright 2007 Randall & Silospen
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
package randall.d2files;

import gomule.gui.D2FileManager;
import gomule.item.D2Prop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * @author Marco
 * <p>
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public final class D2TxtFile {
    public static D2TxtFile MISC;
    public static D2TxtFile ARMOR;
    public static D2TxtFile WEAPONS;
    public static D2TxtFile UNIQUES;
    public static D2TxtFile SETITEMS;
    public static D2TxtFile PREFIX;
    public static D2TxtFile SUFFIX;
    public static D2TxtFile RAREPREFIX;
    public static D2TxtFile RARESUFFIX;
    public static D2TxtFile RUNES;
    public static D2TxtFile ITEM_TYPES;
    public static D2TxtFile ITEM_STAT_COST;
    public static D2TxtFile SKILL_DESC;
    public static D2TxtFile SKILLS;
    public static D2TxtFile GEMS;
    public static D2TxtFile PROPS;
    public static D2TxtFile HIRE;
    public static D2TxtFile FULLSET;
    public static D2TxtFile CHARSTATS;
    public static D2TxtFile AUTOMAGIC;
    //	/**
//	* DROP CALC
//	*/
    public static D2TxtFile MONSTATS;
    public static D2TxtFile TCS;
    public static D2TxtFile LEVELS;
    public static D2TxtFile SUPUNIQ;
    public static D2TxtFile ITEMRATIO;
    private static String sMod;
    private static boolean read = false;
    private String iFileName;
    private String[] iHeader;
    private String[][] iData;


    private D2TxtFile(String pFileName) {
        iFileName = pFileName;

    }

    public static void constructTxtFiles(String pMod) {

        if (read) return;
        sMod = pMod;
        MISC = new D2TxtFile("misc");
        ARMOR = new D2TxtFile("armor");
        WEAPONS = new D2TxtFile("weapons");
        UNIQUES = new D2TxtFile("uniqueitems");
        SETITEMS = new D2TxtFile("setitems");
        PREFIX = new D2TxtFile("magicprefix");
        SUFFIX = new D2TxtFile("magicsuffix");
        RAREPREFIX = new D2TxtFile("rareprefix");
        RARESUFFIX = new D2TxtFile("raresuffix");
        RUNES = new D2TxtFile("runes");
        ITEM_TYPES = new D2TxtFile("itemtypes");
        ITEM_STAT_COST = new D2TxtFile("itemstatcost");
        SKILL_DESC = new D2TxtFile("skilldesc");
        SKILLS = new D2TxtFile("skills");
        GEMS = new D2TxtFile("gems");
        PROPS = new D2TxtFile("properties");
        MONSTATS = new D2TxtFile("monstats");
        TCS = new D2TxtFile("treasureclassex");
        LEVELS = new D2TxtFile("levels");
        SUPUNIQ = new D2TxtFile("superuniques");
        HIRE = new D2TxtFile("hireling");
        FULLSET = new D2TxtFile("sets");
        CHARSTATS = new D2TxtFile("charstats");
        AUTOMAGIC = new D2TxtFile("automagic");
        ITEMRATIO = new D2TxtFile("itemRatio");

        read = true;
    }

    public static String getCharacterCode(int pChar) {
        switch (pChar) {
            case 0:
                return "Amazon";
            case 1:
                return "Sorceress";
            case 2:
                return "Necromancer";
            case 3:
                return "Paladin";
            case 4:
                return "Barbarian";
            case 5:
                return "Druid";
            case 6:
                return "Assassin";
            case 7:
                return "Warlock";
        }
        return "<none>";
    }

    public static ArrayList propToStat(String pCode, String pMin, String pMax, String pParam, int qFlag) {

        ArrayList outArr = new ArrayList();
        for (int x = 1; x < 8; x++) {

            D2TxtFileItemProperties propsRow = D2TxtFile.PROPS.searchColumns("code", pCode);
            if (propsRow == null) {
                // pCode itself has no row in properties.txt at all -- not merely a blank "stat1"
                // on a row that exists (that case is handled below), but the code being wholly
                // unknown to the loaded tables. This is a real, reachable case, not theoretical:
                // a save or mod build newer than the ./d2111 tables loaded can reference a
                // properties.txt code we don't have, and D2Item.addSetProperties (the item-load
                // path, not just this grail-tooltip path) calls propToStat with no try/catch at
                // all -- an unguarded NPE here would abort loading the whole item, not just blank
                // one tooltip line. Degrade the same way the empty-stat1/no-rescue "break" below
                // does: stop and return whatever props were already resolved (empty on the first
                // iteration), rather than throwing.
                break;
            }
            String propsStatCode = propsRow.get("stat" + x);
            if (propsStatCode.equals("")) {
                // A handful of properties.txt codes carry no "stat1" column at all -- their stat is
                // implied by the "func" column instead, a modding convention the data itself uses
                // rather than naming the stat directly (func 5/6/7/20, respectively, for the four
                // rescued below). "dmg%" was the only one of these ever rescued before this; the
                // other three silently produced zero D2Props -- and therefore no line at all -- for
                // every item that used them (uniqueitems.txt/setitems.txt/sets.txt reference these
                // codes ~1056 times total; a real example this fix restores: the unique caduceus
                // "Wrath of the Seraphim" (*ID 618) has prop2="dmg-max" min=100 max=200, "+100-200
                // to Maximum Weapon Damage" on the mod's own site, which GoMule showed nothing for
                // at all). Guarded to x==1 -- exactly like the single dmg% case it replaces -- so an
                // empty stat2..stat7 (a property that simply has fewer than 7 stats) still ends the
                // loop normally instead of being mistaken for one of these.
                //
                // Verified against every row in ./d2111's properties.txt with an empty stat1: each
                // of func 5/6/7/20 is used by exactly one such code, so keying on the literal code
                // (as the single pre-existing dmg% case already did) is exactly as safe as keying on
                // func would be here, and needs no extra column lookup or int parse.
                //
                // "ethereal" (func 23, also stat1-empty) is deliberately NOT rescued: there is no
                // itemstatcost.txt stat for it at all, searched for and confirmed absent -- ethereal
                // is a plain item flag, not a stat with a value, so there is nothing to synthesize a
                // D2Prop from. A found item's real "Ethereal" line comes from D2Item.isEthereal()
                // (a bitstream flag) via D2ItemRenderer's own dedicated rendering, entirely separate
                // from this property-list pipeline; a missing entry's synthesized tooltip has no
                // instance to read that flag from, so it simply has no "Ethereal" line, matching an
                // item flag having no numeric value to synthesize in the first place.
                if (x == 1 && pCode.equals("dmg%")) {
                    propsStatCode = "item_maxdamage_percent";
                } else if (x == 1 && pCode.equals("dmg-min")) {
                    propsStatCode = "mindamage";
                } else if (x == 1 && pCode.equals("dmg-max")) {
                    propsStatCode = "maxdamage";
                } else if (x == 1 && pCode.equals("indestruct")) {
                    propsStatCode = "item_indesctructible"; // sic -- itemstatcost.txt's own spelling
                } else {
                    break;
                }
            }

            int[] pVals = {0, 0, 0};

            if (!pMin.equals("")) {
                try {
                    pVals[0] = Integer.parseInt(pMin);
                } catch (NumberFormatException e) {
                    return outArr;
                }
            }
            ;

            if (!pMax.equals("")) {
                try {
                    pVals[1] = Integer.parseInt(pMax);
                } catch (NumberFormatException e) {
                    return outArr;
                }
            }
            ;

            if (!pParam.equals("")) {
                try {
                    pVals[2] = Integer.parseInt(pParam);
                } catch (NumberFormatException e) {
                    return outArr;
                }
            }
            ;

            if (propsStatCode.endsWith("_perlevel") && pMin.equals("") && pMax.equals("")) {
                // A per-level property (properties.txt code ends in "/lvl", resolving to an
                // itemstatcost.txt "item_*_perlevel" stat -- e.g. "hp/lvl" -> "item_hp_perlevel",
                // "att/lvl" -> "item_tohit_perlevel") whose min/max are both blank stores its
                // fixed value in "par" instead -- confirmed against real data: uniqueitems.txt's
                // Harlequin Crest has prop2="hp/lvl" min="" max="" par="12", and setitems.txt's
                // Cleglaw's Pincers has aprop1a="att/lvl" min="" max="" par="20" (13 sets.txt
                // FCode/PCode entries across ./d2111 share this exact shape -- e.g. Civerb's
                // Vestments FCode4="att/lvl" FParam="16"). Before this, pVals[2] (par) was always
                // zeroed out unconditionally two lines below unless the stat name contained "max"
                // or "length", so every one of these rendered as a flat "+0" regardless of level.
                //
                // Checked BEFORE "max"/"length" below, not just alongside them as an else-if:
                // seven of these per-level stats (item_maxdamage_perlevel,
                // item_maxdamage_percent_perlevel, item_{cold,fire,ltng,pois,magic}_damagemax_
                // perlevel -- e.g. setitems.txt's real "Civerb's Cudgel", aprop1a="dmg/lvl"
                // apar1a="12" amin1a="" amax1a="") happen to contain the substring "max" in their
                // *name* purely by coincidence of English, which used to make the (rightly still
                // untouched) "max" branch below claim them first -- taking the blank "amax" (0)
                // instead of "par", right back to a flat "+0". The both-blank min/max guard is what
                // makes checking this first safe: an ordinary "max" stat (e.g. "dmg%" ->
                // "item_maxdamage_percent") always has real min/max data, so it can never satisfy
                // this branch's condition and falls through to the "max" branch exactly as before.
                //
                // No extra scaling is applied here -- this only places the right raw value where
                // D2Prop.applyOp() (NOT generateDisplay(), whose cLvl parameter is never actually
                // read) already knows how to divide it by the correct stat-specific divisor (2 for
                // item_tohit_perlevel, 8 for most others including item_maxdamage_perlevel, per
                // itemstatcost.txt's own "op"/"op param" columns) and multiply by character level
                // -- that math was already correct for a save's bitstream-read value, since
                // D2Item.applyItemMods() already calls applyOp() on every real item's whole
                // property collection. Verified end to end: the real "Cleglaw's Pincers" in
                // charFiles/pally9.d2s (a level-85 character) renders its bitstream-read att/lvl
                // bonus as "+850" (20 * 85 / 2); feeding this same "par"=20 through propToStat,
                // then the same tidy()/applyOp(85)/generateDisplay(0, 85) sequence a caller with no
                // D2Item of its own has to run itself, produces the identical "+850 to Attack
                // Rating (Based on Character Level)" -- see D2PropToStatPerLevelTest.
                if (pVals[2] != 0) {
                    pVals[0] = pVals[2];
                }
            } else if (propsStatCode.indexOf("max") != -1) {
                pVals[0] = pVals[1];
            } else if (propsStatCode.indexOf("length") != -1) {
                if (pVals[2] != 0) {
                    pVals[0] = pVals[2];
                }
            }
            pVals[2] = 0;

            if (propsStatCode.equals("item_addclassskills")) {
                // Reuse propsRow rather than re-querying searchColumns("code", pCode): it is the
                // same row keyed on the same pCode, already proven non-null by the guard above,
                // so there is nothing new here that could return null.
                pVals[0] = Integer.parseInt(propsRow.get("val1"));
            }

            outArr.add(new D2Prop(Integer.parseInt(D2TxtFile.ITEM_STAT_COST.searchColumns("Stat", propsStatCode).get("*ID")), pVals, qFlag));

        }
        return outArr;

    }

    public static D2TxtFileItemProperties search(String pCode) {
        D2TxtFileItemProperties lFound = MISC.searchColumns("code", pCode);
        if (lFound == null) {
            lFound = ARMOR.searchColumns("code", pCode);
        }
        if (lFound == null) {
            lFound = WEAPONS.searchColumns("code", pCode);
        }
        return lFound;
    }

    public String getFileName() {
        return iFileName;
    }

    public int getRowSize() {
        if (iData == null) {
            readInData();
        }
        return iData.length;
    }

    private void readInData() {
        try {
            ArrayList strArr = new ArrayList();
            FileReader lFileIn = new FileReader(sMod + File.separator + iFileName + ".txt");
            BufferedReader lIn = new BufferedReader(lFileIn);
            String lFirstLine = lIn.readLine();

            Pattern p = Pattern.compile("	");
            iHeader = p.split(lFirstLine);
            String lLine = lIn.readLine();

            boolean lSkipExpansion = "UniqueItems".equals(iFileName) || "SetItems".equals(iFileName);
            while (lLine != null) {
                String[] lineArr = p.split(lLine);
                if (lineArr.length > 0 && lSkipExpansion && lineArr[0].equals("Expansion")) {

                } else {
//					iData.add(lSplit);
                    strArr.add(lineArr);
                }
                lLine = lIn.readLine();
            }

            lFileIn.close();
            lIn.close();

            iData = new String[strArr.size()][];
            strArr.toArray(iData);

        } catch (Exception pEx) {
            D2FileManager.displayErrorDialog(pEx);
        }
    }

    protected String getValue(int pRowNr, String pCol) {
        int lColNr = getCol(pCol);

        if (lColNr != -1 && pRowNr < iData.length && iData[pRowNr].length > lColNr) {
            return iData[pRowNr][lColNr];
        }

        return "";
    }

    private int getCol(String col) {

        if (iData == null) {
            readInData();
        }

        for (int x = 0; x < iHeader.length; x++) {
            if (iHeader[x].equals(col)) {
                return x;
            }
        }

        return -1;
    }

    public D2TxtFileItemProperties getRow(int pRowNr) {
        return new D2TxtFileItemProperties(this, pRowNr);
    }

    public D2TxtFileItemProperties searchColumns(String pCol, String pText) {

        int lColNr = getCol(pCol);

        if (lColNr != -1) {
            for (int i = 0; i < iData.length; i++) {
                if (iData[i].length - 1 >= lColNr) {
                    if (iData[i][lColNr].equals(pText)) {
                        return new D2TxtFileItemProperties(this, i);
                    }
                }
            }
        }

        return null;
    }

    public D2TxtFileItemProperties searchByID(int id) {
        if (iData == null) {
            readInData();
        }

        if (id >= iData.length)
            return null;

        for (int i=0; i < iData.length; i++) {
            if (iData[i][0].equals("Expansion")) {
                if (id < i)
                    return new D2TxtFileItemProperties(this, id);

                if (id + 1 < iData.length)
                    return new D2TxtFileItemProperties(this, id + 1);

                return null;
            }
        }

        return null;
    }

    public ArrayList searchColumnsMultipleHits(String pCol, String pText) {
        ArrayList hits = new ArrayList();
        int lColNr = getCol(pCol);

        if (lColNr != -1) {
            for (int i = 0; i < iData.length; i++) {
                if (iData[i].length - 1 >= lColNr) {
                    if (iData[i][lColNr].equals(pText)) {
                        hits.add(new D2TxtFileItemProperties(this, i));
                    }
                }
            }
        }

        return hits;
    }

    public D2TxtFileItemProperties searchRuneWord(ArrayList pList) {
        int lRuneNr[] = new int[]{getCol("Rune1"), getCol("Rune2"), getCol("Rune3"), getCol("Rune4"), getCol("Rune5"), getCol("Rune6")};
        for (int i = 0; i < iData.length; i++) {
            ArrayList lRW = new ArrayList();
            for (int j = 0; j < lRuneNr.length; j++) {
                String lFile = iData[i][lRuneNr[j]];

                if (lFile != null && !lFile.equals("")) {
                    lRW.add(lFile);
                } else {
                    break;
                }
            }

            if (pList.size() == lRW.size()) {
                boolean lIsRuneWord = true;
                for (int j = 0; j < pList.size() && lIsRuneWord; j++) {
                    if (!((String) lRW.get(j)).equals((String) pList.get(j))) {
                        lIsRuneWord = false;
                    }
                }
                if (lIsRuneWord) {
                    return new D2TxtFileItemProperties(this, i);
                }
            }
        }
        return null;
    }
}