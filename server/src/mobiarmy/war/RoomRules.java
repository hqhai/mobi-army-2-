package mobiarmy.war;

import mobiarmy.server.Glass;
import mobiarmy.server.User;

/**
 * Match rules the room owner picks in the waiting room (Menu → Cài đặt → Luật trận đấu). Sent to clients as one byte.
 * <ul>
 * <li>Tự do: the game's own rules; every stat point, piece of gear, upgrade and gem counts.</li>
 * <li>Cân bằng: SkillBalance; stats only pay off on accurate shots and Skin Thor gives nothing.</li>
 * <li>Sát thương gốc: everyone fights with a fresh character's stats; points, gear, upgrades and gems do not count.</li>
 * </ul>
 */
public final class RoomRules {
    public static final byte FREE = 0, BALANCED = 1, BASE = 2;
    private static final String[] NAMES = {"Tự do", "Cân bằng", "Sát thương gốc"};
    private static final String[] DETAILS = {
        "chỉ số, đồ, nâng cấp, ngọc tính đủ như game gốc",
        "bắn trúng thân mới ăn đủ chỉ số, Skin Thor không cộng",
        "ai cũng như nhân vật mới: không tính điểm, đồ, nâng cấp, ngọc",
    };

    /** Rules of a new or emptied room; the admin page changes it (lan_setting room_rules). */
    public static volatile byte defaultRules = BALANCED;

    private RoomRules() {
    }

    public static boolean valid(int rules) {
        return rules >= FREE && rules <= BASE;
    }

    public static String name(int rules) {
        return NAMES[valid(rules) ? rules : FREE];
    }

    public static String detail(int rules) {
        return DETAILS[valid(rules) ? rules : FREE];
    }

    /** HP, attack, defense, luck and teamwork the user's selected character takes into a match under these rules. */
    public static int[] battleStats(User user, int rules) {
        Glass glass = user.glass();
        if (rules == BASE) return baseStats(glass);
        return glass.createAbility(rules == BALANCED);
    }

    /** A level 1 character without gear: the catalog's starting points (Glass.createAbility with nothing worn). */
    public static int[] baseStats(Glass glass) {
        Glass catalog = Glass.entrys != null && glass.id >= 0 && glass.id < Glass.entrys.length ? Glass.entrys[glass.id] : null;
        int[] start = catalog != null && catalog.ability != null ? catalog.ability : new int[5];
        return new int[] {
            1000 + start[0] * 10,
            glass.att * (start[1] / 3 + 100) / 100,
            start[2] * 10,
            start[3] * 10,
            start[4] * 10,
        };
    }
}
