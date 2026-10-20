import mobiarmy.server.Server;
import java.nio.file.Files;
import java.nio.file.Path;

public class LanServer {
    public static void main(String[] args) {
        Server server = new Server(Integer.getInteger("army.port", 8122));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
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
    }
}
