import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

/**
 * /api/admin/*: admin login plus player and catalog management for the server owner. Accounts have a role: "admin",
 * or "super" for a super-admin, who may also gift gear and items to players (scripts/Create-SuperAdmin.ps1 makes one).
 */
public final class LanAdmin implements HttpHandler {
    static final String DEFAULT_USERNAME = "admin";
    static final String DEFAULT_PASSWORD = "admin123";
    static final String ROLE_ADMIN = "admin", ROLE_SUPER = "super";
    private static final String COOKIE = "lanadmin";
    private static final Pattern ROUTE = Pattern.compile("/api/admin/([a-z]+)(?:/(\\d{1,9}))?(?:/([a-z]+))?");

    private static final class AdminSession {
        final String username;
        volatile boolean defaultPassword;
        /** Role at login, for the page; gift routes check the current role again (requireSuper). */
        final boolean superAdmin;

        AdminSession(String username, boolean defaultPassword, boolean superAdmin) {
            this.username = username;
            this.defaultPassword = defaultPassword;
            this.superAdmin = superAdmin;
        }
    }

    private final LanWeb.Database database;
    private final LanGameAdmin game;
    private final LongSupplier clock;
    private final LanApi.Sessions<AdminSession> sessions = new LanApi.Sessions<>(12L * 60 * 60 * 1000);
    private final LanApi.LoginGuard guard = new LanApi.LoginGuard(5, 5L * 60 * 1000);

    LanAdmin(LanWeb.Database database, LanGameAdmin game, LongSupplier clock) {
        this.database = database;
        this.game = game;
        this.clock = clock;
    }

