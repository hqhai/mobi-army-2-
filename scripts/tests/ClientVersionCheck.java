import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mobiarmy.io.Message;
import mobiarmy.server.ClientVersion;
import mobiarmy.server.ControlHandler;
import mobiarmy.server.Session;
import mobiarmy.server.SessionHandler;
import sun.misc.Unsafe;

/**
 * Login refuses LAN clients older than ClientVersion.REQUIRED, before any account lookup. Run from the repository
 * root: it also checks that the client source asks for the same build. No database needed.
 */
public class ClientVersionCheck {
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
        String client = Files.readString(Path.of("client/core/src/com/teamobi/mobiarmy2/GameMidlet.java"), StandardCharsets.UTF_8);
        Matcher build = Pattern.compile("LAN_BUILD\\s*=\\s*(\\d+)").matcher(client);
        check(build.find() && Integer.parseInt(build.group(1)) == ClientVersion.LATEST,
                "client LAN_BUILD equals server LATEST (" + ClientVersion.LATEST + ")");
        check(ClientVersion.REQUIRED <= ClientVersion.LATEST && ClientVersion.KICK_BUILD <= ClientVersion.LATEST,
                "the required build and the kick's are not newer than the latest");

        check(ClientVersion.problem(0) != null && ClientVersion.problem(0).contains("cũ"), "a client without a build is old");
        check(ClientVersion.problem(ClientVersion.REQUIRED) == null && ClientVersion.problem(ClientVersion.REQUIRED + 1) == null,
                "the required build and newer ones may log in");
        if (ClientVersion.REQUIRED > 1) {
            check(ClientVersion.problem(ClientVersion.REQUIRED - 1).contains("LAN " + (ClientVersion.REQUIRED - 1)), "the message names the old build");
        }
        System.setProperty("army.client.minBuild", "0");
        check(ClientVersion.problem(0) == null, "-Darmy.client.minBuild=0 lets old clients in");
        System.clearProperty("army.client.minBuild");

        Capture session = session();
        session.controlHandler.handleControlMessage(login(false, 0));
        check(session.user == null && session.commands.equals(List.of(45)), "a login without a build gets one notice and no account");
        String notice = new DataInputStream(new java.io.ByteArrayInputStream(session.sent.get(0))).readUTF();
        check(notice.contains("cũ") && notice.contains("LAN " + ClientVersion.REQUIRED), "the notice says to update: " + notice);

        session = session();
        session.controlHandler.handleControlMessage(login(true, ClientVersion.REQUIRED - 1));
        check(session.user == null && session.commands.equals(List.of(45)), "an older build is refused too");
        check(session.build == ClientVersion.REQUIRED - 1, "the session keeps the build it logged in with");
        System.out.println("CLIENT VERSION PASSED: " + checks + " checks");
    }

    static Capture session() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Capture session = (Capture) ((Unsafe) field.get(null)).allocateInstance(Capture.class);
        session.sent = new ArrayList<>();
        session.commands = new ArrayList<>();
        session.sessionHandler = new SessionHandler(session);
        session.controlHandler = new ControlHandler(session);
        return session;
    }

    /** Message 1 as the client sends it; withBuild=false is a client from before the build number. */
    static Message login(boolean withBuild, int build) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("someone");
        out.writeUTF("abc123");
        out.writeUTF("2.4.1");
        if (withBuild) out.writeInt(build);
        return new Message((byte) 1, bytes.toByteArray());
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }
}
