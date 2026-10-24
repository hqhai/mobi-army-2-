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
 * Costume bullets in the game's own style (art/costume-bullets): every character's bullet sheet res/bullet{glass}.png
 * has a Thor, Batman, mummy and pumpkin kind, listed in res/costume-bullets.json. At start the costumes' guns get
 * them: Thor's weapon piece and the gun each batman, zombie (mummy) and pumpkin VIP costume lists first in its data.
 * Iron Man keeps the game's own kind 6.
 */
public final class CostumeBullets {
    static final String FILE = "res/costume-bullets.json";
    /** VIP costume name in the catalog and its kind in the JSON. */
    static final String[][] COSTUMES = {{"batman", "batman"}, {"zombie", "mummy"}, {"bí ngô", "pumpkin"}};

    private CostumeBullets() {}

    public static void ensureCatalog() throws IOException, SQLException {
        Path file = Path.of(FILE);
        if (!Files.exists(file)) return;
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        try (Connection c = Server.dbManager.openConnection()) {
            c.setAutoCommit(false);
            try {
                for (JsonElement element : root.getAsJsonArray("kinds")) {
                    JsonObject kinds = element.getAsJsonObject();
                    int glass = kinds.get("glass").getAsInt();
                    Server.dbManager.update(c, "UPDATE equip SET bullet=? WHERE glassID=? AND id=? AND type=0",
                            kinds.get("thor").getAsInt(), glass, ThorCostume.WEAPON);
                    for (String[] costume : COSTUMES) {
                        Server.dbManager.update(c, """
                                UPDATE equip gun JOIN equip vip ON vip.glassID = gun.glassID
                                    AND gun.id = CAST(JSON_EXTRACT(vip.data, '$[0]') AS INT)
                                SET gun.bullet = ?
                                WHERE vip.glassID = ? AND vip.vip = 1 AND vip.type = 1 AND vip.name = ? AND gun.type = 0
                                """, kinds.get(costume[1]).getAsInt(), glass, costume[0]);
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException error) {
                c.rollback();
                throw error;
            }
        }
    }
}
