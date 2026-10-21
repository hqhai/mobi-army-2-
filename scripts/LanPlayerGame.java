import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import mobiarmy.server.Equip;
import mobiarmy.server.Exp;
import mobiarmy.server.Glass;
import mobiarmy.server.Item;
import mobiarmy.server.LinhTinh;
import mobiarmy.server.Server;
import mobiarmy.server.SessionManager;
import mobiarmy.server.ShopEquipment;
import mobiarmy.server.ShopLinhTinh;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;

/**
 * What a signed-in player can do from the web: pick a character, spend stat points, wear gear and shop.
 * Everything runs on the game thread against the in-memory player, with the same rules as the game client,
 * so a player who is also in the game sees the change right away.
 */
final class LanPlayerGame {
    /** The bag packet sends its size as a signed byte; stay well below that. */
    static final int BAG_LIMIT = 120;
    static final int MAX_ITEMS = 99;
    private static final String[] TYPES = {"Súng", "Nón", "Áo", "Kính", "Balo"};

    private final LanApi.GameThread game;

    LanPlayerGame(LanApi.GameThread game) {
        this.game = game;
    }

    Map<String, Object> profile(int userId, String username) throws Exception {
        return game.call(() -> view(player(userId, username)));
    }

    Map<String, Object> select(int userId, String username, int glassId) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            if (glassId < 0 || glassId >= user.glass.length || !user.glass[glassId].isOpen) {
                throw new LanApi.BadRequest("Bạn chưa có nhân vật này.");
            }
            user.selectGlass((byte) glassId);
            Server.requestSave();
            return done(user, "Đã chọn " + user.glass().name + ".");
        });
    }

    Map<String, Object> addPoints(int userId, String username, int[] add) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Glass glass = user.glass();
            if (add.length != 5) throw new LanApi.BadRequest("Cần đủ 5 chỉ số.");
            int total = 0;
            for (int value : add) {
                if (value < 0) throw new LanApi.BadRequest("Không thể trừ điểm đã cộng.");
                total += value;
            }
            if (total == 0) throw new LanApi.BadRequest("Bạn chưa cộng điểm nào.");
            if (total > glass.point) throw new LanApi.BadRequest("Bạn chỉ còn " + glass.point + " điểm tiềm năng.");
            glass.upadtePoint(add);
            Server.requestSave();
            log(user, "spent " + total + " stat points on " + glass.name);
            return done(user, "Đã cộng " + total + " điểm cho " + glass.name + ".");
        });
    }

    Map<String, Object> wear(int userId, String username, int dbKey) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Equip equip = ownedForSelected(user, dbKey);
            if (equip.isUse) return done(user, displayName(equip) + " đang được mặc.");
            if (equip.date() <= 0) throw new LanApi.BadRequest(displayName(equip) + " đã hết hạn.");
            if (user.glass().level < equip.level) {
                throw new LanApi.BadRequest("Cần cấp " + equip.level + " để mặc " + displayName(equip) + ".");
            }
            if (equip.vip != 0) {
                user.setEquipVip((byte) 1, dbKey);
            } else {
                int[] slots = wornSlots(user);
                slots[equip.type] = dbKey;
                user.setEquip(slots);
            }
            // The game refuses some combinations (for example a VIP costume over Skin Thor) without saying why here.
            if (!equip.isUse) throw new LanApi.BadRequest("Không mặc được " + displayName(equip) + " cùng đồ đang mặc.");
            Server.requestSave();
            return done(user, "Đã mặc " + displayName(equip) + ".");
        });
    }

    Map<String, Object> remove(int userId, String username, int dbKey) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Equip equip = ownedForSelected(user, dbKey);
            if (!equip.isUse) return done(user, displayName(equip) + " chưa được mặc.");
            if (equip.vip != 0) {
                user.setEquipVip((byte) 0, dbKey);
            } else {
                int[] slots = wornSlots(user);
                slots[equip.type] = -1;
                user.setEquip(slots);
            }
            if (equip.isUse) throw new LanApi.BadRequest("Không tháo được " + displayName(equip) + ".");
            Server.requestSave();
            return done(user, "Đã tháo " + displayName(equip) + ".");
        });
    }

    Map<String, Object> shop(int userId, String username) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            Map<String, Object> shop = new LinkedHashMap<>();
            List<Map<String, Object>> equipment = new ArrayList<>();
            for (Equip entry : ShopEquipment.generate(user.selectGlass)) {
                Map<String, Object> row = gear(entry);
                row.put("days", entry.date);
                row.put("xu", entry.xu);
                row.put("luong", entry.luong);
                row.put("owned", user.equips.stream().filter(owned -> owned.glassID == entry.glassID && owned.id == entry.id).count());
                equipment.add(row);
            }
            shop.put("equipment", equipment);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Item catalog : Item.entrys) {
                Item owned = user.getItem(catalog.id);
                Item priced = owned != null ? owned : catalog; // the game charges the price on the player's own copy
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", catalog.id);
                row.put("name", catalog.name);
                row.put("xu", priced.xu);
                row.put("luong", priced.luong);
                row.put("owned", owned != null ? owned.num : 0);
                items.add(row);
            }
            shop.put("items", items);
            List<Map<String, Object>> specials = new ArrayList<>();
            for (LinhTinh entry : ShopLinhTinh.entrys) {
                LinhTinh catalog = LinhTinh.get(entry.id);
                LinhTinh owned = user.getLinhTinh(entry.id);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", (int) entry.id);
                row.put("name", catalog.name);
                row.put("detail", catalog.detail);
                row.put("xu", catalog.xu);
                row.put("luong", catalog.luong);
                row.put("days", (int) catalog.date);
                row.put("pickAmount", entry.isSelectNum);
                row.put("owned", owned != null ? owned.num : 0);
                specials.add(row);
            }
            shop.put("specials", specials);
            return shop;
        });
    }

    Map<String, Object> buyEquipment(int userId, String username, int equipId, boolean luong) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Equip entry = null;
            for (Equip candidate : ShopEquipment.generate(user.selectGlass)) {
                if (candidate.id == equipId) entry = candidate;
            }
            if (entry == null) throw new NoSuchElementException("Cửa hàng của " + user.glass().name + " không bán món này.");
            if (user.equips.size() >= BAG_LIMIT) throw new LanApi.BadRequest("Rương đồ đã đầy (tối đa " + BAG_LIMIT + " món).");
            if (ThorSkin.is(entry) && user.equips.stream().anyMatch(ThorSkin::is)) {
                throw new LanApi.BadRequest("Bạn đã sở hữu Skin Thor rồi.");
            }
            int price = luong ? entry.luong : entry.xu;
            pay(user, price, luong, displayName(entry));
            Equip bought = entry.deepCopy();
            bought.renewalDate = System.currentTimeMillis();
            user.addEquip(bought);
            Server.requestSave();
            log(user, "bought " + displayName(entry) + " for " + price + (luong ? " luong" : " xu"));
            notifyGame(user, "Đã mua " + displayName(entry) + " trên web.");
            return done(user, "Đã mua " + displayName(entry) + ". Vào Rương đồ để mặc.");
        });
    }

    Map<String, Object> buyItem(int userId, String username, int itemId, int amount, boolean luong) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Item catalog = Item.get(itemId);
            if (catalog == null) throw new NoSuchElementException("Không có vật phẩm này.");
            if (amount < 1 || amount > MAX_ITEMS) throw new LanApi.BadRequest("Số lượng từ 1 đến " + MAX_ITEMS + ".");
            Item item = user.getItem(itemId);
            int have = item != null ? item.num : 0;
            int count = Math.min(amount, MAX_ITEMS - have);
            if (count <= 0) throw new LanApi.BadRequest("Bạn đã có tối đa " + MAX_ITEMS + " " + catalog.name + ".");
            Item priced = item != null ? item : catalog;
            int unit = luong ? priced.luong : priced.xu;
            if (unit < 0) throw new LanApi.BadRequest(catalog.name + " không bán bằng " + (luong ? "lượng" : "xu") + ".");
            long total = (long) unit * count;
            if (total > Integer.MAX_VALUE) throw new LanApi.BadRequest("Số lượng quá lớn.");
            pay(user, (int) total, luong, count + " " + catalog.name, false);
            if (item == null) {
                item = catalog.deepCopy();
                item.num = 0;
                user.items.add(item);
            }
            item.num += count;
            if (user.session != null) user.session.sessionHandler.updateItem(item, (int) user.xu, (int) user.luong);
            Server.requestSave();
            log(user, "bought " + count + " x " + catalog.name + " for " + total + (luong ? " luong" : " xu"));
            String capped = count < amount ? " (đã đủ " + MAX_ITEMS + " nên chỉ mua " + count + ")" : "";
            return done(user, "Đã mua " + count + " " + catalog.name + capped + ".");
        });
    }

    Map<String, Object> buySpecial(int userId, String username, int specialId, int amount, boolean luong) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            LinhTinh entry = ShopLinhTinh.get(specialId);
            LinhTinh catalog = LinhTinh.get(specialId);
            if (entry == null || catalog == null) throw new NoSuchElementException("Cửa hàng không bán món này.");
            if (amount < 1 || amount > MAX_ITEMS) throw new LanApi.BadRequest("Số lượng từ 1 đến " + MAX_ITEMS + ".");
            if (!entry.isSelectNum && amount != 1) throw new LanApi.BadRequest(catalog.name + " chỉ mua được từng cái.");
            int unit = luong ? catalog.luong : catalog.xu;
            if (unit < 0) throw new LanApi.BadRequest(catalog.name + " không bán bằng " + (luong ? "lượng" : "xu") + ".");
            long total = (long) unit * amount;
            if (total > Integer.MAX_VALUE) throw new LanApi.BadRequest("Số lượng quá lớn.");
            pay(user, (int) total, luong, amount + " " + catalog.name);
            user.addLinhTinh(catalog.id, amount);
            Server.requestSave();
            log(user, "bought " + amount + " x " + catalog.name + " for " + total + (luong ? " luong" : " xu"));
            return done(user, "Đã mua " + amount + " " + catalog.name + ".");
        });
    }

    // --- helpers ------------------------------------------------------------------------------

    /** Same as the game login: an account not yet in memory is created with the game's defaults. */
    private static User player(int userId, String username) {
        User user = SessionManager.findUserById(userId);
        if (user == null) {
            user = new User(userId, username);
            SessionManager.addUser(user);
        }
        return user;
    }

    /** The game itself blocks shopping and gear changes while the player sits in a room or a match. */
    private static void requireFree(User user) {
        if (user.roomWait != null) {
            throw new LanApi.ApiError(409, "Bạn đang ở trong phòng chờ hoặc trận đấu. Hãy rời phòng trong game rồi thử lại.");
        }
    }

    private static void pay(User user, int price, boolean luong, String what) {
        pay(user, price, luong, what, true);
    }

    /** Charges the player; tells the game client about the new balance when it is online and notify is set. */
    private static void pay(User user, int price, boolean luong, String what, boolean notify) {
        String currency = luong ? "lượng" : "xu";
        if (price < 0) throw new LanApi.BadRequest(what + " không bán bằng " + currency + ".");
        long balance = luong ? user.luong : user.xu;
        if (price > balance) {
            throw new LanApi.BadRequest("Không đủ " + currency + " để mua " + what + ": cần " + String.format("%,d", price).replace(',', '.')
                    + ", bạn có " + String.format("%,d", balance).replace(',', '.') + ".");
        }
        boolean online = notify && user.session != null;
        if (luong) {
            user.addLuong(-price, online);
        } else {
            user.addXu(-price, online);
        }
    }

    private static Equip ownedForSelected(User user, int dbKey) {
        Equip equip = user.getEquip(dbKey);
        if (equip == null) throw new NoSuchElementException("Không tìm thấy món đồ này trong rương.");
        if (equip.glassID != user.selectGlass) throw new LanApi.BadRequest(displayName(equip) + " dành cho nhân vật khác.");
        return equip;
    }

    /** dbKeys of the normal gear the selected character wears, one per slot; -1 for an empty slot. */
    private static int[] wornSlots(User user) {
        int[] slots = {-1, -1, -1, -1, -1};
        for (Equip equip : user.equips) {
            if (equip.glassID == user.selectGlass && equip.vip == 0 && equip.isUse && equip.date() > 0
                    && equip.type >= 0 && equip.type < slots.length) {
                slots[equip.type] = equip.dbKey;
            }
        }
        return slots;
    }

    private static void notifyGame(User user, String message) {
        if (user.session != null) user.session.sessionHandler.log(message);
    }

    private static void log(User user, String action) {
        System.out.println("Player " + user.name + " (web): " + action);
    }

    private static Map<String, Object> done(User user, String message) {
        Map<String, Object> result = view(user);
        result.put("message", message);
        return result;
    }

    private static String displayName(Equip equip) {
        if (equip.name != null && !equip.name.isBlank()) return equip.name;
        for (Equip entry : ShopEquipment.entrys) {
            if (entry != null && entry.glassID == equip.glassID && entry.id == equip.id && entry.name != null) return entry.name;
        }
        return typeName(equip.type) + " mặc định";
    }

    private static String typeName(int type) {
        return type >= 0 && type < TYPES.length ? TYPES[type] : "Đồ";
    }

    /** Fields shared by shop rows and bag rows, including where the item's picture sits on its character's sheet. */
    private static Map<String, Object> gear(Equip equip) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("glassId", (int) equip.glassID);
        row.put("equipId", (int) equip.id);
        row.put("name", displayName(equip));
        row.put("type", (int) equip.type);
        row.put("typeName", equip.vip != 0 ? "Trang phục VIP" : typeName(equip.type));
        row.put("vip", equip.vip != 0);
        row.put("level", (int) equip.level);
        row.put("permanent", ThorSkin.is(equip));
        row.put("ability", toInts(equip.inv_ability));
        row.put("percent", toInts(equip.inv_percen));
        if (equip.x != null && equip.x.length > 0 && equip.w[0] > 0 && equip.h[0] > 0) {
            row.put("sprite", List.of((int) equip.x[0], (int) equip.y[0], (int) equip.w[0], (int) equip.h[0]));
        }
        return row;
    }

    private static int[] toInts(byte[] values) {
        int[] result = new int[5];
        for (int i = 0; values != null && i < Math.min(5, values.length); i++) result[i] = values[i];
        return result;
    }

    private static Map<String, Object> view(User user) {
        Glass glass = user.glass();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.id);
        view.put("name", user.name);
        view.put("xu", user.xu);
        view.put("luong", user.luong);
        view.put("cup", user.cup);
        view.put("online", user.session != null);
        view.put("busy", user.roomWait != null);
        view.put("selectedGlass", (int) user.selectGlass);
        List<Map<String, Object>> characters = new ArrayList<>();
        for (Glass entry : user.glass) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", (int) entry.id);
            row.put("name", entry.name);
            row.put("level", entry.level);
            row.put("open", entry.isOpen);
            characters.add(row);
        }
        view.put("characters", characters);
        Map<String, Object> character = new LinkedHashMap<>();
        character.put("id", (int) glass.id);
        character.put("name", glass.name);
        character.put("level", glass.level);
        character.put("expPercent", Exp.get(glass.level) == null ? 0 : Math.max(0, Math.min(100, glass.getPercentLevel())));
        character.put("points", glass.point);
        character.put("ability", glass.ability.clone());
        character.put("stats", glass.createAbility());
        view.put("character", character);
        List<Map<String, Object>> bag = new ArrayList<>();
        user.equips.stream()
                .filter(equip -> equip.glassID == user.selectGlass)
                .sorted(Comparator.comparingInt((Equip equip) -> equip.vip != 0 ? 9 : equip.type).thenComparingInt(equip -> equip.dbKey))
                .forEach(equip -> {
                    Map<String, Object> row = gear(equip);
                    row.put("dbKey", equip.dbKey);
                    row.put("days", equip.date());
                    row.put("inUse", equip.isUse);
                    row.put("wearable", equip.date() > 0 && glass.level >= equip.level);
                    bag.add(row);
                });
        view.put("bag", bag);
        view.put("bagSize", user.equips.size());
        view.put("bagLimit", BAG_LIMIT);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item item : user.items) {
            if (item.num > 0) items.add(Map.of("id", item.id, "name", item.name, "num", item.num));
        }
        view.put("items", items);
        List<Map<String, Object>> specials = new ArrayList<>();
        for (LinhTinh special : user.linhtinhs) {
            specials.add(Map.of("id", (int) special.id, "name", special.name, "detail", special.detail == null ? "" : special.detail, "num", special.num));
        }
        view.put("specials", specials);
        return view;
    }
}
