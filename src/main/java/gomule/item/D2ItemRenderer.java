package gomule.item;

import java.awt.*;
import java.util.ArrayList;

public class D2ItemRenderer {

    public static String stripColorCodes(String text) {
        return text.replaceAll("ÿc.", "");
    }

    public static String itemDumpHtml(D2Item d2Item, boolean extended) {
        return stripColorCodes(generatePropString(d2Item, extended).toString());
    }

    public static String itemDump(D2Item d2Item, boolean extended) {
        return stripColorCodes(htmlStrip(generatePropString(d2Item, extended)));
    }

    private static String htmlStrip(StringBuilder htmlString) {
        String dumpStr = htmlString.toString().replaceAll("<br>&#10;", System.getProperty("line.separator"));
        dumpStr = dumpStr.replaceAll("&#32;", "");
        return dumpStr.replaceAll("<[^>]*>", "");
    }

    private static StringBuilder generatePropString(D2Item d2Item, boolean extended) {
        StringBuilder propString = new StringBuilder("<html>");
        propString.append(generatePropStringNoHtmlTags(d2Item, extended));
        return propString.append("</html>");
    }

    public static String stripItemName(String itemName) {
        String result = itemName.replaceAll("\\n.*", "");
        result = result.replaceAll("\\*", "");
        return result;
    }

    private static StringBuilder generatePropStringNoHtmlTags(D2Item d2Item, boolean extended) {
        d2Item.getPropCollection().tidy();
        StringBuilder dispStr = new StringBuilder("<center>");
        String base = (Integer.toHexString(Color.white.getRGB())).substring(2);
        String rgb = (Integer.toHexString(d2Item.getItemColor().getRGB())).substring(2);
        String iItemName = stripItemName(d2Item.getItemName());
        if (d2Item.getPersonalization() == null) {
            dispStr.append("<font color=\"#")
                    .append(base)
                    .append("\">")
                    .append("<font color=\"#")
                    .append(rgb)
                    .append("\">")
                    .append(iItemName)
                    .append("</font>")
                    .append("<br>&#10;");
        } else {
            dispStr.append("<font color=\"#")
                    .append(base)
                    .append("\">")
                    .append("<font color=\"#")
                    .append(rgb)
                    .append("\">")
                    .append(d2Item.getPersonalization())
                    .append("'s ")
                    .append(iItemName)
                    .append("</font>")
                    .append("<br>&#10;");
        }
        String iBaseItemName = stripItemName(d2Item.getBaseItemName());
        ArrayList<D2Item> iSocketedItems = d2Item.getiSocketedItems();
        if (!iBaseItemName.equals(iItemName))
            dispStr.append("<font color=\"#")
                    .append(rgb)
                    .append("\">")
                    .append(iBaseItemName)
                    .append("</font>")
                    .append("<br>&#10;");
        if (d2Item.isRuneWord()) {
            dispStr.append("<font color=\"#").append(rgb).append("\">");
            for (D2Item iSocketedItem : iSocketedItems) {
                dispStr.append(iSocketedItem.getName(), 0, iSocketedItem.getName().indexOf(' '));
            }
            dispStr.append("</font><br>&#10;");
        }
        if (d2Item.isTypeWeapon() || d2Item.isTypeArmor()) {
            if (d2Item.isTypeWeapon()) {
                int iWhichHand = d2Item.getiWhichHand();
                short[] i1Dmg = d2Item.getI1Dmg();
                short[] i2Dmg = d2Item.getI2Dmg();
                if (iWhichHand == 0) {
                    if (d2Item.isiThrow()) {
                        dispStr.append("Throw Damage: ")
                                .append(i2Dmg[1])
                                .append(" - ")
                                .append(i2Dmg[3])
                                .append("<br>&#10;");
                        dispStr.append("One Hand Damage: ")
                                .append(i1Dmg[1])
                                .append(" - ")
                                .append(i1Dmg[3])
                                .append("<br>&#10;");
                    } else {
                        dispStr.append("One Hand Damage: ")
                                .append(i1Dmg[1])
                                .append(" - ")
                                .append(i1Dmg[3])
                                .append("<br>&#10;");
                        dispStr.append("Two Hand Damage: ")
                                .append(i2Dmg[1])
                                .append(" - ")
                                .append(i2Dmg[3])
                                .append("<br>&#10;");
                    }
                } else if (iWhichHand == 1) {
                    dispStr.append("One Hand Damage: ")
                            .append(i1Dmg[1])
                            .append(" - ")
                            .append(i1Dmg[3])
                            .append("<br>&#10;");
                } else {
                    dispStr.append("Two Hand Damage: ")
                            .append(i1Dmg[1])
                            .append(" - ")
                            .append(i1Dmg[3])
                            .append("<br>&#10;");
                }
            } else if (d2Item.isTypeArmor()) {
                dispStr.append("Defense: ").append(d2Item.getiDef()).append("<br>&#10;");
                if (d2Item.isShield()) {
                    dispStr.append("Chance to Block: ")
                            .append(d2Item.getiBlock())
                            .append("<br>&#10;");
                }
            }
            if (d2Item.isStackable()) {
                dispStr.append("Quantity: ").append(d2Item.getiCurDur()).append("<br>&#10;");
            } else {
                if (d2Item.getiMaxDur() == 0) {
                    dispStr.append("Indestructible" + "<br>&#10;");
                } else {
                    dispStr.append("Durability: ")
                            .append(d2Item.getiCurDur())
                            .append(" of ")
                            .append(d2Item.getiMaxDur())
                            .append("<br>&#10;");
                }
            }
        }
        if (d2Item.getReqLvl() > 0)
            dispStr.append("Required Level: ").append(d2Item.getReqLvl()).append("<br>&#10;");
        if (d2Item.getReqStr() > 0)
            dispStr.append("Required Strength: ").append(d2Item.getReqStr()).append("<br>&#10;");
        if (d2Item.getReqDex() > 0)
            dispStr.append("Required Dexterity: ").append(d2Item.getReqDex()).append("<br>&#10;");
        if (d2Item.getFingerprint() != null)
            dispStr.append("Fingerprint: ").append(d2Item.getFingerprint()).append("<br>&#10;");
        if (d2Item.getiGUID() != null)
            dispStr.append("GUID: ").append(d2Item.getiGUID()).append("<br>&#10;");
        if (d2Item.getIlvl() != 0)
            dispStr.append("Item Level: ").append(d2Item.getIlvl()).append("<br>&#10;");
        dispStr.append("Version: ").append(d2Item.get_version()).append("<br>&#10;");
        if (!d2Item.isiIdentified()) dispStr.append("Unidentified" + "<br>&#10;");

        dispStr.append(getItemPropertyString(d2Item));

        if (extended) {
            if (d2Item.isSocketed()) {

                if (iSocketedItems != null) {
                    dispStr.append("<br>&#10;");
                    for (int x = 0; x < iSocketedItems.size(); x = x + 1) {
                        if (iSocketedItems.get(x) != null) {
                            dispStr.append(D2ItemRenderer.generatePropStringNoHtmlTags(iSocketedItems.get(x), false));
                            if (x != iSocketedItems.size() - 1) {
                                dispStr.append("<br>&#10;");
                            }
                        }
                    }
                }
            }
        }

        return dispStr.append("</center>");
    }

