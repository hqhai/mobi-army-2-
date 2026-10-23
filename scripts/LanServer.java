import mobiarmy.server.Server;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.sun.net.httpserver.HttpHandler;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class LanServer {
    public static void main(String[] args) {
        int port = Integer.getInteger("army.port", 8122);
        Server server = new Server(port);
        LanWeb web = startWeb(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (web != null) web.stop();
            server.stop();
        }));
        String stopFile = System.getProperty("army.stopFile");
        if (stopFile != null) {
            Thread monitor = new Thread(() -> {
                try {
                    while (true) {
                        if (Files.exists(Path.of(stopFile))) {
                            if (server.stop()) System.exit(0);
                            Thread.sleep(5000);
                        } else {
                            Thread.sleep(500);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "lan-stop-monitor");
            monitor.setDaemon(true);
            monitor.start();
        }
        server.start();
        // start() returns once the game server stops or fails to bind; don't let the web page keep the JVM alive.
        if (web != null) web.stop();
    }

    private static LanWeb startWeb(int gamePort) {
        int webPort = Integer.getInteger("army.web.port", 8123);
        if (webPort <= 0) return null;
        LanWeb.Database database = Server.dbManager::openConnection;
        LanApi.GameThread gameThread = new LanApi.GameThread() {
            @Override
            public <T> T call(Callable<T> task) throws Exception {
                return Server.callOnGameThread(task, 5000);
            }
        };
        Map<String, HttpHandler> apis = new LinkedHashMap<>();
        // Before Server.start() loads the special item catalog.
        try (Connection conn = database.open()) {
            LanChests.ensureCatalog(conn);
            LanPlayerGame.ensureUpgradeCatalog(conn);
            LanGameAdmin.ensureMatchResults(conn);
            LanGameAdmin.ensureItems(conn);
        } catch (SQLException e) {
            System.err.println("LAN tables missing, database setup failed: " + e.getMessage());
        }
        LanPlayer player = new LanPlayer(database, new LanPlayerGame(gameThread), System::currentTimeMillis);
        LanAdmin admin = startAdmin(database, new LanGameAdmin(database, gameThread, player::endSessions));
        if (admin != null) apis.put("/api/admin/", admin);
        apis.put("/api/player/", player);
        apis.put("/game-res/", LanWeb.gameResources(Path.of("res"))); // the server runs from server/, next to res/
        try {
            return LanWeb.start(webPort, Path.of(System.getProperty("army.web.root", "../web")), gamePort, database, apis);
        } catch (IOException e) {
            System.err.println("Registration page disabled, cannot listen on port " + webPort + ": " + e.getMessage());
            return null;
        }
    }

    private static LanAdmin startAdmin(LanWeb.Database database, LanGameAdmin game) {
        LanAdmin admin = new LanAdmin(database, game, System::currentTimeMillis);
        try {
            game.loadSettings();
            admin.ensureAccount();
            return admin;
        } catch (SQLException e) {
            System.err.println("Admin page disabled, database setup failed: " + e.getMessage());
            return null;
        }
    }
}
