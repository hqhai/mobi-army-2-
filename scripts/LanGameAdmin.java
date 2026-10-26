import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.function.IntConsumer;
import mobiarmy.server.Equip;
import mobiarmy.server.Exp;
import mobiarmy.server.Glass;
import mobiarmy.server.Item;
import mobiarmy.server.LinhTinh;
import mobiarmy.server.Server;
import mobiarmy.server.SessionManager;
import mobiarmy.server.ShopEquipment;
import mobiarmy.server.ShopLinhTinh;
import mobiarmy.server.User;
import mobiarmy.war.RoomInfo;
import mobiarmy.war.RoomRules;
import mobiarmy.war.RoomWait;
import mobiarmy.server.ThorSkin;
import mobiarmy.war.SetPassive;

/**
 * Admin operations on players and catalogs. The server keeps players in memory and periodically overwrites the
 * database with that state, so player changes are made on the in-memory objects, on the game thread.
 */
public final class LanGameAdmin {
    static final String[] EQUIP_TYPES = {"Súng", "Nón", "Áo", "Kính", "Balo"};
    /** Items cap at 99 in the bag, like the shop; a super-admin gift never exceeds it. */
    static final int ITEM_MAX = 99;
    private static final Gson GSON = new Gson();

    private final LanWeb.Database database;
    private final LanApi.GameThread game;
    private final IntConsumer accountChanged;

    /** accountChanged hears the id of a player whose password or name the admin changed. */
    LanGameAdmin(LanWeb.Database database, LanApi.GameThread game, IntConsumer accountChanged) {
        this.database = database;
        this.game = game;
        this.accountChanged = accountChanged;
    }

    /** Creates the settings table if needed and restores the persisted data revision. */
    void loadSettings() throws SQLException {
        try (Connection conn = database.open(); Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS lan_setting (name VARCHAR(50) NOT NULL PRIMARY KEY, value VARCHAR(255) NOT NULL)");
            try (ResultSet rs = statement.executeQuery("SELECT value FROM lan_setting WHERE name = 'data_revision'")) {
                Server.dataRevision = rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
            }
            try (ResultSet rs = statement.executeQuery("SELECT value FROM lan_setting WHERE name = 'room_rules'")) {
                int rules = rs.next() ? Integer.parseInt(rs.getString(1)) : RoomRules.BALANCED;
                RoomRules.defaultRules = (byte) (RoomRules.valid(rules) ? rules : RoomRules.BALANCED);
            }
        }
    }

    // --- match rules --------------------------------------------------------------------------

    Map<String, Object> settings() {
        return Map.of("rules", LanPlayerGame.rulesInfo());
    }

    /** Rules a room starts with; owners change them in the game. Rooms with players keep theirs until they empty. */
    Map<String, Object> updateSettings(int defaultRules) throws Exception {
        if (!RoomRules.valid(defaultRules)) throw new LanApi.BadRequest("Luật không hợp lệ.");
        write(conn -> {
            try (PreparedStatement upsert = conn.prepareStatement(
                    "INSERT INTO lan_setting (name, value) VALUES ('room_rules', ?) ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
                upsert.setString(1, Integer.toString(defaultRules));
                return upsert.executeUpdate();
            }
        });
        RoomRules.defaultRules = (byte) defaultRules;
        game.call(() -> {
            for (RoomWait room : rooms()) {
                if (room.numPlayer == 0) room.rules = (byte) defaultRules;
            }
            return null;
        });
        System.out.println("Admin: new rooms start with " + RoomRules.name(defaultRules) + " rules");
        return settings();
    }

    /** Every waiting room on the server. */
    private static List<RoomWait> rooms() {
        List<RoomWait> rooms = new ArrayList<>();
        if (RoomInfo.entrys == null) return rooms;
        for (RoomInfo info : RoomInfo.entrys) {
            if (info != null && info.roomWaits != null) {
                for (RoomWait wait : info.roomWaits) if (wait != null) rooms.add(wait);
            }
        }
        return rooms;
    }

    // --- players ------------------------------------------------------------------------------

    List<Map<String, Object>> players() throws Exception {
        List<Object[]> accounts = new ArrayList<>();
        try (Connection conn = database.open();
             PreparedStatement statement = conn.prepareStatement("SELECT id, username FROM user ORDER BY id");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) accounts.add(new Object[] {rs.getInt(1), rs.getString(2)});
        }
        return game.call(() -> {
            List<Map<String, Object>> players = new ArrayList<>();
            for (Object[] account : accounts) {
                int id = (int) account[0];
                String username = (String) account[1];
                User user = SessionManager.findUserById(id);
                // Accounts registered after startup are not in memory until their first login; show their defaults.
                players.add(user != null ? view(user, username, true) : view(new User(id, username), username, false));
            }
            return players;
        });
    }

