import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import mobiarmy.io.Message;
import mobiarmy.server.Caption;
import mobiarmy.server.ControlHandler;
import mobiarmy.server.GameData;
import mobiarmy.server.Pack;
import mobiarmy.server.Session;
import mobiarmy.server.SessionHandler;
import mobiarmy.server.User;
import sun.misc.Unsafe;

/**
 * After login the client fetches its game files one by one (message 90). It asks for the map icons (1), the player
 * layers (3) and the level captions (5) with another file's version (its tile map version, never set, its icon version
 * and its equipment version), and reads the file whenever its own version of that file differs. So the server sends
 * these three with their file whatever version it is sent: a client whose equipment version happened to be the caption
 * version (13) got no captions, read past the end and waited at "Xin chờ..." for good. No database needed.
 */
public class LoginDataCheck {
    static int checks;

    /** A game connection that only records what the server sends. */
    static final class Capture extends Session {
        List<byte[]> sent;
        List<Integer> commands;

        Capture() throws Exception {
            super(null, -1);
        }

        @Override
        public void sendMessage(Message message) {
            commands.add((int) message.getCommand());
            sent.add(message.getData());
        }
    }

    public static void main(String[] args) throws Exception {
        GameData.mapIcon = new Pack();
        GameData.mapIcon.addEntry("0.png", new byte[] {1, 2, 3});
        GameData.player = new Pack();
        GameData.player.addEntry("0.png", new byte[] {4, 5});
        Caption caption = new Caption();
        caption.caption = "Tân binh";
        caption.level = 1;
        Caption.entrys = new Caption[] {caption};

        // {file, the server's version of it}; asked with that very version too, as an old client may.
        int[][] files = {{1, 12}, {3, 12}, {5, 13}};
        for (int[] file : files) {
            for (int asked : new int[] {file[1], 0, file[1] - 1, 19}) {
                Capture session = session();
                session.controlHandler.handleControlMessage(new Message((byte) 90, new byte[] {(byte) file[0], (byte) asked}));
                check(session.commands.equals(List.of(90)), "file " + file[0] + " asked with version " + asked + " is answered");
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(session.sent.get(0)));
                check(in.readByte() == file[0] && in.readByte() == file[1], "the answer names file " + file[0] + " and version " + file[1]);
                int length = in.readUnsignedShort();
                byte[] data = new byte[length];
                in.readFully(data);
                check(length > 0 && in.available() == 0, "file " + file[0] + " comes with its data when asked with version " + asked);
                if (file[0] == 5) {
                    // As the client reads the captions (CCanvas.readMess 2): a count, then a name and a level each.
                    DataInputStream captions = new DataInputStream(new ByteArrayInputStream(data));
                    check(captions.readByte() == 1 && captions.readUTF().equals("Tân binh") && captions.readByte() == 1,
                            "the captions read as the client reads them");
                }
            }
        }
        System.out.println("LOGIN DATA PASSED: " + checks + " checks");
    }

    static Capture session() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        Capture session = (Capture) unsafe.allocateInstance(Capture.class);
        session.sent = new ArrayList<>();
        session.commands = new ArrayList<>();
        session.sessionHandler = new SessionHandler(session);
        session.controlHandler = new ControlHandler(session);
        session.user = (User) unsafe.allocateInstance(User.class); // signed in, in no room
        return session;
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }
}
