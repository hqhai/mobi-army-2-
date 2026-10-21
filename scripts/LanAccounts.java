import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.mindrot.jbcrypt.BCrypt;

public final class LanAccounts {
    // The in-game login fields only accept letters and digits when typed directly.
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9]{3,20}");
    private static final Pattern PASSWORD = Pattern.compile("[a-zA-Z0-9]{6,30}");

    private LanAccounts() {
    }

    static String problem(String username, String password) {
        String problem = usernameProblem(username);
        return problem != null ? problem : passwordProblem(password);
    }

    static String usernameProblem(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            return "Tên đăng nhập gồm 3-20 chữ cái không dấu hoặc chữ số.";
        }
        return null;
    }

    static String passwordProblem(String password) {
        if (password == null || !PASSWORD.matcher(password).matches()) {
            return "Mật khẩu gồm 6-30 chữ cái không dấu hoặc chữ số.";
        }
        return null;
    }

    // `user.username` has no unique index and login matches it case-insensitively,
    // so check-then-insert must not interleave.
    static synchronized boolean create(Connection conn, String username, String password) throws SQLException {
        if (taken(conn, username, -1)) return false;
        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO user (username, password) VALUES (?, ?)")) {
            insert.setString(1, username);
            insert.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
            insert.executeUpdate();
        }
        return true;
    }

    /** Renames the login and the saved in-game name together; false when another account has the name. */
    static synchronized boolean rename(Connection conn, int userId, String username) throws SQLException {
        if (taken(conn, username, userId)) return false;
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement login = conn.prepareStatement("UPDATE user SET username = ? WHERE id = ?");
             PreparedStatement display = conn.prepareStatement("UPDATE user_ SET name = ? WHERE user_id = ?")) {
            login.setString(1, username);
            login.setInt(2, userId);
            login.executeUpdate();
            display.setString(1, username);
            display.setInt(2, userId);
            display.executeUpdate();
            conn.commit();
            return true;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    static void setPassword(Connection conn, int userId, String password) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement("UPDATE user SET password = ? WHERE id = ?")) {
            update.setString(1, BCrypt.hashpw(password, BCrypt.gensalt()));
            update.setInt(2, userId);
            update.executeUpdate();
        }
    }

    private static boolean taken(Connection conn, String username, int exceptUserId) throws SQLException {
        try (PreparedStatement taken = conn.prepareStatement("SELECT 1 FROM user WHERE LOWER(username) = LOWER(?) AND id <> ? LIMIT 1")) {
            taken.setString(1, username);
            taken.setInt(2, exceptUserId);
            try (ResultSet rs = taken.executeQuery()) {
                return rs.next();
            }
        }
    }
}
