import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import mobiarmy.Util;
import mobiarmy.server.Equip;
import mobiarmy.server.Exp;
import mobiarmy.server.Glass;
import mobiarmy.server.Item;
import mobiarmy.server.LinhTinh;
import mobiarmy.server.Server;
import mobiarmy.server.SessionHandler;
import mobiarmy.server.SessionManager;
import mobiarmy.server.ShopEquipment;
import mobiarmy.server.ShopLinhTinh;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;
import mobiarmy.war.RoomRules;
import mobiarmy.war.SkillBalance;

/**
 * What a signed-in player can do from the web: pick a character, spend stat points, wear gear and shop.
 * Everything runs on the game thread against the in-memory player, with the same rules as the game client,
 * so a player who is also in the game sees the change right away.
 */
final class LanPlayerGame {
    /** The bag packet sends its size as a signed byte; stay well below that. */
    static final int BAG_LIMIT = mobiarmy.server.ClientVersion.BAG_LIMIT;
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
            syncGame(user);
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

    /** Gives back every stat point spent on the selected character: stats go back to the character's defaults. */
    Map<String, Object> resetPoints(int userId, String username) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Glass glass = user.glass();
            int[] base = Glass.entrys[glass.id].ability;
            int spent = 0;
            for (int i = 0; i < 5; i++) spent += Math.max(0, glass.ability[i] - base[i]);
            if (spent == 0) throw new LanApi.BadRequest(glass.name + " chưa cộng điểm nào để đặt lại.");
            glass.ability = base.clone();
            glass.point += spent;
            glass.updateAll();
            if (user.session != null) user.session.sessionHandler.loadInfo();
            Server.requestSave();
            log(user, "reset " + spent + " stat points on " + glass.name);
            return done(user, "Đã đặt lại tiềm năng của " + glass.name + ": trả lại " + spent + " điểm, giờ có " + glass.point + " điểm để cộng.");
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
            syncGame(user);
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
            syncGame(user);
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
            List<Map<String, Object>> chests = new ArrayList<>();
            for (LanChests.Chest chest : LanChests.Chest.values()) {
                LinhTinh catalog = LinhTinh.get(chest.id);
                if (catalog == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", chest.key);
                row.put("name", catalog.name);
                row.put("detail", catalog.detail);
                row.put("xu", catalog.xu);
                row.put("luong", catalog.luong);
                row.put("plus", chest.plus);
                List<Map<String, Object>> prizes = new ArrayList<>();
                for (LanChests.Prize prize : LanChests.prizes(chest, user)) {
                    Map<String, Object> prizeRow = gear(prize.entry());
                    prizeRow.put("chance", Math.round(prize.chance() * 10_000) / 100.0);
                    prizes.add(prizeRow);
                }
                row.put("prizes", prizes);
                chests.add(row);
            }
            shop.put("chests", chests);
            return shop;
        });
    }

    /** Buys a lucky chest and opens it at once (see LanChests). */
    Map<String, Object> openChest(int userId, String username, String key, boolean luong) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            LanChests.Chest chest = LanChests.find(key);
            LinhTinh catalog = LinhTinh.get(chest.id);
            if (catalog == null) throw new NoSuchElementException("Cửa hàng chưa bán " + chest.name + ".");
            if (user.equips.size() >= BAG_LIMIT) throw new LanApi.BadRequest("Rương đồ đã đầy (tối đa " + BAG_LIMIT + " món).");
            List<LanChests.Prize> prizes = LanChests.prizes(chest, user);
            if (prizes.isEmpty()) throw new LanApi.BadRequest(catalog.name + " không có gì cho " + user.glass().name + ".");
            int price = luong ? catalog.luong : catalog.xu;
            pay(user, price, luong, catalog.name);
            LanChests.Prize prize = LanChests.pick(prizes, Util.nextDouble());
            Equip entry = prize.entry();
            Equip owned = null;
            if (chest.skins && !ThorSkin.is(entry)) {
                for (Equip equip : user.equips) {
                    if (equip.glassID == entry.glassID && equip.id == entry.id && (owned == null || equip.date() > owned.date())) owned = equip;
                }
            }
            Equip reward;
            String message;
            if (owned != null) {
                int days = LanChests.extend(owned, System.currentTimeMillis());
                if (user.session != null) user.session.sessionHandler.updateEquip(owned);
                reward = owned;
                message = "Bạn đã có " + displayName(owned) + " nên được cộng thêm ngày, giờ còn " + days + " ngày.";
            } else {
                reward = entry.deepCopy();
                reward.renewalDate = System.currentTimeMillis();
                if (chest.plus > 0) setPlus(reward, upgradeStats(reward), chest.plus);
                user.addEquip(reward);
                int element = chest.plus >= mobiarmy.war.SetElement.FIRST_PLUS ? chestElement(user, reward) : 0;
                if (element > 0) {
                    Server.dbManager.update("INSERT INTO lan_equip_element (user_id, db_key, element) VALUES (?, ?, ?)"
                            + " ON DUPLICATE KEY UPDATE element = VALUES(element)", userId, reward.dbKey, element);
                }
                message = "Bạn nhận được " + displayName(reward) + (reward.level2 > 0 ? " +" + reward.level2 : "")
                        + (element > 0 ? ", hệ " + mobiarmy.war.SetElement.NAMES[element] : "") + "!";
            }
            Server.requestSave();
            syncGame(user);
            log(user, "opened " + catalog.name + " for " + price + (luong ? " luong" : " xu") + ": " + displayName(reward)
                    + (reward.level2 > 0 ? " +" + reward.level2 : "") + (owned != null ? " (more days)" : ""));
            notifyGame(user, "Mở " + catalog.name + " trên web: " + displayName(reward) + (reward.level2 > 0 ? " +" + reward.level2 : "") + ".");
            Map<String, Object> result = done(user, message);
            Map<String, Object> won = gear(reward);
            won.put("dbKey", reward.dbKey);
            won.put("plus", (int) reward.level2);
            won.put("days", reward.date());
            won.put("inUse", reward.isUse);
            won.put("wearable", reward.date() > 0 && user.glass().level >= reward.level);
            won.put("extended", owned != null);
            won.put("chance", Math.round(prize.chance() * 10_000) / 100.0);
            won.put("chest", chest.key);
            result.put("prize", won);
            return result;
        });
    }

    /**
     * Element of a chest prize at +15 or more: the one most of the character's other +15 pieces have, so it can
     * join their set (SetElement.of), else a random one.
     */
    static int chestElement(User user, Equip prize) {
        Map<Integer, Integer> elements = mobiarmy.war.SetElement.elements(user);
        int[] count = new int[mobiarmy.war.SetElement.NAMES.length];
        for (Equip equip : user.equips) {
            Integer element = elements.get(equip.dbKey);
            if (equip != prize && equip.glassID == prize.glassID && equip.level2 >= mobiarmy.war.SetElement.FIRST_PLUS
                    && element != null && element > 0 && element < count.length) count[element]++;
        }
        int best = 0;
        for (int e = 1; e < count.length; e++) if (count[e] > count[best]) best = e;
        return best > 0 ? best : Util.nextInt(1, count.length);
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
            syncGame(user);
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

    // --- selling (the game's prices) --------------------------------------------------------------

    /** Same as the game's "bán trang bị": gear of the selected character that is not worn and holds no gems. */
    Map<String, Object> sellEquipment(int userId, String username, int[] dbKeys) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            if (dbKeys.length == 0) throw new LanApi.BadRequest("Chưa chọn món nào để bán.");
            if (dbKeys.length > BAG_LIMIT) throw new LanApi.BadRequest("Chọn tối đa " + BAG_LIMIT + " món.");
            List<Equip> sold = new ArrayList<>();
            long price = 0;
            for (int dbKey : dbKeys) {
                Equip equip = ownedForSelected(user, dbKey);
                if (sold.contains(equip)) continue;
                if (equip.isUse) throw new LanApi.BadRequest("Hãy tháo " + displayName(equip) + " ra trước khi bán.");
                // The game asks to take the gems out first rather than sell them with the gear.
                if (equip.slot.length - equip.slot() > 0) {
                    throw new LanApi.BadRequest(displayName(equip) + " còn gắn ngọc: tháo ngọc ở tab Khảm ngọc rồi hãy bán.");
                }
                sold.add(equip);
                price += sellPrice(equip);
            }
            boolean online = user.session != null;
            user.equips.removeAll(sold);
            if (online) user.session.sessionHandler.removeEquip(sold.toArray(new Equip[0]));
            user.addXu((int) Math.min(price, Integer.MAX_VALUE), online);
            Server.requestSave();
            syncGame(user);
            List<String> names = new ArrayList<>();
            for (Equip equip : sold) names.add(displayName(equip) + (equip.level2 > 0 ? " +" + equip.level2 : ""));
            log(user, "sold " + String.join(", ", names) + " for " + price + " xu");
            return done(user, "Đã bán " + (sold.size() == 1 ? names.get(0) : sold.size() + " món") + ", thu " + money(price) + " xu.");
        });
    }

    /** Same as the game's "bán ngọc": half the xu price for each one sold; used for every special item here. */
    Map<String, Object> sellSpecial(int userId, String username, int id, int amount) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            LinhTinh owned = user.getLinhTinh(id);
            if (owned == null || owned.num <= 0) throw new NoSuchElementException("Bạn không có món này.");
            int unit = Math.max(0, owned.xu / 2);
            if (unit == 0) throw new LanApi.BadRequest(owned.name + " không bán được.");
            if (amount < 1 || amount > owned.num) throw new LanApi.BadRequest("Bạn có " + owned.num + " " + owned.name + ", bán từ 1 đến " + owned.num + ".");
            long total = (long) unit * amount;
            String name = owned.name;
            user.addLinhTinh(owned.id, -amount);
            user.addXu((int) Math.min(total, Integer.MAX_VALUE), user.session != null);
            Server.requestSave();
            log(user, "sold " + amount + " x " + name + " for " + total + " xu");
            return done(user, "Đã bán " + amount + " " + name + ", thu " + money(total) + " xu.");
        });
    }

    /** Battle items sell back for half their xu price too; HP and Teleport are free and cannot be sold. */
    Map<String, Object> sellItem(int userId, String username, int id, int amount) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Item owned = user.getItem(id);
            if (owned == null || owned.num <= 0) throw new NoSuchElementException("Bạn không có vật phẩm này.");
            int unit = Math.max(0, owned.xu / 2);
            if (unit == 0) throw new LanApi.BadRequest(owned.name + " không bán được.");
            if (amount < 1 || amount > owned.num) throw new LanApi.BadRequest("Bạn có " + owned.num + " " + owned.name + ", bán từ 1 đến " + owned.num + ".");
            long total = (long) unit * amount;
            owned.num -= amount;
            user.addXu((int) Math.min(total, Integer.MAX_VALUE), false);
            if (user.session != null) user.session.sessionHandler.updateItem(owned, (int) user.xu, (int) user.luong);
            Server.requestSave();
            log(user, "sold " + amount + " x " + owned.name + " for " + total + " xu");
            return done(user, "Đã bán " + amount + " " + owned.name + ", thu " + money(total) + " xu.");
        });
    }

    /**
     * Equip.calculatePrice (half the xu price, or 500 xu per lượng, for the days left), except that days a lucky
     * chest added on top of the item's own duration do not raise it.
     */
    static int sellPrice(Equip equip) {
        int full = equip.xu != -1 ? equip.xu / 2 : equip.luong != -1 ? equip.luong * 500 : 0;
        if (full <= 0 || equip.date <= 0) return 0;
        return (int) ((long) full * Math.min(equip.date(), equip.date) / equip.date);
    }

    /** Same as the game's "gắn ngọc": one gem into the next free hole of a piece of gear. */
    Map<String, Object> socketGem(int userId, String username, int dbKey, int gemId) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Equip equip = ownedForSelected(user, dbKey);
            LinhTinh gem = ownedGem(user, gemId);
            if (equip.slot() <= 0) throw new LanApi.BadRequest(displayName(equip) + " đã hết lỗ để gắn ngọc.");
            equip.slot[equip.slot() - 1] = gem.id;
            for (int i = 0; i < gem.ability.length; i++) equip.inv_ability[i] += gem.ability[i];
            if (user.session != null) user.session.sessionHandler.updateEquip(equip);
            user.addLinhTinh(gem.id, -1);
            Server.requestSave();
            syncGame(user);
            log(user, "socketed " + gem.name + " into " + displayName(equip));
            return done(user, "Đã gắn " + gem.name + " vào " + displayName(equip) + ".");
        });
    }

    /** Same as the game: takes every gem out for 25% of their xu value; the gear must not be worn. */
    Map<String, Object> unsocketGems(int userId, String username, int dbKey) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            Equip equip = ownedForSelected(user, dbKey);
            if (equip.slot.length - equip.slot() <= 0) throw new LanApi.BadRequest(displayName(equip) + " chưa gắn ngọc nào.");
            if (equip.isUse) throw new LanApi.BadRequest("Hãy tháo " + displayName(equip) + " ra trước khi lấy ngọc.");
            int price = (int) (equip.priceSlot() * 0.25);
            pay(user, price, false, "tháo ngọc");
            for (int i = 0; i < equip.slot.length; i++) {
                LinhTinh gem = equip.slot[i] == -1 ? null : LinhTinh.get(equip.slot[i]);
                if (gem != null) {
                    user.addLinhTinh(gem.id, 1);
                    for (int j = 0; j < gem.ability.length; j++) equip.inv_ability[j] -= gem.ability[j];
                }
                equip.slot[i] = -1;
            }
            if (user.session != null) user.session.sessionHandler.updateEquip(equip);
            Server.requestSave();
            syncGame(user);
            log(user, "unsocketed gems from " + displayName(equip) + " for " + price + " xu");
            return done(user, "Đã tháo ngọc khỏi " + displayName(equip) + ", phí " + String.format("%,d", price).replace(',', '.') + " xu.");
        });
    }

    /** Same as the game: every 5 gems is one try at the next level; a failed try loses 1 gem. */
    Map<String, Object> combineGems(int userId, String username, int gemId, int amount) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            LinhTinh gem = ownedGem(user, gemId);
            if ((gem.id + 1) % 10 == 0) throw new LanApi.BadRequest(gem.name + " đã là cấp cao nhất.");
            if (amount < 5 || amount % 5 != 0) throw new LanApi.BadRequest("Số ngọc ghép phải là bội số của 5.");
            if (amount > gem.num) throw new LanApi.BadRequest("Bạn chỉ có " + gem.num + " " + gem.name + ".");
            int rate = 100 - ((gem.id + 1) % 10) * 10;
            int tries = amount / 5, success = 0;
            for (int i = 0; i < tries; i++) {
                if (Util.nextInt(1, 101) <= rate) {
                    success++;
                    user.addLinhTinh(gem.id, -5);
                    user.addLinhTinh(gem.id + 1, 1);
                    int level = (gem.id + 1) % 10;
                    if (level >= 7 && level <= 9) user.addMission(level + 2, 1);
                } else {
                    user.addLinhTinh(gem.id, -1);
                }
            }
            Server.requestSave();
            String next = LinhTinh.get(gem.id + 1).name;
            log(user, "combined " + gem.name + ": " + success + "/" + tries);
            return done(user, success > 0
                    ? "Ghép thành công " + success + "/" + tries + " lần, nhận " + success + " " + next + "."
                    : "Ghép thất bại cả " + tries + " lần. Chúc bạn may mắn lần sau.");
        });
    }

    // --- upgrade (+1 to +16; +15 picks the element of the set, SetElement) ----------------------

    static final int MAX_PLUS = 16;
    /** Special item used as upgrade stone; its icon is server/res/icon/item/80.png. */
    static final int STONE_ID = 80;
    /** Index = target level, in tenths of a percent. Money is plentiful on the LAN server, so the rate is what makes the top levels rare. */
    static final int[] UPGRADE_RATE = {0, 1000, 900, 800, 700, 600, 500, 400, 320, 250, 180, 120, 80, 2, 1, 1, 1};
    /** Success chance is UPGRADE_RATE[level] out of this many. */
    static final int RATE_SCALE = 1000;
    /** +13 to +16 never take more than this many tries in a row: the last one always succeeds. */
    static final int[] UPGRADE_PITY = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 500, 1000, 1500, 2000};
    static final int[] UPGRADE_XU = {0, 5_000, 9_000, 16_000, 29_000, 52_000, 94_000, 170_000, 306_000, 551_000, 992_000, 1_785_000, 3_213_000, 5_784_000, 10_411_000, 18_740_000, 33_730_000};
    static final int[] UPGRADE_LUONG = {0, 0, 0, 0, 0, 0, 0, 0, 20, 40, 80, 160, 320, 640, 1280, 2560, 5120};
    static final int[] UPGRADE_STONES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    /** Total points added to each stat the item has at a given level (stored in level2). */
    static final int[] UPGRADE_BONUS = {0, 1, 2, 3, 4, 6, 8, 10, 12, 15, 18, 22, 26, 31, 37, 43, 50};
    /** From this level a failed try drops the item one level. */
    static final int DROP_FROM = 7;

    /** Upgrade insurance: special items that keep the level on a failed try, each up to a target level. */
    enum Insurance {
        BASIC(88, "Bảo hiểm sơ cấp", 9, 50_000, 20),
        MEDIUM(89, "Bảo hiểm trung cấp", 12, 300_000, 100),
        HIGH(90, "Bảo hiểm cao cấp", 16, 2_000_000, 500);

        final int id;
        final String name;
        final int upTo;
        final int xu;
        final int luong;

        Insurance(int id, String name, int upTo, int xu, int luong) {
            this.id = id;
            this.name = name;
            this.upTo = upTo;
            this.xu = xu;
            this.luong = luong;
        }

        static Insurance of(int id) {
            for (Insurance insurance : values()) if (insurance.id == id) return insurance;
            throw new LanApi.BadRequest("Không có loại bảo hiểm này.");
        }
    }

    /**
     * The swirl on the web page lasts this long, a success at +12 or higher a little longer, and the server refuses
     * the next try before it ends, so auto clickers gain nothing over watching it.
     */
    static int spinMillis(int target, boolean success) {
        return success && target >= 12 ? 2000 : 1000;
    }

    /** Failed tries in a row at this target level for one item (pity counter), kept in the database. */
    private static int pityFails(int userId, int dbKey, int target) throws java.sql.SQLException {
        var rows = Server.dbManager.selectColumnName("SELECT fails FROM lan_upgrade_pity WHERE user_id = ? AND db_key = ? AND level = ?", userId, dbKey, target);
        return rows.isEmpty() ? 0 : rows.get(0).getInt("fails");
    }

    /** pityFails for the page: a database hiccup shows 0 rather than breaking the whole view. */
    private static int shownPityFails(int userId, int dbKey, int target) {
        try {
            return pityFails(userId, dbKey, target);
        } catch (java.sql.SQLException e) {
            return 0;
        }
    }

    private static void setPityFails(int userId, int dbKey, int target, int fails) {
        if (fails == 0) {
            Server.dbManager.update("DELETE FROM lan_upgrade_pity WHERE user_id = ? AND db_key = ? AND level = ?", userId, dbKey, target);
        } else {
            Server.dbManager.update("INSERT INTO lan_upgrade_pity (user_id, db_key, level, fails) VALUES (?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE fails = VALUES(fails)", userId, dbKey, target, fails);
        }
    }

    private final Map<Integer, Long> nextUpgradeAt = new java.util.HashMap<>();

    /** Adds the insurance rows to the special item catalog and the shop, and the pity table; safe to run on every start. */
    // --- match stats ("Thành tích"): win 3 points, draw 1 ----------------------------------------

    private static final String POINTS = "3 * SUM(result = 1) + SUM(result = 0)";

    /** Totals, last 30 matches with every fighter, head-to-head against each enemy, and the leaderboard. */
    Map<String, Object> stats(int userId, String username) throws Exception {
        game.call(() -> player(userId, username));
        Map<String, Object> out = new LinkedHashMap<>();
        var total = Server.dbManager.selectColumnName("SELECT COUNT(*) AS matches, COALESCE(SUM(result = 1), 0) AS wins,"
                + " COALESCE(SUM(result = 0), 0) AS draws, COALESCE(SUM(result = -1), 0) AS losses, COALESCE(SUM(dealt), 0) AS dealt,"
                + " COALESCE(SUM(taken), 0) AS taken, COALESCE(SUM(healed), 0) AS healed, COALESCE(SUM(kills), 0) AS kills,"
                + " COALESCE(" + POINTS + ", 0) AS points FROM lan_match_player WHERE user_id = ?", userId);
        out.put("total", total.get(0));
        var mine = Server.dbManager.selectColumnName("SELECT match_key, DATE_FORMAT(played_at, '%d/%m %H:%i') AS at, map_id, glass_id,"
                + " team, result, dealt, taken, healed, kills FROM lan_match_player WHERE user_id = ? ORDER BY id DESC LIMIT 30", userId);
        List<Object> matches = new ArrayList<>();
        for (var m : mine) {
            m.put("glass", glassName(m.getInt("glass_id")));
            var fighters = Server.dbManager.selectColumnName("SELECT name, glass_id, team, result, dealt, taken, healed, kills"
                    + " FROM lan_match_player WHERE match_key = ? ORDER BY team, id", m.getString("match_key"));
            for (var f : fighters) f.put("glass", glassName(f.getInt("glass_id")));
            m.put("fighters", fighters);
            matches.add(m);
        }
        out.put("matches", matches);
        out.put("rivals", Server.dbManager.selectColumnName("SELECT o.name, COUNT(*) AS matches, SUM(me.result = 1) AS wins,"
                + " SUM(me.result = 0) AS draws, SUM(me.result = -1) AS losses FROM lan_match_player me"
                + " JOIN lan_match_player o ON o.match_key = me.match_key AND o.team <> me.team AND o.user_id <> me.user_id"
                + " WHERE me.user_id = ? GROUP BY o.user_id, o.name ORDER BY matches DESC LIMIT 20", userId));
        out.put("ranking", Server.dbManager.selectColumnName("SELECT user_id, MAX(name) AS name, " + POINTS + " AS points,"
                + " COUNT(*) AS matches, SUM(result = 1) AS wins, SUM(dealt) AS dealt, SUM(kills) AS kills"
                + " FROM lan_match_player GROUP BY user_id ORDER BY points DESC, wins DESC, dealt DESC LIMIT 50"));
        out.put("me", userId);
        return out;
    }

    private static String glassName(int id) {
        String[] names = {"Gunner", "Miss 6", "Electrician", "King Kong", "Rocketer", "Granos", "Chicky", "Tarzan", "Apache", "Magenta"};
        return id >= 0 && id < names.length ? names[id] : "?";
    }

    static void ensureUpgradeCatalog(java.sql.Connection conn) throws java.sql.SQLException {
        try (java.sql.Statement create = conn.createStatement()) {
            create.executeUpdate("CREATE TABLE IF NOT EXISTS lan_upgrade_pity (user_id INT NOT NULL, db_key INT NOT NULL,"
                    + " level TINYINT NOT NULL, fails INT NOT NULL, PRIMARY KEY (user_id, db_key, level))");
            // The element a piece took at +15 (SetElement: Lôi 1, Hỏa 2, Băng 3, Độc 4).
            create.executeUpdate("CREATE TABLE IF NOT EXISTS lan_equip_element (user_id INT NOT NULL, db_key INT NOT NULL,"
                    + " element TINYINT NOT NULL, PRIMARY KEY (user_id, db_key))");
        }
        try (java.sql.PreparedStatement item = conn.prepareStatement(
                "INSERT INTO linhtinh (id, name, detail, ability, xu, luong, date) SELECT ?, ?, ?, '[0,0,0,0,0]', ?, ?, 0"
                        + " FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM linhtinh WHERE id = ?)");
             java.sql.PreparedStatement detail = conn.prepareStatement("UPDATE linhtinh SET detail = ? WHERE id = ?");
             java.sql.PreparedStatement shop = conn.prepareStatement(
                "INSERT INTO shop_linhtinh (id, isSelectNum) SELECT ?, 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM shop_linhtinh WHERE id = ?)")) {
            for (Insurance insurance : Insurance.values()) {
                String text = "Nâng cấp tới +" + insurance.upTo + " thất bại không bị tụt cấp. Mất 1 cái mỗi lần nâng cấp có dùng.";
                item.setInt(1, insurance.id);
                item.setString(2, insurance.name);
                item.setString(3, text);
                item.setInt(4, insurance.xu);
                item.setInt(5, insurance.luong);
                item.setInt(6, insurance.id);
                item.executeUpdate();
                detail.setString(1, text); // (the top one covers +16 since the +15/+16 upgrade)
                detail.setInt(2, insurance.id);
                detail.executeUpdate();
                shop.setInt(1, insurance.id);
                shop.setInt(2, insurance.id);
                shop.executeUpdate();
            }
        }
    }

    Map<String, Object> upgrade(int userId, String username, int dbKey, int insuranceId, int element) throws Exception {
        return game.call(() -> {
            User user = player(userId, username);
            requireFree(user);
            long now = System.currentTimeMillis();
            long waitUntil = nextUpgradeAt.getOrDefault(userId, 0L);
            if (now < waitUntil) {
                throw new LanApi.ApiError(429, "Vòng xoáy còn đang quay, đợi " + ((waitUntil - now + 999) / 1000) + " giây nữa.");
            }
            Equip equip = ownedForSelected(user, dbKey);
            int plus = equip.level2;
            if (plus >= MAX_PLUS) throw new LanApi.BadRequest(displayName(equip) + " đã đạt +" + MAX_PLUS + ".");
            Insurance insurance = insuranceId == 0 ? null : Insurance.of(insuranceId);
            if (insurance != null) {
                if (plus + 1 > insurance.upTo) throw new LanApi.BadRequest(insurance.name + " chỉ dùng được khi nâng tới +" + insurance.upTo + ".");
                LinhTinh owned = user.getLinhTinh(insurance.id);
                if (owned == null || owned.num < 1) throw new LanApi.BadRequest("Bạn không có " + insurance.name + ". Mua ở Cửa hàng → Đồ đặc biệt.");
            }
            boolean[] stats = upgradeStats(equip);
            if (!any(stats)) throw new LanApi.BadRequest(displayName(equip) + " không có chỉ số để nâng cấp.");
            if (equip.date() <= 0 && !ThorSkin.is(equip)) throw new LanApi.BadRequest(displayName(equip) + " đã hết hạn.");
            int next = plus + 1;
            boolean pickElement = next == mobiarmy.war.SetElement.FIRST_PLUS;
            if (pickElement && (element < 1 || element >= mobiarmy.war.SetElement.NAMES.length)) {
                throw new LanApi.BadRequest("Lên +" + next + " cần chọn hệ cho bộ: Lôi, Hỏa, Băng hoặc Độc.");
            }
            LinhTinh stones = user.getLinhTinh(STONE_ID);
            int haveStones = stones == null ? 0 : stones.num;
            if (haveStones < UPGRADE_STONES[next]) {
                throw new LanApi.BadRequest("Cần " + UPGRADE_STONES[next] + " Đá cường hóa, bạn có " + haveStones + ". Mua ở Cửa hàng → Đồ đặc biệt.");
            }
            if (user.xu < UPGRADE_XU[next]) throw new LanApi.BadRequest("Không đủ xu: cần " + money(UPGRADE_XU[next]) + ".");
            if (user.luong < UPGRADE_LUONG[next]) throw new LanApi.BadRequest("Không đủ lượng: cần " + money(UPGRADE_LUONG[next]) + ".");
            boolean online = user.session != null;
            user.addXu(-UPGRADE_XU[next], online);
            if (UPGRADE_LUONG[next] > 0) user.addLuong(-UPGRADE_LUONG[next], online);
            user.addLinhTinh(STONE_ID, -UPGRADE_STONES[next]);
            if (insurance != null) user.addLinhTinh(insurance.id, -1);
            int pity = UPGRADE_PITY[next];
            int fails = pity > 0 ? pityFails(userId, dbKey, next) : 0;
            boolean success = Util.nextInt(1, RATE_SCALE + 1) <= UPGRADE_RATE[next] || (pity > 0 && fails + 1 >= pity);
            if (pity > 0) setPityFails(userId, dbKey, next, success ? 0 : fails + 1);
            int spin = spinMillis(next, success);
            nextUpgradeAt.put(userId, now + spin);
            int target = success ? next : plus >= DROP_FROM && insurance == null ? plus - 1 : plus;
            setPlus(equip, stats, target);
            if (success && pickElement) {
                // The element of the set (SetElement): the five pieces must share it for it to work.
                Server.dbManager.update("INSERT INTO lan_equip_element (user_id, db_key, element) VALUES (?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE element = VALUES(element)", userId, dbKey, element);
            }
            if (online) user.session.sessionHandler.updateEquip(equip);
            Server.requestSave();
            syncGame(user);
            log(user, "upgrade " + displayName(equip) + " +" + plus + " -> +" + target + (success ? " (success)" : " (fail)")
                    + (insurance != null ? " with " + insurance.name : ""));
            Map<String, Object> result = done(user, success
                    ? "Thành công! " + displayName(equip) + " đã lên +" + target
                            + (pickElement ? ", hệ " + mobiarmy.war.SetElement.NAMES[element] : "") + "."
                    : target < plus ? "Thất bại, " + displayName(equip) + " bị tụt xuống +" + target + "."
                    : "Thất bại, " + displayName(equip) + " vẫn giữ +" + plus + (insurance != null && plus >= DROP_FROM ? " nhờ " + insurance.name : "") + ".");
            result.put("upgrade", Map.of("dbKey", dbKey, "success", success, "from", plus, "to", target, "spinMs", spin));
            return result;
        });
    }

    /** Stats the item itself gives (gems excluded); only these grow when upgrading. */
    private static boolean[] upgradeStats(Equip equip) {
        int[] base = new int[5];
        for (int i = 0; i < 5 && equip.inv_ability != null && i < equip.inv_ability.length; i++) base[i] = equip.inv_ability[i];
        for (short id : equip.slot) {
            LinhTinh gem = id == -1 ? null : LinhTinh.get(id);
            if (gem != null) for (int i = 0; i < 5 && i < gem.ability.length; i++) base[i] -= gem.ability[i];
        }
        for (int i = 0; i < 5; i++) base[i] -= UPGRADE_BONUS[Math.max(0, Math.min(MAX_PLUS, equip.level2))];
        boolean[] stats = new boolean[5];
        for (int i = 0; i < 5; i++) stats[i] = base[i] > 0;
        return stats;
    }

    private static void setPlus(Equip equip, boolean[] stats, int plus) {
        int delta = UPGRADE_BONUS[plus] - UPGRADE_BONUS[equip.level2];
        for (int i = 0; i < 5; i++) if (stats[i]) equip.inv_ability[i] += delta;
        equip.level2 = (byte) plus;
    }

    /** A new piece of a shop entry already upgraded to +plus, the way chest prizes and admin gifts are made. */
    static Equip upgradedCopy(Equip entry, int plus) {
        Equip piece = entry.deepCopy();
        piece.renewalDate = System.currentTimeMillis();
        if (plus > 0) setPlus(piece, upgradeStats(piece), plus);
        return piece;
    }

    private static boolean any(boolean[] values) {
        for (boolean value : values) if (value) return true;
        return false;
    }

    private static String money(long value) {
        return String.format("%,d", value).replace(',', '.');
    }

    // --- helpers ------------------------------------------------------------------------------

    /** Gems are the special items with id below 50 (5 colours x 10 levels). */
    private static LinhTinh ownedGem(User user, int gemId) {
        if (gemId < 0 || gemId >= 50) throw new LanApi.BadRequest("Đây không phải ngọc.");
        LinhTinh gem = user.getLinhTinh(gemId);
        if (gem == null || gem.num <= 0) throw new LanApi.BadRequest("Bạn không có viên ngọc này.");
        return gem;
    }

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

    static void notifyGame(User user, String message) {
        if (user.session != null) user.session.sessionHandler.log(message);
    }

    /**
     * Sends a player who is online in the game everything the client keeps its own copy of, as at login: worn gear
     * of every character, the selected character's VIP costume, the whole bag and the stat points. The client only
     * gets small updates for changes made in the game itself, and drops some of them when another screen is open.
     */
    static void syncGame(User user) {
        if (user.session == null) return;
        for (Glass glass : user.glass) {
            if (glass != null) glass.updateAll();
        }
        SessionHandler client = user.session.sessionHandler;
        client.wornGear();
        Glass selected = user.glass();
        client.setEquipItem(selected.data != null ? 1 : 0, selected.data);
        client.updateRuong(); // closes any open dialog, so notifyGame comes after it
        client.loadInfo();
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
        if (ThorSkin.is(equip) || (equip.id >= 30100 && equip.id <= 30109)) {
            // Thor costumes borrow Iron Man's spot in the game's picture; the web shows their own icon (web/img/thor).
            row.put("icon", "../img/thor/" + equip.id + ".png");
        } else if (equip.x != null && equip.x.length > 0 && equip.w[0] > 0 && equip.h[0] > 0) {
            row.put("sprite", List.of((int) equip.x[0], (int) equip.y[0], (int) equip.w[0], (int) equip.h[0]));
        }
        return row;
    }

    private static int[] toInts(byte[] values) {
        int[] result = new int[5];
        for (int i = 0; values != null && i < Math.min(5, values.length); i++) result[i] = values[i];
        return result;
    }

    /** The room rules a new room starts with, all three modes and the balanced-mode numbers, for the pages that explain them. */
    static Map<String, Object> rulesInfo() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("default", (int) RoomRules.defaultRules);
        List<Map<String, Object>> modes = new ArrayList<>();
        for (int id = RoomRules.FREE; id <= RoomRules.BASE; id++) {
            modes.add(Map.of("id", id, "name", RoomRules.name(id), "detail", RoomRules.detail(id)));
        }
        rules.put("modes", modes);
        rules.put("bonusFull", SkillBalance.BONUS_FULL_ACCURACY);
        rules.put("bonusZero", SkillBalance.BONUS_ZERO_ACCURACY);
        rules.put("pierce", SkillBalance.DIRECT_PIERCE_PERCENT);
        rules.put("tank", SkillBalance.DIRECT_TANK_PERCENT);
        rules.put("baseHp", SkillBalance.BASE_HP);
        rules.put("maxReduction", SkillBalance.MAX_REDUCTION_PERCENT);
        rules.put("maxLuck", SkillBalance.MAX_LUCK_PERCENT);
        return rules;
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
        int[] stats = glass.createAbility();
        character.put("stats", stats);
        // Same sums as Glass.createAbility, kept apart so the page can show where each stat comes from.
        int[] gearFlat = new int[5], gearPercent = new int[5];
        int worn = 0;
        for (Equip equip : user.equips) {
            if (equip.glassID != glass.id || !equip.isUse) continue;
            worn++;
            for (int i = 0; i < 5; i++) {
                if (equip.inv_ability != null && i < equip.inv_ability.length) gearFlat[i] += equip.inv_ability[i];
                if (equip.inv_percen != null && i < equip.inv_percen.length) gearPercent[i] += equip.inv_percen[i];
            }
        }
        character.put("gearFlat", gearFlat);
        character.put("gearPercent", gearPercent);
        character.put("wornCount", worn);
        character.put("baseAttack", glass.att);
        view.put("character", character);
        view.put("rules", rulesInfo());
        view.put("baseStats", RoomRules.baseStats(glass));
        List<Map<String, Object>> bag = new ArrayList<>();
        Map<Integer, Integer> elements = mobiarmy.war.SetElement.elements(user);
        user.equips.stream()
                .filter(equip -> equip.glassID == user.selectGlass)
                .sorted(Comparator.comparingInt((Equip equip) -> equip.vip != 0 ? 9 : equip.type).thenComparingInt(equip -> equip.dbKey))
                .forEach(equip -> {
                    Map<String, Object> row = gear(equip);
                    row.put("dbKey", equip.dbKey);
                    row.put("days", equip.date());
                    row.put("inUse", equip.isUse);
                    row.put("wearable", equip.date() > 0 && glass.level >= equip.level);
                    List<Integer> gems = new ArrayList<>();
                    for (short gem : equip.slot) if (gem != -1) gems.add((int) gem);
                    row.put("gems", gems);
                    row.put("holes", equip.slot.length);
                    row.put("unsocketPrice", (int) (equip.priceSlot() * 0.25));
                    row.put("sellPrice", sellPrice(equip));
                    int plus = Math.max(0, Math.min(MAX_PLUS, equip.level2));
                    row.put("plus", plus);
                    row.put("upgradeable", any(upgradeStats(equip)));
                    row.put("element", elements.getOrDefault(equip.dbKey, 0)); // SetElement, picked at +15
                    if (plus < MAX_PLUS) {
                        int next = plus + 1;
                        row.put("next", Map.of("rate", UPGRADE_RATE[next], "xu", UPGRADE_XU[next], "luong", UPGRADE_LUONG[next],
                                "stones", UPGRADE_STONES[next], "bonus", UPGRADE_BONUS[next] - UPGRADE_BONUS[plus], "dropOnFail", plus >= DROP_FROM,
                                "pity", UPGRADE_PITY[next], "fails", UPGRADE_PITY[next] > 0 ? shownPityFails(user.id, equip.dbKey, next) : 0,
                                "pickElement", next == mobiarmy.war.SetElement.FIRST_PLUS));
                    }
                    bag.add(row);
                });
        view.put("bag", bag);
        view.put("bagSize", user.equips.size());
        view.put("bagLimit", BAG_LIMIT);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item item : user.items) {
            if (item.num > 0) items.add(Map.of("id", item.id, "name", item.name, "num", item.num, "sell", Math.max(0, item.xu / 2)));
        }
        view.put("items", items);
        List<Map<String, Object>> specials = new ArrayList<>();
        for (LinhTinh special : user.linhtinhs) {
            specials.add(Map.of("id", (int) special.id, "name", special.name, "detail", special.detail == null ? "" : special.detail, "num", special.num,
                    "sell", Math.max(0, special.xu / 2)));
        }
        view.put("specials", specials);
        return view;
    }
}