    private static StringBuilder getItemPropertyString(D2Item d2Item) {
        StringBuilder dispStr = new StringBuilder();
        D2PropCollection iProps = d2Item.getPropCollection();
        int iCharLvl = d2Item.getiCharLvl();
        if (d2Item.isJewel()) {
            dispStr.append(iProps.generateDisplay(1, iCharLvl));
        } else {
            dispStr.append(iProps.generateDisplay(0, iCharLvl));
        }
        if (d2Item.isGem() || d2Item.isRune()) {
            dispStr.append("Weapons: ");
            dispStr.append(iProps.generateDisplay(7, iCharLvl));
            dispStr.append("Armor: ");
            dispStr.append(iProps.generateDisplay(8, iCharLvl));
            dispStr.append("Shields: ");
            dispStr.append(iProps.generateDisplay(9, iCharLvl));
        }
        if (d2Item.getQuality() == 5) {
            // One "Set (N items): " heading per threshold (2..6), combining THREE independent
            // sources that all mean "the bonus you get once N pieces are worn" but live at three
            // different qFlags:
            //   - qFlag N       (2..6):  the ITEM's own per-threshold bonus, read from the save's
            //                            bitstream (setitems.txt "aprop*") -- the one source that
            //                            always had somewhere to render.
            //   - qFlag N+10    (12..16): unused today by any known real item (nothing in this
            //                            codebase currently tags a prop with these) -- kept only
            //                            because some future/legacy data path might.
            //   - qFlag N+20    (22..25, N=2..5 only -- sets.txt has no "PCode6a"): the SET's own
            //                            partial bonus (sets.txt "PCode{N}a"). D2Item.
            //                            addSetProperties already computes this on every set item,
            //                            but nothing ever rendered it before this fix.
            // These used to be two (then three) separate passes over all thresholds, each emitting
            // its own "Set (N items):" heading -- which meant a set item with both an item-level
            // and a set-level bonus at the SAME threshold showed "Set (2 items):" twice in a row
            // with different content, reading like a rendering bug. One heading, one combined body,
            // per threshold, fixes that. Not renumbered to reuse 12..16 for the set-level bonus
            // (the "obvious" free slot) because 12..16 has meaning elsewhere -- D2Prop.addCharMods
            // special-cases it, and D2Item's own stat-calculation filters on it -- so retagging
            // risks silently changing computed character stats instead of just a tooltip line;
            // addSetProperties keeps tagging 22..25 exactly as it always has.
            //
            // The setBuf.length() > 29 guard works because generateDisplay() always returns at
            // least its <font>/</font> wrapper even with nothing inside -- applied to each of the
            // three sources separately, since concatenating first would make an empty source
            // indistinguishable from a real one only 1-2 characters long.
            for (int n = 2; n < 7; n++) {
                StringBuilder itemBuf = iProps.generateDisplay(n, iCharLvl);
                StringBuilder legacyBuf = iProps.generateDisplay(n + 10, iCharLvl);
                StringBuilder fullSetPartialBuf = n < 6 ? iProps.generateDisplay(n + 20, iCharLvl) : null;

                boolean hasItem = itemBuf.length() > 29;
                boolean hasLegacy = legacyBuf.length() > 29;
                boolean hasFullSetPartial = fullSetPartialBuf != null && fullSetPartialBuf.length() > 29;

                if (!hasItem && !hasLegacy && !hasFullSetPartial) {
                    continue;
                }

                dispStr.append("<font color=\"red\">Set (").append(n).append(" items): ");
                if (hasItem) {
                    dispStr.append(itemBuf);
                }
                if (hasFullSetPartial) {
                    dispStr.append(fullSetPartialBuf);
                }
                if (hasLegacy) {
                    dispStr.append(legacyBuf);
                }
                dispStr.append("</font>");
            }
        }
        if (d2Item.isEthereal()) {
            dispStr.append("<font color=\"#4850b8\">Ethereal</font><br>&#10;");
        }
        if (d2Item.getSocketNrTotal() > 0) {
            dispStr.append(d2Item.getSocketNrTotal())
                    .append(" Sockets (")
                    .append(d2Item.getSocketNrFilled())
                    .append(" used)<br>&#10;");
            if (d2Item.getiSocketedItems() != null) {
                for (int i = 0; i < d2Item.getiSocketedItems().size(); i++) {
                    dispStr.append("Socketed: ")
                            .append(stripItemName(d2Item.getiSocketedItems().get(i).getItemName()))
                            .append("<br>&#10;");
                }
            }
        }
        if (d2Item.getQuality() == 5) {
            dispStr.append("<br>&#10;");
            for (int x = 32; x < 36; x++) {
                StringBuilder setBuf = iProps.generateDisplay(x, iCharLvl);
                if (setBuf.length() > 29) {
                    dispStr.append("<font color=\"red\">(").append(x - 30).append(" items): ");
                    dispStr.append(setBuf);
                    dispStr.append("</font>");
                }
            }
            StringBuilder setBuf = iProps.generateDisplay(36, iCharLvl);
            if (setBuf.length() > 33) {
                dispStr.append(setBuf);
            }

            // The full-SET bonus (sets.txt "FCode1".."FCode8", granted once every piece is worn).
            // D2Item.addSetProperties tags these 26; nothing above (32..36 included) ever asked for
            // that flag, so this was silently computed and dropped on every set item -- same root
            // cause, and same fix shape, as the 22..25 block above. "Full Set Bonus: " matches the
            // Holy Grail's missing-item tooltip's wording for this exact section.
            StringBuilder setBuf26 = iProps.generateDisplay(26, iCharLvl);
            if (setBuf26.length() > 29) {
                dispStr.append("<font color=\"red\">Full Set Bonus: ");
                dispStr.append(setBuf26);
                dispStr.append("</font>");
            }
        }
        return dispStr;
    }
}
