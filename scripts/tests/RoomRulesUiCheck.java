package screen;

import CLib.mGraphics;
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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import model.PlayerInfo;
import network.Command;
import network.GameService;
import network.IMessageHandler;
import network.ISession;
import network.Message;
import network.MessageHandler;

/**
 * Room rules in the waiting room: server message 61, the owner's rules menu, the guests' explanation and the
 * "Luật: ..." label. Run with the working directory holding a client's rms/ data so map and player art load,
 * and -Droom.preview=<png> for a picture of the room screen.
 */
public class RoomRulesUiCheck extends MainGame {
    static Throwable failure;
    static int checks;
    static final List<Message> sent = new ArrayList<>();
    mGraphics graphics;
    int frames;
    Pixmap withoutRules;

    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    static Message fromServer(int rules, boolean changed) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(rules);
        out.writeBoolean(changed);
        return new Message((byte) 61, bytes.toByteArray());
    }

    static Command item(String caption) {
        for (Object entry : CCanvas.menu.menuItems) {
            Command command = (Command) entry;
            if (command.caption.equals(caption)) return command;
        }
        return null;
    }

    static List<String> captions() {
        List<String> captions = new ArrayList<>();
        for (Object entry : CCanvas.menu.menuItems) captions.add(((Command) entry).caption);
        return captions;
    }

    static PlayerInfo player(int id, String name, boolean ready) {
        PlayerInfo player = new PlayerInfo();
        player.IDDB = id;
        player.name = name;
        player.isReady = ready;
        player.gun = 0;
        return player;
    }

    @Override
    public void create() {
        try {
            super.create();
            graphics = new mGraphics(new SpriteBatch());
            mGraphics.zoomLevel = 2;
            CCanvas.width = 640;
            CCanvas.hieght = 360;
            GameService.gI().setSession(new ISession() {
                public boolean isConnected() { return true; }
                public void setHandler(IMessageHandler handler) { }
                public void connect(String host, String port) { }
                public void sendMessage(Message message) { sent.add(message); }
            });
            TerrainMidlet.myInfo = player(1, "chuphong", false);
            CCanvas.sendMapData(); // what the client does after login: maps, players and gear from the rms/ cache
            PrepareScr room = CCanvas.prepareScr;
            Vector<PlayerInfo> players = new Vector<>();
            players.add(player(1, "chuphong", false));
            players.add(player(2, "khach", true));
            for (int i = 2; i < 8; i++) players.add(player(-1, "", false));
            room.setPlayers(1, 1000, players);

            check(PrepareScr.rules == -1 && PrepareScr.rulesLabel() == null, "no rules label until the server sends the rules");
            room.doShowMenuPrepare();
            check(item("Luật trận đấu") == null, "no rules menu on a server that never sends rules");

            MessageHandler.gI().onMessage(fromServer(1, false));
            check(PrepareScr.rules == 1 && "Luật: Cân bằng".equals(PrepareScr.rulesLabel()), "server message 61 sets the label: " + PrepareScr.rulesLabel());
            check(room.playerInfos.get(1).isReady, "rules sent on joining keep ready flags");

            room.doShowMenuPrepare();
            Command rulesItem = item("Luật trận đấu");
            check(rulesItem != null, "room menu has Luật trận đấu: " + captions());
            rulesItem.action.perform();
            check(captions().equals(java.util.Arrays.asList("Tự do", "> Cân bằng", "Sát thương gốc")),
                    "owner picks from the three rules, current one marked: " + captions());
            sent.clear();
            item("Sát thương gốc").action.perform();
            check(sent.size() == 1 && sent.get(0).command == 61 && java.util.Arrays.equals(sent.get(0).getData(), new byte[] {2}),
                    "picking Sát thương gốc sends message 61 with 2");

            MessageHandler.gI().onMessage(fromServer(2, true));
            check(PrepareScr.rules == 2 && !room.playerInfos.get(1).isReady, "a rules change from the owner clears ready flags like a new bet");

            TerrainMidlet.myInfo = player(2, "khach", false);
            room.setPlayers(1, 1000, players);
            room.doShowMenuPrepare();
            sent.clear();
            item("Luật trận đấu").action.perform();
            java.lang.reflect.Field info = model.MsgDlg.class.getDeclaredField("info");
            info.setAccessible(true);
            String text = String.join(" ", (String[]) info.get(CCanvas.msgdlg));
            check(CCanvas.currentDialog == CCanvas.msgdlg && text.contains("Sát thương gốc") && sent.isEmpty(),
                    "a guest reads what the rules mean and cannot change them: " + text);
            CCanvas.currentDialog = null;
            TerrainMidlet.myInfo = player(1, "chuphong", false);
            room.setPlayers(1, 1000, players);
            BoardListScr.boardName = "Phòng thử luật"; // sent by the server when joining a room
            room.x_item = CCanvas.width - (item.Item.iWitdh * 18 + 5); // what show() sets, without the shop icons it needs
            room.y_item = CCanvas.hieght - (CScreen.cmdH + 42) + 5;
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
            Gdx.app.exit();
        }
    }

    /**
     * Draws the top of the waiting room (background, players, map, room name, bet) without or with the label.
     * The item slots below need the shop data a real server sends, so they are left out.
     */
    Pixmap draw(byte rules) throws Exception {
        PrepareScr.rules = rules;
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        OrthographicCamera camera = new OrthographicCamera();
        camera.setToOrtho(true, 1280, 720);
        graphics.g.setProjectionMatrix(camera.combined);
        graphics.begin();
        PrepareScr room = CCanvas.prepareScr;
        CScreen.paintDefaultBg(graphics);
        room.paintBack(graphics);
        for (String part : new String[] {"paintPlayerPos", "paintPlayer", "paintMap"}) {
            java.lang.reflect.Method paint = PrepareScr.class.getDeclaredMethod(part, mGraphics.class);
            paint.setAccessible(true);
            paint.invoke(room, graphics);
        }
        graphics.end();
        return Pixmap.createFromFrameBuffer(0, 0, 1280, 720);
    }

    @Override
    public void render() {
        if (failure != null) return;
        try {
            if (++frames < 10) return;
            if (map.MM.mapName == null) {
                System.out.println("ROOM RULES UI PASSED: " + checks + " checks (no rms/ game data in the working directory, picture skipped)");
                Gdx.app.exit();
                return;
            }
            if (withoutRules == null) {
                withoutRules = draw((byte) -1);
                return;
            }
            Pixmap withRules = draw((byte) 1);
            int changed = 0;
            for (int y = 0; y < 720; y++) {
                for (int x = 0; x < 1280; x++) if (withRules.getPixel(x, y) != withoutRules.getPixel(x, y)) changed++;
            }
            check(changed > 200, "the label is drawn on the room screen (" + changed + " pixels)");
            String preview = System.getProperty("room.preview");
            if (preview != null) PixmapIO.writePNG(Gdx.files.absolute(preview), withRules, -1, true);
            withRules.dispose();
            withoutRules.dispose();
            System.out.println("ROOM RULES UI PASSED: " + checks + " checks");
            Gdx.app.exit();
        } catch (Throwable t) {
            failure = t;
            t.printStackTrace();
            Gdx.app.exit();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(1280, 720);
        config.disableAudio(true);
        new Lwjgl3Application(new RoomRulesUiCheck(), config);
        if (failure != null) System.exit(1);
    }
}
