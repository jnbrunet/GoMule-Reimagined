package gomule.grail;

import java.util.Objects;

/**
 * The identity of one grail-eligible entry, used as a HashMap key by both
 * {@link D2GrailIndex} (id -> entry) and {@link D2GrailScanner} (id -> what was found).
 * <p>
 * Uniques and set items are identified by their table's {@code *ID} column (an int); runewords
 * have no numeric id in runes.txt, so they are identified by the {@code Name} column (a string,
 * e.g. "Insight") instead. Two entries of different {@link Type} never collide even if their id
 * happens to be numerically/textually similar, because {@link Type} is always part of identity.
 */
public final class D2GrailKey {

    public enum Type {
        UNIQUE,
        SET,
        RUNEWORD
    }

    private final Type iType;

    // Meaningful only for UNIQUE/SET (the uniqueitems.txt/setitems.txt "*ID" column value).
    // -1 for RUNEWORD keys.
    private final int iId;

    // Meaningful only for RUNEWORD (the runes.txt "Name" column value, e.g. "Insight").
    // null for UNIQUE/SET keys.
    private final String iName;

    private D2GrailKey(Type pType, int pId, String pName) {
        iType = pType;
        iId = pId;
        iName = pName;
    }

    public static D2GrailKey unique(int pId) {
        return new D2GrailKey(Type.UNIQUE, pId, null);
    }

    public static D2GrailKey set(int pId) {
        return new D2GrailKey(Type.SET, pId, null);
    }

    public static D2GrailKey runeword(String pName) {
        return new D2GrailKey(Type.RUNEWORD, -1, pName);
    }

    public Type getType() {
        return iType;
    }

    /**
     * Valid for UNIQUE/SET keys only; -1 for RUNEWORD keys.
     */
    public int getId() {
        return iId;
    }

    /**
     * Valid for RUNEWORD keys only; null for UNIQUE/SET keys.
     */
    public String getName() {
        return iName;
    }

    @Override
    public boolean equals(Object pOther) {
        if (this == pOther) return true;
        if (!(pOther instanceof D2GrailKey)) return false;
        D2GrailKey lOther = (D2GrailKey) pOther;
        return iType == lOther.iType && iId == lOther.iId && Objects.equals(iName, lOther.iName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iType, iId, iName);
    }

    @Override
    public String toString() {
        return iType == Type.RUNEWORD ? (iType + ":" + iName) : (iType + ":" + iId);
    }
}
