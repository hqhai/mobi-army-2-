import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import mobiarmy.server.CostumeBullets;
import mobiarmy.server.Equip;
import mobiarmy.server.Glass;
import mobiarmy.server.ThorCostume;

/**
 * Costume bullets (CostumeBullets, art/costume-bullets): installed twice, every costume's gun throws its own kind,
 * the kinds are drawn in the sheets, and no other item shares those guns. Run in the server folder against a database
 * (it writes the gun rows, as the server does at every start).
 */
public class CostumeBulletsCheck {
    static int checks;
    /** Costume name in the catalog and its kind in res/costume-bullets.json. */
    static final String[][] COSTUMES = {{"batman", "batman"}, {"zombie", "mummy"}, {"bí ngô", "pumpkin"}, {"ironman", "ironman"}};

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }

    public static void main(String[] args) throws Exception {
        Glass.loadGlass();
        ThorCostume.ensureCatalog();
        CostumeBullets.ensureCatalog();
        CostumeBullets.ensureCatalog();
        Equip.loadEquip();
        JsonObject root = JsonParser.parseString(Files.readString(Path.of("res/costume-bullets.json"))).getAsJsonObject();
        for (JsonElement element : root.getAsJsonArray("kinds")) {
            JsonObject kinds = element.getAsJsonObject();
            int glass = kinds.get("glass").getAsInt();
            File file = new File("res/bullet" + glass + ".png");
            check(file.length() <= 32767, "bullet sheet " + glass + " fits the equipment packet (" + file.length() + " bytes)");
            BufferedImage sheet = ImageIO.read(file);
            int frames = glass == 2 ? 2 : glass == 5 ? 4 : 1;
            int count = sheet.getHeight() / (20 * frames);
            for (String kind : new String[] {"thor", "batman", "mummy", "pumpkin"}) {
                int index = kinds.get(kind).getAsInt();
                check(index >= 9 && index < count && drawn(sheet, index * 20 * frames, 20 * frames),
                        "character " + glass + " has a drawn " + kind + " kind (" + index + " of " + count + ")");
            }
            Equip thor = Equip.get((byte) glass, ThorCostume.WEAPON, 0);
            check(thor != null && thor.bullet == kinds.get("thor").getAsInt(), "character " + glass + " Thor weapon throws the Thor kind");
            for (String[] costume : COSTUMES) {
                Equip vip = costume(glass, costume[0]);
                if (vip == null) continue;
                Equip gun = Equip.get((byte) glass, vip.data[0], 0);
                check(gun != null && gun.bullet == kinds.get(costume[1]).getAsInt(),
                        "character " + glass + " " + costume[0] + " gun throws kind " + kinds.get(costume[1]).getAsInt());
                if (!costume[0].equals("ironman")) { // Magenta's Photon wears the Iron Man gun; its kind stays the game's
                    check(gun.name == null && users(glass, gun.id) == 1, "character " + glass + " " + costume[0] + " gun is that costume's alone");
                }
            }
        }
        System.out.println("COSTUME BULLETS PASSED: " + checks + " checks");
    }

    static Equip costume(int glass, String name) {
        for (Equip e : Equip.entrys) if (e.glassID == glass && e.vip == 1 && e.type == 1 && name.equals(e.name)) return e;
        return null;
    }

    /** VIP costumes of the character that wear this gun. */
    static int users(int glass, short gun) {
        int n = 0;
        for (Equip e : Equip.entrys) if (e.glassID == glass && e.vip == 1 && e.data != null && e.data.length > 0 && e.data[0] == gun) n++;
        return n;
    }

    static boolean drawn(BufferedImage sheet, int top, int height) {
        for (int y = top; y < top + height; y++) {
            for (int x = 0; x < sheet.getWidth(); x++) if ((sheet.getRGB(x, y) >>> 24) != 0) return true;
        }
        return false;
    }
}
