package mobiarmy.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Thor costumes drawn like the game's own costumes (art/thor-native/make_thor_costume.py): for every character a
 * helmet, weapon, armour and cape piece in its equipment atlas res/bigImage{glass}.png, at the frames
 * res/thor-costume.json gives. Installed at start: the hidden pieces of each character, the Thor VIP costumes
 * 30100 + character that wear them, and the look of Apache's Skin Thor (ThorSkin.ID).
 */
public final class ThorCostume {
    /** Hidden pieces of the outfit, the same ids for every character (the key is glassID + id). */
    public static final short HELMET = 30110, WEAPON = 30111, ARMOR = 30112, CAPE = 30113;
    /** Thor VIP costume of character n is FIRST_VIP + n; Apache has Skin Thor instead. */
    public static final short FIRST_VIP = 30100;
    static final String FILE = "res/thor-costume.json";

    private ThorCostume() {}

    public static void ensureCatalog() throws IOException, SQLException {
        Path file = Path.of(FILE);
        if (!Files.exists(file)) return;
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        try (Connection c = Server.dbManager.openConnection()) {
            c.setAutoCommit(false);
            try {
                for (JsonElement element : root.getAsJsonArray("glasses")) {
                    install(c, element.getAsJsonObject());
                }
                c.commit();
            } catch (SQLException | RuntimeException error) {
                c.rollback();
                throw error;
            }
        }
    }

    private static void install(Connection c, JsonObject outfit) throws SQLException {
        int glass = outfit.get("glass").getAsInt();
        JsonObject helmet = outfit.getAsJsonObject("helmet");
        JsonObject weapon = outfit.getAsJsonObject("weapon");
        JsonObject armor = outfit.getAsJsonObject("armor");
        boolean cape = outfit.has("cape");
        piece(c, glass, HELMET, 1, helmet, -1);
        piece(c, glass, WEAPON, 0, weapon, weapon.get("bullet").getAsInt());
        piece(c, glass, ARMOR, 2, armor, -1);
        if (cape) {
            piece(c, glass, CAPE, 4, outfit.getAsJsonObject("cape"), -1);
        } else {
            Server.dbManager.update(c, "DELETE FROM equip WHERE glassID=? AND id=?", glass, CAPE);
        }
        // Like the game's own VIP costumes, the costume row is the helmet of the outfit its data lists.
        int vip = FIRST_VIP + glass;
        String data = "[" + WEAPON + "," + vip + "," + ARMOR + ",-1," + (cape ? CAPE : -1) + "]";
        Server.dbManager.update(c, "UPDATE equip SET x=?,y=?,w=?,h=?,dx=?,dy=?,data=? WHERE glassID=? AND id=? AND vip=1",
                frames(helmet, "x"), frames(helmet, "y"), frames(helmet, "w"), frames(helmet, "h"),
                frames(helmet, "dx"), frames(helmet, "dy"), data, glass, vip);
        if (glass == ThorSkin.GLASS) {
            Server.dbManager.update(c, "UPDATE equip SET x=?,y=?,w=?,h=?,dx=?,dy=? WHERE glassID=? AND id=?",
                    frames(armor, "x"), frames(armor, "y"), frames(armor, "w"), frames(armor, "h"),
                    frames(armor, "dx"), frames(armor, "dy"), glass, ThorSkin.ID);
        }
    }

    /** A piece that is only ever drawn as part of an outfit: not sold, no stats, never in a bag. */
    private static void piece(Connection c, int glass, short id, int type, JsonObject at, int bullet) throws SQLException {
        Server.dbManager.update(c, """
                INSERT INTO equip (glassID,id,type,bullet,icon,level,x,y,w,h,dx,dy,
                    inv_ability,inv_percen,vip,data,date,luong,xu,name)
                VALUES (?,?,?,?,0,0,?,?,?,?,?,?,'[0,0,0,0,0]','[0,0,0,0,0]',0,'null',30,-1,-1,NULL)
                ON DUPLICATE KEY UPDATE type=VALUES(type),bullet=VALUES(bullet),x=VALUES(x),y=VALUES(y),
                    w=VALUES(w),h=VALUES(h),dx=VALUES(dx),dy=VALUES(dy)
                """, glass, id, type, bullet, frames(at, "x"), frames(at, "y"), frames(at, "w"), frames(at, "h"),
                frames(at, "dx"), frames(at, "dy"));
    }

    private static String frames(JsonObject at, String key) {
        return at.getAsJsonArray(key).toString();
    }
}
