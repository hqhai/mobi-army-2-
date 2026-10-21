import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** Web pages for the LAN server: static files from the web root, /api/register, /api/info and the admin API. */
public final class LanWeb {
    interface Database {
        Connection open() throws SQLException;
    }

    private static final int MAX_FORM_BYTES = 4096;
    private static final Gson GSON = new Gson();
    private static final String CSP = "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "png", "image/png",
            "jpg", "image/jpeg",
            "svg", "image/svg+xml",
            "ico", "image/x-icon",
            "zip", "application/zip");

    private static final Pattern GAME_RESOURCE = Pattern.compile("/(bigImage\\d|icon/item/\\d{1,3})\\.png");

    private final HttpServer server;
    private final ExecutorService workers;

    private LanWeb(HttpServer server, ExecutorService workers) {
        this.server = server;
        this.workers = workers;
    }

    /** Starts the pages; extra maps context paths (for example "/api/admin/") to their handlers. */
    static LanWeb start(int port, Path root, int gamePort, Database database, Map<String, HttpHandler> extra) throws IOException {
        Path webRoot = root.toAbsolutePath().normalize();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService workers = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "lan-web");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(workers);
        server.createContext("/api/register", guarded(exchange -> register(exchange, database)));
        server.createContext("/api/info", guarded(exchange -> info(exchange, gamePort)));
        extra.forEach((path, handler) -> server.createContext(path, guarded(handler)));
        server.createContext("/", guarded(exchange -> serveFile(exchange, webRoot, exchange.getRequestURI().getPath())));
        server.start();
        System.out.println("Registration page listening on port " + port);
        return new LanWeb(server, workers);
    }

    void stop() {
        server.stop(1);
        workers.shutdownNow();
    }

    private static HttpHandler guarded(HttpHandler handler) {
        return exchange -> {
            try (exchange) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("X-Content-Type-Options", "nosniff");
                headers.set("Referrer-Policy", "no-referrer");
                headers.set("Content-Security-Policy", CSP);
                handler.handle(exchange);
            }
        };
    }

    private static void register(HttpExchange exchange, Database database) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendJson(exchange, 405, Map.of("error", "Chỉ hỗ trợ gửi biểu mẫu (POST)."));
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_FORM_BYTES + 1);
        if (body.length > MAX_FORM_BYTES) {
            sendJson(exchange, 413, Map.of("error", "Dữ liệu gửi lên quá lớn."));
            return;
        }
        Map<String, String> form;
        try {
            form = parseForm(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", "Dữ liệu gửi lên không hợp lệ."));
            return;
        }
        // The game client lowercases the name on login; storing it lowercase keeps name lookups consistent.
        String username = form.getOrDefault("username", "").trim().toLowerCase(Locale.ROOT);
        String password = form.get("password");
        String problem = LanAccounts.problem(username, password);
        if (problem != null) {
            sendJson(exchange, 400, Map.of("error", problem));
            return;
        }
        boolean created;
        try (Connection conn = database.open()) {
            created = LanAccounts.create(conn, username, password);
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "Máy chủ đang bận, bạn thử lại sau ít phút nhé."));
            return;
        }
        if (!created) {
            sendJson(exchange, 409, Map.of("error", "Tên đăng nhập này đã có người dùng, bạn chọn tên khác nhé."));
            return;
        }
        System.out.println("Registered account " + username + " from " + exchange.getRemoteAddress().getAddress().getHostAddress());
        sendJson(exchange, 201, Map.of("username", username));
    }

    private static void info(HttpExchange exchange, int gamePort) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendJson(exchange, 405, Map.of("error", "Chỉ hỗ trợ GET."));
            return;
        }
        sendJson(exchange, 200, Map.of("gamePort", gamePort));
    }

    /** Game images the pages show (equipment sheets and special-item icons), read from the server's res folder. */
    static HttpHandler gameResources(Path resDir) {
        Path root = resDir.toAbsolutePath().normalize();
        return exchange -> {
            String path = exchange.getRequestURI().getPath().substring("/game-res".length());
            if (!GAME_RESOURCE.matcher(path).matches()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            serveFile(exchange, root, path);
        };
    }

    private static void serveFile(HttpExchange exchange, Path root, String requestPath) throws IOException {
        String method = exchange.getRequestMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        Path file = resolve(root, requestPath);
        if (file == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        String name = file.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", TYPES.getOrDefault(extension, "application/octet-stream"));
        headers.set("Cache-Control", "no-cache");
        long size = Files.size(file);
        if (method.equals("HEAD")) {
            headers.set("Content-Length", Long.toString(size));
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        exchange.sendResponseHeaders(200, size == 0 ? -1 : size);
        try (OutputStream out = exchange.getResponseBody()) {
            Files.copy(file, out);
        }
    }

    /** Maps a decoded request path to a regular file inside root, or null. */
    private static Path resolve(Path root, String path) {
        if (path == null || !path.startsWith("/")) return null;
        String relative = path.substring(1);
        if (relative.isEmpty() || relative.endsWith("/")) relative += "index.html";
        try {
            Path file = root.resolve(relative).normalize();
            return file.startsWith(root) && Files.isRegularFile(file) ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int split = pair.indexOf('=');
            String key = URLDecoder.decode(split < 0 ? pair : pair.substring(0, split), StandardCharsets.UTF_8);
            String value = split < 0 ? "" : URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8);
            form.putIfAbsent(key, value);
        }
        return form;
    }

    static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