    Map<String, Object> addMoney(int userId, String currency, long amount) throws Exception {
        boolean xu = currency.equals("xu");
        if (amount == 0) throw new LanApi.BadRequest("Hãy nhập một số khác 0.");
        String username = username(userId);
        return game.call(() -> {
            User user = loadedOrCreated(userId, username);
            long before = xu ? user.xu : user.luong;
            long after = Math.max(0, Math.min(Integer.MAX_VALUE, before + amount));
            int delta = (int) (after - before);
            boolean online = user.session != null;
            if (xu) {
                user.addXu(delta, online);
            } else {
                user.addLuong(delta, online);
            }
            Server.requestSave();
            System.out.printf("Admin: %+d %s for %s (%d -> %d)%n", delta, currency, username, before, after);
            Map<String, Object> result = view(user, username, true);
            result.put("before", before);
            result.put("after", after);
            return result;
        });
    }

    Map<String, Object> levelUp(int userId, int glassId, int level) throws Exception {
        String username = username(userId);
        return game.call(() -> {
            int threshold = expThreshold(level);
            User user = SessionManager.findUserById(userId);
            boolean loaded = user != null;
            if (!loaded) user = new User(userId, username);
            if (glassId < 0 || glassId >= user.glass.length) throw new LanApi.BadRequest("Nhân vật không hợp lệ.");
            Glass glass = user.glass[glassId];
            if (!glass.isOpen) throw new LanApi.BadRequest(username + " chưa mở nhân vật " + glass.name + ".");
            if (level <= glass.level) {
                throw new LanApi.BadRequest(glass.name + " của " + username + " đang ở cấp " + glass.level + ", hãy chọn cấp cao hơn.");
            }
            if (!loaded) SessionManager.addUser(user);
            int before = glass.level;
            glass.addExp(threshold - glass.exp, user.session != null);
            Server.requestSave();
            System.out.printf("Admin: %s of %s level %d -> %d%n", glass.name, username, before, glass.level);
            Map<String, Object> result = view(user, username, true);
            result.put("before", before);
            result.put("after", glass.level);
            result.put("points", glass.point);
            return result;
        });
    }

    /** Levels a character can be set to: the rows of the exp table. */
    List<Integer> levels() throws Exception {
        return game.call(() -> {
            List<Integer> levels = new ArrayList<>();
            for (int i = 0; i < Exp.entrys.length; i++) levels.add(Exp.getLevelExp(i == 0 ? 0 : Exp.entrys[i - 1].exp));
            return levels;
        });
    }

    /** Passwords live only in the database (the game checks them at each login), so no game state changes. */
    Map<String, Object> resetPassword(int userId, String password) throws Exception {
        String problem = LanAccounts.passwordProblem(password);
        if (problem != null) throw new LanApi.BadRequest(problem);
        String username = username(userId);
        try (Connection conn = database.open()) {
            LanAccounts.setPassword(conn, userId, password);
        }
        accountChanged.accept(userId);
        System.out.println("Admin: reset the password of " + username);
        return playerView(userId, username);
    }

    /** Renames the login and the in-game name together; the server keeps them equal. */
    Map<String, Object> rename(int userId, String requested) throws Exception {
        String name = requested.trim().toLowerCase(Locale.ROOT); // the game client lowercases names at login
        String problem = LanAccounts.usernameProblem(name);
        if (problem != null) throw new LanApi.BadRequest(problem);
        String previous = username(userId);
        if (name.equals(previous)) throw new LanApi.BadRequest("Tên mới giống tên hiện tại.");
        try (Connection conn = database.open()) {
            if (!LanAccounts.rename(conn, userId, name)) throw new LanApi.ApiError(409, "Tên " + name + " đã có người dùng.");
        }
        game.call(() -> {
            User user = SessionManager.findUserById(userId);
            if (user != null) {
                // The session manager indexes players by name: take the player out and put them back under the new one.
                SessionManager.removeUserById(userId);
                user.name = name;
                SessionManager.addUser(user);
                Server.requestSave();
            }
            return null;
        });
        accountChanged.accept(userId);
        System.out.println("Admin: renamed " + previous + " to " + name);
        Map<String, Object> result = playerView(userId, name);
        result.put("previous", previous);
        return result;
    }

