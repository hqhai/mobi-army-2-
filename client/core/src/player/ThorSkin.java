package player;

import CLib.Image;
import CLib.mGraphics;
import CLib.mImage;
import Equipment.Equip;
import Equipment.PlayerEquip;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import model.FrameImage;

/**
 * Purchased Apache costume (armour slot); identity matches the server catalog. Worn, Apache is drawn in the whole
 * Thor outfit made of the game's own kind of pieces (server ThorCostume, art/thor-native), so every frame of the
 * game animates as for any costume; his hammers become Thor's.
 */
public final class ThorSkin {
    public static final short ID = 30000;
    public static final byte GLASS = 8;
    /** Hidden pieces of the Thor outfit, the same ids for every character (server mobiarmy.server.ThorCostume). */
    public static final short HELMET = 30110, WEAPON = 30111, CAPE = 30113;
    /** Thor VIP costume of character n (n != Apache) is FIRST_VIP + n. */
    public static final short FIRST_VIP = 30100;
    private static FrameImage hammer;
    private static mImage hammerSheet;
    private static int hammerKind = -1;
    private static boolean hammerPending;
    private static boolean iconsAttempted;
    private static mImage icons;
    private static Equip outfitArmor;
    private static PlayerEquip outfit;

    private ThorSkin() {}

    /**
     * Skin Thor's hammer: the Thor kind of Apache's bullet sheet (Mjölnir, server CostumeBullets), turning like his own
     * hammers. Null while the catalog or the sheet does not have it, and until its picture is ready: like BM does for
     * Apache's own hammers, the cut picture gets its texture on the next frame.
     */
    private static FrameImage hammer() {
        Equip weapon = PlayerEquip.getEquip(GLASS, (byte) 0, WEAPON);
        final mImage sheet = PlayerEquip.bullets[GLASS];
        if (weapon == null || sheet == null || sheet.image == null || weapon.bullet < 0) return null;
        int size = sheet.image.getWidth();
        if (size <= 0 || (weapon.bullet + 1) * size > sheet.image.getHeight()) return null; // a sheet without the kind
        if (sheet != hammerSheet || weapon.bullet != hammerKind) {
            hammerSheet = sheet;
            hammerKind = weapon.bullet;
            hammer = null;
            hammerPending = false;
        }
        if (hammer == null && !hammerPending) {
            hammerPending = true;
            coreLG.CCanvas.cutImage(sheet, weapon.bullet, new model.IAction2() {
                public void perform(Object object) {
                    if (hammerSheet == sheet) hammer = new FrameImage((Image) object, 8);
                    hammerPending = false;
                }
            });
        }
        return hammer;
    }

    public static boolean is(Equip equip) {
        return equip != null && equip.glass == GLASS && equip.type == 2 && equip.id == ID;
    }

    /** Skin Thor or one of the Thor VIP costumes. */
    public static boolean isThorCostume(Equip equip) {
        return is(equip) || equip != null && equip.type == 1 && equip.id == FIRST_VIP + equip.glass && equip.glass != GLASS;
    }

    public static boolean isEquipped(PlayerEquip equip) {
        return equip != null && is(equip.equips[2]);
    }

    public static boolean isShooter(int shooter) {
        return PM.p != null && shooter >= 0 && shooter < PM.p.length && PM.p[shooter] != null
                && isEquipped(PM.p[shooter].equip);
    }

    /** Apache in Skin Thor: the whole Thor outfit, drawn the way the game draws any costume. */
    public static boolean paint(mGraphics g, PlayerEquip equip, int look, int frame, int x, int y) {
        if (!isEquipped(equip)) return false;
        hammer(); // gets Mjölnir's picture ready before the first throw
        PlayerEquip thor = outfit(equip.equips[2]);
        if (thor == null) return false; // a catalog without the Thor pieces: the armour alone, drawn as usual
        thor.paintNative(g, look, frame, x, y);
        return true;
    }

    private static PlayerEquip outfit(Equip armor) {
        if (armor != outfitArmor) {
            outfitArmor = armor;
            outfit = null;
            Equip helmet = PlayerEquip.createEquip(GLASS, (byte) 1, HELMET);
            Equip weapon = PlayerEquip.createEquip(GLASS, (byte) 0, WEAPON);
            if (helmet != null && weapon != null) {
                outfit = new PlayerEquip();
                outfit.equips[0] = weapon;
                outfit.equips[1] = helmet;
                outfit.equips[2] = armor;
                outfit.equips[4] = PlayerEquip.createEquip(GLASS, (byte) 4, CAPE);
            }
        }
        return outfit;
    }

    /** Icon of Skin Thor and the Thor costumes: that character's head in the Thor helmet (16 x 16 each). */
    public static boolean paintIcon(mGraphics g, Equip equip, int x, int y, boolean clip) {
        if (!isThorCostume(equip) || !loadIcons()) return false;
        g.drawRegion(icons, equip.glass * 16, 0, 16, 16, 0, x, y, mGraphics.TOP | mGraphics.LEFT, clip);
        return true;
    }

    private static boolean loadIcons() {
        if (iconsAttempted) return icons != null;
        iconsAttempted = true;
        try {
            Pixmap pixels = new Pixmap(Gdx.files.internal("skins/thor-native/icons.png"));
            Image image = new Image();
            image.texture = new Texture(pixels);
            image.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            image.width = pixels.getWidth();
            image.height = pixels.getHeight();
            pixels.dispose();
            icons = new mImage(image);
            return true;
        } catch (RuntimeException error) {
            Gdx.app.error("ThorSkin", "Thor icons unavailable; using the catalog icons", error);
            return false;
        }
    }

    public static FrameImage projectile(FrameImage original, int shooter) {
        FrameImage thor = isShooter(shooter) ? hammer() : null;
        return thor != null ? thor : original;
    }

    /** POW throws the same Mjölnir; the game's own POW effects go with it. */
    public static FrameImage projectile(FrameImage original, int shooter, boolean pow) {
        return projectile(original, shooter);
    }
}
