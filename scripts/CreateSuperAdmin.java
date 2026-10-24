import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Makes an admin-page account a super-admin, who may also gift gear and items to players, creating it when missing;
 * with --revoke it becomes a plain admin again. The password comes in ARMY_ACCOUNT_PASSWORD (scripts/Create-SuperAdmin.ps1
 * asks for it without showing it) and is stored as a BCrypt hash, like every admin password.
 */
public class CreateSuperAdmin {
    public static void main(String[] args) throws Exception {
        boolean revoke = args.length == 2 && args[1].equals("--revoke");
        if ((args.length != 1 && !revoke) || !args[0].matches("[a-zA-Z0-9_]{3,50}")) {
            throw new IllegalArgumentException("Usage: CreateSuperAdmin <username of 3-50 letters, numbers or underscores> [--revoke]");
        }
        try (Connection conn = DriverManager.getConnection(
                System.getProperty("army.db.url"), System.getProperty("army.db.user"), System.getenv("ARMY_DB_PASSWORD"))) {
            if (revoke) {
                revoke(conn, args[0]);
                System.out.println(args[0] + " is a plain admin again.");
            } else {
                grant(conn, args[0], System.getenv("ARMY_ACCOUNT_PASSWORD"));
                System.out.println(args[0] + " is a super-admin. Log in on the admin page with this account to gift gear and items.");
            }
        }
    }

    /** Creates the account as a super-admin, or makes an existing one a super-admin with this password. */
    static void grant(Connection conn, String username, String password) throws Exception {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new IllegalArgumentException("Use a password of 8 to 64 characters.");
        }
        try (Statement statement = conn.createStatement()) {
            LanAdmin.ensureTable(statement);
        }
        try (PreparedStatement upsert = conn.prepareStatement(
                "INSERT INTO admin_account (username, password, role) VALUES (?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE password = VALUES(password), role = VALUES(role)")) {
            upsert.setString(1, username);
            upsert.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
            upsert.setString(3, LanAdmin.ROLE_SUPER);
            upsert.executeUpdate();
        }
    }

    /** The account becomes a plain admin; its password stays. */
    static void revoke(Connection conn, String username) throws Exception {
        try (Statement statement = conn.createStatement()) {
            LanAdmin.ensureTable(statement);
        }
        try (PreparedStatement update = conn.prepareStatement("UPDATE admin_account SET role = ? WHERE username = ?")) {
            update.setString(1, LanAdmin.ROLE_ADMIN);
            update.setString(2, username);
            if (update.executeUpdate() == 0) throw new IllegalArgumentException("No admin account " + username + ".");
        }
    }
}
