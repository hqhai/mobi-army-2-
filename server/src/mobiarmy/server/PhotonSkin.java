package mobiarmy.server;

import java.sql.*;

/** Separate VIP catalog entry using the native Magenta VIP wire format. */
public final class PhotonSkin {
    public static final short ID = 30001;
    private PhotonSkin() {}
    public static void ensureCatalog() throws SQLException {
        try (Connection c=Server.dbManager.openConnection()) {
            c.setAutoCommit(false);
            try {
                for(String table:new String[]{"equip","shop_equipment"}) {
                    try(PreparedStatement p=c.prepareStatement("SELECT glassID,name FROM "+table+" WHERE id=?")){
                        p.setInt(1,ID);
                        try(ResultSet r=p.executeQuery()){
                            while(r.next())if(r.getInt(1)!=9 || !"Photon".equals(r.getString(2)))
                                throw new SQLException("Photon reserved ID occupied: "+table);
                        }
                    }
                }
                Server.dbManager.update(c,"""
                    INSERT INTO equip (glassID,id,type,bullet,icon,level,x,y,w,h,dx,dy,
                        inv_ability,inv_percen,vip,data,date,luong,xu,name)
                    SELECT 9,30001,type,bullet,icon,level,x,y,w,h,dx,dy,
                        inv_ability,inv_percen,1,'[21,30001,20,-1,-1]',7,50,-1,'Photon'
                    FROM equip WHERE glassID=9 AND id=19 AND type=1
                        AND NOT EXISTS (SELECT 1 FROM equip WHERE id=30001)
                    """);
                Server.dbManager.update(c,"""
                    INSERT INTO shop_equipment (id,glassID,equipId,name,xu,luong,inv_ability,inv_percen)
                    SELECT 30001,9,30001,'Photon',-1,50,inv_ability,inv_percen
                    FROM equip WHERE glassID=9 AND id=30001
                        AND NOT EXISTS (SELECT 1 FROM shop_equipment WHERE id=30001)
                    """);
                try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM equip WHERE glassID=9 AND id=30001");
                    ResultSet r=p.executeQuery()){
                    r.next();if(r.getInt(1)!=1)throw new SQLException("Missing Photon source catalog");
                }
                c.commit();
            } catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
}
