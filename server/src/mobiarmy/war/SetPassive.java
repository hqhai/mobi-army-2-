package mobiarmy.war;

import mobiarmy.server.Equip;
import mobiarmy.server.ThorSkin;
import mobiarmy.server.User;

/**
 * Character passives unlocked by wearing a full +14 set (gun, hat, armour, glasses, backpack of the same set).
 * The set picks the strength: set 1 gives half, gold the full passive. A costume worn on top does not matter.
 *
 * <p>The numbers come from a duel simulation (both sides in full gold +14): without passives the low-attack
 * characters won 25-33% and Apache/King Kong 74-83%; with them every character lands around 44-60%.
 * Passives are off in "Sát thương gốc" rooms, where gear does not count either.
 */
public final class SetPassive {
    /** The upgrade level every piece of the set needs. */
    public static final int SET_PLUS = 14;
    /** Set names as the last word of the gear's name, weakest first; index + 1 is the set tier. */
    static final String[] SETS = {"1", "2", "3", "4", "bạc", "vàng"};
    /** Passive strength in percent for set tiers 1..6. */
    static final int[] STRENGTH = {0, 50, 60, 70, 80, 90, 100};

    static final int GUNNER = 0, MISS6 = 1, KINGKONG = 3, ROCKETER = 4, CHICKY = 6, TARZAN = 7, MAGENTA = 9;

    /**
     * Gunner "Đòn kết liễu": the closer the target the harder he hits, GUNNER_CLOSE percent more point blank down to
     * GUNNER_FAR percent less from GUNNER_RANGE pixels away (full strength); his POW shot is always lucky; and a shot
     * while item 36 "Chỉ kẻ đầu" is on digs a hole GUNNER_FINISH_HOLE percent wide and stuns the opponents it hits:
     * they lose their next turn.
     */
    static final int GUNNER_CLOSE = 30, GUNNER_FAR = 20, GUNNER_RANGE = 600, GUNNER_FINISH_HOLE = 200;
    /** Miss 6: every third hit ignores all defense and crits for this multiplier (percent, full strength). */
    static final int MISS6_EVERY = 3, MISS6_CRIT = 150;
    static final int ROCKETER_DAMAGE = 25;
    /** Chicky: every fourth shot freezes the opponents it hits for their next turn (Player.countFreeze). */
    static final int CHICKY_EVERY = 4, CHICKY_FREEZE = 2;
    static final int TARZAN_LESS = 12;
    /** King Kong: each round he stands still takes this much off damage he takes, up to KINGKONG_MAX. */
    static final int KINGKONG_STEP = 10, KINGKONG_MAX = 30;
    /** Electrician digs 50% wider holes with his own bullets, Granos three times as wide (percent, full strength). */
    static final int ELECTRICIAN_HOLE = 150, GRANOS_HOLE = 300;
    static final int ELECTRICIAN = 2, GRANOS = 5;
    /** King Kong: after this many rounds stood still the ground under him cannot be dug until he moves. */
    static final int KINGKONG_ROCK_ROUNDS = 2;

    /** Magenta: how much of the wind her laser no longer feels, in percent (full strength: none at all). */
    static final int MAGENTA_WIND_IGNORED = 100;

    private SetPassive() {
    }

    /** Set tier 1..6 of the full +14 set the user wears on this character, or 0. */
    public static int tier(User user, int glassId) {
        int set = -1;
        boolean[] slots = new boolean[5];
        for (Equip equip : user.equips) {
            if (equip.glassID != glassId || !equip.isUse || equip.vip != 0 || ThorSkin.is(equip)) continue;
            if (equip.type < 0 || equip.type > 4 || equip.level2 < SET_PLUS) return 0;
            int piece = setOf(equip);
            if (piece < 0 || (set >= 0 && piece != set)) return 0;
            set = piece;
            slots[equip.type] = true;
        }
        for (boolean slot : slots) if (!slot) return 0;
        return set + 1;
    }

