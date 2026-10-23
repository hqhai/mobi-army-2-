import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import mobiarmy.server.Equip;
import mobiarmy.server.ShopEquipment;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;

/**
 * Lucky chests on the web shop. Each chest is a special item row so the admin can rename or reprice it on the
 * "Đồ đặc biệt" page, but it is not in the game's own shop and never sits in a bag: it opens when bought.
 * Prizes come from the selected character's shop, and the pricier an item is, the rarer it drops.
 */
final class LanChests {
    /** Days a skin can have left after a chest adds time to it; the game sends days as a signed byte. */
    static final int MAX_DAYS = 120;

    enum Chest {
        /** Any weapon or gear. */
        BRONZE(81, "bronze", "Rương đồng", "Mở ra 1 vũ khí hoặc trang bị ngẫu nhiên của nhân vật đang chọn. Đồ càng xịn càng hiếm.", 15_000, 15, 0.5, 0, false),
        /** A costume (VIP skin, Thor for Apache); one you already have gets more days instead. */
        SILVER(82, "silver", "Rương bạc", "Mở ra 1 trang phục (skin) ngẫu nhiên của nhân vật đang chọn. Đã có skin đó thì được cộng thêm ngày.", -1, 30, 1.0, 0, true),
        /** Any weapon or gear, already upgraded to +10. */
        GOLD(83, "gold", "Rương vàng", "Mở ra 1 vũ khí hoặc trang bị ngẫu nhiên đã cường hóa +10.", 3_000_000, 300, 0.25, 10, false),
        /** Any weapon or gear, already upgraded to the top level +14. */
        PLATINUM(84, "platinum", "Rương bạch kim", "Mở ra 1 vũ khí hoặc trang bị ngẫu nhiên đã cường hóa +14, cấp cao nhất.", 30_000_000, 3000, 0.25, 14, false);

        final int id;
        final String key;
        final String name;
        final String detail;
        final int xu;
        final int luong;
        /** Weight of a prize is its price to the power -rarity: 0 makes every prize equally likely. */
        final double rarity;
        final int plus;
        final boolean skins;

        Chest(int id, String key, String name, String detail, int xu, int luong, double rarity, int plus, boolean skins) {
            this.id = id;
            this.key = key;
            this.name = name;
            this.detail = detail;
            this.xu = xu;
            this.luong = luong;
            this.rarity = rarity;
            this.plus = plus;
            this.skins = skins;
        }
    }

    record Prize(Equip entry, double chance) {
    }

    private LanChests() {
    }

    /** Adds the chest rows to the special item catalog of an existing database; safe to run on every start. */
    static void ensureCatalog(Connection conn) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO linhtinh (id, name, detail, ability, xu, luong, date) SELECT ?, ?, ?, '[0,0,0,0,0]', ?, ?, 0"
                        + " FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM linhtinh WHERE id = ?)")) {
            for (Chest chest : Chest.values()) {
                insert.setInt(1, chest.id);
                insert.setString(2, chest.name);
                insert.setString(3, chest.detail);
                insert.setInt(4, chest.xu);
                insert.setInt(5, chest.luong);
                insert.setInt(6, chest.id);
                insert.executeUpdate();
            }
        }
    }

    static Chest find(String key) {
        for (Chest chest : Chest.values()) {
            if (chest.key.equals(key)) return chest;
        }
        throw new NoSuchElementException("Không có loại rương này.");
    }

    /** What the chest can give the user's selected character, most likely first. */
    static List<Prize> prizes(Chest chest, User user) {
        List<Equip> pool = new ArrayList<>();
        for (Equip entry : ShopEquipment.generate(user.selectGlass)) {
            boolean skin = entry.vip != 0 || ThorSkin.is(entry);
            if (skin != chest.skins || value(entry) <= 0) continue;
            if (!skin && (entry.type < 0 || entry.type > 4)) continue;
            if (chest.plus > 0 && !hasStats(entry)) continue;
            if (ThorSkin.is(entry) && user.equips.stream().anyMatch(ThorSkin::is)) continue; // only one Thor per player
            pool.add(entry);
        }
        double total = 0;
        for (Equip entry : pool) total += weight(chest, entry);
        List<Prize> prizes = new ArrayList<>();
        for (Equip entry : pool) prizes.add(new Prize(entry, weight(chest, entry) / total));
        prizes.sort(Comparator.comparingDouble(Prize::chance).reversed());
        return prizes;
    }

    /** roll is uniform in [0, 1). */
    static Prize pick(List<Prize> prizes, double roll) {
        double sum = 0;
        for (Prize prize : prizes) {
            sum += prize.chance();
            if (roll < sum) return prize;
        }
        return prizes.get(prizes.size() - 1);
    }

    /** Shop price in xu; a lượng counts as 1,000 xu like the game's renewal price. */
    static long value(Equip entry) {
        if (entry.xu > 0) return entry.xu;
        if (entry.luong > 0) return entry.luong * 1000L;
        return 0;
    }

    /** Gives an owned costume another full duration on top of the days it has left; returns the days it now has. */
    static int extend(Equip owned, long now) {
        long day = 24L * 60 * 60 * 1000;
        int target = Math.min(MAX_DAYS, owned.date() + owned.date);
        int extra = target - owned.date;
        // Equip.date() is date - (now - renewalDate) / day, and long division rounds toward zero. A renewal date in
        // the future therefore counts whole days ahead: putting it just under one more day ahead keeps the target
        // for a full day, then it counts down one per day like any other item.
        owned.renewalDate = extra > 0 ? now + (extra + 1) * day - 1 : now - (long) (owned.date - target) * day;
        return target;
    }

    private static double weight(Chest chest, Equip entry) {
        return Math.pow(value(entry), -chest.rarity);
    }

    private static boolean hasStats(Equip entry) {
        for (int i = 0; entry.inv_ability != null && i < entry.inv_ability.length; i++) {
            if (entry.inv_ability[i] > 0) return true;
        }
        return false;
    }
}
