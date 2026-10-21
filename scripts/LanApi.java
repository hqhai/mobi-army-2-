import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Pieces shared by the JSON APIs that sit behind a login (admin and player pages). */
final class LanApi {
    static final int MAX_BODY_BYTES = 8192;

    /** Runs work on the game loop thread, where all game state changes happen. */
    interface GameThread {
        <T> T call(Callable<T> task) throws Exception;
    }

    /** A request the API refuses; the message is shown to the user as is. */
    static class ApiError extends RuntimeException {
        final int status;

        ApiError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    static final class BadRequest extends ApiError {
        BadRequest(String message) {
            super(400, message);
        }
    }

    private LanApi() {
    }

    /** JSON-only, same-origin writes: a form or script on another site cannot make a browser send these. */
    static boolean sameOriginJson(HttpExchange exchange) {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) return false;
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");
        return origin == null || (host != null && origin.equalsIgnoreCase("http://" + host));
    }

    /** The request's JSON object body; GET requests have none. */
    static JsonObject body(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) return new JsonObject();
        if (!sameOriginJson(exchange)) throw new ApiError(403, "Yêu cầu không hợp lệ.");
        byte[] raw = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (raw.length > MAX_BODY_BYTES) throw new ApiError(413, "Dữ liệu gửi lên quá lớn.");
        JsonElement parsed = raw.length == 0 ? new JsonObject() : JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new BadRequest("Dữ liệu gửi lên không hợp lệ.");
        return parsed.getAsJsonObject();
    }

    static String cookie(HttpExchange exchange, String name) {
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2 || !pair[0].equals(name)) continue;
                String value = pair[1];
                // Cookie values may arrive quoted (RFC 6265 allows it; some clients always quote).
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
                return value;
            }
        }
        return null;
    }

    static void setCookie(HttpExchange exchange, String name, String value, int maxAgeSeconds) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=" + value + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=" + maxAgeSeconds);
    }

    static String clientIp(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    static String stringField(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive()) throw new BadRequest("Thiếu thông tin: " + name + ".");
        return value.getAsString();
    }

    static int intField(JsonObject body, String name) {
        return toInt(body.get(name), name);
    }

    static int[] intArray(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonArray()) throw new BadRequest("Thiếu thông tin: " + name + ".");
        int[] result = new int[value.getAsJsonArray().size()];
        for (int i = 0; i < result.length; i++) result[i] = toInt(value.getAsJsonArray().get(i), name);
        return result;
    }

    private static int toInt(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new BadRequest("Ô " + name + " phải là số.");
        }
        double number = value.getAsDouble();
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new BadRequest("Ô " + name + " phải là số nguyên.");
        }
        return (int) number;
    }

    static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        LanWeb.sendJson(exchange, status, Map.of("error", message));
    }

    interface Work {
        Object run() throws Exception;
    }

    /** Runs the request and answers with its JSON result, or with the status that matches the failure. */
    static void respond(HttpExchange exchange, Work work) throws IOException {
        try {
            LanWeb.sendJson(exchange, 200, work.run());
        } catch (ApiError e) {
            sendError(exchange, e.status, e.getMessage());
        } catch (JsonParseException e) {
            sendError(exchange, 400, "Dữ liệu gửi lên không hợp lệ.");
        } catch (NoSuchElementException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (TimeoutException e) {
            sendError(exchange, 503, "Server game đang bận, bạn thử lại sau ít giây.");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Có lỗi khi xử lý, xem log server.");
        }
    }

    /** Locks an IP out of login for a while after too many wrong passwords in a row. */
    static final class LoginGuard {
        private static final class Failures {
            int count;
            long lockedUntil;
        }

        private final int maxFailures;
        private final long lockMillis;
        private final Map<String, Failures> failures = new ConcurrentHashMap<>();

        LoginGuard(int maxFailures, long lockMillis) {
            this.maxFailures = maxFailures;
            this.lockMillis = lockMillis;
        }

        /** Whole minutes left on the lock (rounded up), or 0 when the IP may try. */
        long minutesLocked(String ip, long now) {
            Failures entry = failures.get(ip);
            if (entry == null || entry.lockedUntil <= now) return 0;
            return Math.max(1, (entry.lockedUntil - now + 59_999) / 60_000);
        }

        void failed(String ip, long now) {
            Failures entry = failures.computeIfAbsent(ip, unused -> new Failures());
            synchronized (entry) {
                if (++entry.count >= maxFailures) {
                    entry.count = 0;
                    entry.lockedUntil = now + lockMillis;
                }
            }
        }

        void succeeded(String ip) {
            failures.remove(ip);
        }
    }

    /** Login sessions kept in memory, ended after a period without requests. Restarting the server logs everyone out. */
    static final class Sessions<T> {
        private static final class Entry<T> {
            final T value;
            volatile long lastSeen;

            Entry(T value, long lastSeen) {
                this.value = value;
                this.lastSeen = lastSeen;
            }
        }

        private final SecureRandom random = new SecureRandom();
        private final Map<String, Entry<T>> entries = new ConcurrentHashMap<>();
        private final long idleMillis;

        Sessions(long idleMillis) {
            this.idleMillis = idleMillis;
        }

        String create(T value, long now) {
            byte[] token = new byte[32];
            random.nextBytes(token);
            String key = HexFormat.of().formatHex(token);
            entries.put(key, new Entry<>(value, now));
            return key;
        }

        T get(String token, long now) {
            if (token == null) return null;
            Entry<T> entry = entries.get(token);
            if (entry == null) return null;
            if (now - entry.lastSeen > idleMillis) {
                entries.remove(token);
                return null;
            }
            entry.lastSeen = now;
            return entry.value;
        }

        void remove(String token) {
            if (token != null) entries.remove(token);
        }

        void removeIf(Predicate<T> test) {
            entries.values().removeIf(entry -> test.test(entry.value));
        }

        Collection<T> values() {
            return entries.values().stream().map(entry -> entry.value).collect(Collectors.toList());
        }
    }
}
