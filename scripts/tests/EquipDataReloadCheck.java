import Equipment.PlayerEquip;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.teamobi.mobiarmy2.MainGame;
import coreLG.CCanvas;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Equipment data read twice in one run (the cached copy at start-up, then the server's newer one at login) ends with
 * the newer bullet sheets: new bullet kinds (Thor, costume bullets) show without restarting the game.
 */
public class EquipDataReloadCheck extends MainGame {
    static Throwable failure;
    static int checks;
    int frames;

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }

    /** Equipment data (CCanvas.readMess type 1) with no characters and ten bullet sheets of this many kinds. */
    static byte[] data(int kinds) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0);
        out.writeShort(0);
        for (int c = 0; c < 10; c++) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(20, 20 * kinds * (c == 5 ? 4 : 1), BufferedImage.TYPE_INT_ARGB), "png", png);
            out.writeShort(png.size());
            png.writeTo(out);
        }
        return bytes.toByteArray();
    }

    @Override
    public void create() {
        try {
            super.create();
            CCanvas.readMess(data(9), (byte) 1);
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
            frames++;
            if (frames == 2) {
                check(PlayerEquip.bullets[4] != null && PlayerEquip.bullets[4].image.getHeight() == 180, "cached sheets loaded at start-up");
                CCanvas.readMess(data(13), (byte) 1);
            }
            if (frames == 4) {
                for (int c = 0; c < 10; c++) {
                    int height = 20 * 13 * (c == 5 ? 4 : 1);
                    check(PlayerEquip.bullets[c] != null && PlayerEquip.bullets[c].image.getHeight() == height,
                            "sheet " + c + " is the server's newer one");
                }
                System.out.println("EQUIP DATA RELOAD PASSED: " + checks + " checks");
                Gdx.app.exit();
            }
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
            Gdx.app.exit();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(320, 200);
        config.disableAudio(true);
        new Lwjgl3Application(new EquipDataReloadCheck(), config);
        if (failure != null) System.exit(1);
    }
}
