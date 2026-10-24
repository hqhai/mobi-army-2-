package player;

import CLib.mGraphics;
import CLib.mImage;
import Equipment.Equip;
import Equipment.PlayerEquip;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.teamobi.mobiarmy2.MainGame;
import coreLG.CCanvas;
import coreLG.TerrainMidlet;
import java.nio.file.Files;
import java.nio.file.Path;
import model.FrameImage;
import screen.GameScr;

/**
 * Skin Thor and the Thor costumes drawn with the game's own pieces (server ThorCostume): the real server catalog
 * (-Dthor.catalog, written by ThorShopCheck) with the atlases and bodies from server/res, rendered in LibGDX.
 * Run from the repository root; -Dthor.preview is where the rendered sheet goes.
 */
public class ThorSkinCheck extends MainGame {
    static final String[] BODIES = {"yellowP", "cuteGirl", "greenP", "conKhiP", "rocketer", "robot", "ga", "tazz", "apache", "cowg"};
    static Throwable failure;
    mGraphics graphics;
    int frames;
    int waits;
    boolean kindsChecked;
    int checks;
    PlayerEquip apache, plain;
    PlayerEquip[] outfits = new PlayerEquip[10];

    void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    static mImage image(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        return new mImage(CLib.Image.createImage(bytes, 0, bytes.length));
    }

    /** Loaded like the game loads bullet sheets (CCanvas): the pixels stay readable for CCanvas.cutImage. */
    static mImage sheet(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
        CLib.Image image = new CLib.Image();
        image.texture = new com.badlogic.gdx.graphics.Texture(pixels, Pixmap.Format.RGBA8888, false);
        image.width = pixels.getWidth();
        image.height = pixels.getHeight();
        return new mImage(image);
    }

    /** The Thor outfit of character glass: weapon, helmet (the VIP costume, or the hidden helmet), armour, cape. */
    static PlayerEquip outfit(int glass) {
        short helmet = glass == ThorSkin.GLASS ? ThorSkin.HELMET : (short) (ThorSkin.FIRST_VIP + glass);
        short armor = glass == ThorSkin.GLASS ? ThorSkin.ID : (short) 30112;
        return new PlayerEquip(new short[][] {{(short) glass, 0, ThorSkin.WEAPON}, {(short) glass, 1, helmet},
                {(short) glass, 2, armor}, {(short) glass, 3, -1}, {(short) glass, 4, ThorSkin.CAPE}});
    }

