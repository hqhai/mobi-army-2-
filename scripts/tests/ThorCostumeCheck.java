import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;
import mobiarmy.server.Equip;
import mobiarmy.server.Glass;
import mobiarmy.server.ThorCostume;
import mobiarmy.server.ThorSkin;

/**
 * Thor costumes in the game's own pieces (ThorCostume, art/thor-native): installed twice without duplicates, every
 * character has its helmet, weapon, armour (and cape) with six frames inside its atlas, the VIP costumes wear them
 * and Skin Thor has the armour's look. Run in the server folder against a database (it writes the catalog rows,
 * which is what the server does at every start).
 */
public class ThorCostumeCheck {
    static int checks;

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }

    public static void main(String[] args) throws Exception {
        Glass.loadGlass();
        ThorCostume.ensureCatalog();
        ThorCostume.ensureCatalog();
        Equip.loadEquip();
        for (int glass = 0; glass < 10; glass++) {
            File file = new File("res/bigImage" + glass + ".png");
            check(file.length() <= 65535, "atlas " + glass + " fits the client's unsigned short length (" + file.length() + " bytes)");
            BufferedImage atlas = ImageIO.read(file);
            Equip helmet = piece(atlas, glass, ThorCostume.HELMET, 1);
            Equip weapon = piece(atlas, glass, ThorCostume.WEAPON, 0);
            Equip armor = piece(atlas, glass, ThorCostume.ARMOR, 2);
            Equip cape = Equip.get((byte) glass, ThorCostume.CAPE, 4);
            if (glass != 5) {
                check(cape != null, "character " + glass + " has a cape");
                piece(atlas, glass, ThorCostume.CAPE, 4);
            } else {
                check(cape == null, "Granos has no cape piece (his Batman has none either)");
            }
            check(weapon.bullet >= 0, "character " + glass + " weapon keeps a bullet sprite (" + weapon.bullet + ")");
            int count = 0;
            for (Equip entry : Equip.entrys) {
                if (entry.glassID == glass && entry.id >= ThorCostume.HELMET && entry.id <= ThorCostume.CAPE) count++;
            }
            check(count == (glass == 5 ? 3 : 4), "character " + glass + " has each hidden piece once (" + count + ")");
            if (glass == ThorSkin.GLASS) {
                Equip skin = Equip.get((byte) glass, ThorSkin.ID, ThorSkin.SLOT);
                check(skin != null && same(skin, armor), "Skin Thor looks like the Thor armour");
            } else {
                Equip vip = Equip.get((byte) glass, ThorCostume.FIRST_VIP + glass, 1);
                if (vip == null) continue; // a database without the Thor VIP migration
                check(vip.vip == 1 && same(vip, helmet), "Thor costume " + vip.id + " is the helmet of its outfit");
                short[] data = {ThorCostume.WEAPON, vip.id, ThorCostume.ARMOR, -1, glass == 5 ? -1 : ThorCostume.CAPE};
                check(Arrays.equals(vip.data, data), "Thor costume " + vip.id + " wears weapon, helmet, armour and cape: " + Arrays.toString(vip.data));
            }
        }
        System.out.println("THOR COSTUME PASSED: " + checks + " checks");
    }

    /** A hidden outfit piece: six frames, each inside the atlas and not empty. */
    static Equip piece(BufferedImage atlas, int glass, short id, int type) {
        Equip piece = Equip.get((byte) glass, id, type);
        check(piece != null && piece.vip == 0 && piece.name == null && piece.xu == -1 && piece.luong == -1,
                "character " + glass + " piece " + id + " is hidden and not for sale");
        check(piece.x.length == 6 && piece.y.length == 6 && piece.w.length == 6 && piece.h.length == 6
                && piece.dx.length == 6 && piece.dy.length == 6, "character " + glass + " piece " + id + " has six frames");
        for (int f = 0; f < 6; f++) {
            boolean inside = piece.x[f] >= 0 && piece.y[f] >= 0 && piece.w[f] > 0 && piece.h[f] > 0
                    && piece.x[f] + piece.w[f] <= atlas.getWidth() && piece.y[f] + piece.h[f] <= atlas.getHeight();
            check(inside, "character " + glass + " piece " + id + " frame " + f + " is inside the atlas");
            boolean drawn = false;
            for (int y = piece.y[f]; y < piece.y[f] + piece.h[f] && !drawn; y++) {
                for (int x = piece.x[f]; x < piece.x[f] + piece.w[f] && !drawn; x++) drawn = (atlas.getRGB(x, y) >>> 24) != 0;
            }
            check(drawn, "character " + glass + " piece " + id + " frame " + f + " has pixels");
        }
        return piece;
    }

    static boolean same(Equip a, Equip b) {
        return Arrays.equals(a.x, b.x) && Arrays.equals(a.y, b.y) && Arrays.equals(a.w, b.w) && Arrays.equals(a.h, b.h)
                && Arrays.equals(a.dx, b.dx) && Arrays.equals(a.dy, b.dy);
    }
}
