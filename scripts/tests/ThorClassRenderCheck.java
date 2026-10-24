package player;

import CLib.mGraphics;
import CLib.mImage;
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
import model.PlayerInfo;

/**
 * Review sheet of every Thor VIP costume in all six equipment frames, both directions, drawn the game's own way
 * (server ThorCostume pieces; no picture override). -Dreview.root is the repository; the catalog comes from
 * verification/thor-classes-catalog.bin (ThorClassVipCheck writes it).
 */
public class ThorClassRenderCheck extends MainGame {
    static final String[] BODIES = {"yellowP", "cuteGirl", "greenP", "conKhiP", "rocketer", "robot", "ga", "tazz", "apache", "cowg"};
    static Throwable failure;
    mGraphics g;
    PlayerEquip[] outfits = new PlayerEquip[10];
    int frames;

    @Override
    public void create() {
        try {
            super.create();
            g = new mGraphics(new SpriteBatch());
            TerrainMidlet.myInfo = new PlayerInfo();
            String root = System.getProperty("review.root");
            CCanvas.readMess(Files.readAllBytes(Path.of(root, "verification/thor-classes-catalog.bin")), (byte) 1);
            for (int i = 0; i < 10; i++) {
                byte[] body = Files.readAllBytes(Path.of(root, "server/res/player/" + BODIES[i] + ".png"));
                CPlayer.pImg[i] = new mImage(CLib.Image.createImage(body, 0, body.length));
                byte[] atlas = Files.readAllBytes(Path.of(root, "server/res/bigImage" + i + ".png"));
                PlayerEquip.imgData[i] = new mImage(CLib.Image.createImage(atlas, 0, atlas.length));
                if (i == ThorSkin.GLASS) continue; // Apache's Thor is Skin Thor (ThorSkinCheck)
                short c = (short) i;
                outfits[i] = new PlayerEquip(new short[][] {{c, 0, ThorSkin.WEAPON}, {c, 1, (short) (ThorSkin.FIRST_VIP + i)},
                        {c, 2, 30112}, {c, 3, -1}, {c, 4, ThorSkin.CAPE}});
                if (outfits[i].equips[1] == null || outfits[i].equips[2] == null) throw new AssertionError("Missing Thor pieces " + i);
            }
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
            Gdx.app.exit();
        }
    }

    @Override
    public void render() {
        if (failure != null) return;
        try {
            Gdx.gl.glClearColor(.06f, .09f, .15f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            OrthographicCamera c = new OrthographicCamera();
            c.setToOrtho(true, 1200, 800);
            g.g.setProjectionMatrix(c.combined);
            mGraphics.zoomLevel = 2;
            g.begin();
            for (int i = 0; i < 10; i++) {
                if (outfits[i] == null) continue;
                for (int j = 0; j < 12; j++) outfits[i].paint(g, j < 6 ? 2 : 0, j % 6, 30 + j * 46, 36 + i * 38);
            }
            g.end();
            if (++frames == 12) {
                Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1200, 800);
                PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("review.root") + "/art/concepts/thor-all-classes/thor-all-poses-runtime.png"), p, -1, true);
                p.dispose();
                System.out.println("THOR CLASS RENDER PASSED");
                Gdx.app.exit();
            }
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
            Gdx.app.exit();
        }
    }

    public static void main(String[] a) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setInitialVisible(false);
        c.setWindowedMode(1200, 800);
        c.disableAudio(true);
        new Lwjgl3Application(new ThorClassRenderCheck(), c);
        if (failure != null) System.exit(1);
    }
}
