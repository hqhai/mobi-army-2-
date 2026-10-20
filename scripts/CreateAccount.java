import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import org.mindrot.jbcrypt.BCrypt;

public class CreateAccount {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[a-zA-Z0-9_]{3,20}")) {
            throw new IllegalArgumentException("Provide a username of 3-20 letters, numbers or underscores.");
        }
        try (Connection conn = DriverManager.getConnection(
                System.getProperty("army.db.url"), System.getProperty("army.db.user"),
                System.getenv("ARMY_DB_PASSWORD"));
             PreparedStatement statement = conn.prepareStatement(
                     "INSERT INTO user (username, password) VALUES (?, ?)")) {
            statement.setString(1, args[0]);
            statement.setString(2, BCrypt.hashpw(System.getenv("ARMY_ACCOUNT_PASSWORD"), BCrypt.gensalt()));
            statement.executeUpdate();
            System.out.println("Created account: " + args[0]);
        }
    }
}