    private Map<String, Object> playerView(int userId, String username) throws Exception {
        return game.call(() -> {
            User user = SessionManager.findUserById(userId);
            return user != null ? view(user, username, true) : view(new User(userId, username), username, false);
        });
    }

    // --- balance: win rates from real matches (MapData.endTheWar writes them) ------------------

    /** LAN items 36-38 (client build 3+): Chỉ kẻ đầu, Bê tông cốt thép, Cơn mưa độc. */
    static final int HEAD_GUIDE_ITEM = 36;

    static void ensureItems(Connection conn) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO item (id, name, xu, luong, carryable) SELECT ?, ?, ?, -1, 1"
                + " FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM item WHERE id = ?)")) {
            Object[][] items = {
                {HEAD_GUIDE_ITEM, "Chỉ kẻ đầu", 600},
                {37, "Bê tông cốt thép", 500}, // ground under you cannot be dug for 2 rounds (MapData.useItem)
                {38, "Cơn mưa độc", 700}, // purple Sao băng; hit players heal half as much for 3 turns
            };
            for (Object[] item : items) {
                insert.setInt(1, (Integer) item[0]);
                insert.setString(2, (String) item[1]);
                insert.setInt(3, (Integer) item[2]);
                insert.setInt(4, (Integer) item[0]);
                insert.executeUpdate();
            }
        }
    }

    static void ensureMatchResults(Connection conn) throws SQLException {
        try (java.sql.Statement create = conn.createStatement()) {
            create.executeUpdate("CREATE TABLE IF NOT EXISTS lan_match_result (id INT AUTO_INCREMENT PRIMARY KEY,"
                    + " played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, glass_id TINYINT NOT NULL,"
                    + " set_tier TINYINT NOT NULL, rules TINYINT NOT NULL, players TINYINT NOT NULL, result TINYINT NOT NULL)");
            // One row per fighter per match; rows of a match share match_key (web "Thành tích").
            create.executeUpdate("CREATE TABLE IF NOT EXISTS lan_match_player (id INT AUTO_INCREMENT PRIMARY KEY,"
                    + " played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, match_key VARCHAR(40) NOT NULL, map_id TINYINT NOT NULL,"
                    + " user_id INT NOT NULL, name VARCHAR(64) NOT NULL, glass_id TINYINT NOT NULL, team TINYINT NOT NULL,"
                    + " result TINYINT NOT NULL, dealt INT NOT NULL, taken INT NOT NULL, healed INT NOT NULL, kills INT NOT NULL,"
                    + " KEY (user_id), KEY (match_key))");
            // Admin-granted rare pieces with a hidden damage bonus (SetPassive.hiddenAtt); never shown to players.
            create.executeUpdate("CREATE TABLE IF NOT EXISTS lan_hidden_bonus (user_id INT NOT NULL, db_key INT NOT NULL,"
                    + " att_percent INT NOT NULL, PRIMARY KEY (user_id, db_key))");
        }
    }

    /** Matches, wins, draws and losses per character, split into with and without a full +14 set. */
    List<Map<String, Object>> balance() throws SQLException {
        return query("SELECT g.id, g.name, r.set_tier > 0 AS passive, COUNT(r.id) AS matches,"
                + " COALESCE(SUM(r.result = 1), 0) AS wins, COALESCE(SUM(r.result = 0), 0) AS draws, COALESCE(SUM(r.result = -1), 0) AS losses"
                + " FROM glass g LEFT JOIN lan_match_result r ON r.glass_id = g.id"
                + " GROUP BY g.id, g.name, passive ORDER BY g.id, passive", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getInt("id"));
            row.put("name", rs.getString("name"));
            row.put("passive", rs.getInt("passive") == 1);
            for (String column : new String[] {"matches", "wins", "draws", "losses"}) row.put(column, rs.getInt(column));
            return row;
        });
    }

    // --- catalogs -----------------------------------------------------------------------------

    List<Map<String, Object>> characters() throws SQLException {
        return query("SELECT id, name, xu, luong, att, friction, angle, distance, bullet FROM glass ORDER BY id", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : new String[] {"id", "name", "xu", "luong", "att", "friction", "angle", "distance", "bullet"}) {
                row.put(column, rs.getObject(column));
            }
            return row;
        });
    }

    Map<String, Object> updateCharacter(int id, int xu, int luong, int att) throws Exception {
        // The client receives the unlock price as xu/1000 and luong in 16-bit fields.
        if (xu < 0 || xu > 32_767_000 || xu % 1000 != 0) throw new LanApi.BadRequest("Giá xu phải là bội số của 1.000, từ 0 đến 32.767.000.");
        if (luong < 0 || luong > Short.MAX_VALUE) throw new LanApi.BadRequest("Giá lượng phải từ 0 đến 32.767.");
        if (att < 1 || att > Short.MAX_VALUE) throw new LanApi.BadRequest("Sát thương phải từ 1 đến 32.767.");
        int revision = write(conn -> {
            try (PreparedStatement update = conn.prepareStatement("UPDATE glass SET xu = ?, luong = ?, att = ? WHERE id = ?")) {
                update.setInt(1, xu);
                update.setInt(2, luong);
                update.setInt(3, att);
                update.setInt(4, id);
                if (update.executeUpdate() == 0) throw new NoSuchElementException("Không tìm thấy nhân vật.");
            }
            return bumpRevision(conn);
        });
        game.call(() -> {
            for (Glass glass : Glass.entrys) {
                if (glass.id == id) setCharacter(glass, xu, luong, att);
            }
            for (User user : SessionManager.generateUsers()) {
                for (Glass glass : user.glass) {
                    if (glass.id == id) setCharacter(glass, xu, luong, att);
                }
            }
            Server.dataRevision = revision;
            return null;
        });
        System.out.printf("Admin: character %d set to xu=%d luong=%d att=%d%n", id, xu, luong, att);
        return find(characters(), id);
    }

    List<Map<String, Object>> equipment() throws SQLException {
        return query("SELECT s.id, s.glassID, g.name AS glassName, s.equipId, s.name, e.type, e.level, e.date, s.xu, s.luong, "
                + "s.inv_ability, s.inv_percen FROM shop_equipment s JOIN equip e ON e.glassID = s.glassID AND e.id = s.equipId "
                + "LEFT JOIN glass g ON g.id = s.glassID ORDER BY s.glassID, e.type, s.id", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getInt("id"));
            row.put("glassId", rs.getInt("glassID"));
            row.put("glassName", rs.getString("glassName"));
            row.put("name", rs.getString("name"));
            int type = rs.getInt("type");
            row.put("type", type);
            row.put("typeName", type >= 0 && type < EQUIP_TYPES.length ? EQUIP_TYPES[type] : "Loại " + type);
            row.put("level", rs.getInt("level"));
            row.put("days", rs.getInt("date"));
            row.put("xu", rs.getInt("xu"));
            row.put("luong", rs.getInt("luong"));
            row.put("ability", GSON.fromJson(rs.getString("inv_ability"), int[].class));
            row.put("percent", GSON.fromJson(rs.getString("inv_percen"), int[].class));
            return row;
        });
    }

    Map<String, Object> updateEquipment(int shopId, int xu, int luong, int[] ability, int[] percent) throws Exception {
        requirePrice(xu, Integer.MAX_VALUE, "Giá xu");
        requirePrice(luong, Short.MAX_VALUE, "Giá lượng");
        requireStats(ability, "Chỉ số cộng");
        requireStats(percent, "Chỉ số %");
        int[] keys = new int[2];
        int revision = write(conn -> {
            try (PreparedStatement find = conn.prepareStatement("SELECT glassID, equipId FROM shop_equipment WHERE id = ? FOR UPDATE")) {
                find.setInt(1, shopId);
                try (ResultSet rs = find.executeQuery()) {
                    if (!rs.next()) throw new NoSuchElementException("Không tìm thấy món đồ.");
                    keys[0] = rs.getInt(1);
                    keys[1] = rs.getInt(2);
                }
            }
            // shop_equipment sells the item; equip prices owned copies (sell-back, renewal) and feeds the client's cached stats.
            try (PreparedStatement shop = conn.prepareStatement("UPDATE shop_equipment SET xu = ?, luong = ?, inv_ability = ?, inv_percen = ? WHERE id = ?");
                 PreparedStatement base = conn.prepareStatement("UPDATE equip SET xu = ?, luong = ?, inv_ability = ?, inv_percen = ? WHERE glassID = ? AND id = ?")) {
                for (PreparedStatement update : new PreparedStatement[] {shop, base}) {
                    update.setInt(1, xu);
                    update.setInt(2, luong);
                    update.setString(3, GSON.toJson(ability));
                    update.setString(4, GSON.toJson(percent));
                }
                shop.setInt(5, shopId);
                shop.executeUpdate();
                base.setInt(5, keys[0]);
                base.setInt(6, keys[1]);
                base.executeUpdate();
            }
            return bumpRevision(conn);
        });
        game.call(() -> {
            Equip catalog = Equip.get((byte) keys[0], (short) keys[1]);
            if (catalog != null) setEquipment(catalog, xu, luong, ability, percent);
            for (Equip entry : ShopEquipment.entrys) {
                if (entry != null && entry.glassID == keys[0] && entry.id == keys[1]) setEquipment(entry, xu, luong, ability, percent);
            }
            // Items already bought keep their stats but are valued at the current price.
            for (User user : SessionManager.generateUsers()) {
                for (Equip owned : user.equips) {
                    if (owned.glassID == keys[0] && owned.id == keys[1]) {
                        owned.xu = xu;
                        owned.luong = luong;
                    }
                }
            }
            Server.dataRevision = revision;
            return null;
        });
        System.out.printf("Admin: shop equipment %d set to xu=%d luong=%d ability=%s percent=%s%n",
                shopId, xu, luong, GSON.toJson(ability), GSON.toJson(percent));
        return find(equipment(), shopId);
    }

    List<Map<String, Object>> items() throws SQLException {
        return query("SELECT id, name, xu, luong, carryable FROM item ORDER BY id", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : new String[] {"id", "name", "xu", "luong", "carryable"}) row.put(column, rs.getObject(column));
            return row;
        });
    }

    Map<String, Object> updateItem(int id, int xu, int luong) throws Exception {
        requirePrice(xu, Integer.MAX_VALUE, "Giá xu");
        requirePrice(luong, Integer.MAX_VALUE, "Giá lượng");
        write(conn -> {
            try (PreparedStatement update = conn.prepareStatement("UPDATE item SET xu = ?, luong = ? WHERE id = ?")) {
                update.setInt(1, xu);
                update.setInt(2, luong);
                update.setInt(3, id);
                if (update.executeUpdate() == 0) throw new NoSuchElementException("Không tìm thấy vật phẩm.");
            }
            return 0;
        });
        game.call(() -> {
            Item catalog = Item.get(id);
            if (catalog != null) setPrice(catalog, xu, luong);
            // Players buy battle items at the price of their own copy, which the client also shows after login.
            for (User user : SessionManager.generateUsers()) {
                for (Item owned : user.items) {
                    if (owned.id == id) setPrice(owned, xu, luong);
                }
            }
            return null;
        });
        System.out.printf("Admin: item %d set to xu=%d luong=%d%n", id, xu, luong);
        return find(items(), id);
    }

    List<Map<String, Object>> specials() throws SQLException {
        return query("SELECT id, name, detail, xu, luong, date FROM linhtinh ORDER BY id", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getInt("id"));
            row.put("name", rs.getString("name"));
            row.put("detail", rs.getString("detail"));
            row.put("xu", rs.getInt("xu"));
            row.put("luong", rs.getInt("luong"));
            row.put("days", rs.getInt("date"));
            return row;
        });
    }

    Map<String, Object> updateSpecial(int id, int xu, int luong) throws Exception {
        requirePrice(xu, Integer.MAX_VALUE, "Giá xu");
        requirePrice(luong, Short.MAX_VALUE, "Giá lượng");
        write(conn -> {
            try (PreparedStatement update = conn.prepareStatement("UPDATE linhtinh SET xu = ?, luong = ? WHERE id = ?")) {
                update.setInt(1, xu);
                update.setInt(2, luong);
                update.setInt(3, id);
                if (update.executeUpdate() == 0) throw new NoSuchElementException("Không tìm thấy đồ đặc biệt.");
            }
            return 0;
        });
        game.call(() -> {
            LinhTinh catalog = LinhTinh.get(id);
            if (catalog != null) setPrice(catalog, xu, luong);
            LinhTinh shop = ShopLinhTinh.get(id);
            if (shop != null) setPrice(shop, xu, luong);
            // Owned copies set the sell-back price.
            for (User user : SessionManager.generateUsers()) {
                for (LinhTinh owned : user.linhtinhs) {
                    if (owned.id == id) setPrice(owned, xu, luong);
                }
            }
            return null;
        });
        System.out.printf("Admin: special item %d set to xu=%d luong=%d%n", id, xu, luong);
        return find(specials(), id);
    }

    // --- helpers ------------------------------------------------------------------------------

    private interface RowMapper {
        Map<String, Object> map(ResultSet rs) throws SQLException;
    }

    private interface Write {
        int run(Connection conn) throws SQLException;
    }

    private List<Map<String, Object>> query(String sql, RowMapper mapper) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = database.open();
             PreparedStatement statement = conn.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) rows.add(mapper.map(rs));
        }
        return rows;
    }

    /** Runs the writes in one transaction. */
    private int write(Write work) throws SQLException {
        try (Connection conn = database.open()) {
            conn.setAutoCommit(false);
            try {
                int result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static int bumpRevision(Connection conn) throws SQLException {
        int next;
        try (PreparedStatement read = conn.prepareStatement("SELECT value FROM lan_setting WHERE name = 'data_revision' FOR UPDATE");
             ResultSet rs = read.executeQuery()) {
            next = (rs.next() ? Integer.parseInt(rs.getString(1)) : 0) + 1;
        }
        try (PreparedStatement upsert = conn.prepareStatement(
                "INSERT INTO lan_setting (name, value) VALUES ('data_revision', ?) ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
            upsert.setString(1, Integer.toString(next));
            upsert.executeUpdate();
        }
        return next;
    }

    private String username(int userId) throws SQLException {
        try (Connection conn = database.open();
             PreparedStatement statement = conn.prepareStatement("SELECT username FROM user WHERE id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("Không tìm thấy người chơi.");
                return rs.getString(1);
            }
        }
    }

    /** Same as the login path: an account not yet in memory is created with the game's defaults. */
    private static User loadedOrCreated(int id, String username) {
        User user = SessionManager.findUserById(id);
        if (user == null) {
            user = new User(id, username);
            SessionManager.addUser(user);
        }
        return user;
    }

    /** Smallest exp at which a character is at the given level. */
    private static int expThreshold(int level) {
        for (int i = 0; i < Exp.entrys.length; i++) {
            int threshold = i == 0 ? 0 : Exp.entrys[i - 1].exp;
            if (Exp.getLevelExp(threshold) == level) return threshold;
        }
        throw new LanApi.BadRequest("Cấp " + level + " không có trong bảng cấp của game.");
    }

    private static Map<String, Object> view(User user, String username, boolean loaded) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.id);
        view.put("username", username);
        view.put("loaded", loaded);
        view.put("online", user.session != null);
        view.put("xu", user.xu);
        view.put("luong", user.luong);
        view.put("cup", user.cup);
        view.put("selectedGlass", (int) user.selectGlass);
        List<Map<String, Object>> glasses = new ArrayList<>();
        for (Glass glass : user.glass) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", (int) glass.id);
            entry.put("name", glass.name);
            entry.put("level", glass.level);
            entry.put("open", glass.isOpen);
            glasses.add(entry);
        }
        view.put("glasses", glasses);
        return view;
    }

    private static Map<String, Object> find(List<Map<String, Object>> rows, int id) {
        for (Map<String, Object> row : rows) {
            if (((Number) row.get("id")).intValue() == id) return row;
        }
        throw new NoSuchElementException("Không tìm thấy dữ liệu vừa lưu.");
    }

    private static void requirePrice(int value, int max, String label) {
        if (value < -1 || value > max) throw new LanApi.BadRequest(label + " phải từ -1 (không bán) đến " + String.format("%,d", max).replace(',', '.') + ".");
    }

    private static void requireStats(int[] values, String label) {
        if (values == null || values.length != 5) throw new LanApi.BadRequest(label + " cần đủ 5 giá trị.");
        for (int value : values) {
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) throw new LanApi.BadRequest(label + " mỗi ô phải từ -128 đến 127.");
        }
    }

    private static byte[] bytes(int[] values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    private static void setCharacter(Glass glass, int xu, int luong, int att) {
        glass.xu = xu;
        glass.luong = luong;
        glass.att = att;
    }

    private static void setEquipment(Equip equip, int xu, int luong, int[] ability, int[] percent) {
        equip.xu = xu;
        equip.luong = luong;
        equip.inv_ability = bytes(ability);
        equip.inv_percen = bytes(percent);
    }

    private static void setPrice(Item item, int xu, int luong) {
        item.xu = xu;
        item.luong = luong;
    }

    private static void setPrice(LinhTinh item, int xu, int luong) {
        item.xu = xu;
        item.luong = luong;
    }

    // --- Super-admin gifts (LanAdmin.superOnly restricts the routes; the role is re-checked per request) ---

    /**
     * A set of the character's five pieces at +plus (14 to 16; from +15 with one element for all, SetElement), each
     * hole filled with the gem (special item below 50, its stats added as when socketing it), or none (-1).
     */
    Map<String, Object> giftSet(int userId, int glassId, int tier, boolean wear, int plus, int element, int gem) throws Exception {
        if (tier < 1 || tier > SetPassive.SETS.length) throw new LanApi.BadRequest("Bộ đồ không hợp lệ.");
        if (plus < SetPassive.SET_PLUS || plus > LanPlayerGame.MAX_PLUS) {
            throw new LanApi.BadRequest("Cấp cường hóa từ +" + SetPassive.SET_PLUS + " đến +" + LanPlayerGame.MAX_PLUS + ".");
        }
        boolean elemental = plus >= mobiarmy.war.SetElement.FIRST_PLUS;
        if (elemental && (element < 1 || element >= mobiarmy.war.SetElement.NAMES.length)) {
            throw new LanApi.BadRequest("Bộ +" + plus + " cần chọn hệ: Lôi, Hỏa, Băng hoặc Độc.");
        }
        LinhTinh gemItem = gem < 0 ? null : gem < 50 ? LinhTinh.get(gem) : null;
        if (gem >= 0 && gemItem == null) throw new LanApi.BadRequest("Không có viên ngọc này.");
        String username = username(userId);
        return game.call(() -> {
            User user = SessionManager.findUserById(userId);
            boolean loaded = user != null;
            if (!loaded) user = new User(userId, username);
            if (glassId < 0 || glassId >= user.glass.length) throw new LanApi.BadRequest("Nhân vật không hợp lệ.");
            Glass glass = user.glass[glassId];
            if (!glass.isOpen) throw new LanApi.BadRequest(username + " chưa mở nhân vật " + glass.name + ".");
            // The game itself blocks gear changes in a room or a match.
            if (user.roomWait != null) {
                throw new LanApi.ApiError(409, username + " đang ở trong phòng chờ hoặc trận đấu. Đợi người chơi rời phòng rồi tặng lại.");
            }
            String set = "bộ " + SetPassive.SETS[tier - 1] + " +" + plus
                    + (elemental ? " hệ " + mobiarmy.war.SetElement.NAMES[element] : "") + (gemItem != null ? ", khảm full " + gemItem.name : "");
            Equip[] entries = new Equip[EQUIP_TYPES.length];
            for (Equip entry : ShopEquipment.generate((byte) glassId)) {
                if (setPiece(entry) && SetPassive.setOf(entry) == tier - 1) entries[entry.type] = entry;
            }
            for (int type = 0; type < entries.length; type++) {
                if (entries[type] == null) {
                    throw new LanApi.BadRequest("Cửa hàng của " + glass.name + " không có " + EQUIP_TYPES[type].toLowerCase(Locale.ROOT) + " của " + set + ".");
                }
            }
            if (user.equips.size() + entries.length > LanPlayerGame.BAG_LIMIT) {
                throw new LanApi.BadRequest("Rương đồ của " + username + " không đủ chỗ cho " + entries.length + " món (tối đa " + LanPlayerGame.BAG_LIMIT + ").");
            }
            if (!loaded) SessionManager.addUser(user);
            List<Equip> pieces = new ArrayList<>();
            int need = 0;
            for (Equip entry : entries) {
                Equip piece = LanPlayerGame.upgradedCopy(entry, plus);
                if (gemItem != null) {
                    for (int hole = 0; hole < piece.slot.length; hole++) {
                        piece.slot[hole] = (short) gemItem.id;
                        for (int i = 0; i < gemItem.ability.length && i < piece.inv_ability.length; i++) {
                            piece.inv_ability[i] = (byte) Math.min(Byte.MAX_VALUE, piece.inv_ability[i] + gemItem.ability[i]);
                        }
                    }
                }
                user.addEquip(piece);
                pieces.add(piece);
                need = Math.max(need, piece.level);
            }
            try {
                saveHiddenAtt(userId, pieces, 0); // (a reused key keeps no hidden bonus of an old piece)
                if (elemental) {
                    for (Equip piece : pieces) {
                        Server.dbManager.update("INSERT INTO lan_equip_element (user_id, db_key, element) VALUES (?, ?, ?)"
                                + " ON DUPLICATE KEY UPDATE element = VALUES(element)", userId, piece.dbKey, element);
                    }
                }
            } catch (SQLException | RuntimeException e) {
                user.equips.removeAll(pieces);
                LanPlayerGame.syncGame(user);
                throw e;
            }
            boolean worn = wear && glass.level >= need;
            if (worn) {
                // Same as wearing them in the game: the set replaces the character's normal gear, a costume stays on.
                for (Equip equip : user.equips) {
                    if (equip.glassID == glassId && equip.vip == 0) equip.isUse = pieces.contains(equip);
                }
                glass.updateAll();
            }
            Server.requestSave();
            LanPlayerGame.syncGame(user);
            LanPlayerGame.notifyGame(user, "Bạn được tặng " + set + " cho " + glass.name + (worn ? ", đã mặc sẵn." : ". Vào Rương đồ để mặc."));
            System.out.printf("Admin: gave %s %s for %s%s%n", username, set, glass.name, worn ? ", worn" : "");
            String message = "Đã tặng " + username + " " + set + " cho " + glass.name
                    + (worn ? ", đã mặc sẵn (nội tại " + SetPassive.STRENGTH[tier] + "%)."
                    : wear ? " vào rương đồ. Chưa mặc được: " + glass.name + " cần cấp " + need + ", đang cấp " + glass.level + "."
                    : " vào rương đồ.");
            Map<String, Object> result = view(user, username, true);
            result.put("message", message);
            result.put("worn", worn);
            return result;
        });
    }

    Map<String, Object> giftItems(int userId, int itemId, int amount) throws Exception {
        if (itemId != -1 && Item.get(itemId) == null) throw new LanApi.BadRequest("Không có vật phẩm này.");
        if (itemId != -1 && (amount < 1 || amount > ITEM_MAX)) throw new LanApi.BadRequest("Số lượng từ 1 đến " + ITEM_MAX + ".");
        String username = username(userId);
        return game.call(() -> {
            User user = loadedOrCreated(userId, username);
            if (user.roomWait != null && user.roomWait.started) {
                throw new LanApi.ApiError(409, username + " đang trong trận. Đợi trận kết thúc rồi tặng lại.");
            }
            List<Item> changed = new ArrayList<>();
            int given = 0;
            for (Item entry : Item.entrys) {
                if (itemId != -1 && entry.id != itemId) continue;
                Item owned = user.getItem(entry.id);
                int before = owned == null ? 0 : owned.num;
                user.addItem(entry.id, itemId == -1 ? ITEM_MAX - before : amount);
                Item after = user.getItem(entry.id);
                if (after != null && after.num > ITEM_MAX) after.num = ITEM_MAX;
                if (after != null && after.num != before) {
                    changed.add(after);
                    given += after.num - before;
                }
            }
            if (user.session != null) {
                for (Item item : changed) user.session.sessionHandler.updateItem(item, (int) user.xu, (int) user.luong);
            }
            Server.requestSave();
            String what = itemId == -1 ? "đầy đủ vật phẩm" : given + " " + Item.get(itemId).name;
            System.out.printf("Super-admin: gave %s %s%n", username, itemId == -1 ? "every item up to " + ITEM_MAX : given + " of item " + itemId);
            if (given > 0) LanPlayerGame.notifyGame(user, "Bạn được tặng " + what + ".");
            Map<String, Object> result = view(user, username, true);
            result.put("message", given == 0 ? username + " đã có đủ " + (itemId == -1 ? "mọi vật phẩm" : Item.get(itemId).name) + " (tối đa " + ITEM_MAX + ")."
                    : "Đã tặng " + username + " " + what + ".");
            return result;
        });
    }

    List<Map<String, Object>> sets() throws Exception {
        return game.call(() -> {
            List<Map<String, Object>> sets = new ArrayList<>();
            for (int i = 0; i < SetPassive.SETS.length; i++) {
                int level = 0;
                for (Equip entry : ShopEquipment.entrys) {
                    if (setPiece(entry) && SetPassive.setOf(entry) == i) level = Math.max(level, entry.level);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tier", i + 1);
                row.put("name", "Bộ " + SetPassive.SETS[i]);
                row.put("level", level);
                row.put("strength", SetPassive.STRENGTH[i + 1]);
                sets.add(row);
            }
            return sets;
        });
    }

    private static boolean setPiece(Equip entry) {
        return entry != null && entry.vip == 0 && !ThorSkin.is(entry) && entry.type >= 0 && entry.type < EQUIP_TYPES.length;
    }

    /** Hidden damage bonus of the pieces (lan_hidden_bonus); gifts now clear it (0). */
    private void saveHiddenAtt(int userId, List<Equip> pieces, int percent) throws SQLException {
        write(conn -> {
            try (PreparedStatement clear = conn.prepareStatement("DELETE FROM lan_hidden_bonus WHERE user_id = ? AND db_key = ?");
                 PreparedStatement insert = conn.prepareStatement("INSERT INTO lan_hidden_bonus (user_id, db_key, att_percent) VALUES (?, ?, ?)")) {
                for (Equip piece : pieces) {
                    clear.setInt(1, userId);
                    clear.setInt(2, piece.dbKey);
                    clear.executeUpdate();
                    if (percent <= 0) continue;
                    insert.setInt(1, userId);
                    insert.setInt(2, piece.dbKey);
                    insert.setInt(3, percent);
                    insert.executeUpdate();
                }
            }
            return pieces.size();
        });
    }
}
