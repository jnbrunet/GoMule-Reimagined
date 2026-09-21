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
    /**
     * propertygroups.txt -- a D2R-era table with no Diablo II classic counterpart, which is why it
     * was missing from ./d2111 and from this list entirely. A uniqueitems.txt (or magicprefix.txt)
     * "propN" column may name a row of THIS table instead of a properties.txt code, in which case
     * the item rolls exactly ONE of that group's Prop1..Prop8 entries; the referencing row's
     * minN/maxN is then the pick COUNT (1, or blank for a group that is listed but never actually
     * rolls), not a value range. Nothing in the item-parsing path needs it -- a found item stores whatever it actually
     * rolled as plain stats in its own bitstream -- but the Holy Grail's tooltip for an item nobody
     * has found yet has only the tables to go on, and used to drop every one of these codes
     * silently (propToStat finds no properties.txt row for them and returns nothing).
     * <p>
     * The real case this was found on: "Renewed Flame Rift" (uniqueitems.txt index "Crafted Flame
     * Rift") references Incendiary-Affix1..6 across prop3..prop8 and rendered nothing at all beyond
     * its two fixed sunder properties. Eight uniqueitems.txt rows use groups in total (the six
     * "Crafted" sunder charms, plus Wraithstep and Opalvein); setitems.txt, sets.txt, runes.txt,
     * gems.txt and automagic.txt use none, which is why only the grail's unique tooltip path reads
     * this table.
     */
    public static D2TxtFile PROPERTY_GROUPS;
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
        PROPERTY_GROUPS = new D2TxtFile("propertygroups");
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

            // Eleven properties.txt codes hold an ID in "par" rather than a number, and none of
            // them fits the plain (min, max, par) triple the generic code below assumes:
            //
            //   code                                       par      min                 max
            //   att/hit/gethit/kill/death/levelup-skill     Skill    % Chance (0 -> 5)   Skill Level
            //   charged                                    Skill    # of Max Charges    Skill Level
            //   skill / oskill / aura                      Skill    Min level           Max level
            //   skilltab                                   Tab id   Min level           Max level
            //
            // Two separate things went wrong on all of them, and either one alone silenced the line
            // completely:
            //   - "par" holds a skill NAME in every uniqueitems.txt/setitems.txt/sets.txt/runes.txt
            //     row that uses one of these codes -- e.g. Schaefer's Hammer's prop1 "hit-skill"
            //     par="Static Field", or the runeword Insight's "aura" par="Meditation" -- not a
            //     number, so the pParam parse below threw NumberFormatException and the whole call
            //     returned an EMPTY list: no D2Prop, no tooltip line at all. (Only magicsuffix.txt
            //     and "skilltab" spell the param as a raw number, so both are accepted -- see
            //     resolveSkillId.)
            //   - even given a numeric param, the generic path zeroes pVals[2] and never fills
            //     pVals[1] at all, while D2Prop's own renderers for these stats read the layouts
            //     D2PropCollection.readProp() builds from a real item's bitstream: [skill level,
            //     skill id, chance] for the six skill-on-event stats (its dedicated 195/196/197/
            //     198/199/201 branch, descfunc 15), [skill level, skill id, charges, max charges]
            //     for item_charged_skill (its 204 branch, descfunc 24), and the generic
            //     "Save Param Bits" [id, value] pair for the rest (descfunc 14/16/27/28). So the
            //     pieces have to be placed by hand, per stat, not left to the generic assignment
            //     below.
            //
            // Confirmed against the mod's own item pages: Schaefer's Hammer was missing exactly one
            // line versus its page, "10% Chance to cast level 10 Static Field on striking" (prop1
            // "hit-skill", par="Static Field", min=10, max=10), and the runeword Insight was missing
            // "Level 12-17 Meditation Aura When Equipped" -- the entire point of that runeword.
            if (x == 1 && parNamesASkill(propsRow)) {
                addSkillParamProps(outArr, propsRow, pMin, pMax, pParam, qFlag);
                break;
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
            } else if (pMin.equals("") && pMax.equals("") && pVals[2] != 0) {
                // The per-level branch above is one instance of a wider convention the tables use:
                // a property with NOTHING in its min/max columns stores its single fixed value in
                // "par" instead. Two more families do it without being per-level, and both used to
                // resolve to a flat 0 because pVals[2] is unconditionally zeroed just below:
                //   - "rep-dur" (item_replenish_durability, 66 slots across ./d2111 -- e.g. the
                //     runeword "Exile", T1Code7="rep-dur" par=25). Its renderer is descfunc 11,
                //     "Repairs 1 Durability in 100/value Seconds", so a 0 did not merely print
                //     wrong, it threw ArithmeticException out of D2Prop.generateDisplay and blanked
                //     the entire tooltip it appeared in.
                //   - "rep-quant" (item_replenish_quantity, 107 slots -- every throwing weapon's
                //     "Replenishes quantity"), plus one-off "sock" (par=2/3) and "att" (par=25)
                //     slots, all of which simply rendered as 0.
                // Checked BEFORE the "max"/"length" branches for the same reason the per-level one
                // is (see its comment): those two match on the STAT NAME, so a blank-min/max stat
                // whose name happens to contain "max" would otherwise be handed pVals[1] -- the
                // blank max, i.e. 0 -- instead of the value that is actually there. "cold-len" (the
                // one real blank-min/max user of the "length" branch, par=300) reaches this branch
                // first now and gets the identical pVals[0] = par assignment, so its output is
                // unchanged.
                pVals[0] = pVals[2];
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

    /**
     * True when properties.txt's own "*Parameter" column says this code's "par" holds an id rather
     * than a number: "Skill" (the ten skill-granting codes) or "Class Skill Tab ID" ("skilltab").
     * <p>
     * Keyed on that column rather than on a list of code names so the rule is the table's own
     * statement about its data, and so it separates the look-alikes exactly: "skill-rand" is
     * excluded (its *Parameter is "Skill Level" -- the ids live in its MIN/MAX columns instead, and
     * its existing rendering must not change), and so is "oskill_hide", whose *Parameter is blank
     * even though real rows do put a skill name there. That last exclusion is deliberate and
     * load-bearing: oskill_hide is the internal, never-displayed skill grant
     * (D2PropCollection.isHiddenSkillGrant), so it must keep resolving to nothing at all here.
     */
    private static boolean parNamesASkill(D2TxtFileItemProperties pPropsRow) {
        String lParameter = pPropsRow.get("*Parameter");
        return "Skill".equals(lParameter) || "Class Skill Tab ID".equals(lParameter);
    }

    /**
     * Adds one D2Prop per stat of a par-holds-an-id property (see {@link #parNamesASkill}), each in
     * the pVals layout D2Prop.generateDisplay() expects for that particular stat -- the same layout
     * D2PropCollection.readProp() produces for a found item, so a table-sourced tooltip line and a
     * real item's line render through identical code:
     * <ul>
     *   <li>the six "*-skill" stats (descfunc 15): {skill level, skill id, % chance}. The level is
     *   the "max" column and the chance is the "min" column -- the two are NOT the ends of one
     *   range (properties.txt's own uiRangeType 7 says so), and a 0 or blank chance means 5, per
     *   that same row's "*Min" note "% Chance (If 0, then default to 5)".</li>
     *   <li>"charged" (descfunc 24): {skill level, skill id, charges, max charges}. Its "min"
     *   column is the max-charge count, and an item nobody has found yet is shown at full charges,
     *   so that one number fills both slots -- e.g. Spellsteel's "Level 10 Holy Bolt (100/100
     *   Charges)".</li>
     *   <li>everything else -- item_aura, item_singleskill, item_nonclassskill(_display),
     *   item_addskill_tab (descfunc 16/27/28/14) -- the generic {id, value} pair readProp() builds
     *   for any stat with a "Save Param Bits" column, where the value is the skill/tab LEVEL. The
     *   max column is that level when present (it is the top of the roll, which is also what the
     *   pre-fix code displayed for these), falling back to min when there is no max.</li>
     * </ul>
     * One property can carry several stats and they are all emitted: "oskill" is stat1
     * item_nonclassskill_display + stat2 item_nonclassskill, and BOTH are needed -- a 97 with no
     * matching 387 carrying the same skill id is what D2PropCollection.isHiddenSkillGrant treats as
     * an internal, invisible grant.
     * <p>
     * Adds nothing at all -- never throws, never a half-built prop -- when the skill cannot be
     * resolved or a present column is non-numeric, matching how the rest of propToStat degrades on
     * data it cannot use.
     */
    private static void addSkillParamProps(ArrayList pOut, D2TxtFileItemProperties pPropsRow,
                                            String pMin, String pMax, String pParam, int qFlag) {
        int lSkillId = resolveSkillId(pParam);
        if (lSkillId < 0) {
            return;
        }
        int lMin;
        int lMax;
        try {
            lMin = (pMin == null || pMin.isEmpty()) ? 0 : Integer.parseInt(pMin);
            lMax = (pMax == null || pMax.isEmpty()) ? 0 : Integer.parseInt(pMax);
        } catch (NumberFormatException pEx) {
            return;
        }
        int lLevel = (pMax == null || pMax.isEmpty()) ? lMin : lMax;

        for (int x = 1; x < 8; x++) {
            String lStatCode = pPropsRow.get("stat" + x);
            if (lStatCode == null || lStatCode.isEmpty()) {
                break;
            }
            D2TxtFileItemProperties lStatRow = ITEM_STAT_COST.searchColumns("Stat", lStatCode);
            if (lStatRow == null) {
                continue;
            }
            int lStatId;
            try {
                lStatId = Integer.parseInt(lStatRow.get("*ID"));
            } catch (NumberFormatException pEx) {
                continue;
            }
            if (isSkillOnEventStat(lStatCode)) {
                pOut.add(new D2Prop(lStatId, new int[]{lMax, lSkillId, lMin == 0 ? 5 : lMin}, qFlag));
            } else if ("item_charged_skill".equals(lStatCode)) {
                pOut.add(new D2Prop(lStatId, new int[]{lMax, lSkillId, lMin, lMin}, qFlag));
            } else if ("item_addskill_tab".equals(lStatCode)) {
                // properties.txt's "Class Skill Tab ID" and the id stat 188 actually STORES are two
                // different numberings, and only the stored one is what D2Prop.getSkillTree()
                // decodes (see its own comment): the table numbers the game's skill tabs
                // sequentially, 0-20 for the seven vanilla classes plus 21-23 for the mod's
                // Warlock, three per class in class order; an item stores the global
                // "class * 8 + tab position" index, which is why getSkillTree's cases run
                // 0/1/2, 8/9/10, 16/17/18, ... 56/57/58. Converting here rather than teaching
                // getSkillTree a second numbering keeps the found-item path -- which already feeds
                // it the stored index -- untouched. Confirmed against the runeword "Exile", whose
                // T1Param5 is 10: 10/3 = class 3 (Paladin), 10%3 = tab 1, so 3*8+1 = 25 =
                // "Offensive Aura Skills (Paladin Only)", matching the runeword's real bonus. Left
                // unconverted (and so rendering as getSkillTree's own "Unknown Tree" fallback)
                // rather than guessed at if the table id is negative.
                int lTabId = lSkillId < 0 ? lSkillId : (lSkillId / 3) * 8 + (lSkillId % 3);
                pOut.add(new D2Prop(lStatId, new int[]{lTabId, lLevel}, qFlag));
            } else {
                pOut.add(new D2Prop(lStatId, new int[]{lSkillId, lLevel}, qFlag));
            }
        }
    }

    /**
     * The six stats behind properties.txt's skill-on-event codes ("att-skill"/"hit-skill"/
     * "gethit-skill"/"kill-skill"/"death-skill"/"levelup-skill" -> item_skillonattack/onhit/
     * ongethit/onkill/ondeath/onlevelup, stat ids 195/198/201/196/197/199) -- exactly the ids
     * D2PropCollection.readProp() special-cases when reading the same properties off a real item's
     * bitstream. Keyed on the STAT name rather than the properties.txt code so a mod adding another
     * code on top of one of these stats is covered automatically.
     */
    private static boolean isSkillOnEventStat(String pStatCode) {
        return "item_skillonattack".equals(pStatCode)
                || "item_skillonhit".equals(pStatCode)
                || "item_skillongethit".equals(pStatCode)
                || "item_skillonkill".equals(pStatCode)
                || "item_skillondeath".equals(pStatCode)
                || "item_skillonlevelup".equals(pStatCode);
    }

    /**
     * Resolves a property slot's "par" column to a skills.txt skill id. The tables spell it two
     * ways, and both are real: uniqueitems.txt/setitems.txt/sets.txt/runes.txt use the skill's NAME
     * (skills.txt's own "skill" column -- "Static Field", "Blessed Hammer", and mod-added ones like
     * "Storm Pulse" or "Winters Pulse"), while magicsuffix.txt uses the raw numeric id. Anything
     * else -- blank, or a name with no skills.txt row -- returns -1 ("no such skill"), which drops
     * only that one property line.
     * <p>
     * The name lookup returns skills.txt's own "*Id" column, which is what D2Prop's renderers feed
     * straight back to SKILLS.getRow(): safe because skills.txt is indexed BY that id -- verified
     * row-by-row against ./d2111's skills.txt, where row N is *Id N throughout (it carries no
     * "Expansion" separator row of the kind that shifts uniqueitems.txt/setitems.txt).
     */
    private static int resolveSkillId(String pParam) {
        if (pParam == null || pParam.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(pParam);
        } catch (NumberFormatException pNotANumericId) {
            // Falls through to the name lookup below -- the spelling used by every table except
            // magicsuffix.txt.
        }
        D2TxtFileItemProperties lSkillRow = SKILLS.searchColumns("skill", pParam);
        if (lSkillRow == null) {
            return -1;
        }
        try {
            return Integer.parseInt(lSkillRow.get("*Id"));
        } catch (NumberFormatException pEx) {
            return -1;
        }
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