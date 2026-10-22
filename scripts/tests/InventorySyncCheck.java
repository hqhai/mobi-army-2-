package screen;

import Equipment.Equip;
import Equipment.PlayerEquip;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.teamobi.mobiarmy2.MainGame;
import coreLG.CCanvas;
import coreLG.TerrainMidlet;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Vector;
import model.PlayerInfo;
import network.Message;
import network.MessageHandler;

/**
 * Gear updates from the server (message 27, action 2) land in the bag whichever screen is open. The web page
 * upgrades gear and sockets gems at any time; the client used to look the item up only while the bag or equipment
 * screen was showing and dropped the update otherwise.
 */
public class InventorySyncCheck extends MainGame {
    static Throwable failure;
    static int checks;

    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    static Equip gear(int dbKey, int type) {
        Equip equip = new Equip();
        equip.dbKey = dbKey | 0x10000;
        equip.glass = 0;
        equip.type = (byte) type;
        equip.id = 3;
        equip.name = "test";
        return equip;
    }

    /** Message 27 with one "update" action per dbKey: ability/percent pairs, free gem holes, days left. */
    static Message update(int[] dbKeys, int hp, int attack, int days) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(dbKeys.length);
        for (int dbKey : dbKeys) {
            out.writeByte(2);
            out.writeInt(dbKey | 0x10000);
            out.writeByte(10);
            byte[] pairs = {(byte) hp, 0, (byte) attack, 5, 0, 0, 0, 0, 0, 0};
            out.write(pairs);
            out.writeByte(3);
            out.writeByte(days);
        }
        return new Message((byte) 27, bytes.toByteArray());
    }

    @Override
    public void create() {
        try {
            super.create();
            CCanvas.width = 640;
            CCanvas.hieght = 360;
            PlayerInfo me = new PlayerInfo();
            me.IDDB = 1;
            me.gun = 0;
            TerrainMidlet.myInfo = me;
            me.myEquip = new PlayerEquip(); // nothing worn; the catalog is not loaded here
            Equip hat = gear(7, 1);
            Equip gun = gear(8, 0);
            EquipScreen.inventory = new Vector<>();
            EquipScreen.inventory.add(hat);
            EquipScreen.inventory.add(gun);
            CCanvas.curScr = CCanvas.shopEquipScr; // browsing the in-game shop, not the bag or the equipment screen

            MessageHandler.gI().onMessage(update(new int[] {7}, 20, 30, 29));
            check(hat.inv_ability[0] == 20 && hat.inv_ability[1] == 30 && hat.inv_percen[1] == 5 && hat.date == 29,
                    "an upgrade from the web reaches the bag while another screen is open");

            MessageHandler.gI().onMessage(update(new int[] {99, 8}, 11, 12, 28));
            check(gun.inv_ability[0] == 11 && gun.inv_ability[1] == 12 && gun.date == 28,
                    "an unknown item in the same message does not stop the next update");
            check(hat.inv_ability[0] == 20, "other gear is left alone");
            System.out.println("INVENTORY SYNC PASSED: " + checks + " checks");
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
        }
        Gdx.app.exit();
    }

    /** Nothing to draw: the shop screen picked above has no shop data here. */
    @Override
    public void render() {
        Gdx.app.exit();
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(640, 360);
        config.disableAudio(true);
        new Lwjgl3Application(new InventorySyncCheck(), config);
        if (failure != null) System.exit(1);
    }
}
