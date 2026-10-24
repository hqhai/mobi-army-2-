import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Super-admins on the admin page: only they may gift gear and items (LanAdmin.superOnly); CreateSuperAdmin makes an
 * account one (a BCrypt hash, role "super") and takes the right back. Uses a throwaway account it removes again.
 */
public class AdminRoleCheck {
    static int checks;

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
        System.out.println("PASS " + what);
    }

    public static void main(String[] args) throws Exception {
        check(LanAdmin.superOnly("POST players # set") && LanAdmin.superOnly("POST players # items") && LanAdmin.superOnly("GET sets"),
                "gifting gear and items is for super-admins");
        check(!LanAdmin.superOnly("POST players # xu") && !LanAdmin.superOnly("GET players") && !LanAdmin.superOnly("GET items")
                        && !LanAdmin.superOnly("POST players # level"), "everything else stays open to admins");
        String name = "rolecheck_" + Long.toHexString(System.nanoTime()).substring(0, 8);
        try (Connection conn = DriverManager.getConnection(System.getProperty("army.db.url"), System.getProperty("army.db.user", "root"),
                System.getenv("ARMY_DB_PASSWORD"))) {
            try {
                boolean refused = false;
                try {
                    CreateSuperAdmin.grant(conn, name, "short");
                } catch (IllegalArgumentException expected) {
                    refused = true;
                }
                check(refused && row(conn, name) == null, "a password under 8 characters is refused");
                CreateSuperAdmin.grant(conn, name, "first-secret");
                String[] made = row(conn, name);
                check(made != null && LanAdmin.ROLE_SUPER.equals(made[1]) && BCrypt.checkpw("first-secret", made[0])
                        && !made[0].contains("first-secret"), "a new super-admin, its password stored as a BCrypt hash");
                CreateSuperAdmin.revoke(conn, name);
                check(LanAdmin.ROLE_ADMIN.equals(row(conn, name)[1]) && BCrypt.checkpw("first-secret", row(conn, name)[0]),
                        "revoked: a plain admin with the same password");
                CreateSuperAdmin.grant(conn, name, "second-secret");
                check(LanAdmin.ROLE_SUPER.equals(row(conn, name)[1]) && BCrypt.checkpw("second-secret", row(conn, name)[0]),
                        "an existing admin made super-admin again with a new password");
            } finally {
                try (PreparedStatement delete = conn.prepareStatement("DELETE FROM admin_account WHERE username = ?")) {
                    delete.setString(1, name);
                    delete.executeUpdate();
                }
            }
            check(row(conn, name) == null, "the throwaway account is gone");
        }
        System.out.println("ADMIN ROLE PASSED: " + checks + " checks");
    }

    /** Password hash and role of an admin account, or null. */
    static String[] row(Connection conn, String username) throws Exception {
        try (PreparedStatement select = conn.prepareStatement("SELECT password, role FROM admin_account WHERE username = ?")) {
            select.setString(1, username);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? new String[] {rs.getString(1), rs.getString(2)} : null;
            }
        }
    }
}