    /** Index in SETS of the set this piece belongs to, or -1 (from the catalog name, e.g. "nón bạc"). */
    static int setOf(Equip equip) {
        Equip catalog = Equip.entrys == null ? null : Equip.get((byte) equip.glassID, equip.id, equip.type);
        String name = catalog != null && catalog.name != null ? catalog.name : equip.name;
        if (name == null) return -1;
        String last = name.substring(name.lastIndexOf(' ') + 1);
        for (int i = 0; i < SETS.length; i++) if (SETS[i].equals(last)) return i;
        return -1;
    }

    static int scaled(Player player, int full) {
        return full * STRENGTH[player.setTier] / 100;
    }

    /** Starts the shot and hit counts over (practice, when the set changes). */
    public static void resetCounters(Player player) {
        player.passiveShots = 0;
        player.passiveHits = 0;
        player.passiveStill = 0;
    }

    /** Called before a player's shot. */
    public static void beforeShot(Player shooter) {
        if (shooter.setTier == 0) return;
        shooter.passiveShots++;
        shooter.passiveHit = false;
        shooter.passiveTrueShot = shooter.glassID == MISS6 && shooter.passiveHits % MISS6_EVERY == MISS6_EVERY - 1;
        shooter.passiveFreezeShot = shooter.glassID == CHICKY && shooter.passiveShots % CHICKY_EVERY == 0;
        shooter.passiveFinisher = shooter.glassID == GUNNER && shooter.headGuideShots > 0;
    }

    /** Gunner's POW shot is always lucky (call after the luck roll). */
    static void powLuck(Player shooter) {
        if (shooter != null && shooter.setTier != 0 && shooter.glassID == GUNNER && shooter.isPow) shooter.lucky = true;
    }

    /** Called after a player's shot has been applied. */
    public static void afterShot(Player shooter) {
        if (shooter.setTier != 0 && shooter.passiveHit) shooter.passiveHits++;
        shooter.passiveTrueShot = false;
        shooter.passiveFreezeShot = false;
        shooter.passiveFinisher = false;
        if (shooter.headGuideShots > 0) shooter.headGuideShots--;
    }

    /** After a hit: Chicky's fourth shot freezes an opponent it damaged for one turn. */
    static void afterHit(Player shooter, Player target, int damage) {
        if (damage <= 0 || !enemies(shooter, target)) return;
        shooter.passiveHit = true;
        if (shooter.passiveFreezeShot && !target.isDie) {
            target.countFreeze = Math.max(target.countFreeze, CHICKY_FREEZE);
            target.mapData.freeze(target.index, 0);
        }
        if (shooter.passiveFinisher && !target.isDie) target.stunTurns = 1; // Gunner's finisher: loses the next turn
    }

    /** Damage of one explosion on target after the shooter's passive. */
    static int dealt(Player shooter, Player target, int att) {
        if (shooter == null || shooter.setTier == 0 || att <= 0) return att;
        int bonus = switch (shooter.glassID) {
            case GUNNER -> gunnerRange(shooter, target);
            case ROCKETER -> ROCKETER_DAMAGE;
            default -> 0;
        };
        return att + att * scaled(shooter, bonus) / 100;
    }

    /** Gunner's damage change in percent at the target's distance: +GUNNER_CLOSE point blank down to -GUNNER_FAR. */
    static int gunnerRange(Player shooter, Player target) {
        double d = Math.min(GUNNER_RANGE, Math.hypot(shooter.x - target.x, shooter.y - target.y));
        return (int) Math.round(GUNNER_CLOSE - (GUNNER_CLOSE + GUNNER_FAR) * d / GUNNER_RANGE);
    }

    /** True when this hit from shooter ignores defense and crits (Miss 6's third hit). */
    static boolean trueHit(Player shooter, Player target) {
        return shooter != null && shooter.passiveTrueShot && enemies(shooter, target);
    }