    @Override
    public void create() {
        try {
            super.create();
            graphics = new mGraphics(new SpriteBatch());
            TerrainMidlet.myInfo = new model.PlayerInfo();
            TerrainMidlet.myInfo.gun = 8;
            CCanvas.readMess(Files.readAllBytes(Path.of(System.getProperty("thor.catalog"))), (byte) 1);
            for (int glass = 0; glass < 10; glass++) {
                CPlayer.pImg[glass] = image("server/res/player/" + BODIES[glass] + ".png");
                PlayerEquip.imgData[glass] = image("server/res/bigImage" + glass + ".png");
                PlayerEquip.bullets[glass] = sheet("server/res/bullet" + glass + ".png");
            }
            Equip thorWeapon = PlayerEquip.getEquip((byte) 8, (byte) 0, ThorSkin.WEAPON);
            check(thorWeapon != null && thorWeapon.bullet >= 9
                    && (thorWeapon.bullet + 1) * 20 <= PlayerEquip.bullets[8].image.getHeight(),
                    "Thor's weapon throws the added Mjölnir kind " + (thorWeapon == null ? null : thorWeapon.bullet) + " of Apache's bullet sheet");
            Equip skin = PlayerEquip.getEquip((byte) 8, (byte) 2, ThorSkin.ID);
            check(skin != null, "real server catalog decodes Skin Thor 30000");
            check(!skin.getStrShopDetail().contains("10000") && !skin.getStrInvDetail().contains("10000")
                    && skin.getStrInvDetail().contains("Vĩnh viễn"), "shop and inventory advertise no flat bonus");
            for (int glass = 0; glass < 10; glass++) {
                outfits[glass] = outfit(glass);
                PlayerEquip o = outfits[glass];
                check(o.equips[0] != null && o.equips[1] != null && o.equips[2] != null && (o.equips[4] != null) == (glass != 5),
                        "character " + glass + " Thor pieces decode (cape for all but Granos)");
                check(o.equips[1].x.length == 6 && o.equips[2].x.length == 6, "character " + glass + " pieces have six frames");
                if (glass != ThorSkin.GLASS) {
                    check(ThorSkin.isThorCostume(o.equips[1]), "Thor costume " + o.equips[1].id + " is recognised");
                }
            }
            apache = new PlayerEquip(new short[][] {{8, 0, 2}, {8, 1, 1}, {8, 2, ThorSkin.ID}, {8, 3, -1}, {8, 4, -1}});
            plain = new PlayerEquip(new short[][] {{8, 0, 2}, {8, 1, 1}, {8, 2, 0}, {8, 3, -1}, {8, 4, -1}});
            check(ThorSkin.isEquipped(apache) && !ThorSkin.isEquipped(plain), "appearance reconstructed from equipment IDs");
            check(!ThorSkin.paint(null, plain, 2, 0, 0, 0), "Apache without the skin is not reskinned");
            check(!ThorSkin.isThorCostume(plain.equips[1]) && ThorSkin.isThorCostume(skin), "only Thor items get the Thor icon");
            check(ThorSkin.projectile(null, -1) == null && ThorSkin.projectile(null, 1) == null, "invalid shooter guarded");

            GameScr game = new GameScr();
            CCanvas.curScr = game;
            CCanvas.currentDialog = null;
            java.lang.reflect.Field cross = CPlayer.class.getDeclaredField("crosshair");
            cross.setAccessible(true);
            CLib.Image dummy = new CLib.Image();
            dummy.width = 8;
            dummy.height = 8;
            cross.set(null, new mImage(dummy));
            CPlayer p = new CPlayer(1, (byte) 0, 100, 100, false, 2, (byte) 16, null, 11000);
            p.gun = 8;
            p.equip = apache;
            p.active = true;
            p.falling = false;
            p.movePoint = 0;
            CPlayer.angleLock = new short[30];
            PM.p = new CPlayer[] {p};
            PM.curP = 0;
            GameScr.myIndex = 0;
            java.lang.reflect.Field current = CPlayer.class.getDeclaredField("curFrame");
            current.setAccessible(true);
            java.util.Set<Integer> walked = new java.util.HashSet<>();
            for (int i = 0; i < 6; i++) {
                p.falling = false;
                p.move(2);
                walked.add(current.getInt(p));
            }
            check(p.x == 106 && walked.contains(4) && walked.contains(5), "Apache in Thor moves six pixels and alternates both walk frames");
            p.falling = false;
            p.move(0);
            check(p.x == 105 && p.look == 0, "Apache also moves left");
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
            Gdx.app.exit();
        }
    }

    /** Pixels that differ between two same-size boxes of the frame buffer (y from the top). */
    static int differences(Pixmap pix, int ax, int bx, int top, int w, int h) {
        int count = 0;
        for (int y = top; y < top + h; y++) {
            for (int x = 0; x < w; x++) if (pix.getPixel(ax + x, y) != pix.getPixel(bx + x, y)) count++;
        }
        return count;
    }