    /** Creates the admin account table and seeds admin/admin123 when it is empty. */
    void ensureAccount() throws SQLException {
        try (Connection conn = database.open(); Statement statement = conn.createStatement()) {
            ensureTable(statement);
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM admin_account")) {
                rs.next();
                if (rs.getInt(1) > 0) return;
            }
            try (PreparedStatement insert = conn.prepareStatement("INSERT INTO admin_account (username, password) VALUES (?, ?)")) {
                insert.setString(1, DEFAULT_USERNAME);
                insert.setString(2, BCrypt.hashpw(DEFAULT_PASSWORD, BCrypt.gensalt()));
                insert.executeUpdate();
            }
        }
    }

    /** The admin account table with its role column (older installs get the column; their accounts stay admins). */
    static void ensureTable(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS admin_account (username VARCHAR(50) NOT NULL PRIMARY KEY, password VARCHAR(255) NOT NULL)");
        statement.executeUpdate("ALTER TABLE admin_account ADD COLUMN IF NOT EXISTS role VARCHAR(10) NOT NULL DEFAULT '" + ROLE_ADMIN + "'");
    }

    /** Routes only a super-admin may use: gifting gear and items. */
    static boolean superOnly(String key) {
        return key.equals("POST players # set") || key.equals("POST players # items") || key.equals("GET sets");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LanApi.respond(exchange, () -> {
            Matcher route = ROUTE.matcher(exchange.getRequestURI().getPath());
            if (!route.matches()) throw new NoSuchElementException("Không có chức năng này.");
            Integer id = route.group(2) == null ? null : Integer.valueOf(route.group(2));
            String key = exchange.getRequestMethod() + " " + route.group(1) + (id == null ? "" : " #")
                    + (route.group(3) == null ? "" : " " + route.group(3));
            JsonObject body = LanApi.body(exchange);
            if (key.equals("POST login")) return login(exchange, body);
            String token = LanApi.cookie(exchange, COOKIE);
            AdminSession session = sessions.get(token, clock.getAsLong());
            if (session == null) throw new LanApi.ApiError(401, "Bạn cần đăng nhập.");
            if (superOnly(key)) requireSuper(session);
            return switch (key) {
                case "GET me" -> me(session);
                case "POST logout" -> {
                    sessions.remove(token);
                    LanApi.setCookie(exchange, COOKIE, "", 0);
                    yield Map.of("ok", true);
                }
                case "POST password" -> changePassword(session, body);
                case "GET players" -> game.players();
                case "POST players # xu" -> game.addMoney(id, "xu", LanApi.intField(body, "amount"));
                case "POST players # luong" -> game.addMoney(id, "luong", LanApi.intField(body, "amount"));
                case "POST players # level" -> game.levelUp(id, LanApi.intField(body, "glassId"), LanApi.intField(body, "level"));
                case "POST players # password" -> game.resetPassword(id, LanApi.stringField(body, "password"));
                case "POST players # name" -> game.rename(id, LanApi.stringField(body, "username"));
                case "POST players # set" -> game.giftSet(id, LanApi.intField(body, "glassId"), LanApi.intField(body, "tier"),
                        LanApi.intField(body, "wear") != 0, LanApi.intField(body, "hiddenAtt"));
                case "POST players # items" -> game.giftItems(id, LanApi.intField(body, "itemId"), LanApi.intField(body, "amount"));
                case "GET levels" -> game.levels();
                case "GET sets" -> game.sets();
                case "GET characters" -> game.characters();
                case "PUT characters #" -> game.updateCharacter(id, LanApi.intField(body, "xu"), LanApi.intField(body, "luong"), LanApi.intField(body, "att"));
                case "GET equipment" -> game.equipment();
                case "PUT equipment #" -> game.updateEquipment(id, LanApi.intField(body, "xu"), LanApi.intField(body, "luong"),
                        LanApi.intArray(body, "ability"), LanApi.intArray(body, "percent"));
                case "GET items" -> game.items();
                case "PUT items #" -> game.updateItem(id, LanApi.intField(body, "xu"), LanApi.intField(body, "luong"));
                case "GET specials" -> game.specials();
                case "PUT specials #" -> game.updateSpecial(id, LanApi.intField(body, "xu"), LanApi.intField(body, "luong"));
                case "GET settings" -> game.settings();
                case "GET balance" -> game.balance();
                case "PUT settings" -> game.updateSettings(LanApi.intField(body, "defaultRules"));
                default -> throw new NoSuchElementException("Không có chức năng này.");
            };
        });
    }

    private Map<String, Object> login(HttpExchange exchange, JsonObject body) throws SQLException {
        String ip = LanApi.clientIp(exchange);
        long now = clock.getAsLong();
        long locked = guard.minutesLocked(ip, now);
        if (locked > 0) throw new LanApi.ApiError(429, "Sai mật khẩu quá nhiều lần. Bạn thử lại sau " + locked + " phút.");
        String username = LanApi.stringField(body, "username").trim();
        String password = LanApi.stringField(body, "password");
        String hash = passwordHash(username);
        if (hash == null || !BCrypt.checkpw(password, hash)) {
            guard.failed(ip, now);
            System.out.println("Admin login failed from " + ip);
            throw new LanApi.ApiError(401, "Sai tên đăng nhập hoặc mật khẩu.");
        }
        guard.succeeded(ip);
        boolean superAdmin = ROLE_SUPER.equals(role(username));
        AdminSession session = new AdminSession(username, BCrypt.checkpw(DEFAULT_PASSWORD, hash), superAdmin);
        LanApi.setCookie(exchange, COOKIE, sessions.create(session, now), 43200);
        System.out.println((superAdmin ? "Super-admin " : "Admin ") + username + " logged in from " + ip);
        return me(session);
    }

    /** Gift routes: the account must be a super-admin now (a revoked one is refused at once, not at its next login). */
    private void requireSuper(AdminSession session) throws SQLException {
        if (!ROLE_SUPER.equals(role(session.username))) {
            throw new LanApi.ApiError(403, "Chỉ super-admin mới được tặng đồ cho người chơi.");
        }
    }

    private Map<String, Object> changePassword(AdminSession session, JsonObject body) throws SQLException {
        String current = LanApi.stringField(body, "current");
        String next = LanApi.stringField(body, "next");
        String hash = passwordHash(session.username);
        if (hash == null || !BCrypt.checkpw(current, hash)) throw new LanApi.BadRequest("Mật khẩu hiện tại không đúng.");
        if (next.length() < 6 || next.length() > 64) throw new LanApi.BadRequest("Mật khẩu mới cần từ 6 đến 64 ký tự.");
        if (next.equals(current)) throw new LanApi.BadRequest("Mật khẩu mới phải khác mật khẩu hiện tại.");
        try (Connection conn = database.open();
             PreparedStatement update = conn.prepareStatement("UPDATE admin_account SET password = ? WHERE username = ?")) {
            update.setString(1, BCrypt.hashpw(next, BCrypt.gensalt()));
            update.setString(2, session.username);
            update.executeUpdate();
        }
        for (AdminSession other : sessions.values()) {
            if (other.username.equals(session.username)) other.defaultPassword = next.equals(DEFAULT_PASSWORD);
        }
        System.out.println("Admin " + session.username + " changed the password");
        return me(session);
    }

    private static Map<String, Object> me(AdminSession session) {
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("username", session.username);
        me.put("defaultPassword", session.defaultPassword);
        me.put("role", session.superAdmin ? ROLE_SUPER : ROLE_ADMIN);
        return me;
    }

    private String role(String username) throws SQLException {
        try (Connection conn = database.open();
             PreparedStatement statement = conn.prepareStatement("SELECT role FROM admin_account WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String passwordHash(String username) throws SQLException {
        try (Connection conn = database.open();
             PreparedStatement statement = conn.prepareStatement("SELECT password FROM admin_account WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