    /** Opponents: different teams, or anyone else in a free-for-all room (everyone is team 3 there). */
    static boolean enemies(Player a, Player b) {
        return a != b && (a.team != b.team || a.team == 3);
    }

    /** Miss 6's crit multiplier in percent. */
    static int trueHitCrit(Player shooter) {
        return 100 + scaled(shooter, MISS6_CRIT - 100);
    }

    /** Damage the target takes after its own passive. */
    static int taken(Player target, int damage) {
        if (target.setTier == 0 || damage <= 0) return damage;
        int less = switch (target.glassID) {
            case TARZAN -> TARZAN_LESS;
            case KINGKONG -> Math.min(KINGKONG_MAX, KINGKONG_STEP * target.passiveStill);
            default -> 0;
        };
        return damage - damage * scaled(target, less) / 100;
    }

    /** Share of the wind (percent) the shooter's shots feel, out of usual (100 = the whole wind). */
    public static int windShare(Player shooter, int usual) {
        if (shooter == null || shooter.setTier == 0 || shooter.glassID != MAGENTA) return usual;
        return usual * (100 - scaled(shooter, MAGENTA_WIND_IGNORED)) / 100;
    }

    /**
     * At the start of the player's own turn: King Kong counts the rounds he is still where he was at the start of
     * his last turn. Walking, teleporting or being knocked away all reset it.
     */
    static void newTurn(Player player) {
        if (player.setTier == 0) return;
        boolean still = player.passiveX == player.x && player.passiveY == player.y;
        player.passiveStill = still ? player.passiveStill + 1 : 0;
        player.passiveX = player.x;
        player.passiveY = player.y;
    }

    /**
     * Hole size in percent for this shooter's bullet: Electrician's and Granos' own bullets dig wider holes. The
     * client gets the same number with the shot (SessionHandler.shoot) and scales the hole the same way.
     */
    public static int holeScale(Player shooter, int bulletId) {
        if (shooter == null || shooter.setTier == 0 || bulletId != shooter.bulletIdByGlassID()) return 100;
        if (shooter.passiveFinisher) return 100 + scaled(shooter, GUNNER_FINISH_HOLE - 100); // Gunner's finisher blast
        return switch (shooter.glassID) {
            case ELECTRICIAN -> 100 + scaled(shooter, ELECTRICIAN_HOLE - 100);
            case GRANOS -> 100 + scaled(shooter, GRANOS_HOLE - 100);
            default -> 100;
        };
    }

    /**
     * Ground that cannot be dug right now: under every King Kong who has stood still long enough, and under whoever
     * used item 37 "Bê tông cốt thép" this round or the last.
     * Each rectangle is {x, y, width, height} in map pixels; the client gets them with every shot.
     */
    public static java.util.List<short[]> protectedGround(MapData map) {
        java.util.List<short[]> rects = new java.util.ArrayList<>();
        for (Player player : map.players) {
            boolean rock = player != null && player.setTier != 0 && player.glassID == KINGKONG && player.passiveStill >= KINGKONG_ROCK_ROUNDS;
            boolean concrete = player != null && player.concreteTurns > 0; // LAN item 37 "Bê tông cốt thép"
            if (player != null && !player.isDie && (rock || concrete)) {
                rects.add(new short[] {(short) (player.x - 16), (short) (player.y - 4), 32, 24});
            }
        }
        return rects;
    }

    /** Hole mask scaled by percent with nearest-pixel sampling; the client's CMap.scaledMask does exactly this. */
    public static int[] scaleMask(int[] mask, int w, int h, int percent, int[] size) {
        int w2 = Math.max(1, w * percent / 100), h2 = Math.max(1, h * percent / 100);
        int[] out = new int[w2 * h2];
        for (int i = 0; i < h2; i++) {
            for (int j = 0; j < w2; j++) out[i * w2 + j] = mask[(i * h / h2) * w + (j * w / w2)];
        }
        size[0] = w2;
        size[1] = h2;
        return out;
    }
}