    @Override
    public void render() {
        if (failure != null) return;
        try {
            Gdx.gl.glClearColor(.12f, .16f, .22f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            OrthographicCamera camera = new OrthographicCamera();
            camera.setToOrtho(true, 900, 800);
            graphics.g.setProjectionMatrix(camera.combined);
            mGraphics.zoomLevel = 2;
            FrameImage hammer = ThorSkin.projectile(null, 0);
            if (hammer == null) {
                // As in the game, the cut picture of Mjölnir gets its texture on a later frame.
                if (++waits > 10) throw new AssertionError("Mjölnir's picture never got ready");
                return;
            }
            if (hammer.nFrame != 8) throw new AssertionError("Mjölnir turns in eight directions");
            if (frames == 0) {
                GameScr.bm = new item.BM();
                java.lang.reflect.Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                uf.setAccessible(true);
                sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
                java.lang.reflect.Method init = item.Bullet.class.getDeclaredMethod("initBulletType", int.class);
                init.setAccessible(true);
                java.lang.reflect.Field img = item.Bullet.class.getDeclaredField("frmImg");
                img.setAccessible(true);
                for (int type : new int[] {17, 18}) {
                    Object b = u.allocateInstance(item.Bullet.class);
                    init.invoke(b, type);
                    check(img.get(b) == hammer, "projectile hook " + type);
                }
                PlayerEquip saved = PM.p[0].equip;
                PM.p[0].equip = null;
                check(ThorSkin.projectile(null, 0) == null, "ordinary Apache keeps original projectile");
                PM.p[0].equip = saved;
            }
            if (!kindsChecked) {
                // King Kong, Chicky and Tarzan bullets follow the kind each shot carries (the game cut only kind 0).
                Object boomerang = GameScr.bm.kindPicture(21, 10), chicken = GameScr.bm.kindPicture(19, 12), banana = GameScr.bm.kindPicture(9, 11);
                if (boomerang != null && chicken != null && banana != null) {
                    check(boomerang instanceof FrameImage && ((FrameImage) boomerang).nFrame == 4 && chicken instanceof mImage
                            && banana instanceof FrameImage, "Tarzan, Chicky and King Kong bullets are cut for each shot's kind");
                    check(GameScr.bm.kindPicture(21, 0) == null && GameScr.bm.kindPicture(21, 99) == null,
                            "kind 0 keeps the start-of-match pictures; a kind past the sheet is refused");
                    kindsChecked = true;
                }
            }
            graphics.begin();
            // Row 1: plain Apache, then Apache in Skin Thor, then its walk frames 4 and 5 side by side.
            plain.paint(graphics, 2, 0, 20, 40);
            apache.paint(graphics, 2, 0, 70, 40);
            apache.paint(graphics, 2, 4, 120, 40);
            apache.paint(graphics, 2, 5, 170, 40);
            for (int i = 0; i < 8; i++) hammer.drawFrame(i, 220 + i * 17, 30, 0, 3, graphics);
            for (int glass = 0; glass < 10; glass++) {
                Equip icon = outfits[glass].equips[glass == ThorSkin.GLASS ? 2 : 1];
                if (!ThorSkin.paintIcon(graphics, icon, 6 + glass * 20, 52, false)) throw new AssertionError("icon " + glass);
            }
            // Then every character's Thor outfit, six frames facing each way.
            for (int glass = 0; glass < 10; glass++) {
                for (int f = 0; f < 12; f++) {
                    outfits[glass].paint(graphics, f < 6 ? 2 : 0, f % 6, 18 + f * 36, 100 + glass * 26);
                }
            }
            graphics.end();
            if (++frames == 20) {
                Pixmap pix = Pixmap.createFromFrameBuffer(0, 0, 900, 800);
                // Frame buffer rows count from the bottom; the first row of characters stands on y = 40 (x2 zoom).
                int top = 800 - 80;
                check(differences(pix, 20, 120, top, 60, 70) > 150, "Apache in Skin Thor looks different from plain Apache");
                check(differences(pix, 220, 320, top, 60, 70) > 20, "Thor's walk frames 4 and 5 differ");
                check(kindsChecked, "bullet kinds got cut within the frames of the test");
                PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("thor.preview")), pix, -1, true);
                pix.dispose();
                System.out.println("THOR CLIENT PASSED: " + checks + " checks and render");
                Gdx.app.exit();
            }
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
            Gdx.app.exit();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setInitialVisible(false);
        c.setWindowedMode(900, 800);
        c.disableAudio(true);
        new Lwjgl3Application(new ThorSkinCheck(), c);
        if (failure != null) System.exit(1);
    }
}
