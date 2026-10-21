package mobiarmy.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Catalog identity is also transmitted by the existing equipment protocol. */
public final class ThorSkin {
    public static final byte GLASS = 8, SLOT = 2;
    public static final short ID = 30000;
    public static final int PRICE = 1000;
    private ThorSkin() {}

    public static boolean is(Equip equip) {
        return equip != null && equip.glassID == GLASS && equip.type == SLOT && equip.id == ID;
    }

    public static void ensureCatalog() throws SQLException {
        // Both tables are required: user_equip has a foreign key to equip.id.
        try (Connection c = Server.dbManager.openConnection()) {
            c.setAutoCommit(false);
            try {
                validateReservedId(c, "equip");
                validateReservedId(c, "shop_equipment");
                Server.dbManager.update(c, """
                    INSERT INTO equip (glassID,id,type,bullet,icon,level,x,y,w,h,dx,dy,
                        inv_ability,inv_percen,vip,data,date,luong,xu,name)
                    SELECT 8,30000,2,bullet,icon,0,x,y,w,h,dx,dy,
                        '[0,0,0,0,0]','[0,0,0,0,0]',0,'null',127,1000,-1,'Skin Thor'
                    FROM equip WHERE glassID=8 AND type=2 AND id<>30000
                        AND NOT EXISTS (SELECT 1 FROM equip WHERE id=30000)
                    ORDER BY id LIMIT 1
                    """);
                Server.dbManager.update(c, """
                    INSERT INTO shop_equipment (id,glassID,equipId,name,xu,luong,inv_ability,inv_percen)
                    SELECT 30000,8,30000,'Skin Thor',-1,1000,'[0,0,0,0,0]','[0,0,0,0,0]'
                    WHERE NOT EXISTS (SELECT 1 FROM shop_equipment WHERE id=30000)
                    """);
                c.commit();
            } catch (SQLException | RuntimeException error) {
                c.rollback();
                throw error;
            }
        }
    }

    private static void validateReservedId(Connection c, String table) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT glassID,name FROM " + table + " WHERE id=?")) {
            p.setShort(1, ID);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) if (r.getByte(1) != GLASS || !"Skin Thor".equals(r.getString(2)))
                    throw new SQLException("Reserved Thor catalog ID already used in " + table);
            }
        }
    }

    public static void addBonus(Glass glass, int[] stats) {
        if (glass.id != GLASS) return;
        for (Equip equip : glass.user.equips) {
            if (is(equip) && equip.isUse) {
                stats[0] += 10000;
                stats[1] += 1000;
                stats[3] += 10000;
                return; // A skin bonus never stacks with a duplicate inventory record.
            }
        }
    }
}
