import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

/** /api/player/*: login with the game account, then shop, bag and stat points for that player. */
public final class LanPlayer implements HttpHandler {
    private static final String COOKIE = "lanplayer";
    private static final Pattern ROUTE = Pattern.compile("/api/player/([a-z]+)(?:/([a-z]+))?");

    private record PlayerSession(int userId, String username) {
    }

    private final LanWeb.Database database;
    private final LanPlayerGame game;
    private final LongSupplier clock;
    private final LanApi.Sessions<PlayerSession> sessions = new LanApi.Sessions<>(12L * 60 * 60 * 1000);
    private final LanApi.LoginGuard guard = new LanApi.LoginGuard(5, 5L * 60 * 1000);

    LanPlayer(LanWeb.Database database, LanPlayerGame game, LongSupplier clock) {
        this.database = database;
        this.game = game;
        this.clock = clock;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LanApi.respond(exchange, () -> {
            Matcher route = ROUTE.matcher(exchange.getRequestURI().getPath());
            if (!route.matches()) throw new NoSuchElementException("Không có chức năng này.");
            String key = exchange.getRequestMethod() + " " + route.group(1) + (route.group(2) == null ? "" : " " + route.group(2));
            JsonObject body = LanApi.body(exchange);
            if (key.equals("POST login")) return login(exchange, body);
            String token = LanApi.cookie(exchange, COOKIE);
            PlayerSession session = sessions.get(token, clock.getAsLong());
            if (session == null) throw new LanApi.ApiError(401, "Bạn cần đăng nhập.");
            int id = session.userId();
            String name = session.username();
            return switch (key) {
                case "GET me" -> game.profile(id, name);
                case "POST logout" -> {
                    sessions.remove(token);
                    LanApi.setCookie(exchange, COOKIE, "", 0);
                    yield Map.of("ok", true);
                }
                case "POST select" -> game.select(id, name, LanApi.intField(body, "glassId"));
                case "POST points" -> game.addPoints(id, name, LanApi.intArray(body, "add"));
                case "POST points reset" -> game.resetPoints(id, name);
                case "POST wear" -> game.wear(id, name, LanApi.intField(body, "dbKey"));
                case "POST remove" -> game.remove(id, name, LanApi.intField(body, "dbKey"));
                case "GET shop" -> game.shop(id, name);
                case "POST buy equipment" -> game.buyEquipment(id, name, LanApi.intField(body, "equipId"), luong(body));
                case "POST buy item" -> game.buyItem(id, name, LanApi.intField(body, "id"), LanApi.intField(body, "amount"), luong(body));
                case "POST upgrade" -> game.upgrade(id, name, LanApi.intField(body, "dbKey"), body.has("insurance") ? LanApi.intField(body, "insurance") : 0);
                case "POST gem socket" -> game.socketGem(id, name, LanApi.intField(body, "dbKey"), LanApi.intField(body, "gemId"));
                case "POST gem unsocket" -> game.unsocketGems(id, name, LanApi.intField(body, "dbKey"));
                case "POST gem combine" -> game.combineGems(id, name, LanApi.intField(body, "gemId"), LanApi.intField(body, "amount"));
                case "POST buy special" -> game.buySpecial(id, name, LanApi.intField(body, "id"), LanApi.intField(body, "amount"), luong(body));
                case "POST chest open" -> game.openChest(id, name, LanApi.stringField(body, "chest"), luong(body));
                case "POST sell equipment" -> game.sellEquipment(id, name, LanApi.intArray(body, "dbKeys"));
                case "POST sell special" -> game.sellSpecial(id, name, LanApi.intField(body, "id"), LanApi.intField(body, "amount"));
                case "POST sell item" -> game.sellItem(id, name, LanApi.intField(body, "id"), LanApi.intField(body, "amount"));
                default -> throw new NoSuchElementException("Không có chức năng này.");
            };
        });
    }

    /** Signs the player out of the web everywhere, for example after the admin changed their password or name. */
    void endSessions(int userId) {
        sessions.removeIf(session -> session.userId() == userId);
    }

    private Map<String, Object> login(HttpExchange exchange, JsonObject body) throws Exception {
        String ip = LanApi.clientIp(exchange);
        long now = clock.getAsLong();
        long locked = guard.minutesLocked(ip, now);
        if (locked > 0) throw new LanApi.ApiError(429, "Sai mật khẩu quá nhiều lần. Bạn thử lại sau " + locked + " phút.");
        String typed = LanApi.stringField(body, "username").trim();
        String password = LanApi.stringField(body, "password");
        // Same lookup and check as the game server's login (ControlHandler, command 1).
        int userId = -1;
        String username = null;
        String hash = null;
        try (Connection conn = database.open();
             PreparedStatement statement = conn.prepareStatement("SELECT id, username, password FROM user WHERE LOWER(username) = LOWER(?)")) {
            statement.setString(1, typed);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getInt("id");
                    username = rs.getString("username");
                    hash = rs.getString("password");
                }
            }
        }
        if (hash == null || !checkPassword(password, hash)) {
            guard.failed(ip, now);
            throw new LanApi.ApiError(401, "Sai tên đăng nhập hoặc mật khẩu.");
        }
        guard.succeeded(ip);
        Map<String, Object> profile = game.profile(userId, username);
        LanApi.setCookie(exchange, COOKIE, sessions.create(new PlayerSession(userId, username), now), 43200);
        System.out.println("Player " + username + " signed in on the web from " + ip);
        return profile;
    }

    private static boolean checkPassword(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash.replaceFirst("^\\$2[aby]\\$", "\\$2a\\$"));
        } catch (IllegalArgumentException e) {
            return false; // not a bcrypt hash
        }
    }

    private static boolean luong(JsonObject body) {
        String currency = LanApi.stringField(body, "currency");
        if (currency.equals("xu")) return false;
        if (currency.equals("luong")) return true;
        throw new LanApi.BadRequest("Chọn trả bằng xu hoặc lượng.");
    }
}
